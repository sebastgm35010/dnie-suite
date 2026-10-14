package pe.gob.reniec.dnie.ui.identity

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import pe.gob.reniec.dnie.icao.*

/** Public data projection. MRZ controls and embedded SOD signature never assert issuer/chip trust. */
@Composable
internal fun DigitalIdCredentialCard(document: IcaoDocument, onUseMark: () -> Unit, onExport: (IcaoImage) -> Unit) {
    fun content(file: IcaoFile) = document.blocks[file]?.takeIf { it.status == BlockStatus.AVAILABLE }?.content
    val person = content(IcaoFile.DG1) as? IcaoContent.Identity
    val photo = (content(IcaoFile.DG2) as? IcaoContent.Images)?.images?.firstOrNull()
    val mark = (content(IcaoFile.DG7) as? IcaoContent.Images)?.images?.firstOrNull()
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Credencial digital · lectura del DNIe", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                photo?.let { IdentityImage(it, Modifier.width(100.dp).height(132.dp)) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (person != null) {
                        Text(person.givenNames + " " + person.surnames, style = MaterialTheme.typography.titleLarge)
                        Text("Documento: " + person.documentNumber)
                        Text("Nacimiento: " + IdentityPresentation.date(person.birthDateMrz, true), style = MaterialTheme.typography.bodySmall)
                        Text("Vencimiento: " + IdentityPresentation.date(person.expiryDateMrz, false), style = MaterialTheme.typography.bodySmall)
                    } else Text("Datos personales no disponibles en esta lectura")
                }
            }
            if (mark != null) {
                Surface(color = Color.White, shape = MaterialTheme.shapes.medium) { IdentityImage(mark, Modifier.fillMaxWidth().height(88.dp).padding(8.dp)) }
                Button(enabled = IdentityPresentation.markUsable(document), onClick = onUseMark, modifier = Modifier.fillMaxWidth()) { Text("Usar esta rúbrica en mis firmas PDF") }
                TextButton(onClick = { onExport(mark) }) { Text("Guardar imagen") }
            }
            if (IdentityPresentation.mrzVerified(document)) SuggestionChip(onClick = {}, label = { Text("Control MRZ verificado") })
            if (IdentityPresentation.sodSignatureVerified(document)) SuggestionChip(onClick = {}, label = { Text("Firma EF.SOD íntegra") })
            if (document.blocks.values.any { it.hashStatus == HashStatus.MISMATCH }) Text("Hay bloques con integridad incorrecta. Revisa los detalles antes de utilizar sus datos.", color = MaterialTheme.colorScheme.error)
            Text("Lectura informativa: no acredita confianza en el emisor ni autenticidad física del chip. La MRZ expresa años de dos dígitos.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
