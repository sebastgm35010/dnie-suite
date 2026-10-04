package pe.gob.reniec.dnie.core.apdu

/**
 * Constantes de protocolo APDU ISO 7816-4 específicas para el chip del DNIe Peruano v2/v3.
 *
 * Basado en las especificaciones oficiales de RENIEC PKI y las normas ISO/IEC 7816-4 e ISO/IEC 14443-4.
 */
object ApduConstants {

    /**
     * Application Identifier (AID) del Applet PKI de RENIEC:
     * RID: E8 28 BD 08 0F (Perú)
     * PIX: D2 50 47 65 6E 65 72 69 63 ("PGeneric")
     */
    val AID_RENIEC_PKI: ByteArray = byteArrayOf(
        0xE8.toByte(), 0x28.toByte(), 0xBD.toByte(), 0x08.toByte(), 0x0F.toByte(),
        0xD2.toByte(), 0x50.toByte(), 0x47.toByte(), 0x65.toByte(), 0x6E.toByte(),
        0x65.toByte(), 0x72.toByte(), 0x69.toByte(), 0x63.toByte()
    )

    // Clases APDU (CLA)
    const val CLA_ISO7816: Byte = 0x00.toByte()

    // Códigos de Instrucción (INS)
    const val INS_SELECT: Byte = 0xA4.toByte()
    const val INS_VERIFY: Byte = 0x20.toByte()
    const val INS_MSE: Byte = 0x22.toByte()
    const val INS_PSO: Byte = 0x2A.toByte()
    const val INS_READ_BINARY_DO: Byte = 0xB1.toByte()

    // Parámetros SELECT FILE
    const val P1_SELECT_DF_BY_NAME: Byte = 0x04.toByte()
    const val P2_SELECT_FIRST_RECORD: Byte = 0x00.toByte()
    const val P1_SELECT_EF_UNDER_DF: Byte = 0x02.toByte()
    const val P2_SELECT_NO_FCI: Byte = 0x04.toByte()

    // Selectores de Archivos Elementales (EF File IDs)
    val EF_SIGNATURE: ByteArray = byteArrayOf(0x00, 0x1D)
    val EF_AUTHENTICATION: ByteArray = byteArrayOf(0x00, 0x1C)
    val EF_ENCRYPTION: ByteArray = byteArrayOf(0x00, 0x1B)

    // Parámetros READ BINARY con Data Object (DO)
    const val TAG_OFFSET_DO: Byte = 0x54.toByte()
    const val TAG_BER_TLV_DATA: Byte = 0x53.toByte()
    const val DEFAULT_BLOCK_SIZE: Int = 0x00E4 // 228 bytes

    // Tipos de PIN
    const val PIN_TYPE_SIGNATURE: Byte = 0x81.toByte()
    const val PIN_TYPE_AUTHENTICATION: Byte = 0x83.toByte()

    // Manage Security Environment (MSE: SET DST)
    const val P1_MSE_SET: Byte = 0x41.toByte()
    const val P2_MSE_DST: Byte = 0xB6.toByte()

    /**
     * Datos de configuración MSE para firma digital RSA-2048 con SHA-256 PKCS#1 v1.5:
     * Tag 0x80 (Algorithm Reference): 0x8A (RSA SHA-256)
     * Tag 0x84 (Key Reference): 0x81 (Signature Private Key)
     */
    val MSE_SET_DST_DATA: ByteArray = byteArrayOf(
        0x80.toByte(), 0x01.toByte(), 0x8A.toByte(),
        0x84.toByte(), 0x01.toByte(), 0x81.toByte()
    )

    // Perform Security Operation (PSO: Compute Digital Signature)
    const val P1_PSO_CDS: Byte = 0x9E.toByte()
    const val P2_PSO_CDS_DIGEST: Byte = 0x9A.toByte()

    /**
     * Prefijo ASN.1 DER DigestInfo de 19 bytes para resúmenes criptográficos SHA-256:
     * SEQUENCE(49) {
     *   AlgorithmIdentifier { OID 2.16.840.1.101.3.4.2.1, NULL }
     *   OCTET STRING (32 bytes)
     * }
     */
    val SHA256_DIGEST_INFO_PREFIX: ByteArray = byteArrayOf(
        0x30, 0x31, 0x30, 0x0D, 0x06, 0x09, 0x60, 0x86.toByte(), 0x48, 0x01,
        0x65, 0x03, 0x04, 0x02, 0x01, 0x05, 0x00, 0x04, 0x20
    )

    const val SHA256_HASH_SIZE: Int = 32
    const val RSA_2048_SIGNATURE_SIZE: Int = 256
    const val PSO_CDS_PAYLOAD_SIZE: Int = 51 // 19 + 32
}
