package pe.gob.reniec.dnie.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import pe.gob.reniec.dnie.ui.certificates.CertificatesScreen
import pe.gob.reniec.dnie.ui.certificates.CertificatesViewModel
import pe.gob.reniec.dnie.development.DevelopmentScreen
import pe.gob.reniec.dnie.development.DevelopmentController
import pe.gob.reniec.dnie.ui.signer.SignerScreen
import pe.gob.reniec.dnie.ui.signer.SignerViewModel
import pe.gob.reniec.dnie.ui.identity.IdentityScreen
import pe.gob.reniec.dnie.ui.identity.IdentityViewModel
import pe.gob.reniec.dnie.ui.vault.VaultScreen
import pe.gob.reniec.dnie.ui.vault.VaultViewModel

/**
 * Grafo principal de navegación con barra inferior Material 3.
 */
@Composable
fun NavGraph(
    navController: NavHostController = rememberNavController(),
    certificatesViewModel: CertificatesViewModel = viewModel(),
    signerViewModel: SignerViewModel = viewModel(),
    batchSignerViewModel: SignerViewModel = viewModel(key = "batchSigner"),
    consoleViewModel: DevelopmentController = viewModel(),
    identityViewModel: IdentityViewModel = viewModel(),
    vaultViewModel: VaultViewModel = viewModel(),
    onRouteChanged: (String?) -> Unit = {},
    onOpenBridge: () -> Unit = {},
    onOpenSuite: () -> Unit = {}
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    LaunchedEffect(currentRoute) { onRouteChanged(currentRoute) }
    IdentitySignatureBridge(identityViewModel, certificatesViewModel, signerViewModel, batchSignerViewModel)
    val incomingFiles by remember(vaultViewModel) {
        vaultViewModel.uiState.map { it.incomingFiles }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = 0)
    LaunchedEffect(incomingFiles) {
        if (incomingFiles > 0) navController.navigate(Screen.Vault.route) { launchSingleTop = true }
    }

    Scaffold(
        bottomBar = {
            if (currentRoute != Screen.Vault.route) NavigationBar {
                Screen.items.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = currentRoute == screen.route,
                        onClick = {
                            if (currentRoute != screen.route) {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Certificates.route,
            modifier = Modifier.padding(innerPadding),
            // Bottom tabs switch immediately: avoid laying out and drawing two
            // feature screens together during Navigation's default crossfade.
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None }
        ) {
            composable(Screen.Certificates.route) {
                CertificatesScreen(viewModel = certificatesViewModel,
                    onOpenBridge = onOpenBridge,
                    onOpenSuite = onOpenSuite,
                    onOpenVault = { navController.navigate(Screen.Vault.route) { launchSingleTop = true } })
            }
            composable(Screen.Signer.route) {
                SignerScreen(viewModel = signerViewModel,
                    onReadIdentity = { navController.navigate(Screen.Identity.route) { launchSingleTop = true } },
                    onReadCertificates = { navController.navigate(Screen.Certificates.route) { launchSingleTop = true } })
            }
            if (pe.gob.reniec.dnie.BuildConfig.DEBUG) composable(Screen.Console.route) {
                DevelopmentScreen(consoleViewModel)
            }
            composable(Screen.BatchSigner.route) {
                SignerScreen(viewModel = batchSignerViewModel, batch = true,
                    onReadIdentity = { navController.navigate(Screen.Identity.route) { launchSingleTop = true } },
                    onReadCertificates = { navController.navigate(Screen.Certificates.route) { launchSingleTop = true } })
            }
            composable(Screen.Identity.route) {
                IdentityScreen(viewModel = identityViewModel)
            }
            composable(Screen.Vault.route) {
                val context = LocalContext.current
                val state by vaultViewModel.uiState.collectAsStateWithLifecycle()
                LaunchedEffect(context) { vaultViewModel.initialize(context) }
                LaunchedEffect(state.preview) {
                    state.preview?.let { ticket ->
                        try {
                            context.startActivity(pe.gob.reniec.dnie.ui.vault.VaultPreviewActivity.intent(context, ticket))
                            vaultViewModel.previewLaunched()
                        } catch (_: Exception) { vaultViewModel.cancel() }
                    }
                }
                DisposableEffect(vaultViewModel) {
                    onDispose { vaultViewModel.clearSelection(); vaultViewModel.cancel() }
                }
                val leaveVault = {
                    vaultViewModel.clearSelection(); vaultViewModel.cancel(); navController.popBackStack(); Unit
                }
                BackHandler(onBack = leaveVault)
                VaultScreen(state = state,
                    onCanChange = vaultViewModel::updateCan,
                    onPinChange = vaultViewModel::updatePin,
                    onRememberCan = vaultViewModel::setRememberCan,
                    onRun = vaultViewModel::startTest,
                    onCancel = vaultViewModel::cancel,
                    onImportArchive = vaultViewModel::importArchive,
                    onExportEncrypted = vaultViewModel::exportEncrypted,
                    onRequestOpen = vaultViewModel::requestOpen,
                    onStartOpen = vaultViewModel::startOpen,
                    onClearSelection = vaultViewModel::clearSelection,
                    onDelete = vaultViewModel::delete,
                    onAcknowledgeRisk = vaultViewModel::acknowledgeRisk,
                    recovery = pe.gob.reniec.dnie.ui.vault.VaultRecoveryActions(
                        prepare = vaultViewModel::prepareRecovery, restore = vaultViewModel::restoreRecovery),
                    library = pe.gob.reniec.dnie.ui.vault.VaultLibraryActions(
                        importFiles = vaultViewModel::importFiles, exportBackup = vaultViewModel::exportBackup,
                        importBackup = vaultViewModel::importBackup, query = vaultViewModel::updateQuery,
                        folder = vaultViewModel::selectFolder, sort = vaultViewModel::sortBy,
                        trash = vaultViewModel::showTrash, organize = vaultViewModel::organize,
                        recover = vaultViewModel::recoverTrash, purge = vaultViewModel::permanentlyDelete,
                        preview = vaultViewModel::requestPreview, createNote = vaultViewModel::createNote,
                        encryptIncoming = vaultViewModel::encryptSharedFiles, dismissIncoming = vaultViewModel::dismissSharedFiles,
                        inspect = vaultViewModel::inspect, closeDetails = vaultViewModel::closeDetails,
                        trashMany = vaultViewModel::trashMany, organizeMany = vaultViewModel::organizeMany),
                    onBack = leaveVault)
            }
        }
    }
}
