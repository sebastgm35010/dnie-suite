package pe.gob.reniec.dnie.icao

import kotlinx.coroutines.ensureActive
import pe.gob.reniec.dnie.core.apdu.CommandApdu
import pe.gob.reniec.dnie.core.apdu.ResponseApdu
import java.io.ByteArrayOutputStream
import kotlin.coroutines.coroutineContext

/** Application status errors are recoverable; transport/SM failures invalidate the session. */
class IcaoStatusException(val sw: Int) : Exception("Estado de tarjeta %04X".format(sw))

class IcaoFileReader(private val send: (CommandApdu, String) -> ResponseApdu) {
    suspend fun read(fileId: ByteArray, expectedTag: Int? = null, title: String, oddFromStart: Boolean = false): ByteArray {
        var selected = send(CommandApdu.selectEf(fileId), "ICAO: SELECT $title")
        if (selected.sw in setOf(0x6A86, 0x6A82)) {
            selected = send(CommandApdu.selectEfDirect(fileId), "ICAO: SELECT directo $title")
        }
        if (!selected.isSuccess) throw IcaoStatusException(selected.sw)
        val output = ByteArrayOutputStream()
        var total: Int? = null
        while (total == null || output.size() < total) {
            coroutineContext.ensureActive()
            val offset = output.size()
            require(offset < BerTlv.MAX_FILE_BYTES) { "Archivo ICAO fuera de límites" }
            val odd = oddFromStart || offset >= 0x8000
            val remaining = total?.minus(offset) ?: 223
            val payloadLength = if (odd) minOf(219, remaining) else minOf(223, remaining, 0x8000 - offset)
            val cmd = if (!odd) CommandApdu.readBinary(offset, payloadLength) else {
                val offsetBytes = if (offset <= 65535) byteArrayOf((offset ushr 8).toByte(), offset.toByte())
                    else byteArrayOf((offset ushr 16).toByte(), (offset ushr 8).toByte(), offset.toByte())
                CommandApdu(0, 0xB1.toByte(), 0, 0,
                    byteArrayOf(0x54, offsetBytes.size.toByte()) + offsetBytes,
                    le = payloadLength + if (payloadLength < 128) 2 else 3)
            }
            var response = send(cmd, "ICAO: READ $title ($offset/${total ?: "?"})")
            if (response.sw1 == 0x6C) {
                response = send(CommandApdu(cmd.cla, cmd.ins, cmd.p1, cmd.p2, cmd.data,
                    le = if (response.sw2 == 0) 256 else response.sw2), "ICAO: READ $title longitud corregida")
            }
            if (!response.isSuccess && response.sw != 0x6282) throw IcaoStatusException(response.sw)
            val chunk = if (odd) BerTlv.value(response.data, 0x53) else response.data
            require(chunk.isNotEmpty()) { "Archivo ICAO truncado: respuesta sin datos" }
            require(output.size() + chunk.size <= BerTlv.MAX_FILE_BYTES) { "Archivo ICAO fuera de límites" }
            output.write(chunk)
            if (total == null) {
                val h = BerTlv.header(output.toByteArray())
                if (h != null) {
                    require(expectedTag == null || h.tag == expectedTag) { "Tag incorrecto en $title" }
                    total = h.total
                }
            }
            require(total == null || output.size() <= total) { "Datos fuera de la longitud declarada en $title" }
            if (response.sw == 0x6282) {
                require(total != null && output.size() == total) { "Fin de archivo prematuro en $title" }
            }
        }
        return output.toByteArray()
    }
}
