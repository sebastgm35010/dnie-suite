package pe.gob.reniec.dnie.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Terminal
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
        val items = listOf(Certificates, Signer, Console)
    }
}
