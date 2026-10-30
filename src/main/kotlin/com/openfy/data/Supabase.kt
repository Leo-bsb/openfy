package com.openfy.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import com.openfy.core.Track

/**
 * Configuração pública do Supabase. A chave publishable é feita para ficar no
 * cliente; a service_role nunca entra aqui.
 */
object SupabaseConfig {
    val url: String = System.getenv("OPENFY_SUPABASE_URL")
        ?.takeIf { it.isNotBlank() }
        ?: "https://arxoiibjocazfxtliysc.supabase.co"

    val anonKey: String = System.getenv("OPENFY_SUPABASE_ANON_KEY")
        ?.takeIf { it.isNotBlank() }
        ?: "sb_publishable_p9DT11Nnv2TTYPOo4_oW7A_6DOIeMni"
}

class SupabaseException(val status: Int, message: String) : Exception(message)

@Serializable
data class RemoteAuthUser(val id: String = "", val email: String? = null)

@Serializable
data class RemoteAuthSession(
    @SerialName("access_token") val accessToken: String = "",
    @SerialName("refresh_token") val refreshToken: String = "",
    @SerialName("expires_in") val expiresIn: Long = 0,
    val user: RemoteAuthUser? = null,
)

@Serializable
data class RemoteProfile(
    val id: String = "",
    val handle: String = "",
    @SerialName("display_name") val displayName: String = "",
    @SerialName("avatar_url") val avatarUrl: String = "",
)

/**
 * Linha de `activities` usada como snapshot privado do perfil enquanto a
 * migration 0003 não entra. `payload` guarda o ProfileExtras serializado.
 */
@Serializable
data class RemoteActivity(
    val id: String = "",
    val payload: JsonElement = JsonNull,
    @SerialName("created_at") val createdAt: String = "",
)

@Serializable
data class PushActivity(
    @SerialName("actor_id") val actorId: String,
    val type: String,
    val payload: JsonElement,
    @SerialName("is_public") val isPublic: Boolean = false,
)

@Serializable
data class ProfilePatch(
    @SerialName("display_name") val displayName: String,
    @SerialName("avatar_url") val avatarUrl: String,
)

@Serializable
data class DeletedPatch(val deleted: Boolean = true)

@Serializable
data class RemoteTrack(
    val id: String,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val duration: Int = 0,
    val thumbnail: String = "",
)

@Serializable
data class RemotePlaylist(
    val id: String,
    val name: String = "",
    @SerialName("updated_at") val updatedAt: String? = null,
    val deleted: Boolean = false,
    @SerialName("share_code") val shareCode: String? = null,
    @SerialName("cover_url") val coverUrl: String = "",
)

@Serializable
data class RemotePlaylistItem(
    val id: String,
    @SerialName("playlist_id") val playlistId: String,
    @SerialName("track_id") val trackId: String,
    val rank: String = "",
    val deleted: Boolean = false,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class RemoteLike(
    @SerialName("track_id") val trackId: String,
    @SerialName("updated_at") val updatedAt: String? = null,
    val deleted: Boolean = false,
)

@Serializable
data class PushTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Int,
    val thumbnail: String,
)

@Serializable
data class PushPlaylist(
    val id: String,
    val name: String,
    val deleted: Boolean,
    @SerialName("cover_url") val coverUrl: String = "",
)

@Serializable
data class PushPlaylistItem(
    val id: String,
    @SerialName("playlist_id") val playlistId: String,
    @SerialName("track_id") val trackId: String,
    val rank: String,
    val deleted: Boolean,
)

@Serializable
data class PushLike(
    @SerialName("user_id") val userId: String,
    @SerialName("track_id") val trackId: String,
    val deleted: Boolean,
)

@Serializable
data class PushPlayEvent(
    val id: String? = null,
    @SerialName("user_id") val userId: String,
    @SerialName("device_id") val deviceId: String? = null,
    @SerialName("track_id") val trackId: String,
    @SerialName("started_at") val startedAt: String,
    @SerialName("played_ms") val playedMs: Int,
    @SerialName("duration_ms") val durationMs: Int,
    val completed: Boolean = true,
    val skipped: Boolean = false,
    val source: String = "desktop",
    @SerialName("context_id") val contextId: String? = null,
)

@Serializable
data class RemotePlayEvent(
    val id: String,
    @SerialName("track_id") val trackId: String,
    @SerialName("started_at") val startedAt: String,
    @SerialName("played_ms") val playedMs: Int = 0,
    @SerialName("duration_ms") val durationMs: Int = 0,
    val completed: Boolean = false,
    val skipped: Boolean = false,
    val source: String = "desktop",
    @SerialName("context_id") val contextId: String? = null,
    val deleted: Boolean = false,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class PushDevice(
    val id: String,
    val name: String,
    val platform: String,
    @SerialName("last_seen_at") val lastSeenAt: String,
)

@Serializable
data class RemotePlaybackState(
    @SerialName("device_id") val deviceId: String? = null,
    @SerialName("track_id") val trackId: String? = null,
    val queue: List<Track> = emptyList(),
    @SerialName("queue_index") val queueIndex: Int = -1,
    @SerialName("position_ms") val positionMs: Int = 0,
    val playing: Boolean = false,
    val shuffle: Boolean = false,
    val repeat: String = "OFF",
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class PushPlaybackState(
    @SerialName("user_id") val userId: String,
    @SerialName("device_id") val deviceId: String?,
    @SerialName("track_id") val trackId: String?,
    val queue: List<Track>,
    @SerialName("queue_index") val queueIndex: Int,
    @SerialName("position_ms") val positionMs: Int,
    val playing: Boolean,
    val shuffle: Boolean,
    val repeat: String,
)

@Serializable
data class RemoteMonthlyListening(
    val month: String = "",
    @SerialName("played_ms") val playedMs: Long = 0,
    val plays: Long = 0,
    @SerialName("unique_tracks") val uniqueTracks: Long = 0,
    @SerialName("completed_plays") val completedPlays: Long = 0,
    @SerialName("skipped_plays") val skippedPlays: Long = 0,
)

@Serializable
data class RemoteMonthlyTrack(
    @SerialName("track_id") val trackId: String = "",
    val plays: Long = 0,
    @SerialName("played_ms") val playedMs: Long = 0,
)

@Serializable
data class RemoteMonthlyArtist(
    val artist: String = "",
    val plays: Long = 0,
    @SerialName("played_ms") val playedMs: Long = 0,
)

@Serializable
private data class ApiError(
    val message: String? = null,
    val error: String? = null,
    @SerialName("error_description") val errorDescription: String? = null,
)

@Serializable
private data class LoginBody(val email: String, val password: String)

@Serializable
private data class RefreshBody(@SerialName("refresh_token") val refreshToken: String)

@Serializable
private data class MemberRow(@SerialName("playlist_id") val playlistId: String)

@Serializable
private data class IdRow(val id: String)

@Serializable
private data class ShareCodeBody(@SerialName("p_playlist_id") val playlistId: String)

@Serializable
private data class JoinBody(@SerialName("p_code") val code: String)

class SupabaseApi {

    private val baseUrl = SupabaseConfig.url.trimEnd('/')
    private val key = SupabaseConfig.anonKey
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 30_000
        }
    }

    private suspend fun fail(response: HttpResponse): Nothing {
        val text = runCatching { response.bodyAsText() }.getOrDefault("")
        val parsed = runCatching { json.decodeFromString<ApiError>(text) }.getOrNull()
        val message = parsed?.message
            ?: parsed?.errorDescription
            ?: parsed?.error
            ?: text.takeIf { it.isNotBlank() }
            ?: "Erro ${response.status.value}"
        throw SupabaseException(response.status.value, message)
    }

    private suspend inline fun <reified T> decode(response: HttpResponse): T {
        if (!response.status.isSuccess()) fail(response)
        val text = response.bodyAsText()
        if (text.isBlank()) throw SupabaseException(response.status.value, "Resposta vazia do servidor.")
        return json.decodeFromString(text)
    }

    private suspend fun ensure(response: HttpResponse) {
        if (!response.status.isSuccess()) fail(response)
    }

    private fun inClause(values: Collection<String>): String =
        "in.(${values.joinToString(",")})"

    suspend fun signIn(email: String, password: String): RemoteAuthSession =
        decode(
            client.post("$baseUrl/auth/v1/token") {
                parameter("grant_type", "password")
                header("apikey", key)
                contentType(ContentType.Application.Json)
                setBody(LoginBody(email.trim(), password))
            },
        )

    suspend fun signUp(email: String, password: String): RemoteAuthSession =
        decode(
            client.post("$baseUrl/auth/v1/signup") {
                header("apikey", key)
                contentType(ContentType.Application.Json)
                setBody(LoginBody(email.trim(), password))
            },
        )

    suspend fun refresh(refreshToken: String): RemoteAuthSession =
        decode(
            client.post("$baseUrl/auth/v1/token") {
                parameter("grant_type", "refresh_token")
                header("apikey", key)
                contentType(ContentType.Application.Json)
                setBody(RefreshBody(refreshToken))
            },
        )

    suspend fun logout(accessToken: String) {
        runCatching {
            client.post("$baseUrl/auth/v1/logout") {
                header("apikey", key)
                header(HttpHeaders.Authorization, "Bearer $accessToken")
            }
        }
    }

    suspend fun profile(accessToken: String, userId: String): RemoteProfile? {
        val rows = decode<List<RemoteProfile>>(
            client.get("$baseUrl/rest/v1/profiles") {
                auth(accessToken)
                parameter("select", "id,handle,display_name,avatar_url")
                parameter("id", "eq.$userId")
                parameter("limit", "1")
            },
        )
        return rows.firstOrNull()
    }

    suspend fun updateProfile(
        accessToken: String,
        userId: String,
        displayName: String,
        avatarUrl: String,
    ) {
        ensure(
            client.patch("$baseUrl/rest/v1/profiles") {
                auth(accessToken)
                header("Prefer", "return=minimal")
                parameter("id", "eq.$userId")
                contentType(ContentType.Application.Json)
                setBody(ProfilePatch(displayName, avatarUrl))
            },
        )
    }

    /** Último snapshot privado do perfil; a RLS só devolve as linhas do próprio usuário. */
    suspend fun profilePrefs(accessToken: String, userId: String): RemoteActivity? {
        val rows = decode<List<RemoteActivity>>(
            client.get("$baseUrl/rest/v1/activities") {
                auth(accessToken)
                parameter("select", "id,payload,created_at")
                parameter("actor_id", "eq.$userId")
                parameter("type", "eq.profile_prefs")
                parameter("deleted", "eq.false")
                parameter("order", "created_at.desc")
                parameter("limit", "1")
            },
        )
        return rows.firstOrNull()
    }

    /**
     * Grava um novo snapshot e devolve o id. `activities` não permite alterar o
     * payload de uma linha existente (trigger protect_activity_fields), então a
     * versão anterior é marcada como deleted depois que a nova entra.
     */
    suspend fun saveProfilePrefs(
        accessToken: String,
        userId: String,
        payload: JsonElement,
    ): String {
        val rows = decode<List<IdRow>>(
            client.post("$baseUrl/rest/v1/activities") {
                auth(accessToken)
                header("Prefer", "return=representation")
                contentType(ContentType.Application.Json)
                setBody(PushActivity(userId, "profile_prefs", payload, isPublic = false))
            },
        )
        return rows.firstOrNull()?.id
            ?: throw SupabaseException(0, "O servidor não devolveu o id do perfil.")
    }

    suspend fun deleteActivity(accessToken: String, id: String) {
        ensure(
            client.patch("$baseUrl/rest/v1/activities") {
                auth(accessToken)
                header("Prefer", "return=minimal")
                parameter("id", "eq.$id")
                contentType(ContentType.Application.Json)
                setBody(DeletedPatch())
            },
        )
    }

    /** Snapshot dos ajustes (tema, player, download, EQ) privado da conta. */
    suspend fun settingsPrefs(accessToken: String, userId: String): RemoteActivity? {
        val rows = decode<List<RemoteActivity>>(
            client.get("$baseUrl/rest/v1/activities") {
                auth(accessToken)
                parameter("select", "id,payload,created_at")
                parameter("actor_id", "eq.$userId")
                parameter("type", "eq.settings_prefs")
                parameter("deleted", "eq.false")
                parameter("order", "created_at.desc")
                parameter("limit", "1")
            },
        )
        return rows.firstOrNull()
    }

    suspend fun saveSettingsPrefs(
        accessToken: String,
        userId: String,
        payload: JsonElement,
    ): String {
        val rows = decode<List<IdRow>>(
            client.post("$baseUrl/rest/v1/activities") {
                auth(accessToken)
                header("Prefer", "return=representation")
                contentType(ContentType.Application.Json)
                setBody(PushActivity(userId, "settings_prefs", payload, isPublic = false))
            },
        )
        return rows.firstOrNull()?.id
            ?: throw SupabaseException(0, "O servidor não devolveu o id dos ajustes.")
    }

    /**
     * Sobe avatar/capa do perfil no bucket público. O nome inclui timestamp para
     * o cache dos clients não mostrar a imagem anterior.
     */
    suspend fun uploadProfileImage(
        accessToken: String,
        userId: String,
        bucket: String,
        baseName: String,
        file: java.io.File,
    ): String {
        require(bucket == "avatars" || bucket == "covers") { "bucket inválido" }
        val ext = file.extension.lowercase().ifBlank { "jpg" }
        val mime = when (ext) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            else -> "image/jpeg"
        }
        val path = "$userId/$baseName.$ext"
        ensure(
            client.post("$baseUrl/storage/v1/object/$bucket/$path") {
                auth(accessToken)
                header("x-upsert", "true")
                contentType(ContentType.parse(mime))
                setBody(file.readBytes())
            },
        )
        return "$baseUrl/storage/v1/object/public/$bucket/$path?v=${System.currentTimeMillis()}"
    }

    suspend fun memberPlaylistIds(accessToken: String, userId: String): List<String> =
        decode<List<MemberRow>>(
            client.get("$baseUrl/rest/v1/playlist_members") {
                auth(accessToken)
                parameter("select", "playlist_id")
                parameter("user_id", "eq.$userId")
                parameter("deleted", "eq.false")
                parameter("limit", "1000")
            },
        ).map { it.playlistId }

    suspend fun ownedPlaylistIds(accessToken: String, userId: String): List<String> =
        decode<List<IdRow>>(
            client.get("$baseUrl/rest/v1/playlists") {
                auth(accessToken)
                parameter("select", "id")
                parameter("owner_id", "eq.$userId")
                parameter("limit", "1000")
            },
        ).map { it.id }

    suspend fun playlists(accessToken: String, ids: Collection<String>): List<RemotePlaylist> {
        if (ids.isEmpty()) return emptyList()
        return decode(
            client.get("$baseUrl/rest/v1/playlists") {
                auth(accessToken)
                parameter("select", "id,name,updated_at,deleted,cover_url")
                parameter("id", inClause(ids))
                parameter("limit", "1000")
            },
        )
    }

    /**
     * Lê o código de convite via RPC `playlist_share_code` (SECURITY DEFINER),
     * que só responde para owner/membros ativos. A coluna `share_code` deixou de
     * ser visível por SELECT direto na tabela (migration 0002).
     */
    suspend fun shareCode(accessToken: String, playlistId: String): String? {
        val response = client.post("$baseUrl/rest/v1/rpc/playlist_share_code") {
            auth(accessToken)
            contentType(ContentType.Application.Json)
            setBody(ShareCodeBody(playlistId))
        }
        if (!response.status.isSuccess()) fail(response)
        val text = response.bodyAsText().trim()
        if (text.isEmpty()) return null  // PostgREST pode devolver corpo vazio para null
        return json.decodeFromString<String?>(text)?.takeIf { it.isNotBlank() }
    }

    /** Sobe a capa para o bucket público e devolve a URL usada pelos outros aparelhos. */
    suspend fun uploadCover(
        accessToken: String,
        userId: String,
        playlistId: String,
        file: java.io.File,
    ): String {
        val ext = file.extension.lowercase().ifBlank { "jpg" }
        val mime = when (ext) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            else -> "image/jpeg"
        }
        val path = "$userId/$playlistId.$ext"
        ensure(
            client.post("$baseUrl/storage/v1/object/covers/$path") {
                auth(accessToken)
                header("x-upsert", "true")
                contentType(ContentType.parse(mime))
                setBody(file.readBytes())
            },
        )
        return "$baseUrl/storage/v1/object/public/covers/$path"
    }

    suspend fun joinPlaylist(accessToken: String, code: String): String =
        decode(
            client.post("$baseUrl/rest/v1/rpc/join_playlist") {
                auth(accessToken)
                contentType(ContentType.Application.Json)
                setBody(JoinBody(code.trim()))
            },
        )

    suspend fun items(accessToken: String, playlistIds: Collection<String>): List<RemotePlaylistItem> {
        if (playlistIds.isEmpty()) return emptyList()
        return decode(
            client.get("$baseUrl/rest/v1/playlist_items") {
                auth(accessToken)
                parameter("select", "id,playlist_id,track_id,rank,deleted,updated_at")
                parameter("playlist_id", inClause(playlistIds))
                parameter("limit", "5000")
            },
        )
    }

    suspend fun likes(accessToken: String): List<RemoteLike> =
        decode(
            client.get("$baseUrl/rest/v1/likes") {
                auth(accessToken)
                parameter("select", "track_id,updated_at,deleted")
                parameter("limit", "5000")
            },
        )

    suspend fun tracks(accessToken: String, ids: Collection<String>): List<RemoteTrack> {
        if (ids.isEmpty()) return emptyList()
        return decode(
            client.get("$baseUrl/rest/v1/tracks") {
                auth(accessToken)
                parameter("select", "id,title,artist,album,duration,thumbnail")
                parameter("id", inClause(ids))
                parameter("limit", "1000")
            },
        )
    }

    /**
     * Escutas da conta. `cursor` é o `updated_at` da última linha vista; usar
     * updated_at (e não started_at) pega eventos que o celular enviou offline
     * depois, sem depender do relógio do aparelho.
     */
    suspend fun playEvents(
        accessToken: String,
        cursor: String? = null,
        limit: Int = 2000,
    ): List<RemotePlayEvent> =
        decode(
            client.get("$baseUrl/rest/v1/play_events") {
                auth(accessToken)
                parameter(
                    "select",
                    "id,track_id,started_at,played_ms,duration_ms,completed,skipped," +
                        "source,context_id,deleted,updated_at",
                )
                if (cursor.isNullOrBlank()) {
                    parameter("order", "started_at.desc")
                } else {
                    parameter("updated_at", "gt.$cursor")
                    parameter("order", "updated_at.asc")
                }
                parameter("limit", limit.toString())
            },
        )

    suspend fun registerDevice(accessToken: String, deviceId: String, name: String) {
        ensure(
            client.post("$baseUrl/rest/v1/devices") {
                auth(accessToken)
                header("Prefer", "resolution=merge-duplicates,return=minimal")
                parameter("on_conflict", "id")
                contentType(ContentType.Application.Json)
                setBody(PushDevice(deviceId, name, "windows", java.time.Instant.now().toString()))
            },
        )
    }

    suspend fun playbackState(accessToken: String): RemotePlaybackState? =
        decode<List<RemotePlaybackState>>(
            client.get("$baseUrl/rest/v1/playback_state") {
                auth(accessToken)
                parameter(
                    "select",
                    "device_id,track_id,queue,queue_index,position_ms,playing,shuffle,repeat,updated_at",
                )
                parameter("limit", "1")
            },
        ).firstOrNull()

    suspend fun pushPlaybackState(accessToken: String, state: PushPlaybackState) {
        ensure(
            client.post("$baseUrl/rest/v1/playback_state") {
                auth(accessToken)
                header("Prefer", "resolution=merge-duplicates,return=minimal")
                parameter("on_conflict", "user_id")
                contentType(ContentType.Application.Json)
                setBody(state)
            },
        )
    }

    suspend fun pushTracks(accessToken: String, tracks: List<PushTrack>) {
        if (tracks.isEmpty()) return
        tracks.chunked(100).forEach { batch ->
            ensure(
                client.post("$baseUrl/rest/v1/tracks") {
                    auth(accessToken)
                    header("Prefer", "resolution=ignore-duplicates,return=minimal")
                    parameter("on_conflict", "id")
                    contentType(ContentType.Application.Json)
                    setBody(batch)
                },
            )
        }
    }

    suspend fun pushPlaylists(accessToken: String, playlists: List<PushPlaylist>) {
        if (playlists.isEmpty()) return
        playlists.chunked(100).forEach { batch ->
            ensure(
                client.post("$baseUrl/rest/v1/playlists") {
                    auth(accessToken)
                    header("Prefer", "resolution=merge-duplicates,return=minimal")
                    parameter("on_conflict", "id")
                    contentType(ContentType.Application.Json)
                    setBody(batch)
                },
            )
        }
    }

    suspend fun pushItems(accessToken: String, items: List<PushPlaylistItem>) {
        if (items.isEmpty()) return
        items.chunked(200).forEach { batch ->
            ensure(
                client.post("$baseUrl/rest/v1/playlist_items") {
                    auth(accessToken)
                    header("Prefer", "resolution=merge-duplicates,return=minimal")
                    parameter("on_conflict", "id")
                    contentType(ContentType.Application.Json)
                    setBody(batch)
                },
            )
        }
    }

    suspend fun pushLikes(accessToken: String, likes: List<PushLike>) {
        if (likes.isEmpty()) return
        likes.chunked(200).forEach { batch ->
            ensure(
                client.post("$baseUrl/rest/v1/likes") {
                    auth(accessToken)
                    header("Prefer", "resolution=merge-duplicates,return=minimal")
                    parameter("on_conflict", "user_id,track_id")
                    contentType(ContentType.Application.Json)
                    setBody(batch)
                },
            )
        }
    }

    suspend fun pushPlayEvents(accessToken: String, events: List<PushPlayEvent>) {
        if (events.isEmpty()) return
        // O PostgREST exige o mesmo conjunto de chaves em todos os objetos do lote.
        // Como `explicitNulls=false`, eventos sem id/contexto serializam menos campos;
        // agrupa por formato para não misturar objetos com chaves diferentes.
        events.groupBy { event ->
            listOf(event.id != null, event.deviceId != null, event.contextId != null)
        }.values.forEach { group ->
            group.chunked(200).forEach { batch ->
                ensure(
                    client.post("$baseUrl/rest/v1/play_events") {
                        auth(accessToken)
                        header("Prefer", "resolution=ignore-duplicates,return=minimal")
                        parameter("on_conflict", "id")
                        contentType(ContentType.Application.Json)
                        setBody(batch)
                    },
                )
            }
        }
    }

    private fun io.ktor.client.request.HttpRequestBuilder.auth(accessToken: String) {
        header("apikey", key)
        header(HttpHeaders.Authorization, "Bearer $accessToken")
    }

    suspend fun monthlyListening(accessToken: String, month: java.time.YearMonth): List<RemoteMonthlyListening> =
        decode(
            client.get("$baseUrl/rest/v1/monthly_listening") {
                auth(accessToken)
                parameter("select", "month,played_ms,plays,unique_tracks,completed_plays,skipped_plays")
                parameter("month", "eq.${month.atDay(1)}")
                parameter("limit", "1")
            },
        )

    suspend fun monthlyTopTracks(
        accessToken: String,
        month: java.time.YearMonth,
        limit: Int = 20,
    ): List<RemoteMonthlyTrack> =
        decode(
            client.get("$baseUrl/rest/v1/monthly_top_tracks") {
                auth(accessToken)
                parameter("select", "track_id,plays,played_ms")
                parameter("month", "eq.${month.atDay(1)}")
                parameter("order", "plays.desc")
                parameter("limit", limit.toString())
            },
        )

    suspend fun monthlyTopArtists(
        accessToken: String,
        month: java.time.YearMonth,
        limit: Int = 10,
    ): List<RemoteMonthlyArtist> =
        decode(
            client.get("$baseUrl/rest/v1/monthly_top_artists") {
                auth(accessToken)
                parameter("select", "artist,plays,played_ms")
                parameter("month", "eq.${month.atDay(1)}")
                parameter("order", "plays.desc")
                parameter("limit", limit.toString())
            },
        )

    fun close() {
        client.close()
    }
}
