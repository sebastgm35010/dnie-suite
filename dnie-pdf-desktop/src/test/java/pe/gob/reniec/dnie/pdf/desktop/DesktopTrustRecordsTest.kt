package pe.gob.reniec.dnie.pdf.desktop

import java.util.UUID
import java.util.prefs.Preferences
import org.junit.Test
import org.junit.Assert.*
import pe.gob.reniec.dnie.validation.TrustedListRecord

class DesktopTrustRecordsTest {
    @Test fun publicTrustRecordSurvivesNewAdapterAndRejectsRollbackOrChangedDigest() {
        val node=Preferences.userRoot().node("pe/gob/reniec/dnie/tests/${UUID.randomUUID()}")
        try {
            val store=DesktopTrustedListRecords(node)
            assertNull(store.read())
            val record=TrustedListRecord(26,"a".repeat(64));store.save(record)
            assertEquals(record,DesktopTrustedListRecords(node).read())
            for(invalid in listOf(TrustedListRecord(25,"a".repeat(64)),TrustedListRecord(26,"b".repeat(64)))) {
                assertTrue(runCatching { store.save(invalid) }.isFailure)
                assertEquals(record,store.read())
            }
            node.put("tsl-record","invalid");node.flush()
            assertTrue(runCatching { store.read() }.isFailure)
        } finally { node.removeNode();node.flush() }
    }
}
