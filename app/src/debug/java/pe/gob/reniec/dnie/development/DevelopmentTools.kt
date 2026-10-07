package pe.gob.reniec.dnie.development
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pe.gob.reniec.dnie.ui.console.ConsoleViewModel
import pe.gob.reniec.dnie.ui.console.ConsoleScreen
import pe.gob.reniec.dnie.ui.diagnostics.*
import pe.gob.reniec.dnie.ui.navigation.Screen
typealias DevelopmentController = ConsoleViewModel
fun defaultTailnetHost() = "sebas-laptop.tail2752e9.ts.net"
fun developmentDestinations(): List<Screen> = listOf(Screen.Console)
@Composable fun DevelopmentScreen(controller: DevelopmentController) {
    var page by remember { mutableStateOf(0) }
    val context = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        Row {
            TextButton(onClick = { controller.cancelCardProbe(); page = 0 }) { Text("APDU") }
            TextButton(onClick = { controller.cancelCardProbe(); page = 1 }, enabled = !controller.busy) { Text("Eventos") }
            TextButton(onClick = { controller.cancelCardProbe(); context.startActivity(Intent(context, pe.gob.reniec.dnie.ui.bridge.BridgeActivity::class.java).putExtra("development", true)) }, enabled = !controller.busy) { Text("Puente debug") }
        }
        Box(Modifier.weight(1f)) {
            if (page == 0) ConsoleScreen(controller)
            else {
                val model: DiagnosticsViewModel = viewModel()
                DisposableEffect(model) { model.start(); onDispose { model.stop() } }
                val state by model.state.collectAsStateWithLifecycle()
                DiagnosticsScreen(state, model::filter, model::older, model::newest, model::clear, model::export) { page = 0 }
            }
        }
    }
}
@Composable fun DevelopmentEntry() {
    val context = LocalContext.current
    TextButton(onClick = { context.startActivity(Intent(context, DiagnosticsActivity::class.java)) }) { Text("Diagnóstico de desarrollo") }
}
