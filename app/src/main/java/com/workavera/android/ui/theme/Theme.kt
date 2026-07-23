package com.workavera.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Dynamic color (Material You) is deliberately not used, keeping Workavera's
// black-and-white brand consistent.
private val LightColors = lightColorScheme(
    primary = Ink,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6E6E6),
    onPrimaryContainer = Ink,
    secondary = Color(0xFF5A5A5A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEAEAEA),
    onSecondaryContainer = Ink,
    tertiary = Color(0xFF3C3C3C),
    onTertiary = Color.White,
    background = Color(0xFFF7F7F7),
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEFEFEF),
    onSurfaceVariant = Color(0xFF4A4A4A),
    surfaceTint = Ink,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF5F5F5),
    surfaceContainer = Color(0xFFF0F0F0),
    surfaceContainerHigh = Color(0xFFEAEAEA),
    surfaceContainerHighest = Color(0xFFE4E4E4),
    outline = Color(0xFF787878),
    outlineVariant = Color(0xFFD6D6D6),
)

private val DarkColors = darkColorScheme(
    primary = Paper,
    onPrimary = Ink,
    primaryContainer = Color(0xFF333333),
    onPrimaryContainer = Paper,
    secondary = Color(0xFFB8B8B8),
    onSecondary = Ink,
    secondaryContainer = Color(0xFF2E2E2E),
    onSecondaryContainer = Color(0xFFE6E6E6),
    tertiary = Color(0xFFCFCFCF),
    onTertiary = Ink,
    background = Color(0xFF121212),
    onBackground = Color(0xFFE8E8E8),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFE8E8E8),
    surfaceVariant = Color(0xFF2A2A2A),
    onSurfaceVariant = Color(0xFFBDBDBD),
    surfaceTint = Paper,
    surfaceContainerLowest = Color(0xFF0D0D0D),
    surfaceContainerLow = Color(0xFF1A1A1A),
    surfaceContainer = Color(0xFF1F1F1F),
    surfaceContainerHigh = Color(0xFF292929),
    surfaceContainerHighest = Color(0xFF333333),
    outline = Color(0xFF8A8A8A),
    outlineVariant = Color(0xFF3A3A3A),
)

/**
 * Material 3 Expressive theme with the app's own monochrome color scheme:
 * we take the expressive spring-physics motion but keep the black-and-white
 * brand palette instead of expressive/dynamic colors.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WorkaveraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialExpressiveTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        motionScheme = MotionScheme.expressive(),
        typography = Typography(),
        content = content,
    )
}
