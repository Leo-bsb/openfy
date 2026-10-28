package com.openfy.android.data

import com.openfy.core.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class LyricLine(val timeMs: Long, val text: String)

/** Letras via LRCLIB (pública, sem chave). Prefere a versão sincronizada. */
object LyricsClient {

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
                .joinToString("&") { (key, value) -> "$key=${enc(value)}" }
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

    private fun isUsable(lines: List<LyricLine>): Boolean =
        lines.size >= 3 || lines.sumOf { it.text.length } >= 40

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
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 12_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "Openfy/0.3 (https://github.com/Leo-bsb/openfy)")
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("LRCLIB $code")
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
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
