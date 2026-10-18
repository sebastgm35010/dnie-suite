package pe.gob.reniec.dnie.ui.design

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import pe.gob.reniec.dnie.ui.design.StatusCheck

/** Shared visual session only. Each module supplies independently observed status. */
@Composable fun ConnectionSessionScreen(title: String, channel: String, headline: String,
    pcConnected: Boolean, phoneReady: Boolean, cardReady: Boolean, cardLabel: String,
    inProgress: Boolean, stopping: Boolean, instruction: String, onStop: () -> Unit,
    sessionTag: String = "bridge_session") {
    BackHandler { if (!stopping) onStop() }
    Surface(Modifier.fillMaxSize().testTag(sessionTag), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Spacer(Modifier.height(12.dp))
                Text(title, style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary)
                Text(headline, style = MaterialTheme.typography.headlineMedium)
                Text(channel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly) {
                        SessionNode(Icons.Default.Computer, "PC", pcConnected, if (pcConnected) "Enlazada" else "Esperando")
                        SessionLinkConnector(pcConnected, pcConnected && phoneReady)
                        SessionNode(Icons.Default.PhoneAndroid, "Teléfono", phoneReady, if (phoneReady) "Activo" else "Esperando")
                        SessionLinkConnector(cardReady || inProgress, cardReady || inProgress)
                        SessionNode(Icons.Default.Badge, "DNIe", cardReady, if (cardReady) "Conectado" else "Acerca DNIe")
                    }
                }
                if (inProgress)
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        StatusCheck("PC conectada", pcConnected)
                        StatusCheck(cardLabel, cardReady)
                    }
                }
                Text(instruction, style = MaterialTheme.typography.bodyLarge)
                Text("Al salir o bloquear el teléfono, la sesión se detiene.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = onStop, enabled = !stopping,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp).testTag("bridge_stop")) {
                Icon(Icons.Default.StopCircle, null); Spacer(Modifier.width(8.dp)); Text("Detener sesión")
            }
        }
    }
}

@Composable private fun SessionNode(icon: ImageVector, label: String, complete: Boolean, status: String) {
    val reduced = rememberReducedMotion()
    val containerColor by animateColorAsState(
        targetValue = if (complete) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        animationSpec = aetherSpring(reduced),
        label = "session_node_container"
    )
    val iconTint by animateColorAsState(
        targetValue = if (complete) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline,
        animationSpec = aetherSpring(reduced),
        label = "session_node_icon"
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(shape = MaterialTheme.shapes.medium, color = containerColor) {
            Icon(icon, null, Modifier.padding(16.dp).size(28.dp), tint = iconTint)
        }
        Text(label, style = MaterialTheme.typography.labelLarge)
        Surface(color = containerColor, shape = MaterialTheme.shapes.extraSmall) {
            Text(status, Modifier.padding(horizontal = 6.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable private fun SessionLinkConnector(active: Boolean, flowing: Boolean) {
    val reduced = rememberReducedMotion()
    val pulseState = if (reduced || !flowing) {
        null
    } else {
        val infinite = rememberInfiniteTransition(label = "session_link")
        infinite.animateFloat(0f, 1f, infiniteRepeatable(tween(1050, easing = LinearEasing)), label = "link_pulse")
    }
    val targetColor = if (active) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant
    val colorState = animateColorAsState(targetColor, aetherSpring(reduced), label = "link_color")
    Spacer(Modifier.width(30.dp).height(22.dp).drawBehind {
        val pulse = pulseState?.value ?: 0.5f
        val color = colorState.value
        val y = size.height / 2
        drawLine(color.copy(alpha = .65f), androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), 2.dp.toPx(), StrokeCap.Round)
        if (flowing) drawCircle(color, 4.dp.toPx(), androidx.compose.ui.geometry.Offset(size.width * pulse, y))
    })
}
