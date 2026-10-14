package pe.gob.reniec.dnie.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Destinos principales de navegación de la aplicación DNIe.
 */
sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    data object Certificates : Screen(
        route = "certificates",
        title = "Mi DNIe",
        icon = Icons.Default.Badge
    )

    data object Signer : Screen(
        route = "signer",
        title = "Firmar",
        icon = Icons.Default.DriveFileRenameOutline
    )

    data object Console : Screen(
        route = "console",
        title = "Desarrollo",
        icon = Icons.Default.Terminal
    )

    companion object {
        // Defer nested objects until Screen superclass initialization has completed.
        val items: List<Screen> by lazy { listOf(Home, Certificates, Signer, Vault, Pc) }
        val destinations: List<Screen> get() = items + listOf(Identity, BatchSigner)
        fun primaryFor(route: String?): Screen? = when (route) {
            Identity.route -> Certificates
            BatchSigner.route -> Signer
            else -> items.firstOrNull { it.route == route }
        }
    }

    data object Home : Screen("home", "Inicio", Icons.Default.Home)
    data object Pc : Screen("pc", "PC", Icons.Default.Computer)

    data object Identity : Screen("identity", "Identidad", Icons.Default.Person)
    data object BatchSigner : Screen("batchSigner", "Lotes", Icons.AutoMirrored.Filled.LibraryBooks)
    data object Vault : Screen("vault", "Bóveda", Icons.Default.Lock)
}
