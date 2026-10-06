package pe.gob.reniec.dnie.bridge.android

import org.bouncycastle.util.encoders.Base64
import org.json.JSONObject
import java.io.InputStream
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Bounded, in-memory import. Never persist or include the provisioning content in errors. */
object BridgeProvision {
    fun parseQr(text: String, now: Long = System.currentTimeMillis()): BridgePairing {
        var decoded: ByteArray? = null
        try {
            require(text.length <= 2048 && text.startsWith("DNIEBRIDGE1:"))
            val encoded = text.removePrefix("DNIEBRIDGE1:")
            require(encoded.isNotEmpty() && encoded.matches(Regex("[A-Za-z0-9_-]+")))
            decoded = Base64.decode(encoded.replace('-', '+').replace('_', '/').let {
                it + "=".repeat((4 - it.length % 4) % 4)
            })
            val pairing = parse(decoded.toString(Charsets.UTF_8), now)
            if (pairing.expiresAtEpochMillis - now > 15 * 60_000L) {
                pairing.close(); error("QR validity too long")
            }
            return pairing
        } catch (_: Exception) { throw IllegalArgumentException("QR del puente inválido o vencido") }
        finally { decoded?.fill(0) }
    }

    fun read(input: InputStream, now: Long = System.currentTimeMillis()): BridgePairing {
        val bytes = input.readBytesBounded()
        return try { parse(bytes.toString(Charsets.UTF_8), now) } finally { bytes.fill(0) }
    }

    fun parse(text: String, now: Long = System.currentTimeMillis()): BridgePairing {
        var token: ByteArray? = null
        var pin: ByteArray? = null
        try {
            require(text.toByteArray(Charsets.UTF_8).size <= 8192)
            val json = JSONObject(text)
            require(json.get("version") == 1)
            val host = json.getString("host")
            val transport = json.optString("transport", "tls")
            // Provisioning comes from the Windows setup command, which uses a literal IP.
            require(if (transport == "tailscale_tcp") TailnetAddress.validHost(host)
                else host.length in 2..64 && host.all { it in "0123456789abcdefABCDEF:." })
            val port = json.get("port")
            require(port is Int)
            val hex = json.getString("certificateSha256")
            require(hex.matches(Regex("[0-9a-fA-F]{64}")))
            pin = ByteArray(32) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
            val encoded = json.getString("pairingTokenBase64")
            require(encoded.matches(Regex("[A-Za-z0-9+/]{43}=")))
            token = Base64.decode(encoded)
            require(token.size == 32)
            val expires = utcDate(json.getString("expiresAt"))
            require(expires > now)
            return BridgePairing(host, port, pin, token, expires, transport)
        } catch (_: Exception) {
            throw IllegalArgumentException("Emparejamiento inválido o vencido")
        } finally { token?.fill(0); pin?.fill(0) }
    }

    private fun utcDate(value: String): Long {
        val match = Regex("(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2})(?:\\.(\\d{1,7}))?Z").matchEntire(value)
            ?: error("Invalid UTC date")
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC"); isLenient = false
        }
        val position = ParsePosition(0)
        val seconds = match.groupValues[1]
        val date = format.parse(seconds, position) ?: error("Invalid date")
        require(position.index == seconds.length)
        return date.time + match.groupValues[2].padEnd(3, '0').take(3).toInt()
    }

    private fun InputStream.readBytesBounded(): ByteArray {
        val bytes = ByteArray(8193)
        try {
            var size = 0
            while (size < bytes.size) {
                val count = read(bytes, size, bytes.size - size)
                if (count < 0) break
                if (count == 0) { val next = read(); if (next < 0) break; bytes[size++] = next.toByte() }
                else size += count
            }
            require(size <= 8192) { "Emparejamiento demasiado grande" }
            return bytes.copyOf(size)
        } finally { bytes.fill(0) }
    }
}
