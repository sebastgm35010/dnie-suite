package pe.gob.reniec.dnie.core.nfc

import pe.gob.reniec.dnie.core.apdu.CommandApdu
import pe.gob.reniec.dnie.core.apdu.ResponseApdu
import pe.gob.reniec.dnie.core.logging.ApduLogger
import pe.gob.reniec.dnie.crypto.X509CertificateParser
import java.io.ByteArrayOutputStream

/** Read-only profile from RENIEC manual 2025_0014, 2025-07-02, sections 4.1–4.4.
 * https://identidad.reniec.gob.pe/documents/d/guest/manual_comandos_dnie_v3
 * Interface availability is established by the card responses, not assumed from the manual.
 */
internal class V3PkiProbe(
    private val send: (CommandApdu, String) -> ResponseApdu,
    private val logger: ApduLogger
) {
    fun explore(context: String): Map<String, ByteArray> {
        logger.logInfo("=== PKI V3 OFICIAL: $context; solo selección y lectura, sin PIN ===")
        if (!select(4, 12, IAS_AID, "$context: SELECT IAS v3 oficial")) return emptyMap()
        if (!select(4, 12, PKI_AID, "$context: SELECT ADF PKI dentro de IAS")) return emptyMap()
        return exploreSelectedPki(context)
    }

    fun exploreSelectedPki(context: String): Map<String, ByteArray> {
        val certificates = linkedMapOf<String, ByteArray>()
        for ((file, name) in listOf("CE82" to "Firma", "CE81" to "Autenticación", "CE83" to "Cifrado")) {
            val bytes = readFile(file, name, context) ?: continue
            certificates[file] = bytes
        }
        logger.logInfo("PKI V3 OFICIAL: ${certificates.size} certificados X.509 completos en $context.")
        return certificates
    }

    fun readFile(file: String, name: String, context: String): ByteArray? {
        // P2=0C is the NFC profile used for certificate selection; no FCP requested.
        if (!select(2, 12, hex(file), "$context: SELECT EF $file ($name)")) return null
        val bytes = readCertificate("$context: EF $file ($name)") ?: return null
        try {
            val cert = X509CertificateParser.toX509Certificate(bytes)
            logger.logInfo("PKI V3 OFICIAL: $file X.509 completo (${bytes.size} bytes); " +
                "subject=${cert.subjectX500Principal.name}; issuer=${cert.issuerX500Principal.name}. " +
                "Lectura confirmada; capacidad de firma aún no probada.")
            return bytes
        } catch (e: Exception) {
            logger.logInfo("PKI V3 OFICIAL: $file no contiene un X.509 válido: ${e.message}")
            return null
        }
    }

    private fun select(p1: Int, p2: Int, data: ByteArray, label: String): Boolean {
        var response = send(CommandApdu(0, 0xA4.toByte(), p1.toByte(), p2.toByte(), data), label)
        // SELECT with FCP may return 61xx. Consume it before selecting another file.
        repeat(8) {
            if (response.sw1 != 0x61) return@repeat
            response = send(CommandApdu(0, 0xC0.toByte(), 0, 0,
                le = minOf(if (response.sw2 == 0) 256 else response.sw2, 223)), "$label: GET RESPONSE")
        }
        if (!response.isSuccess) logger.logInfo("PKI V3 OFICIAL: $label detenido con ${response.statusWordHex}; " +
            "no confirma ausencia de PKI en otras condiciones de acceso.")
        return response.isSuccess
    }

    private fun readCertificate(label: String): ByteArray? {
        val output = ByteArrayOutputStream()
        var expected: Int? = null
        repeat(40) {
            val offset = output.size()
            var command = CommandApdu.readBinary(offset, le = minOf(223, expected?.minus(offset) ?: 223))
            var response = send(command, "$label: READ BINARY offset=%04X".format(offset))
            if (response.sw1 == 0x6C) {
                val required = if (response.sw2 == 0) 256 else response.sw2
                if (required > 223) {
                    logger.logInfo("PKI V3 OFICIAL: $label requiere bloque $required; lectura incompleta.")
                    return null
                }
                command = CommandApdu.readBinary(offset, le = required)
                response = send(command, "$label: READ BINARY longitud corregida")
            }
            if (!response.isSuccess && response.sw != 0x6282) {
                logger.logInfo("PKI V3 OFICIAL: $label lectura incompleta, SW=${response.statusWordHex}.")
                return null
            }
            output.write(response.data)
            val bytes = output.toByteArray()
            if (expected == null && bytes.size >= 2) {
                if (bytes[0] != 0x30.toByte()) {
                    logger.logInfo("PKI V3 OFICIAL: $label no comienza con una secuencia DER X.509.")
                    return null
                }
                val first = bytes[1].toInt() and 255
                val count = if (first < 128) 0 else first and 127
                if (first == 128 || count > 2) return null
                if (bytes.size >= 2 + count) {
                    var length = if (count == 0) first else 0
                    repeat(count) { i -> length = (length shl 8) or (bytes[2 + i].toInt() and 255) }
                    expected = 2 + count + length
                    if (expected!! !in 3..8192) return null
                }
            }
            if (expected != null && bytes.size >= expected!!) return bytes.copyOf(expected!!)
            if (response.sw == 0x6282 || response.data.isEmpty()) {
                logger.logInfo("PKI V3 OFICIAL: $label truncado: ${bytes.size}/${expected ?: "?"} bytes.")
                return null
            }
        }
        logger.logInfo("PKI V3 OFICIAL: $label excedió el límite de lectura; certificado incompleto.")
        return null
    }

    companion object {
        private fun hex(value: String) = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val IAS_AID = hex("A000000077030C60000000FE00000500")
        val PKI_AID = hex("E828BD080FD25047656E65726963")
    }
}
