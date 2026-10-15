package pe.gob.reniec.dnie.ui.identity

import pe.gob.reniec.dnie.ui.design.AetherCard as Card
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.ui.Alignment
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import pe.gob.reniec.dnie.icao.*

/** Public data projection. MRZ controls and embedded SOD signature never assert issuer/chip trust. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DigitalIdCredentialCard(document: IcaoDocument, onUseMark: () -> Unit, onExport: (IcaoImage) -> Unit) {
    fun content(file: IcaoFile) = document.blocks[file]?.takeIf { it.status == BlockStatus.AVAILABLE }?.content
    val person = content(IcaoFile.DG1) as? IcaoContent.Identity
    val photo = (content(IcaoFile.DG2) as? IcaoContent.Images)?.images?.firstOrNull()
    val mark = (content(IcaoFile.DG7) as? IcaoContent.Images)?.images?.firstOrNull()
    Card(Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.medium) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Badge, null, tint = MaterialTheme.colorScheme.primary)
                    Column { Text("Credencial digital", style = MaterialTheme.typography.titleMedium);Text("Datos leídos del documento", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                photo?.let { IdentityImage(it, Modifier.width(100.dp).height(132.dp)) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (person != null) {
                        Text(person.givenNames + " " + person.surnames, style = MaterialTheme.typography.titleLarge)

                    } else Text("Datos personales no disponibles en esta lectura")
                }
            }
            if (person != null) {
                val fields = listOf("Documento" to person.documentNumber, "Nacionalidad" to person.nationality,
                    "Nacimiento" to IdentityPresentation.date(person.birthDateMrz, true), "Vencimiento" to IdentityPresentation.date(person.expiryDateMrz, false))
                fields.chunked(2).forEach { pair -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { (label, value) -> Surface(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.small) {
                        Column(Modifier.padding(12.dp)) {Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant);Text(value, style = MaterialTheme.typography.bodyMedium)}
                    } }
                } }
            }
            if (mark != null) {
                Surface(color = Color.White, shape = MaterialTheme.shapes.medium) { IdentityImage(mark, Modifier.fillMaxWidth().height(88.dp).padding(8.dp)) }
                Button(enabled = IdentityPresentation.markUsable(document), onClick = onUseMark, modifier = Modifier.fillMaxWidth()) { Text("Usar esta rúbrica en mis firmas PDF") }
                TextButton(onClick = { onExport(mark) }) { Text("Guardar imagen") }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (IdentityPresentation.mrzVerified(document)) IdentityIntegrityBadge("Control MRZ verificado")
                if (IdentityPresentation.sodSignatureVerified(document)) IdentityIntegrityBadge("Firma EF.SOD íntegra")
            }
            if (document.blocks.values.any { it.hashStatus == HashStatus.MISMATCH }) Text("Hay bloques con integridad incorrecta. Revisa los detalles antes de utilizar sus datos.", color = MaterialTheme.colorScheme.error)
            Text("Lectura informativa: no acredita confianza en el emisor ni autenticidad física del chip. La MRZ expresa años de dos dígitos.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable private fun IdentityIntegrityBadge(text: String) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
        Text(text, Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.onSecondaryContainer, style = MaterialTheme.typography.labelSmall)
    }
}
