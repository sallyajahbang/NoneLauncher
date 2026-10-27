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

/** Persisted appearance settings. */
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

// Palette: deep charcoal surfaces with violet primary and lime launch accent.
private val DarkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFA98BFF),
    onPrimary = Color(0xFF1C0C4D),
    primaryContainer = Color(0xFF3B2A78),
    onPrimaryContainer = Color(0xFFEADBFF),
    secondary = Color(0xFF8BE05A),
    onSecondary = Color(0xFF0D2600),
    secondaryContainer = Color(0xFF2A4A1C),
    onSecondaryContainer = Color(0xFFD2F7B8),
    tertiary = Color(0xFFFF7AB6),
    onTertiary = Color(0xFF4A0027),
    tertiaryContainer = Color(0xFF5A1A3D),
    onTertiaryContainer = Color(0xFFFFD8EA),
    error = Color(0xFFFF6B81),
    onError = Color(0xFF690015),
    errorContainer = Color(0xFF7A1F30),
    onErrorContainer = Color(0xFFFFDAE0),
    background = Color(0xFF0C0C11),
    onBackground = Color(0xFFEEEEF4),
    surface = Color(0xFF0C0C11),
    onSurface = Color(0xFFEEEEF4),
    surfaceVariant = Color(0xFF2A2A38),
    onSurfaceVariant = Color(0xFFA6A6BA),
    outline = Color(0xFF4A4A5C),
    outlineVariant = Color(0xFF2E2E3C),
    surfaceContainerLowest = Color(0xFF08080C),
    surfaceContainerLow = Color(0xFF14141C),
    surfaceContainer = Color(0xFF1A1A24),
    surfaceContainerHigh = Color(0xFF22222E),
    surfaceContainerHighest = Color(0xFF2A2A38),
)

private val LightScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF6D4AE0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE6DDFF),
    onPrimaryContainer = Color(0xFF230A63),
    secondary = Color(0xFF3F9A1C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD5F5BE),
    onSecondaryContainer = Color(0xFF0F3300),
    tertiary = Color(0xFFB8336A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD8EA),
    onTertiaryContainer = Color(0xFF3A0019),
    error = Color(0xFFBA1A3A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAE0),
    onErrorContainer = Color(0xFF410010),
    background = Color(0xFFF6F5FB),
    onBackground = Color(0xFF15131F),
    surface = Color(0xFFF6F5FB),
    onSurface = Color(0xFF15131F),
    surfaceVariant = Color(0xFFE4E1EE),
    onSurfaceVariant = Color(0xFF4B4860),
    outline = Color(0xFF7B7890),
    outlineVariant = Color(0xFFD0CDDD),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEFEDF7),
    surfaceContainer = Color(0xFFE9E7F2),
    surfaceContainerHigh = Color(0xFFE2DFEE),
    surfaceContainerHighest = Color(0xFFDBD8E8),
)

private val SallyShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
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
            surfaceContainerLow = Color(0xFF0A0A0E),
            surfaceContainer = Color(0xFF0E0E14),
        )
    }
    MaterialTheme(colorScheme = scheme, typography = Typography(), shapes = SallyShapes, content = content)
}
