package pe.gob.reniec.dnie.ui.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
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
    onPreview: () -> Unit, onOrganize: () -> Unit, onRecover: () -> Unit, onInspect: () -> Unit,
    selectionMode: Boolean = false, checked: Boolean = false, onToggle: () -> Unit = {}) {
    var menu by remember { mutableStateOf(false) }
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = VaultSurface)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selectionMode) Checkbox(checked = checked, onCheckedChange = { onToggle() }, enabled = enabled)
                else Icon(Icons.Default.Lock, null, tint = VaultAccent, modifier = Modifier.padding(end = 10.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.displayName ?: "Archivo cifrado", fontWeight = FontWeight.Medium)
                    Text(encryptedSize(item.encryptedBytes) + if (item.folder.isNotBlank()) " · ${item.folder}" else "",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (selected) Text("Pendiente de abrir", style = MaterialTheme.typography.labelSmall)
                }
                if (!selectionMode) Box {
                    IconButton(onClick = { menu = true }, enabled = enabled) {
                        Icon(Icons.Default.MoreVert, "Opciones del archivo")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        fun run(action: () -> Unit) { menu = false; action() }
                        if (!item.trashed) {
                            if (item.canPreview) DropdownMenuItem(text = { Text("Descifrar copia") }, onClick = { run(onRestore) })
                            DropdownMenuItem(text = { Text("Exportar cifrado") }, onClick = { run(onExport) })
                            DropdownMenuItem(text = { Text("Carpeta y etiquetas") }, onClick = { run(onOrganize) })
                        }
                        DropdownMenuItem(text = { Text("Ficha del archivo") }, onClick = { run(onInspect) })
                        DropdownMenuItem(text = { Text(if (item.trashed) "Eliminar definitivamente" else "Mover a papelera") }, onClick = { run(onDelete) })
                    }
                }
            }
            if (!selectionMode) TextButton(onClick = when {
                item.trashed -> onRecover; item.canPreview -> onPreview; else -> onRestore
            }, enabled = enabled) { Text(when { item.trashed -> "Restaurar"; item.canPreview -> "Ver con DNI"; else -> "Descifrar copia" }) }
        }
    }
}

private fun encryptedSize(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B cifrados"
    bytes < 1024L * 1024L -> String.format(Locale.ROOT, "%.1f KiB cifrados", bytes / 1024.0)
    else -> String.format(Locale.ROOT, "%.1f MiB cifrados", bytes / (1024.0 * 1024.0))
}
