package pe.gob.reniec.dnie.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/** Presentation only. Counts describe loaded results, never a live card or trust decision. */
data class HomeSummary(val certificates: Int = 0, val identityAvailable: Boolean = false, val reading: Boolean = false)

@Composable
fun HomeScreen(summary: HomeSummary, onRead: () -> Unit, onIdentity: () -> Unit,
    onCertificates: () -> Unit, onPdf: () -> Unit, onBatch: () -> Unit,
    onVault: () -> Unit, onPc: () -> Unit, onDevelopment: (() -> Unit)? = null) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text("DNIe · IDENTIDAD Y FIRMA", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary, fontFamily = FontFamily.Monospace)
            Text("Inicio", style = MaterialTheme.typography.headlineLarge)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Badge, null, tint = MaterialTheme.colorScheme.primary)
                    Text(if (summary.reading) "Lectura en curso" else "Tu DNI, en un solo lugar", style = MaterialTheme.typography.titleLarge)
                    Text("Lee identidad ICAO y certificados en un mismo flujo. Cada resultado se conserva de forma independiente.", style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = onRead, enabled = !summary.reading, modifier = Modifier.fillMaxWidth()) {
                        Text("Leer mi DNIe")
                    }
                    Text("La lectura pública no requiere PIN. Para firmar o descifrar, autoriza cada operación.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryCard("Certificados leídos", summary.certificates.toString(), Modifier.weight(1f))
                SummaryCard("Identidad ICAO", if (summary.identityAvailable) "Disponible" else "Sin lectura", Modifier.weight(1f))
            }
            Text("Resultados de esta sesión de la app; no indican que el DNI siga conectado.",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        }
        item { Text("Qué quieres hacer", style = MaterialTheme.typography.titleLarge) }
        item { ModuleCard("Ficha de identidad", "Datos e imágenes disponibles del documento", Icons.Default.Person, onIdentity) }
        item { ModuleCard("Certificados del DNIe", "Inspección, exportación y comprobación de confianza", Icons.Default.VerifiedUser, onCertificates) }
        item { ModuleCard("Firma PDF", "Firma integrada, sello visual y TSA RENIEC opcional", Icons.Default.Edit, onPdf) }
        item { ModuleCard("Firma por lotes", "Varios documentos en una autorización", Icons.Default.Folder, onBatch) }
        item { ModuleCard("Archivos protegidos", "Cifrar, descifrar y respaldar tu bóveda", Icons.Default.Lock, onVault) }
        item { ModuleCard("Conexión con PC", "Lector compatible y suite propia de firma", Icons.Default.Computer, onPc) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Antes de renovar tus certificados", style = MaterialTheme.typography.titleSmall)
                    Text("Exporta tu bóveda con una contraseña de recuperación. Una renovación puede reemplazar las claves del chip.", style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onVault) { Text("Ir a mis archivos") }
                }
            }
        }
        if (onDevelopment != null) item { ModuleCard("Desarrollo", "Diagnósticos y herramientas de esta compilación", Icons.Default.Build, onDevelopment) }
    }
}

@Composable private fun SummaryCard(label: String, value: String, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable internal fun ModuleCard(title: String, description: String, icon: ImageVector, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(10.dp)) {
                Icon(icon, null, Modifier.padding(12.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
