package pe.gob.reniec.dnie.data.bridge

import android.content.Context
import android.net.Uri
import android.nfc.Tag
import android.nfc.tech.IsoDep
import pe.gob.reniec.dnie.bridge.ContactlessAtr
import pe.gob.reniec.dnie.bridge.ContactlessReaderCard
import pe.gob.reniec.dnie.bridge.android.BridgeConnection
import pe.gob.reniec.dnie.bridge.android.BridgePairing
import pe.gob.reniec.dnie.bridge.android.BridgeProvision
import pe.gob.reniec.dnie.core.nfc.IsoDepCardChannel

/** Android file/NFC APIs stay here; no cryptography or UI policy in this adapter. */
class BridgeAndroidAdapter(private val context: Context) {
    private val traceFile get() = java.io.File(context.filesDir, "bridge-apdu-trace.jsonl")
    fun hasTrace(): Boolean = traceFile.exists() && traceFile.length() > 0
    fun recordOpeningFailure(failure: Throwable, windowsPace: Boolean = false, translatePace: Boolean = false) = BridgeApduTrace.openingFailure(traceFile, failure, if (windowsPace) "pace_windows_raw" else if (translatePace) "pace_android_translated" else "pace_android_logical")
    fun clearTrace() { if (traceFile.exists() && !traceFile.delete()) error("Cannot clear trace") }
    fun exportTrace(uri: Uri) {
        context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
            traceFile.inputStream().use { it.copyTo(output) }
        } ?: error("Cannot export trace")
    }
    fun importQr(text: String): BridgePairing = BridgeProvision.parseQr(text)
    fun tailnet(host: String): BridgePairing = BridgePairing(host,39443,ByteArray(32),ByteArray(32),Long.MAX_VALUE,"tailscale_sync")
    fun import(uri: Uri): BridgePairing = context.contentResolver.openInputStream(uri)?.use {
        BridgeProvision.read(it)
    } ?: error("Missing provisioning file")

    fun prepare(pairing: BridgePairing, tag: Tag, windowsPace: Boolean = false, translatePace: Boolean = false): Prepared? {
        val iso = IsoDep.get(tag) ?: return null
        // Type B needs a separate ATQB/ATTRIB mapping; never substitute a contact DNI ATR.
        val history = iso.historicalBytes ?: return null
        if (history.size > 15) return null
        val atr = ContactlessAtr.fromTypeAHistory(history)
        return Prepared(BridgeConnection(pairing,
            smDiagnostic = { event, context -> pe.gob.reniec.dnie.diagnostics.DiagnosticLog.event("bridge",event,"flow" to context) },
            decorate = { card -> BridgeApduTrace(ContactlessReaderCard(card, tag.id ?: byteArrayOf()), traceFile, if (windowsPace) "pace_windows_raw" else if (translatePace) "pace_android_translated" else "pace_android_logical") }) {
            IsoDepCardChannel(iso)
        }, atr)
    }

    class Prepared(val connection: BridgeConnection, val atr: ByteArray)
}
