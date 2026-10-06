package pe.gob.reniec.dnie.ui.vault

import androidx.compose.ui.graphics.Color
import androidx.compose.material3.darkColorScheme

internal val VaultBackground = Color(0xFF10181D)
internal val VaultSurface = Color(0xFF1B272D)
internal val VaultAccent = Color(0xFFB7E2CB)
internal val VaultColors = darkColorScheme(
    primary = VaultAccent, onPrimary = VaultBackground,
    background = VaultBackground, surface = VaultBackground,
    surfaceVariant = VaultSurface, onSurface = Color(0xFFF0F5F3),
    onSurfaceVariant = Color(0xFFB8C6C3), outline = Color(0xFF647771)
)
