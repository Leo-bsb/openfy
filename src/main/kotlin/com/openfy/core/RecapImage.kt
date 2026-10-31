package com.openfy.core

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.FontMetrics
import java.awt.GradientPaint
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.imageio.ImageIO

/**
 * Gera o PNG 1080×1920 do recap usando só Java2D. O layout é fixo para caber
 * em stories: capa textual, minutos em destaque, top 3 faixas e artistas.
 */
object RecapImage {

    const val WIDTH = 1080
    const val HEIGHT = 1920

    fun monthLabel(month: YearMonth): String {
        val formatted = month.atDay(1).format(
            DateTimeFormatter.ofPattern("MMMM 'de' yyyy", Locale("pt", "BR")),
        )
        return formatted.replaceFirstChar { it.uppercase() }
    }

    /** Renderiza e devolve o arquivo em `%LOCALAPPDATA%\OpenfyData\recap`. */
    fun render(
        snapshot: RecapSnapshot,
        month: YearMonth,
        userName: String,
        accent: Int,
        accent2: Int,
    ): File {
        val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            paintBackground(g, accent, accent2)
            paintContent(g, snapshot, month, userName, accent, accent2)
        } finally {
            g.dispose()
        }
        val dir = AppPaths.home.resolve("recap")
        Files.createDirectories(dir)
        val file = dir.resolve("openfy-recap-$month.png").toFile()
        ImageIO.write(image, "png", file)
        Log.write("recap: imagem gerada em ${file.absolutePath}")
        return file
    }

    private fun paintBackground(g: Graphics2D, accent: Int, accent2: Int) {
        val start = mix(accent, 0x0B0B12, 0.58)
        val end = mix(accent2, 0x09090D, 0.78)
        g.paint = GradientPaint(0f, 0f, start, WIDTH.toFloat(), HEIGHT.toFloat(), end)
        g.fillRect(0, 0, WIDTH, HEIGHT)

        // Brilho suave no topo, no tom de destaque.
        val glow = blend(Color(accent), Color.BLACK, 0.35f).let { Color(it.red, it.green, it.blue, 90) }
        g.paint = GradientPaint(0f, 0f, glow, 0f, 700f, Color(0, 0, 0, 0))
        g.fillRect(0, 0, WIDTH, 700)
    }

    private fun paintContent(
        g: Graphics2D,
        snapshot: RecapSnapshot,
        month: YearMonth,
        userName: String,
        accent: Int,
        accent2: Int,
    ) {
        val accentColor = Color(accent)
        val accent2Color = Color(accent2)
        val white = Color(0xFFF4F4F7.toInt())
        val muted = Color(0xFFB9B9C4.toInt())
        val summary = snapshot.summary

        val labelFont = font(Font.BOLD, 40f)
        val monthFont = font(Font.BOLD, 74f)
        val nameFont = font(Font.PLAIN, 40f)
        val bigFont = font(Font.BOLD, 210f)
        val captionFont = font(Font.PLAIN, 38f)
        val sectionFont = font(Font.BOLD, 34f)
        val itemFont = font(Font.BOLD, 40f)
        val subFont = font(Font.PLAIN, 34f)
        val footerFont = font(Font.BOLD, 46f)

        g.color = accent2Color
        g.font = labelFont
        g.drawString("SEU RECAP", 96, 168)

        g.color = accentColor
        g.font = monthFont
        g.drawString(monthLabel(month), 96, 270)

        g.color = white
        g.font = nameFont
        g.drawString(fit(g, userName.ifBlank { "Sua conta Openfy" }, nameFont, 880), 96, 345)

        g.color = white
        g.font = bigFont
        val minutesText = "${summary.minutes} min"
        g.drawString(fit(g, minutesText, bigFont, 880), 90, 600)

        g.color = muted
        g.font = captionFont
        g.drawString("ouvindo em ${monthLabel(month)}", 96, 668)
        val stats = "${summary.plays} execuções · ${summary.uniqueTracks} faixas · " +
            "${summary.uniqueArtists} artistas"
        g.drawString(fit(g, stats, captionFont, 880), 96, 726)

        g.color = blend(accentColor, Color.WHITE, 0.15f)
        g.stroke = BasicStroke(3f)
        g.drawLine(96, 790, WIDTH - 96, 790)

        // Top 3 faixas.
        g.color = accent2Color
        g.font = sectionFont
        g.drawString("TOP 3 FAIXAS", 96, 872)
        var y = 946
        snapshot.topTracks.take(3).forEachIndexed { index, entry ->
            g.color = accentColor
            g.font = itemFont
            g.drawString("${index + 1}", 96, y)
            g.color = white
            g.font = itemFont
            g.drawString(fit(g, entry.track.title, itemFont, 780), 150, y)
            g.color = muted
            g.font = subFont
            g.drawString(fit(g, entry.track.artist, subFont, 780), 150, y + 42)
            y += 140
        }
        if (snapshot.topTracks.isEmpty()) {
            g.color = muted
            g.font = subFont
            g.drawString("Nenhuma faixa neste mês", 150, y)
            y += 120
        }

        // Top 3 artistas.
        g.color = accent2Color
        g.font = sectionFont
        g.drawString("TOP 3 ARTISTAS", 96, y + 70)
        y += 150
        snapshot.topArtists.take(3).forEachIndexed { index, artist ->
            g.color = accentColor
            g.font = itemFont
            g.drawString("${index + 1}", 96, y)
            g.color = white
            g.font = itemFont
            g.drawString(fit(g, artist.name, itemFont, 700), 150, y)
            g.color = muted
            g.font = subFont
            g.drawString("${artist.plays} execuções", 150, y + 42)
            y += 140
        }
        if (snapshot.topArtists.isEmpty()) {
            g.color = muted
            g.font = subFont
            g.drawString("Nenhum artista neste mês", 150, y)
        }

        g.color = white
        g.font = footerFont
        g.drawString("Openfy", 96, HEIGHT - 96)
        g.color = muted
        g.font = subFont
        val footer = "Seu ano em música, mês a mês."
        g.drawString(footer, WIDTH - 96 - g.fontMetrics.stringWidth(footer), HEIGHT - 96)
    }

    private fun font(style: Int, size: Float): Font =
        runCatching { Font("SansSerif", style, size.toInt()) }
            .getOrDefault(Font(Font.SANS_SERIF, style, size.toInt()))

    /** Corta com reticências o que não couber na largura. */
    private fun fit(g: Graphics2D, text: String, font: Font, maxWidth: Int): String {
        g.font = font
        val metrics: FontMetrics = g.fontMetrics
        if (metrics.stringWidth(text) <= maxWidth) return text
        val ellipsis = "…"
        var end = text.length
        while (end > 0 && metrics.stringWidth(text.substring(0, end) + ellipsis) > maxWidth) {
            end--
        }
        return text.substring(0, end).trimEnd() + ellipsis
    }

    private fun mix(value: Int, target: Int, amount: Double): Color {
        val source = Color(value)
        val dest = Color(target)
        val ratio = amount.coerceIn(0.0, 1.0)
        return Color(
            (source.red * (1 - ratio) + dest.red * ratio).toInt().coerceIn(0, 255),
            (source.green * (1 - ratio) + dest.green * ratio).toInt().coerceIn(0, 255),
            (source.blue * (1 - ratio) + dest.blue * ratio).toInt().coerceIn(0, 255),
        )
    }

    private fun blend(first: Color, second: Color, ratio: Float): Color {
        val r = ratio.coerceIn(0f, 1f)
        return Color(
            (first.red * (1 - r) + second.red * r).toInt().coerceIn(0, 255),
            (first.green * (1 - r) + second.green * r).toInt().coerceIn(0, 255),
            (first.blue * (1 - r) + second.blue * r).toInt().coerceIn(0, 255),
        )
    }
}
