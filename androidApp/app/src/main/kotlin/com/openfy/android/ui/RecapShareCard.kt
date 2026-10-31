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
                mix(accent, 0x0B0B12, 0.60f),
                mix(accent2, 0x09090D, 0.80f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), background)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val white = Color.rgb(244, 244, 247)
        val muted = Color.rgb(185, 185, 196)

        paint.color = accent2
        paint.textSize = 40f
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        canvas.drawText("SEU RECAP", 96f, 168f, paint)

        paint.color = accent
        paint.textSize = 72f
        canvas.drawText(monthLabel(month), 96f, 268f, paint)

        paint.color = white
        paint.textSize = 40f
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        canvas.drawText(fit(paint, userName.ifBlank { "Sua conta Openfy" }, 880f), 96f, 344f, paint)

        val minutes = (listening?.playedMs ?: 0L) / 60_000L
        paint.color = white
        paint.textSize = 200f
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        canvas.drawText(fit(paint, "$minutes min", 880f), 92f, 600f, paint)

        paint.color = muted
        paint.textSize = 36f
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        canvas.drawText("execuções: ${listening?.plays ?: 0}", 96f, 676f, paint)
        canvas.drawText("faixas: ${listening?.uniqueTracks ?: 0}", 96f, 730f, paint)

        paint.color = accent2
        paint.textSize = 34f
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        canvas.drawText("TOP FAIXAS", 96f, 860f, paint)

        var y = 936f
        topTracks.take(3).forEachIndexed { index, title ->
            paint.color = accent
            paint.textSize = 40f
            canvas.drawText("${index + 1}", 96f, y, paint)
            paint.color = white
            paint.textSize = 40f
            canvas.drawText(fit(paint, title, 780f), 150f, y, paint)
            y += 96f
        }

        y += 90f
        paint.color = accent2
        paint.textSize = 34f
        canvas.drawText("TOP ARTISTAS", 96f, y, paint)
        y += 82f
        topArtists.take(3).forEachIndexed { index, artist ->
            paint.color = accent
            paint.textSize = 40f
            canvas.drawText("${index + 1}", 96f, y, paint)
            paint.color = white
            canvas.drawText(fit(paint, artist, 780f), 150f, y, paint)
            y += 96f
        }

        paint.color = white
        paint.textSize = 46f
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        canvas.drawText("Openfy", 96f, HEIGHT - 96f, paint)

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
