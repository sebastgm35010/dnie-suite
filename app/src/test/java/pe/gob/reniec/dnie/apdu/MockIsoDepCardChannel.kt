package pe.gob.reniec.dnie.apdu

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import pe.gob.reniec.dnie.core.apdu.ApduConstants
import pe.gob.reniec.dnie.core.apdu.CommandApdu
import pe.gob.reniec.dnie.core.apdu.ResponseApdu
import pe.gob.reniec.dnie.core.apdu.StatusWord
import pe.gob.reniec.dnie.core.nfc.DnieCardChannel
import java.io.IOException
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Security
import java.security.interfaces.RSAPrivateKey
import java.util.Date

/**
 * Simulador de alta fidelidad en memoria de la tarjeta inteligente DNIe Peruano (v2/v3).
 *
 * Mantiene estado de hardware real:
 * - Selección de Applet PKI de RENIEC y archivos elementales (EF).
 * - Certificados X.509 auténticos generados criptográficamente con BouncyCastle.
 * - Desensamblado de bloques en formato BER-TLV (Tag 0x53) con padding simulado de EEPROM.
 * - Estado de autenticación de PIN con contador de reintentos y bloqueo irrevocable (69 83).
 * - Cómputo de firma digital RSA-2048 PKCS#1 v1.5 con verificación matemática real.
 */
open class MockIsoDepCardChannel(
    var pin: String = "123456",
    var pinRetryCounter: Int = 3,
    var isCardBlocked: Boolean = false,
    includeEncryptionCert: Boolean = true,
    paddingBytesCount: Int = 32
) : DnieCardChannel {

    init {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    var connected: Boolean = true
    var timeoutMs: Int = 10000
    var throwOnNextTransceive: IOException? = null

    // Claves criptográficas RSA-2048 reales del chip
    val signatureKeyPair: KeyPair = generateRsa2048KeyPair()
    val authKeyPair: KeyPair = generateRsa2048KeyPair()

    // Certificados DER X.509
    val signatureCertDer: ByteArray = generateSelfSignedCertificate(
        keyPair = signatureKeyPair,
        subjectCn = "PEREZ LOPEZ JUAN CARLOS",
        dniNumber = "45678901",
        certUsage = "Firma Digital"
    )
    val authCertDer: ByteArray = generateSelfSignedCertificate(
        keyPair = authKeyPair,
        subjectCn = "PEREZ LOPEZ JUAN CARLOS",
        dniNumber = "45678901",
        certUsage = "Autenticacion"
    )
    val encCertDer: ByteArray? = if (includeEncryptionCert) {
        generateSelfSignedCertificate(
            keyPair = authKeyPair,
            subjectCn = "PEREZ LOPEZ JUAN CARLOS",
            dniNumber = "45678901",
            certUsage = "Cifrado"
        )
    } else null

    // Archivos EF en la memoria de la tarjeta (incluyen posible padding EEPROM)
    private val efFiles: MutableMap<String, ByteArray> = mutableMapOf(
        "001D" to appendEepromPadding(signatureCertDer, paddingBytesCount),
        "001C" to appendEepromPadding(authCertDer, paddingBytesCount),
        "011D" to appendEepromPadding(signatureCertDer, paddingBytesCount),
        "011C" to byteArrayOf(
            0x31.toByte(), 0x16.toByte(), 0x30.toByte(), 0x14.toByte(),
            0x06.toByte(), 0x0A.toByte(), 0x04.toByte(), 0x00.toByte(),
            0x7F.toByte(), 0x00.toByte(), 0x07.toByte(), 0x02.toByte(),
            0x02.toByte(), 0x04.toByte(), 0x02.toByte(), 0x02.toByte(),
            0x02.toByte(), 0x01.toByte(), 0x02.toByte(),
            0x02.toByte(), 0x01.toByte(), 0x0D.toByte()
        )
    )

    init {
        if (encCertDer != null) {
            efFiles["001B"] = appendEepromPadding(encCertDer, paddingBytesCount)
        }
    }

    // Estado interno del chip
    var isPkiAppletSelected: Boolean = false
    var selectedEfId: String? = null
    var isPinVerified: Boolean = false
    var isMseConfigured: Boolean = false

    override fun isConnected(): Boolean = connected

    override fun setTimeout(timeoutMs: Int) {
        this.timeoutMs = timeoutMs
    }

    override fun close() {
        connected = false
    }

    override fun transceive(command: ByteArray): ByteArray {
        if (!connected) {
            throw IOException("Card channel is closed or lost RF connection")
        }
        throwOnNextTransceive?.let {
            throwOnNextTransceive = null
            throw it
        }

        val apdu = CommandApdu.fromBytes(command)

        return when (apdu.ins) {
            ApduConstants.INS_SELECT -> handleSelect(apdu)
            ApduConstants.INS_READ_BINARY -> handleStandardReadBinary(apdu)
            ApduConstants.INS_READ_BINARY_DO -> handleReadBinary(apdu)
            ApduConstants.INS_VERIFY -> handleVerify(apdu)
            ApduConstants.INS_MSE -> handleMse(apdu)
            ApduConstants.INS_PSO -> handlePso(apdu)
            else -> ResponseApdu.error(StatusWord.SW_INS_NOT_SUPPORTED).toByteArray()
        }
    }

    private fun handleSelect(apdu: CommandApdu): ByteArray {
        return when (apdu.p1) {
            ApduConstants.P1_SELECT_DF_BY_NAME -> {
                if (ApduConstants.CANDIDATE_AIDS.any { it.second.contentEquals(apdu.data) }) {
                    isPkiAppletSelected = true
                    selectedEfId = null
                    ResponseApdu.success().toByteArray()
                } else {
                    isPkiAppletSelected = false
                    selectedEfId = null
                    ResponseApdu.error(StatusWord.SW_FILE_NOT_FOUND).toByteArray()
                }
            }
            0x00.toByte() -> {
                if (apdu.data.contentEquals(byteArrayOf(0x3F.toByte(), 0x00.toByte()))) {
                    isPkiAppletSelected = true
                    selectedEfId = null
                    ResponseApdu.success().toByteArray()
                } else if (ApduConstants.CANDIDATE_DFS.any { it.second.contentEquals(apdu.data) }) {
                    isPkiAppletSelected = true
                    selectedEfId = null
                    ResponseApdu.success().toByteArray()
                } else if (apdu.data.size == 2) {
                    // SELECT EF direct by File ID
                    if (!isPkiAppletSelected) {
                        return ResponseApdu.error(StatusWord.SW_SECURITY_STATUS_NOT_SATISFIED).toByteArray()
                    }
                    val efHex = apdu.data.joinToString("") { "%02X".format(it) }
                    if (efFiles.containsKey(efHex)) {
                        selectedEfId = efHex
                        ResponseApdu.success().toByteArray()
                    } else {
                        selectedEfId = null
                        ResponseApdu.error(StatusWord.SW_FILE_NOT_FOUND).toByteArray()
                    }
                } else {
                    ResponseApdu.error(StatusWord.SW_INCORRECT_P1_P2).toByteArray()
                }
            }
            ApduConstants.P1_SELECT_CHILD_DF -> {
                if (ApduConstants.CANDIDATE_DFS.any { it.second.contentEquals(apdu.data) }) {
                    isPkiAppletSelected = true
                    selectedEfId = null
                    ResponseApdu.success().toByteArray()
                } else {
                    ResponseApdu.error(StatusWord.SW_FILE_NOT_FOUND).toByteArray()
                }
            }
            ApduConstants.P1_SELECT_EF_UNDER_DF -> {
                if (!isPkiAppletSelected) {
                    return ResponseApdu.error(StatusWord.SW_SECURITY_STATUS_NOT_SATISFIED).toByteArray()
                }
                val efHex = apdu.data.joinToString("") { "%02X".format(it) }
                if (efFiles.containsKey(efHex)) {
                    selectedEfId = efHex
                    ResponseApdu.success().toByteArray()
                } else {
                    selectedEfId = null
                    ResponseApdu.error(StatusWord.SW_FILE_NOT_FOUND).toByteArray()
                }
            }
            else -> ResponseApdu.error(StatusWord.SW_INCORRECT_P1_P2).toByteArray()
        }
    }

    private fun handleStandardReadBinary(apdu: CommandApdu): ByteArray {
        val currentEf = selectedEfId ?: return ResponseApdu.error(StatusWord.SW_SECURITY_STATUS_NOT_SATISFIED).toByteArray()
        val fileBytes = efFiles[currentEf] ?: return ResponseApdu.error(StatusWord.SW_FILE_NOT_FOUND).toByteArray()

        val offsetHi = apdu.p1.toInt() and 0x7F
        val offsetLo = apdu.p2.toInt() and 0xFF
        val offset = (offsetHi shl 8) or offsetLo

        if (offset >= fileBytes.size) {
            return ResponseApdu.error(StatusWord.SW_WRONG_OFFSET).toByteArray()
        }

        val remaining = fileBytes.size - offset
        val requestedLe = apdu.le ?: 256
        val chunkSize = minOf(requestedLe, remaining)
        val chunk = fileBytes.copyOfRange(offset, offset + chunkSize)

        val sw = if (offset + chunkSize >= fileBytes.size) {
            StatusWord.SW_EOF_REACHED
        } else {
            StatusWord.SW_SUCCESS
        }

        return ResponseApdu(chunk, (sw ushr 8) and 0xFF, sw and 0xFF).toByteArray()
    }

    private fun handleReadBinary(apdu: CommandApdu): ByteArray {
        val currentEf = selectedEfId ?: return ResponseApdu.error(StatusWord.SW_SECURITY_STATUS_NOT_SATISFIED).toByteArray()
        val fileBytes = efFiles[currentEf] ?: return ResponseApdu.error(StatusWord.SW_FILE_NOT_FOUND).toByteArray()

        // El comando debe contener Tag 0x54, Len 0x02, Offset HI, Offset LO
        if (apdu.data.size < 4 || apdu.data[0] != ApduConstants.TAG_OFFSET_DO || apdu.data[1] != 0x02.toByte()) {
            return ResponseApdu.error(StatusWord.SW_WRONG_LENGTH).toByteArray()
        }

        val offsetHi = apdu.data[2].toInt() and 0xFF
        val offsetLo = apdu.data[3].toInt() and 0xFF
        val offset = (offsetHi shl 8) or offsetLo

        if (offset >= fileBytes.size) {
            return ResponseApdu.error(StatusWord.SW_INCORRECT_PARAMETERS_IN_DATA_FIELD).toByteArray()
        }

        val remaining = fileBytes.size - offset
        val chunkSize = minOf(ApduConstants.DEFAULT_BLOCK_SIZE, remaining)
        val chunk = fileBytes.copyOfRange(offset, offset + chunkSize)

        // Encapsulado en contenedor TLV (Tag 0x53, 2 bytes de longitud, carga útil)
        val tlvFrame = ByteArray(3 + chunkSize)
        tlvFrame[0] = ApduConstants.TAG_BER_TLV_DATA
        tlvFrame[1] = ((chunkSize shr 8) and 0xFF).toByte()
        tlvFrame[2] = (chunkSize and 0xFF).toByte()
        System.arraycopy(chunk, 0, tlvFrame, 3, chunkSize)

        val sw = if (offset + chunkSize >= fileBytes.size) {
            StatusWord.SW_EOF_REACHED
        } else {
            StatusWord.SW_SUCCESS
        }

        return ResponseApdu(tlvFrame, (sw ushr 8) and 0xFF, sw and 0xFF).toByteArray()
    }

    private fun handleVerify(apdu: CommandApdu): ByteArray {
        if (isCardBlocked) {
            return ResponseApdu.error(StatusWord.SW_AUTHENTICATION_BLOCKED).toByteArray()
        }

        if (apdu.p2 != ApduConstants.PIN_TYPE_SIGNATURE && apdu.p2 != ApduConstants.PIN_TYPE_AUTHENTICATION) {
            return ResponseApdu.error(StatusWord.SW_REFERENCED_DATA_NOT_FOUND).toByteArray()
        }

        val enteredPin = String(apdu.data, Charsets.US_ASCII)
        return if (enteredPin == pin) {
            pinRetryCounter = 3
            isPinVerified = true
            ResponseApdu.success().toByteArray()
        } else {
            isPinVerified = false
            pinRetryCounter--
            if (pinRetryCounter <= 0) {
                isCardBlocked = true
                ResponseApdu.error(StatusWord.SW_AUTHENTICATION_BLOCKED).toByteArray()
            } else {
                val sw = StatusWord.SW_PIN_RETRY_BASE or (pinRetryCounter and 0x0F)
                ResponseApdu.error(sw).toByteArray()
            }
        }
    }

    private fun handleMse(apdu: CommandApdu): ByteArray {
        if (!isPinVerified) {
            return ResponseApdu.error(StatusWord.SW_SECURITY_STATUS_NOT_SATISFIED).toByteArray()
        }

        if (apdu.p1 == ApduConstants.P1_MSE_SET && apdu.p2 == ApduConstants.P2_MSE_DST) {
            if (apdu.data.contentEquals(ApduConstants.MSE_SET_DST_DATA)) {
                isMseConfigured = true
                return ResponseApdu.success().toByteArray()
            }
        }

        return ResponseApdu.error(StatusWord.SW_INCORRECT_P1_P2).toByteArray()
    }

    private fun handlePso(apdu: CommandApdu): ByteArray {
        if (!isPinVerified || !isMseConfigured) {
            return ResponseApdu.error(StatusWord.SW_SECURITY_STATUS_NOT_SATISFIED).toByteArray()
        }

        if (apdu.p1 != ApduConstants.P1_PSO_CDS || apdu.p2 != ApduConstants.P2_PSO_CDS_DIGEST) {
            return ResponseApdu.error(StatusWord.SW_INCORRECT_P1_P2).toByteArray()
        }

        if (apdu.data.size != ApduConstants.PSO_CDS_PAYLOAD_SIZE) {
            return ResponseApdu.error(StatusWord.SW_WRONG_LENGTH).toByteArray()
        }

        // Cómputo genuino de firma RSA-2048 PKCS#1 v1.5 con la clave privada de la tarjeta
        val rsaPrivate = signatureKeyPair.private as RSAPrivateKey
        val paddedBlock = ByteArray(256)
        paddedBlock[0] = 0x00
        paddedBlock[1] = 0x01
        val padLength = 256 - 3 - apdu.data.size
        for (i in 2 until 2 + padLength) {
            paddedBlock[i] = 0xFF.toByte()
        }
        paddedBlock[2 + padLength] = 0x00
        System.arraycopy(apdu.data, 0, paddedBlock, 3 + padLength, apdu.data.size)

        // Exponenciación modular: s = m^d mod n
        val m = BigInteger(1, paddedBlock)
        val s = m.modPow(rsaPrivate.privateExponent, rsaPrivate.modulus)
        var sBytes = s.toByteArray()

        if (sBytes.size > 256) {
            sBytes = sBytes.copyOfRange(sBytes.size - 256, sBytes.size)
        } else if (sBytes.size < 256) {
            val full256 = ByteArray(256)
            System.arraycopy(sBytes, 0, full256, 256 - sBytes.size, sBytes.size)
            sBytes = full256
        }

        return ResponseApdu.success(sBytes).toByteArray()
    }

    companion object {
        fun generateRsa2048KeyPair(): KeyPair {
            val kpg = KeyPairGenerator.getInstance("RSA")
            kpg.initialize(2048)
            return kpg.generateKeyPair()
        }

        fun generateSelfSignedCertificate(
            keyPair: KeyPair,
            subjectCn: String,
            dniNumber: String,
            certUsage: String
        ): ByteArray {
            val now = System.currentTimeMillis()
            val notBefore = Date(now - 1000L * 60 * 60 * 24)
            val notAfter = Date(now + 1000L * 60 * 60 * 24 * 365)
            val serialNumber = BigInteger.valueOf(now)

            val issuerName = X500Name("C=PE, O=RENIEC, OU=PKI, CN=RENIEC AC DNIe")
            val subjectName = X500Name("C=PE, O=RENIEC, OU=$certUsage, CN=$subjectCn, SERIALNUMBER=DNI $dniNumber")

            val certBuilder = JcaX509v3CertificateBuilder(
                issuerName,
                serialNumber,
                notBefore,
                notAfter,
                subjectName,
                keyPair.public
            )

            val signer = JcaContentSignerBuilder("SHA256withRSA")
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build(keyPair.private)

            val certHolder = certBuilder.build(signer)
            return certHolder.encoded
        }

        private fun appendEepromPadding(data: ByteArray, count: Int): ByteArray {
            if (count <= 0) return data
            val result = ByteArray(data.size + count)
            System.arraycopy(data, 0, result, 0, data.size)
            for (i in data.size until result.size) {
                result[i] = 0xFF.toByte() // Relleno típico EEPROM virgen / borrada
            }
            return result
        }
    }
}
