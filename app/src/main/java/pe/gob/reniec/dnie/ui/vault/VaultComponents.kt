package pe.gob.reniec.dnie.ui.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
internal fun VaultHero(pairedFingerprint: String?) {
    Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = VaultSurface)) {
        Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF263D36), VaultSurface)))
            .padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Box(Modifier.size(54.dp).background(VaultAccent.copy(alpha = .12f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Lock, null, tint = VaultAccent, modifier = Modifier.size(28.dp))
                }
                Surface(color = VaultAccent.copy(alpha = .12f), shape = CircleShape) {
                    Text("EXPERIMENTAL", Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelSmall, color = VaultAccent, fontFamily = FontFamily.Monospace)
                }
            }
            Text("Tu DNI, la llave.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text("Guarda cifrado sin acercar el DNI. Recupera tus archivos con la tarjeta y su PIN.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            if (pairedFingerprint != null) {
                Text("CLAVE VINCULADA · SHA-256", color = VaultAccent, style = MaterialTheme.typography.labelSmall)
                Text(pairedFingerprint.chunked(16).joinToString("\n"), fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall)
            } else Text("Sin DNI vinculado", color = VaultAccent, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun SectionLabel(text: String) {
    Text(text, color = VaultAccent, style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace)
}

@Composable
internal fun VaultResult(running: Boolean, awaitingCard: Boolean, verified: Boolean,
                        message: String?, restoredName: String?) {
    Card(colors = CardDefaults.cardColors(containerColor = VaultSurface), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp).semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (running || awaitingCard) LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = VaultAccent)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (verified) Icon(Icons.Default.CheckCircle, null, tint = VaultAccent)
                Text(when {
                    awaitingCard -> "Acerca tu DNI"
                    running -> "Operación en curso"
                    verified -> "Clave comprobada"
                    else -> "Estado de la bóveda"
                }, fontWeight = FontWeight.SemiBold)
            }
            message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            restoredName?.let { name ->
                Text("Nombre original", style = MaterialTheme.typography.labelMedium)
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Text(name, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (awaitingCard) Text("Busca el sensor NFC en la parte posterior del teléfono y mantén el DNI inmóvil.",
                style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
internal fun VaultFileCard(item: VaultItemUi, enabled: Boolean, selected: Boolean,
    onRestore: () -> Unit, onExport: () -> Unit, onDelete: () -> Unit,
    onPreview: () -> Unit, onOrganize: () -> Unit, onRecover: () -> Unit, onInspect: () -> Unit) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = VaultSurface)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, null, tint = VaultAccent)
                Column {
                    Text(item.displayName ?: "Archivo cifrado · nombre pendiente", fontWeight = FontWeight.Medium)
                    if (selected) Text("Archivo seleccionado", style = MaterialTheme.typography.labelSmall, color = VaultAccent)
                    Text(encryptedSize(item.encryptedBytes), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (item.folder.isNotBlank()) Text("Carpeta: ${item.folder}", style = MaterialTheme.typography.labelMedium)
            if (item.tags.isNotEmpty()) Text(item.tags.joinToString(" · "), style = MaterialTheme.typography.labelSmall)
            if (item.createdAt > 0) Text(java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT)
                .format(java.util.Date(item.createdAt)), style = MaterialTheme.typography.bodySmall)
            Text(item.id.chunked(20).joinToString("\n"), fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!item.trashed) {
            if (item.canPreview) OutlinedButton(onClick = onPreview, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("Ver con DNI · vista privada")
            }
            TextButton(onClick = onOrganize, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("Carpeta y etiquetas") }
            OutlinedButton(onClick = onRestore, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("Descifrar copia")
            }
            TextButton(onClick = onExport, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("Exportar cifrado")
            }
            } else {
                OutlinedButton(onClick = onRecover, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("Recuperar de papelera") }
            }
            TextButton(onClick = onDelete, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(if (item.trashed) "Eliminar definitivamente" else "Mover a papelera")
            }
            TextButton(onClick = onInspect, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("Ficha del archivo") }
        }
    }
}

private fun encryptedSize(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B cifrados"
    bytes < 1024L * 1024L -> String.format(Locale.ROOT, "%.1f KiB cifrados", bytes / 1024.0)
    else -> String.format(Locale.ROOT, "%.1f MiB cifrados", bytes / (1024.0 * 1024.0))
}
