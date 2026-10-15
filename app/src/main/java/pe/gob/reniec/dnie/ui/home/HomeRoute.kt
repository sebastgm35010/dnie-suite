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
    onVault: () -> Unit, onPc: () -> Unit, onSettings: () -> Unit, onDevelopment: (() -> Unit)?) {
    val summaries = remember(viewModel, identityViewModel) {
        combine(viewModel.uiState, identityViewModel.uiState) { state, identity ->
            HomeSummary((state as? CertificatesUiState.Loaded)?.certificates?.size ?: 0,
                identity.document?.blocks?.values?.any { it.status == pe.gob.reniec.dnie.icao.BlockStatus.AVAILABLE && it.file.group != null } == true, state is CertificatesUiState.ReadingCard || identity.reading || identity.awaitingCard)
        }.distinctUntilChanged()
    }
    val summary by summaries.collectAsStateWithLifecycle(initialValue = HomeSummary())
    val context = androidx.compose.ui.platform.LocalContext.current
    val preferences = (context.applicationContext as pe.gob.reniec.dnie.DnieApplication).appearance
    val dismissed by preferences.onboardingDismissed.collectAsStateWithLifecycle()
    var showGuide by remember { mutableStateOf(false) }
    var guideSavedCan by remember { mutableStateOf(false) }
    val canSaved by produceState(false, context, showGuide) { value = pe.gob.reniec.dnie.data.preferences.CanStorage.loadSavedCan(context).length == 6 }
    if (showGuide) pe.gob.reniec.dnie.ui.onboarding.OnboardingGuideSheet(
        onDismiss = { showGuide = false }, onFinish = { preferences.dismissOnboarding(); showGuide = false },
        onCanSaved = { guideSavedCan = true })
    HomeScreen(summary, onRead, onIdentity, onCertificates, onPdf, onBatch, onVault, onPc, onSettings, onDevelopment, savedCan = canSaved || guideSavedCan, onboardingDismissed = dismissed, onOpenGuide = { showGuide = true }, onDismissOnboarding = preferences::dismissOnboarding)
}
