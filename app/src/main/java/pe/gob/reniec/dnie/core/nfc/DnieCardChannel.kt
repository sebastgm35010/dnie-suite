package pe.gob.reniec.dnie.core.nfc

import java.io.Closeable

/**
 * Abstracción de hardware para la comunicación bidireccional APDU con el chip del DNIe.
 * Permite desacoplar la lógica de transceptor de la API nativa de Android [android.nfc.tech.IsoDep]
 * facilitando pruebas unitarias de alta fidelidad e integración en cualquier entorno.
 */
interface DnieCardChannel : Closeable {
    /**
     * Envía una trama de comando APDU a la tarjeta y retorna la trama de respuesta.
     * @param command Trama binaria del comando APDU.
     * @return Trama binaria devuelta por la tarjeta (incluye datos y SW1-SW2).
     * @throws java.io.IOException Si ocurre un fallo de transporte o desconexión física.
     */
    fun transceive(command: ByteArray): ByteArray

    /**
     * Indica si el canal de comunicación con la tarjeta se encuentra actualmente activo.
     */
    fun isConnected(): Boolean

    /**
     * Configura el tiempo de espera máximo (en milisegundos) para operaciones transceive.
     */
    fun setTimeout(timeoutMs: Int)

    /**
     * Cierra el canal y libera los recursos asociados.
     */
    override fun close()
}
