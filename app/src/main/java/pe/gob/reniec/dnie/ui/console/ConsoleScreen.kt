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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.KeyboardDoubleArrowDown
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Terminal
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pe.gob.reniec.dnie.core.logging.ApduDirection

/**
 * Pantalla de Consola Diagnóstica APDU ISO 7816-4 en tiempo real.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsoleScreen(
    viewModel: ConsoleViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    // Filtering used to allocate a new list for each label, emptiness check,
    // scrolling effect and item provider in the same composition.
    val filteredEntries = remember(uiState.entries, uiState.filter) { uiState.filteredEntries }
    val directionCounts = remember(uiState.entries) {
        uiState.entries.count { it.direction == ApduDirection.TX } to
            uiState.entries.count { it.direction == ApduDirection.RX }
    }

    // Configurar dispatcher de audio/vibración si aún no está asignado
    LaunchedEffect(Unit) {
        if (viewModel.feedbackDispatcher == null) {
            viewModel.feedbackDispatcher = withContext(Dispatchers.IO) {
                AndroidFeedbackDispatcher(context.applicationContext)
            }
        }
    }

    // Auto-scroll automático cuando llegan nuevos registros
    LaunchedEffect(filteredEntries.lastOrNull(), uiState.autoScroll) {
        if (uiState.autoScroll && filteredEntries.isNotEmpty()) {
            // Jump to live traffic instead of composing intervening rows in a
            // long animated trip through the history on every screen entry.
            listState.scrollToItem(filteredEntries.size - 1)
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
                    IconButton(onClick = { context.startActivity(android.content.Intent(context, pe.gob.reniec.dnie.ui.diagnostics.DiagnosticsActivity::class.java)) }) {
                        Icon(Icons.Default.Terminal, contentDescription = "Diagnóstico persistente de la app")
                    }
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
                        Text("TX (${directionCounts.first})")
                    }
                )
                FilterChip(
                    selected = uiState.filter == ConsoleFilter.RX,
                    onClick = { viewModel.setFilter(ConsoleFilter.RX) },
                    label = {
                        Text("RX (${directionCounts.second})")
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

            if (filteredEntries.isEmpty()) {
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
                    items(filteredEntries) { entry ->
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
