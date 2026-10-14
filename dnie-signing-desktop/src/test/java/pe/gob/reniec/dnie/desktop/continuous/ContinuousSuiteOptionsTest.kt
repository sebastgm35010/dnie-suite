package pe.gob.reniec.dnie.desktop.continuous

import org.junit.Test
import org.junit.Assert.*

class ContinuousSuiteOptionsTest {
    @Test fun automaticConnectionNeedsNoAddressesAndKeepsLegacySyntax() {
        val automatic=ContinuousSuiteOptions.parse(arrayOf("suite","--workspace","jobs","--identity","pc.dpapi","--pdf-tsa","reniec"))
        assertEquals("",automatic.pcHint);assertEquals("",automatic.phoneHint);assertTrue(automatic.pdf.build().reniecTimestamp)
        val legacy=ContinuousSuiteOptions.parse(arrayOf("suite","--pc","192.168.1.5","--phone","192.168.1.11","--workspace","jobs","--identity","pc.dpapi"))
        assertEquals("192.168.1.11",legacy.phoneHint)
    }
    @Test fun refusesSecretsPublicDestinationsDuplicatesAndMalformedArguments() {
        val base=arrayOf("suite","--workspace","jobs","--identity","pc.dpapi")
        for(extra in listOf(arrayOf("--phone","8.8.8.8"),arrayOf("--pin","1234"),arrayOf("--can","123456"),arrayOf("--identity","other"),arrayOf("--phone")))
            assertThrows(Exception::class.java) {ContinuousSuiteOptions.parse(base+extra)}
    }
}
