package pe.gob.reniec.dnie.ui.certificates

import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pe.gob.reniec.dnie.domain.model.CertificateInfo
import pe.gob.reniec.dnie.domain.model.CertificateType
import pe.gob.reniec.dnie.validation.RevocationStatus

@Composable
fun CertificateValidationPanel(certificate: CertificateInfo,
    model: CertificateValidationViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(certificate.sha256FingerprintHex) { model.clear() }
    DisposableEffect(Unit) { onDispose { model.clear() } }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Comprobación del certificado", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(enabled = !state.checking, onClick = {
            model.check(certificate.rawDerBytes, certificate.sha256FingerprintHex, certificate.type == CertificateType.SIGNATURE)
        }) { Text(if (state.checking) "Consultando estado…" else "Comprobar revocación") }
        if (state.fingerprint == certificate.sha256FingerprintHex) {
            state.error?.let { Text(it) }
            state.report?.let { report ->
                Text(if (report.certificate.acceptable) "Vigencia y uso del certificado: aptos" else report.certificate.issues.joinToString("; "))
                val label = when (report.revocation.status) {
                    RevocationStatus.GOOD -> "Sin revocación en evidencia verificada"
                    RevocationStatus.REVOKED -> "Certificado revocado"
                    RevocationStatus.UNKNOWN -> "Revocación indeterminada"
                }
                Text(label)
                Text(report.revocation.detail, style = MaterialTheme.typography.bodySmall)
                report.revocation.nextUpdate?.let { Text("Estado válido hasta: $it", style = MaterialTheme.typography.bodySmall) }
                Text("Consulta del certificado seleccionado. Confianza de cadena y TSL aún no comprobadas.",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
