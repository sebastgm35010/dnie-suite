package pe.gob.reniec.dnie.passkeys

import org.junit.Assert.*
import org.junit.Test

class PasskeyBackupPolicyTest {
    private fun record(value: Byte, eligible: Boolean) = PasskeyRecord(ByteArray(32) { value }, "example.com", byteArrayOf(1),
        "Test", "Test", byteArrayOf(2, 3), byteArrayOf(4), backupEligible = eligible)
    @Test fun legacyV1KeepsItsImmutableBackupFlagAndIsNotExported() {
        PasskeyCatalog(mutableListOf(record(1, false))).use { catalog ->
            val encoded = catalog.encode().dropLast(2).toByteArray().also { it[3] = 1 }
            PasskeyCatalog.decode(encoded).use { legacy ->
                assertFalse(legacy.records.single().backupEligible); assertFalse(legacy.records.single().backedUp)
                try { PasskeyBackupPolicy.portableCopy(legacy).close(); fail("Legacy exported") } catch (_: IllegalArgumentException) {}
                try { PasskeyBackupPolicy.validateRestore(legacy); fail("Legacy transferred") } catch (_: IllegalArgumentException) {}
            }
        }
    }
    @Test fun portableCopyFiltersLegacyAndRetainsOriginalKeysWithBackupState() {
        PasskeyCatalog(mutableListOf(record(1, false), record(2, true))).use { catalog ->
            PasskeyBackupPolicy.portableCopy(catalog).use { copy ->
                assertEquals(1, copy.records.size)
                assertArrayEquals(catalog.records[1].id, copy.records.single().id)
                assertArrayEquals(catalog.records[1].privateKey, copy.records.single().privateKey)
                assertTrue(copy.records.single().backedUp)
                assertFalse(catalog.records[1].backedUp)
            }
            assertArrayEquals(byteArrayOf(2, 3), catalog.records[1].privateKey)
        }
    }
    @Test fun restoreDoesNotAcceptMixedCatalogOrEscalateLegacyEligibility() {
        PasskeyCatalog(mutableListOf(record(1, false), record(2, true))).use { catalog ->
            try { PasskeyBackupPolicy.validateRestore(catalog); fail("Legacy escalated") } catch (_: IllegalArgumentException) {}
            assertFalse(catalog.records[0].backupEligible); assertFalse(catalog.records[1].backedUp)
        }
    }
    @Test fun v2PreservesEligibilityAndBackupStateAndRejectsImpossibleFlags() {
        PasskeyCatalog(mutableListOf(record(1, true).apply { backedUp = true })).use { catalog ->
            val bytes = catalog.encode()
            PasskeyCatalog.decode(bytes).use { restored ->
                assertTrue(restored.records.single().backupEligible); assertTrue(restored.records.single().backedUp)
            }
            bytes[bytes.lastIndex - 1] = 0
            try { PasskeyCatalog.decode(bytes).close(); fail("BS without BE accepted") } catch (_: IllegalArgumentException) {}
        }
    }
}
