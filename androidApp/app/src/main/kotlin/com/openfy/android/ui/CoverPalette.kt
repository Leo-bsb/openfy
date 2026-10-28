package com.openfy.android.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.bitmapConfig
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Cores dominantes de uma capa, com cache e fallback silencioso. */
object CoverPalettes {

    data class CoverPalette(
        val primary: Color,
        val secondary: Color,
        val tertiary: Color = secondary,
    )

    private const val MAX = 64
    private val cache = object : LinkedHashMap<String, CoverPalette>(16, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, CoverPalette>?,
        ): Boolean = size > MAX
    }

    fun peek(url: String): CoverPalette? = synchronized(cache) { cache[url] }

    suspend fun load(context: Context, url: String): CoverPalette? {
        if (url.isBlank()) return null
        peek(url)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .size(96, 96)
                    .bitmapConfig(android.graphics.Bitmap.Config.ARGB_8888)
                    .build()
                val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult
                    ?: return@runCatching null
                val bitmap = result.image.toBitmap()
                extract(bitmap.asImageBitmap().toPixelMap())?.also {
                    synchronized(cache) { cache[url] = it }
                }
            }.getOrNull()
        }
    }

    private fun extract(map: androidx.compose.ui.graphics.PixelMap): CoverPalette? {
        if (map.width < 8 || map.height < 8) return null
        val step = maxOf(1, minOf(map.width, map.height) / 48)
        val buckets = HashMap<Int, FloatArray>()
        var y = 0
        while (y < map.height) {
            var x = 0
            while (x < map.width) {
                val color = map[x, y]
                val luma = 0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue
                if (color.alpha >= 0.6f && luma in 0.05f..0.95f) {
                    val key = ((color.red * 15).toInt() shl 8) or
                        ((color.green * 15).toInt() shl 4) or
                        (color.blue * 15).toInt()
                    val saturation = maxOf(color.red, color.green, color.blue) -
                        minOf(color.red, color.green, color.blue)
                    val weight = 0.20f + saturation * 2.0f + (1f - luma) * 0.15f
                    val bucket = buckets.getOrPut(key) { FloatArray(4) }
                    bucket[0] += color.red * weight
                    bucket[1] += color.green * weight
                    bucket[2] += color.blue * weight
                    bucket[3] += weight
                }
                x += step
            }
            y += step
        }
        val ranked = buckets.values
            .filter { it[3] > 0f }
            .sortedByDescending { it[3] }
            .map { Color(it[0] / it[3], it[1] / it[3], it[2] / it[3]) }
        if (ranked.isEmpty()) return null
        val chosen = mutableListOf<Color>()
        for (color in ranked) {
            if (chosen.size >= 3) break
            if (chosen.all { distanceSq(it, color) > 0.035f }) chosen += color
        }
        if (chosen.isEmpty()) return null
        return CoverPalette(
            primary = chosen[0],
            secondary = chosen.getOrElse(1) { chosen[0] },
            tertiary = chosen.getOrElse(2) { chosen.last() },
        )
    }

    private fun distanceSq(a: Color, b: Color): Float {
        val dr = a.red - b.red
        val dg = a.green - b.green
        val db = a.blue - b.blue
        return dr * dr + dg * dg + db * db
    }
}

@Composable
fun rememberCoverPalette(url: String, enabled: Boolean): CoverPalettes.CoverPalette? {
    val context = LocalContext.current.applicationContext
    val palette by produceState<CoverPalettes.CoverPalette?>(
        initialValue = if (enabled) CoverPalettes.peek(url) else null,
        url,
        enabled,
    ) {
        value = if (enabled) CoverPalettes.load(context, url) else null
    }
    return palette
}
