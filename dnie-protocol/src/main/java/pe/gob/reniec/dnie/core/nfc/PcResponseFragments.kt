package pe.gob.reniec.dnie.core.nfc

import pe.gob.reniec.dnie.core.apdu.CommandApdu

/** Owns PC-facing fragments only; physical GET RESPONSE is handled by the physical channel.
 * Called under the endpoint lock. Draining never advances SSC or transmits to the DNI. */
internal class PcResponseFragments {
    private var continuation = byteArrayOf()
    val pending: Boolean get() = continuation.isNotEmpty()

    fun read(command: CommandApdu): ByteArray {
        require(command.cla == 0.toByte() && command.ins == 0xc0.toByte() &&
            command.p1 == 0.toByte() && command.p2 == 0.toByte() && command.data.isEmpty())
        return begin(continuation, command.le ?: 256)
    }

    /** Takes ownership of the encoded response and zeroes consumed buffers. */
    fun begin(response: ByteArray, le: Int): ByteArray {
        if (response.size - 2 <= le) {
            val result = response.copyOf()
            clear()
            response.fill(0)
            return result
        }
        val remaining = minOf(255, response.size - 2 - le)
        val result = response.copyOfRange(0, le) + byteArrayOf(0x61, remaining.toByte())
        val rest = response.copyOfRange(le, response.size)
        clear()
        continuation = rest
        response.fill(0)
        return result
    }

    fun clear() {
        continuation.fill(0)
        continuation = byteArrayOf()
    }
}
