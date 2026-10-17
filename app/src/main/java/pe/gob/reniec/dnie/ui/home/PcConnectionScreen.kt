package pe.gob.reniec.dnie.ui.home

import pe.gob.reniec.dnie.ui.design.AetherCard as Card
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp

/** Chooses existing, independently distributed features; transport setup stays in each module. */
@Composable
fun PcConnectionScreen(onBack: () -> Unit, onBridge: () -> Unit, onSuite: () -> Unit) {
    pe.gob.reniec.dnie.ui.design.FeatureScaffold("Conexión PC", "Tu DNI en Windows", onBack = onBack,
        subtitle = "Elige cómo utilizar tu DNI desde el equipo.") { padding ->
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { PcConnectionOption("Lector para otras aplicaciones", "Usa el DNIe en programas que ya conoces.", Icons.Default.Computer,
            listOf("ReFirma", "Firma Perú", "Lector PC/SC"), onBridge) }
        item { PcConnectionOption("Suite propia de firma", "Firma y organiza lotes desde la suite DNIe.", Icons.Default.Description,
            listOf("Lotes PDF", "Sello visual", "Sin instalar driver"), onSuite) }

    }
}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun PcConnectionOption(title: String, description: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    chips: List<String>, onClick: () -> Unit) {
    pe.gob.reniec.dnie.ui.design.AetherCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(description, style = MaterialTheme.typography.bodyMedium) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { chips.forEach { AssistChip(onClick = onClick, label = { Text(it) }) } }
        }
    }
}
