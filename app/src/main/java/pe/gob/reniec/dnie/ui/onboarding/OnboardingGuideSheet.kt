package pe.gob.reniec.dnie.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import pe.gob.reniec.dnie.ui.design.*

@Composable
fun FirstRunWelcomeCard(onOpen: () -> Unit, onHide: () -> Unit) {
    AetherCard {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("¿Primera vez usando tu DNIe en el teléfono?", style = MaterialTheme.typography.titleMedium)
            Text("Aprende dónde apoyar el DNIe, cómo recordar el CAN y cómo firmar en móvil o PC.", style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onOpen) { Text("Ver guía rápida (3 pasos)") }
            TextButton(onClick = onHide) { Text("Ocultar") }
        }
    }
}

/** Opt-in overlay: never appears automatically or changes an operation/session route. */
@Composable
fun OnboardingGuideSheet(onDismiss: () -> Unit, onFinish: () -> Unit, onCanSaved: () -> Unit = {},
    model: OnboardingViewModel = viewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val reduced = rememberReducedMotion()
    var step by rememberSaveable { mutableStateOf(0) }
    LaunchedEffect(state.saved) { if (state.saved) onCanSaved() }
    DisposableEffect(model) { onDispose { model.clearInput() } }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), modifier = Modifier.testTag("onboarding_guide")) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Paso ${step + 1} de 3", style = MaterialTheme.typography.labelLarge, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "Guía, paso ${step + 1} de 3" }) {
                repeat(3) { index -> Box(Modifier.size(8.dp).background(if (step == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape)) }
            }
            AnimatedContent(step, transitionSpec = { aetherContentTransition(reduced) }, label = "onboarding-step") { page ->
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    GuideScene(page)
                    when (page) {
                        0 -> {
                            Text("Tu DNIe y el código CAN", style = MaterialTheme.typography.titleLarge)
                            Text("El CAN tiene 6 dígitos y está impreso en el frente del DNIe peruano, en la zona inferior. Busca la indicación CAN; la disposición depende del documento.")
                            Text("Permite abrir el canal NFC protegido. Es distinto del PIN de firma: aquí no te pediremos tu PIN.")
                            if (state.saved) Text("CAN guardado en este teléfono", color = MaterialTheme.colorScheme.secondary)
                            else {
                                OutlinedTextField(state.can, model::changeCan, singleLine = true, enabled = !state.saving,
                                    textStyle = MaterialTheme.typography.bodyLarge, label = { Text("CAN opcional (6 dígitos)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                                OutlinedButton(enabled = state.can.length == 6 && !state.saving, onClick = { model.saveCan(context) }) { Text("Guardar mi CAN") }
                                state.message?.let { Text(it) }
                            }
                        }
                        1 -> {
                            Text("Encuentra el sensor NFC", style = MaterialTheme.typography.titleLarge)
                            Text("Activa NFC y acerca el DNIe al reverso del teléfono. La antena suele estar en la zona superior o central; muévelo suavemente hasta que la app lo detecte.")
                            Text("Cuando aparezca DNIe detectado, mantenlo inmóvil hasta que la pantalla confirme que terminó. Entonces puedes retirarlo.")
                            Text("Si la vibración está activada y el teléfono la permite, sentirás un aviso al iniciar y un doble pulso al completar. Guíate siempre por el resultado en pantalla.")
                        }
                        else -> {
                            Text("Firma en móvil o en Windows", style = MaterialTheme.typography.titleLarge)
                            Text("En el teléfono, abre Firmar para colocar el sello en un PDF o preparar un lote. Puedes solicitar sello de tiempo RENIEC si tienes internet.")
                            Text("En Windows, abre DNIe Suite o DNIe Bridge y vincula el teléfono con el QR. Después puedes usar la PC guardada: la red busca el dispositivo automáticamente cuando ambos extremos están disponibles.")
                            Text("Wi-Fi local y Tailscale usan el canal seguro de la app. USB requiere el extremo Windows compatible; el lector virtual necesita su instalación en la PC.")
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                if (step == 0) TextButton(onClick = onDismiss) { Text("Saltar guía") }
                else TextButton(onClick = { step-- }) { Text("Anterior") }
                Button(onClick = { if (step < 2) step++ else onFinish() }) { Text(if (step < 2) "Siguiente" else "Empezar a usar") }
            }
        }
    }
}
