package com.openfy.android.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.FileProvider
import com.openfy.android.data.MonthlyListeningRow
import java.io.File
import java.io.FileOutputStream
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * PNG 1080×1920 do recap, no formato de story. Usa só android.graphics para
 * não adicionar dependência; o arquivo vai para `files/downloads`, que já está
 * exposto pelo FileProvider do app.
 */
object RecapShareCard {

    const val WIDTH = 1080
    const val HEIGHT = 1920

    fun share(
        context: Context,
        month: String,
        userName: String,
        listening: MonthlyListeningRow?,
        topTracks: List<String>,
        topArtists: List<String>,
        accent: Int,
        accent2: Int,
    ): File {
        val bitmap = render(month, userName, listening, topTracks, topArtists, accent, accent2)
        val dir = File(context.filesDir, "downloads").apply { mkdirs() }
        val file = File(dir, "openfy-recap-$month.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bitmap.recycle()
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(send, "Compartilhar recap")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        return file
    }

    fun render(
        month: String,
        userName: String,
        listening: MonthlyListeningRow?,
        topTracks: List<String>,
        topArtists: List<String>,
        accent: Int,
        accent2: Int,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val background = Paint().apply {
            shader = LinearGradient(
                0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(),
                mix(accent, 0x07090C, 0.72f),
                mix(accent2, 0x05060A, 0.86f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), background)
        // Dois halos suaves: o gradiente cobre a transicao e as duas cores nao
        // viram blocos separados.
        val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.RadialGradient(
                WIDTH * 0.18f, 120f, WIDTH * 0.85f,
                Color.argb(110, Color.red(accent), Color.green(accent), Color.blue(accent)),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), halo)
        halo.shader = android.graphics.RadialGradient(
            WIDTH * 0.92f, HEIGHT * 0.78f, WIDTH * 0.75f,
            Color.argb(90, Color.red(accent2), Color.green(accent2), Color.blue(accent2)),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), halo)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val white = Color.rgb(244, 244, 247)
        val muted = Color.rgb(185, 185, 196)

        paint.color = accent2
        paint.textSize = 36f
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        canvas.drawText("SEU RECAP", 96f, 160f, paint)

        paint.shader = LinearGradient(
            96f, 220f, 760f, 220f,
            white, mix(accent2, white, 0.25f), Shader.TileMode.CLAMP,
        )
        paint.textSize = 78f
        canvas.drawText(monthLabel(month), 96f, 266f, paint)
        paint.shader = null

        paint.color = muted
        paint.textSize = 38f
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        canvas.drawText(fit(paint, userName.ifBlank { "Sua conta Openfy" }, 880f), 96f, 342f, paint)

        val minutes = (listening?.playedMs ?: 0L) / 60_000L
        paint.color = white
        paint.textSize = 196f
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        val minutesText = "$minutes"
        canvas.drawText(fit(paint, minutesText, 700f), 90f, 600f, paint)
        val minutesWidth = paint.measureText(minutesText)
        paint.color = mix(accent2, white, 0.2f)
        paint.textSize = 64f
        canvas.drawText("min", 104f + minutesWidth, 600f, paint)

        paint.color = muted
        paint.textSize = 36f
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        canvas.drawText("ouvindo em ${monthLabel(month)}", 96f, 674f, paint)

        // Cartoes de estatistica (2x2).
        val stats = listOf(
            "${listening?.plays ?: 0}" to "execuções",
            "${listening?.uniqueTracks ?: 0}" to "faixas",
            "${topArtists.size}" to "artistas",
            "${listening?.completedPlays ?: 0}" to "completas",
        )
        val cardY = 742f
        val cardW = (WIDTH - 192f - 36f) / 2f
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(16, 255, 255, 255) }
        stats.forEachIndexed { index, (value, label) ->
            val col = index % 2
            val row = index / 2
            val x = 96f + col * (cardW + 36f)
            val y = cardY + row * 150f
            val rect = android.graphics.RectF(x, y, x + cardW, y + 126f)
            canvas.drawRoundRect(rect, 28f, 28f, cardPaint)
            paint.color = white
            paint.textSize = 54f
            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            canvas.drawText(value, x + 28f, y + 68f, paint)
            paint.color = muted
            paint.textSize = 24f
            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            canvas.drawText(label, x + 28f, y + 104f, paint)
        }

        var y = 1078f
        paint.color = accent2
        paint.textSize = 32f
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        canvas.drawText("TOP 3 FAIXAS", 96f, y, paint)
        y += 66f
        val rowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(12, 255, 255, 255) }
        topTracks.take(3).forEachIndexed { index, title ->
            val rect = android.graphics.RectF(84f, y - 42f, WIDTH - 84f, y + 50f)
            canvas.drawRoundRect(rect, 22f, 22f, rowPaint)
            paint.color = accent
            paint.textSize = 38f
            canvas.drawText("${index + 1}", 112f, y + 8f, paint)
            paint.color = white
            canvas.drawText(fit(paint, title, 700f), 166f, y + 8f, paint)
            y += 108f
        }
        if (topTracks.isEmpty()) {
            paint.color = muted
            paint.textSize = 31f
            canvas.drawText("Nenhuma faixa neste mês", 120f, y + 20f, paint)
            y += 100f
        }

        y += 42f
        paint.color = accent2
        paint.textSize = 32f
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        canvas.drawText("TOP 3 ARTISTAS", 96f, y, paint)
        y += 56f
        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(22, 255, 255, 255) }
        topArtists.take(3).forEachIndexed { index, artist ->
            paint.color = white
            paint.textSize = 38f
            canvas.drawText(fit(paint, artist, 760f), 96f, y, paint)
            val barY = y + 16f
            val barRect = android.graphics.RectF(96f, barY, WIDTH - 96f, barY + 10f)
            canvas.drawRoundRect(barRect, 5f, 5f, barPaint)
            val fraction = (1f - index * 0.22f).coerceAtLeast(0.2f)
            paint.color = mix(accent, accent2, index * 0.45f)
            val fillRect = android.graphics.RectF(
                96f,
                barY,
                96f + (WIDTH - 192f) * fraction,
                barY + 10f,
            )
            canvas.drawRoundRect(fillRect, 5f, 5f, paint)
            y += 92f
        }
        if (topArtists.isEmpty()) {
            paint.color = muted
            paint.textSize = 31f
            canvas.drawText("Nenhum artista neste mês", 96f, y + 20f, paint)
        }

        paint.color = white
        paint.textSize = 44f
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        canvas.drawText("Openfy", 96f, HEIGHT - 88f, paint)
        paint.color = muted
        paint.textSize = 31f
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        val footer = "Seu ano em música, mês a mês."
        canvas.drawText(
            footer,
            WIDTH - 96f - paint.measureText(footer),
            HEIGHT - 88f,
            paint,
        )

        return bitmap
    }

    fun monthLabel(month: String): String {
        val parsed = runCatching { YearMonth.parse(month) }.getOrNull() ?: return "Seu mês"
        return parsed.atDay(1)
            .format(DateTimeFormatter.ofPattern("MMMM 'de' yyyy", Locale("pt", "BR")))
            .replaceFirstChar { it.uppercase() }
    }

    private fun fit(paint: Paint, text: String, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 0 && paint.measureText(text.substring(0, end) + "…") > maxWidth) {
            end--
        }
        return text.substring(0, end).trimEnd() + "…"
    }

    private fun mix(value: Int, target: Int, ratio: Float): Int {
        val r = ratio.coerceIn(0f, 1f)
        val red = (Color.red(value) * (1 - r) + Color.red(target) * r).toInt()
        val green = (Color.green(value) * (1 - r) + Color.green(target) * r).toInt()
        val blue = (Color.blue(value) * (1 - r) + Color.blue(target) * r).toInt()
        return Color.rgb(red, green, blue)
    }
}
