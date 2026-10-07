package pe.gob.reniec.dnie.timestamp

import java.net.HttpURLConnection
import java.net.URI
import java.io.ByteArrayOutputStream
import java.io.InterruptedIOException

fun interface TimestampClient { fun request(query: ByteArray): ByteArray }

/** Fixed RENIEC RFC3161 endpoint. Only an imprint/nonce/policy is transmitted. */
class ReniecTimestampClient : TimestampClient {
    override fun request(query: ByteArray): ByteArray {
        require(query.size in 1..4096)
        val connection = URI(ENDPOINT).toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = 7000; connection.readTimeout = 10000
        connection.instanceFollowRedirects = false
        connection.requestMethod = "POST"; connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/timestamp-query")
        connection.setRequestProperty("Accept", "application/timestamp-reply")
        connection.setFixedLengthStreamingMode(query.size)
        val deadline = System.nanoTime() + 20_000_000_000L
        try {
            connection.outputStream.use { it.write(query) }
            require(connection.responseCode == 200) { "TSA RENIEC no disponible" }
            require(connection.contentLengthLong <= MAX_RESPONSE)
            return connection.inputStream.use { input ->
                val out = ByteArrayOutputStream(); val buffer = ByteArray(8192)
                while (true) {
                    if (Thread.currentThread().isInterrupted || System.nanoTime() > deadline) throw InterruptedIOException()
                    val count = input.read(buffer); if (count < 0) break
                    require(out.size() + count <= MAX_RESPONSE); out.write(buffer, 0, count)
                }
                out.toByteArray()
            }
        } finally { connection.disconnect() }
    }
    companion object {
        const val ENDPOINT = "https://tsa.reniec.gob.pe/service"
        const val MAX_RESPONSE = 2 * 1024 * 1024
    }
}
