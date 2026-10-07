package pe.gob.reniec.dnie.desktop.remote

import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.attribute.AclEntry
import java.nio.file.attribute.AclEntryPermission
import java.nio.file.attribute.AclEntryType
import java.nio.file.attribute.AclFileAttributeView
import java.nio.file.attribute.PosixFilePermissions
import java.security.SecureRandom
import java.util.EnumSet
import java.util.UUID
import pe.gob.reniec.dnie.desktop.application.DesktopSigningService
import pe.gob.reniec.dnie.desktop.model.DesktopSigningResult
import pe.gob.reniec.dnie.desktop.model.SigningBackend
import pe.gob.reniec.dnie.desktop.storage.DesktopWorkspace
import pe.gob.reniec.dnie.signing.link.PrivateSigningCard
import pe.gob.reniec.dnie.signing.link.SigningProvision

object RemoteSigningCommand {
    suspend fun execute(options: RemoteOptions, output: PrintStream): DesktopSigningResult {
        DesktopWorkspace.validate(options.request)
        require(java.net.NetworkInterface.getByInetAddress(java.net.InetAddress.getByName(options.pc)) != null) { "PC address unavailable" }
        val identity = JobTlsIdentity()
        val provision = SigningProvision(UUID.randomUUID(), options.pc, System.currentTimeMillis() + 300_000,
            options.request.inputs.size, identity.fingerprint, ByteArray(32).also(SecureRandom()::nextBytes))
        val root = Files.createDirectories(options.request.workspaceRoot.resolve("pairings"))
        val file = Files.createFile(root.resolve("${provision.sessionId}.txt"))
        try {
            val acl = Files.getFileAttributeView(file, AclFileAttributeView::class.java)
            if (acl != null) acl.acl = listOf(AclEntry.newBuilder().setType(AclEntryType.ALLOW)
                .setPrincipal(Files.getOwner(file)).setPermissions(EnumSet.allOf(AclEntryPermission::class.java)).build())
            else Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"))
            Files.writeString(file, provision.encode())
            output.println("Solicitud privada de ${provision.count} archivo(s), válida 5 minutos.")
            output.println("Importa en Android → Certificados → Suite, o genera su QR: $file")
            output.println("Huella TLS: ${provision.fingerprintText()}")
            output.println("Esperando autorización y DNI en Android. No se publica lector PC/SC.")
            val exchange = ReverseTlsExchange(options.phone, provision, identity)
            val cancelHook = Thread({ exchange.close() }, "suite-remote-shutdown")
            Runtime.getRuntime().addShutdownHook(cancelHook)
            try {
                return DesktopSigningService().execute(options.request, PrivateSigningCard(provision.sessionId, exchange),
                    "", SigningBackend.PRIVATE_ANDROID)
            } finally {
                exchange.close()
                runCatching { Runtime.getRuntime().removeShutdownHook(cancelHook) }
            }
        } finally {
            provision.close()
            runCatching { Files.deleteIfExists(file) }
            runCatching { Files.deleteIfExists(file.resolveSibling(file.fileName.toString().removeSuffix(".txt") + ".png")) }
        }
    }
}
