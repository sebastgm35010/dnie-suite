package pe.gob.reniec.dnie.validation

import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class TrustTimestampParserTest {
    @Test fun utcAndExplicitOffsetsIdentifyTheSameInstant() {
        val utc = parseDate("2026-10-07T18:07:07Z")
        assertEquals(utc, parseDate("2026-10-07T13:07:07-05:00"))
        assertEquals(utc, parseDate("2026-10-08T08:07:07+14:00"))
    }
    @Test fun fractionsUseMillisecondPrecisionWithoutDependingOnXmlDatatypeFactory() {
        val previous = System.getProperty("javax.xml.datatype.DatatypeFactory")
        try {
            System.setProperty("javax.xml.datatype.DatatypeFactory", "missing.optional.Provider")
            val seconds = parseDate("2026-08-10T16:10:31Z").time
            assertEquals(seconds + 312, parseDate("2026-08-10T16:10:31.312Z").time)
            assertEquals(seconds + 300, parseDate("2026-08-10T16:10:31.3Z").time)
            assertEquals(seconds + 312, parseDate("2026-08-10T16:10:31.312999999Z").time)
        } finally {
            if (previous == null) System.clearProperty("javax.xml.datatype.DatatypeFactory")
            else System.setProperty("javax.xml.datatype.DatatypeFactory", previous)
        }
    }
    @Test fun impossibleDatesMissingTimezonesAndMalformedOffsetsCannotValidate() {
        for (value in listOf("2026-02-30T12:00:00Z", "2026-10-07T25:00:00Z", "2026-10-07T12:00:60Z",
            "2026-10-07T12:00:00", "2026-10-07T12:00:00+14:01", "2026-10-07T12:00:00+05:60",
            "2026-10-07T12:00:00+0500", "2026-10-07T12:00:00Zjunk", "0000-10-07T12:00:00Z"))
            assertTrue(value, runCatching { parseDate(value) }.isFailure)
    }
    @Test fun nestedStagesPreserveTheFailureLocationWithoutExposingExceptionText() {
        val failure = runCatching {
            trustStep(TrustStage.TSL_XML) {
                trustStep(TrustStage.TSL_DOWNLOAD) { throw IOException("private material must not be displayed") }
            }
        }.exceptionOrNull() as TrustValidationFailure
        assertEquals(TrustStage.TSL_DOWNLOAD, failure.stage)
        assertEquals("TSL_DOWNLOAD:IOException", failure.safeCode)
        assertFalse(failure.safeCode.contains("private"))
    }
}
