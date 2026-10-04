package pe.gob.reniec.dnie.core.crypto

import org.bouncycastle.asn1.ASN1InputStream
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.ASN1Primitive
import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.asn1.ASN1Set
import org.bouncycastle.asn1.teletrust.TeleTrusTNamedCurves
import org.bouncycastle.asn1.x9.ECNamedCurveTable
import org.bouncycastle.asn1.x9.X9ECParameters
import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.macs.CMac
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.math.ec.ECCurve
import org.bouncycastle.math.ec.ECPoint
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import pe.gob.reniec.dnie.crypto.CryptoProviderInitializer
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Metadatos y parámetros de seguridad extraídos de EF.CardAccess (Tag 0x53 / SecurityInfos).
 */
data class PaceSecurityInfo(
    val oid: String,
    val oidValueBytes: ByteArray,
    val version: Int,
    val parameterId: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PaceSecurityInfo) return false
        return oid == other.oid &&
                oidValueBytes.contentEquals(other.oidValueBytes) &&
                version == other.version &&
                parameterId == other.parameterId
    }

    override fun hashCode(): Int {
        var result = oid.hashCode()
        result = 31 * result + oidValueBytes.contentHashCode()
        result = 31 * result + version
        result = 31 * result + parameterId
        return result
    }
}

/**
 * Par de claves para el paso de Generic Mapping (PACE-GM).
 */
data class PaceMappingKeyPair(
    val privateScalar: BigInteger,
    val publicPoint: ECPoint,
    val publicPointBytes: ByteArray
)

/**
 * Grupo elíptico con generador mapeado G' acordado mediante PACE.
 */
data class MappedGroup(
    val curve: ECCurve,
    val baseGenerator: ECPoint,
    val mappedGenerator: ECPoint,
    val order: BigInteger,
    val cofactor: BigInteger
)

/**
 * Par de claves efímeras generadas sobre el generador mapeado G'.
 */
data class PaceEphemeralKeyPair(
    val privateScalar: BigInteger,
    val publicPoint: ECPoint,
    val publicPointBytes: ByteArray
)

/**
 * Claves de sesión simétricas derivadas para Mensajería Segura (Secure Messaging).
 */
data class PaceSessionKeys(
    val kEnc: ByteArray,
    val kMac: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PaceSessionKeys) return false
        return kEnc.contentEquals(other.kEnc) && kMac.contentEquals(other.kMac)
    }

    override fun hashCode(): Int {
        return 31 * kEnc.contentHashCode() + kMac.contentHashCode()
    }
}

/**
 * Motor criptográfico para el protocolo PACE (Password Authenticated Connection Establishment)
 * conforme a ICAO Doc 9303 Parte 11 y BSI TR-03110 v2.
 *
 * Implementa el protocolo PACE-ECDH-GM-AES-CBC-CMAC-128 con curvas Brainpool / NIST.
 */
object PaceProtocol {

    private val random = SecureRandom()

    init {
        CryptoProviderInitializer.init()
    }

    /**
     * Parsea la estructura SecurityInfos de EF.CardAccess (Tag 0x53 / SET OF SecurityInfo)
     * para extraer los parámetros de PACE del DNIe.
     */
    fun parseCardAccess(rawBytes: ByteArray): PaceSecurityInfo? {
        if (rawBytes.isEmpty()) return null

        // Si viene envuelto en el contenedor BER-TLV Tag 0x53 (Discretionary Data)
        val payload = if (rawBytes[0] == 0x53.toByte() && rawBytes.size > 2) {
            val lenByte = rawBytes[1].toInt() and 0xFF
            val start = when {
                lenByte == 0x82 -> 4
                lenByte == 0x81 -> 3
                lenByte in 0x01..0x7F -> 2
                lenByte == 0x00 && rawBytes.size >= 3 -> 3
                else -> 3
            }
            if (rawBytes.size > start) rawBytes.copyOfRange(start, rawBytes.size) else rawBytes
        } else {
            rawBytes
        }

        try {
            val asn1In = ASN1InputStream(ByteArrayInputStream(payload))
            var obj: ASN1Primitive? = asn1In.readObject()

            // Puede venir como SET (0x31) directamente o envuelto en SEQUENCE (0x30)
            val elements = when (obj) {
                is ASN1Set -> (0 until obj.size()).map { obj.getObjectAt(it) }
                is ASN1Sequence -> {
                    // Si el primer elemento es un SET
                    val first = obj.getObjectAt(0)
                    if (first is ASN1Set) {
                        (0 until first.size()).map { first.getObjectAt(it) }
                    } else {
                        listOf(obj)
                    }
                }
                else -> emptyList()
            }

            for (elem in elements) {
                if (elem is ASN1Sequence && elem.size() >= 2) {
                    val oidObj = elem.getObjectAt(0) as? ASN1ObjectIdentifier
                    val versionObj = elem.getObjectAt(1) as? ASN1Integer
                    val paramIdObj = if (elem.size() > 2) elem.getObjectAt(2) as? ASN1Integer else null

                    if (oidObj != null && oidObj.id.startsWith("0.4.0.127.0.7.2.2.4")) {
                        // Extraer los bytes del valor del OID (cuerpo sin Tag 0x06 ni longitud)
                        val encodedOid = oidObj.encoded
                        val oidValueBytes = if (encodedOid.size > 2) {
                            encodedOid.copyOfRange(2, encodedOid.size)
                        } else {
                            encodedOid
                        }

                        val version = versionObj?.value?.toInt() ?: 2
                        val paramId = paramIdObj?.value?.toInt() ?: 13 // 13 = BrainpoolP256r1 por defecto

                        return PaceSecurityInfo(
                            oid = oidObj.id,
                            oidValueBytes = oidValueBytes,
                            version = version,
                            parameterId = paramId
                        )
                    }
                }
            }
        } catch (_: Exception) {}

        // Fallback heurístico si ASN1InputStream falla: buscar secuencia OID id-PACE
        val pacePrefix = byteArrayOf(0x04, 0x00, 0x7F, 0x00, 0x07, 0x02, 0x02, 0x04)
        val idx = indexOfSubarray(payload, pacePrefix)
        if (idx != -1 && payload.size >= idx + 10) {
            val oidValue = payload.copyOfRange(idx, idx + 10)
            // Buscar paramId cercano
            var paramId = 13
            for (i in idx + 10 until minOf(payload.size - 2, idx + 25)) {
                if (payload[i] == 0x02.toByte() && payload[i + 1] == 0x01.toByte()) {
                    paramId = payload[i + 2].toInt() and 0xFF
                }
            }
            return PaceSecurityInfo(
                oid = "0.4.0.127.0.7.2.2.4.2.2",
                oidValueBytes = oidValue,
                version = 2,
                parameterId = paramId
            )
        }

        return null
    }

    private fun indexOfSubarray(array: ByteArray, sub: ByteArray): Int {
        if (sub.isEmpty() || array.size < sub.size) return -1
        for (i in 0..array.size - sub.size) {
            var match = true
            for (j in sub.indices) {
                if (array[i + j] != sub[j]) {
                    match = false
                    break
                }
            }
            if (match) return i
        }
        return -1
    }

    /**
     * Resuelve los parámetros elípticos a partir del Parameter ID de BSI TR-03110 / ICAO Doc 9303.
     * ID 13 = brainpoolP256r1 (estándar DNIe 3.0), ID 12 = secp256r1.
     */
    fun getCurveParameters(parameterId: Int): X9ECParameters {
        val curveName = when (parameterId) {
            9 -> "brainpoolP160r1"
            10 -> "brainpoolP192r1"
            11 -> "brainpoolP224r1"
            12 -> "secp256r1"
            13 -> "brainpoolP256r1"
            14 -> "brainpoolP320r1"
            15 -> "secp384r1"
            16 -> "brainpoolP384r1"
            17 -> "secp521r1"
            18 -> "brainpoolP512r1"
            else -> "brainpoolP256r1"
        }

        return TeleTrusTNamedCurves.getByName(curveName)
            ?: ECNamedCurveTable.getByName(curveName)
            ?: throw IllegalArgumentException("Curva elíptica no soportada para parameterId $parameterId ($curveName)")
    }

    /**
     * Deriva la clave simétrica K_pi a partir del CAN (o MRZ) según BSI TR-03110:
     * K_pi = SHA-1(CAN || 0x00, 0x00, 0x00, 0x03)[0..15]
     */
    fun deriveKpi(password: String, keyLength: Int = 16): ByteArray {
        val pwdBytes = password.toByteArray(Charsets.US_ASCII)
        val counterBytes = byteArrayOf(0x00, 0x00, 0x00, 0x03)
        val md = MessageDigest.getInstance("SHA-1")
        md.update(pwdBytes)
        md.update(counterBytes)
        val hash = md.digest()
        return hash.copyOfRange(0, keyLength)
    }

    /**
     * Descifra el Nonce aleatorio devuelto por la tarjeta en el paso 1 de PACE:
     * Nonce = AES-128-CBC-Decrypt(K_pi, IV=0, EncryptedNonce)
     */
    fun decryptNonce(password: String, encryptedNonce: ByteArray): ByteArray {
        val kPi = deriveKpi(password, 16)
        val iv = ByteArray(16) // IV de 16 ceros
        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(kPi, "AES"), IvParameterSpec(iv))
        return cipher.doFinal(encryptedNonce)
    }

    /**
     * Genera un par de claves efímeras para el paso de Generic Mapping (PACE-GM).
     */
    fun generateMappingKey(parameterId: Int = 13): PaceMappingKeyPair {
        val params = getCurveParameters(parameterId)
        val n = params.n
        val g = params.g

        // Generar escalar aleatorio x1 en [1, n-1]
        val x1 = generateRandomScalar(n)
        val y1 = g.multiply(x1).normalize()
        val y1Bytes = y1.getEncoded(false) // 65 bytes sin comprimir (0x04 || X || Y)

        return PaceMappingKeyPair(
            privateScalar = x1,
            publicPoint = y1,
            publicPointBytes = y1Bytes
        )
    }

    /**
     * Calcula el nuevo generador G' a partir del punto público de mapeo de la tarjeta y el Nonce s:
     * H = x1 * Y2
     * G' = s * G + H
     */
    fun computeMappedGenerator(
        mappingPair: PaceMappingKeyPair,
        cardMappingPubBytes: ByteArray,
        nonceBytes: ByteArray,
        parameterId: Int = 13
    ): MappedGroup {
        val params = getCurveParameters(parameterId)
        val curve = params.curve
        val g = params.g
        val n = params.n
        val h = params.h

        // Decodificar punto de la tarjeta Y2
        val y2 = curve.decodePoint(cardMappingPubBytes)

        // Calcular punto secreto compartido H = x1 * Y2
        val sharedH = y2.multiply(mappingPair.privateScalar).normalize()

        // Convertir Nonce a entero s
        val s = BigInteger(1, nonceBytes)

        // G' = s * G + H
        val mappedG = g.multiply(s).add(sharedH).normalize()

        return MappedGroup(
            curve = curve,
            baseGenerator = g,
            mappedGenerator = mappedG,
            order = n,
            cofactor = h
        )
    }

    /**
     * Genera un par de claves efímeras sobre el nuevo generador mapeado G'.
     */
    fun generateEphemeralKey(mappedGroup: MappedGroup): PaceEphemeralKeyPair {
        val n = mappedGroup.order
        val gPrime = mappedGroup.mappedGenerator

        val x3 = generateRandomScalar(n)
        val y3 = gPrime.multiply(x3).normalize()
        val y3Bytes = y3.getEncoded(false)

        return PaceEphemeralKeyPair(
            privateScalar = x3,
            publicPoint = y3,
            publicPointBytes = y3Bytes
        )
    }

    /**
     * Calcula el punto secreto compartido K a partir de la clave pública efímera de la tarjeta Y4:
     * K = x3 * Y4
     * Retorna la coordenada X de K en exactamente 32 bytes (para curvas de 256 bits).
     */
    fun computeSharedSecretX(
        ephemeralPair: PaceEphemeralKeyPair,
        cardEphemeralPubBytes: ByteArray,
        mappedGroup: MappedGroup
    ): ByteArray {
        val y4 = mappedGroup.curve.decodePoint(cardEphemeralPubBytes)
        val sharedK = y4.multiply(ephemeralPair.privateScalar).normalize()
        val kx = sharedK.affineXCoord.toBigInteger()

        val coordSize = (mappedGroup.curve.fieldSize + 7) / 8
        return bigIntegerToFixedBytes(kx, coordSize)
    }

    /**
     * Deriva las claves de sesión K_Enc y K_Mac a partir del secreto compartido Kx:
     * K_Enc = SHA-1(Kx || 0x00, 0x00, 0x00, 0x01)[0..15]
     * K_Mac = SHA-1(Kx || 0x00, 0x00, 0x00, 0x02)[0..15]
     */
    fun deriveSessionKeys(sharedSecretX: ByteArray, keyLength: Int = 16): PaceSessionKeys {
        val md = MessageDigest.getInstance("SHA-1")

        // K_Enc (counter = 1)
        md.update(sharedSecretX)
        md.update(byteArrayOf(0x00, 0x00, 0x00, 0x01))
        val kEnc = md.digest().copyOfRange(0, keyLength)

        // K_Mac (counter = 2)
        md.reset()
        md.update(sharedSecretX)
        md.update(byteArrayOf(0x00, 0x00, 0x00, 0x02))
        val kMac = md.digest().copyOfRange(0, keyLength)

        return PaceSessionKeys(kEnc = kEnc, kMac = kMac)
    }

    /**
     * Construye la estructura BER-TLV Tag 7F49 que encapsula el OID y la clave pública para el Token MAC:
     * 7F 49 <len>
     *   06 <len_oid> <oidValueBytes>
     *   86 <len_pub> <pubKeyBytes>
     */
    fun buildAuthenticationTokenData(oidValueBytes: ByteArray, pubKeyBytes: ByteArray): ByteArray {
        val oidDo = byteArrayOf(0x06.toByte(), oidValueBytes.size.toByte()) + oidValueBytes
        val pubDo = byteArrayOf(0x86.toByte(), pubKeyBytes.size.toByte()) + pubKeyBytes
        val inner = oidDo + pubDo

        val lenBytes = if (inner.size < 0x80) {
            byteArrayOf(inner.size.toByte())
        } else {
            byteArrayOf(0x81.toByte(), inner.size.toByte())
        }

        return byteArrayOf(0x7F.toByte(), 0x49.toByte()) + lenBytes + inner
    }

    /**
     * Calcula el Message Authentication Code (AES-CMAC) de 8 bytes (64 bits)
     * para autenticación mutua (T_PCD o T_PICC).
     */
    fun computeAuthenticationToken(
        kMac: ByteArray,
        oidValueBytes: ByteArray,
        pubKeyBytes: ByteArray
    ): ByteArray {
        val tokenData = buildAuthenticationTokenData(oidValueBytes, pubKeyBytes)
        return computeCmac(kMac, tokenData)
    }

    /**
     * Calcula AES-CMAC sobre los datos provistos y trunca el resultado a 8 bytes según ICAO Doc 9303.
     */
    fun computeCmac(key: ByteArray, data: ByteArray): ByteArray {
        val mac = CMac(AESEngine())
        mac.init(KeyParameter(key))
        mac.update(data, 0, data.size)
        val fullMac = ByteArray(mac.macSize)
        mac.doFinal(fullMac, 0)
        return fullMac.copyOfRange(0, 8) // Truncado a 8 bytes
    }

    /**
     * Genera un número aleatorio criptográficamente seguro en [1, n-1].
     */
    private fun generateRandomScalar(n: BigInteger): BigInteger {
        val bitLength = n.bitLength()
        while (true) {
            val k = BigInteger(bitLength, random)
            if (k > BigInteger.ONE && k < n) {
                return k
            }
        }
    }

    /**
     * Convierte un [BigInteger] a un arreglo de bytes con tamaño fijo exacto,
     * truncando el byte de signo positivo adicional o rellenando con ceros a la izquierda.
     */
    fun bigIntegerToFixedBytes(bi: BigInteger, size: Int): ByteArray {
        val raw = bi.toByteArray()
        if (raw.size == size) return raw

        val result = ByteArray(size)
        if (raw.size > size) {
            // Descartar bytes más significativos (ej. byte de signo 0x00)
            System.arraycopy(raw, raw.size - size, result, 0, size)
        } else {
            // Rellenar con ceros a la izquierda
            System.arraycopy(raw, 0, result, size - raw.size, raw.size)
        }
        return result
    }
}
