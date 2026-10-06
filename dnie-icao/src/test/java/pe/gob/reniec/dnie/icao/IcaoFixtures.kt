package pe.gob.reniec.dnie.icao

import pe.gob.reniec.dnie.core.apdu.CommandApdu
import pe.gob.reniec.dnie.core.apdu.ResponseApdu
import pe.gob.reniec.dnie.core.nfc.DnieCardChannel
import java.io.IOException
import java.nio.ByteBuffer

internal fun tlv(tag: Int, value: ByteArray): ByteArray {
    val tags = if (tag > 255) byteArrayOf((tag ushr 8).toByte(), tag.toByte()) else byteArrayOf(tag.toByte())
    val len = when {
        value.size < 128 -> byteArrayOf(value.size.toByte())
        value.size <= 255 -> byteArrayOf(0x81.toByte(), value.size.toByte())
        value.size <= 65535 -> byteArrayOf(0x82.toByte(), (value.size ushr 8).toByte(), value.size.toByte())
        else -> byteArrayOf(0x83.toByte(), (value.size ushr 16).toByte(), (value.size ushr 8).toByte(), value.size.toByte())
    }
    return tags + len + value
}

internal val td1 = "I<UTOD231458907<<<<<<<<<<<<<<<\n7408122F1204159UTO<<<<<<<<<<<6\nERIKSSON<<ANNA<MARIA<<<<<<<<<<"
internal fun dg1(mrz: String = td1) = tlv(0x61, tlv(0x5F1F, mrz.replace("\n", "").toByteArray()))
internal fun inventory(vararg tags: Int): ByteArray = tlv(0x60, tlv(0x5F01, "0107".toByteArray()) + tlv(0x5C, tags.map { it.toByte() }.toByteArray()))
internal val jpeg = byteArrayOf(-1, -40, -1, -32, 0, 0, -1, -39)
internal val jp2 = byteArrayOf(0, 0, 0, 12, 0x6A, 0x50, 0x20, 0x20, 13, 10, -121, 10)
internal fun face(image: ByteArray = jpeg, features: Int = 0): ByteArray {
    val block = ByteBuffer.allocate(32 + features * 8 + image.size)
    block.putInt(block.capacity()).putShort(features.toShort())
    block.position(20 + features * 8)
    block.put(1).put(if (image === jp2) 1 else 0).putShort(120).putShort(160)
    block.position(32 + features * 8)
    block.put(image)
    val record = ByteBuffer.allocate(14 + block.capacity())
        .put(byteArrayOf(0x46, 0x41, 0x43, 0)).put("010\u0000".toByteArray())
        .putInt(14 + block.capacity()).putShort(1).put(block.array()).array()
    return tlv(0x75, tlv(0x7F61, tlv(0x02, byteArrayOf(1)) + tlv(0x7F60, tlv(0x5F2E, record))))
}

internal class FilesChannel(
    val files: Map<Int, ByteArray>,
    val statuses: Map<Int, Int> = emptyMap(),
    val disconnectOn: Int? = null,
    val maxChunk: Int = 223
) : DnieCardChannel {
    val commands = mutableListOf<CommandApdu>()
    var selected = 0
    var closed = false
    override fun transceive(command: ByteArray): ByteArray {
        val c = CommandApdu.fromBytes(command)
        commands += c
        if (c.ins == 0xA4.toByte()) {
            if (c.p1 == 4.toByte() || c.data.contentEquals(byteArrayOf(0x3F, 0))) return ResponseApdu.success().toByteArray()
            selected = ((c.data[0].toInt() and 255) shl 8) or (c.data[1].toInt() and 255)
            if (selected == disconnectOn) throw IOException("NFC perdido")
            return ResponseApdu.error(statuses[selected] ?: if (selected in files) 0x9000 else 0x6A82).toByteArray()
        }
        val data = files.getValue(selected)
        val odd = c.ins == 0xB1.toByte()
        val offset = if (odd) c.data.drop(2).fold(0) { n, b -> (n shl 8) or (b.toInt() and 255) }
            else ((c.p1.toInt() and 127) shl 8) or (c.p2.toInt() and 255)
        val length = minOf(maxChunk, (c.le ?: 223) - if (odd) { if ((c.le ?: 223) <= 129) 2 else 3 } else 0, data.size - offset)
        if (length <= 0) return ResponseApdu.error(0x6282).toByteArray()
        val chunk = data.copyOfRange(offset, offset + length)
        return ResponseApdu.success(if (odd) tlv(0x53, chunk) else chunk).toByteArray()
    }
    override fun isConnected() = !closed
    override fun setTimeout(timeoutMs: Int) {}
    override fun close() { closed = true }
}
