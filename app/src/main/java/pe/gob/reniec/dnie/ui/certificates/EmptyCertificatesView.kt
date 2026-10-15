package pe.gob.reniec.dnie.ui.certificates

import pe.gob.reniec.dnie.ui.design.AetherCard as Card
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import pe.gob.reniec.dnie.ui.design.FeatureSection

@Composable
internal fun EmptyCertificatesView(onScanClicked: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.Badge, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                Text("Lee tu DNIe", style = MaterialTheme.typography.titleLarge)
                Text("Identidad y certificados · sin PIN")
                Button(onClick = onScanClicked, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Nfc, null); Spacer(Modifier.width(8.dp))
                    Text("Leer identidad y certificados")
                }
            }
        }
        FeatureSection("Cómo leer", "Necesitas el CAN de seis dígitos impreso en el documento.") {
            Text("1 · Autoriza la lectura e introduce el CAN.")
            Text("2 · Acerca el DNI al sensor NFC del teléfono.")
            Text("3 · Mantenlo inmóvil hasta completar ambas etapas.")
        }
        Text("Comprueba la confianza de cada certificado desde su ficha.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
