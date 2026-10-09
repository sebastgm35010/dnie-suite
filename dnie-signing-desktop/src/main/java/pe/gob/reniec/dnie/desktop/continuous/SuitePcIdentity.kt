package pe.gob.reniec.dnie.desktop.continuous

import java.io.*
import java.nio.file.*
import java.security.SecureRandom
import java.util.UUID
import pe.gob.reniec.dnie.desktop.remote.JobTlsIdentity
import pe.gob.reniec.dnie.signing.link.SuitePairing

/** One user-owned PC identity, encrypted DPAPI; separate from the SYSTEM bridge identity. */
class SuitePcIdentity(val tls: JobTlsIdentity, val pairing: SuitePairing) : AutoCloseable {
    override fun close() = pairing.close()
    companion object {
        fun open(file: Path, pc: String): SuitePcIdentity {
            val store = WindowsPrivateStore()
            if (Files.exists(file)) {
                require(Files.size(file) in 1..65536)
                val plain = store.unprotect(Files.readAllBytes(file))
                try {
                    val input = DataInputStream(ByteArrayInputStream(plain))
                    require(input.readInt() == 0x44535032)
                    val pairSize = input.readInt(); require(pairSize in 1..1024)
                    val pair = SuitePairing.parse(String(ByteArray(pairSize).also(input::readFully), Charsets.UTF_8))
                    try {
                        val size = input.readInt(); require(size in 1..32768 && input.available() == size)
                        val p12 = ByteArray(size).also(input::readFully)
                        val identity = try { JobTlsIdentity.restore(p12) } finally { p12.fill(0) }
                        require(java.security.MessageDigest.isEqual(pair.fingerprint, identity.fingerprint))
                        // Keep the same DPAPI identity/key/token. Only the current route changes.
                        return SuitePcIdentity(identity, SuitePairing(pair.id, pc, pair.fingerprint, pair.token))
                    } finally { pair.close() }
                } finally { plain.fill(0) }
            }
            Files.createDirectories(file.toAbsolutePath().parent)
            val tls = JobTlsIdentity()
            val pairing = SuitePairing(UUID.randomUUID(), pc, tls.fingerprint, ByteArray(32).also(SecureRandom()::nextBytes))
            try {
                val raw = ByteArrayOutputStream()
                val pair = pairing.encode().toByteArray(Charsets.UTF_8); val p12 = tls.encode()
                try { DataOutputStream(raw).apply { writeInt(0x44535032); writeInt(pair.size); write(pair); writeInt(p12.size); write(p12) } }
                finally { pair.fill(0); p12.fill(0) }
                val plain = raw.toByteArray()
                val protected = try { store.protect(plain) } finally { plain.fill(0); raw.reset() }
                Files.createFile(file)
                WindowsPrivateStore.restrict(file)
                Files.write(file, protected)
                return SuitePcIdentity(tls, pairing)
            } catch (e: Exception) { pairing.close(); throw e }
        }
    }
}
