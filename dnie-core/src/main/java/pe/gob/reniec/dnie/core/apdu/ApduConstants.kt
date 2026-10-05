package pe.gob.reniec.dnie.core.apdu

/**
 * Constantes de protocolo APDU ISO 7816-4 específicas para el chip del DNIe Peruano v2/v3.
 *
 * Basado en las especificaciones oficiales de RENIEC PKI y las normas ISO/IEC 7816-4 e ISO/IEC 14443-4.
 */
object ApduConstants {

    /**
     * Application Identifiers (AIDs) conocidos para DNIe de Perú (v2 y v3):
     */
    // DNIe v3.0: Instancia PKI oficial RENIEC (Oberthur/IDEMIA CosmopolIC)
    val AID_RENIEC_PKI_V3_IDEMIA: ByteArray = byteArrayOf(
        0xA0.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x77.toByte(),
        0x01.toByte(), 0x00.toByte(), 0x70.toByte(), 0x0A.toByte(), 0x10.toByte(),
        0x00.toByte(), 0xF1.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(),
        0x00.toByte()
    )

    // DNIe v3.0: IAS-ECC IDEMIA Cosmo estándar
    val AID_IAS_ECC_IDEMIA: ByteArray = byteArrayOf(
        0xA0.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x77.toByte(),
        0x01.toByte(), 0x08.toByte(), 0x00.toByte(), 0x07.toByte(), 0x00.toByte(),
        0x00.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(),
        0x00.toByte()
    )

    // DNIe v3.0: IAS-ECC Raíz
    val AID_IAS_ECC_ROOT: ByteArray = byteArrayOf(
        0xA0.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x77.toByte(),
        0x01.toByte()
    )

    // DNIe v3.0: ICAO LDS1 eMRTD (Documento de Viaje / Datos Ciudadanos ICAO 9303)
    val AID_ICAO_MRTD: ByteArray = byteArrayOf(
        0xA0.toByte(), 0x00.toByte(), 0x00.toByte(), 0x02.toByte(), 0x47.toByte(),
        0x10.toByte(), 0x01.toByte()
    )

    // DNIe v2.0: Applet PKI clásico de RENIEC ("PGeneric")
    val AID_RENIEC_PKI_V2: ByteArray = byteArrayOf(
        0xE8.toByte(), 0x28.toByte(), 0xBD.toByte(), 0x08.toByte(), 0x0F.toByte(),
        0xD2.toByte(), 0x50.toByte(), 0x47.toByte(), 0x65.toByte(), 0x6E.toByte(),
        0x65.toByte(), 0x72.toByte(), 0x69.toByte(), 0x63.toByte()
    )

    // DNIe v2.0: RID RENIEC corto
    val AID_RENIEC_SHORT: ByteArray = byteArrayOf(
        0xE8.toByte(), 0x28.toByte(), 0xBD.toByte(), 0x08.toByte(), 0x0F.toByte()
    )

    // Estándar eID PKCS#15
    val AID_PKCS15: ByteArray = byteArrayOf(
        0xA0.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x18.toByte(),
        0x00.toByte(), 0x00.toByte(), 0x01.toByte()
    )

    // Alias retrocompatible
    val AID_RENIEC_PKI: ByteArray = AID_RENIEC_PKI_V2

    /**
     * Lista priorizada de AIDs candidatos para auto-detección de DNIe v2 y v3.
     */
    val CANDIDATE_AIDS: List<Pair<String, ByteArray>> = listOf(
        Pair("DNIe v3 ICAO eMRTD", AID_ICAO_MRTD),
        Pair("DNIe v3 PKI (RENIEC IDEMIA)", AID_RENIEC_PKI_V3_IDEMIA),
        Pair("DNIe v3 IAS-ECC (IDEMIA Cosmo)", AID_IAS_ECC_IDEMIA),
        Pair("DNIe v3 IAS-ECC (Raíz)", AID_IAS_ECC_ROOT),
        Pair("DNIe v2 PKI (PGeneric)", AID_RENIEC_PKI_V2),
        Pair("DNIe v2 RID (RENIEC)", AID_RENIEC_SHORT),
        Pair("eID PKCS#15 Genérico", AID_PKCS15)
    )

    // Clases APDU (CLA)
    const val CLA_ISO7816: Byte = 0x00.toByte()

    // Códigos de Instrucción (INS)
    const val INS_SELECT: Byte = 0xA4.toByte()
    const val INS_VERIFY: Byte = 0x20.toByte()
    const val INS_MSE: Byte = 0x22.toByte()
    const val INS_PSO: Byte = 0x2A.toByte()
    const val INS_READ_BINARY: Byte = 0xB0.toByte()
    const val INS_READ_BINARY_DO: Byte = 0xB1.toByte()
    const val INS_GENERAL_AUTHENTICATE: Byte = 0x86.toByte()

    // Parámetros SELECT FILE
    const val P1_SELECT_MF_OR_DF_DIRECT: Byte = 0x00.toByte()
    const val P1_SELECT_CHILD_DF: Byte = 0x01.toByte()
    const val P1_SELECT_DF_BY_NAME: Byte = 0x04.toByte()
    const val P2_SELECT_FIRST_RECORD: Byte = 0x00.toByte()
    const val P1_SELECT_EF_UNDER_DF: Byte = 0x02.toByte()
    const val P2_SELECT_NO_FCI: Byte = 0x0C.toByte()

    // Directorios Dedicados (DF) conocidos bajo Master File (3F 00)
    val DF_IDEMIA_PKCS15_5011: ByteArray = byteArrayOf(0x50.toByte(), 0x11.toByte())
    val DF_IDEMIA_PKI: ByteArray = byteArrayOf(0x70.toByte(), 0x01.toByte())
    val DF_PKCS15: ByteArray = byteArrayOf(0x50.toByte(), 0x15.toByte())

    val CANDIDATE_DFS: List<Pair<String, ByteArray>> = listOf(
        Pair("IDEMIA PKCS#15 (50 11)", DF_IDEMIA_PKCS15_5011),
        Pair("IDEMIA PKI (70 01)", DF_IDEMIA_PKI),
        Pair("PKCS#15 (50 15)", DF_PKCS15)
    )

    // Selectores de Archivos Elementales (EF File IDs)
    val EF_SIGNATURE: ByteArray = byteArrayOf(0x00, 0x1D)
    val EF_AUTHENTICATION: ByteArray = byteArrayOf(0x00, 0x1C)
    val EF_ENCRYPTION: ByteArray = byteArrayOf(0x00, 0x1B)
    val EF_COSMO_SIGNATURE: ByteArray = byteArrayOf(0x20.toByte(), 0x01.toByte())
    val EF_COSMO_AUTHENTICATION: ByteArray = byteArrayOf(0x20.toByte(), 0x00.toByte())
    val EF_IAS_ECC_341F: ByteArray = byteArrayOf(0x34.toByte(), 0x1F.toByte())
    val EF_IAS_ECC_3401: ByteArray = byteArrayOf(0x34.toByte(), 0x01.toByte())
    val EF_CARD_ACCESS: ByteArray = byteArrayOf(0x01.toByte(), 0x1C.toByte())
    val EF_SOD: ByteArray = byteArrayOf(0x01.toByte(), 0x1D.toByte())
    val EF_DG1: ByteArray = byteArrayOf(0x01.toByte(), 0x01.toByte())

    // Parámetros READ BINARY con Data Object (DO)
    const val TAG_OFFSET_DO: Byte = 0x54.toByte()
    const val TAG_BER_TLV_DATA: Byte = 0x53.toByte()
    const val DEFAULT_BLOCK_SIZE: Int = 0x00E4 // 228 bytes

    // PACE Constants (ICAO Doc 9303 Part 11 / BSI TR-03110)
    const val P1_MSE_SET_AT: Byte = 0xC1.toByte()
    const val P2_MSE_SET_AT: Byte = 0xA4.toByte()
    const val PACE_PWD_MRZ: Byte = 0x01.toByte()
    const val PACE_PWD_CAN: Byte = 0x02.toByte()
    const val PACE_PWD_PIN: Byte = 0x03.toByte()
    val OID_PACE_ECDH_GM_AES128: ByteArray = byteArrayOf(
        0x04.toByte(), 0x00.toByte(), 0x7F.toByte(), 0x00.toByte(), 0x07.toByte(),
        0x02.toByte(), 0x02.toByte(), 0x04.toByte(), 0x02.toByte(), 0x02.toByte()
    )
    val OID_PACE_ECDH_GM_AES256: ByteArray = byteArrayOf(
        0x04.toByte(), 0x00.toByte(), 0x7F.toByte(), 0x00.toByte(), 0x07.toByte(),
        0x02.toByte(), 0x02.toByte(), 0x04.toByte(), 0x02.toByte(), 0x04.toByte()
    )
    val OID_PACE_ECDH_IM_AES256: ByteArray = byteArrayOf(
        0x04.toByte(), 0x00.toByte(), 0x7F.toByte(), 0x00.toByte(), 0x07.toByte(),
        0x02.toByte(), 0x02.toByte(), 0x04.toByte(), 0x04.toByte(), 0x04.toByte()
    )

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
