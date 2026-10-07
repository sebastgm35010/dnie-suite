package pe.gob.reniec.dnie.signing.link

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

enum class SigningOperation(val code: Int) { OPEN(1), CERTIFICATE(2), AUTHORIZE(3), SIGN_SHA256(4), CLOSE(5) }
enum class SigningStatus(val code: Int) { REQUEST(0), OK(1), INVALID_PIN(2), BLOCKED(3), FAILED(4) }

/** Fixed, versioned protocol. No APDU, reset, PIN, CAN, document or private-key field. */
class SigningFrame(val sessionId: UUID, val sequence: Long, val operation: SigningOperation,
                   val status: SigningStatus, payload: ByteArray = byteArrayOf()) {
    val payload = payload.copyOf()
    init { require(sequence > 0 && payload.size <= SigningFrames.MAX_PAYLOAD) }
    override fun toString(): String = "SigningFrame($operation, $status)"
}

object SigningFrames {
    const val MAX_PAYLOAD = 65536
    private const val MAGIC = 0x444E4953 // DNIS, independent from DNIBRDG1/VPCD
    private const val VERSION = 1

    /** Caller bounds incomplete reads via its transport and closes on ANY decoding error. */
    fun read(input: InputStream): SigningFrame? {
        val first = input.read()
        if (first < 0) return null
        val data = DataInputStream(input)
        val magic = (first shl 24) or (data.readUnsignedByte() shl 16) or
            (data.readUnsignedByte() shl 8) or data.readUnsignedByte()
        require(magic == MAGIC && data.readUnsignedByte() == VERSION) { "Invalid signing protocol" }
        val session = UUID(data.readLong(), data.readLong())
        val sequence = data.readLong()
        val operationCode = data.readUnsignedByte()
        val statusCode = data.readUnsignedByte()
        val operation = SigningOperation.entries.singleOrNull { it.code == operationCode }
            ?: error("Invalid signing operation")
        val status = SigningStatus.entries.singleOrNull { it.code == statusCode }
            ?: error("Invalid signing status")
        val size = data.readInt()
        require(sequence > 0 && size in 0..MAX_PAYLOAD) { "Invalid signing frame size" }
        return SigningFrame(session, sequence, operation, status, ByteArray(size).also(data::readFully))
    }

    fun write(output: OutputStream, frame: SigningFrame) {
        DataOutputStream(output).apply {
            writeInt(MAGIC); writeByte(VERSION)
            writeLong(frame.sessionId.mostSignificantBits); writeLong(frame.sessionId.leastSignificantBits)
            writeLong(frame.sequence); writeByte(frame.operation.code); writeByte(frame.status.code)
            writeInt(frame.payload.size); write(frame.payload); flush()
        }
    }
}
