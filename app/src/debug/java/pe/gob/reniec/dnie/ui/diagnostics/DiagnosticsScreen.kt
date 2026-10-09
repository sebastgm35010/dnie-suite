package pe.gob.reniec.dnie.ui.diagnostics

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

@Composable
fun DiagnosticsScreen(state: DiagnosticsUiState, filter: (String?) -> Unit, older: () -> Unit, newest: () -> Unit,
    clear: () -> Unit, export: (Uri) -> Unit, back: () -> Unit) {
    var confirming by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/x-ndjson")) { it?.let(export) }
    Scaffold { padding -> Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
        Text("Diagnóstico de la app · temporal", style = MaterialTheme.typography.titleLarge)
        Text("Captura persistente activa. Solo Borrar elimina el historial. Sin PIN, CAN, claves, contenido ni datos del titular.", style = MaterialTheme.typography.bodySmall)
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            TextButton(back) { Text("Volver") }; TextButton(onClick = { picker.launch("dnie-diagnostico.jsonl") }) { Text("Exportar todo") }
            TextButton(onClick = { confirming = true }) { Text("Borrar") }
        }
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            listOf(null, "apdu", "vault", "signer", "batch", "identity", "certificates", "lifecycle", "performance", "crash").forEach { category ->
                FilterChip(selected = state.category == category, onClick = { filter(category) }, label = { Text(category ?: "Todo") })
            }
        }
        Text("${state.page?.total ?: 0} eventos guardados · ${state.page?.rows?.size ?: 0} en esta página")
        state.page?.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        state.message?.let { Text(it) }
        Row { TextButton(newest) { Text("Más recientes") }; TextButton(older, enabled = state.page?.rows?.size == 300) { Text("Anteriores") } }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.page?.rows.orEmpty(), key = { it.id }) { row ->
                OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(10.dp)) {
                    Text("#${row.id} · ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(row.time))}", style = MaterialTheme.typography.labelSmall)
                    Text("${row.category} / ${row.name}", style = MaterialTheme.typography.titleSmall)
                    Text(row.details, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                } }
            }
        }
    } }
    if (confirming) AlertDialog(onDismissRequest = { confirming = false }, title = { Text("¿Borrar todo el historial?") },
        text = { Text("La captura continuará para las próximas operaciones.") },
        confirmButton = { TextButton(onClick = { confirming = false; clear() }) { Text("Borrar") } },
        dismissButton = { TextButton(onClick = { confirming = false }) { Text("Cancelar") } })
}
