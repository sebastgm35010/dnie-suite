package pe.gob.reniec.dnie.ui.console

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.KeyboardDoubleArrowDown
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.gob.reniec.dnie.core.logging.ApduDirection
import pe.gob.reniec.dnie.core.logging.ApduLogEntry

/**
 * Pantalla de Consola Diagnóstica APDU ISO 7816-4 en tiempo real.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsoleScreen(
    viewModel: ConsoleViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val listState = rememberLazyListState()

    // Configurar dispatcher de audio/vibración si aún no está asignado
    LaunchedEffect(Unit) {
        if (viewModel.feedbackDispatcher == null) {
            viewModel.feedbackDispatcher = AndroidFeedbackDispatcher(context)
        }
    }

    // Auto-scroll automático cuando llegan nuevos registros
    LaunchedEffect(uiState.entries.size, uiState.autoScroll) {
        if (uiState.autoScroll && uiState.filteredEntries.isNotEmpty()) {
            listState.animateScrollToItem(uiState.filteredEntries.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Consola APDU") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    // Alternar Auto-Scroll
                    IconButton(onClick = { viewModel.toggleAutoScroll() }) {
                        Icon(
                            imageVector = Icons.Default.KeyboardDoubleArrowDown,
                            contentDescription = "Auto Scroll",
                            tint = if (uiState.autoScroll) Color.White else Color.White.copy(alpha = 0.5f)
                        )
                    }

                    // Copiar al Portapapeles
                    IconButton(onClick = {
                        val copied = viewModel.copyLogsToClipboard(context)
                        if (copied) {
                            Toast.makeText(context, "Registros copiados al portapapeles", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "No hay registros para copiar", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copiar")
                    }

                    // Exportar / Compartir
                    IconButton(onClick = { viewModel.exportLogs(context) }) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = "Exportar")
                    }

                    // Limpiar registros
                    IconButton(onClick = { viewModel.clearLogs() }) {
                        Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = "Limpiar")
                    }
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Fila de Filtros
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = uiState.filter == ConsoleFilter.ALL,
                    onClick = { viewModel.setFilter(ConsoleFilter.ALL) },
                    label = { Text("Todos (${uiState.entries.size})") }
                )
                FilterChip(
                    selected = uiState.filter == ConsoleFilter.TX,
                    onClick = { viewModel.setFilter(ConsoleFilter.TX) },
                    label = {
                        Text("TX (${uiState.entries.count { it.direction == ApduDirection.TX }})")
                    }
                )
                FilterChip(
                    selected = uiState.filter == ConsoleFilter.RX,
                    onClick = { viewModel.setFilter(ConsoleFilter.RX) },
                    label = {
                        Text("RX (${uiState.entries.count { it.direction == ApduDirection.RX }})")
                    }
                )
            }

            // Barra de acción para Diagnóstico / Sondeo de AIDs (DNIe v2 / v3)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                color = if (uiState.isProbing) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (uiState.isProbing) "Modo Sondeo Activo: Aproxime su DNIe" else "Sondeo de AIDs (DNIe v2 / v3)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (uiState.isProbing) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (uiState.isProbing) "Mantenga el DNIe pegado al teléfono para probar todos los AIDs" else "Envía ráfaga de prueba a todos los AIDs conocidos para identificar la tarjeta",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (uiState.isProbing) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    if (uiState.isProbing) {
                        androidx.compose.material3.Button(
                            onClick = { viewModel.cancelCardProbe() },
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Cancelar")
                        }
                    } else {
                        androidx.compose.material3.Button(
                            onClick = { viewModel.startCardProbe() }
                        ) {
                            Text("Escanear AIDs")
                        }
                    }
                }
            }

            if (uiState.filteredEntries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            modifier = Modifier.size(60.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            text = "Sin tráfico APDU registrado",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Aproxime una tarjeta DNIe en la pestaña de Certificados o Firmador para visualizar el intercambio de comandos en tiempo real.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(uiState.filteredEntries) { entry ->
                        ApduLogItem(entry = entry)
                    }
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

/**
 * Elemento de lista para cada comando o respuesta APDU individual.
 */
@Composable
private fun ApduLogItem(entry: ApduLogEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                !entry.isSuccess -> Color(0xFFFFEBEE) // Error suave
                entry.direction == ApduDirection.TX -> Color(0xFFF0F4F8)
                entry.direction == ApduDirection.RX -> Color(0xFFF7F5FA)
                else -> Color(0xFFF5F5F5)
            }
        )
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Encabezado del registro
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Badge de Dirección (TX / RX / INFO)
                    val badgeColor = when (entry.direction) {
                        ApduDirection.TX -> Color(0xFF1565C0) // Azul
                        ApduDirection.RX -> Color(0xFF6A1B9A) // Púrpura
                        ApduDirection.INFO -> Color(0xFF616161) // Gris
                    }
                    Surface(
                        color = badgeColor,
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Text(
                            text = " ${entry.direction.name} ",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = entry.description,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                val durationText = if (entry.durationMs != null) " (${entry.durationMs}ms)" else ""
                Text(
                    text = "${entry.formattedTime}$durationText",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            // Trama Hexadecimal
            if (entry.apduHex.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                SelectionContainer {
                    Text(
                        text = entry.apduHex,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = if (entry.direction == ApduDirection.TX) Color(0xFF0D47A1) else Color(0xFF4A148C)
                    )
                }
            }

            // Status Word Badge
            val statusWordHex = entry.statusWordHex
            if (statusWordHex != null) {
                Spacer(modifier = Modifier.height(4.dp))
                val sw = statusWordHex.uppercase()
                val (badgeBackground, badgeText, statusLabel) = when {
                    sw == "9000" || sw.startsWith("90 00") -> Triple(Color(0xFF2E7D32), "90 00", "OK")
                    sw.startsWith("63C") || sw.startsWith("63 C") -> Triple(Color(0xFFF57F17), sw, "PIN Inválido")
                    sw.startsWith("6983") || sw.startsWith("69 83") -> Triple(Color(0xFFC62828), sw, "BLOQUEADO")
                    sw.startsWith("6B00") || sw.startsWith("6B 00") -> Triple(Color(0xFF757575), sw, "Fin de archivo")
                    sw.startsWith("6282") || sw.startsWith("62 82") -> Triple(Color(0xFF2E7D32), sw, "Fin de EF")
                    else -> Triple(Color(0xFFC62828), sw, "Error")
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Status Word: ",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        color = badgeBackground,
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Text(
                            text = " $badgeText ($statusLabel) ",
                            color = Color.White,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
