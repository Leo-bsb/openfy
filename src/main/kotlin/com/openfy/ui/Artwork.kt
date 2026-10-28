package com.openfy.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/**
 * Cache de capas em duas camadas: disco (em %LOCALAPPDATA%\OpenfyData\artwork\<hash>)
 * e memoria com LRU. Consulta em ordem: memoria -> disco -> rede.
 */
object ArtworkCache {

    private const val MAX_MEMORY = 220
    private const val MAX_PALETTES = 80

    data class CoverPalette(
        val primary: Color,
        val secondary: Color,
        val tertiary: Color = secondary,
    )

    private val dir: Path by lazy {
        val base = com.openfy.core.AppPaths.home.resolve("artwork")
        runCatching { Files.createDirectories(base) }
        base
    }

    // LinkedHashMap em access-order: re-inserir move para o fim; o primeiro e o LRU.
    private val memory = object : LinkedHashMap<String, ImageBitmap>(64, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, ImageBitmap>?,
        ): Boolean = size > MAX_MEMORY
    }

    private val palettes = object : LinkedHashMap<String, CoverPalette>(32, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, CoverPalette>?,
        ): Boolean = size > MAX_PALETTES
    }

    fun peek(url: String): ImageBitmap? = synchronized(memory) { memory[url] }

    fun peekPalette(url: String): CoverPalette? = synchronized(palettes) { palettes[url] }

    suspend fun palette(url: String): CoverPalette? {
        if (url.isBlank() || !url.startsWith("http")) return null
        peekPalette(url)?.let { return it }
        val bitmap = get(url) ?: return null
        val extracted = withContext(Dispatchers.Default) { extractPalette(bitmap) } ?: return null
        synchronized(palettes) { palettes[url] = extracted }
        return extracted
    }

    /** Limpa memoria e disco (Ajustes > Dados). */
    fun clearDisk() {
        runCatching {
            synchronized(memory) { memory.clear() }
            synchronized(palettes) { palettes.clear() }
            Files.list(dir).use { stream ->
                stream.forEach { path -> runCatching { Files.deleteIfExists(path) } }
            }
        }
    }

    suspend fun get(url: String): ImageBitmap? {
        if (url.isBlank()) return null
        peek(url)?.let { return it }
        return withContext(Dispatchers.IO) {
            val file = fileFor(url)
            val bytes = if (Files.isRegularFile(file)) {
                Files.readAllBytes(file)
            } else {
                runCatching {
                    val conn = URI(url).toURL().openConnection() as HttpURLConnection
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                    conn.connectTimeout = 10_000
                    conn.readTimeout = 10_000
                    val data = conn.inputStream.use { it.readBytes() }
                    runCatching { Files.write(file, data) }
                    data
                }.getOrNull() ?: return@withContext null
            }
            val bitmap = runCatching {
                org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()
            }.getOrNull() ?: return@withContext null
            synchronized(memory) { memory[url] = bitmap }
            bitmap
        }
    }

    private fun extractPalette(bitmap: ImageBitmap): CoverPalette? {
        if (bitmap.width < 8 || bitmap.height < 8) return null
        val map = bitmap.toPixelMap()
        val step = maxOf(1, minOf(map.width, map.height) / 96)
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

    private fun fileFor(url: String): Path {
        val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        return dir.resolve("$hex.img")
    }
}
