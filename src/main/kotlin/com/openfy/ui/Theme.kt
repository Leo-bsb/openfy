package com.openfy.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Preto profundo, verde-água e ciano.
private val Ink = Color(0xFF060909)
private val Panel = Color(0xFF0B1312)
private val PanelHigh = Color(0xFF13201D)
private val Line = Color(0xFF1D2C28)
private val TextHigh = Color(0xFFF1F7F4)
private val TextLow = Color(0xFF8CA6A0)
private val Accent = Color(0xFF2FBFA0)
private val Accent2 = Color(0xFF19D3E6)

val OpenfyColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF04120E),
    secondary = Accent2,
    onSecondary = Color(0xFF041214),
    background = Ink,
    onBackground = TextHigh,
    surface = Panel,
    onSurface = TextHigh,
    surfaceVariant = PanelHigh,
    onSurfaceVariant = TextLow,
    outline = Line,
)

object Tokens {
    val ink = Ink
    val panel = Panel
    val panelHigh = PanelHigh
    val line = Line
    val textHigh = TextHigh
    val textLow = TextLow
    val accent = Accent
    val accent2 = Accent2

    /** Gradiente da marca (verde-água -> ciano). */
    val brandGradient: Brush = Brush.linearGradient(listOf(Accent, Accent2))

    /** Fundo do player em tela cheia. */
    val heroGradient: Brush = Brush.verticalGradient(
        0f to Color(0xFF10312C),
        0.55f to Color(0xFF09110F),
        1f to Ink,
    )

    /** Fundo dos painéis (sidebar/player bar). */
    val panelGradient: Brush = Brush.verticalGradient(
        listOf(Color(0xFF101A18), Color(0xFF0A100F)),
    )
}

@Composable
fun OpenfyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = OpenfyColors, content = content)
}
