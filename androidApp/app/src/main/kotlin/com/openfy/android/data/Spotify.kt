package com.openfy.android.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.net.HttpURLConnection
import java.net.URL

data class SpotifyTrack(
    val title: String,
    val artist: String,
    val durationMs: Long = 0L,
    /** Id base62 no Spotify; vazio quando a faixa veio só do embed. */
    val id: String = "",
)

data class SpotifyPlaylist(
    val name: String,
    val tracks: List<SpotifyTrack>,
    /** Total informado pelo Spotify, que pode ser maior que [tracks] por causa do teto. */
    val totalTracks: Int = tracks.size,
)

/**
 * Importa playlists públicas do Spotify pela página de embed, sem OAuth.
 * O embed traz nome e no máximo 100 faixas no __NEXT_DATA__. Para playlists
 * maiores, a própria página entrega um token anônimo que a API do player usa
 * para devolver a lista completa (só os ids); os metadados que faltam vêm de
 * /metadata/4/track. Tudo cai de volta para as 100 faixas do embed se falhar.
 */
object SpotifyClient {

    /** Mesmo teto do importador do YouTube Music. */
    const val MAX_TRACKS = 500

    private const val SPCLIENT = "https://spclient.wg.spotify.com"
    private const val API = "https://api.spotify.com/v1"
    private const val METADATA_BATCH = 8
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/131.0 Safari/537.36"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val idRegex = Regex("""(?:playlist/|playlist:)([A-Za-z0-9]+)""")

    fun playlistId(url: String): String? =
        idRegex.find(url.trim())?.groupValues?.get(1)

    suspend fun playlist(url: String, accessToken: String? = null): SpotifyPlaylist? =
        withContext(Dispatchers.IO) {
            val id = playlistId(url) ?: return@withContext null
            if (accessToken != null) {
                val web = runCatching { webPlaylist(id, accessToken) }.getOrNull()
                if (web != null && web.tracks.isNotEmpty()) return@withContext web
            }
            embedPlaylist(id)
        }

    /** Caminho sem conta: embed (100) + API anônima do player (até [MAX_TRACKS]). */
    private suspend fun embedPlaylist(id: String): SpotifyPlaylist? = withContext(Dispatchers.IO) {
        val html = get("https://open.spotify.com/embed/playlist/$id")
            ?: get("https://open.spotify.com/playlist/$id")
            ?: return@withContext null
        android.util.Log.d(
            "OpenfySpotify",
            "html id=$id len=${html.length} next=${html.contains("__NEXT_DATA__")}",
        )
        val preview = parse(id, html) ?: return@withContext null
        val token = sessionToken(html) ?: return@withContext preview
        val full = runCatching { fullPlaylist(id, token, preview) }.getOrNull()
        full ?: preview
    }

    /** Web API oficial: paginação, sem o teto do embed. */
    private suspend fun webPlaylist(id: String, token: String): SpotifyPlaylist? =
        withContext(Dispatchers.IO) {
            val metadata = getJson(
                "https://api.spotify.com/v1/playlists/$id?fields=name",
                token,
            ) ?: return@withContext null
            val name = runCatching {
                json.parseToJsonElement(metadata).jsonObject["name"]
                    ?.jsonPrimitive?.contentOrNull
            }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Playlist do Spotify"

            // Fevereiro/2026: /items substituiu /tracks no modo desenvolvimento.
            // Apps com quota estendida ainda podem usar /tracks.
            val page = fetchPages("$API/playlists/$id/items", 50, token)
                ?: fetchPages("$API/playlists/$id/tracks", 100, token)
                ?: return@withContext null
            if (page.tracks.isEmpty()) return@withContext null
            android.util.Log.d(
                "OpenfySpotify",
                "Web API $id com ${page.tracks.size}/${page.total} faixas (conta conectada)",
            )
            SpotifyPlaylist(
                name = name,
                tracks = page.tracks,
                totalTracks = maxOf(page.total, page.tracks.size),
            )
        }

    private suspend fun fetchPages(
        url: String,
        limit: Int,
        token: String,
    ): WebPage? = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<SpotifyTrack>()
        val seen = HashSet<String>()
        var offset = 0
        var total = 0
        var pages = 0
        var started = false
        while (pages++ < 200) {
            val body = getJson("$url?limit=$limit&offset=$offset", token)
                ?: return@withContext if (started) WebPage(tracks, total) else null
            started = true
            val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
                ?: break
            total = root["total"]?.jsonPrimitive?.intOrNull ?: total
            val items = root["items"]?.jsonArray ?: break
            if (items.isEmpty()) break
            items.forEach { element ->
                val item = runCatching { element.jsonObject }.getOrNull() ?: return@forEach
                // /items devolve "item"; /tracks (quota estendida) devolve "track".
                val track = runCatching {
                    item["item"]?.jsonObject ?: item["track"]?.jsonObject
                }.getOrNull() ?: return@forEach
                val type = track["type"]?.jsonPrimitive?.contentOrNull
                if (type != null && type != "track") return@forEach
                if (track["is_local"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() == true) {
                    return@forEach
                }
                val title = track["name"]?.jsonPrimitive?.contentOrNull
                    ?.replace('\u00A0', ' ')
                    ?.trim().orEmpty()
                if (title.isBlank()) return@forEach
                val trackId = track["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
                if (trackId.isNotBlank() && !seen.add(trackId)) return@forEach
                val artist = track["artists"]?.jsonArray?.mapNotNull { artistItem ->
                    artistItem.jsonObject["name"]?.jsonPrimitive?.contentOrNull
                        ?.replace('\u00A0', ' ')
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                }?.joinToString(", ").orEmpty()
                tracks += SpotifyTrack(
                    title = title,
                    artist = artist,
                    durationMs = track["duration_ms"]?.jsonPrimitive?.longOrNull ?: 0L,
                    id = trackId,
                )
            }
            if (root["next"]?.jsonPrimitive?.contentOrNull.isNullOrBlank()) break
            offset += items.size
        }
        WebPage(tracks, total)
    }

    private data class WebPage(val tracks: List<SpotifyTrack>, val total: Int)

    private fun parse(id: String, html: String): SpotifyPlaylist? {
        val root = nextData(html) ?: return null
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
            val uri = track["uri"]?.jsonPrimitive?.contentOrNull.orEmpty()
            SpotifyTrack(
                title = title,
                artist = artist,
                durationMs = track["duration"]?.jsonPrimitive?.longOrNull
                    ?: track["duration"]?.jsonPrimitive?.intOrNull?.toLong()
                    ?: 0L,
                id = if (uri.startsWith("spotify:track:")) uri.substringAfterLast(':') else "",
            )
        }.distinctBy { it.id.ifBlank { "${it.artist}|${it.title}" } }
        android.util.Log.d("OpenfySpotify", "playlist $id com ${tracks.size} faixas no embed")
        return SpotifyPlaylist(name = name, tracks = tracks)
    }

    /**
     * Lista completa da playlist pela API do player. O endpoint devolve só
     * `spotify:track:ID`; os 100 primeiros já têm metadados do embed e o resto
     * é buscado em paralelo.
     */
    private suspend fun fullPlaylist(
        id: String,
        token: String,
        preview: SpotifyPlaylist,
    ): SpotifyPlaylist? {
        val body = getJson("$SPCLIENT/playlist/v2/playlist/$id?format=json&market=from_token", token)
            ?: return null
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val items = root["contents"]?.jsonObject?.get("items")?.jsonArray ?: return null
        val trackIds = items.mapNotNull { element ->
            val uri = element.jsonObject["uri"]?.jsonPrimitive?.contentOrNull
                ?: return@mapNotNull null
            if (!uri.startsWith("spotify:track:")) return@mapNotNull null
            uri.substringAfterLast(':').takeIf { it.isNotBlank() }
        }.distinct().take(MAX_TRACKS)
        if (trackIds.isEmpty()) return null

        val total = root["length"]?.jsonPrimitive?.intOrNull ?: trackIds.size
        val known = preview.tracks.filter { it.id.isNotBlank() }.associateBy { it.id }
        val missing = trackIds.filter { it !in known }
        val fetched = fetchMetadata(missing, token)
        val tracks = trackIds.mapNotNull { known[it] ?: fetched[it] }
        if (tracks.isEmpty()) return null
        android.util.Log.d(
            "OpenfySpotify",
            "playlist $id completa com ${tracks.size} faixas (${missing.size} metadados extras)",
        )
        return SpotifyPlaylist(
            name = preview.name,
            tracks = tracks,
            totalTracks = maxOf(total, tracks.size),
        )
    }

    private suspend fun fetchMetadata(
        ids: List<String>,
        token: String,
    ): Map<String, SpotifyTrack> = coroutineScope {
        ids.chunked(METADATA_BATCH).flatMap { chunk ->
            chunk.map { id -> async(Dispatchers.IO) { metadata(id, token) } }.awaitAll()
        }.filterNotNull().associateBy { it.id }
    }

    private fun metadata(id: String, token: String): SpotifyTrack? {
        val gid = trackGid(id) ?: return null
        val body = getJson("$SPCLIENT/metadata/4/track/$gid?market=from_token", token) ?: return null
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val title = root["name"]?.jsonPrimitive?.contentOrNull
            ?.replace('\u00A0', ' ')
            ?.trim().orEmpty()
        if (title.isBlank()) return null
        val artist = root["artist"]?.jsonArray?.mapNotNull { element ->
            element.jsonObject["name"]?.jsonPrimitive?.contentOrNull
                ?.replace('\u00A0', ' ')
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        }?.joinToString(", ").orEmpty()
        return SpotifyTrack(
            title = title,
            artist = artist,
            durationMs = root["duration"]?.jsonPrimitive?.longOrNull ?: 0L,
            id = id,
        )
    }

    /** Id base62 do Spotify para o GID hexadecimal usado pela API interna. */
    internal fun trackGid(id: String): String? {
        if (id.isEmpty()) return null
        var value = java.math.BigInteger.ZERO
        val radix = java.math.BigInteger.valueOf(62)
        for (ch in id) {
            val digit = when (ch) {
                in '0'..'9' -> ch - '0'
                in 'a'..'z' -> ch - 'a' + 10
                in 'A'..'Z' -> ch - 'A' + 36
                else -> return null
            }
            value = value.multiply(radix).add(java.math.BigInteger.valueOf(digit.toLong()))
        }
        return value.toString(16).padStart(32, '0')
    }

    private fun sessionToken(html: String): String? =
        nextData(html)
            ?.get("props")?.jsonObject
            ?.get("pageProps")?.jsonObject
            ?.get("state")?.jsonObject
            ?.get("settings")?.jsonObject
            ?.get("session")?.jsonObject
            ?.get("accessToken")?.jsonPrimitive?.contentOrNull
            ?.takeIf { it.isNotBlank() }

    private fun nextData(html: String): JsonObject? {
        val start = html.indexOf("id=\"__NEXT_DATA__\"")
        if (start < 0) return null
        val jsonStart = html.indexOf('>', start)
        val jsonEnd = html.indexOf("</script>", jsonStart)
        if (jsonStart < 0 || jsonEnd < 0) return null
        val body = html.substring(jsonStart + 1, jsonEnd)
        return runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
    }

    private fun get(url: String): String? = request(url, token = null, json = false, logSuccess = true)

    private fun getJson(url: String, token: String): String? =
        request(url, token = token, json = true, logSuccess = false)

    private fun request(url: String, token: String?, json: Boolean, logSuccess: Boolean): String? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            requestMethod = "GET"
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty(
                "Accept",
                if (json) "application/json" else "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8",
            )
            setRequestProperty("Accept-Language", "pt-BR,pt;q=0.9,en;q=0.8")
            setRequestProperty("Accept-Encoding", "identity")
            setRequestProperty("Cache-Control", "no-cache")
            setRequestProperty("Referer", "https://open.spotify.com/")
            if (token != null) {
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("app-platform", "WebPlayer")
                setRequestProperty("Origin", "https://open.spotify.com")
            }
        }
        return try {
            val code = connection.responseCode
            if (code !in 200..299) {
                android.util.Log.d("OpenfySpotify", "GET $url -> $code")
                return null
            }
            if (logSuccess) android.util.Log.d("OpenfySpotify", "GET $url -> $code")
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (error: Exception) {
            android.util.Log.w("OpenfySpotify", "GET $url falhou: ${error.message}")
            null
        } finally {
            connection.disconnect()
        }
    }
}
