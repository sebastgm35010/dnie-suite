package pe.gob.reniec.dnie.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Lock
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
        title = "Certificados",
        icon = Icons.Default.Badge
    )

    data object Signer : Screen(
        route = "signer",
        title = "Firmar PDF",
        icon = Icons.Default.DriveFileRenameOutline
    )

    data object Console : Screen(
        route = "console",
        title = "Consola APDU",
        icon = Icons.Default.Terminal
    )

    companion object {
        val items = listOf(Identity, Certificates, Signer, BatchSigner, Console)
    }

    data object Identity : Screen("identity", "Identidad", Icons.Default.Person)
    data object BatchSigner : Screen("batchSigner", "Lotes", Icons.AutoMirrored.Filled.LibraryBooks)
    data object Vault : Screen("vault", "Bóveda", Icons.Default.Lock)
}
