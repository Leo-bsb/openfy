package com.openfy.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
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

    fun peek(url: String): ImageBitmap? = synchronized(memory) { memory[url] }

    /** Limpa memoria e disco (Ajustes > Dados). */
    fun clearDisk() {
        runCatching {
            synchronized(memory) { memory.clear() }
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

    private fun fileFor(url: String): Path {
        val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        return dir.resolve("$hex.img")
    }
}
