package pe.gob.reniec.dnie.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pe.gob.reniec.dnie.data.preferences.AppearancePreferences
import pe.gob.reniec.dnie.data.preferences.ThemeMode
import pe.gob.reniec.dnie.ui.design.*

@Composable
fun SettingsScreen(preferences: AppearancePreferences, onBack: () -> Unit) {
    var showGuide by remember { mutableStateOf(false) }
    if (showGuide) pe.gob.reniec.dnie.ui.onboarding.OnboardingGuideSheet(onDismiss = { showGuide = false },
        onFinish = { preferences.dismissOnboarding(); showGuide = false })
    val haptics by preferences.hapticsEnabled.collectAsStateWithLifecycle()
    val selected by preferences.mode.collectAsStateWithLifecycle()
    FeatureScaffold("Configuración", "Preferencias", onBack = onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedButton(onClick = { showGuide = true }) { Text("Ver guía de primer inicio") }
            FeatureSection("Apariencia", "El mismo tema se aplica a todas las pantallas, incluida la bóveda y las conexiones PC.") {
                ThemeMode.entries.forEach { mode ->
                    Row(Modifier.fillMaxWidth().testTag("theme_" + mode.name.lowercase())
                        .selectable(selected == mode, role = Role.RadioButton, onClick = { preferences.select(mode) })
                        .padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        RadioButton(selected == mode, onClick = null)
                        Text(mode.label, Modifier.padding(top = 12.dp))
                    }
                }
            }
            FeatureSection("Respuesta táctil (NFC)") {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    Text("Vibrar al leer, firmar y avisar errores",Modifier.weight(1f))
                    Switch(checked=haptics,onCheckedChange=preferences::setHaptics,modifier=Modifier.testTag("haptics_enabled"))
                }
            }
            Text("DNIe Evolución ${pe.gob.reniec.dnie.BuildConfig.VERSION_NAME} (${pe.gob.reniec.dnie.BuildConfig.VERSION_CODE})",
                modifier = Modifier.testTag("app_version"), style = MaterialTheme.typography.bodySmall)
            FeatureSection("Privacidad") {
                Text("El tema no cambia las autorizaciones. Las claves privadas permanecen en el DNI; salir de una operación cancela su sesión.")
                Text("Las opciones de transporte están en Conexión PC. El diagnóstico solo existe en compilaciones de desarrollo.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
