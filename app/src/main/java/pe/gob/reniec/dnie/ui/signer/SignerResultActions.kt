package pe.gob.reniec.dnie.ui.signer

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import pe.gob.reniec.dnie.domain.signing.*

/** Export/share always use the artifact returned by the signing engine, never a guessed path. */
internal fun shareSignedArtifact(context: Context, artifact: SignedArtifact) {
    val output = artifact.output ?: return
    val files = if (artifact.document.isPdf) listOf(output) else listOf(artifact.document.file, output)
    val uris = ArrayList(files.map { FileProvider.getUriForFile(context, context.packageName + ".fileprovider", it) })
    val intent = Intent(if (files.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
        type = if (artifact.document.isPdf) "application/pdf" else "*/*"
        if (files.size == 1) putExtra(Intent.EXTRA_STREAM, uris[0]) else putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = android.content.ClipData.newUri(context.contentResolver, "Firma", uris[0]).also { clip -> uris.drop(1).forEach { clip.addItem(android.content.ClipData.Item(it)) } }
    }
    runCatching { context.startActivity(Intent.createChooser(intent, "Compartir documento y firma")) }
}

@Composable
internal fun SignedResultHeroCard(signed: Int, total: Int, busy: Boolean) {
    val complete = signed == total && total > 0 && !busy
    Card(colors = CardDefaults.cardColors(containerColor = if (complete) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (complete) "Firma completada" else if (busy) "Lote en curso" else "Resultados parciales", style = MaterialTheme.typography.titleLarge)
            Text("$signed de $total firmados" + if (complete) " · guarda o comparte el resultado" else " · se conservan los resultados disponibles")
        }
    }
}

@Composable
internal fun SigningDocumentRow(doc: SigningDocument, selected: Boolean, result: SignedArtifact?, editing: Boolean,
    onSelect: () -> Unit, onRemove: () -> Unit, onSave: (SignedArtifact) -> Unit, onShare: (SignedArtifact) -> Unit) {
    OutlinedCard(onClick = { if (editing) onSelect() }, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text((if (selected) "● " else "") + doc.name, style = MaterialTheme.typography.titleSmall)
            Text("${doc.size / 1024} KiB · " + if (result?.status == DocumentSignStatus.SIGNED) "Firmado" else result?.message ?: "Pendiente", style = MaterialTheme.typography.bodySmall)
            Row {
                if (result?.status == DocumentSignStatus.SIGNED) {
                    TextButton(enabled = editing, onClick = { onSave(result) }) { Text("Guardar") }
                    TextButton(enabled = editing, onClick = { onShare(result) }) { Text("Compartir") }
                }
                TextButton(enabled = editing, onClick = onRemove) { Text("Quitar") }
            }
        }
    }
}

@Composable
internal fun SignerActionBar(workspace: SigningWorkspace, batch: Boolean, enabled: Boolean, selectedResult: SignedArtifact?,
    onSign: () -> Unit, onSave: (SignedArtifact) -> Unit, onShare: (SignedArtifact) -> Unit, onNew: () -> Unit, onSaveBatch: () -> Unit) {
    if (workspace.documents.isEmpty()) return
    Surface(modifier = Modifier.testTag("signer_action_bar"), tonalElevation = 3.dp, shadowElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (selectedResult != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = enabled, onClick = { onSave(selectedResult) }, modifier = Modifier.weight(1f)) { Text("Guardar") }
                    FilledTonalButton(enabled = enabled, onClick = { onShare(selectedResult) }, modifier = Modifier.weight(1f)) { Text("Compartir") }
                }
                if (!batch) TextButton(enabled = enabled, onClick = onNew, modifier = Modifier.fillMaxWidth()) { Text("Firmar otro") }
            }
            if (batch && workspace.artifacts.any { it.status == DocumentSignStatus.SIGNED }) {
                OutlinedButton(enabled = enabled, onClick = onSaveBatch, modifier = Modifier.fillMaxWidth()) { Text("Guardar lote completo · ZIP") }
            }
            if (workspace.pending.isNotEmpty()) {
                Text("${workspace.pending.size} pendientes" + if (workspace.selected?.options?.reniecTimestamp == true) " · PAdES-T RENIEC" else "", style = MaterialTheme.typography.labelSmall)
                Button(enabled = enabled, onClick = onSign, modifier = Modifier.fillMaxWidth()) { Text(if (batch) "Firmar ${workspace.pending.size} archivo(s)" else "Firmar PDF") }
            }
        }
    }
}
