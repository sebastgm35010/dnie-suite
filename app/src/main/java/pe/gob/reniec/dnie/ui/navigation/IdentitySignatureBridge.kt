package pe.gob.reniec.dnie.ui.navigation

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pe.gob.reniec.dnie.domain.signing.HandwrittenMark
import pe.gob.reniec.dnie.domain.model.CertificateType
import pe.gob.reniec.dnie.icao.*
import pe.gob.reniec.dnie.ui.certificates.*
import pe.gob.reniec.dnie.ui.identity.*
import pe.gob.reniec.dnie.ui.signer.SignerViewModel
import java.io.ByteArrayOutputStream

/** Share only public certificate data and the in-memory DG7 image between feature screens. */
@Composable
fun IdentitySignatureBridge(identity: IdentityViewModel, certificates: CertificatesViewModel,
                            signer: SignerViewModel, batchSigner: SignerViewModel) {
    val signers = remember(signer, batchSigner) { listOf(signer, batchSigner) }
    val identityState by identity.uiState.collectAsStateWithLifecycle()
    val certificateState by certificates.uiState.collectAsStateWithLifecycle()
    val document = identityState.document
    val block = document?.blocks?.get(IcaoFile.DG7)
    val image = (block?.content as? IcaoContent.Images)?.images?.firstOrNull()
    val person = document?.blocks?.get(IcaoFile.DG1)?.content as? IcaoContent.Identity
    val number = (document?.blocks?.get(IcaoFile.DG11)?.content as? IcaoContent.Details)?.fields?.get("Número personal")
    LaunchedEffect(image, person, number, block?.hashStatus) {
        if (image == null) signers.forEach { it.setHandwrittenMark(null) }
        else if (block?.hashStatus == HashStatus.MISMATCH) signers.forEach {
            it.setHandwrittenMark(null, "La integridad de DG7 no coincide; vuelva a leer el DNI.")
        }
        else {
            try {
                // Prepare DG7 once for both workspaces. Encoding can take several
                // frames, so keep both decoding and compression off the UI thread.
                val png = withContext(Dispatchers.Default) {
                    val bitmap = IdentityImageDecoder.decode(image)
                    try { ByteArrayOutputStream().use { out ->
                        check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out))
                        out.toByteArray()
                    } } finally { bitmap.recycle() }
                }
                val mark = HandwrittenMark(png,
                    person?.let { (it.givenNames + " " + it.surnames).trim() } ?: "Titular de la lectura ICAO", number)
                signers.forEach { it.setHandwrittenMark(mark) }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { signers.forEach { it.setHandwrittenMark(null, e.message ?: "No se pudo preparar la imagen manuscrita") } }
        }
    }
    val certificate = (certificateState as? CertificatesUiState.Loaded)?.certificates?.get(CertificateType.SIGNATURE)
    LaunchedEffect(certificate) {
        certificate?.let { cert -> withContext(Dispatchers.Default) {
            signers.forEach { it.setPublicCertificate(cert.rawDerBytes) }
        } }
    }
}
