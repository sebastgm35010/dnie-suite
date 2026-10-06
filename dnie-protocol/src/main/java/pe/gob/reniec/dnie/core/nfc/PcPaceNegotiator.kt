package pe.gob.reniec.dnie.core.nfc

import pe.gob.reniec.dnie.core.apdu.CommandApdu
import pe.gob.reniec.dnie.core.crypto.*
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Owns PC-side negotiation, key retirement and SM; never talks to the physical card. */
internal class PcPaceNegotiator(can: String) : AutoCloseable {
    private val password = can.toCharArray()
    private val oid = byteArrayOf(4,0,0x7f,0,7,2,2,4,4,4)
    private var stage = 0
    private var failures = 0
    private var closed = false
    private var nonce = byteArrayOf()
    private var group: MappedGroup? = null
    private var ephemeral: PaceEphemeralKeyPair? = null
    private var peer = byteArrayOf()
    private var keys: PaceSessionKeys? = null
    var secure: TerminalSecureMessaging? = null
        private set
    private val endpointId = java.util.UUID.randomUUID().toString()
    private var generation = 0
    var onContext: (String, String) -> Unit = { _, _ -> }
    private fun contextEvent(event: String, context: String) { try { onContext(event,context) } catch (_: Exception) {} }
    init { require(can.length == 6 && can.all { it in '0'..'9' }) }
    val inProgress get() = stage != 0
    fun accepts(c: CommandApdu): Boolean =
        (c.cla == 0.toByte() && c.ins == 0x22.toByte() && c.p1 == 0xc1.toByte() && c.p2 == 0xa4.toByte()) ||
            (c.ins == 0x86.toByte() && c.p1 == 0.toByte() && c.p2 == 0.toByte())
    fun transmit(c: CommandApdu): ByteArray {
        check(!closed)
        // Only observed suite/CAN/curve profile is terminated locally.
        if (c.cla == 0.toByte() && c.ins == 0x22.toByte() && c.p1 == 0xc1.toByte() && c.p2 == 0xa4.toByte()) {
            contextEvent("pc_pace_requested", "$endpointId:$generation")
            retire()
            val expected = SmTlv.encode(0x80, oid) + SmTlv.encode(0x83, byteArrayOf(2)) + SmTlv.encode(0x84, byteArrayOf(13))
            // The observed RENIEC Java client omits DO84. This endpoint implements
            // only the advertised curve-13 profile; omission selects that profile.
            // Exact encodings still reject other OIDs, password references, domains,
            // duplicated objects and unrecognized fields.
            val implicitDomain = SmTlv.encode(0x80, oid) + SmTlv.encode(0x83, byteArrayOf(2))
            if ((!c.data.contentEquals(expected) && !c.data.contentEquals(implicitDomain)) || failures >= 3) return status(0x6a80)
            stage = 1; return status(0x9000)
        }
        if (c.ins == 0x86.toByte() && c.p1 == 0.toByte() && c.p2 == 0.toByte()) {
            if (stage !in 1..4) return status(0x6985)
            try { return authenticate(c) }
            catch (_: IllegalArgumentException) { failures++; retire(); return status(0x6a80) }
        }
        error("Not a PC PACE negotiation command")
    }
    private fun authenticate(c: CommandApdu): ByteArray {
        require(c.cla == (if (stage == 4) 0 else 0x10).toByte())
        val outer = SmTlv.parse(c.data)
        require(outer.keys.toList() == listOf(0x7c))
        val fields = SmTlv.parse(outer.getValue(0x7c))
        when (stage) {
            1 -> {
                require(fields.isEmpty())
                nonce = ByteArray(32).also { SecureRandom().nextBytes(it) }
                val key = PaceProtocol.deriveKpi(String(password), 32)
                try {
                    val cipher = Cipher.getInstance("AES/CBC/NoPadding")
                    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key,"AES"), IvParameterSpec(ByteArray(16)))
                    stage = 2; return reply(0x80, cipher.doFinal(nonce))
                } finally { key.fill(0) }
            }
            2 -> {
                require(fields.keys.toList() == listOf(0x81) && fields.getValue(0x81).size == 32)
                group = PaceProtocol.computeIntegratedMappedGenerator(nonce, fields.getValue(0x81), 13)
                nonce.fill(0); nonce = byteArrayOf()
                stage = 3; return reply(0x82, byteArrayOf())
            }
            3 -> {
                require(fields.keys.toList() == listOf(0x83) && fields.getValue(0x83).size == 65)
                peer = fields.getValue(0x83)
                val mapped = checkNotNull(group)
                val pair = PaceProtocol.generateEphemeralKey(mapped)
                val shared = PaceProtocol.computeSharedSecretX(pair, peer, mapped)
                try { keys = PaceProtocol.deriveSessionKeys(shared, keyLength = 32) } finally { shared.fill(0) }
                ephemeral = pair; stage = 4; return reply(0x84, pair.publicPointBytes)
            }
            else -> {
                require(fields.keys.toList() == listOf(0x85) && fields.getValue(0x85).size == 8)
                val k = checkNotNull(keys)
                val expected = PaceProtocol.computeAuthenticationToken(k.kMac, oid, checkNotNull(ephemeral).publicPointBytes)
                if (!MessageDigest.isEqual(expected, fields.getValue(0x85))) {
                    failures++; retire(); return status(0x6300)
                }
                val token = PaceProtocol.computeAuthenticationToken(k.kMac, oid, peer)
                generation++
                val context = "$endpointId:$generation"
                secure = TerminalSecureMessaging(k.kEnc,k.kMac,context)
                contextEvent("pc_pace_ready",context)
                k.kEnc.fill(0); k.kMac.fill(0); keys = null; group = null; ephemeral = null; peer.fill(0); stage = 0
                return reply(0x86, token)
            }
        }
    }
    private fun reply(tag: Int, value: ByteArray) = SmTlv.encode(0x7c, SmTlv.encode(tag,value)) + status(0x9000)
    private fun status(sw: Int) = byteArrayOf((sw shr 8).toByte(), sw.toByte())
    private fun retire() {
        secure?.close(); secure = null; nonce.fill(0); peer.fill(0)
        keys?.let { it.kEnc.fill(0); it.kMac.fill(0) }; keys = null
        ephemeral = null; group = null; stage = 0
    }
    @Synchronized override fun close() { closed = true; retire(); password.fill('\u0000') }
}
