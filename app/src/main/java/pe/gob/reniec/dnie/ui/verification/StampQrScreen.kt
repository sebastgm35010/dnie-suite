package pe.gob.reniec.dnie.ui.verification

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import pe.gob.reniec.dnie.data.qr.AndroidQrScanner
import pe.gob.reniec.dnie.data.qr.AndroidStampQrRepository
import pe.gob.reniec.dnie.pdf.StampQrMatch
import pe.gob.reniec.dnie.ui.certificates.CertificateValidationPanel
import pe.gob.reniec.dnie.ui.design.FeatureScaffold
import pe.gob.reniec.dnie.ui.design.FeatureSection

@Composable
fun StampQrRoute(onBack: () -> Unit) {
    val context=LocalContext.current
    val factory=remember(context) { object: ViewModelProvider.Factory {
        override fun <T: ViewModel> create(modelClass: Class<T>): T {
            require(modelClass==StampQrViewModel::class.java)
            @Suppress("UNCHECKED_CAST")
            return StampQrViewModel(AndroidStampQrRepository(context.applicationContext.contentResolver)) as T
        }
    } }
    val model: StampQrViewModel=viewModel(factory=factory)
    val scanner=remember(context) { AndroidQrScanner(context as Activity) }
    val state by model.state.collectAsStateWithLifecycle()
    val original=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { model.original(it.toString()) } }
    val certificate=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { model.certificate(it.toString()) } }
    DisposableEffect(model) { onDispose { model.clear() } }
    StampQrScreen(state,onBack,{ scanner.scan(model::scan,model::status) },model::scan,
        { original.launch(arrayOf("*/*")) },{ certificate.launch(arrayOf("*/*")) },model::clear)
}

@Composable
fun StampQrScreen(state: StampQrUiState, onBack: () -> Unit, onScan: () -> Unit,
    onPayload: (String) -> Unit, onOriginal: () -> Unit, onCertificate: () -> Unit, onClear: () -> Unit) {
    var paste by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var details by remember(state.record) { mutableStateOf(false) }
    FeatureScaffold(title="Cotejar sello QR",category="Certificados",subtitle="Compara el original y el certificado.",
        actions={ TextButton(onClick=onBack) { Text("Volver") } }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement=Arrangement.spacedBy(16.dp)) {
            FeatureSection("Leer sello") {
                Text("El QR permite cotejar datos públicos. Por sí solo no valida la firma digital ni una copia impresa.")
                Button(onClick=onScan,enabled=!state.busy,modifier=Modifier.testTag("stamp_qr_scan")) { Text("Escanear QR") }
                TextButton(onClick={ paste=!paste;draft="" },enabled=!state.busy) { Text(if(paste) "Cerrar entrada" else "Pegar contenido QR") }
                if(paste) {
                    OutlinedTextField(value=draft,onValueChange={ if(it.length<=1800) draft=it },label={ Text("Contenido del sello") },
                        modifier=Modifier.fillMaxWidth().testTag("stamp_qr_payload"),maxLines=5)
                    Button(onClick={ onPayload(draft);draft="";paste=false },modifier=Modifier.testTag("stamp_qr_read")) { Text("Leer contenido") }
                }
            }
            if(state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.message?.let { Text(it,modifier=Modifier.testTag("stamp_qr_message")) }
            state.record?.let { record ->
                FeatureSection(if(record.metadata.preview) "Vista previa · no cotejable" else "Datos declarados en el QR") {
                    Text(record.name,style=MaterialTheme.typography.titleMedium)
                    record.dni?.let { Text("DNI: $it") };record.moment?.let { Text("Fecha/hora declarada: $it") }
                    TextButton(onClick={ details=!details }) { Text(if(details) "Ocultar huellas" else "Ver huellas completas") }
                    if(details) {
                        Text("Perfil declarado: PAdES Baseline ${record.requestedProfile}. La presencia de TSA se comprueba en el PDF.",style=MaterialTheme.typography.bodySmall)
                        Text("Serie: ${record.metadata.certificateSerial}")
                        Text("Certificado SHA-256: ${record.metadata.certificateSha256}")
                        Text("Original SHA-256: ${record.metadata.originalSha256}")
                        Text("Original: ${record.metadata.originalSize} bytes")
                    }
                }
                if(!record.metadata.preview) FeatureSection("Cotejo local") {
                    Text("Elige el archivo original anterior a esta firma; el PDF firmado tiene otra huella.")
                    OutlinedButton(onClick=onOriginal,enabled=!state.busy,modifier=Modifier.testTag("stamp_qr_original")) { Text("Seleccionar original") }
                    Comparison(state.original,"Original: huella y tamaño coinciden","El archivo no coincide con el original declarado")
                    Text("El certificado CER o PEM del firmante se exporta desde Mi DNIe.")
                    OutlinedButton(onClick=onCertificate,enabled=!state.busy,modifier=Modifier.testTag("stamp_qr_certificate")) { Text("Seleccionar certificado") }
                    Comparison(state.certificateMatch,"Certificado: huella y serie coinciden","El certificado no coincide con el declarado")
                    state.certificate?.let { cert -> Text("Titular del certificado: ${cert.citizenName}") }
                    state.identityMatches?.let { matches -> Text(if(matches) "Identidad declarada coincide con el certificado" else
                        "La identidad declarada NO coincide con el certificado",color=if(matches) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) }
                    Text("Comprueba la firma digital con el PDF en un validador de firmas.",style=MaterialTheme.typography.bodySmall)
                }
                if(state.certificateMatch==StampQrMatch.MATCH && state.identityMatches==true) state.certificate?.let { CertificateValidationPanel(it) }
                TextButton(onClick=onClear,modifier=Modifier.testTag("stamp_qr_clear")) { Text("Limpiar cotejo") }
            }
        }
    }
}

@Composable
private fun Comparison(match: StampQrMatch?, success: String, failure: String) {
    if(match!=null) Text(if(match==StampQrMatch.MATCH) success else failure,
        color=if(match==StampQrMatch.MATCH) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
}
