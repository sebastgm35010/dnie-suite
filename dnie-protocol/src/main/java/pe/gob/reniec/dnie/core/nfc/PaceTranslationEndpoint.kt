package pe.gob.reniec.dnie.core.nfc

import pe.gob.reniec.dnie.core.apdu.CommandApdu

/** Routes PC APDU while negotiation, response fragments and physical SM have separate owners. */
class PaceTranslationEndpoint(can: String, private val logical: (ByteArray) -> ByteArray) : AutoCloseable {
    private val negotiation = PcPaceNegotiator(can)
    private val responseFragments = PcResponseFragments()
    private val certificateReads = PlainCertificateReadRoute()
    private var closed = false
    var onContext: (String, String) -> Unit
        get() = negotiation.onContext
        set(value) { negotiation.onContext = value }

    @Synchronized fun transmit(bytes: ByteArray): ByteArray {
        check(!closed)
        val command = CommandApdu.fromBytes(bytes)
        if (responseFragments.pending) return responseFragments.read(command)
        if ((command.cla.toInt() and 12) == 12) {
            // A reset retires PC keys. Ask the client to authenticate again without
            // forwarding stale ciphertext or closing a healthy physical session.
            val session = negotiation.secure ?: return status(0x6985)
            val clear = try { session.decode(bytes) } catch (failure: Exception) { close(); throw failure }
            certificateReads.reset()
            try {
                val parsed = CommandApdu.fromBytes(clear)
                // A nested rekey must not retire the independently protected physical session.
                val paceRekey = parsed.ins == 0x86.toByte() ||
                    (parsed.ins == 0x22.toByte() && parsed.p1 == 0xc1.toByte() && parsed.p2 == 0xa4.toByte())
                val response = if (paceRekey) status(0x6a80) else logical(clear)
                try { return responseFragments.begin(session.encode(response), command.le ?: 256) }
                finally { response.fill(0) }
            } catch (failure: Exception) { close(); throw failure }
            finally { clear.fill(0) }
        }
        if (negotiation.accepts(command)) {
            certificateReads.reset()
            responseFragments.clear()
            return negotiation.transmit(command)
        }
        if (negotiation.inProgress) return status(0x6985)
        if (negotiation.secure != null) {
            return try { certificateReads.transmit(command, bytes, logical) ?: status(0x6985) }
            catch (failure: Exception) { close(); throw failure }
        }
        return logical(bytes)
    }

    private fun status(sw: Int) = byteArrayOf((sw shr 8).toByte(), sw.toByte())

    @Synchronized override fun close() {
        closed = true
        certificateReads.reset()
        responseFragments.clear()
        negotiation.close()
    }
}
