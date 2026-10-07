package pe.gob.reniec.dnie.signing.link

import java.io.*
import java.util.UUID

enum class SuitePacketKind { BEGIN, READY, FRAME, REPLY, END, ENDED }
class SuitePacket(val kind: SuitePacketKind, val bytes: ByteArray = byteArrayOf()) {
    override fun toString() = "SuitePacket($kind)"
}
/** H2 outer job envelope. PIN is allowed only in BEGIN inside authenticated TLS. */
object SuitePackets {
    private const val MAGIC = 0x444E4932
    private const val MAX = SigningFrames.MAX_PAYLOAD + 64
    fun write(output: OutputStream, packet: SuitePacket) {
        require(packet.bytes.size <= MAX)
        DataOutputStream(output).apply { writeInt(MAGIC); writeByte(packet.kind.ordinal); writeInt(packet.bytes.size); write(packet.bytes); flush() }
    }
    fun read(input: InputStream): SuitePacket? {
        val first = input.read(); if (first < 0) return null
        val data = DataInputStream(input)
        require(((first shl 24) or (data.readUnsignedByte() shl 16) or (data.readUnsignedByte() shl 8) or data.readUnsignedByte()) == MAGIC)
        val kind = SuitePacketKind.entries.getOrNull(data.readUnsignedByte()) ?: error("Invalid suite packet")
        val size = data.readInt(); require(size in 0..MAX)
        return SuitePacket(kind, ByteArray(size).also(data::readFully))
    }
    fun begin(id: UUID, count: Int, pin: CharArray): SuitePacket {
        require(count in 1..50 && pin.size in 4..8 && pin.all { it in '0'..'9' })
        val bytes = java.nio.ByteBuffer.allocate(21 + pin.size).apply {
            putLong(id.mostSignificantBits); putLong(id.leastSignificantBits); putInt(count); put(pin.size.toByte())
            pin.forEach { put(it.code.toByte()) }
        }.array()
        return SuitePacket(SuitePacketKind.BEGIN, bytes)
    }

    class Job(val id: UUID, val count: Int, val pin: CharArray) : AutoCloseable {
        override fun close() { pin.fill('\u0000') }
        override fun toString() = "SuiteJob($count)"
    }
    fun job(packet: SuitePacket): Job {
        require(packet.kind == SuitePacketKind.BEGIN)
        val input = DataInputStream(ByteArrayInputStream(packet.bytes))
        val id = UUID(input.readLong(), input.readLong()); val count = input.readInt(); val size = input.readUnsignedByte()
        require(count in 1..50 && size in 4..8 && input.available() == size)
        val pin = CharArray(size) { input.readUnsignedByte().toChar() }
        if (pin.any { it !in '0'..'9' }) { pin.fill('\u0000'); error("Invalid PIN format") }
        return Job(id, count, pin)
    }
    fun frame(kind: SuitePacketKind, frame: SigningFrame): SuitePacket =
        SuitePacket(kind, ByteArrayOutputStream().also { SigningFrames.write(it, frame) }.toByteArray())
    fun frame(packet: SuitePacket): SigningFrame {
        require(packet.kind == SuitePacketKind.FRAME || packet.kind == SuitePacketKind.REPLY)
        val input = ByteArrayInputStream(packet.bytes)
        val frame = SigningFrames.read(input) ?: error("Missing frame")
        require(input.available() == 0); return frame
    }
}
