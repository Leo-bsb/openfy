package com.openfy.android.data

import com.openfy.core.Track
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

@Serializable
data class AuthUser(
    val id: String = "",
    val email: String? = null,
)

@Serializable
data class AuthSession(
    @SerialName("access_token") val accessToken: String = "",
    @SerialName("refresh_token") val refreshToken: String = "",
    @SerialName("expires_in") val expiresIn: Long = 0,
    @SerialName("token_type") val tokenType: String = "bearer",
    val user: AuthUser? = null,
)

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class SignUpRequest(
    val email: String,
    val password: String,
    val data: Map<String, String> = emptyMap(),
)

@Serializable
data class RefreshRequest(
    @SerialName("refresh_token") val refreshToken: String,
)

@Serializable
data class RecoverRequest(
    val email: String,
    @SerialName("redirect_to") val redirectTo: String,
)

@Serializable
data class VerifyRecoveryRequest(
    val type: String = "recovery",
    val token: String,
    val email: String,
)

@Serializable
data class UpdatePasswordRequest(val password: String)

@Serializable
data class Profile(
    val id: String = "",
    val handle: String = "",
    @SerialName("display_name") val displayName: String = "",
    @SerialName("avatar_url") val avatarUrl: String = "",
)

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
data class PlaylistDto(
    val id: String = "",
    val name: String = "",
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("cover_url") val coverUrl: String = "",
    val deleted: Boolean = false,
)

@Serializable
data class RemotePlaylistItem(
    val id: String = "",
    @SerialName("playlist_id") val playlistId: String = "",
    @SerialName("track_id") val trackId: String = "",
    val rank: String = "",
    val deleted: Boolean = false,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class RemoteTrack(
    val id: String = "",
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val duration: Int = 0,
    val thumbnail: String = "",
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
    @SerialName("cover_url") val coverUrl: String = "",
    val deleted: Boolean = false,
)

@Serializable
data class PushPlaylistItem(
    val id: String,
    @SerialName("playlist_id") val playlistId: String,
    @SerialName("track_id") val trackId: String,
    val rank: String,
    val deleted: Boolean = false,
)

@Serializable
data class LikeRow(
    @SerialName("track_id") val trackId: String = "",
)

@Serializable
data class LikeUpsert(
    @SerialName("user_id") val userId: String,
    @SerialName("track_id") val trackId: String,
    val deleted: Boolean,
)

@Serializable
data class DeletedPatch(val deleted: Boolean = true)

@Serializable
data class IdRow(val id: String = "")

@Serializable
data class MemberRow(
    @SerialName("playlist_id") val playlistId: String = "",
)

@Serializable
data class MonthlyTrackRow(
    @SerialName("track_id") val trackId: String = "",
    val plays: Long = 0,
    @SerialName("played_ms") val playedMs: Long = 0,
)

@Serializable
data class MonthlyListeningRow(
    val month: String = "",
    @SerialName("played_ms") val playedMs: Long = 0,
    val plays: Long = 0,
    @SerialName("unique_tracks") val uniqueTracks: Long = 0,
    @SerialName("completed_plays") val completedPlays: Long = 0,
    @SerialName("skipped_plays") val skippedPlays: Long = 0,
)

@Serializable
data class MonthlyArtistRow(
    val artist: String = "",
    val plays: Long = 0,
    @SerialName("played_ms") val playedMs: Long = 0,
)

@Serializable
data class MonthRow(val month: String = "")

@Serializable
data class CoverPatch(@SerialName("cover_url") val coverUrl: String)

@Serializable
data class ShareCodeBody(@SerialName("p_playlist_id") val playlistId: String)

@Serializable
data class JoinBody(@SerialName("p_code") val code: String)

@Serializable
data class PushPlayEvent(
    val id: String? = null,
    @SerialName("user_id") val userId: String,
    @SerialName("device_id") val deviceId: String?,
    @SerialName("track_id") val trackId: String,
    @SerialName("started_at") val startedAt: String,
    @SerialName("played_ms") val playedMs: Int,
    @SerialName("duration_ms") val durationMs: Int,
    val completed: Boolean,
    val skipped: Boolean,
    val source: String,
    @SerialName("context_id") val contextId: String?,
)

@Serializable
data class RemotePlayEventDto(
    val id: String = "",
    @SerialName("track_id") val trackId: String = "",
    @SerialName("started_at") val startedAt: String = "",
    @SerialName("played_ms") val playedMs: Int = 0,
    @SerialName("duration_ms") val durationMs: Int = 0,
    val completed: Boolean = false,
    val skipped: Boolean = false,
    val source: String = "android",
    @SerialName("context_id") val contextId: String? = null,
    val deleted: Boolean = false,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class RemoteLikeDto(
    @SerialName("track_id") val trackId: String = "",
    val deleted: Boolean = false,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class PlaylistPatch(
    val name: String,
    @SerialName("updated_at") val updatedAt: String,
    val deleted: Boolean? = null,
)

@Serializable
data class UpdatedPlaylistPatch(
    @SerialName("updated_at") val updatedAt: String,
    val deleted: Boolean? = null,
)

@Serializable
data class CoverUpdatedPatch(
    @SerialName("cover_url") val coverUrl: String,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
data class PlaybackStateDto(
    @SerialName("track_id") val trackId: String? = null,
    val queue: List<Track> = emptyList(),
    @SerialName("queue_index") val queueIndex: Int = -1,
    @SerialName("position_ms") val positionMs: Int = 0,
    val playing: Boolean = false,
    val shuffle: Boolean = false,
    val repeat: String = "OFF",
    @SerialName("device_id") val deviceId: String? = null,
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
data class CreatePlaylistRequest(
    val name: String,
    val description: String = "",
)

@Serializable
data class DeviceUpsert(
    val id: String,
    val name: String,
    val platform: String = "android",
    @SerialName("last_seen_at") val lastSeenAt: String,
)

@Serializable
data class ApiError(
    val message: String? = null,
    val error: String? = null,
    @SerialName("error_description") val errorDescription: String? = null,
)
