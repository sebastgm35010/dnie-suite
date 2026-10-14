package pe.gob.reniec.dnie.signing.link

import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class DiscoveryOptionalTest {
    @Test fun unavailableDiscoveryKeepsTlsListenerReadyAndCancellationClosesIt() {
        val provision=SigningProvision(UUID.randomUUID(),"192.168.1.5",System.currentTimeMillis()+30_000,1,ByteArray(32),ByteArray(32))
        val unavailable=CountDownLatch(1);val ready=CountDownLatch(1)
        val transport=PhoneSigningTransport(provision,presenceFactory={throw java.net.BindException("Synthetic port conflict")})
        val worker=Executors.newSingleThreadExecutor()
        try {
            val result=worker.submit<Boolean> {
                try {transport.accept(onEvent={if(it=="discovery_unavailable")unavailable.countDown();if(it=="listener_ready")ready.countDown()});false}
                catch(_: Exception){true}
            }
            assertTrue(unavailable.await(5,TimeUnit.SECONDS))
            assertTrue(ready.await(5,TimeUnit.SECONDS))
            assertFalse(result.isDone)
            transport.close();assertTrue(result.get(5,TimeUnit.SECONDS))
        } finally {transport.close();provision.close();worker.shutdownNow()}
    }
}
