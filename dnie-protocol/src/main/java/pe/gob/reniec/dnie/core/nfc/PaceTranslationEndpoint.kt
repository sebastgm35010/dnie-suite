package pe.gob.reniec.dnie.core.nfc

import pe.gob.reniec.dnie.core.apdu.CommandApdu
import pe.gob.reniec.dnie.core.crypto.*
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** PC-facing PACE-IM endpoint; physical PACE remains owned by the supplied logical channel.
 * This is a protocol gateway, not proof of a direct PC-to-chip PACE exchange. */
class PaceTranslationEndpoint(can: String, private val logical: (ByteArray) -> ByteArray) : AutoCloseable {
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
    private var secure: TerminalSecureMessaging? = null
    private val responseFragments = PcResponseFragments()
    private val certificateReads = PlainCertificateReadRoute()
    private val endpointId = java.util.UUID.randomUUID().toString()
    private var generation = 0
    var onContext: (String, String) -> Unit = { _, _ -> }
    private fun contextEvent(event: String, context: String) { try { onContext(event,context) } catch (_: Exception) {} }
    init { require(can.length == 6 && can.all { it in '0'..'9' }) }
    @Synchronized fun transmit(bytes: ByteArray): ByteArray {
        check(!closed)
        val c = CommandApdu.fromBytes(bytes)
        if (responseFragments.pending) return responseFragments.read(c)
        if ((c.cla.toInt() and 12) == 12) {
            val session = secure ?: error("Terminal SM before authentication")
            val clear = try { session.decode(bytes) } catch (failure: Exception) { close(); throw failure }
            certificateReads.reset()
            try {
                val parsed = CommandApdu.fromBytes(clear)
                // Nested PACE/rekey requires a separate state transition, not forwarding
                // to the physical card and destroying its independent secure session.
                val paceRekey = parsed.ins == 0x86.toByte() ||
                    (parsed.ins == 0x22.toByte() && parsed.p1 == 0xc1.toByte() && parsed.p2 == 0xa4.toByte())
                val response = if (paceRekey) status(0x6a80) else logical(clear)
                try { return responseFragments.begin(session.encode(response), c.le ?: 256) } finally { response.fill(0) }
            } catch (failure: Exception) { close(); throw failure }
            finally { clear.fill(0) }
        }
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
        if (stage != 0) return status(0x6985)
        if (secure != null) {
            // RENIEC switches from protected identity reads to plain public PKI
            // reads. Only its explicit IAS -> PKI -> certificate/read sequence is
            // routed, through the existing independently protected physical link.
            return try { certificateReads.transmit(c,bytes,logical) ?: status(0x6985) }
            catch (failure: Exception) { close(); throw failure }
        }
        return logical(bytes)
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
        certificateReads.reset()
        secure?.close(); secure = null; nonce.fill(0); peer.fill(0)
        responseFragments.clear()
        keys?.let { it.kEnc.fill(0); it.kMac.fill(0) }; keys = null
        ephemeral = null; group = null; stage = 0
    }
    @Synchronized override fun close() { closed = true; retire(); password.fill('\u0000') }
}
