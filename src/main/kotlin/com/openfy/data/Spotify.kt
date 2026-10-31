package com.openfy.data

import com.openfy.core.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

data class SpotifyTrack(
    val title: String,
    val artist: String,
    val durationMs: Long = 0L,
)

data class SpotifyPlaylist(
    val name: String,
    val tracks: List<SpotifyTrack>,
)

/**
 * Importa playlists públicas do Spotify pela página de embed, sem OAuth.
 * O HTML público traz o __NEXT_DATA__ com nome e trackList.
 */
object SpotifyClient {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val idRegex = Regex("""(?:playlist/|playlist:)([A-Za-z0-9]+)""")
    private val client: HttpClient by lazy {
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()
    }

    /** Extrai o id de links `open.spotify.com/playlist/ID` e URIs `spotify:playlist:ID`. */
    fun playlistId(url: String): String? =
        idRegex.find(url.trim())?.groupValues?.get(1)

    suspend fun playlist(url: String): SpotifyPlaylist? = withContext(Dispatchers.IO) {
        val id = playlistId(url) ?: return@withContext null
        val html = get("https://open.spotify.com/embed/playlist/$id")
            ?: get("https://open.spotify.com/playlist/$id")
            ?: return@withContext null
        Log.write("spotify: html id=$id len=${html.length} next=${html.contains("__NEXT_DATA__")}")
        parsePlaylist(id, html)
    }

    /** Parse puro do HTML da página de embed; exposto para teste sem rede. */
    fun parsePlaylist(id: String, html: String): SpotifyPlaylist? {
        val start = html.indexOf("id=\"__NEXT_DATA__\"").takeIf { it >= 0 }
            ?: html.indexOf("__NEXT_DATA__")
        if (start < 0) return null
        val jsonStart = html.indexOf('>', start)
        val jsonEnd = html.indexOf("</script>", jsonStart)
        if (jsonStart < 0 || jsonEnd < 0) return null
        val body = html.substring(jsonStart + 1, jsonEnd)
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val entity = root["props"]
            ?.jsonObject?.get("pageProps")
            ?.jsonObject?.get("state")
            ?.jsonObject?.get("data")
            ?.jsonObject?.get("entity")
            ?.jsonObject ?: return null
        val name = entity["name"]?.jsonPrimitive?.contentOrNull
            ?.takeIf { it.isNotBlank() }
            ?: entity["title"]?.jsonPrimitive?.contentOrNull
            ?: "Playlist do Spotify"
        val list = entity["trackList"]?.jsonArray ?: return null
        val tracks = list.mapNotNull { element ->
            val track = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
            val type = track["entityType"]?.jsonPrimitive?.contentOrNull
            if (type != null && type != "track") return@mapNotNull null
            val title = track["title"]?.jsonPrimitive?.contentOrNull
                ?.replace('\u00A0', ' ')
                ?.trim().orEmpty()
            val artist = track["subtitle"]?.jsonPrimitive?.contentOrNull
                ?.replace('\u00A0', ' ')
                ?.trim().orEmpty()
            if (title.isBlank()) return@mapNotNull null
            SpotifyTrack(
                title = title,
                artist = artist,
                durationMs = track["duration"]?.jsonPrimitive?.longOrNull
                    ?: track["duration"]?.jsonPrimitive?.intOrNull?.toLong()
                    ?: 0L,
            )
        }.distinctBy { "${it.artist}|${it.title}" }
        Log.write("spotify: playlist $id com ${tracks.size} faixas")
        return SpotifyPlaylist(name = name, tracks = tracks)
    }

    private fun get(url: String): String? {
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(20))
            .header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/131.0 Safari/537.36",
            )
            .header("Accept", "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "pt-BR,pt;q=0.9,en;q=0.8")
            .header("Accept-Encoding", "identity")
            .header("Referer", "https://open.spotify.com/")
            .header("Cache-Control", "no-cache")
            .GET()
            .build()
        return try {
            val response = client.send(request, HttpResponse.BodyHandlers.ofString(Charsets.UTF_8))
            Log.write("spotify: GET $url -> ${response.statusCode()}")
            if (response.statusCode() !in 200..299) return null
            response.body()
        } catch (error: Exception) {
            Log.write("spotify: GET $url falhou: ${error.message}")
            null
        }
    }
}
