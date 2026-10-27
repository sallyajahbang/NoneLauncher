package com.sally.launcher

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

internal enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Persisted appearance settings (design.md §4.1). */
internal data class ThemeSettings(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val amoled: Boolean = false,
    val dynamicColor: Boolean = false,
) {
    companion object {
        const val PREFS = "sally_launcher_settings"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_AMOLED = "amoled_black"
        const val KEY_DYNAMIC_COLOR = "dynamic_color"
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
            return ThemeSettings(
                mode = mode,
                amoled = prefs.getBoolean(KEY_AMOLED, false),
                dynamicColor = prefs.getBoolean(KEY_DYNAMIC_COLOR, false),
            )
        }
    }
}

// Palette from design.md §4.1 (primary, onPrimary, primaryContainer, secondary, tertiary,
// surface, error, and the dark/light pairs). Remaining roles are derived from the same green ramp.
private val LightScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF2E7D32),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB8F2B8),
    onPrimaryContainer = Color(0xFF002106),
    secondary = Color(0xFF52634F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD4E8CF),
    onSecondaryContainer = Color(0xFF101F10),
    tertiary = Color(0xFF39656B),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBCEBF1),
    onTertiaryContainer = Color(0xFF001F24),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF7FBF1),
    onBackground = Color(0xFF181D16),
    surface = Color(0xFFF7FBF1),
    onSurface = Color(0xFF181D16),
    surfaceVariant = Color(0xFFDDE5D6),
    onSurfaceVariant = Color(0xFF424940),
    outline = Color(0xFF72796F),
    outlineVariant = Color(0xFFC2C9BD),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F7EC),
    surfaceContainer = Color(0xFFEBF1E5),
    surfaceContainerHigh = Color(0xFFE5EBDF),
    surfaceContainerHighest = Color(0xFFDFE6D9),
)

private val DarkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFF81C784),
    onPrimary = Color(0xFF003910),
    primaryContainer = Color(0xFF0F5222),
    onPrimaryContainer = Color(0xFFB8F2B8),
    secondary = Color(0xFFB9CCB4),
    onSecondary = Color(0xFF243424),
    secondaryContainer = Color(0xFF3A4B39),
    onSecondaryContainer = Color(0xFFD4E8CF),
    tertiary = Color(0xFFA1CED5),
    onTertiary = Color(0xFF033640),
    tertiaryContainer = Color(0xFF204D56),
    onTertiaryContainer = Color(0xFFBCEBF1),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF10140F),
    onBackground = Color(0xFFE0E4DB),
    surface = Color(0xFF10140F),
    onSurface = Color(0xFFE0E4DB),
    surfaceVariant = Color(0xFF424940),
    onSurfaceVariant = Color(0xFFC2C9BD),
    outline = Color(0xFF8C9388),
    outlineVariant = Color(0xFF424940),
    surfaceContainerLowest = Color(0xFF0B0F0A),
    surfaceContainerLow = Color(0xFF181D16),
    surfaceContainer = Color(0xFF1C211A),
    surfaceContainerHigh = Color(0xFF262B24),
    surfaceContainerHighest = Color(0xFF313630),
)

// design.md §4.3: cards/dialogs large (16–28dp), chips/text fields small/medium.
private val SallyShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(20.dp),
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
    SallyMaterialTheme(darkTheme = dark, amoled = settings.amoled, dynamicColor = settings.dynamicColor, content = content)
}

@Composable
internal fun SallyMaterialTheme(
    darkTheme: Boolean,
    amoled: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    var scheme: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkScheme
        else -> LightScheme
    }
    if (darkTheme && amoled) {
        scheme = scheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF0A0D09),
            surfaceContainer = Color(0xFF0E120D),
        )
    }
    MaterialTheme(colorScheme = scheme, typography = Typography(), shapes = SallyShapes, content = content)
}
