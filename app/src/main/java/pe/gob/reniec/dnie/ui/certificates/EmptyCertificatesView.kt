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
            CertificateReadStep("1", "Autoriza con tu CAN")
            CertificateReadStep("2", "Apoya el DNIe al reverso")
            CertificateReadStep("3", "Consulta certificados e identidad ICAO")
        }
        Text("Comprueba la confianza de cada certificado desde su ficha.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun CertificateReadStep(number: String, text: String) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small) {
            Text(number, Modifier.padding(horizontal = 9.dp, vertical = 5.dp), color = MaterialTheme.colorScheme.primary)
        }
        Text(text)
    }
}
