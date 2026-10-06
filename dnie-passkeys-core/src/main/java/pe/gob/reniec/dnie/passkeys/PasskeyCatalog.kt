package pe.gob.reniec.dnie.passkeys

import java.io.*

class PasskeyRecord(val id: ByteArray, val rpId: String, val userId: ByteArray, val username: String,
    val displayName: String, val privateKey: ByteArray, val publicKey: ByteArray,
    val backupEligible: Boolean = false, var backedUp: Boolean = false) {
    init { require(!backedUp || backupEligible) }
    val encodedId get() = PasskeyEncoding.encode(id)
    fun destroy() { privateKey.fill(0) }
    override fun toString() = "PasskeyRecord(redacted)"
}

/** Serialized only inside an authenticated encrypted envelope. No public RP or account index. */
class PasskeyCatalog(val records: MutableList<PasskeyRecord> = mutableListOf()) : AutoCloseable {
    fun matching(options: PasskeyGetOptions) = records.filter {
        it.rpId == options.rpId && (options.allowed.isEmpty() || it.encodedId in options.allowed)
    }
    override fun close() { records.forEach(PasskeyRecord::destroy); records.clear() }
    fun encode(): ByteArray {
        require(records.size <= MAX_RECORDS)
        val output = object : ByteArrayOutputStream() { fun wipe() { buf.fill(0); reset() } }
        try {
            DataOutputStream(output).apply {
                writeInt(2); writeInt(records.size)
                records.forEach { record ->
                    blob(record.id); writeUTF(record.rpId); blob(record.userId)
                    writeUTF(record.username); writeUTF(record.displayName)
                    blob(record.privateKey); blob(record.publicKey)
                    require(!record.backedUp || record.backupEligible)
                    writeBoolean(record.backupEligible); writeBoolean(record.backedUp)
                }
            }
            require(output.size() <= MAX_BYTES)
            return output.toByteArray()
        } finally { output.wipe() }
    }
    companion object {
        const val MAX_RECORDS = 1000
        const val MAX_BYTES = 1024 * 1024
        fun decode(bytes: ByteArray): PasskeyCatalog {
            require(bytes.size <= MAX_BYTES)
            val catalog = PasskeyCatalog()
            try {
                DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                    val version = input.readInt().also { require(it in 1..2) }
                    val count = input.readInt().also { require(it in 0..MAX_RECORDS) }
                    val ids = mutableSetOf<String>()
                    repeat(count) {
                        val id = input.blob(32).also { require(it.size == 32 && ids.add(PasskeyEncoding.encode(it))) }
                        val rpId = PasskeyJson.rpId(input.readUTF())
                        val userId = input.blob(64)
                        val username = input.readUTF().also { require(it.toByteArray().size in 1..256) }
                        val displayName = input.readUTF().also { require(it.toByteArray().size in 1..256) }
                        val privateKey = input.blob(1024)
                        try {
                            val publicKey = input.blob(256)
                            val eligible = version == 2 && input.readBoolean()
                            val backedUp = version == 2 && input.readBoolean()
                            catalog.records += PasskeyRecord(id, rpId, userId, username, displayName, privateKey, publicKey, eligible, backedUp)
                        } catch (failure: Throwable) { privateKey.fill(0); throw failure }
                    }
                    require(input.read() == -1)
                }
                return catalog
            } catch (failure: Throwable) { catalog.close(); throw failure }
        }
        private fun DataInputStream.blob(maximum: Int): ByteArray {
            val size = readInt().also { require(it in 1..maximum) }
            return ByteArray(size).also(::readFully)
        }
        private fun DataOutputStream.blob(bytes: ByteArray) { writeInt(bytes.size); write(bytes) }
    }
}
