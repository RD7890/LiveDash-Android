package com.rd.livedash.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary              = Indigo,
    onPrimary            = OnIndigo,
    primaryContainer     = IndigoContainer,
    secondary            = Emerald,
    tertiary             = Rose,
    background           = Background,
    surface              = SurfaceCard,
    surfaceVariant       = SurfaceElevated,
    onBackground         = TextPrimary,
    onSurface            = TextPrimary,
    onSurfaceVariant     = TextSecondary,
    outline              = OutlineColor,
    error                = Color(0xFFFF4444),
)

@Composable
fun LiveDashTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, typography = Typography, content = content)
}
