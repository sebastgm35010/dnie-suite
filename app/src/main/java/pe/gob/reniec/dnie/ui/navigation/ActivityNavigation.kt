package pe.gob.reniec.dnie.ui.navigation

import android.app.Activity
import android.content.Intent
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import pe.gob.reniec.dnie.MainActivity

/** Continuous navigation across separate foreground NFC owners; onPause revokes their sessions. */
@Composable
fun ActivityNavigation() {
    val activity = LocalContext.current as? Activity
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        Screen.items.forEach { screen ->
            NavigationBarItem(selected = false, modifier = Modifier.testTag("nav_" + screen.route),
                icon = { Icon(screen.icon, screen.title) }, label = { Text(screen.title) },
                onClick = {
                    activity?.startActivity(Intent(activity, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        .putExtra("destination", screen.route))
                    activity?.finish()
                })
        }
    }
}
