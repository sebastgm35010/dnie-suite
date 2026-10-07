package pe.gob.reniec.dnie.desktop.orchestration

import org.junit.Test
import org.junit.Assert.*

class DesktopProductCoordinatorTest {
    private class Fake : BridgeGateway {
        var snapshot=BridgeSnapshot(BridgeAvailability.AVAILABLE,BridgeHostState.STOPPED)
        var opens=0;var stops=0
        override fun status()=snapshot
        override fun openManager() {opens++}
        override fun stop(): BridgeSnapshot {stops++;snapshot=BridgeSnapshot(BridgeAvailability.AVAILABLE,BridgeHostState.STOPPED);return snapshot}
    }
    @Test fun runningOrUnknownBridgeNeverAcquiresSuiteAndDoesNotStopItAutomatically() {
        val gateway=Fake();val product=DesktopProductCoordinator(gateway)
        for(state in BridgeHostState.entries.filter { it!=BridgeHostState.STOPPED }) {
            gateway.snapshot=BridgeSnapshot(BridgeAvailability.AVAILABLE,state)
            assertTrue(runCatching { product.acquirePrivateSuite() }.exceptionOrNull() is ProductModeConflict)
        }
        gateway.snapshot=BridgeSnapshot(BridgeAvailability.UNAVAILABLE)
        assertTrue(runCatching { product.acquirePrivateSuite() }.isFailure)
        assertEquals(0,gateway.stops);assertEquals(0,gateway.opens)
    }
    @Test fun disconnectedOrStandaloneBridgeAllowsSuiteWithoutInstallingAnything() {
        val gateway=Fake();val product=DesktopProductCoordinator(gateway)
        for(availability in listOf(BridgeAvailability.OPTIONAL_MODULE_ABSENT,BridgeAvailability.NOT_INSTALLED,BridgeAvailability.SERVICE_STOPPED)) {
            gateway.snapshot=BridgeSnapshot(availability)
            product.acquirePrivateSuite().close()
        }
        assertEquals(0,gateway.stops);assertEquals(0,gateway.opens)
    }
    @Test fun permitBlocksModeSwitchUntilDisconnectAndOldPermitCannotReleaseNewSession() {
        val gateway=Fake();val product=DesktopProductCoordinator(gateway)
        val first=product.acquirePrivateSuite()
        assertTrue(runCatching { product.openBridgeManager() }.isFailure)
        assertTrue(runCatching { product.stopBridge() }.isFailure)
        assertTrue(runCatching { product.acquirePrivateSuite() }.isFailure)
        first.close();val second=product.acquirePrivateSuite();first.close()
        assertTrue(runCatching { product.openBridgeManager() }.isFailure)
        second.close();product.openBridgeManager();product.stopBridge()
        assertEquals(1,gateway.opens);assertEquals(1,gateway.stops)
    }
    @Test fun nativeProtocolRejectsAdditionalFieldsAndUnrecognizedStates() {
        val line="DNIE1\tAVAILABLE\tRUNNING\t1"
        assertTrue(WindowsBridgeGateway.decode(line).cardPresent)
        for(invalid in listOf(line+"\tprivate-pairing",line.replace("RUNNING","evil"),line.replace("\t1","\ttrue"),"{}"))
            assertTrue(runCatching { WindowsBridgeGateway.decode(invalid) }.isFailure)
    }
    @Test fun missingAdapterIsOptionalAndCannotExecuteBridgeActions() {
        val gateway=WindowsBridgeGateway(null)
        assertEquals(BridgeAvailability.OPTIONAL_MODULE_ABSENT,gateway.status().availability)
        assertTrue(runCatching { gateway.openManager() }.isFailure)
        assertTrue(runCatching { gateway.stop() }.isFailure)
    }
}
