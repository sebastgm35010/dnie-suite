package pe.gob.reniec.dnie.ui.certificates

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pe.gob.reniec.dnie.domain.model.CertificateType
import pe.gob.reniec.dnie.ui.components.CertificateCard
import pe.gob.reniec.dnie.ui.components.CredentialsDialog
import pe.gob.reniec.dnie.ui.components.NfcTapPromptSheet

/**
 * Pantalla principal del Inspector de Certificados Digitales del DNIe.
 */
@OptIn(ExperimentalMaterial3Api::class,androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun CertificatesScreen(
    viewModel: CertificatesViewModel,
    modifier: Modifier = Modifier,
    onOpenVault: () -> Unit = {},
    onOpenBridge: () -> Unit = {},
    onOpenSuite: () -> Unit = {},
    onOpenSigner: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Variables para el tipo y formato de exportación actual
    var pendingExportType by remember { mutableStateOf(CertificateType.SIGNATURE) }
    var pendingExportAsPem by remember { mutableStateOf(false) }

    // SAF Document Creator Launcher
    val exportDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(
            if (pendingExportAsPem) "application/x-pem-file" else "application/x-x509-ca-cert"
        )
    ) { uri: Uri? ->
        if (uri != null) {
            val contentResolver = context.contentResolver
            val outputStream = contentResolver.openOutputStream(uri)
            if (outputStream != null) {
                val success = viewModel.exportCertificate(
                    type = pendingExportType,
                    outputStream = outputStream,
                    asPem = pendingExportAsPem
                )
                if (success) {
                    Toast.makeText(context, "Certificado exportado correctamente", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Error al escribir el archivo", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    pe.gob.reniec.dnie.ui.design.FeatureScaffold(
        title = "Mi DNIe", category = "Certificados e identidad",
        subtitle = "Identidad y certificados en una sola lectura.",
        modifier = modifier, actions = {
            IconButton(onClick = { viewModel.requestCanEntry() }, enabled = uiState !is CertificatesUiState.ReadingCard) {
                Icon(Icons.Default.Nfc, "Leer DNIe con NFC")
            }
        }) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = uiState) {
                is CertificatesUiState.Idle,
                is CertificatesUiState.EnteringCan -> {
                    EmptyCertificatesView(
                        onScanClicked = { viewModel.requestCanEntry() }
                    )
                }

                is CertificatesUiState.ReadingCard -> {
                    EmptyCertificatesView(
                        onScanClicked = { viewModel.requestCanEntry() }
                    )
                    NfcTapPromptSheet(
                        isShowing = true,
                        statusMessage = state.message,
                        onCancel = { viewModel.cancelReading() }
                    )
                }

                is CertificatesUiState.Loaded -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        state.scanMessage?.let { message ->
                            Text(message, modifier=Modifier.padding(16.dp),style=MaterialTheme.typography.bodyMedium)
                        }
                        // Barra de Pestañas (Firma, Autenticación, Cifrado)
                        TabRow(
                            selectedTabIndex = CertificateType.entries.indexOf(state.selectedType)
                                .coerceAtLeast(0)
                        ) {
                            CertificateType.entries.forEach { type ->
                                val hasCert = state.certificates.containsKey(type)
                                Tab(
                                    selected = state.selectedType == type,
                                    onClick = { viewModel.selectTab(type) },
                                    text = {
                                        Text(
                                            text = type.displayName + if (hasCert) "" else " (N/D)",
                                            fontWeight = if (state.selectedType == type) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                )
                            }
                        }

                        Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Text("Usar este DNIe",style=MaterialTheme.typography.titleSmall)
                            if(state.certificates.containsKey(CertificateType.SIGNATURE)) Text("Certificado público listo para la vista previa de firma PDF",style=MaterialTheme.typography.bodySmall)
                            androidx.compose.foundation.layout.FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                androidx.compose.material3.OutlinedButton(onClick=onOpenSigner) {Text("Firmar PDF")}
                                androidx.compose.material3.OutlinedButton(onClick=onOpenVault) {Text("Bóveda")}
                                androidx.compose.material3.OutlinedButton(onClick=onOpenBridge) {Text("Puente PC")}
                                androidx.compose.material3.OutlinedButton(onClick=onOpenSuite) {Text("Firma remota")}
                            }
                        }
                        val activeCert = state.certificates[state.selectedType]

                        if (activeCert != null) {
                            CertificateValidationPanel(activeCert)
                            Box(modifier = Modifier.padding(16.dp)) {
                                CertificateCard(
                                    certificate = activeCert,
                                    onExportCer = {
                                        pendingExportType = state.selectedType
                                        pendingExportAsPem = false
                                        exportDocumentLauncher.launch(
                                            "dnie_cert_${state.selectedType.name.lowercase()}.cer"
                                        )
                                    },
                                    onExportPem = {
                                        pendingExportType = state.selectedType
                                        pendingExportAsPem = true
                                        exportDocumentLauncher.launch(
                                            "dnie_cert_${state.selectedType.name.lowercase()}.pem"
                                        )
                                    }
                                )
                            }
                        } else {
                            // Certificado opcional no disponible en este chip
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "El certificado de ${state.selectedType.displayName} no se encuentra emitido en este DNIe.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }

                is CertificatesUiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(48.dp)
                                )
                                Text(
                                    text = "Error al leer certificados",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = state.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Button(onClick = { viewModel.requestCanEntry() }) {
                                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Reintentar Lectura")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Diálogo de solicitud previa de CAN (6 dígitos)
    val enteringCanState = uiState as? CertificatesUiState.EnteringCan
    CredentialsDialog(
        show = enteringCanState != null,
        title = "Lectura de DNIe 3.0",
        subtitle = "Ingrese el código CAN (6 dígitos) ubicado en el frente inferior de su DNIe para habilitar la lectura de certificados por NFC.",
        requireCan = true,
        requirePin = false,
        initialCan = enteringCanState?.initialCan ?: viewModel.enteredCan ?: "",
        onConfirm = { can, _ -> viewModel.onCanConfirmed(can) },
        onDismiss = { viewModel.cancelCanEntry() }
    )
}
