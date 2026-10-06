package pe.gob.reniec.dnie.passkeys.android

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.util.UUID

object PasskeyProviderIntents {
    const val ACTIVITY = "pe.gob.reniec.dnie.ui.passkeys.PasskeyCeremonyActivity"
    const val MODE = "dnie.passkeys.mode"
    const val ID = "dnie.passkeys.id"
    const val AUTHORIZATION = "dnie.passkeys.authorization"
    fun intent(context: Context, mode: String, id: String? = null) = Intent().apply {
        component = ComponentName(context.packageName, ACTIVITY)
        action = "dnie.passkeys.$mode"
        data = Uri.parse("dnie-passkeys://ceremony/${UUID.randomUUID()}")
        putExtra(MODE, mode); putExtra(ID, id)
    }
    fun pending(context: Context, mode: String, id: String? = null): PendingIntent =
        PendingIntent.getActivity(context, 0, intent(context, mode, id), PendingIntent.FLAG_MUTABLE)
}
