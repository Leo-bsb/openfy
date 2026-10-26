package com.openfy.data

import com.openfy.core.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

data class LyricLine(val timeMs: Long, val text: String)

/** Letras via LRCLIB (publica, sem chave). Prefere a versao sincronizada (LRC). */
object LyricsClient {

    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val tag = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")

    suspend fun fetch(track: Track): List<LyricLine> = withContext(Dispatchers.IO) {
        val artist = track.artist.substringBefore("•").substringBefore(",").trim()
        val title = cleanTitle(track.title)
        val album = track.album.substringBefore("•").trim()
        val direct = runCatching {
            val url = "https://lrclib.net/api/get?" + listOf(
                "artist_name" to artist,
                "track_name" to title,
                "album_name" to album,
                "duration" to track.durationSec.toString(),
            ).filter { it.second.isNotBlank() }
                .joinToString("&") { (k, v) -> "$k=${enc(v)}" }
            get(url)
        }.getOrNull()
        parse(direct)?.takeIf(::isUsable)?.let { return@withContext it }

        val search = runCatching {
            get("https://lrclib.net/api/search?q=${enc("$artist $title")}")
        }.getOrNull() ?: return@withContext emptyList()
        val root = runCatching { json.parseToJsonElement(search).jsonArray }.getOrNull()
            ?: return@withContext emptyList()
        for (item in root) {
            val obj = runCatching { item.jsonObject }.getOrNull() ?: continue
            val synced = obj["syncedLyrics"]?.jsonPrimitive?.contentOrNull
            val plain = obj["plainLyrics"]?.jsonPrimitive?.contentOrNull
            if (!synced.isNullOrBlank()) {
                val lines = parseLrc(synced)
                if (isUsable(lines)) return@withContext lines
            }
            if (!plain.isNullOrBlank()) {
                val lines = plain.lines()
                    .filter { it.isNotBlank() }
                    .map { LyricLine(-1, it.trim()) }
                if (isUsable(lines)) return@withContext lines
            }
        }
        emptyList()
    }

    /** Descarta registros-lixo (ex.: "[00:00.00]probe") e deixa cair no fallback. */
    private fun isUsable(lines: List<LyricLine>): Boolean =
        lines.size >= 3 || lines.sumOf { it.text.length } >= 40

    /** Remove sufixos de video oficial/lyric/audio do titulo antes de buscar. */
    private fun cleanTitle(value: String): String {
        val markers = listOf(
            " (official", " [official", " (lyric", " [lyric",
            " (audio", " [audio", " (video", " [video",
            " (visualizer", " [visualizer", " (clipe",
        )
        var out = value
        for (marker in markers) {
            val index = out.indexOf(marker, ignoreCase = true)
            if (index > 0) out = out.substring(0, index)
        }
        return out.trim()
    }

    private fun get(url: String): String {
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(12))
            .header("User-Agent", "Openfy/0.1 (https://github.com/openfy)")
            .header("Accept", "application/json")
            .GET()
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw IllegalStateException("LRCLIB ${response.statusCode()}")
        }
        return response.body()
    }

    private fun parse(body: String?): List<LyricLine>? {
        if (body.isNullOrBlank()) return null
        val obj = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val synced = obj["syncedLyrics"]?.jsonPrimitive?.contentOrNull
        if (!synced.isNullOrBlank()) {
            val lines = parseLrc(synced)
            if (lines.isNotEmpty()) return lines
        }
        val plain = obj["plainLyrics"]?.jsonPrimitive?.contentOrNull
        if (!plain.isNullOrBlank()) {
            return plain.lines().filter { it.isNotBlank() }.map { LyricLine(-1, it.trim()) }
        }
        return null
    }

    private fun parseLrc(text: String): List<LyricLine> {
        val out = mutableListOf<LyricLine>()
        for (line in text.lines()) {
            val stamps = tag.findAll(line).toList()
            if (stamps.isEmpty()) continue
            val content = line.replace(tag, "").trim()
            if (content.isEmpty()) continue
            for (stamp in stamps) {
                val minutes = stamp.groupValues[1].toLongOrNull() ?: 0
                val seconds = stamp.groupValues[2].toLongOrNull() ?: 0
                val fraction = stamp.groupValues[3]
                val millis = when (fraction.length) {
                    0 -> 0L
                    1 -> fraction.toLongOrNull()?.times(100) ?: 0
                    2 -> fraction.toLongOrNull()?.times(10) ?: 0
                    else -> fraction.take(3).toLongOrNull() ?: 0
                }
                out += LyricLine(minutes * 60_000 + seconds * 1_000 + millis, content)
            }
        }
        return out.sortedBy { it.timeMs }
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
}
