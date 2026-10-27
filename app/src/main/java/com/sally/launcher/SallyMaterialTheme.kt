package com.sally.launcher

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

internal enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Persisted appearance settings. The accent is always Material 3 orange. */
internal data class ThemeSettings(
    val mode: ThemeMode = ThemeMode.SYSTEM,
) {
    companion object {
        const val PREFS = "sally_launcher_settings"
        const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_LEGACY_DARK = "dark_theme"

        fun read(context: Context): ThemeSettings {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val stored = prefs.getString(KEY_THEME_MODE, null)?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            val mode = stored
                ?: if (prefs.contains(KEY_LEGACY_DARK)) {
                    if (prefs.getBoolean(KEY_LEGACY_DARK, true)) ThemeMode.DARK else ThemeMode.LIGHT
                } else {
                    ThemeMode.SYSTEM
                }
            return ThemeSettings(mode = mode)
        }
    }
}

// Material 3 tonal palette, seeded with orange #A44A00 (light) and #FFB68A (dark).
private val LightScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFFA44A00),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDBC9),
    onPrimaryContainer = Color(0xFF351000),
    secondary = Color(0xFF775644),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDBC9),
    onSecondaryContainer = Color(0xFF2C160A),
    tertiary = Color(0xFF64613B),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFEBE7AD),
    onTertiaryContainer = Color(0xFF1F1D00),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFF8F5),
    onBackground = Color(0xFF201A17),
    surface = Color(0xFFFFF8F5),
    onSurface = Color(0xFF201A17),
    surfaceVariant = Color(0xFFF4DED3),
    onSurfaceVariant = Color(0xFF52443C),
    outline = Color(0xFF85736A),
    outlineVariant = Color(0xFFD7C2B8),
    inverseSurface = Color(0xFF362F2B),
    inverseOnSurface = Color(0xFFFBEEE9),
    inversePrimary = Color(0xFFFFB68A),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFCF1EB),
    surfaceContainer = Color(0xFFF6EAE3),
    surfaceContainerHigh = Color(0xFFF1E4DD),
    surfaceContainerHighest = Color(0xFFEBDED7),
)

private val DarkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFFFB68A),
    onPrimary = Color(0xFF562000),
    primaryContainer = Color(0xFF7A3000),
    onPrimaryContainer = Color(0xFFFFDBC9),
    secondary = Color(0xFFE7BFA8),
    onSecondary = Color(0xFF442B1B),
    secondaryContainer = Color(0xFF5D4131),
    onSecondaryContainer = Color(0xFFFFDBC9),
    tertiary = Color(0xFFD1CA8E),
    onTertiary = Color(0xFF363208),
    tertiaryContainer = Color(0xFF4D4920),
    onTertiaryContainer = Color(0xFFEDE7A9),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF201A17),
    onBackground = Color(0xFFECE0DA),
    surface = Color(0xFF201A17),
    onSurface = Color(0xFFECE0DA),
    surfaceVariant = Color(0xFF52443C),
    onSurfaceVariant = Color(0xFFD7C2B8),
    outline = Color(0xFFA08D83),
    outlineVariant = Color(0xFF52443C),
    inverseSurface = Color(0xFFECE0DA),
    inverseOnSurface = Color(0xFF362F2B),
    inversePrimary = Color(0xFFA44A00),
    surfaceContainerLowest = Color(0xFF181210),
    surfaceContainerLow = Color(0xFF2A221E),
    surfaceContainer = Color(0xFF2E2622),
    surfaceContainerHigh = Color(0xFF382F2A),
    surfaceContainerHighest = Color(0xFF43392F),
)

private val SallyShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Resolves ThemeMode (System/Light/Dark) against the system setting. */
@Composable
internal fun SallyLauncherTheme(settings: ThemeSettings, content: @Composable () -> Unit) {
    val dark = when (settings.mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    SallyMaterialTheme(darkTheme = dark, content = content)
}

@Composable
internal fun SallyMaterialTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = Typography(),
        shapes = SallyShapes,
        content = content,
    )
}

/** Solid theme background for every launcher screen. */
@Composable
internal fun Modifier.launcherBackground(): Modifier = this.background(MaterialTheme.colorScheme.background)
