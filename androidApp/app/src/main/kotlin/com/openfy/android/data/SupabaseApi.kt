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
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
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

    /** Envia o e-mail de recuperação de senha com o link de volta para o site. */
    suspend fun recover(email: String, redirectTo: String) {
        requireConfigured()
        val response = client.post("$baseUrl/auth/v1/recover") {
            header("apikey", anonKey)
            contentType(ContentType.Application.Json)
            setBody(RecoverRequest(email.trim(), redirectTo))
        }
        if (!response.status.isSuccess()) decodeError(response)
    }

    /** Troca o código do e-mail por uma sessão de recuperação. */
    suspend fun verifyRecovery(email: String, token: String): AuthSession {
        requireConfigured()
        val response = client.post("$baseUrl/auth/v1/verify") {
            header("apikey", anonKey)
            contentType(ContentType.Application.Json)
            setBody(VerifyRecoveryRequest(token = token.trim(), email = email.trim()))
        }
        return decoded(response)
    }

    /** Define a nova senha usando a sessão de recuperação. */
    suspend fun updatePassword(accessToken: String, password: String) {
        requireConfigured()
        val response = client.put("$baseUrl/auth/v1/user") {
            header("apikey", anonKey)
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            contentType(ContentType.Application.Json)
            setBody(UpdatePasswordRequest(password))
        }
        if (!response.status.isSuccess()) decodeError(response)
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

    suspend fun updateProfile(
        accessToken: String,
        userId: String,
        displayName: String,
        avatarUrl: String,
    ) {
        val response = client.patch("$baseUrl/rest/v1/profiles") {
            header("apikey", anonKey)
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            header("Prefer", "return=minimal")
            parameter("id", "eq.$userId")
            contentType(ContentType.Application.Json)
            setBody(ProfilePatch(displayName, avatarUrl))
        }
        if (!response.status.isSuccess()) decodeError(response)
    }

    /** Último snapshot privado do perfil; a RLS devolve só as linhas do próprio usuário. */
    suspend fun profilePrefs(accessToken: String, userId: String): RemoteActivity? {
        val response = client.get("$baseUrl/rest/v1/activities") {
            header("apikey", anonKey)
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            parameter("select", "id,payload,created_at")
            parameter("actor_id", "eq.$userId")
            parameter("type", "eq.profile_prefs")
            parameter("deleted", "eq.false")
            parameter("order", "created_at.desc")
            parameter("limit", "1")
        }
        return decoded<List<RemoteActivity>>(response).firstOrNull()
    }

    suspend fun saveProfilePrefs(
        accessToken: String,
        userId: String,
        payload: JsonElement,
    ): String {
        val response = client.post("$baseUrl/rest/v1/activities") {
            header("apikey", anonKey)
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            header("Prefer", "return=representation")
            contentType(ContentType.Application.Json)
            setBody(PushActivity(userId, "profile_prefs", payload, isPublic = false))
        }
        val rows = decoded<List<IdRow>>(response)
        return rows.firstOrNull()?.id
            ?: throw SupabaseException("O servidor não devolveu o id do perfil.")
    }

    suspend fun deleteActivity(accessToken: String, id: String) {
        val response = client.patch("$baseUrl/rest/v1/activities") {
            header("apikey", anonKey)
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            header("Prefer", "return=minimal")
            parameter("id", "eq.$id")
            contentType(ContentType.Application.Json)
            setBody(DeletedPatch())
        }
        if (!response.status.isSuccess()) decodeError(response)
    }

    suspend fun uploadProfileImage(
        accessToken: String,
        userId: String,
        bucket: String,
        baseName: String,
        bytes: ByteArray,
        mime: String,
        extension: String,
    ): String {
        require(bucket == "avatars" || bucket == "covers") { "bucket inválido" }
        val ext = extension.lowercase().ifBlank { "jpg" }
        val path = "$userId/$baseName.$ext"
        val response = client.post("$baseUrl/storage/v1/object/$bucket/$path") {
            header("apikey", anonKey)
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            header("x-upsert", "true")
            contentType(ContentType.parse(mime))
            setBody(bytes)
        }
        if (!response.status.isSuccess()) decodeError(response)
        return "$baseUrl/storage/v1/object/public/$bucket/$path?v=${System.currentTimeMillis()}"
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

    suspend fun ownedPlaylistIds(accessToken: String, userId: String): List<String> {
        val response = client.get("$baseUrl/rest/v1/playlists") {
            header("apikey", anonKey)
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            parameter("select", "id")
            parameter("owner_id", "eq.$userId")
            parameter("deleted", "eq.false")
            parameter("limit", "1000")
        }
        return decoded<List<IdRow>>(response).map { it.id }
    }

    suspend fun memberPlaylistIds(accessToken: String, userId: String): List<String> {
        val response = client.get("$baseUrl/rest/v1/playlist_members") {
            header("apikey", anonKey)
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            parameter("select", "playlist_id")
            parameter("user_id", "eq.$userId")
            parameter("deleted", "eq.false")
            parameter("limit", "1000")
        }
        return decoded<List<MemberRow>>(response).map { it.playlistId }
    }

    suspend fun playlists(accessToken: String, ids: Collection<String>): List<PlaylistDto> {
        if (ids.isEmpty()) return emptyList()
        return ids.chunked(50).flatMap { chunk ->
            val response = client.get("$baseUrl/rest/v1/playlists") {
                header("apikey", anonKey)
                header(HttpHeaders.Authorization, "Bearer $accessToken")
                parameter("select", "id,name,updated_at,cover_url,deleted")
                parameter("id", "in.(${chunk.joinToString(",")})")
                parameter("deleted", "eq.false")
                parameter("order", "created_at.asc")
                parameter("limit", "1000")
            }
            decoded<List<PlaylistDto>>(response)
        }
    }

    /** Todos os itens (inclusive tombstones) de várias playlists, para o pull. */
    suspend fun allPlaylistItems(
        accessToken: String,
        playlistIds: Collection<String>,
    ): List<RemotePlaylistItem> {
        if (playlistIds.isEmpty()) return emptyList()
        return playlistIds.chunked(40).flatMap { chunk ->
            val response = client.get("$baseUrl/rest/v1/playlist_items") {
                header("apikey", anonKey)
                header(HttpHeaders.Authorization, "Bearer $accessToken")
                parameter("select", "id,playlist_id,track_id,rank,deleted,updated_at")
                parameter("playlist_id", "in.(${chunk.joinToString(",")})")
                parameter("order", "playlist_id.asc,rank.asc")
                parameter("limit", "5000")
            }
            decoded<List<RemotePlaylistItem>>(response)
        }
    }

    suspend fun renamePlaylist(accessToken: String, playlistId: String, name: String) {
        val response = client.patch("$baseUrl/rest/v1/playlists") {
            header("apikey", anonKey)
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            header("Prefer", "return=minimal")
            parameter("id", "eq.$playlistId")
            contentType(ContentType.Application.Json)
            setBody(PlaylistPatch(name.trim(), Instant.now().toString()))
        }
        if (!response.status.isSuccess()) decodeError(response)
    }

    suspend fun deletePlaylist(accessToken: String, playlistId: String) {
        val response = client.patch("$baseUrl/rest/v1/playlists") {
            header("apikey", anonKey)
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            header("Prefer", "return=minimal")
            parameter("id", "eq.$playlistId")
            contentType(ContentType.Application.Json)
            setBody(UpdatedPlaylistPatch(Instant.now().toString(), deleted = true))
        }
        if (!response.status.isSuccess()) decodeError(response)
    }

    /** Upsert de playlists usado pelo flush do outbox (criar/renomear/excluir). */
    suspend fun upsertPlaylists(accessToken: String, playlists: List<PushPlaylist>) {
        if (playlists.isEmpty()) return
        playlists.chunked(100).forEach { batch ->
            val response = client.post("$baseUrl/rest/v1/playlists") {
                header("apikey", anonKey)
                header(HttpHeaders.Authorization, "Bearer $accessToken")
                header("Prefer", "resolution=merge-duplicates,return=minimal")
                parameter("on_conflict", "id")
                contentType(ContentType.Application.Json)
                setBody(batch)
            }
            if (!response.status.isSuccess()) decodeError(response)
        }
    }

    /** Upsert de itens usado pelo reorder e pelo flush do outbox. */
    suspend fun upsertPlaylistItems(accessToken: String, items: List<PushPlaylistItem>) {
        if (items.isEmpty()) return
        items.chunked(200).forEach { batch ->
            val response = client.post("$baseUrl/rest/v1/playlist_items") {
                header("apikey", anonKey)
                header(HttpHeaders.Authorization, "Bearer $accessToken")
                header("Prefer", "resolution=merge-duplicates,return=minimal")
                parameter("on_conflict", "id")
                contentType(ContentType.Application.Json)
                setBody(batch)
            }
            if (!response.status.isSuccess()) decodeError(response)
        }
    }

    /** Curtidas com tombstones; o pull substitui o conjunto local com segurança. */
    suspend fun likeRows(accessToken: String): List<RemoteLikeDto> {
        val response = client.get("$baseUrl/rest/v1/likes") {
            header("apikey", anonKey)
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            parameter("select", "track_id,deleted,updated_at")
            parameter("limit", "5000")
        }
        return decoded(response)
    }

    suspend fun playEvents(
        accessToken: String,
        cursor: String? = null,
        limit: Int = 1000,
    ): List<RemotePlayEventDto> {
        val response = client.get("$baseUrl/rest/v1/play_events") {
            header("apikey", anonKey)
            header(HttpHeaders.Authorization, "Bearer $accessToken")
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
        }
        return decoded(response)
    }

    suspend fun playbackState(accessToken: String): PlaybackStateDto? {
        val response = client.get("$baseUrl/rest/v1/playback_state") {
            header("apikey", anonKey)
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            parameter(
                "select",
                "device_id,track_id,queue,queue_index,position_ms,playing,shuffle,repeat,updated_at",
            )
            parameter("limit", "1")
        }
        return decoded<List<PlaybackStateDto>>(response).firstOrNull()
    }

    suspend fun pushPlaybackState(accessToken: String, state: PushPlaybackState) {
        val response = client.post("$baseUrl/rest/v1/playback_state") {
            header("apikey", anonKey)
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            header("Prefer", "resolution=merge-duplicates,return=minimal")
            parameter("on_conflict", "user_id")
            contentType(ContentType.Application.Json)
            setBody(state)
        }
        if (!response.status.isSuccess()) decodeError(response)
    }

    suspend fun settingsPrefs(accessToken: String, userId: String): RemoteActivity? {
        val response = client.get("$baseUrl/rest/v1/activities") {
            header("apikey", anonKey)
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            parameter("select", "id,payload,created_at")
            parameter("actor_id", "eq.$userId")
            parameter("type", "eq.settings_prefs")
            parameter("deleted", "eq.false")
            parameter("order", "created_at.desc")
            parameter("limit", "1")
        }
        return decoded<List<RemoteActivity>>(response).firstOrNull()
    }

    suspend fun saveSettingsPrefs(
        accessToken: String,
        userId: String,
        payload: JsonElement,
    ): String {
        val response = client.post("$baseUrl/rest/v1/activities") {
            header("apikey", anonKey)
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            header("Prefer", "return=representation")
            contentType(ContentType.Application.Json)
            setBody(PushActivity(userId, "settings_prefs", payload, isPublic = false))
        }
        val rows = decoded<List<IdRow>>(response)
        return rows.firstOrNull()?.id
            ?: throw SupabaseException("O servidor não devolveu o id dos ajustes.")
    }

    suspend fun monthlyTopTracks(
        accessToken: String,
        month: String,
        limit: Int = 20,
    ): List<MonthlyTrackRow> {
        val response = client.get("$baseUrl/rest/v1/monthly_top_tracks") {
            header("apikey", anonKey)
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            parameter("select", "track_id,plays,played_ms")
            parameter("month", "eq.$month")
            parameter("order", "plays.desc,played_ms.desc")
            parameter("limit", limit.toString())
        }
        return decoded(response)
    }

    suspend fun monthlyListening(accessToken: String, month: String): MonthlyListeningRow? {
        val response = client.get("$baseUrl/rest/v1/monthly_listening") {
            header("apikey", anonKey)
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            parameter("select", "month,played_ms,plays,unique_tracks,completed_plays,skipped_plays")
            parameter("month", "eq.$month")
            parameter("limit", "1")
        }
        return decoded<List<MonthlyListeningRow>>(response).firstOrNull()
    }

    suspend fun monthlyTopArtists(
        accessToken: String,
        month: String,
        limit: Int = 10,
    ): List<MonthlyArtistRow> {
        val response = client.get("$baseUrl/rest/v1/monthly_top_artists") {
            header("apikey", anonKey)
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            parameter("select", "artist,plays,played_ms")
            parameter("month", "eq.$month")
            parameter("order", "plays.desc,played_ms.desc")
            parameter("limit", limit.toString())
        }
        return decoded(response)
    }

    suspend fun monthlyMonths(accessToken: String, limit: Int = 12): List<String> {
        val response = client.get("$baseUrl/rest/v1/monthly_listening") {
            header("apikey", anonKey)
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            parameter("select", "month")
            parameter("order", "month.desc")
            parameter("limit", limit.toString())
        }
        return decoded<List<MonthRow>>(response).map { it.month.take(10) }
    }

    suspend fun uploadCover(
        accessToken: String,
        userId: String,
        playlistId: String,
        bytes: ByteArray,
        mime: String,
        extension: String,
    ): String {
        val ext = extension.lowercase().ifBlank { "jpg" }
        val path = "$userId/$playlistId.$ext"
        val response = client.post("$baseUrl/storage/v1/object/covers/$path") {
            header("apikey", anonKey)
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            header("x-upsert", "true")
            contentType(ContentType.parse(mime))
            setBody(bytes)
        }
        if (!response.status.isSuccess()) decodeError(response)
        return "$baseUrl/storage/v1/object/public/covers/$path?v=${System.currentTimeMillis()}"
    }

    suspend fun setPlaylistCover(accessToken: String, playlistId: String, url: String) {
        val response = client.patch("$baseUrl/rest/v1/playlists") {
            header("apikey", anonKey)
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            header("Prefer", "return=minimal")
            parameter("id", "eq.$playlistId")
            contentType(ContentType.Application.Json)
            setBody(CoverUpdatedPatch(url, Instant.now().toString()))
        }
        if (!response.status.isSuccess()) decodeError(response)
    }

    /**
     * Lê o código de convite via RPC `playlist_share_code` (SECURITY DEFINER),
     * que só responde para owner/membros ativos. A coluna `share_code` deixou de
     * ser visível por SELECT direto na tabela (migration 0002).
     */
    suspend fun shareCode(accessToken: String, playlistId: String): String? {
        val response = client.post("$baseUrl/rest/v1/rpc/playlist_share_code") {
            header("apikey", anonKey)
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            contentType(ContentType.Application.Json)
            setBody(ShareCodeBody(playlistId))
        }
        if (!response.status.isSuccess()) decodeError(response)
        val text = response.bodyAsText().trim()
        if (text.isEmpty()) return null  // PostgREST pode devolver corpo vazio para null
        return json.decodeFromString<String?>(text)?.takeIf { it.isNotBlank() }
    }

    suspend fun joinPlaylist(accessToken: String, code: String): String {
        val response = client.post("$baseUrl/rest/v1/rpc/join_playlist") {
            header("apikey", anonKey)
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            contentType(ContentType.Application.Json)
            setBody(JoinBody(code.trim()))
        }
        if (!response.status.isSuccess()) decodeError(response)
        return decoded<String>(response)
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
        pushTracks(accessToken, listOf(track))
    }

    suspend fun pushTracks(accessToken: String, tracks: List<com.openfy.core.Track>) {
        if (tracks.isEmpty()) return
        tracks.chunked(100).forEach { batch ->
            val response = client.post("$baseUrl/rest/v1/tracks") {
                header("apikey", anonKey)
                header(HttpHeaders.Authorization, "Bearer $accessToken")
                header("Prefer", "resolution=ignore-duplicates,return=minimal")
                parameter("on_conflict", "id")
                contentType(ContentType.Application.Json)
                setBody(
                    batch.map { track ->
                        PushTrack(
                            id = track.id,
                            title = track.title,
                            artist = track.artist,
                            album = track.album,
                            duration = track.durationSec,
                            thumbnail = track.thumbnail,
                        )
                    },
                )
            }
            if (!response.status.isSuccess()) decodeError(response)
        }
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

    suspend fun addPlaylistItems(
        accessToken: String,
        playlistId: String,
        tracks: List<com.openfy.core.Track>,
    ): Int {
        if (tracks.isEmpty()) return 0
        pushTracks(accessToken, tracks)
        var added = 0
        val stamp = System.currentTimeMillis()
        tracks.chunked(200).forEachIndexed { batchIndex, batch ->
            val items = batch.mapIndexed { index, track ->
                val position = batchIndex * 200 + index
                PushPlaylistItem(
                    id = java.util.UUID.nameUUIDFromBytes(
                        "openfy:$playlistId:${track.id}".toByteArray(Charsets.UTF_8),
                    ).toString(),
                    playlistId = playlistId,
                    trackId = track.id,
                    rank = "z${String.format(java.util.Locale.ROOT, "%013d%03d", stamp, position)}",
                    deleted = false,
                )
            }
            val response = client.post("$baseUrl/rest/v1/playlist_items") {
                header("apikey", anonKey)
                header(HttpHeaders.Authorization, "Bearer $accessToken")
                header("Prefer", "resolution=merge-duplicates,return=minimal")
                parameter("on_conflict", "id")
                contentType(ContentType.Application.Json)
                setBody(items)
            }
            if (!response.status.isSuccess()) decodeError(response)
            added += items.size
        }
        return added
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
            parameter("select", "id,name,updated_at,cover_url,deleted")
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
