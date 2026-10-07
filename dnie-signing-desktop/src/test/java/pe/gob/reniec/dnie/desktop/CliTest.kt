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
}
