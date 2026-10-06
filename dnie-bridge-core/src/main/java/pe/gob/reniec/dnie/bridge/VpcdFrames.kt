package pe.gob.reniec.dnie.bridge

import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream

/** Independently implemented public VPCD framing; no remote plaintext socket here. */
object VpcdFrames {
    fun read(input: InputStream): ByteArray? {
        val hi = input.read()
        if (hi == -1) return null
        val lo = input.read()
        if (lo == -1) throw EOFException("Incomplete frame")
        val length = (hi shl 8) or lo
        require(length > 0) { "Empty frame" }
        val data = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val count = input.read(data, offset, length - offset)
            if (count <= 0) throw EOFException("Incomplete frame")
            offset += count
        }
        return data
    }

    fun write(output: OutputStream, data: ByteArray) {
        require(data.size in 1..65535)
        output.write(data.size ushr 8)
        output.write(data.size and 255)
        output.write(data)
        output.flush()
    }
}
