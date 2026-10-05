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
 * Motor criptográfico para el protocolo PACE (Password Authenticated Connection Establishment)
 * conforme a ICAO Doc 9303 Parte 11 y BSI TR-03110 v2.
 *
 * Implementa PACE-ECDH-GM/IM-AES-CBC-CMAC con curvas Brainpool / NIST.
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
                oid = ASN1ObjectIdentifier.getInstance(byteArrayOf(0x06, 0x0A) + oidValue).id,
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
            else -> throw IllegalArgumentException("Parameter ID PACE no soportado: $parameterId")
        }

        return TeleTrusTNamedCurves.getByName(curveName)
            ?: ECNamedCurveTable.getByName(curveName)
            ?: throw IllegalArgumentException("Curva elíptica no soportada para parameterId $parameterId ($curveName)")
    }

    /**
     * Deriva la clave simétrica K_pi a partir del CAN (o MRZ) según BSI TR-03110:
     * K_pi = SHA-1(CAN || 0x00, 0x00, 0x00, 0x03)[0..15] para AES-128
     * K_pi = SHA-256(CAN || 0x00, 0x00, 0x00, 0x03)[0..31] para AES-256
     */
    fun deriveKpi(password: String, keyLength: Int = 16): ByteArray {
        require(keyLength in listOf(16, 24, 32))
        val pwdBytes = password.toByteArray(Charsets.US_ASCII)
        val counterBytes = byteArrayOf(0x00, 0x00, 0x00, 0x03)
        val md = if (keyLength > 16) MessageDigest.getInstance("SHA-256") else MessageDigest.getInstance("SHA-1")
        md.update(pwdBytes)
        md.update(counterBytes)
        val hash = md.digest()
        return hash.copyOfRange(0, keyLength)
    }

    /**
     * Descifra el Nonce aleatorio devuelto por la tarjeta en el paso 1 de PACE:
     * Nonce = AES-CBC-Decrypt(K_pi, IV=0, EncryptedNonce)
     */
    fun decryptNonce(password: String, encryptedNonce: ByteArray, isAes256: Boolean = false,
                     keyLength: Int = if (isAes256) 32 else 16): ByteArray {
        require(encryptedNonce.isNotEmpty() && encryptedNonce.size % 16 == 0)
        val kPi = deriveKpi(password, keyLength)
        val iv = ByteArray(16) // IV de 16 ceros para AES-CBC
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
     * ICAO 9303-11 §4.4.3.3.2 and Appendix B.2: G' = fG(Rp(s,t)).
     * terminalNonce is sent to the chip in mapping DO81 before key agreement.
     */
    fun computeIntegratedMappedGenerator(
        nonceBytes: ByteArray,
        terminalNonce: ByteArray,
        parameterId: Int = 13
    ): MappedGroup {
        val params = getCurveParameters(parameterId)
        val curve = params.curve
        val g = params.g
        val n = params.n
        val h = params.h

        val p = curve.field.characteristic
        require(p.mod(BigInteger.valueOf(4)) == BigInteger.valueOf(3)) {
            "PACE-IM requiere un campo primo p = 3 mod 4"
        }
        val t = integratedMappingFieldElement(nonceBytes, terminalNonce, p)
        require(t.signum() > 0) { "PACE-IM produjo un elemento de campo nulo" }
        val a = curve.a.toBigInteger()
        val b = curve.b.toBigInteger()
        val alpha = t.multiply(t).negate().mod(p)
        val denominator = alpha.add(alpha.multiply(alpha)).mod(p)
        require(denominator.signum() != 0 && a.signum() != 0) { "Entrada singular para PACE-IM" }
        val x2 = b.negate().multiply(a.modInverse(p))
            .multiply(BigInteger.ONE.add(denominator.modInverse(p))).mod(p)
        val x3 = alpha.multiply(x2).mod(p)
        val h2 = x2.modPow(BigInteger.valueOf(3), p).add(a.multiply(x2)).add(b).mod(p)
        val u = t.modPow(BigInteger.valueOf(3), p).multiply(h2).mod(p)
        val exponent = p.subtract(BigInteger.ONE).subtract(p.add(BigInteger.ONE).divide(BigInteger.valueOf(4)))
        val aa = h2.modPow(exponent, p)
        val useX2 = aa.multiply(aa).multiply(h2).mod(p) == BigInteger.ONE
        val x = if (useX2) x2 else x3
        val y = aa.multiply(if (useX2) h2 else u).mod(p)
        val mappedG = curve.validatePoint(x, y).multiply(h).normalize()
        require(!mappedG.isInfinity && mappedG.isValid && mappedG.multiply(n).isInfinity)

        return MappedGroup(
            curve = curve,
            baseGenerator = g,
            mappedGenerator = mappedG,
            order = n,
            cofactor = h
        )
    }

    /** Pseudo-random number mapping, ICAO 9303-11 Figure 2 (fresh zero IV per CBC call). */
    fun integratedMappingFieldElement(s: ByteArray, t: ByteArray, p: BigInteger): BigInteger {
        require(t.size in listOf(16, 24, 32))
        require(s.size == if (t.size == 16) 16 else 32)
        fun hex(value: String) = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val c0 = hex(if (s.size == 16) "a668892a7c41e3ca739f40b057d85904"
            else "d463d65234124ef7897054986dca0a174e28df758cbaa03f240616414d5a1676")
        val c1 = hex(if (s.size == 16) "a4e136ac725f738b01c1f60217c188ad"
            else "54bd7255f0aaf831bec3423fcf39d69b6cbf066677d0faae5aadd99df8e53517")
        fun encrypt(key: ByteArray, data: ByteArray): ByteArray {
            val cipher = Cipher.getInstance("AES/CBC/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(ByteArray(16)))
            return cipher.doFinal(data)
        }
        var key = encrypt(t, s).copyOf(t.size)
        val output = ByteArrayOutputStream()
        val iterations = (p.bitLength() + 64 + s.size * 8 - 1) / (s.size * 8)
        repeat(iterations) {
            output.write(encrypt(key, c1))
            key = encrypt(key, c0).copyOf(t.size)
        }
        return BigInteger(1, output.toByteArray()).mod(p)
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
        require(!y4.isInfinity && y4.isValid && y4.multiply(mappedGroup.order).isInfinity)
        require(!y4.normalize().equals(ephemeralPair.publicPoint.normalize())) { "Claves PACE reflejadas" }
        val sharedK = y4.multiply(ephemeralPair.privateScalar).normalize()
        val kx = sharedK.affineXCoord.toBigInteger()

        val coordSize = (mappedGroup.curve.fieldSize + 7) / 8
        return bigIntegerToFixedBytes(kx, coordSize)
    }

    /**
     * Deriva las claves de sesión K_Enc y K_Mac a partir del secreto compartido Kx:
     * Para AES-128: SHA-1(Kx || counter)[0..15]
     * Para AES-256: SHA-256(Kx || counter)[0..31]
     */
    fun deriveSessionKeys(
        sharedSecretX: ByteArray,
        keyLength: Int = 16,
        isAes256: Boolean = false
    ): PaceSessionKeys {
        val actualLen = if (isAes256) 32 else keyLength
        require(actualLen in listOf(16, 24, 32))
        val md = if (actualLen > 16) MessageDigest.getInstance("SHA-256") else MessageDigest.getInstance("SHA-1")

        // K_Enc (counter = 1)
        md.update(sharedSecretX)
        md.update(byteArrayOf(0x00, 0x00, 0x00, 0x01))
        val kEnc = md.digest().copyOfRange(0, actualLen)

        // K_Mac (counter = 2)
        md.reset()
        md.update(sharedSecretX)
        md.update(byteArrayOf(0x00, 0x00, 0x00, 0x02))
        val kMac = md.digest().copyOfRange(0, actualLen)

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
        val mac = CMac(AESEngine.newInstance())
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
