package pe.gob.reniec.dnie.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Aether prototype adapted to actual product capabilities; all colours stay local/offline.
private val LightColorScheme = lightColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF004AC6),
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = androidx.compose.ui.graphics.Color(0xFFE5EEFF),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF00174B),
    secondary = androidx.compose.ui.graphics.Color(0xFF006A63),
    onSecondary = androidx.compose.ui.graphics.Color.White,
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFFD0F7F0),
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFF00201D),
    tertiary = androidx.compose.ui.graphics.Color(0xFF005A89),
    background = androidx.compose.ui.graphics.Color(0xFFF8F9FF),
    onBackground = androidx.compose.ui.graphics.Color(0xFF0B1C30),
    surface = androidx.compose.ui.graphics.Color.White,
    onSurface = androidx.compose.ui.graphics.Color(0xFF0B1C30),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFFEFF4FF),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF434655),
    outlineVariant = androidx.compose.ui.graphics.Color(0xFFDCE9FF),
    outline = androidx.compose.ui.graphics.Color(0xFF737686),
    surfaceTint = androidx.compose.ui.graphics.Color(0xFF004AC6),
    surfaceBright = androidx.compose.ui.graphics.Color(0xFFF8F9FF),
    surfaceDim = androidx.compose.ui.graphics.Color(0xFFCBDBF5),
    surfaceContainerLowest = androidx.compose.ui.graphics.Color.White,
    surfaceContainerLow = androidx.compose.ui.graphics.Color(0xFFF4F7FF),
    surfaceContainer = androidx.compose.ui.graphics.Color(0xFFEFF4FF),
    surfaceContainerHigh = androidx.compose.ui.graphics.Color(0xFFE5EEFF),
    surfaceContainerHighest = androidx.compose.ui.graphics.Color(0xFFDCE9FF),
    error = androidx.compose.ui.graphics.Color(0xFFBA1A1A)
)
private val DarkColorScheme = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF6B9EFF),
    onPrimary = androidx.compose.ui.graphics.Color(0xFF00174B),
    primaryContainer = androidx.compose.ui.graphics.Color(0xFF243554),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFFE5EEFF),
    secondary = androidx.compose.ui.graphics.Color(0xFF67D9C8),
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFF123D39),
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFF9CF2E8),
    background = androidx.compose.ui.graphics.Color(0xFF0B1326),
    surface = androidx.compose.ui.graphics.Color(0xFF131B2E),
    onSurface = androidx.compose.ui.graphics.Color(0xFFEAF1FF),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFF1D2840),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFA9B5CB),
    onBackground = androidx.compose.ui.graphics.Color(0xFFEAF1FF),
    onSecondary = androidx.compose.ui.graphics.Color(0xFF00201D),
    tertiary = androidx.compose.ui.graphics.Color(0xFF93CCFF),
    onTertiary = androidx.compose.ui.graphics.Color(0xFF001D31),
    tertiaryContainer = androidx.compose.ui.graphics.Color(0xFF004B73),
    onTertiaryContainer = androidx.compose.ui.graphics.Color(0xFFCCE5FF),
    outline = androidx.compose.ui.graphics.Color(0xFF8D94A6),
    outlineVariant = androidx.compose.ui.graphics.Color(0xFF2E3C56),
    surfaceTint = androidx.compose.ui.graphics.Color(0xFF6B9EFF),
    surfaceBright = androidx.compose.ui.graphics.Color(0xFF243554),
    surfaceDim = androidx.compose.ui.graphics.Color(0xFF0B1326),
    surfaceContainerLowest = androidx.compose.ui.graphics.Color(0xFF080E1C),
    surfaceContainerLow = androidx.compose.ui.graphics.Color(0xFF131B2E),
    surfaceContainer = androidx.compose.ui.graphics.Color(0xFF1D2840),
    surfaceContainerHigh = androidx.compose.ui.graphics.Color(0xFF243554),
    surfaceContainerHighest = androidx.compose.ui.graphics.Color(0xFF2E4063)
)

@Composable
fun DnieTheme(
    darkTheme: Boolean? = null,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val preferences = (context.applicationContext as pe.gob.reniec.dnie.DnieApplication).appearance
    val mode by preferences.mode.collectAsStateWithLifecycle()
    val resolvedDark = darkTheme ?: when (mode) {
        pe.gob.reniec.dnie.data.preferences.ThemeMode.LIGHT -> false
        pe.gob.reniec.dnie.data.preferences.ThemeMode.DARK -> true
        else -> isSystemInDarkTheme()
    }
    // Palette selection is independent of keyboard size and orientation; dark mode is an explicit key.
    val colorScheme = remember(context, dynamicColor, resolvedDark) {
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                if (resolvedDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            resolvedDark -> DarkColorScheme
            else -> LightColorScheme
        }
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        DisposableEffect(view, colorScheme.background, resolvedDark) {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = colorScheme.background.toArgb()
                window.navigationBarColor = colorScheme.surface.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !resolvedDark
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !resolvedDark
            }
            onDispose { }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes(small = RoundedCornerShape(8.dp), medium = RoundedCornerShape(12.dp), large = RoundedCornerShape(16.dp)),
        content = {
            if (mode != null || darkTheme != null || view.isInEditMode) content()
            else androidx.compose.material3.Surface(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {}
        }
    )
}
