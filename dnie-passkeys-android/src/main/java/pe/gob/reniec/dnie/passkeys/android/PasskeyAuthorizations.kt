package pe.gob.reniec.dnie.passkeys.android

import android.os.Handler
import android.os.Looper
import androidx.credentials.provider.CallingAppInfo
import pe.gob.reniec.dnie.passkeys.*
import java.security.MessageDigest

/** Android adapter for expiry; replaceable authorization boundary for future opt-in device delegation. */
object PasskeyAuthorizations {
    val continuation = PasskeyContinuation()
    fun issue(entries: List<Pair<PasskeyAuthorizationScope, PasskeyRecord>>): String {
        val token = continuation.issue(entries)
        Handler(Looper.getMainLooper()).postDelayed({ continuation.cancel(token) }, continuation.lifetimeMs)
        return token
    }
    fun scope(info: CallingAppInfo, options: PasskeyGetOptions, caller: PasskeyCaller): PasskeyAuthorizationScope {
        val signers = info.signingInfo.apkContentsSigners.map {
            PasskeyEncoding.encode(MessageDigest.getInstance("SHA-256").digest(it.toByteArray()))
        }.sorted().joinToString(",")
        return PasskeyAuthorizationScope(options.rpId, "${info.packageName}:$signers", caller.origin,
            options.challenge, caller.clientDataHash?.let(PasskeyEncoding::encode))
    }
}
