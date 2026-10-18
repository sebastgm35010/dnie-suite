package pe.gob.reniec.dnie.data.bridge

import android.content.Context
import android.net.Uri
import android.nfc.Tag
import android.nfc.tech.IsoDep
import pe.gob.reniec.dnie.bridge.ContactlessAtr
import pe.gob.reniec.dnie.bridge.ContactlessReaderCard
import pe.gob.reniec.dnie.bridge.BridgeMode
import pe.gob.reniec.dnie.bridge.android.BridgeConnection
import pe.gob.reniec.dnie.bridge.android.BridgePairing
import pe.gob.reniec.dnie.bridge.android.BridgeProvision
import pe.gob.reniec.dnie.core.nfc.IsoDepCardChannel

/** Android file/NFC APIs stay here; no cryptography or UI policy in this adapter. */
class BridgeAndroidAdapter(private val context: Context) {
    private val transport = BridgeTransportAdapter(context)
    fun arm(pairing: BridgePairing) = transport.arm(pairing)
    fun closeTransport() = transport.close()
    private val trace = BridgeTraceSupport(context)
    fun hasTrace() = trace.hasTrace()
    fun recordOpeningFailure(failure: Throwable, windowsPace: Boolean = false, translatePace: Boolean = false) = trace.openingFailure(failure, if (windowsPace) "pace_windows_raw" else if (translatePace) "pace_android_translated" else "pace_android_logical")
    fun clearTrace() = trace.clear()
    fun exportTrace(uri: Uri) = trace.export(uri)
    fun importQr(text: String): BridgePairing = BridgeProvision.parseQr(text)
    fun tailnet(host: String): BridgePairing = BridgePairing(host,39443,ByteArray(32),ByteArray(32),Long.MAX_VALUE,"tailscale_sync")
    fun import(uri: Uri): BridgePairing = context.contentResolver.openInputStream(uri)?.use {
        BridgeProvision.read(it)
    } ?: throw BridgeProvisionFileException("No se pudo abrir el archivo de emparejamiento seleccionado.")

    fun prepare(pairing: BridgePairing, tag: Tag, windowsPace: Boolean = false, translatePace: Boolean = false,
        settings: pe.gob.reniec.dnie.bridge.BridgeRuntimeSettings = pe.gob.reniec.dnie.bridge.BridgeRuntimeSettings()): Prepared? {
        val iso = IsoDep.get(tag) ?: return null
        // Type B needs a separate ATQB/ATTRIB mapping; never substitute a contact DNI ATR.
        val history = iso.historicalBytes ?: return null
        if (history.size > 15) return null
        val atr = ContactlessAtr.fromTypeAHistory(history)
        return Prepared(BridgeConnection(pairing,
            settings = settings,
            transportSocket = transport.socketFactory(pairing),
            smDiagnostic = { event, context -> pe.gob.reniec.dnie.diagnostics.DiagnosticLog.event("bridge",event,"flow" to context) },
            decorate = { card -> trace.decorate(ContactlessReaderCard(card, tag.id ?: byteArrayOf()), if (windowsPace) "pace_windows_raw" else if (translatePace) "pace_android_translated" else "pace_android_logical") }) {
            IsoDepCardChannel(iso, settings.nfcTimeoutMs)
        }, atr)
    }

    fun prepare(pairing: BridgePairing, tag: Tag, mode: BridgeMode,
        settings: pe.gob.reniec.dnie.bridge.BridgeRuntimeSettings = pe.gob.reniec.dnie.bridge.BridgeRuntimeSettings()): Prepared? =
        prepare(pairing, tag, mode.windowsPace, mode.translatePace, settings)

    fun awaiting(pairing: BridgePairing, mode: BridgeMode,
        settings: pe.gob.reniec.dnie.bridge.BridgeRuntimeSettings): Awaiting {
        val pending = pe.gob.reniec.dnie.bridge.android.PendingCard<Tag>()
        var iso: IsoDep? = null
        var tagId = byteArrayOf()
        val connection = BridgeConnection(pairing, settings = settings,
            transportSocket = transport.socketFactory(pairing), cancelPendingCard = pending::close,
            smDiagnostic = { event, context -> pe.gob.reniec.dnie.diagnostics.DiagnosticLog.event("bridge", event, "flow" to context) },
            decorate = { card -> trace.decorate(ContactlessReaderCard(card, tagId),
                if (mode.windowsPace) "pace_windows_raw" else if (mode.translatePace) "pace_android_translated" else "pace_android_logical") }) {
            IsoDepCardChannel(checkNotNull(iso), settings.nfcTimeoutMs)
        }
        return Awaiting(connection, pending) {
            val tag = pending.await()
            iso = checkNotNull(IsoDep.get(tag)) { "Tarjeta NFC incompatible" }
            val history = checkNotNull(iso!!.historicalBytes) { "Tarjeta NFC incompatible" }
            require(history.size <= 15)
            tagId = tag.id ?: byteArrayOf()
            ContactlessAtr.fromTypeAHistory(history)
        }
    }

    class Awaiting(val connection: BridgeConnection,
        private val pending: pe.gob.reniec.dnie.bridge.android.PendingCard<Tag>, val awaitAtr: () -> ByteArray) {
        fun offer(tag: Tag) = pending.offer(tag)
    }

    class Prepared(val connection: BridgeConnection, val atr: ByteArray)
}

class BridgeProvisionFileException(message: String, cause: Throwable? = null) : java.io.IOException(message, cause)
