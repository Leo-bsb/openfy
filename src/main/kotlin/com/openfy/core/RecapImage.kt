package com.openfy.core

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.FontMetrics
import java.awt.GradientPaint
import java.awt.Graphics2D
import java.awt.RadialGradientPaint
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
        // Base escura com dois halos: um no tom principal (topo) e outro no
        // segundo tom (canto inferior direito). O gradiente cobre a transicao
        // para as duas cores nao virarem blocos separados.
        g.paint = GradientPaint(
            0f, 0f, mix(accent, 0x07090C, 0.72),
            WIDTH.toFloat(), HEIGHT.toFloat(), mix(accent2, 0x05060A, 0.86),
        )
        g.fillRect(0, 0, WIDTH, HEIGHT)

        g.paint = RadialGradientPaint(
            java.awt.geom.Point2D.Float(WIDTH * 0.18f, 120f),
            WIDTH * 0.85f,
            floatArrayOf(0f, 1f),
            arrayOf(
                Color(accent).let { Color(it.red, it.green, it.blue, 110) },
                Color(0, 0, 0, 0),
            ),
        )
        g.fillRect(0, 0, WIDTH, HEIGHT)

        g.paint = RadialGradientPaint(
            java.awt.geom.Point2D.Float(WIDTH * 0.92f, HEIGHT * 0.78f),
            WIDTH * 0.75f,
            floatArrayOf(0f, 1f),
            arrayOf(
                Color(accent2).let { Color(it.red, it.green, it.blue, 90) },
                Color(0, 0, 0, 0),
            ),
        )
        g.fillRect(0, 0, WIDTH, HEIGHT)
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

        val labelFont = font(Font.BOLD, 36f)
        val monthFont = font(Font.BOLD, 78f)
        val nameFont = font(Font.PLAIN, 38f)
        val bigFont = font(Font.BOLD, 196f)
        val captionFont = font(Font.PLAIN, 36f)
        val sectionFont = font(Font.BOLD, 32f)
        val itemFont = font(Font.BOLD, 38f)
        val subFont = font(Font.PLAIN, 31f)
        val footerFont = font(Font.BOLD, 44f)
        val statValueFont = font(Font.BOLD, 54f)
        val statLabelFont = font(Font.PLAIN, 24f)

        g.color = accent2Color
        g.font = labelFont
        g.drawString("SEU RECAP", 96, 160)

        g.paint = GradientPaint(
            96f, 220f, white,
            720f, 220f, blend(accent2Color, Color.WHITE, 0.25f),
        )
        g.font = monthFont
        g.drawString(monthLabel(month), 96, 266)
        g.paint = white

        g.color = muted
        g.font = nameFont
        g.drawString(fit(g, userName.ifBlank { "Sua conta Openfy" }, nameFont, 880), 96, 342)

        g.color = white
        g.font = bigFont
        val minutesText = "${summary.minutes}"
        g.drawString(fit(g, minutesText, bigFont, 700), 90, 600)
        val minutesWidth = g.fontMetrics.stringWidth(fit(g, minutesText, bigFont, 700))
        g.color = blend(accent2Color, Color.WHITE, 0.2f)
        g.font = font(Font.BOLD, 64f)
        g.drawString("min", 104 + minutesWidth, 600)

        g.color = muted
        g.font = captionFont
        g.drawString("ouvindo em ${monthLabel(month)}", 96, 674)

        // Cartoes de estatistica (2x2), com fundo translucido para leitura.
        val stats = listOf(
            "${summary.plays}" to "execuções",
            "${summary.uniqueTracks}" to "faixas",
            "${summary.uniqueArtists}" to "artistas",
            "${snapshot.liked}" to "curtidas",
        )
        val cardY = 742
        val cardW = (WIDTH - 192 - 36) / 2
        stats.forEachIndexed { index, (value, label) ->
            val col = index % 2
            val row = index / 2
            val x = 96 + col * (cardW + 36)
            val yCard = cardY + row * 150
            roundedRect(g, x, yCard, cardW, 126, 28, Color(255, 255, 255, 16))
            g.color = white
            g.font = statValueFont
            g.drawString(value, x + 28, yCard + 68)
            g.color = muted
            g.font = statLabelFont
            g.drawString(label, x + 28, yCard + 104)
        }

        var y = 1078

        g.color = accent2Color
        g.font = sectionFont
        g.drawString("TOP 3 FAIXAS", 96, y)
        y += 66
        snapshot.topTracks.take(3).forEachIndexed { index, entry ->
            roundedRect(g, 84, y - 42, WIDTH - 168, 92, 22, Color(255, 255, 255, 12))
            g.color = accentColor
            g.font = itemFont
            g.drawString("${index + 1}", 112, y + 8)
            g.color = white
            g.font = itemFont
            g.drawString(fit(g, entry.track.title, itemFont, 700), 166, y + 8)
            g.color = muted
            g.font = subFont
            g.drawString(fit(g, entry.track.artist, subFont, 640), 166, y + 46)
            g.color = blend(accent2Color, Color.WHITE, 0.1f)
            g.font = subFont
            val plays = "${entry.plays}x"
            g.drawString(plays, WIDTH - 120 - g.fontMetrics.stringWidth(plays), y + 8)
            y += 108
        }
        if (snapshot.topTracks.isEmpty()) {
            g.color = muted
            g.font = subFont
            g.drawString("Nenhuma faixa neste mês", 120, y + 20)
            y += 100
        }

        y += 42

        g.color = accent2Color
        g.font = sectionFont
        g.drawString("TOP 3 ARTISTAS", 96, y)
        y += 56
        val maxPlays = snapshot.topArtists.firstOrNull()?.plays?.coerceAtLeast(1) ?: 1
        snapshot.topArtists.take(3).forEachIndexed { index, artist ->
            g.color = white
            g.font = itemFont
            g.drawString(fit(g, artist.name, itemFont, 560), 96, y)
            g.color = muted
            g.font = subFont
            val plays = "${artist.plays} execuções"
            g.drawString(plays, WIDTH - 96 - g.fontMetrics.stringWidth(plays), y)

            val barY = y + 16
            roundedRect(g, 96, barY, WIDTH - 192, 10, 5, Color(255, 255, 255, 22))
            val fill = ((WIDTH - 192) * (artist.plays.toFloat() / maxPlays)).toInt().coerceAtLeast(12)
            roundedRect(g, 96, barY, fill, 10, 5, mix(accent, accent2, index * 0.45))
            y += 92
        }
        if (snapshot.topArtists.isEmpty()) {
            g.color = muted
            g.font = subFont
            g.drawString("Nenhum artista neste mês", 96, y + 20)
        }

        g.color = white
        g.font = footerFont
        g.drawString("Openfy", 96, HEIGHT - 88)
        g.color = muted
        g.font = subFont
        val footer = "Seu ano em música, mês a mês."
        g.drawString(footer, WIDTH - 96 - g.fontMetrics.stringWidth(footer), HEIGHT - 88)
    }

    private fun roundedRect(g: Graphics2D, x: Int, y: Int, w: Int, h: Int, r: Int, color: Color) {
        g.color = color
        g.fillRoundRect(x, y, w, h, r, r)
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
