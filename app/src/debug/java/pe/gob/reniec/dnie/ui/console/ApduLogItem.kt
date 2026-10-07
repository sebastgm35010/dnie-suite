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
