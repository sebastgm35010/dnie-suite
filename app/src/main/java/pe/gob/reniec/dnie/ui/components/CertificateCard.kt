package pe.gob.reniec.dnie.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pe.gob.reniec.dnie.domain.model.CertificateInfo
import pe.gob.reniec.dnie.domain.model.CertificateType
import pe.gob.reniec.dnie.ui.design.AetherCard
import pe.gob.reniec.dnie.ui.design.aetherSpring
import pe.gob.reniec.dnie.ui.design.rememberReducedMotion
import java.text.SimpleDateFormat
import java.util.Locale

/** A compact credential pass. Cryptographic detail remains available without leading the screen. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CertificateCard(certificate: CertificateInfo, onExportCer: () -> Unit, onExportPem: () -> Unit,
    modifier: Modifier = Modifier) {
    var details by remember(certificate.sha256FingerprintHex) { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }
    val critical = certificate.isExpired || certificate.daysRemaining < 60
    val tone = when { certificate.isExpired -> MaterialTheme.colorScheme.error; critical -> Color(0xFF9A6500); else -> MaterialTheme.colorScheme.secondary }
    val timeline = certificate.daysRemaining.coerceIn(0, 1460).toFloat() / 1460f
    val icon = when (certificate.type) {
        CertificateType.SIGNATURE -> Icons.Default.Draw
        CertificateType.AUTHENTICATION -> Icons.Default.VerifiedUser
        CertificateType.ENCRYPTION -> Icons.Default.EnhancedEncryption
    }
    val reduced = rememberReducedMotion()
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            kotlinx.coroutines.delay(2000)
            copied = false
        }
    }
    AetherCard(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(icon, null, Modifier.padding(12.dp).size(24.dp), MaterialTheme.colorScheme.primary)
                }
                Column(Modifier.weight(1f)) {
                    Text(certificate.type.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("RENIEC CA", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AssistChip(onClick = {}, enabled = false, label = { Text(if (certificate.isExpired) "Expirado" else "Vigente") },
                    leadingIcon = { Icon(if (certificate.isExpired) Icons.Default.Cancel else Icons.Default.CheckCircle, null, Modifier.size(16.dp)) })
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LinearProgressIndicator(timeline, Modifier.fillMaxWidth().height(6.dp), color = tone, trackColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                Text(if (certificate.isExpired) "Venció el ${dateFormat.format(certificate.validTo)}" else "${certificate.daysRemaining} días restantes · vence el ${dateFormat.format(certificate.validTo)}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CredentialDatum("Titular", certificate.citizenName.ifBlank { "Sin nombre" }, Modifier.weight(1f))
                CredentialDatum("DNI", certificate.citizenDni.ifBlank { "Sin dato" }, Modifier.weight(1f))
                CredentialDatum("Válido desde", dateFormat.format(certificate.validFrom), Modifier.weight(1f))
                CredentialDatum("Uso", certificate.keyUsageDescription, Modifier.weight(1f))
            }
            TextButton(onClick = { details = !details }) { Text(if (details) "Ocultar detalles" else "Ver detalles técnicos") }
            AnimatedVisibility(
                visible = details,
                enter = expandVertically(animationSpec = aetherSpring(reduced)) + fadeIn(animationSpec = aetherSpring(reduced)),
                exit = shrinkVertically(animationSpec = aetherSpring(reduced)) + fadeOut(animationSpec = aetherSpring(reduced))
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Emisor · ${certificate.issuerDn}", style = MaterialTheme.typography.bodySmall)
                    Text("Serie · ${certificate.serialNumberHex}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.small) {
                        Column(Modifier.padding(10.dp)) {
                            Text(certificate.sha256FingerprintHex.chunked(4).joinToString(" "), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                            TextButton(onClick = {
                                clipboard.setText(AnnotatedString(certificate.sha256FingerprintHex))
                                copied = true
                            }) {
                                Text(if (copied) "✓ Copiada" else "Copiar huella")
                            }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onExportCer, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Download, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Exportar .cer") }
                OutlinedButton(onClick = onExportPem, modifier = Modifier.weight(1f)) { Text("Exportar .pem") }
            }
        }
    }
}

@Composable private fun CredentialDatum(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier, color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
}
