package com.openfy.data

import com.openfy.core.Track
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Duration

/**
 * Baixa a faixa para %LOCALAPPDATA%\OpenfyData\downloads usando a URL que o extrator
 * devolve. O mpv nunca ve essa URL; quem consome e o StreamServer local, que
 * prefere o arquivo baixado quando existe (modo offline automatico).
 */
class Downloader(
    private val yt: YouTubeEngine,
    private val dir: Path,
) {

    init {
        runCatching { Files.createDirectories(dir) }
        // Parciais de sessoes anteriores nunca sao reaproveitados.
        runCatching {
            Files.list(dir).use { stream ->
                stream.filter { it.fileName.toString().endsWith(".part") }
                    .forEach { runCatching { Files.deleteIfExists(it) } }
            }
        }
    }

    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    /** Baixa e devolve o caminho final; null em qualquer falha. */
    fun download(track: Track): Path? {
        val media = yt.media(track.id) as? Media.Remote ?: return null
        if (media.url.isBlank()) return null
        val response = runCatching {
            val builder = HttpRequest.newBuilder(URI.create(media.url))
                .timeout(Duration.ofSeconds(120))
                .header("User-Agent", UA)
                .header("Accept-Encoding", "identity")
                // O URL extraido e servido por range; sem isso o upstream
                // deixava o corpo vazio e o download travava em 0 bytes.
                .header("Range", "bytes=0-")
                .GET()
            media.headers.forEach { (key, value) ->
                if (!key.equals("host", true) &&
                    !key.equals("content-length", true) &&
                    !key.equals("range", true)
                ) {
                    runCatching { builder.header(key, value) }
                }
            }
            http.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray())
        }.getOrNull() ?: return null

        if (response.statusCode() !in 200..299) {
            com.openfy.core.Log.write("download: ${track.id} status ${response.statusCode()}")
            return null
        }
        val bytes = response.body()
        if (bytes.isEmpty()) {
            com.openfy.core.Log.write("download: ${track.id} corpo vazio")
            return null
        }

        val contentType = response.headers().firstValue("Content-Type").orElse("")
        val ext = if (contentType.contains("mpeg") || contentType.contains("mp4")) ".m4a" else ".webm"
        val final = dir.resolve("${track.id}$ext")
        val tmp = dir.resolve("${track.id}.part")
        return runCatching {
            Files.write(tmp, bytes)
            Files.move(tmp, final, StandardCopyOption.REPLACE_EXISTING)
            final
        }.onFailure {
            com.openfy.core.Log.write("download: falha ao gravar ${track.id}: ${it.message}")
            runCatching { Files.deleteIfExists(tmp) }
        }.getOrNull()
    }

    fun delete(id: String) {
        runCatching { Files.deleteIfExists(dir.resolve("$id.m4a")) }
        runCatching { Files.deleteIfExists(dir.resolve("$id.webm")) }
    }

    private companion object {
        const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/131.0.0.0 Safari/537.36"
    }
}
