package pe.gob.reniec.dnie.icao

enum class IcaoFile(val fid: Int, val tag: Int, val title: String, val group: Int? = null) {
    COM(0x011E, 0x60, "Inventario"),
    DG1(0x0101, 0x61, "Identidad", 1),
    DG11(0x010B, 0x6B, "Datos personales adicionales", 11),
    DG12(0x010C, 0x6C, "Expedición del documento", 12),
    SOD(0x011D, 0x77, "Integridad del documento"),
    DG2(0x0102, 0x75, "Fotografía", 2),
    DG5(0x0105, 0x65, "Retrato adicional", 5),
    DG7(0x0107, 0x67, "Firma manuscrita", 7),
    DG13(0x010D, 0x6D, "Datos específicos del emisor", 13);

    val fileId: ByteArray get() = byteArrayOf((fid ushr 8).toByte(), fid.toByte())
}

enum class BlockStatus(val label: String) {
    PENDING("Pendiente"), READING("Leyendo"), AVAILABLE("Disponible"),
    ABSENT("No presente"), RESTRICTED("Acceso restringido"),
    ERROR("Error en este bloque"), INTERRUPTED("Lectura interrumpida")
}

enum class HashStatus(val label: String) {
    NOT_CHECKED("Integridad sin comprobar"), MATCH("Hash coincide con EF.SOD"),
    MISMATCH("El hash no coincide con EF.SOD"), NOT_LISTED("Sin hash en EF.SOD")
}

sealed interface IcaoContent {
    data class Inventory(val tags: Set<Int>, val ldsVersion: String?) : IcaoContent
    data class Identity(
        val documentType: String, val issuingState: String, val documentNumber: String,
        val surnames: String, val givenNames: String, val nationality: String,
        val birthDateMrz: String, val sex: String, val expiryDateMrz: String,
        val optionalData: String, val checkDigits: Map<String, Boolean>, val mrz: String
    ) : IcaoContent
    data class Details(val fields: Map<String, String>) : IcaoContent
    data class Images(val images: List<IcaoImage>) : IcaoContent
    data class Opaque(val byteCount: Int) : IcaoContent
    data class Security(
        val digestAlgorithm: String, val hashes: Map<Int, ByteArray>,
        val signatureValid: Boolean, val signerSubject: String,
        val signerIssuer: String,
        // Embedded DSC verification is NOT trust-chain validation or chip authentication.
        val issuerTrustVerified: Boolean = false
    ) : IcaoContent
}

data class IcaoImage(val bytes: ByteArray, val mimeType: String, val width: Int? = null, val height: Int? = null)

data class IcaoBlock(
    val file: IcaoFile, val status: BlockStatus = BlockStatus.PENDING,
    val content: IcaoContent? = null, val rawBytes: ByteArray? = null,
    val message: String? = null, val hashStatus: HashStatus = HashStatus.NOT_CHECKED
)

data class IcaoDocument(
    val blocks: Map<IcaoFile, IcaoBlock> = IcaoFile.entries.associateWith { IcaoBlock(it) },
    val sessionMessage: String? = null, val completed: Boolean = false
)

interface IcaoReader : AutoCloseable {
    suspend fun read(onProgress: (IcaoDocument) -> Unit = {}): IcaoDocument
    override fun close() {}
}
