package pe.gob.reniec.dnie.ui.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.gob.reniec.dnie.core.logging.ApduDirection
import pe.gob.reniec.dnie.core.logging.ApduLogEntry

@Composable
internal fun ApduLogItem(entry: ApduLogEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                !entry.isSuccess -> MaterialTheme.colorScheme.errorContainer // Error suave
                entry.direction == ApduDirection.TX -> MaterialTheme.colorScheme.surface
                entry.direction == ApduDirection.RX -> MaterialTheme.colorScheme.surfaceVariant
                else -> MaterialTheme.colorScheme.surface
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
                        ApduDirection.TX -> MaterialTheme.colorScheme.primary // Azul
                        ApduDirection.RX -> MaterialTheme.colorScheme.secondary // Púrpura
                        ApduDirection.INFO -> MaterialTheme.colorScheme.onSurfaceVariant // Gris
                    }
                    Surface(
                        color = badgeColor,
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Text(
                            text = " ${entry.direction.name} ",
                            color = MaterialTheme.colorScheme.surface,
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
                        color = if (entry.direction == ApduDirection.TX) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                    )
                }
            }

            // Status Word Badge
            val statusWordHex = entry.statusWordHex
            if (statusWordHex != null) {
                Spacer(modifier = Modifier.height(4.dp))
                val sw = statusWordHex.uppercase()
                val (badgeBackground, badgeText, statusLabel) = when {
                    sw == "9000" || sw.startsWith("90 00") -> Triple(MaterialTheme.colorScheme.secondary, "90 00", "OK")
                    sw.startsWith("63C") || sw.startsWith("63 C") -> Triple(MaterialTheme.colorScheme.tertiary, sw, "PIN Inválido")
                    sw.startsWith("6983") || sw.startsWith("69 83") -> Triple(MaterialTheme.colorScheme.error, sw, "BLOQUEADO")
                    sw.startsWith("6B00") || sw.startsWith("6B 00") -> Triple(MaterialTheme.colorScheme.outline, sw, "Fin de archivo")
                    sw.startsWith("6282") || sw.startsWith("62 82") -> Triple(MaterialTheme.colorScheme.secondary, sw, "Fin de EF")
                    else -> Triple(MaterialTheme.colorScheme.error, sw, "Error")
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
                            color = MaterialTheme.colorScheme.surface,
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
