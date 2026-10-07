package pe.gob.reniec.dnie.validation

import java.security.MessageDigest
import java.security.cert.X509Certificate

/** Session memory only. UNKNOWN cannot overwrite authenticated revocation evidence. */
object KnownRevocationPolicy {
    private val states = object : LinkedHashMap<String, RevocationStatus>(256,0.75f,true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String,RevocationStatus>?) = size > 256
    }
    private fun key(cert: X509Certificate) = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
        .joinToString("") { "%02x".format(it) }
    @Synchronized internal fun recordVerified(cert: X509Certificate, status: RevocationStatus) {
        if(status != RevocationStatus.UNKNOWN) states[key(cert)] = status
    }
    @Synchronized fun requireNotKnownRevoked(cert: X509Certificate) {
        require(states[key(cert)] != RevocationStatus.REVOKED) { "Certificado revocado según evidencia verificada" }
    }
}
