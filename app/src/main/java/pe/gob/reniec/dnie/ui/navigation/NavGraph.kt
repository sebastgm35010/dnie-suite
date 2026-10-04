package pe.gob.reniec.dnie.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import pe.gob.reniec.dnie.ui.certificates.CertificatesScreen
import pe.gob.reniec.dnie.ui.certificates.CertificatesViewModel
import pe.gob.reniec.dnie.ui.console.ConsoleScreen
import pe.gob.reniec.dnie.ui.console.ConsoleViewModel
import pe.gob.reniec.dnie.ui.signer.SignerScreen
import pe.gob.reniec.dnie.ui.signer.SignerViewModel

/**
 * Grafo principal de navegación con barra inferior Material 3.
 */
@Composable
fun NavGraph(
    navController: NavHostController = rememberNavController(),
    certificatesViewModel: CertificatesViewModel = viewModel(),
    signerViewModel: SignerViewModel = viewModel(),
    consoleViewModel: ConsoleViewModel = viewModel()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
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
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Certificates.route) {
                CertificatesScreen(viewModel = certificatesViewModel)
            }
            composable(Screen.Signer.route) {
                SignerScreen(viewModel = signerViewModel)
            }
            composable(Screen.Console.route) {
                ConsoleScreen(viewModel = consoleViewModel)
            }
        }
    }
}
