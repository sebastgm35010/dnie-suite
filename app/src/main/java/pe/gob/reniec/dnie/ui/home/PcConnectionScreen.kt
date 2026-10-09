package pe.gob.reniec.dnie.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Chooses existing, independently distributed features; transport setup stays in each module. */
@Composable
fun PcConnectionScreen(onBack: () -> Unit, onBridge: () -> Unit, onSuite: () -> Unit) {
    pe.gob.reniec.dnie.ui.design.FeatureScaffold("Conexión PC", "Tu DNI en Windows", onBack = onBack,
        subtitle = "Elige cómo utilizar tu DNI desde el equipo.") { padding ->
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { ModuleCard("Lector para otras aplicaciones", "ReFirma y Firma Perú · requiere lector virtual en Windows.", Icons.Default.Computer, onBridge) }
        item { ModuleCard("Suite propia de firma", "Firma desde nuestra suite · sin lector virtual.", Icons.Default.Description, onSuite) }

    }
}
}
