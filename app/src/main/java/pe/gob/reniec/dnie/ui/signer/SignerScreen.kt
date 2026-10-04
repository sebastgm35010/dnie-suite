package pe.gob.reniec.dnie.ui.signer

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.gob.reniec.dnie.pdf.SignaturePreset
import pe.gob.reniec.dnie.ui.components.CredentialsDialog
import pe.gob.reniec.dnie.ui.components.NfcTapPromptSheet
import pe.gob.reniec.dnie.ui.components.PinEntryDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pantalla para la selección de documentos PDF y firma digital PAdES con DNIe.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SignerScreen(
    viewModel: SignerViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Selector SAF para abrir documento PDF
    val openPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            var fileName = "documento.pdf"
            var fileSize = 0L

            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) fileName = cursor.getString(nameIndex) ?: fileName
                    if (sizeIndex != -1) fileSize = cursor.getLong(sizeIndex)
                }
            }

            viewModel.onDocumentSelected(
                uri = uri,
                name = fileName,
                sizeBytes = fileSize,
                pageCount = 1
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Firmador PDF con DNIe") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Sección 1: Selector / Información del Documento
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "DOCUMENTO PDF",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )

                    when (val state = uiState) {
                        is SignerUiState.NoDocument -> {
                            Button(
                                onClick = { openPdfLauncher.launch(arrayOf("application/pdf")) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(imageVector = Icons.Default.FileOpen, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Seleccionar Documento PDF")
                            }
                        }

                        is SignerUiState.DocumentLoaded,
                        is SignerUiState.EnteringPin,
                        is SignerUiState.AwaitingCard,
                        is SignerUiState.Signing -> {
                            val docName = when (state) {
                                is SignerUiState.DocumentLoaded -> state.documentName
                                is SignerUiState.EnteringPin -> state.documentName
                                is SignerUiState.AwaitingCard -> state.documentName
                                else -> "documento.pdf"
                            }
                            val docSize = when (state) {
                                is SignerUiState.DocumentLoaded -> state.documentSizeBytes
                                is SignerUiState.EnteringPin -> state.documentSizeBytes
                                is SignerUiState.AwaitingCard -> state.documentSizeBytes
                                else -> 0L
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PictureAsPdf,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = docName,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "${docSize / 1024} KB",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { openPdfLauncher.launch(arrayOf("application/pdf")) }) {
                                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Cambiar archivo")
                                }
                            }
                        }

                        else -> {
                            Button(
                                onClick = { openPdfLauncher.launch(arrayOf("application/pdf")) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(imageVector = Icons.Default.FileOpen, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Seleccionar Otro PDF")
                            }
                        }
                    }
                }
            }

            // Sección 2: Parámetros de la Firma Digital (Visible cuando hay documento cargado)
            val currentDoc = uiState as? SignerUiState.DocumentLoaded
            if (currentDoc != null) {
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "PARÁMETROS DE LA FIRMA",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )

                        OutlinedTextField(
                            value = currentDoc.reason,
                            onValueChange = { viewModel.onReasonChanged(it) },
                            label = { Text("Motivo de la firma") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = currentDoc.location,
                            onValueChange = { viewModel.onLocationChanged(it) },
                            label = { Text("Lugar (Ciudad, País)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text(
                            text = "Posición del Estampado Visual:",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )

                        // Selector de Presets
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            SignaturePreset.entries.forEach { preset ->
                                FilterChip(
                                    selected = currentDoc.preset == preset,
                                    onClick = { viewModel.onPresetSelected(preset) },
                                    label = { Text(preset.displayName) }
                                )
                            }
                        }

                        // Vista previa gráfica del posicionamiento en la página
                        SignaturePlacementPreviewBox(
                            preset = currentDoc.preset,
                            reason = currentDoc.reason,
                            location = currentDoc.location
                        )
                    }
                }

                // Botón Principal: Firmar con DNIe (2 Pasos)
                Button(
                    onClick = { viewModel.startPinEntry() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(imageVector = Icons.Default.DriveFileRenameOutline, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Firmar Documento con DNIe", fontSize = 16.sp)
                }
            }

            // Sección 3: Banner de Éxito cuando se completó la firma
            val successState = uiState as? SignerUiState.SignedSuccess
            if (successState != null) {
                val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()) }

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFE8F5E9)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "¡Documento Firmado Exitosamente!",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1B5E20)
                            )
                        }

                        HorizontalDivider(color = Color(0xFFC8E6C9))

                        DetailLine(label = "Firmante:", value = successState.signerName)
                        DetailLine(label = "DNI:", value = successState.signerDni)
                        DetailLine(label = "Fecha:", value = dateFormat.format(Date(successState.timestamp)))
                        DetailLine(label = "Tamaño de Firma:", value = "${successState.signatureSize} bytes")
                        if (successState.signedFilePath != null) {
                            DetailLine(label = "Guardado en:", value = successState.signedFilePath)
                        }

                        // Botón de Compartir PDF Firmado
                        if (successState.signedFileUri != null) {
                            Button(
                                onClick = {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/pdf"
                                        putExtra(Intent.EXTRA_STREAM, successState.signedFileUri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Compartir PDF Firmado"))
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF2E7D32)
                                )
                            ) {
                                Icon(imageVector = Icons.Default.Share, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Compartir Documento Firmado")
                            }
                        }

                        OutlinedButton(
                            onClick = { viewModel.reset() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Firmar Otro Documento")
                        }
                    }
                }
            }

            // Sección 4: Banner de Error si falló la operación
            val errorState = uiState as? SignerUiState.Error
            if (errorState != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Error al firmar",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Text(
                            text = errorState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Button(
                            onClick = { viewModel.reset() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Aceptar y Volver")
                        }
                    }
                }
            }
        }
    }

    // Paso 1: Diálogo de Pre-ingreso de Credenciales (CAN de 6 dígitos + PIN de 6 dígitos)
    val enteringPinState = uiState as? SignerUiState.EnteringPin
    CredentialsDialog(
        show = enteringPinState != null,
        title = "Credenciales para Firma Digital",
        subtitle = "Ingrese el código CAN (6 dígitos en el frente del DNIe) y su PIN de firma digital antes de aproximar el documento al lector NFC.",
        requireCan = true,
        requirePin = true,
        initialCan = enteringPinState?.initialCan ?: viewModel.enteredCan ?: "",
        onConfirm = { can, pin -> viewModel.onCredentialsConfirmed(can, pin) },
        onDismiss = { viewModel.cancelPinEntry() }
    )

    // Paso 2: Bottom Sheet de Escaneo NFC
    val isScanningOrSigning = uiState is SignerUiState.AwaitingCard || uiState is SignerUiState.Signing
    val promptMessage = when (val state = uiState) {
        is SignerUiState.AwaitingCard -> "Aproxime su DNIe para verificar PIN y generar firma digital..."
        is SignerUiState.Signing -> state.message
        else -> ""
    }

    NfcTapPromptSheet(
        isShowing = isScanningOrSigning,
        statusMessage = promptMessage,
        onCancel = { viewModel.cancelAwaitingCard() }
    )
}

/**
 * Previsualizador gráfico del posicionamiento del sello de firma en una página PDF simulada.
 */
@Composable
private fun SignaturePlacementPreviewBox(
    preset: SignaturePreset,
    reason: String,
    location: String
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Previsualización de Posicionamiento:",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (preset == SignaturePreset.INVISIBLE) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.extraSmall)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Firma Digital Invisible (PAdES Detached sin estampado gráfico)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                // Hoja simulada (aspect ratio de una página)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.extraSmall)
                        .background(Color.White)
                        .padding(8.dp)
                ) {
                    val boxAlignment = when (preset) {
                        SignaturePreset.BOTTOM_RIGHT -> Alignment.BottomEnd
                        SignaturePreset.BOTTOM_LEFT -> Alignment.BottomStart
                        SignaturePreset.TOP_RIGHT -> Alignment.TopEnd
                        SignaturePreset.TOP_LEFT -> Alignment.TopStart
                        SignaturePreset.CUSTOM -> Alignment.Center
                        SignaturePreset.INVISIBLE -> Alignment.Center
                    }

                    // Sello simulado
                    Box(
                        modifier = Modifier
                            .align(boxAlignment)
                            .width(180.dp)
                            .border(1.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.extraSmall)
                            .background(Color(0xFFF0F4FF))
                            .padding(6.dp)
                    ) {
                        Column {
                            Text(
                                text = "FIRMADO CON DNIe",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Titular: [DNIe Ciudadano]",
                                fontSize = 7.sp,
                                color = Color.DarkGray
                            )
                            Text(
                                text = "Motivo: $reason",
                                fontSize = 7.sp,
                                maxLines = 1,
                                color = Color.DarkGray
                            )
                            Text(
                                text = "Lugar: $location",
                                fontSize = 7.sp,
                                maxLines = 1,
                                color = Color.DarkGray
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1B5E20)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF2E7D32),
            maxLines = 1
        )
    }
}
