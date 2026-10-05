package pe.gob.reniec.dnie.core.crypto

import java.math.BigInteger
import org.bouncycastle.math.ec.ECCurve
import org.bouncycastle.math.ec.ECPoint

/**
 * Metadatos y parámetros de seguridad extraídos de EF.CardAccess (Tag 0x53 / SecurityInfos).
 */
data class PaceSecurityInfo(
    val oid: String,
    val oidValueBytes: ByteArray,
    val version: Int,
    val parameterId: Int
) {
    val isGenericMapping: Boolean
        get() = oid.substringBeforeLast('.').endsWith(".4.2")

    val isIntegratedMapping: Boolean
        get() = oid.substringBeforeLast('.').endsWith(".4.4")

    val keyLength: Int
        get() = when (oid.substringAfterLast('.')) {
            "2" -> 16
            "3" -> 24
            "4" -> 32
            else -> throw IllegalArgumentException("Cifrado PACE no soportado: $oid")
        }

    val isAes256: Boolean
        get() = oid.endsWith(".4") ||
                (oidValueBytes.size >= 10 && oidValueBytes[9] == 0x04.toByte())

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
