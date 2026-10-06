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
    @Volatile private var closed = false
    private var timeoutConfigured = false
    override val extendedLengthSupported: Boolean get() = isoDep.isExtendedLengthApduSupported
    override val maxCommandLength: Int get() = isoDep.maxTransceiveLength

    // Construction happens in Activity; defer blocking connect until transceive on IO.
    private var connectionTimeoutMs = defaultTimeoutMs

    override fun transceive(command: ByteArray): ByteArray {
        if (closed) throw CardDisconnectedException("La sesión NFC ya está cerrada")
        return try {
            if (!isoDep.isConnected) {
                isoDep.connect()
                timeoutConfigured = false
            }
            if (closed) {
                // Cancellation can race a blocking connect. Retire a connection opened late.
                try { isoDep.close() } catch (_: IOException) { }
                throw CardDisconnectedException("La sesión NFC ya está cerrada")
            }
            if (!timeoutConfigured) {
                isoDep.timeout = connectionTimeoutMs
                timeoutConfigured = true
            }
            if (command.size > isoDep.maxTransceiveLength) {
                throw CardProtocolException("El lector NFC no admite este comando (${command.size} bytes; máximo ${isoDep.maxTransceiveLength})")
            }
            if (command.size > 5 && command[4] == 0.toByte() && !isoDep.isExtendedLengthApduSupported) {
                throw CardProtocolException("El lector NFC no admite APDU extendidas")
            }
            isoDep.transceive(command)
        } catch (e: TagLostException) {
            throw CardDisconnectedException("Tarjeta DNIe perdida o retirada del lector NFC", e)
        } catch (e: IOException) {
            throw CardDisconnectedException("Error de E/S en comunicación NFC con DNIe: ${e.message}", e)
        }
    }

    override fun isConnected(): Boolean {
        return !closed && isoDep.isConnected
    }

    override fun setTimeout(timeoutMs: Int) {
        connectionTimeoutMs = timeoutMs
        timeoutConfigured = false
    }

    override fun close() {
        closed = true
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
