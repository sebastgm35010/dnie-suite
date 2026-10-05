package pe.gob.reniec.dnie.crypto

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.util.encoders.Base64
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import pe.gob.reniec.dnie.domain.model.CertificateType
import java.io.File
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Date

class X509CertificateParserTest {

    companion object {
        private lateinit var testKeyPair: KeyPair

        @BeforeClass
        @JvmStatic
        fun setUp() {
            CryptoProviderInitializer.init()
            val kpg = KeyPairGenerator.getInstance("RSA", CryptoProviderInitializer.provider)
            kpg.initialize(2048)
            testKeyPair = kpg.generateKeyPair()
        }

        private fun createTestCertificate(
            dn: String,
            issuerDn: String = "CN=AC RENIEC DNIe v3, O=RENIEC, C=PE",
            daysOffset: Int = 0,
            daysValid: Int = 365,
            keyUsage: KeyUsage = KeyUsage(KeyUsage.digitalSignature or KeyUsage.nonRepudiation),
            sanList: List<GeneralName> = emptyList()
        ): X509Certificate {
            val notBefore = Date(System.currentTimeMillis() + daysOffset * 86400000L)
            val notAfter = Date(notBefore.time + daysValid * 86400000L)
            val serialNumber = BigInteger(64, SecureRandom())

            val subPubKeyInfo = SubjectPublicKeyInfo.getInstance(testKeyPair.public.encoded)
            val certBuilder = X509v3CertificateBuilder(
                X500Name(issuerDn),
                serialNumber,
                notBefore,
                notAfter,
                X500Name(dn),
                subPubKeyInfo
            )

            certBuilder.addExtension(Extension.keyUsage, true, keyUsage)
            if (sanList.isNotEmpty()) {
                val generalNames = GeneralNames(sanList.toTypedArray())
                certBuilder.addExtension(Extension.subjectAlternativeName, false, generalNames)
            }

            val signer = JcaContentSignerBuilder("SHA256withRSA")
                .setProvider(CryptoProviderInitializer.provider)
                .build(testKeyPair.private)

            val holder = certBuilder.build(signer)
            return JcaX509CertificateConverter()
                .setProvider(CryptoProviderInitializer.provider)
                .getCertificate(holder)
        }
    }

    @Test
    fun testParseValidCertificateWithSerialNumberDni() {
        val cert = createTestCertificate(
            dn = "CN=QUISPE MAMANI\\, CARLOS ENRIQUE (FIRMA), SERIALNUMBER=47851234, C=PE, O=RENIEC"
        )

        val info = X509CertificateParser.parse(cert, CertificateType.SIGNATURE)

        assertEquals(CertificateType.SIGNATURE, info.type)
        assertEquals("47851234", info.citizenDni)
        assertEquals("QUISPE MAMANI, CARLOS ENRIQUE", info.citizenName)
        assertFalse("El certificado válido no debe figurar como expirado", info.isExpired)
        assertTrue("Los días restantes deben ser mayores a 0", info.daysRemaining > 0)
        assertTrue("KeyUsage debe contener Firma Digital", info.keyUsageDescription.contains("Firma Digital"))
        assertTrue("KeyUsage debe contener No Repudio", info.keyUsageDescription.contains("No Repudio"))
        assertTrue("Huella SHA-256 debe tener formato hex separado por :", info.sha256FingerprintHex.matches(Regex("([0-9A-F]{2}:)+[0-9A-F]{2}")))
        assertNotNull(info.serialNumberHex)
    }

    @Test
    fun testExtractDniFromCommonNameRegex() {
        val cert = createTestCertificate(
            dn = "CN=GARCIA PEREZ\\, JUAN CARLOS [72619402], C=PE, O=RENIEC"
        )

        val citizenData = X509CertificateParser.extractCitizenData(cert)

        assertEquals("72619402", citizenData.dniNumber)
        assertEquals("GARCIA PEREZ, JUAN CARLOS", citizenData.fullName)
    }

    @Test
    fun testExtractDniFromSubjectAlternativeName() {
        val san = GeneralName(GeneralName.otherName, org.bouncycastle.asn1.DERUTF8String("DNI 83920194"))
        val cert = createTestCertificate(
            dn = "CN=FLORES ROJAS\\, MARIA ELENA, C=PE, O=RENIEC",
            sanList = listOf(san)
        )

        val citizenData = X509CertificateParser.extractCitizenData(cert)

        assertEquals("83920194", citizenData.dniNumber)
        assertEquals("FLORES ROJAS, MARIA ELENA", citizenData.fullName)
    }

    @Test
    fun testExpiredCertificateDetection() {
        // Certificado emitido en el pasado y ya expirado
        val cert = createTestCertificate(
            dn = "CN=PEREZ LOPEZ\\, PEDRO, SERIALNUMBER=10293847, C=PE",
            daysOffset = -60,
            daysValid = 30
        )

        val info = X509CertificateParser.parse(cert)

        assertTrue("Certificado vencido debe tener isExpired = true", info.isExpired)
        assertEquals(0L, info.daysRemaining)
    }

    @Test
    fun testExportToCerAndPem() {
        val cert = createTestCertificate(
            dn = "CN=ALVAREZ SOTO\\, ANA (FIRMA), SERIALNUMBER=65432109, C=PE"
        )
        val derBytes = cert.encoded

        // 1. Exportar a CER
        val cerBytes = CertificateExporter.toCer(cert)
        assertArrayEquals(derBytes, cerBytes)

        // 2. Exportar a PEM
        val pemString = CertificateExporter.toPem(cert)
        assertTrue(pemString.startsWith("-----BEGIN CERTIFICATE-----\n"))
        assertTrue(pemString.endsWith("-----END CERTIFICATE-----\n"))

        // Decodificar el Base64 dentro del PEM y verificar coincidencia con el DER
        val cleanedBase64 = pemString
            .replace("-----BEGIN CERTIFICATE-----", "")
            .replace("-----END CERTIFICATE-----", "")
            .replace("\n", "")
            .trim()
        val decodedFromPem = Base64.decode(cleanedBase64)
        assertArrayEquals(derBytes, decodedFromPem)

        // 3. Exportar a archivo
        val tempCer = File.createTempFile("test_cert_", ".cer")
        val tempPem = File.createTempFile("test_cert_", ".pem")
        try {
            CertificateExporter.exportToCerFile(derBytes, tempCer)
            CertificateExporter.exportToPemFile(derBytes, tempPem)
            assertEquals(derBytes.size.toLong(), tempCer.length())
            assertTrue(tempPem.readText().contains("-----BEGIN CERTIFICATE-----"))
        } finally {
            tempCer.delete()
            tempPem.delete()
        }
    }
}
