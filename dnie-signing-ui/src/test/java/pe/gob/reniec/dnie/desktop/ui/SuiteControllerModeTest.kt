package pe.gob.reniec.dnie.desktop.ui

import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Test
import org.junit.Assert.*
import pe.gob.reniec.dnie.desktop.orchestration.*

class SuiteControllerModeTest {
    private class Fake(val host: BridgeHostState): BridgeGateway {
        var opened=false
        override fun status()=BridgeSnapshot(BridgeAvailability.AVAILABLE,host)
        override fun openManager() { opened=true }
        override fun stop(): BridgeSnapshot = error("Must never automatically stop Bridge")
    }
    @Test fun activeBridgeRejectsConnectionBeforeOpeningAnyAndroidTransport() {
        val gateway=Fake(BridgeHostState.RUNNING);val product=DesktopProductCoordinator(gateway)
        val done=CountDownLatch(1);var message=""
        SuiteController(product).use { controller ->
            controller.connect("invalid-address","invalid-address",Path.of("unused"),Path.of("unused"),
                { error("Must not create pairing") },{ ok,text -> assertFalse(ok);message=text;done.countDown() })
            assertTrue(done.await(5,TimeUnit.SECONDS));assertTrue(message.contains("puente compatible"))
            product.openBridgeManager();assertTrue(gateway.opened)
        }
    }
    @Test fun failedConnectionReleasesSuitePermitForExplicitBridgeUse() {
        val gateway=Fake(BridgeHostState.STOPPED);val product=DesktopProductCoordinator(gateway)
        val done=CountDownLatch(1)
        SuiteController(product).use { controller ->
            controller.connect("invalid-address","invalid-address",Path.of("unused"),Path.of("unused"),
                { error("Must not create pairing") },{ ok,_ -> assertFalse(ok);done.countDown() })
            assertTrue(done.await(5,TimeUnit.SECONDS))
            product.openBridgeManager();assertTrue(gateway.opened)
        }
    }
}
