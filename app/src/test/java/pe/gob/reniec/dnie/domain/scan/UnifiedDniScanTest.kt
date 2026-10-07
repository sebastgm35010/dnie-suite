package pe.gob.reniec.dnie.domain.scan

import java.io.IOException
import kotlinx.coroutines.*
import org.junit.Test
import org.junit.Assert.*
import pe.gob.reniec.dnie.icao.*
import pe.gob.reniec.dnie.core.nfc.SecureMessagingIntegrityException

class UnifiedDniScanTest {
    @Test fun certificateSessionClosesBeforeOpeningIndependentIdentitySession() = runBlocking<Unit> {
        val events=mutableListOf<String>()
        val result=UnifiedDniScan().read({ object: PublicCertificateReader {
            override suspend fun read(progress:(String)->Unit): CertificateSnapshot { events.add("cert-read");return CertificateSnapshot(emptyMap()) }
            override fun close() { events.add("cert-close") }
        } },{ events.add("icao-open");object: IcaoReader {
            override suspend fun read(onProgress:(IcaoDocument)->Unit): IcaoDocument { events.add("icao-read");return IcaoDocument(completed=true) }
            override fun close() { events.add("icao-close") }
        } })
        assertEquals(listOf("cert-read","cert-close","icao-open","icao-read","icao-close"),events)
        assertNotNull(result.identity)
    }
    @Test fun missingPkiDoesNotPreventIcaoButTransportOrIntegrityDoes() = runBlocking<Unit> {
        for(failure in listOf(IllegalStateException("PKI absent"),IOException("lost"),SecureMessagingIntegrityException("bad MAC"))) {
            var started=false;var closed=0
            val result=UnifiedDniScan().read({ object: PublicCertificateReader {
                override suspend fun read(progress:(String)->Unit): CertificateSnapshot = throw failure
                override fun close() { closed++ }
            } },{ started=true;object: IcaoReader {
                override suspend fun read(onProgress:(IcaoDocument)->Unit)=IcaoDocument(completed=true)
            } })
            assertEquals(1,closed);assertNotNull(result.certificateIssue)
            assertEquals(failure is IllegalStateException,started)
        }
    }
    @Test fun interruptedCertificatesAreRetainedWithoutOpeningAnotherSession()=runBlocking<Unit> {
        var published=false
        val result=UnifiedDniScan().read({ object: PublicCertificateReader {
            override suspend fun read(progress:(String)->Unit)=CertificateSnapshot(emptyMap(),interrupted=true)
            override fun close() {}
        } },{ error("must not open ICAO") },onCertificates={published=it.interrupted})
        assertTrue(published);assertTrue(result.certificates!!.interrupted);assertNull(result.identity)
    }
    @Test fun cancellationClosesActiveReaderExactlyOnceAndDoesNotOpenIcao()=runBlocking<Unit> {
        val scan=UnifiedDniScan(); val started=CompletableDeferred<Unit>();var closed=0
        val job=launch { scan.read({object: PublicCertificateReader {
            override suspend fun read(progress:(String)->Unit): CertificateSnapshot { started.complete(Unit);awaitCancellation() }
            override fun close() { closed++ }
        }},{error("must not open ICAO")}) }
        started.await();scan.close();job.cancelAndJoin();scan.close()
        assertEquals(1,closed)
    }
    @Test fun identityFailureKeepsCertificateSnapshot()=runBlocking<Unit> {
        val snapshot=CertificateSnapshot(emptyMap());var closed=false
        val result=UnifiedDniScan().read({object: PublicCertificateReader {
            override suspend fun read(progress:(String)->Unit)=snapshot
            override fun close() {}
        }},{object: IcaoReader {
            override suspend fun read(onProgress:(IcaoDocument)->Unit): IcaoDocument=throw IllegalStateException("No ICAO")
            override fun close() { closed=true }
        }})
        assertSame(snapshot,result.certificates);assertNotNull(result.identityIssue);assertTrue(closed)
    }
    @Test fun cancellationBeforeReadReleasesReservedCertificateOnlyOnce()=runBlocking<Unit> {
        var closes=0
        val reader=object: PublicCertificateReader {
            override suspend fun read(progress:(String)->Unit): CertificateSnapshot=error("must not read")
            override fun close() { closes++ }
        }
        val scan=UnifiedDniScan(reader);scan.close()
        assertTrue(runCatching { scan.read({reader},{error("must not open")}) }.exceptionOrNull() is CancellationException)
        scan.close();assertEquals(1,closes)
    }
}
