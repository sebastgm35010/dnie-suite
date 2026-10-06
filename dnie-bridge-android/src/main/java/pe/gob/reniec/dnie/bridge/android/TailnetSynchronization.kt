package pe.gob.reniec.dnie.bridge.android

import java.io.InputStream
import java.io.OutputStream

/** One socket, one fresh token; only called after resolving a tailnet address. */
internal object TailnetSynchronization {
    fun request(input: InputStream, output: OutputStream): ByteArray {
        val magic = "DNISYNC1".toByteArray(Charsets.US_ASCII)
        output.write(magic)
        output.flush()
        val reply = ByteArray(40)
        try {
            var offset = 0
            while (offset < reply.size) {
                val count = input.read(reply, offset, reply.size-offset)
                check(count > 0) { "Tailnet synchronization interrupted" }
                offset += count
            }
            check(reply.copyOfRange(0,8).contentEquals(magic)) { "Unsupported tailnet synchronization" }
            return reply.copyOfRange(8,40)
        } finally { reply.fill(0) }
    }
}
