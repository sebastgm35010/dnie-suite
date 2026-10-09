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
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
            Text("Conexión PC", style = MaterialTheme.typography.headlineMedium)
            Text("Elige cómo utilizar tu DNI desde Windows.", style = MaterialTheme.typography.bodyLarge)
        }
        item { ModuleCard("Lector para otras aplicaciones", "Expone el DNI al sistema para ReFirma, Firma Perú y aplicaciones compatibles. Requiere el lector virtual instalado en Windows.", Icons.Default.Computer, onBridge) }
        item { ModuleCard("Suite propia de firma", "Envía documentos desde nuestra suite. El teléfono autoriza las operaciones del DNI sin exponer un lector a todo Windows.", Icons.Default.Description, onSuite) }
        item { Text("El emparejamiento, el transporte y el estado de la conexión se consultan dentro de cada módulo. No necesitas depuración USB para el modo por cable del puente.", style = MaterialTheme.typography.bodyMedium) }
    }
}
