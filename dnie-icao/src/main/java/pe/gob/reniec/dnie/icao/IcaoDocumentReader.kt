package pe.gob.reniec.dnie.icao

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import pe.gob.reniec.dnie.core.apdu.ApduConstants
import pe.gob.reniec.dnie.core.apdu.CommandApdu
import pe.gob.reniec.dnie.core.apdu.ResponseApdu
import pe.gob.reniec.dnie.core.logging.ApduLogger
import pe.gob.reniec.dnie.core.nfc.DnieCardChannel
import pe.gob.reniec.dnie.core.nfc.PaceHandler
import pe.gob.reniec.dnie.core.nfc.CardProtocolException
import kotlin.coroutines.coroutineContext

/** Dedicated fresh ICAO session. Never enters IAS/PKI, verifies PIN, or changes the signing path. */
class IcaoDocumentReader(
    private val channel: DnieCardChannel,
    private val can: String,
    private val logger: ApduLogger = ApduLogger.default,
    private val openSession: ((DnieCardChannel, String) -> DnieCardChannel)? = null
) : IcaoReader {
    override fun close() = channel.close()
    override suspend fun read(onProgress: (IcaoDocument) -> Unit): IcaoDocument = withContext(Dispatchers.IO) {
        var document = IcaoDocument()
        fun emit() { onProgress(document) }
        try {
            require(can.length == 6 && can.all { it in '0'..'9' }) { "Ingrese un CAN de 6 dígitos" }
            logger.logInfo("=== LECTURA ICAO INDEPENDIENTE: sin PIN ni operaciones PKI ===")
            val secure = openSession?.invoke(channel, can) ?: establishSession()
            val fileReader = IcaoFileReader { cmd, label -> exchange(secure, cmd, label) }
            var inventory: Set<Int>? = null
            for (file in IcaoFile.entries) {
                coroutineContext.ensureActive()
                if (file.group != null && inventory != null && file.tag !in inventory) {
                    document = document.copy(blocks = document.blocks + (file to IcaoBlock(file, BlockStatus.ABSENT,
                        message = "No anunciado en EF.COM")))
                    emit()
                    continue
                }
                document = document.copy(blocks = document.blocks + (file to IcaoBlock(file, BlockStatus.READING)))
                emit()
                var bytes: ByteArray? = null
                val block = try {
                    bytes = fileReader.read(file.fileId, file.tag, file.name)
                    val content = IcaoParser.parse(file, bytes)
                    if (content is IcaoContent.Inventory) inventory = content.tags
                    IcaoBlock(file, BlockStatus.AVAILABLE, content, bytes)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: IcaoStatusException) {
                    val status = when (e.sw) {
                        0x6A82 -> BlockStatus.ABSENT
                        0x6982, 0x6985, 0x6986 -> BlockStatus.RESTRICTED
                        else -> BlockStatus.ERROR
                    }
                    IcaoBlock(file, status, rawBytes = bytes, message = e.message)
                } catch (e: IllegalArgumentException) {
                    IcaoBlock(file, BlockStatus.ERROR, rawBytes = bytes, message = e.message)
                } catch (e: IllegalStateException) {
                    // Parser errors after a complete read do not invalidate the NFC session.
                    if (bytes == null) throw e
                    IcaoBlock(file, BlockStatus.ERROR, rawBytes = bytes, message = e.message)
                } catch (e: Exception) {
                    if (bytes == null) throw e
                    IcaoBlock(file, BlockStatus.ERROR, rawBytes = bytes, message = "No se pudo interpretar ${file.name}: ${e.message}")
                }
                document = SodVerifier.apply(document.copy(blocks = document.blocks + (file to block)))
                logger.logInfo("ICAO ${file.name}: ${block.status.label}; ${bytes?.size ?: 0} bytes")
                emit()
            }
            document = SodVerifier.apply(document.copy(completed = true))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            document = document.copy(completed = true, sessionMessage = e.message ?: "Se interrumpió la conexión NFC",
                blocks = document.blocks.mapValues { (_, block) ->
                    if (block.status in setOf(BlockStatus.PENDING, BlockStatus.READING))
                        block.copy(status = BlockStatus.INTERRUPTED, message = "Retire y vuelva a acercar el DNI") else block
                })
            document = SodVerifier.apply(document)
            logger.logInfo("ICAO: sesión interrumpida; se conservan los bloques completos.")
        } finally {
            channel.close()
        }
        emit()
        document
    }

    private suspend fun establishSession(): DnieCardChannel {
        fun requireSuccess(cmd: CommandApdu, label: String) {
            val r = exchange(channel, cmd, label)
            if (!r.isSuccess) throw IcaoStatusException(r.sw)
        }
        requireSuccess(CommandApdu(0, 0xA4.toByte(), 4, 0x0C, ApduConstants.AID_ICAO_MRTD), "ICAO: SELECT aplicación")
        requireSuccess(CommandApdu.selectMasterFile(), "ICAO: SELECT MF para CardAccess")
        val cardAccess = IcaoFileReader { cmd, label -> exchange(channel, cmd, label) }
            .read(ApduConstants.EF_CARD_ACCESS, 0x31, "CardAccess", oddFromStart = true)
        require(pe.gob.reniec.dnie.core.crypto.PaceProtocol.parseCardAccess(cardAccess) != null) {
            "CardAccess no anuncia un perfil PACE compatible; no se usarán parámetros supuestos"
        }
        coroutineContext.ensureActive()
        val secure = PaceHandler(logger).performPace(channel, can, preloadedCardAccess = cardAccess)
        val selected = exchange(secure, CommandApdu.selectDfByName(ApduConstants.AID_ICAO_MRTD),
            "ICAO: SELECT aplicación protegida")
        if (!selected.isSuccess) throw IcaoStatusException(selected.sw)
        return secure
    }

    private fun exchange(target: DnieCardChannel, cmd: CommandApdu, label: String): ResponseApdu {
        logger.logTx(cmd, label)
        val start = System.currentTimeMillis()
        val response = try {
            ResponseApdu.fromBytes(target.transceive(cmd.toByteArray()))
        } catch (e: CardProtocolException) {
            throw e
        } catch (e: Exception) {
            throw CardProtocolException("Fallo de transporte o sesión segura ICAO", cause = e)
        }
        // Identity and biometrics stay in memory, not in plaintext APDU diagnostic exports.
        logger.logRx(ResponseApdu(byteArrayOf(), response.sw1, response.sw2), System.currentTimeMillis() - start,
            "$label: ${response.data.size} bytes (contenido oculto)")
        return response
    }
}
