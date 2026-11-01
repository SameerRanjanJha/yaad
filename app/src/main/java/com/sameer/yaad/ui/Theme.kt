package com.sameer.yaad.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFFC2410C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE4D6),
    onPrimaryContainer = Color(0xFF5A1A00),
    secondaryContainer = Color(0xFFF1E7DA),
    onSecondaryContainer = Color(0xFF3A2E22),
    background = Color(0xFFFAF7F2),
    onBackground = Color(0xFF1F1A15),
    surface = Color(0xFFFAF7F2),
    onSurface = Color(0xFF1F1A15),
    surfaceVariant = Color(0xFFF1E7DA),
    onSurfaceVariant = Color(0xFF6B5E50),
    surfaceContainer = Color(0xFFF4EEE5),
    surfaceContainerLow = Color(0xFFF7F2EA),
    surfaceContainerHigh = Color(0xFFEFE8DD),
    outline = Color(0xFFB9AC9D),
    outlineVariant = Color(0xFFE2D8CA),
    error = Color(0xFFB3261E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB590),
    onPrimary = Color(0xFF5A1A00),
    primaryContainer = Color(0xFFC2410C),
    onPrimaryContainer = Color(0xFFFFE4D6),
    secondaryContainer = Color(0xFF3A3027),
    onSecondaryContainer = Color(0xFFF1E7DA),
    background = Color(0xFF141210),
    onBackground = Color(0xFFEDE6DC),
    surface = Color(0xFF141210),
    onSurface = Color(0xFFEDE6DC),
    surfaceVariant = Color(0xFF2B2520),
    onSurfaceVariant = Color(0xFFC9BCAD),
    surfaceContainer = Color(0xFF1E1A16),
    surfaceContainerLow = Color(0xFF1A1714),
    surfaceContainerHigh = Color(0xFF28231E),
    outline = Color(0xFF8A7D6F),
    outlineVariant = Color(0xFF3D352D),
    error = Color(0xFFF2B8B5),
)

@Composable
fun YaadTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}
