package pe.gob.reniec.dnie.core.nfc

import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.IsoDep
import java.io.IOException

/**
 * Implementación de [DnieCardChannel] sobre la tecnología NFC nativa de Android [IsoDep].
 *
 * Configura por defecto un tiempo de espera seguro (10,000 ms) para acomodar los cálculos
 * de exponenciación modular privada RSA-2048 en tarjetas DNIe v2 y v3 sin provocar timeouts prematuros.
 */
class IsoDepCardChannel(
    private val isoDep: IsoDep,
    defaultTimeoutMs: Int = DEFAULT_TIMEOUT_MS
) : DnieCardChannel {

    init {
        try {
            if (!isoDep.isConnected) {
                isoDep.connect()
            }
            isoDep.timeout = defaultTimeoutMs
        } catch (e: Exception) {
            // Se propaga o se intentará en transceive
        }
    }

    override fun transceive(command: ByteArray): ByteArray {
        return try {
            if (!isoDep.isConnected) {
                isoDep.connect()
            }
            isoDep.transceive(command)
        } catch (e: TagLostException) {
            throw CardDisconnectedException("Tarjeta DNIe perdida o retirada del lector NFC", e)
        } catch (e: IOException) {
            throw CardDisconnectedException("Error de E/S en comunicación NFC con DNIe: ${e.message}", e)
        }
    }

    override fun isConnected(): Boolean {
        return isoDep.isConnected
    }

    override fun setTimeout(timeoutMs: Int) {
        isoDep.timeout = timeoutMs
    }

    override fun close() {
        try {
            if (isoDep.isConnected) {
                isoDep.close()
            }
        } catch (_: IOException) {
            // Ignorar excepciones al cerrar
        }
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 10000

        /**
         * Crea un [IsoDepCardChannel] a partir de un [Tag] de Android descubierto.
         * Retorna null si el tag no soporta la tecnología IsoDep.
         */
        fun fromTag(tag: Tag, timeoutMs: Int = DEFAULT_TIMEOUT_MS): IsoDepCardChannel? {
            val isoDep = IsoDep.get(tag) ?: return null
            return IsoDepCardChannel(isoDep, timeoutMs)
        }
    }
}
