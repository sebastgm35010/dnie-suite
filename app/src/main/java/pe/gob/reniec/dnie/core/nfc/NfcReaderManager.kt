package pe.gob.reniec.dnie.core.nfc

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Administrador del ciclo de vida de lectura NFC optimizado para DNIe peruano.
 *
 * Configura Reader Mode con banderas de exclusión de NDEF y delay de presencia extendido (5000ms)
 * para evitar interrupciones por fluctuaciones en el acoplamiento inductivo RF.
 */
class NfcReaderManager(
    private val nfcAdapter: NfcAdapter?
) {
    private val _discoveredTags = MutableSharedFlow<Tag>(extraBufferCapacity = 1)
    val discoveredTags: SharedFlow<Tag> = _discoveredTags.asSharedFlow()

    val isNfcAvailable: Boolean
        get() = nfcAdapter != null

    val isNfcEnabled: Boolean
        get() = nfcAdapter?.isEnabled == true

    /**
     * Activa el modo lector de alta fidelidad en la actividad proporcionada.
     */
    fun enableReaderMode(activity: Activity) {
        val adapter = nfcAdapter ?: return

        val flags = NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK or
                NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS

        val options = Bundle().apply {
            putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, READER_PRESENCE_DELAY_MS)
        }

        adapter.enableReaderMode(
            activity,
            { tag ->
                _discoveredTags.tryEmit(tag)
            },
            flags,
            options
        )
    }

    /**
     * Desactiva el modo lector liberando el controlador NFC del dispositivo.
     */
    fun disableReaderMode(activity: Activity) {
        nfcAdapter?.disableReaderMode(activity)
    }

    companion object {
        const val READER_PRESENCE_DELAY_MS = 5000
    }
}
