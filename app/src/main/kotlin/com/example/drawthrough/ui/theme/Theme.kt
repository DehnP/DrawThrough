package com.example.drawthrough.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val WarmStudioColorScheme = lightColorScheme(
    primary = Terracotta,
    onPrimary = OnTerracotta,
    primaryContainer = TerracottaSoft,
    onPrimaryContainer = TerracottaDark,
    secondary = StudioInkMuted,
    onSecondary = StudioCanvas,
    secondaryContainer = StudioSurfaceVariant,
    onSecondaryContainer = StudioInk,
    background = StudioCanvas,
    onBackground = StudioInk,
    surface = StudioHeader,
    onSurface = StudioInk,
    surfaceVariant = StudioPillSurface,
    onSurfaceVariant = StudioInkMuted,
    outline = StudioBorder,
    outlineVariant = StudioGridLine,
)

@Composable
fun DrawThroughTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = WarmStudioColorScheme,
        typography = Typography,
        content = content,
    )
}
