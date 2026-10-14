package pe.gob.reniec.dnie.ui.identity

import org.junit.Assert.*
import org.junit.Test
import pe.gob.reniec.dnie.icao.*
import java.util.*

class IdentityPresentationR8Test {
    private val today = GregorianCalendar(TimeZone.getTimeZone("UTC")).apply { clear(); set(2026, 9, 14) }.time
    private val identity = IcaoContent.Identity("I", "PER", "12345678", "PRUEBA", "SINTETICA", "PER", "031215", "F", "291215", "", mapOf("document" to true), "")
    @Test fun datesUseContextWithoutInventingAnAmbiguousBirthCentury() {
        assertEquals("15/12/2003", IdentityPresentation.date("031215", true, today))
        assertEquals("15/12/1990", IdentityPresentation.date("901215", true, today))
        assertEquals("15/12/2029", IdentityPresentation.date("291215", false, today))
        assertEquals("15/12/10 (siglo no indicado)", IdentityPresentation.date("101215", true, today))
    }
    @Test fun invalidDatesStayRawIncludingLeapDayAndMissingData() {
        listOf("", "notdate", "230229", "261332").forEach { assertEquals(it, IdentityPresentation.date(it, false, today)) }
        assertEquals("29/02/2024", IdentityPresentation.date("240229", false, today))
    }
    @Test fun emptyOrFailedMrzChecksDoNotGetAVerifiedChip() {
        fun doc(value: IcaoContent.Identity, hash: HashStatus = HashStatus.NOT_CHECKED) = IcaoDocument(mapOf(IcaoFile.DG1 to IcaoBlock(IcaoFile.DG1, BlockStatus.AVAILABLE, value, hashStatus = hash)))
        assertFalse(IdentityPresentation.mrzVerified(IcaoDocument()))
        assertFalse(IdentityPresentation.mrzVerified(doc(identity.copy(checkDigits = emptyMap()))))
        assertFalse(IdentityPresentation.mrzVerified(doc(identity.copy(checkDigits = mapOf("document" to false)))))
        assertFalse(IdentityPresentation.mrzVerified(doc(identity, HashStatus.MISMATCH)))
        assertTrue(IdentityPresentation.mrzVerified(doc(identity)))
    }
    @Test fun sodIntegrityDoesNotClaimIssuerTrustAndBadMarkCannotBeSelected() {
        val security = IcaoContent.Security("SHA-256", emptyMap(), true, "Synthetic DS", "Untrusted")
        val sod = IcaoBlock(IcaoFile.SOD, BlockStatus.AVAILABLE, security)
        val mark = IcaoBlock(IcaoFile.DG7, BlockStatus.AVAILABLE, IcaoContent.Images(listOf(IcaoImage(byteArrayOf(1), "image/jpeg"))), hashStatus = HashStatus.MISMATCH)
        val d = IcaoDocument(mapOf(IcaoFile.SOD to sod, IcaoFile.DG7 to mark))
        assertTrue(IdentityPresentation.sodSignatureVerified(d)); assertFalse(security.issuerTrustVerified)
        assertFalse(IdentityPresentation.markUsable(d))
        assertFalse(IdentityPresentation.sodSignatureVerified(d.copy(blocks = mapOf(IcaoFile.SOD to sod.copy(status = BlockStatus.ERROR)))))
    }
}
