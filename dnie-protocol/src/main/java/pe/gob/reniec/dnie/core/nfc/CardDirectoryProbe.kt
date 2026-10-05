package pe.gob.reniec.dnie.core.nfc

import pe.gob.reniec.dnie.core.apdu.ApduConstants
import pe.gob.reniec.dnie.core.apdu.CommandApdu
import pe.gob.reniec.dnie.core.apdu.ResponseApdu
import pe.gob.reniec.dnie.core.logging.ApduLogger
import java.io.ByteArrayOutputStream

data class DirectoryApplication(val aid: ByteArray, val label: String?, val path: ByteArray?)

/** Read-only discovery. A selected application is not automatically a personal PKI applet. */
internal class CardDirectoryProbe(
    private val send: (CommandApdu, String) -> ResponseApdu,
    private val logger: ApduLogger
) {
    fun explore(): List<DirectoryApplication> {
        logger.logInfo("=== SM DESCUBRIMIENTO: EF.DIR y DFs bajo Master File tras PACE ===")
        val applications = if (selectMf()) readDirectory() else emptyList()
        applications.forEach { app ->
            logger.logInfo("EF.DIR anuncia AID=${app.aid.hex()}, nombre=${app.label ?: "sin etiqueta"}, ruta=${app.path?.hex() ?: "no anunciada"}. Compatibilidad PKI pendiente de verificar.")
            send(CommandApdu.selectDfByName(app.aid), "SM DESCUBRIMIENTO: SELECT AID anunciado ${app.aid.hex()}")
        }
        // Reset the parent before each independent DF; a successful SELECT changes the context.
        for ((name, id) in ApduConstants.CANDIDATE_DFS) {
            if (!selectMf()) break
            var selected = send(CommandApdu.selectDf(id), "SM DESCUBRIMIENTO: SELECT DF $name bajo MF")
            if (!selected.isSuccess) {
                selected = send(CommandApdu.selectDfDirect(id), "SM DESCUBRIMIENTO: SELECT DF directo $name bajo MF")
            }
            if (selected.isSuccess) {
                for (ef in listOf("5031", "5032", "5033", "001D", "001C", "2001", "2000", "341F", "3401", "C001")) {
                    send(CommandApdu.selectEf(ef.chunked(2).map { it.toInt(16).toByte() }.toByteArray()),
                        "SM DESCUBRIMIENTO: SELECT EF $ef bajo DF $name")
                }
            }
        }
        logger.logInfo("SM DESCUBRIMIENTO: ${applications.size} AIDs anunciados en EF.DIR. Una selección exitosa no confirma capacidad de firma personal.")
        return applications
    }

    private fun selectMf(): Boolean = send(CommandApdu.selectMasterFile(), "SM DESCUBRIMIENTO: SELECT MF 3F00").isSuccess

    private fun readDirectory(): List<DirectoryApplication> {
        val selected = send(CommandApdu.selectEfDirect(byteArrayOf(0x2F, 0x00)), "SM DESCUBRIMIENTO: SELECT EF.DIR 2F00")
        if (!selected.isSuccess) {
            logger.logInfo("EF.DIR no accesible en este contexto (SW=${selected.statusWordHex}); esto no demuestra ausencia de PKI.")
            return emptyList()
        }
        val data = ByteArrayOutputStream()
        var records = false
        // Bound time, file size and number of records while reserving SM response overhead.
        for (index in 0 until 20) {
            if (data.size() >= 4096) {
                logger.logInfo("EF.DIR supera el límite diagnóstico de 4096 bytes; lectura incompleta.")
                return emptyList()
            }
            fun command(le: Int) = if (records) CommandApdu(0, 0xB2.toByte(), (index + 1).toByte(), 0x04, le)
                else CommandApdu.readBinary(data.size(), le)
            var response = send(command(223), "SM DESCUBRIMIENTO: READ ${if (records) "RECORD ${index + 1}" else "BINARY offset ${data.size()}"} EF.DIR")
            if (index == 0 && response.sw in setOf(0x6981, 0x6A81, 0x6D00)) {
                records = true
                response = send(command(223), "SM DESCUBRIMIENTO: READ RECORD 1 EF.DIR (estructura por registros)")
            }
            if (response.sw1 == 0x6C) {
                val length = if (response.sw2 == 0) 256 else response.sw2
                if (length > 223) {
                    logger.logInfo("EF.DIR requiere Le=$length; supera el límite de esta APDU segura corta. Lectura incompleta.")
                    return emptyList()
                }
                response = send(command(length), "SM DESCUBRIMIENTO: READ EF.DIR reintento Le=$length")
            }
            if (response.isSuccess || response.sw == 0x6282) {
                if (data.size() + response.data.size > 4096) return emptyList()
                data.write(response.data)
                if (response.sw == 0x6282 || response.data.isEmpty() || (!records && response.data.size < 223)) break
            } else if (records && response.sw == 0x6A83) {
                break
            } else {
                logger.logInfo("EF.DIR lectura incompleta (SW=${response.statusWordHex}); no se interpretan datos parciales.")
                return emptyList()
            }
            if (index == 19) {
                logger.logInfo("EF.DIR alcanzó el límite diagnóstico de lecturas; no se interpretan datos parciales.")
                return emptyList()
            }
        }
        logger.logInfo("EF.DIR leído: ${data.size()} bytes, estructura ${if (records) "por registros" else "transparente"}.")
        return try {
            parseApplications(data.toByteArray())
        } catch (e: IllegalArgumentException) {
            logger.logInfo("EF.DIR no interpretable: ${e.message}")
            emptyList()
        }
    }

    companion object {
        private data class Tlv(val tag: Int, val value: ByteArray)
        private fun tlvs(bytes: ByteArray): List<Tlv> {
            val result = mutableListOf<Tlv>()
            var i = 0
            while (i < bytes.size) {
                var tag = bytes[i++].toInt() and 255
                if (tag == 0 || tag == 255) continue // erased/padding octets between templates
                if (tag and 31 == 31) {
                    require(i < bytes.size) { "Tag truncado" }
                    val second = bytes[i++].toInt() and 255
                    require(second and 128 == 0) { "Tag largo no soportado" }
                    tag = (tag shl 8) or second
                }
                require(i < bytes.size) { "Longitud truncada" }
                val first = bytes[i++].toInt() and 255
                val count = if (first < 128) 0 else first and 127
                require(first < 128 || count in 1..2) { "Longitud BER inválida" }
                require(i + count <= bytes.size) { "Longitud truncada" }
                var length = if (count == 0) first else 0
                repeat(count) { length = (length shl 8) or (bytes[i++].toInt() and 255) }
                require(length <= bytes.size - i) { "Valor TLV truncado" }
                result.add(Tlv(tag, bytes.copyOfRange(i, i + length)))
                i += length
            }
            return result
        }

        fun parseApplications(bytes: ByteArray): List<DirectoryApplication> {
            require(bytes.size <= 4096)
            val result = mutableListOf<DirectoryApplication>()
            fun visit(data: ByteArray, depth: Int) {
                require(depth <= 8) { "Plantillas demasiado anidadas" }
                for (tlv in tlvs(data)) {
                    if (tlv.tag == 0x61) {
                        val fields = tlvs(tlv.value)
                        val aids = fields.filter { it.tag == 0x4F }
                        require(aids.size <= 1) { "AID duplicado en plantilla" }
                        val aid = aids.firstOrNull()?.value ?: continue
                        require(aid.size in 5..16) { "Longitud de AID inválida" }
                        if (result.none { it.aid.contentEquals(aid) }) {
                            require(result.size < 32) { "Demasiadas aplicaciones" }
                            val label = fields.firstOrNull { it.tag == 0x50 }?.value?.toString(Charsets.UTF_8)
                                ?.replace(Regex("[\\p{Cntrl}\\s]+"), " ")?.take(60)?.trim()
                            val path = fields.firstOrNull { it.tag == 0x51 }?.value
                            result.add(DirectoryApplication(aid, label, path))
                        }
                    } else if ((if (tlv.tag > 255) tlv.tag shr 8 else tlv.tag) and 0x20 != 0) {
                        visit(tlv.value, depth + 1)
                    }
                }
            }
            visit(bytes, 0)
            return result
        }
    }
}

private fun ByteArray.hex() = joinToString("") { "%02X".format(it) }
