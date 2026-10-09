package pe.gob.reniec.dnie.ui.bridge

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import pe.gob.reniec.dnie.ui.design.StatusCheck

/** All bridge transports share this presentation. No setup controls during a session. */
@Composable fun BridgeSessionScreen(state: BridgeUiState, onStop: () -> Unit) {
    val stage = state.sessionStage
    BackHandler { if (stage != BridgeSessionStage.STOPPING) onStop() }
    Surface(Modifier.fillMaxSize().testTag("bridge_session"), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Spacer(Modifier.height(12.dp))
                Text("CONEXIÓN PC", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary)
                Text(when (stage) {
                    BridgeSessionStage.PREPARING -> "Preparando conexión"
                    BridgeSessionStage.WAITING_CARD -> "Acerca tu DNI"
                    BridgeSessionStage.CONNECTING_PC -> "Conectando con la PC"
                    BridgeSessionStage.OPENING_CARD -> "Abriendo tu DNI"
                    BridgeSessionStage.ACTIVE -> "Puente activo"
                    BridgeSessionStage.STOPPING -> "Deteniendo sesión"
                    BridgeSessionStage.IDLE -> "Sesión finalizada"
                }, style = MaterialTheme.typography.headlineMedium)
                Text(state.method.title, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly) {
                    SessionNode(Icons.Default.Computer, "PC", stage.pcConnected)
                    Icon(Icons.Default.SyncAlt, null, tint = MaterialTheme.colorScheme.outline)
                    SessionNode(Icons.Default.PhoneAndroid, "Teléfono", stage != BridgeSessionStage.PREPARING && stage != BridgeSessionStage.STOPPING)
                    Icon(Icons.Default.SyncAlt, null, tint = MaterialTheme.colorScheme.outline)
                    SessionNode(Icons.Default.Badge, "DNI", stage.cardReady)
                }
                if (stage != BridgeSessionStage.ACTIVE && stage != BridgeSessionStage.WAITING_CARD)
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        StatusCheck("PC conectada", stage.pcConnected)
                        StatusCheck(if (state.windowsPace) "Tarjeta disponible para Windows" else "DNI listo para usar", stage.cardReady)
                    }
                }
                Text(when (stage) {
                    BridgeSessionStage.WAITING_CARD -> "Mantén el DNI junto al sensor NFC. Si lo retiras, puedes volver a acercarlo."
                    BridgeSessionStage.ACTIVE -> "Usa tu programa de la PC. Mantén el DNI inmóvil junto al teléfono."
                    BridgeSessionStage.STOPPING -> "Cerrando la conexión con el DNI y la PC."
                    else -> "Mantén el DNI junto al teléfono mientras se establece la conexión."
                }, style = MaterialTheme.typography.bodyLarge)
                Text("Al salir o bloquear el teléfono, la sesión se detiene.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = onStop, enabled = stage != BridgeSessionStage.STOPPING,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp).testTag("bridge_stop")) {
                Icon(Icons.Default.StopCircle, null); Spacer(Modifier.width(8.dp)); Text("Detener sesión")
            }
        }
    }
}

@Composable private fun SessionNode(icon: ImageVector, label: String, complete: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(shape = MaterialTheme.shapes.medium,
            color = if (complete) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer) {
            Icon(icon, null, Modifier.padding(16.dp).size(28.dp),
                tint = if (complete) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline)
        }
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}
