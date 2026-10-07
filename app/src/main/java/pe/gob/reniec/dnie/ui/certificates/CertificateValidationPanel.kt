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
import pe.gob.reniec.dnie.validation.TrustStatus
import pe.gob.reniec.dnie.data.certificates.OnlineCertificateValidationRepository

@Composable
fun CertificateValidationPanel(certificate: CertificateInfo) {
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val factory = remember(context) { object : androidx.lifecycle.ViewModelProvider.Factory {
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            require(modelClass == CertificateValidationViewModel::class.java)
            @Suppress("UNCHECKED_CAST")
            return CertificateValidationViewModel(OnlineCertificateValidationRepository.create(context)) as T
        }
    } }
    val model: CertificateValidationViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = factory)
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(certificate.sha256FingerprintHex) { model.clear() }
    DisposableEffect(Unit) { onDispose { model.clear() } }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Comprobación del certificado", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(enabled = !state.checking, onClick = {
            model.check(certificate.rawDerBytes, certificate.sha256FingerprintHex, certificate.type == CertificateType.SIGNATURE)
        }) { Text(if (state.checking) "Consultando estado…" else "Comprobar confianza y revocación") }
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
                Text(when (report.trust) {
                    TrustStatus.TRUSTED -> "Cadena de confianza: verificada con TSL Perú"
                    TrustStatus.UNTRUSTED -> "Cadena de confianza: no admitida"
                    TrustStatus.UNKNOWN -> "Cadena de confianza: indeterminada"
                })
                Text(report.trustDetail, style = MaterialTheme.typography.bodySmall)
                if (pe.gob.reniec.dnie.BuildConfig.DEBUG && report.trustFailureCode.isNotEmpty())
                    Text("Diagnóstico: ${report.trustFailureCode}", style = MaterialTheme.typography.bodySmall)
                if (report.trust == TrustStatus.TRUSTED) Text(when (report.chainRevocation) {
                    RevocationStatus.GOOD -> "Titular y CA intermedias: sin revocación en evidencia verificada"
                    RevocationStatus.REVOKED -> "La cadena contiene un certificado revocado"
                    RevocationStatus.UNKNOWN -> "Revocación de la cadena: indeterminada"
                }, style = MaterialTheme.typography.bodySmall)
                report.chainValidUntil?.let { Text("Evidencias de cadena válidas hasta: $it", style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}
