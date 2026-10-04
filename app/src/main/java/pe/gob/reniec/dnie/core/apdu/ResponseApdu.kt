package pe.gob.reniec.dnie.core.apdu

/**
 * Representa una unidad de datos de respuesta de protocolo de aplicación (Response APDU) según ISO/IEC 7816-4.
 *
 * Estructura:
 * - Data (0 a N bytes): Datos de carga útil devueltos por la tarjeta.
 * - SW1  (1 byte): Primer byte de código de estado.
 * - SW2  (1 byte): Segundo byte de código de estado.
 */
class ResponseApdu(
    val data: ByteArray,
    val sw1: Int,
    val sw2: Int
) {
    val sw: Int get() = ((sw1 and 0xFF) shl 8) or (sw2 and 0xFF)

    val isSuccess: Boolean get() = sw == StatusWord.SW_SUCCESS
    val isWarning: Boolean get() = sw1 == 0x62 || sw1 == 0x63
    val isError: Boolean get() = !isSuccess && !isWarning

    val statusWord: StatusWord get() = StatusWord.fromSw(sw)
    val statusWordHex: String get() = "%04X".format(sw)

    /**
     * Serializa la respuesta completa a bytes (Data + SW1 + SW2).
     */
    fun toByteArray(): ByteArray {
        val result = ByteArray(data.size + 2)
        System.arraycopy(data, 0, result, 0, data.size)
        result[data.size] = sw1.toByte()
        result[data.size + 1] = sw2.toByte()
        return result
    }

    /**
     * Retorna la representación hexadecimal de toda la trama de respuesta.
     */
    fun toHexString(separator: String = " "): String {
        return toByteArray().joinToString(separator) { "%02X".format(it) }
    }

    /**
     * Retorna la representación hexadecimal únicamente del campo de datos.
     */
    fun dataToHexString(separator: String = " "): String {
        return data.joinToString(separator) { "%02X".format(it) }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ResponseApdu
        if (sw1 != other.sw1) return false
        if (sw2 != other.sw2) return false
        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + sw1
        result = 31 * result + sw2
        return result
    }

    override fun toString(): String {
        return "ResponseApdu(dataLen=%d, SW=%s, status=%s)".format(
            data.size, statusWordHex, statusWord.description
        )
    }

    companion object {
        /**
         * Parsea una trama binaria recibida de la tarjeta inteligente.
         * Debe contener como mínimo 2 bytes correspondientes a SW1 y SW2.
         */
        fun fromBytes(bytes: ByteArray): ResponseApdu {
            require(bytes.size >= 2) {
                "Response APDU must contain at least 2 bytes (SW1 SW2), got ${bytes.size}"
            }
            val sw1 = bytes[bytes.size - 2].toInt() and 0xFF
            val sw2 = bytes[bytes.size - 1].toInt() and 0xFF
            val data = if (bytes.size > 2) {
                bytes.copyOfRange(0, bytes.size - 2)
            } else {
                byteArrayOf()
            }
            return ResponseApdu(data, sw1, sw2)
        }

        /**
         * Crea una respuesta exitosa (SW: 90 00).
         */
        fun success(data: ByteArray = byteArrayOf()): ResponseApdu {
            return ResponseApdu(data, 0x90, 0x00)
        }

        /**
         * Crea una respuesta con un código de estado específico.
         */
        fun fromStatusWord(sw: StatusWord, data: ByteArray = byteArrayOf()): ResponseApdu {
            return ResponseApdu(data, sw.sw1, sw.sw2)
        }

        /**
         * Crea una respuesta de error con un código de 16 bits.
         */
        fun error(sw: Int, data: ByteArray = byteArrayOf()): ResponseApdu {
            return ResponseApdu(data, (sw ushr 8) and 0xFF, sw and 0xFF)
        }
    }
}
