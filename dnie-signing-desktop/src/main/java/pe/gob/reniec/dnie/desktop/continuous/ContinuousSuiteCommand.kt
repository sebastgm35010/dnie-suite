package pe.gob.reniec.dnie.desktop.continuous

import java.io.Console
import java.io.PrintStream
import java.nio.file.*
import java.util.UUID
import kotlinx.coroutines.runBlocking
import pe.gob.reniec.dnie.desktop.application.DesktopSigningService
import pe.gob.reniec.dnie.desktop.model.*
import pe.gob.reniec.dnie.desktop.remote.ReverseTlsExchange
import pe.gob.reniec.dnie.desktop.storage.DesktopWorkspace
import pe.gob.reniec.dnie.signing.link.*

/** Interactive adapter only; PIN uses Console.readPassword, never argv, echo or persisted queue. */
object ContinuousSuiteCommand {
    fun execute(args: Array<String>, output: PrintStream): Int {
        val options=ContinuousSuiteOptions.parse(args)
        val pdf = options.pdf
        val pdfMode=pdf.mode; val pdfOptions=pdf.build()
        val phone = options.phoneHint
        val pc = pe.gob.reniec.dnie.desktop.remote.SuiteNetworkAddress.choose(options.pcHint,phone)
        require(SigningProvision.privateAddress(pc))
        require(java.net.NetworkInterface.getByInetAddress(java.net.InetAddress.getByName(pc)) != null)
        val workspace = options.workspace
        val console: Console = System.console() ?: error("Use an interactive terminal; PIN is never read from redirected input")
        val identityPath = options.identity
        val firstEnrollment = !Files.exists(identityPath)
        SuitePcIdentity.open(identityPath, pc).use { identity ->
            Files.createDirectories(workspace)
            val export = workspace.resolve("suite-emparejamiento.txt")
            Files.deleteIfExists(export)
            Files.createFile(export); WindowsPrivateStore.restrict(export)
            Files.writeString(export, identity.pairing.encode())
            output.println("Emparejamiento permanente TXT/QR: $export")
            output.println("Huella TLS: ${identity.pairing.fingerprintText()}")
            if (firstEnrollment) {
                if (!SuitePairingPresenter.openQr(export)) output.println("No se pudo abrir QR automaticamente; puedes importar el TXT.")
            } else output.println("PC ya recordada. No necesitas un QR nuevo; usa Conectar PC guardada en Android.")
            output.println("En Android, recuerda esta PC y abre Suite continua. En conexiones posteriores usa Conectar PC guardada.")
            identity.pairing.provision().use { provision ->
                val transport = ReverseTlsExchange(phone, provision, identity.tls, continuous = true)
                val shutdown = Thread({ transport.close() }, "suite-continuous-shutdown")
                Runtime.getRuntime().addShutdownHook(shutdown)
                try {
                    val client = runBlocking { ContinuousSigningClient(transport.openChannel()) }
                    client.use {
                        Files.deleteIfExists(export)
                        Files.deleteIfExists(export.resolveSibling("suite-emparejamiento.png"))
                        if (pdfOptions.reniecTimestamp) output.println("TSA RENIEC: sello verificado obligatorio. Si falla, no se publica el PDF ni se repite la firma del DNI.")
                        output.println(if (pdfMode == DesktopPdfMode.VISIBLE) "PDF: firma integrada con sello visible (_firmado.pdf). Otros archivos: CMS separado (.p7s)." else if (pdfMode == DesktopPdfMode.INVISIBLE) "PDF: firma integrada invisible (_firmado.pdf). Otros archivos: CMS separado (.p7s)." else "PDF: CMS separado (.p7s).")
                        output.println("Suite conectada. Un archivo por linea; linea vacia completa lote; salir termina conexion.")
                        while (true) {
                            val paths = mutableListOf<Path>()
                            while (true) {
                                val line = console.readLine("Archivo: ") ?: return 0
                                if (line == "salir") return 0
                                if (line.isBlank()) break
                                if (paths.size == 50) { output.println("Limite de 50 archivos."); continue }
                                paths.add(Path.of(line.trim().removeSurrounding("\"")))
                            }
                            if (paths.isEmpty()) continue
                            val request = DesktopSigningRequest(paths, workspace, pdfMode, pdfOptions)
                            try { DesktopWorkspace.validate(request) } catch (_: Exception) { output.println("Archivos no validos. Lote no enviado."); continue }
                            val pin = console.readPassword("PIN de firma para %d archivo(s): ", paths.size) ?: return 0
                            try {
                                if (pin.size !in 4..8 || pin.any { it !in '0'..'9' }) { output.println("Formato PIN invalido; no enviado al DNI."); continue }
                                val jobId = UUID.randomUUID()
                                output.println("Lote enviado. Acerca el DNI si todavia no esta disponible.")
                                val result = runBlocking {
                                    val exchange = client.begin(jobId, paths.size, pin)
                                    pin.fill('\u0000')
                                    DesktopSigningService().execute(request, PrivateSigningCard(jobId, exchange), "", SigningBackend.PRIVATE_ANDROID)
                                }
                                output.println("Resultados: ${result.directory}")
                                output.println("Completos: ${result.documents.count { it.signature != null }} / ${result.documents.size}")
                                if (!result.complete) output.println("Lote incompleto: no se repite. Revisa resultados antes de iniciar otro.")
                            } catch (_: SuiteCardUnavailableException) {
                                output.println("DNI no disponible. No se inició ninguna firma; Suite sigue conectada. Acerca el DNI y envía una solicitud nueva.")
                            } finally { pin.fill('\u0000') }
                        }
                    }
                } finally {
                    transport.close(); runCatching { Runtime.getRuntime().removeShutdownHook(shutdown) }
                    Files.deleteIfExists(export); Files.deleteIfExists(export.resolveSibling("suite-emparejamiento.png"))
                }
            }
        }
        return 0
    }
}
