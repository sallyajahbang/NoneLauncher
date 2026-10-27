package com.sally.launcher

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SallyLightColors = lightColorScheme(
    primary = Color(0xFF4259A7),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE2FF),
    onPrimaryContainer = Color(0xFF08164B),
    secondary = Color(0xFF58617D),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDEE2FF),
    onSecondaryContainer = Color(0xFF151C35),
    tertiary = Color(0xFF006B5B),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF78F8D9),
    onTertiaryContainer = Color(0xFF002019),
    background = Color(0xFFF8F8FF),
    onBackground = Color(0xFF191B22),
    surface = Color(0xFFF8F8FF),
    onSurface = Color(0xFF191B22),
    surfaceVariant = Color(0xFFE3E5F2),
    onSurfaceVariant = Color(0xFF454653),
    outline = Color(0xFF767785),
    outlineVariant = Color(0xFFC6C7D5),
)

private val SallyDarkColors = darkColorScheme(
    primary = Color(0xFFB8C5FF),
    onPrimary = Color(0xFF10266A),
    primaryContainer = Color(0xFF293F89),
    onPrimaryContainer = Color(0xFFDDE2FF),
    secondary = Color(0xFFC0C6E4),
    onSecondary = Color(0xFF292F49),
    secondaryContainer = Color(0xFF3F4662),
    onSecondaryContainer = Color(0xFFDEE2FF),
    tertiary = Color(0xFF5DDBC0),
    onTertiary = Color(0xFF00382E),
    tertiaryContainer = Color(0xFF005143),
    onTertiaryContainer = Color(0xFF78F8D9),
    background = Color(0xFF101116),
    onBackground = Color(0xFFE3E2EA),
    surface = Color(0xFF101116),
    onSurface = Color(0xFFE3E2EA),
    surfaceVariant = Color(0xFF444653),
    onSurfaceVariant = Color(0xFFC5C5D4),
    outline = Color(0xFF9091A0),
    outlineVariant = Color(0xFF444653),
)

@Composable
internal fun SallyMaterialTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) SallyDarkColors else SallyLightColors,
        typography = Typography(),
        content = content,
    )
}
