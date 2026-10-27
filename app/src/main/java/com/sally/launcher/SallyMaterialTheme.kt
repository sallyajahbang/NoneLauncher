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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

internal enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Persisted appearance settings. The accent is always Material 3 blue. */
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

// Material 3 tonal palette, seeded with blue #0B57D0 (light) and #A8C7FA (dark).
private val LightScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF0B57D0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3E3FD),
    onPrimaryContainer = Color(0xFF041E49),
    secondary = Color(0xFF565E71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDAE2F9),
    onSecondaryContainer = Color(0xFF131C2B),
    tertiary = Color(0xFF6B5778),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF3DAFF),
    onTertiaryContainer = Color(0xFF251431),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF8F9FF),
    onBackground = Color(0xFF191C20),
    surface = Color(0xFFF8F9FF),
    onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFDFE2EB),
    onSurfaceVariant = Color(0xFF43474E),
    outline = Color(0xFF73777F),
    outlineVariant = Color(0xFFC3C6CF),
    inverseSurface = Color(0xFF2E3135),
    inverseOnSurface = Color(0xFFEFF0F7),
    inversePrimary = Color(0xFFA8C7FA),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2F3F9),
    surfaceContainer = Color(0xFFECEEF4),
    surfaceContainerHigh = Color(0xFFE6E8EE),
    surfaceContainerHighest = Color(0xFFE0E2E8),
)

private val DarkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFA8C7FA),
    onPrimary = Color(0xFF0A306F),
    primaryContainer = Color(0xFF0842A0),
    onPrimaryContainer = Color(0xFFD3E3FD),
    secondary = Color(0xFFBEC6DC),
    onSecondary = Color(0xFF283141),
    secondaryContainer = Color(0xFF3E4759),
    onSecondaryContainer = Color(0xFFDAE2F9),
    tertiary = Color(0xFFD6BEE4),
    onTertiary = Color(0xFF3B2948),
    tertiaryContainer = Color(0xFF523F5F),
    onTertiaryContainer = Color(0xFFF3DAFF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF44474E),
    onSurfaceVariant = Color(0xFFC4C6D0),
    outline = Color(0xFF8E9099),
    outlineVariant = Color(0xFF44474F),
    inverseSurface = Color(0xFFE2E2E9),
    inverseOnSurface = Color(0xFF2F3033),
    inversePrimary = Color(0xFF0B57D0),
    surfaceContainerLowest = Color(0xFF0C0E13),
    surfaceContainerLow = Color(0xFF1A1B20),
    surfaceContainer = Color(0xFF1E1F25),
    surfaceContainerHigh = Color(0xFF282A2F),
    surfaceContainerHighest = Color(0xFF333539),
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

/**
 * Soft vertical gradient with a faint blue glow in the top-right corner.
 * Used as the background of every launcher screen so content sits on one continuous surface.
 */
@Composable
internal fun Modifier.launcherBackground(): Modifier {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val top = if (dark) Color(0xFF14285A) else Color(0xFFD9E6FF)
    val bottom = if (dark) Color(0xFF0C0E13) else Color(0xFFF8F9FF)
    val glow = if (dark) Color(0x553D6BDB) else Color(0x665B8DEF)
    return this.drawBehind {
        drawRect(brush = Brush.verticalGradient(listOf(top, bottom)))
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(glow, Color.Transparent),
                center = Offset(size.width * 0.85f, 0f),
                radius = size.maxDimension * 0.6f,
            ),
        )
    }
}
