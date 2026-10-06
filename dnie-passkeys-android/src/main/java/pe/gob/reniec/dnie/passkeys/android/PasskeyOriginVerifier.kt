package pe.gob.reniec.dnie.passkeys.android

import android.content.Context
import androidx.annotation.RequiresApi
import androidx.credentials.provider.CallingAppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pe.gob.reniec.dnie.passkeys.*
import java.net.URL
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection

/** Browser identity is verified by AndroidX; native apps must be delegated by the RP's DAL. */
@RequiresApi(34)
class PasskeyOriginVerifier(private val context: Context) {
    suspend fun verify(info: CallingAppInfo, rp: String, hash: ByteArray?): PasskeyCaller = withContext(Dispatchers.IO) {
        pe.gob.reniec.dnie.diagnostics.DiagnosticLog.event("passkeys", "origin_check", "package" to info.packageName, "rp" to rp)
        val allowlist = context.assets.open("privileged-passkey-callers.json").bufferedReader().use { it.readText() }
        val origin = validation(PasskeyValidationCode.BROWSER_IDENTITY) { info.getOrigin(allowlist) }
        if (origin != null) {
            if (hash?.size != 32) throw PasskeyValidationFailure(PasskeyValidationCode.BROWSER_HASH)
            return@withContext validation(PasskeyValidationCode.ORIGIN_INVALID) { PasskeyCaller(origin, hash.copyOf()) }
        }
        if (hash != null) throw PasskeyValidationFailure(PasskeyValidationCode.NATIVE_HASH)
        val signers = info.signingInfo.apkContentsSigners
        if (signers.size != 1) throw PasskeyValidationFailure(PasskeyValidationCode.NATIVE_SIGNER)
        val digest = MessageDigest.getInstance("SHA-256").digest(signers.single().toByteArray())
        val fingerprint = digest.joinToString(":") { "%02X".format(it.toInt() and 255) }
        val identity = PasskeyDelegationCache.Identity(rp, info.packageName, fingerprint)
        if (delegations.contains(identity)) {
            pe.gob.reniec.dnie.diagnostics.DiagnosticLog.event("passkeys", "dal_cached")
            return@withContext PasskeyCaller("android:apk-key-hash:${PasskeyEncoding.encode(digest)}", null)
        }
        val started = System.nanoTime()
        val connection = URL("https://$rp/.well-known/assetlinks.json").openConnection() as HttpsURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 8000; connection.readTimeout = 8000
            val status = validation(PasskeyValidationCode.DAL_NETWORK) { connection.responseCode }
            if (status != 200) throw PasskeyValidationFailure(PasskeyValidationCode.DAL_DENIED)
            val bytes = validation(PasskeyValidationCode.DAL_NETWORK) {
                connection.inputStream.use { it.readNBytes(524289) }.also { require(it.size <= 524288) }
            }
            val authorized = validation(PasskeyValidationCode.DAL_DENIED) {
                PasskeyDigitalAssetLinks.authorizes(String(bytes, Charsets.UTF_8), info.packageName, fingerprint)
            }
            if (!authorized) throw PasskeyValidationFailure(PasskeyValidationCode.DAL_DENIED)
            delegations.approve(identity)
        } finally {
            connection.disconnect()
            pe.gob.reniec.dnie.diagnostics.DiagnosticLog.event("passkeys", "dal_checked", "durationMs" to (System.nanoTime() - started) / 1_000_000)
        }
        PasskeyCaller("android:apk-key-hash:${PasskeyEncoding.encode(digest)}", null)
    }
    companion object {
        private val delegations = PasskeyDelegationCache()
        fun clearDelegations() { delegations.clear() }
    }
}
