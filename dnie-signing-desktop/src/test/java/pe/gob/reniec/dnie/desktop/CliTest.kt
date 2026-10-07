package pe.gob.reniec.dnie.desktop

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import org.junit.Assert.*
import org.junit.Test
import pe.gob.reniec.dnie.desktop.cli.CliOptions
import pe.gob.reniec.dnie.desktop.cli.runCli

class CliTest {
    @Test fun positionalPathsStayWholeIncludingSpacesAndUnicode() {
        val request = CliOptions.parse(arrayOf("demo", "--workspace", "trabajos locales", "--input", "acta ñ 1.txt", "--input", "acta 2.bin"))
        assertEquals("trabajos locales", request.workspaceRoot.toString())
        assertEquals(listOf("acta ñ 1.txt", "acta 2.bin"), request.inputs.map { it.toString() })
    }
    @Test fun realBackendAndSecretsAreRejectedWithoutPrintingTheirValues() {
        listOf(arrayOf("sign", "--pin", "secreto-prueba"), arrayOf("demo", "--pin", "secreto-prueba"),
            arrayOf("demo", "--workspace", "jobs", "--input")).forEach { args ->
            val output = ByteArrayOutputStream()
            val errors = ByteArrayOutputStream()
            assertEquals(2, runCli(args, PrintStream(output), PrintStream(errors)))
            assertFalse(output.toString().contains("secreto-prueba")); assertFalse(errors.toString().contains("secreto-prueba"))
        }
    }
    @Test fun helpMakesSimulationScopeExplicit() {
        val output = ByteArrayOutputStream()
        assertEquals(0, runCli(arrayOf("--help"), PrintStream(output)))
        assertTrue(output.toString().contains("SIMULACION")); assertTrue(output.toString().contains("no utiliza el DNI"))
    }
    @Test fun pdfModeDefaultsToInvisibleAndDetachedRequiresExplicitSelection() {
        val base = arrayOf("demo", "--workspace", "jobs", "--input", "prueba.pdf")
        assertEquals(pe.gob.reniec.dnie.desktop.model.DesktopPdfMode.INVISIBLE, CliOptions.parse(base).pdfMode)
        assertEquals(pe.gob.reniec.dnie.desktop.model.DesktopPdfMode.DETACHED,
            CliOptions.parse(base + arrayOf("--pdf-mode", "detached")).pdfMode)
        for (options in listOf(arrayOf("--pdf-mode", "unsupported"),
            arrayOf("--pdf-mode", "invisible", "--pdf-mode", "detached"))) {
            val errors = ByteArrayOutputStream()
            assertEquals(2, runCli(base + options, PrintStream(ByteArrayOutputStream()), PrintStream(errors)))
        }
    }
    @Test fun visibleOptionsAreSharedAndRejectInvalidPlacementAndFields() {
        val base=arrayOf("demo","--workspace","jobs","--input","prueba.pdf")
        val request=CliOptions.parse(base+arrayOf("--pdf-mode","visible","--pdf-page","2",
            "--pdf-box","0.1,0.2,0.6,0.3","--pdf-fields","date,reason","--pdf-reason","Conforme","--pdf-style","minimal"))
        assertEquals(pe.gob.reniec.dnie.desktop.model.DesktopPdfMode.VISIBLE,request.pdfMode)
        assertEquals(2,request.pdfOptions.pageNumber); assertTrue(request.pdfOptions.appearance.showDate)
        assertFalse(request.pdfOptions.appearance.showDni); assertEquals("Conforme",request.pdfOptions.reason)
        for(options in listOf(arrayOf("--pdf-page","0"),arrayOf("--pdf-box","0.9,0.1,0.5,0.2"),
            arrayOf("--pdf-fields","unknown"),arrayOf("--pdf-fields","dni,dni"))) {
            assertEquals(2,runCli(base+options,PrintStream(ByteArrayOutputStream()),PrintStream(ByteArrayOutputStream())))
        }
    }
    @Test fun timestampOptionIsExplicitAndRejectsUnsupportedOrDetachedModes() {
        val base=arrayOf("demo","--workspace","jobs","--input","prueba.pdf")
        assertFalse(CliOptions.parse(base).pdfOptions.reniecTimestamp)
        assertTrue(CliOptions.parse(base+arrayOf("--pdf-tsa","reniec")).pdfOptions.reniecTimestamp)
        for(flags in listOf(arrayOf("--pdf-tsa","unknown"),arrayOf("--pdf-tsa","reniec","--pdf-mode","detached"),
            arrayOf("--pdf-tsa","reniec","--pdf-tsa","none"))) {
            assertEquals(2,runCli(base+flags,PrintStream(ByteArrayOutputStream()),PrintStream(ByteArrayOutputStream())))
        }
    }

}
