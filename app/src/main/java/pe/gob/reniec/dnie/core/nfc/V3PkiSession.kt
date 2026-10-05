package pe.gob.reniec.dnie.core.nfc

import pe.gob.reniec.dnie.core.apdu.ApduConstants
import pe.gob.reniec.dnie.core.apdu.CommandApdu
import pe.gob.reniec.dnie.core.apdu.ResponseApdu
import pe.gob.reniec.dnie.core.crypto.PaceSecurityInfo

/** IAS/PKI NFC flow verified in the installed Certezia 0.6.6 binary.
 * The applet AIDs and signing commands also match RENIEC manual 2025_0014.
 * PACE must run after the plain IAS/PKI selections, without switching to ICAO/MF.
 */
internal object V3PkiSession {
    val paceProfile = PaceSecurityInfo("0.4.0.127.0.7.2.2.4.4.4",
        ApduConstants.OID_PACE_ECDH_IM_AES256, 2, 13)

    fun open(
        can: String?,
        send: (CommandApdu, String) -> ResponseApdu,
        authenticate: (PaceSecurityInfo) -> Unit
    ): Boolean {
        val ias = send(select(V3PkiProbe.IAS_AID), "V3: SELECT IAS en claro (P2=0C)")
        if (ias.sw == 0x6A82 || ias.sw == 0x6A86) return false
        if (!ias.isSuccess) throw CardProtocolException("No se pudo seleccionar IAS v3", ias.sw)
        val pki = send(select(V3PkiProbe.PKI_AID), "V3: SELECT PKI en claro antes de PACE")
        if (!pki.isSuccess) throw CardProtocolException("IAS detectado; selección PKI v3 falló", pki.sw)
        if (can.isNullOrBlank()) throw PaceException("DNIe v3 detectado: ingrese el CAN de 6 dígitos")
        authenticate(paceProfile)
        val securePki = send(select(V3PkiProbe.PKI_AID), "V3 SM: SELECT PKI después de PACE IAS")
        if (!securePki.isSuccess) throw CardProtocolException("PKI v3 rechazado después de PACE IAS", securePki.sw)
        return true
    }

    private fun select(aid: ByteArray) = CommandApdu(0, 0xA4.toByte(), 4, 12, aid)
    fun verifyPin(pin: String): CommandApdu {
        require(pin.length in 4..8 && pin.all { it in '0'..'9' })
        val payload = ByteArray(12) { 0xFF.toByte() }
        pin.toByteArray(Charsets.US_ASCII).copyInto(payload)
        return CommandApdu(0, 0x20, 0, 3, payload)
    }
    fun signatureEnvironment() = CommandApdu(0, 0x22, 0x41, 0xB6.toByte(),
        byteArrayOf(0x84.toByte(), 1, 0x82.toByte(), 0x80.toByte(), 1, 0x42))
    fun sign(hash: ByteArray): CommandApdu {
        require(hash.size == 32)
        return CommandApdu(0, 0x2A, 0x9E.toByte(), 0x9A.toByte(), hash, le = 256)
    }
}
