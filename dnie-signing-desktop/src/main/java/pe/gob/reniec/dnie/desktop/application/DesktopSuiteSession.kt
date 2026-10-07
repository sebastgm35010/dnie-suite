package pe.gob.reniec.dnie.desktop.application

import java.nio.file.*
import java.util.UUID
import pe.gob.reniec.dnie.desktop.continuous.*
import pe.gob.reniec.dnie.desktop.model.*
import pe.gob.reniec.dnie.desktop.remote.ReverseTlsExchange
import pe.gob.reniec.dnie.signing.link.*

/** User-mode application boundary. GUI owns scheduling; this owns one private connection, never PC/SC. */
class DesktopSuiteSession(private val port: Int=SigningProvision.PHONE_PORT) : AutoCloseable {
    data class PairingInfo(val file: Path, val fingerprint: String, val firstEnrollment: Boolean)
    @Volatile private var closed=false
    @Volatile private var transport: ReverseTlsExchange?=null
    private var identity: SuitePcIdentity?=null
    private var client: ContinuousSigningClient?=null
    private var export: Path?=null
    suspend fun connect(pc: String, phone: String, workspace: Path, identityFile: Path, onPairing: (PairingInfo)->Unit) {
        check(!closed && identity==null)
        require(SigningProvision.privateAddress(pc) && SigningProvision.privateAddress(phone))
        require(java.net.NetworkInterface.getByInetAddress(java.net.InetAddress.getByName(pc))!=null)
        val first=!Files.exists(identityFile)
        val own=SuitePcIdentity.open(identityFile,pc); identity=own
        try {
            check(!closed)
            Files.createDirectories(workspace)
            val file=workspace.resolve("suite-emparejamiento.txt"); export=file
            Files.deleteIfExists(file); Files.createFile(file); WindowsPrivateStore.restrict(file)
            Files.writeString(file,own.pairing.encode())
            onPairing(PairingInfo(file,own.pairing.fingerprintText(),first))
            own.pairing.provision().use { provision ->
                val connection=ReverseTlsExchange(phone,provision,own.tls,port,continuous=true); transport=connection
                check(!closed)
                client=ContinuousSigningClient(connection.openChannel())
                check(!closed)
            }
            deletePairing()
        } catch(e: Exception) { close(); throw e }
    }
    suspend fun sign(request: DesktopSigningRequest, pin: CharArray): DesktopSigningResult {
        try {
            check(!closed)
            DesktopWorkspaceValidation.validate(request)
            require(pin.size in 4..8 && pin.all { it in '0'..'9' })
            val active=checkNotNull(client)
            val job=UUID.randomUUID()
            val exchange=active.begin(job,request.inputs.size,pin)
            pin.fill('\u0000')
            return DesktopSigningService().execute(request,PrivateSigningCard(job,exchange),"",SigningBackend.PRIVATE_ANDROID)
        } finally { pin.fill('\u0000') }
    }
    private fun deletePairing() {
        export?.let { Files.deleteIfExists(it); Files.deleteIfExists(it.resolveSibling("suite-emparejamiento.png")) }
    }
    override fun close() {
        closed=true
        transport?.close()
        // No protocol CLOSE during an in-flight signing operation: transport aborts; never replay.
        identity?.close(); runCatching { deletePairing() }
    }
}
private object DesktopWorkspaceValidation {
    fun validate(request: DesktopSigningRequest)=pe.gob.reniec.dnie.desktop.storage.DesktopWorkspace.validate(request)
}
