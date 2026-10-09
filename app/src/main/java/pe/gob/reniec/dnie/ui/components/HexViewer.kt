package pe.gob.reniec.dnie.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Visualizador monoespaciado de datos binarios y tramas APDU en formato hexadecimal.
 *
 * Muestra desplazamiento (offset), bloques de bytes agrupados y representación ASCII imprimible.
 */
@Composable
fun HexViewer(
    bytes: ByteArray,
    modifier: Modifier = Modifier,
    bytesPerLine: Int = 16,
    showAscii: Boolean = true,
    showOffsets: Boolean = true
) {
    val formattedLines = remember(bytes, bytesPerLine, showAscii, showOffsets) {
        formatHexDump(bytes, bytesPerLine, showAscii, showOffsets)
    }

    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .padding(10.dp)
                .horizontalScroll(rememberScrollState())
        ) {
            SelectionContainer {
                Text(
                    text = formattedLines,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Formatea un arreglo de bytes en un volcado hexadecimal clásico (HexDump).
 */
fun formatHexDump(
    bytes: ByteArray,
    bytesPerLine: Int = 16,
    showAscii: Boolean = true,
    showOffsets: Boolean = true
): String {
    if (bytes.isEmpty()) return "[Sin datos]"

    val sb = StringBuilder()
    var offset = 0

    while (offset < bytes.size) {
        val chunkLength = minOf(bytesPerLine, bytes.size - offset)
        val chunk = bytes.copyOfRange(offset, offset + chunkLength)

        // Offset
        if (showOffsets) {
            sb.append("%04X  ".format(offset))
        }

        // Hex bytes
        for (i in 0 until bytesPerLine) {
            if (i < chunkLength) {
                sb.append("%02X ".format(chunk[i]))
            } else {
                sb.append("   ")
            }
            if (i == 7) sb.append(" ") // Espacio separador entre mitades
        }

        // ASCII representation
        if (showAscii) {
            sb.append(" |")
            for (i in 0 until chunkLength) {
                val b = chunk[i].toInt() and 0xFF
                if (b in 32..126) {
                    sb.append(b.toChar())
                } else {
                    sb.append('.')
                }
            }
            sb.append("|")
        }

        sb.append("\n")
        offset += bytesPerLine
    }

    return sb.toString().trimEnd()
}
