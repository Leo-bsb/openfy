package com.openfy.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.openfy.core.ColorPalette

private val Ink = Color(0xFF060909)
private val Panel = Color(0xFF0B1312)
private val PanelHigh = Color(0xFF13201D)
private val Line = Color(0xFF1D2C28)
private val TextHigh = Color(0xFFF1F7F4)
private val TextLow = Color(0xFF8CA6A0)
private val Accent = Color(0xFF2FBFA0)
private val Accent2 = Color(0xFF19D3E6)
private val ActiveBlue = Color(0xFF4D9BFF)

@Immutable
class OpenfyTokens(
    val ink: Color = Ink,
    val panel: Color = Panel,
    val panelHigh: Color = PanelHigh,
    val line: Color = Line,
    val textHigh: Color = TextHigh,
    val textLow: Color = TextLow,
    val accent: Color = Accent,
    val accent2: Color = Accent2,
    val active: Color = ActiveBlue,
    val heroTop: Color = lerp(Accent, Ink, 0.82f),
    val heroMid: Color = lerp(Accent, Ink, 0.94f),
) {
    val brandGradient: Brush = Brush.linearGradient(listOf(accent, accent2))
    val heroGradient: Brush = Brush.verticalGradient(
        0f to heroTop,
        0.55f to heroMid,
        1f to ink,
    )

    fun colorScheme() = darkColorScheme(
        primary = accent,
        onPrimary = ink,
        secondary = accent2,
        onSecondary = ink,
        background = ink,
        onBackground = textHigh,
        surface = panel,
        onSurface = textHigh,
        surfaceVariant = panelHigh,
        onSurfaceVariant = textLow,
        outline = line,
        error = Color(0xFFFFB4AB),
        onError = ink,
        secondaryContainer = panelHigh,
        onSecondaryContainer = accent,
    )
}

private val DefaultTokens = OpenfyTokens(
    heroTop = Color(0xFF10312C),
    heroMid = Color(0xFF09110F),
)

private val LocalOpenfyTokens = staticCompositionLocalOf { DefaultTokens }

private val HexPattern = Regex("^#[0-9a-fA-F]{6}$")

/** Converte "#RRGGBB" em [Color]; devolve null para qualquer outro formato. */
fun rgbHexColor(value: String): Color? {
    val clean = value.trim()
    if (!HexPattern.matches(clean)) return null
    val rgb = clean.substring(1).toLong(16)
    return Color(0xFF000000L or rgb)
}

val OpenfyInk: Color
    @Composable @ReadOnlyComposable get() = LocalOpenfyTokens.current.ink

val OpenfyPanel: Color
    @Composable @ReadOnlyComposable get() = LocalOpenfyTokens.current.panel

val OpenfyPanelHigh: Color
    @Composable @ReadOnlyComposable get() = LocalOpenfyTokens.current.panelHigh

val OpenfyLine: Color
    @Composable @ReadOnlyComposable get() = LocalOpenfyTokens.current.line

val OpenfyTextHigh: Color
    @Composable @ReadOnlyComposable get() = LocalOpenfyTokens.current.textHigh

val OpenfyTextLow: Color
    @Composable @ReadOnlyComposable get() = LocalOpenfyTokens.current.textLow

val OpenfyAccent: Color
    @Composable @ReadOnlyComposable get() = LocalOpenfyTokens.current.accent

val OpenfyAccent2: Color
    @Composable @ReadOnlyComposable get() = LocalOpenfyTokens.current.accent2

val OpenfyHeroGradient: Brush
    @Composable @ReadOnlyComposable get() = LocalOpenfyTokens.current.heroGradient

private fun ColorPalette.toTokens(): OpenfyTokens = when (this) {
    ColorPalette.OPENFY -> DefaultTokens
    ColorPalette.VIOLET -> OpenfyTokens(
        accent = Color(0xFFA78BFA),
        accent2 = Color(0xFF67E8F9),
        heroTop = Color(0xFF25163D),
        heroMid = Color(0xFF0F0A18),
    )
    ColorPalette.ROSE -> OpenfyTokens(
        accent = Color(0xFFF472B6),
        accent2 = Color(0xFFFDA4AF),
        active = Color(0xFFFF8FB8),
        heroTop = Color(0xFF3A1427),
        heroMid = Color(0xFF160A10),
    )
    ColorPalette.AMBER -> OpenfyTokens(
        accent = Color(0xFFF5A524),
        accent2 = Color(0xFFFBBF24),
        active = Color(0xFFFFC65C),
        heroTop = Color(0xFF3A2606),
        heroMid = Color(0xFF160F04),
    )
    ColorPalette.ICE -> OpenfyTokens(
        accent = Color(0xFF7DD3FC),
        accent2 = Color(0xFFE2E8F0),
        active = Color(0xFF93C5FD),
        heroTop = Color(0xFF102A3A),
        heroMid = Color(0xFF071016),
    )
}

@Composable
fun OpenfyTheme(
    palette: ColorPalette = ColorPalette.OPENFY,
    accentHex: String = "",
    accent2Hex: String = "",
    content: @Composable () -> Unit,
) {
    val tokens = remember(palette, accentHex, accent2Hex) {
        val base = palette.toTokens()
        val customAccent = rgbHexColor(accentHex)
        val customAccent2 = rgbHexColor(accent2Hex) ?: customAccent
        if (customAccent == null && customAccent2 == null) {
            base
        } else {
            val finalAccent = customAccent ?: base.accent
            val finalAccent2 = customAccent2 ?: base.accent2
            OpenfyTokens(
                ink = base.ink,
                panel = base.panel,
                panelHigh = base.panelHigh,
                line = base.line,
                textHigh = base.textHigh,
                textLow = base.textLow,
                accent = finalAccent,
                accent2 = finalAccent2,
                active = finalAccent2,
                heroTop = lerp(finalAccent, base.ink, 0.82f),
                heroMid = lerp(finalAccent, base.ink, 0.94f),
            )
        }
    }
    val scheme = remember(tokens) { tokens.colorScheme() }
    CompositionLocalProvider(LocalOpenfyTokens provides tokens) {
        MaterialTheme(
            colorScheme = scheme,
            typography = Typography,
            content = content,
        )
    }
}
