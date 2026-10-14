package pe.gob.reniec.dnie.desktop.cli

import kotlinx.coroutines.runBlocking
import pe.gob.reniec.dnie.desktop.application.DesktopSigningService
import pe.gob.reniec.dnie.desktop.demo.SimulatedSigningCard
import pe.gob.reniec.dnie.desktop.model.SigningBackend
import java.io.PrintStream
import kotlin.system.exitProcess

fun main(args: Array<String>) { exitProcess(runCli(args)) }

fun runCli(args: Array<String>, output: PrintStream = System.out, errors: PrintStream = System.err): Int {
    if (args.isEmpty() || args.contentEquals(arrayOf("--help"))) {
        output.println("Suite DNIe - firma privada de escritorio")
        output.println("demo --workspace <carpeta> --input <archivo> [--input <otro>]")
        output.println("remote --pc <IP privada PC> --phone <IP teléfono> --workspace <carpeta> --input <archivo> [--input <otro>]")
        output.println("suite --workspace <carpeta> --identity <archivo DPAPI> [--pc <IP>] [--phone <IP/nombre Tailscale>] (red automática; terminal interactiva)")
        output.println("PDF: invisible por defecto; --pdf-mode visible agrega sello; detached produce .p7s.")
        output.println("Sello: --pdf-page N --pdf-box left,top,width,height (fracciones) --pdf-style card|minimal")
        output.println("Sello de tiempo: --pdf-tsa reniec (PDF integrado, requiere internet; sin sello por defecto).")
        output.println("Campos: --pdf-fields dni,date,time,reason,location --pdf-reason texto --pdf-location texto")
        output.println("SIMULACION: certificado y clave temporales; no utiliza el DNI ni PC/SC.")
        return 0
    }
    if (args.firstOrNull() == "suite") return try {
        pe.gob.reniec.dnie.desktop.continuous.ContinuousSuiteCommand.execute(args, output)
    } catch (_: Exception) { errors.println("Suite continua finalizada o solicitud invalida; no se repite ninguna firma."); 1 }
    val remote = try { if (args.firstOrNull() == "remote") pe.gob.reniec.dnie.desktop.remote.RemoteOptions.parse(args) else null } catch (_: Exception) {
        errors.println("Solicitud remota inválida. No se aceptan PIN/CAN como argumentos.")
        return 2
    }
    val request = try { remote?.request ?: CliOptions.parse(args) } catch (_: Exception) {
        errors.println("Solicitud inválida. Consulte --help. No se aceptan PIN/CAN como argumentos.")
        return 2
    }
    return try {
        if (remote == null) output.println("SIMULACION: estos resultados no están firmados con un DNI.")
        val result = runBlocking {
            if (remote != null) pe.gob.reniec.dnie.desktop.remote.RemoteSigningCommand.execute(remote, output)
            else DesktopSigningService().execute(request, SimulatedSigningCard(), SimulatedSigningCard.DEMO_PIN, SigningBackend.SIMULATED)
        }
        output.println("Trabajo: ${result.jobId}")
        output.println("Resultados: ${result.directory}")
        output.println("Completos: ${result.documents.count { it.signature != null }} / ${result.documents.size}")
        if (result.complete) 0 else 1
    } catch (e: Exception) {
        // Paths, document contents and credential-bearing exception messages are not diagnostics.
        errors.println("La operación no terminó (${e.javaClass.simpleName}).")
        1
    }
}
