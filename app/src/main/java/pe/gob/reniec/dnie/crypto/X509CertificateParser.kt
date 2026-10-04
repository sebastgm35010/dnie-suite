package pe.gob.reniec.dnie.crypto

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.asn1.x500.style.IETFUtils
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import pe.gob.reniec.dnie.domain.model.CertificateInfo
import pe.gob.reniec.dnie.domain.model.CertificateType
import pe.gob.reniec.dnie.domain.model.CitizenData
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Date

/**
 * Analizador e inspector de certificados digitales X.509 para DNIe peruano v2 y v3.
 *
 * Utiliza BouncyCastle (bcprov-jdk18on / bcpkix-jdk18on) para desempaquetar la estructura
 * ASN.1 DER, extraer el Subject/Issuer, calcular huellas criptográficas SHA-256 y SHA-1,
 * y ejecutar un algoritmo multi-nivel de extracción del DNI y nombres del ciudadano.
 */
object X509CertificateParser {

    private val DNI_REGEX = Regex("""\b(\d{8})\b""")
    private val NAME_CLEANUP_REGEX = Regex("""\s*(\(FIRMA\)|\(AUTENTICACION\)|\(CIFRADO\)|\(SIGNATURE\)|\(AUTH\)|\(ENC\)|\[DNI\s*[:\d]+\]|\[\d{8}\]|-?\s*\b\d{8}\b)\s*""", RegexOption.IGNORE_CASE)

    init {
        CryptoProviderInitializer.init()
    }

    /**
     * Convierte un arreglo de bytes en formato DER binario a [X509Certificate].
     *
     * @param derBytes Arreglo con la codificación ASN.1 DER del certificado.
     * @return Instancia de [X509Certificate] procesada con BouncyCastle.
     */
    fun toX509Certificate(derBytes: ByteArray): X509Certificate {
        require(derBytes.isNotEmpty()) { "Los bytes del certificado no pueden estar vacíos" }

        // 1. Intentar decodificación como certificado X.509 directo (DNIe v2 / IAS-ECC)
        try {
            val holder = X509CertificateHolder(derBytes)
            val converter = JcaX509CertificateConverter()
                .setProvider(CryptoProviderInitializer.provider)
            return converter.getCertificate(holder)
        } catch (_: Exception) {}

        // 2. Si es una estructura ICAO eMRTD EF.SOD (Tag 0x77 o CMS SignedData)
        try {
            val cmsBytes = if ((derBytes[0].toInt() and 0xFF) == 0x77 && derBytes.size > 2) {
                // Desempaquetar Tag 0x77 (LDS Security Object)
                val lenByte = derBytes[1].toInt() and 0xFF
                val contentStart = when {
                    lenByte == 0x82 -> 4
                    lenByte == 0x81 -> 3
                    lenByte < 0x80 -> 2
                    else -> 2
                }
                derBytes.copyOfRange(contentStart, derBytes.size)
            } else {
                derBytes
            }

            val signedData = org.bouncycastle.cms.CMSSignedData(cmsBytes)
            val certStore = signedData.certificates
            val certs = certStore.getMatches(null)
            val firstCertHolder = certs.firstOrNull() as? X509CertificateHolder
            if (firstCertHolder != null) {
                val converter = JcaX509CertificateConverter()
                    .setProvider(CryptoProviderInitializer.provider)
                return converter.getCertificate(firstCertHolder)
            }
        } catch (_: Exception) {}

        // 3. Fallback mediante CertificateFactory estándar configurado con BouncyCastle
        val certFactory = CertificateFactory.getInstance("X.509", CryptoProviderInitializer.provider)
        return certFactory.generateCertificate(ByteArrayInputStream(derBytes)) as X509Certificate
    }

    /**
     * Parsea un certificado digital desde sus bytes DER y produce un [CertificateInfo] completo.
     *
     * @param derBytes Bytes DER del certificado extraídos del chip DNIe.
     * @param type Tipo de certificado según el EF leído (Firma, Autenticación, Cifrado).
     */
    fun parse(derBytes: ByteArray, type: CertificateType = CertificateType.SIGNATURE): CertificateInfo {
        val cert = toX509Certificate(derBytes)
        return parse(cert, type, derBytes)
    }

    /**
     * Parsea un objeto [X509Certificate] existente produciendo un [CertificateInfo].
     */
    fun parse(
        cert: X509Certificate,
        type: CertificateType = CertificateType.SIGNATURE,
        rawDerBytes: ByteArray = cert.encoded
    ): CertificateInfo {
        val subjectDn = cert.subjectX500Principal?.name ?: ""
        val issuerDn = cert.issuerX500Principal?.name ?: ""

        val citizenData = extractCitizenData(cert)
        val citizenName = citizenData.fullName
        val citizenDni = citizenData.dniNumber

        val serialNumberHex = formatSerialNumberHex(cert)
        val validFrom = cert.notBefore ?: Date(0)
        val validTo = cert.notAfter ?: Date(0)

        val now = Date()
        val isExpired = now.after(validTo) || now.before(validFrom)
        val daysRemaining = if (now.after(validTo)) {
            0L
        } else {
            ((validTo.time - now.time) / (1000L * 60L * 60L * 24L)).coerceAtLeast(0L)
        }

        val keyUsageDescription = parseKeyUsage(cert.keyUsage)
        val sha256FingerprintHex = calculateFingerprint(rawDerBytes, "SHA-256")
        val sha1FingerprintHex = calculateFingerprint(rawDerBytes, "SHA-1")

        return CertificateInfo(
            type = type,
            subjectDn = subjectDn,
            issuerDn = issuerDn,
            citizenName = citizenName,
            citizenDni = citizenDni,
            serialNumberHex = serialNumberHex,
            validFrom = validFrom,
            validTo = validTo,
            isExpired = isExpired,
            keyUsageDescription = keyUsageDescription,
            sha256FingerprintHex = sha256FingerprintHex,
            rawDerBytes = rawDerBytes,
            sha1FingerprintHex = sha1FingerprintHex,
            daysRemaining = daysRemaining
        )
    }

    /**
     * Algoritmo de extracción multi-nivel del DNI y nombres del ciudadano.
     *
     * Nivel 1: Atributo SERIALNUMBER en Subject DN (OID 2.5.4.5).
     * Nivel 2: Expresión regular de 8 dígitos en el Common Name (CN).
     * Nivel 3: Nombres alternativos del sujeto (Subject Alternative Names - SAN, OID 2.5.29.17).
     * Nivel 4: Búsqueda heurística en toda la cadena Subject DN.
     */
    fun extractCitizenData(cert: X509Certificate): CitizenData {
        val x500Name = try {
            X500Name.getInstance(cert.subjectX500Principal.encoded)
        } catch (_: Exception) {
            null
        }

        var dni: String? = null

        // 1. Verificar atributo SERIALNUMBER del Subject DN
        if (x500Name != null) {
            val serialRdns = x500Name.getRDNs(BCStyle.SERIALNUMBER)
            for (rdn in serialRdns) {
                val value = IETFUtils.valueToString(rdn.first.value)
                val match = DNI_REGEX.find(value)
                if (match != null) {
                    dni = match.value
                    break
                }
            }
        }

        // 2. Verificar Common Name (CN) con expresión regular de 8 dígitos
        var rawCn: String? = null
        if (x500Name != null) {
            val cnRdns = x500Name.getRDNs(BCStyle.CN)
            if (cnRdns.isNotEmpty()) {
                rawCn = IETFUtils.valueToString(cnRdns.first().first.value)
                if (dni == null) {
                    val match = DNI_REGEX.find(rawCn)
                    if (match != null) {
                        dni = match.value
                    }
                }
            }
        }

        // 3. Verificar Subject Alternative Name (SAN - OID 2.5.29.17)
        if (dni == null) {
            try {
                val sanCollection = cert.subjectAlternativeNames
                if (sanCollection != null) {
                    for (entry in sanCollection) {
                        if (entry.size >= 2) {
                            val second = entry[1]
                            val valueStr = when (second) {
                                is ByteArray -> String(second, Charsets.ISO_8859_1)
                                is String -> second
                                else -> second?.toString() ?: ""
                            }
                            val match = DNI_REGEX.find(valueStr)
                            if (match != null) {
                                dni = match.value
                                break
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Continuar a fallback
            }

            if (dni == null) {
                try {
                    val extBytes = cert.getExtensionValue(org.bouncycastle.asn1.x509.Extension.subjectAlternativeName.id)
                    if (extBytes != null) {
                        val octets = org.bouncycastle.asn1.ASN1OctetString.getInstance(extBytes).octets
                        val text = String(octets, Charsets.ISO_8859_1)
                        val match = DNI_REGEX.find(text)
                        if (match != null) {
                            dni = match.value
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }

        // 4. Fallback heurístico en toda la cadena Subject DN
        if (dni == null) {
            val fullSubject = cert.subjectX500Principal?.name ?: ""
            val match = DNI_REGEX.find(fullSubject)
            if (match != null) {
                dni = match.value
            }
        }

        // Extraer Organización (O)
        var organization: String? = null
        if (x500Name != null) {
            val oRdns = x500Name.getRDNs(BCStyle.O)
            if (oRdns.isNotEmpty()) {
                organization = IETFUtils.valueToString(oRdns.first().first.value)
            }
        }

        // Normalizar nombre del ciudadano
        val fullName = normalizeCitizenName(rawCn ?: cert.subjectX500Principal?.name ?: "Ciudadano")

        return CitizenData(
            fullName = fullName,
            dniNumber = dni ?: "",
            email = null,
            organization = organization
        )
    }

    /**
     * Limpia sufijos institucionales del nombre del titular, tales como:
     * "(FIRMA)", "(AUTENTICACION)", "[DNI 12345678]", etc.
     */
    fun normalizeCitizenName(rawName: String): String {
        var cleaned = NAME_CLEANUP_REGEX.replace(rawName, "").trim()
        cleaned = cleaned.replace("\\,", ",").replace("\\", "").trim()
        // Si quedó con coma al final o guiones residuales
        cleaned = cleaned.trimEnd(',', '-', ':', ' ')
        return cleaned.ifBlank { rawName.trim() }
    }

    /**
     * Parsea el mapa de bits KeyUsage en descripciones legibles.
     */
    fun parseKeyUsage(keyUsage: BooleanArray?): String {
        if (keyUsage == null || keyUsage.isEmpty()) {
            return "No especificado"
        }

        val descriptions = mutableListOf<String>()
        val labels = listOf(
            "Firma Digital (Digital Signature)",       // 0
            "No Repudio (Non-Repudiation)",            // 1
            "Cifrado de Claves (Key Encipherment)",    // 2
            "Cifrado de Datos (Data Encipherment)",    // 3
            "Acuerdo de Claves (Key Agreement)",       // 4
            "Firma de Certificados (Cert Sign)",       // 5
            "Firma CRL (CRL Sign)",                    // 6
            "Sólo Cifrado (Encipher Only)",            // 7
            "Sólo Descifrado (Decipher Only)"          // 8
        )

        for (i in 0 until minOf(keyUsage.size, labels.size)) {
            if (keyUsage[i]) {
                descriptions.add(labels[i])
            }
        }

        return if (descriptions.isEmpty()) "Ninguno" else descriptions.joinToString(", ")
    }

    /**
     * Calcula la huella digital criptográfica (thumbprint) en formato hexadecimal separado por dos puntos.
     */
    fun calculateFingerprint(derBytes: ByteArray, algorithm: String): String {
        val md = MessageDigest.getInstance(algorithm)
        val digest = md.digest(derBytes)
        return digest.joinToString(":") { "%02X".format(it) }
    }

    /**
     * Formatea el número de serie del certificado en hexadecimal con separación por dos puntos.
     */
    fun formatSerialNumberHex(cert: X509Certificate): String {
        val hex = cert.serialNumber.toString(16).uppercase()
        val padded = if (hex.length % 2 != 0) "0$hex" else hex
        return padded.chunked(2).joinToString(":")
    }
}
