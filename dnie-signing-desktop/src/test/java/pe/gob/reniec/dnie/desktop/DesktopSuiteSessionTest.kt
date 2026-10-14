package pe.gob.reniec.dnie.desktop

import java.net.*
import java.nio.file.*
import java.util.concurrent.Executors
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.rules.TemporaryFolder
import org.bouncycastle.cms.*
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder
import pe.gob.reniec.dnie.desktop.application.DesktopSuiteSession
import pe.gob.reniec.dnie.desktop.demo.SimulatedSigningCard
import pe.gob.reniec.dnie.desktop.model.DesktopSigningRequest
import pe.gob.reniec.dnie.signing.link.*

/** Frontend application boundary exercised against simulated Android over actual pinned TLS. */
class DesktopSuiteSessionTest {
    @get:Rule val folder=TemporaryFolder()
    @Test fun guiBoundarySignsTwoJobsWithoutConsoleAndWipesPin()=runBlocking<Unit> {
        assumeTrue(System.getProperty("os.name").startsWith("Windows"))
        val ip=NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }.mapNotNull { it.hostAddress }.firstOrNull(SigningProvision::privateAddress)
        assumeTrue(ip!=null)
        val port=ServerSocket(0).use { it.localPort };val root=folder.root.toPath()
        val pool=Executors.newSingleThreadExecutor();val slot=SuiteCardSlot();val card=SimulatedSigningCard()
        slot.offer { card to { true } }
        var phone: PhoneSigningTransport?=null;var pair: SuitePairing?=null;var provision: SigningProvision?=null
        val session=DesktopSuiteSession(port)
        try {
            session.connect(ip!!,ip,root.resolve("jobs"),root.resolve("pc.dpapi")) { info ->
                pair=SuitePairing.parse(Files.readString(info.file));provision=pair!!.provision()
                phone=PhoneSigningTransport(provision!!,port,continuous=true)
                pool.submit { runBlocking { ContinuousSigningConnection(phone!!.accept(),slot::acquire,slot::invalidate,finishJob=slot::finishJob).run() } }
            }
            val missingPin="0000".toCharArray()
            try {
                session.sign(DesktopSigningRequest(listOf(root.resolve("missing.pdf")),root.resolve("jobs")),missingPin)
                fail("Missing input must fail before BEGIN")
            } catch(_: pe.gob.reniec.dnie.desktop.application.DesktopInputUnavailableException) {
                assertTrue(missingPin.all { it=='\u0000' })
                assertEquals(0,card.selections);assertEquals(0,card.signatures)
            }
            repeat(2) { index ->
                val input=root.resolve("technical-$index.txt");Files.writeString(input,"Synthetic job $index")
                val pin="0000".toCharArray()
                val result=session.sign(DesktopSigningRequest(listOf(input),root.resolve("jobs")),pin)
                assertTrue(pin.all { it=='\u0000' });assertTrue(result.complete)
                val document=result.documents.single()
                val cms=CMSSignedData(CMSProcessableByteArray(Files.readAllBytes(document.original)),Files.readAllBytes(document.signature!!))
                assertTrue(cms.signerInfos.signers.single().verify(JcaSimpleSignerInfoVerifierBuilder().build(card.certificate)))
            }
            assertEquals(1,card.selections);assertEquals(2,card.signatures)
        } finally { session.close();phone?.close();slot.close();provision?.close();pair?.close();pool.shutdownNow() }
    }
}
