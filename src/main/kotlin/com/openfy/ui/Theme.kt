package com.openfy.ui

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
import androidx.compose.ui.graphics.toArgb
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
    val panelGradient: Brush = Brush.verticalGradient(
        listOf(lerp(panelHigh, accent, 0.025f), lerp(panel, ink, 0.50f)),
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
    )

    /** Mantém a base (fundos, textos) e recalcula tudo que depende do destaque. */
    fun withAccent(primary: Color, secondary: Color = accent2): OpenfyTokens = OpenfyTokens(
        ink = ink,
        panel = panel,
        panelHigh = panelHigh,
        line = line,
        textHigh = textHigh,
        textLow = textLow,
        accent = primary,
        accent2 = secondary,
        active = active,
        heroTop = lerp(primary, ink, 0.82f),
        heroMid = lerp(primary, ink, 0.94f),
    )
}

private val DefaultTokens = OpenfyTokens(
    heroTop = Color(0xFF10312C),
    heroMid = Color(0xFF09110F),
)

private val LocalOpenfyTokens = staticCompositionLocalOf { DefaultTokens }

/** Acesso de leitura à paleta atual; a API de uso continua `Tokens.accent` etc. */
object Tokens {
    val ink: Color @Composable @ReadOnlyComposable get() = LocalOpenfyTokens.current.ink
    val panel: Color @Composable @ReadOnlyComposable get() = LocalOpenfyTokens.current.panel
    val panelHigh: Color @Composable @ReadOnlyComposable get() = LocalOpenfyTokens.current.panelHigh
    val line: Color @Composable @ReadOnlyComposable get() = LocalOpenfyTokens.current.line
    val textHigh: Color @Composable @ReadOnlyComposable get() = LocalOpenfyTokens.current.textHigh
    val textLow: Color @Composable @ReadOnlyComposable get() = LocalOpenfyTokens.current.textLow
    val accent: Color @Composable @ReadOnlyComposable get() = LocalOpenfyTokens.current.accent
    val accent2: Color @Composable @ReadOnlyComposable get() = LocalOpenfyTokens.current.accent2
    val active: Color @Composable @ReadOnlyComposable get() = LocalOpenfyTokens.current.active
    val brandGradient: Brush @Composable @ReadOnlyComposable get() = LocalOpenfyTokens.current.brandGradient
    val heroGradient: Brush @Composable @ReadOnlyComposable get() = LocalOpenfyTokens.current.heroGradient
    val panelGradient: Brush @Composable @ReadOnlyComposable get() = LocalOpenfyTokens.current.panelGradient
}

private fun ColorPalette.toTokens(accentHex: String = "", accent2Hex: String = ""): OpenfyTokens {
    val base = when (this) {
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
    val primary = parseHexColor(accentHex) ?: return base
    return base.withAccent(primary, parseHexColor(accent2Hex) ?: primary)
}

private val HexColor = Regex("""^#[0-9a-fA-F]{6}$""")

/** Converte "#RRGGBB" em Color; vazio ou inválido devolve null. */
fun parseHexColor(value: String): Color? {
    val hex = value.trim()
    if (!HexColor.matches(hex)) return null
    val rgb = hex.substring(1).toLongOrNull(16) ?: return null
    return Color(0xFF000000L or rgb)
}

/** Formata a cor como "#rrggbb" (usado como valor inicial do seletor). */
fun colorToHex(color: Color): String {
    val rgb = color.toArgb() and 0xFFFFFF
    return "#" + rgb.toString(16).padStart(6, '0')
}

@Composable
fun OpenfyTheme(
    palette: ColorPalette = ColorPalette.OPENFY,
    accentHex: String = "",
    accent2Hex: String = "",
    content: @Composable () -> Unit,
) {
    val tokens = remember(palette, accentHex, accent2Hex) {
        palette.toTokens(accentHex, accent2Hex)
    }
    val scheme = remember(tokens) { tokens.colorScheme() }
    CompositionLocalProvider(LocalOpenfyTokens provides tokens) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
