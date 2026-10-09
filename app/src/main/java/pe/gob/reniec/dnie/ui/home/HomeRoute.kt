package pe.gob.reniec.dnie.ui.home

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import pe.gob.reniec.dnie.ui.certificates.CertificatesUiState
import pe.gob.reniec.dnie.ui.certificates.CertificatesViewModel
import pe.gob.reniec.dnie.ui.identity.IdentityViewModel

/** Narrow UI projection; does not start engines, load storage or retain identity/credentials. */
@Composable
fun HomeRoute(viewModel: CertificatesViewModel, identityViewModel: IdentityViewModel, onRead: () -> Unit, onIdentity: () -> Unit,
    onCertificates: () -> Unit, onPdf: () -> Unit, onBatch: () -> Unit,
    onVault: () -> Unit, onPc: () -> Unit, onDevelopment: (() -> Unit)?) {
    val summaries = remember(viewModel, identityViewModel) {
        combine(viewModel.uiState, identityViewModel.uiState) { state, identity ->
            HomeSummary((state as? CertificatesUiState.Loaded)?.certificates?.size ?: 0,
                identity.document != null, state is CertificatesUiState.ReadingCard || identity.reading || identity.awaitingCard)
        }.distinctUntilChanged()
    }
    val summary by summaries.collectAsStateWithLifecycle(initialValue = HomeSummary())
    HomeScreen(summary, onRead, onIdentity, onCertificates, onPdf, onBatch, onVault, onPc, onDevelopment)
}
