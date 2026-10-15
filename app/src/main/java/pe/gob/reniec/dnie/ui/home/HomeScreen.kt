package pe.gob.reniec.dnie.ui.home

import pe.gob.reniec.dnie.ui.design.AetherCard as Card
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** Loaded results describe a session snapshot, never a live card or trust decision. */
data class HomeSummary(val certificates: Int = 0, val identityAvailable: Boolean = false, val reading: Boolean = false)

@Composable
fun HomeScreen(summary: HomeSummary, onRead: () -> Unit, onIdentity: () -> Unit,
    onCertificates: () -> Unit, onPdf: () -> Unit, onBatch: () -> Unit,
    onVault: () -> Unit, onPc: () -> Unit, onSettings: () -> Unit = {}, onDevelopment: (() -> Unit)? = null,
    savedCan: Boolean = false, onboardingDismissed: Boolean = true, onOpenGuide: () -> Unit = {}, onDismissOnboarding: () -> Unit = {}) {
    val loaded = summary.certificates > 0 || summary.identityAvailable
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Inicio", style = MaterialTheme.typography.headlineLarge)
                IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Configuración") }
            }
        }
        item {
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium) { Icon(Icons.Default.Badge, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(10.dp)) }
                        Surface(color = if (loaded) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium) {
                            Text(if (summary.reading) "Leyendo DNIe" else if (loaded) "Datos cargados" else if (savedCan) "CAN guardado" else "Lectura NFC", Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Text(if (summary.reading) "Lectura en curso" else if (loaded) "Datos cargados en sesión" else "Tu DNIe en tu teléfono", style = MaterialTheme.typography.titleLarge)
                    Text(if (loaded) "${summary.certificates} certificados · " + if (summary.identityAvailable) "identidad disponible" else "identidad sin lectura" else "Identidad y certificados en una sola lectura.")
                    if (loaded) {
                        Button(onClick = onCertificates, modifier = Modifier.fillMaxWidth()) { Text("Ver mi DNIe") }
                        TextButton(enabled = !summary.reading, onClick = onRead) { Text("Actualizar lectura") }
                    } else Button(enabled = !summary.reading, onClick = onRead, modifier = Modifier.fillMaxWidth()) { Text("Leer mi DNIe") }
                    Text(if (savedCan) "CAN recordado en este teléfono" else "Necesitas el CAN impreso en tu DNIe", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (!onboardingDismissed && !savedCan && !loaded) item { pe.gob.reniec.dnie.ui.onboarding.FirstRunWelcomeCard(onOpenGuide, onDismissOnboarding) }
        item { Text("Qué quieres hacer", style = MaterialTheme.typography.titleLarge) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BentoCard("Firmar PDF", "Sello a medida y PAdES-T", Icons.Default.Edit, Modifier.weight(1f), onPdf)
                BentoCard("Firma por lotes", "Varios archivos con un PIN", Icons.Default.Folder, Modifier.weight(1f), onBatch)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BentoCard("Conectar con PC", "Lector virtual y Suite", Icons.Default.Computer, Modifier.weight(1f), onPc)
                BentoCard("Bóveda segura", "Protege tus archivos", Icons.Default.Lock, Modifier.weight(1f), onVault)
            }
        }
        if (loaded) item {
            OutlinedCard(onClick = onIdentity, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Identidad y certificados", style = MaterialTheme.typography.titleMedium)
                    Text("Resultados de la última lectura; no indican conexión actual.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Antes de renovar tus certificados", style = MaterialTheme.typography.titleSmall)
                    Text("Exporta tu bóveda con contraseña de recuperación. La renovación puede reemplazar las claves del chip.", style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onVault) { Text("Respaldar mi bóveda") }
                }
            }
        }
        if (onDevelopment != null) item { ModuleCard("Desarrollo", "Diagnósticos de esta compilación", Icons.Default.Build, onDevelopment) }
    }
}

@Composable private fun BentoCard(title: String, description: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier.heightIn(min = 158.dp)) {
        Column(Modifier.fillMaxWidth().heightIn(min = 158.dp).padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.size(42.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) }
                }
                Icon(Icons.Default.ArrowOutward, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.height(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable internal fun ModuleCard(title: String, description: String, icon: ImageVector, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(icon, null, Modifier.padding(12.dp), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodyMedium)
            }
            Icon(Icons.Default.ChevronRight, null)
        }
    }
}
