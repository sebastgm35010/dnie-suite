package pe.gob.reniec.dnie.passkeys.android

import android.content.Context
import android.content.Intent
import androidx.annotation.RequiresApi
import androidx.credentials.*
import androidx.credentials.provider.*
import pe.gob.reniec.dnie.passkeys.*

sealed interface PasskeyCeremony {
    val title: String
    data class Create(val options: PasskeyCreateOptions, val caller: PasskeyCaller) : PasskeyCeremony { override val title = options.rpId }
    data class Get(val options: PasskeyGetOptions, val caller: PasskeyCaller, val id: String,
        val scope: PasskeyAuthorizationScope, val authorization: String?) : PasskeyCeremony { override val title = options.rpId }
    data class Discover(val options: List<Pair<BeginGetPublicKeyCredentialOption, PasskeyGetOptions>>,
        val scopes: List<PasskeyAuthorizationScope>) : PasskeyCeremony { override val title = options.map { it.second.rpId }.distinct().joinToString(", ") }
    data object Manage : PasskeyCeremony { override val title = "Tus passkeys" }
}

@RequiresApi(34)
class PasskeyRequestAdapter(private val context: Context) {
    suspend fun prepare(intent: Intent): PasskeyCeremony {
        pe.gob.reniec.dnie.diagnostics.DiagnosticLog.event("passkeys", "prepare", "mode" to intent.getStringExtra(PasskeyProviderIntents.MODE)?.takeIf { it in setOf("create", "get", "discover", "manage") })
        val verifier = PasskeyOriginVerifier(context)
        return when (intent.getStringExtra(PasskeyProviderIntents.MODE)) {
            "create" -> {
                val request = validation(PasskeyValidationCode.REQUEST_MISSING) { requireNotNull(PendingIntentHandler.retrieveProviderCreateCredentialRequest(intent)) }
                val publicKey = request.callingRequest as? CreatePublicKeyCredentialRequest ?: error("Solo passkeys")
                PasskeyRequestDiagnostics.capture(publicKey.requestJson)
                val options = validation(PasskeyValidationCode.CREATE_OPTIONS) { PasskeyCreateOptions.parse(publicKey.requestJson) }
                PasskeyCeremony.Create(options, verifier.verify(request.callingAppInfo, options.rpId, publicKey.clientDataHash))
            }
            "get" -> {
                val request = validation(PasskeyValidationCode.REQUEST_MISSING) { requireNotNull(PendingIntentHandler.retrieveProviderGetCredentialRequest(intent)) }
                // Exactly the selected option must be used, rather than silently choosing a different RP.
                val optionId = intent.getStringExtra("dnie.passkeys.option")
                val eligible = request.credentialOptions.filterIsInstance<GetPublicKeyCredentialOption>()
                eligible.forEach { PasskeyRequestDiagnostics.capture(it.requestJson) }
                val matches = eligible.map { it to validation(PasskeyValidationCode.GET_OPTIONS) { PasskeyGetOptions.parse(it.requestJson) } }.filter {
                    optionId == null || it.second.rpId == optionId
                }
                if (matches.size != 1) throw PasskeyValidationFailure(PasskeyValidationCode.SELECTION)
                val (publicKey, options) = matches.single()
                val caller = verifier.verify(request.callingAppInfo, options.rpId, publicKey.clientDataHash)
                PasskeyCeremony.Get(options, caller,
                    requireNotNull(intent.getStringExtra(PasskeyProviderIntents.ID)),
                    PasskeyAuthorizations.scope(request.callingAppInfo, options, caller), intent.getStringExtra(PasskeyProviderIntents.AUTHORIZATION))
            }
            "discover" -> {
                val request = validation(PasskeyValidationCode.REQUEST_MISSING) { requireNotNull(PendingIntentHandler.retrieveBeginGetCredentialRequest(intent)) }
                require(request.beginGetCredentialOptions.size <= 16) { "Demasiadas opciones" }
                val info = request.callingAppInfo ?: throw PasskeyValidationFailure(PasskeyValidationCode.CALLER_MISSING)
                val scopes = mutableListOf<PasskeyAuthorizationScope>()
                val options = request.beginGetCredentialOptions.filterIsInstance<BeginGetPublicKeyCredentialOption>().map { option ->
                    PasskeyRequestDiagnostics.capture(option.requestJson)
                    val parsed = validation(PasskeyValidationCode.DISCOVERY_OPTIONS) { PasskeyGetOptions.parse(option.requestJson, requireChallenge = false) }
                    // Discovery does not sign, but must authenticate the caller before revealing an account.
                    val caller = verifier.verify(info, parsed.rpId, option.clientDataHash)
                    scopes += PasskeyAuthorizations.scope(info, parsed, caller)
                    option to parsed
                }
                require(options.isNotEmpty()); PasskeyCeremony.Discover(options, scopes)
            }
            "manage" -> PasskeyCeremony.Manage
            else -> error("Solicitud desconocida")
        }
    }
    fun createResult(json: String) = Intent().also { PendingIntentHandler.setCreateCredentialResponse(it, CreatePublicKeyCredentialResponse(json)) }
    fun getResult(json: String) = Intent().also { PendingIntentHandler.setGetCredentialResponse(it, GetCredentialResponse(PublicKeyCredential(json))) }
    fun discoveryResult(ceremony: PasskeyCeremony.Discover, catalog: PasskeyCatalog): Pair<Intent, String?> {
        val candidates = ceremony.options.withIndex().flatMap { (index, pair) -> catalog.matching(pair.second).map { Triple(index, pair, it) } }.take(50)
        if (candidates.isEmpty()) return Intent().also { PendingIntentHandler.setBeginGetCredentialResponse(it, BeginGetCredentialResponse()) } to null
        val authorization = PasskeyAuthorizations.issue(candidates.map { ceremony.scopes[it.first] to it.third })
        try {
        val entries = candidates.mapIndexed { index, (_, pair, record) ->
            val (option, options) = pair
            val intent = PasskeyProviderIntents.intent(context, "get", record.encodedId).putExtra("dnie.passkeys.option", options.rpId)
                .putExtra(PasskeyProviderIntents.AUTHORIZATION, PasskeyAuthorizations.continuation.selectionToken(authorization, index))
            val pending = android.app.PendingIntent.getActivity(context, 0, intent, android.app.PendingIntent.FLAG_MUTABLE)
            PublicKeyCredentialEntry(context, record.username, pending, option, displayName = record.displayName)
        }
        return Intent().also { PendingIntentHandler.setBeginGetCredentialResponse(it, BeginGetCredentialResponse(credentialEntries = entries)) } to authorization
        } catch (failure: Throwable) { PasskeyAuthorizations.continuation.cancel(authorization); throw failure }
    }
}
