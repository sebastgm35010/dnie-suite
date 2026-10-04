package pe.gob.reniec.dnie.core.apdu

/**
 * Representa una unidad de datos de comando de protocolo de aplicación (Command APDU) según ISO/IEC 7816-4.
 *
 * Estructura de trama APDU corta (Short APDU):
 * - CLA (1 byte): Clase de instrucción
 * - INS (1 byte): Código de instrucción
 * - P1  (1 byte): Parámetro 1
 * - P2  (1 byte): Parámetro 2
 * - Lc  (0 o 1 byte): Longitud del campo de datos transmitido
 * - Data (Lc bytes): Datos del comando
 * - Le  (0 o 1 byte): Longitud máxima esperada en la respuesta (0x00 representa 256 bytes)
 */
class CommandApdu(
    val cla: Byte,
    val ins: Byte,
    val p1: Byte,
    val p2: Byte,
    val data: ByteArray = byteArrayOf(),
    val le: Int? = null
) {
    init {
        require(data.size <= 255) { "Short APDU data length cannot exceed 255 bytes (got ${data.size})" }
        if (le != null) {
            require(le in 1..256) { "Short APDU Le must be between 1 and 256 (got $le)" }
        }
    }

    constructor(cla: Byte, ins: Byte, p1: Byte, p2: Byte, le: Int) :
            this(cla, ins, p1, p2, byteArrayOf(), le)

    constructor(cla: Byte, ins: Byte, p1: Byte, p2: Byte, data: ByteArray) :
            this(cla, ins, p1, p2, data, null)

    /**
     * Serializa el comando APDU a su representación binaria estándar ISO 7816-4.
     */
    fun toByteArray(): ByteArray {
        val hasData = data.isNotEmpty()
        val hasLe = le != null

        val expectedSize = 4 + (if (hasData) 1 + data.size else 0) + (if (hasLe) 1 else 0)
        val result = ByteArray(expectedSize)

        result[0] = cla
        result[1] = ins
        result[2] = p1
        result[3] = p2

        var index = 4
        if (hasData) {
            result[index++] = data.size.toByte()
            System.arraycopy(data, 0, result, index, data.size)
            index += data.size
        }

        if (hasLe) {
            val leByte = if (le == 256) 0x00.toByte() else (le!! and 0xFF).toByte()
            result[index] = leByte
        }

        return result
    }

    /**
     * Retorna la representación hexadecimal formateada del comando APDU.
     */
    fun toHexString(separator: String = " "): String {
        return toByteArray().joinToString(separator) { "%02X".format(it) }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CommandApdu
        if (cla != other.cla) return false
        if (ins != other.ins) return false
        if (p1 != other.p1) return false
        if (p2 != other.p2) return false
        if (!data.contentEquals(other.data)) return false
        if (le != other.le) return false

        return true
    }

    override fun hashCode(): Int {
        var result = cla.toInt()
        result = 31 * result + ins.toInt()
        result = 31 * result + p1.toInt()
        result = 31 * result + p2.toInt()
        result = 31 * result + data.contentHashCode()
        result = 31 * result + (le ?: 0)
        return result
    }

    override fun toString(): String {
        return "CommandApdu(CLA=%02X, INS=%02X, P1=%02X, P2=%02X, Lc=%d, Le=%s, hex=%s)".format(
            cla, ins, p1, p2, data.size, le?.toString() ?: "none", toHexString()
        )
    }

    companion object {
        /**
         * Crea un comando SELECT FILE para un AID / Nombre de DF.
         */
        fun selectDfByName(aid: ByteArray): CommandApdu {
            return CommandApdu(
                cla = ApduConstants.CLA_ISO7816,
                ins = ApduConstants.INS_SELECT,
                p1 = ApduConstants.P1_SELECT_DF_BY_NAME,
                p2 = ApduConstants.P2_SELECT_FIRST_RECORD,
                data = aid
            )
        }

        /**
         * Crea un comando SELECT FILE para el Applet PKI de RENIEC.
         */
        fun selectReniecPkiApplet(): CommandApdu {
            return selectDfByName(ApduConstants.AID_RENIEC_PKI)
        }

        /**
         * Crea un comando SELECT EF bajo el DF actual.
         */
        fun selectEf(fileId: ByteArray): CommandApdu {
            require(fileId.size == 2) { "EF File ID must be 2 bytes (got ${fileId.size})" }
            return CommandApdu(
                cla = ApduConstants.CLA_ISO7816,
                ins = ApduConstants.INS_SELECT,
                p1 = ApduConstants.P1_SELECT_EF_UNDER_DF,
                p2 = ApduConstants.P2_SELECT_NO_FCI,
                data = fileId
            )
        }

        /**
         * Crea un comando READ BINARY con Data Object de Offset (Tag 0x54).
         */
        fun readBinaryWithOffset(offset: Int, le: Int = 0xFF): CommandApdu {
            require(offset in 0..0xFFFF) { "Offset must be between 0 and 65535 (got $offset)" }
            val offsetHi = ((offset shr 8) and 0xFF).toByte()
            val offsetLo = (offset and 0xFF).toByte()
            val doData = byteArrayOf(
                ApduConstants.TAG_OFFSET_DO,
                0x02.toByte(),
                offsetHi,
                offsetLo
            )
            return CommandApdu(
                cla = ApduConstants.CLA_ISO7816,
                ins = ApduConstants.INS_READ_BINARY_DO,
                p1 = 0x00.toByte(),
                p2 = 0x00.toByte(),
                data = doData,
                le = le
            )
        }

        /**
         * Crea un comando VERIFY para autenticar el PIN en la tarjeta.
         */
        fun verifyPin(pin: String, pinType: Byte = ApduConstants.PIN_TYPE_SIGNATURE): CommandApdu {
            val pinBytes = pin.toByteArray(Charsets.US_ASCII)
            return CommandApdu(
                cla = ApduConstants.CLA_ISO7816,
                ins = ApduConstants.INS_VERIFY,
                p1 = 0x00.toByte(),
                p2 = pinType,
                data = pinBytes
            )
        }

        /**
         * Crea un comando MSE: SET DST para configurar el coprocesador para firma RSA-2048 con SHA-256.
         */
        fun mseSetDst(): CommandApdu {
            return CommandApdu(
                cla = ApduConstants.CLA_ISO7816,
                ins = ApduConstants.INS_MSE,
                p1 = ApduConstants.P1_MSE_SET,
                p2 = ApduConstants.P2_MSE_DST,
                data = ApduConstants.MSE_SET_DST_DATA
            )
        }

        /**
         * Crea un comando PSO: Compute Digital Signature con el prefijo ASN.1 DER y el hash SHA-256.
         */
        fun psoComputeDigitalSignature(hash32Bytes: ByteArray): CommandApdu {
            require(hash32Bytes.size == ApduConstants.SHA256_HASH_SIZE) {
                "SHA-256 hash must be exactly 32 bytes (got ${hash32Bytes.size})"
            }
            val payload = ByteArray(ApduConstants.PSO_CDS_PAYLOAD_SIZE)
            System.arraycopy(
                ApduConstants.SHA256_DIGEST_INFO_PREFIX, 0,
                payload, 0,
                ApduConstants.SHA256_DIGEST_INFO_PREFIX.size
            )
            System.arraycopy(
                hash32Bytes, 0,
                payload, ApduConstants.SHA256_DIGEST_INFO_PREFIX.size,
                hash32Bytes.size
            )

            return CommandApdu(
                cla = ApduConstants.CLA_ISO7816,
                ins = ApduConstants.INS_PSO,
                p1 = ApduConstants.P1_PSO_CDS,
                p2 = ApduConstants.P2_PSO_CDS_DIGEST,
                data = payload
            )
        }

        /**
         * Deserializa un arreglo de bytes en una instancia de [CommandApdu].
         */
        fun fromBytes(bytes: ByteArray): CommandApdu {
            require(bytes.size >= 4) { "APDU must have at least 4 header bytes (got ${bytes.size})" }
            val cla = bytes[0]
            val ins = bytes[1]
            val p1 = bytes[2]
            val p2 = bytes[3]

            if (bytes.size == 4) {
                return CommandApdu(cla, ins, p1, p2)
            }

            if (bytes.size == 5) {
                // Case 2: CLA INS P1 P2 Le
                val leRaw = bytes[4].toInt() and 0xFF
                val le = if (leRaw == 0) 256 else leRaw
                return CommandApdu(cla, ins, p1, p2, le = le)
            }

            val lc = bytes[4].toInt() and 0xFF
            require(bytes.size >= 5 + lc) {
                "Malformed APDU: Lc=$lc requires at least ${5 + lc} bytes, but got ${bytes.size}"
            }

            val data = bytes.copyOfRange(5, 5 + lc)
            val le = if (bytes.size > 5 + lc) {
                val leRaw = bytes[5 + lc].toInt() and 0xFF
                if (leRaw == 0) 256 else leRaw
            } else {
                null
            }

            return CommandApdu(cla, ins, p1, p2, data, le)
        }
    }
}
