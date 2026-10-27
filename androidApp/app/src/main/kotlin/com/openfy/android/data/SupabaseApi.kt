package com.openfy.android.data

import com.openfy.android.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import java.time.Instant

class SupabaseException(message: String, val status: Int = 0) : Exception(message)

class SupabaseApi {

    private val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val anonKey = BuildConfig.SUPABASE_ANON_KEY

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 30_000
        }
    }

    val configured: Boolean
        get() = baseUrl.isNotBlank() && anonKey.isNotBlank()

    private fun requireConfigured() {
        if (!configured) {
            throw SupabaseException("Supabase não configurado. Verifique o local.properties.")
        }
    }

    private suspend fun decodeError(response: HttpResponse): Nothing {
        val text = runCatching { response.bodyAsText() }.getOrDefault("")
        val parsed = runCatching { json.decodeFromString<ApiError>(text) }.getOrNull()
        val message = parsed?.message
            ?: parsed?.errorDescription
            ?: parsed?.error
            ?: text.takeIf { it.isNotBlank() }
            ?: "Erro ${response.status.value}"
        throw SupabaseException(message, response.status.value)
    }

    private suspend inline fun <reified T> decoded(response: HttpResponse): T {
        if (!response.status.isSuccess()) decodeError(response)
        val text = response.bodyAsText()
        if (text.isBlank()) throw SupabaseException("Resposta vazia do servidor.")
        return json.decodeFromString(text)
    }

    suspend fun signIn(email: String, password: String): AuthSession {
        requireConfigured()
        val response = client.post("$baseUrl/auth/v1/token") {
            parameter("grant_type", "password")
            header("apikey", anonKey)
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(email.trim(), password))
        }
        return decoded(response)
    }

    suspend fun signUp(email: String, password: String, displayName: String): AuthSession {
        requireConfigured()
        val response = client.post("$baseUrl/auth/v1/signup") {
            header("apikey", anonKey)
            contentType(ContentType.Application.Json)
            setBody(
                SignUpRequest(
                    email = email.trim(),
                    password = password,
                    data = mapOf("display_name" to displayName.trim()),
                ),
            )
        }
        return decoded(response)
    }

    suspend fun refresh(refreshToken: String): AuthSession {
        requireConfigured()
        val response = client.post("$baseUrl/auth/v1/token") {
            parameter("grant_type", "refresh_token")
            header("apikey", anonKey)
            contentType(ContentType.Application.Json)
            setBody(RefreshRequest(refreshToken))
        }
        return decoded(response)
    }

    suspend fun logout(accessToken: String) {
        if (!configured) return
        runCatching {
            client.post("$baseUrl/auth/v1/logout") {
                header("apikey", anonKey)
                header(HttpHeaders.Authorization, "Bearer $accessToken")
            }
        }
    }

    suspend fun profile(accessToken: String, userId: String): Profile? {
        val response = client.get("$baseUrl/rest/v1/profiles") {
            header("apikey", anonKey)
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            parameter("select", "id,handle,display_name,avatar_url")
            parameter("id", "eq.$userId")
            parameter("limit", "1")
        }
        val rows: List<Profile> = decoded(response)
        return rows.firstOrNull()
    }

    suspend fun playlists(accessToken: String): List<PlaylistDto> {
        val response = client.get("$baseUrl/rest/v1/playlists") {
            header("apikey", anonKey)
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            parameter("select", "id,name,updated_at,cover_url,deleted")
            parameter("deleted", "eq.false")
            parameter("order", "created_at.asc")
            parameter("limit", "200")
        }
        return decoded(response)
    }

    suspend fun playlistItems(accessToken: String, playlistId: String): List<RemotePlaylistItem> {
        val response = client.get("$baseUrl/rest/v1/playlist_items") {
            header("apikey", anonKey)
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            parameter("select", "id,playlist_id,track_id,rank,deleted")
            parameter("playlist_id", "eq.$playlistId")
            parameter("deleted", "eq.false")
            parameter("order", "rank.asc")
            parameter("limit", "1000")
        }
        return decoded(response)
    }

    suspend fun tracks(accessToken: String, ids: Collection<String>): List<RemoteTrack> {
        if (ids.isEmpty()) return emptyList()
        val response = client.get("$baseUrl/rest/v1/tracks") {
            header("apikey", anonKey)
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            parameter("select", "id,title,artist,album,duration,thumbnail")
            parameter("id", "in.(${ids.joinToString(",")})")
            parameter("limit", "1000")
        }
        return decoded(response)
    }

    suspend fun pushTrack(accessToken: String, track: com.openfy.core.Track) {
        val response = client.post("$baseUrl/rest/v1/tracks") {
            header("apikey", anonKey)
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            header("Prefer", "resolution=ignore-duplicates,return=minimal")
            parameter("on_conflict", "id")
            contentType(ContentType.Application.Json)
            setBody(
                PushTrack(
                    id = track.id,
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    duration = track.durationSec,
                    thumbnail = track.thumbnail,
                ),
            )
        }
        if (!response.status.isSuccess()) decodeError(response)
    }

    suspend fun pushPlayEvent(accessToken: String, event: PushPlayEvent) {
        val response = client.post("$baseUrl/rest/v1/play_events") {
            header("apikey", anonKey)
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            header("Prefer", "return=minimal")
            contentType(ContentType.Application.Json)
            setBody(event)
        }
        if (!response.status.isSuccess()) decodeError(response)
    }

    suspend fun likes(accessToken: String): Set<String> {
        val response = client.get("$baseUrl/rest/v1/likes") {
            header("apikey", anonKey)
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            parameter("select", "track_id")
            parameter("deleted", "eq.false")
            parameter("limit", "5000")
        }
        return decoded<List<LikeRow>>(response).mapTo(HashSet()) { it.trackId }
    }

    suspend fun setLike(
        accessToken: String,
        userId: String,
        track: com.openfy.core.Track,
        liked: Boolean,
    ) {
        pushTrack(accessToken, track)
        val response = client.post("$baseUrl/rest/v1/likes") {
            header("apikey", anonKey)
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            header("Prefer", "resolution=merge-duplicates,return=minimal")
            parameter("on_conflict", "user_id,track_id")
            contentType(ContentType.Application.Json)
            setBody(LikeUpsert(userId, track.id, deleted = !liked))
        }
        if (!response.status.isSuccess()) decodeError(response)
    }

    suspend fun addPlaylistItem(
        accessToken: String,
        playlistId: String,
        track: com.openfy.core.Track,
    ): String {
        pushTrack(accessToken, track)
        val itemId = java.util.UUID.nameUUIDFromBytes(
            "openfy:$playlistId:${track.id}".toByteArray(Charsets.UTF_8),
        ).toString()
        val rank = "z${System.currentTimeMillis()}"
        val response = client.post("$baseUrl/rest/v1/playlist_items") {
            header("apikey", anonKey)
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            header("Prefer", "resolution=merge-duplicates,return=minimal")
            parameter("on_conflict", "id")
            contentType(ContentType.Application.Json)
            setBody(
                PushPlaylistItem(
                    id = itemId,
                    playlistId = playlistId,
                    trackId = track.id,
                    rank = rank,
                    deleted = false,
                ),
            )
        }
        if (!response.status.isSuccess()) decodeError(response)
        return itemId
    }

    suspend fun removePlaylistItem(accessToken: String, itemId: String) {
        val response = client.patch("$baseUrl/rest/v1/playlist_items") {
            header("apikey", anonKey)
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            header("Prefer", "return=minimal")
            parameter("id", "eq.$itemId")
            contentType(ContentType.Application.Json)
            setBody(DeletedPatch())
        }
        if (!response.status.isSuccess()) decodeError(response)
    }

    suspend fun createPlaylist(accessToken: String, name: String): PlaylistDto? {
        val response = client.post("$baseUrl/rest/v1/playlists") {
            header("apikey", anonKey)
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            header("Prefer", "return=representation")
            contentType(ContentType.Application.Json)
            setBody(CreatePlaylistRequest(name.trim()))
        }
        val rows: List<PlaylistDto> = decoded(response)
        return rows.firstOrNull()
    }

    suspend fun upsertDevice(accessToken: String, deviceId: String, name: String) {
        val response = client.post("$baseUrl/rest/v1/devices") {
            header("apikey", anonKey)
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            header("Prefer", "resolution=merge-duplicates,return=minimal")
            parameter("on_conflict", "id")
            contentType(ContentType.Application.Json)
            setBody(
                DeviceUpsert(
                    id = deviceId,
                    name = name,
                    platform = "android",
                    lastSeenAt = Instant.now().toString(),
                ),
            )
        }
        if (!response.status.isSuccess()) decodeError(response)
    }

    fun close() {
        client.close()
    }
}
