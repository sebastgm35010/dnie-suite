package pe.gob.reniec.dnie.core.nfc

import pe.gob.reniec.dnie.core.apdu.CommandApdu

/** Observed RENIEC public-certificate reads after PC PACE. Physical SM belongs
 * to the supplied logical transport; this route never owns or resets its keys. */
internal class PlainCertificateReadRoute {
    private enum class Selection { NONE, IAS, PKI, CERTIFICATE }
    private var selection = Selection.NONE
    private val ias = decode("A000000077030C60000000FE00000500")
    private val pki = decode("E828BD080FD25047656E65726963")
    private fun decode(hex: String) = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    fun reset() { selection = Selection.NONE }

    fun transmit(command: CommandApdu, bytes: ByteArray, logical: (ByteArray) -> ByteArray): ByteArray? {
        if (command.cla != 0.toByte()) { reset(); return null }
        val select = command.ins == 0xa4.toByte() && command.p2 in byteArrayOf(4, 12)
        val next = when {
            select && command.p1 == 4.toByte() && command.data.contentEquals(ias) -> Selection.IAS
            select && command.p1 == 4.toByte() && command.data.contentEquals(pki) && selection == Selection.IAS -> Selection.PKI
            select && command.p1 == 2.toByte() && command.data.size == 2 &&
                command.data[0] == 0xce.toByte() && command.data[1] in byteArrayOf(0x81.toByte(),0x82.toByte(),0x83.toByte()) &&
                selection in listOf(Selection.PKI,Selection.CERTIFICATE) -> Selection.CERTIFICATE
            command.ins == 0xb0.toByte() && command.data.isEmpty() &&
                (command.p1.toInt() and 0x80) == 0 && command.le?.let { it in 1..256 } == true &&
                selection == Selection.CERTIFICATE -> Selection.CERTIFICATE
            else -> { reset(); return null }
        }
        reset()
        val response = logical(bytes)
        require(response.size >= 2)
        val sw = ((response[response.size-2].toInt() and 255) shl 8) or (response.last().toInt() and 255)
        if (sw == 0x9000 || (command.ins == 0xb0.toByte() && sw == 0x6282)) selection = next
        return response
    }
}
