package com.openfy.android.data

import android.content.Context
import android.os.Build
import com.openfy.core.ColorPalette
import com.openfy.core.PlayerLayout
import com.openfy.core.ProfileExtras
import com.openfy.core.ProfileStatsData
import com.openfy.core.RepeatMode
import com.openfy.core.Track
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

data class StoredSession(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val email: String,
    val expiresAt: Long,
) {
    val isExpired: Boolean
        get() = expiresAt > 0 && System.currentTimeMillis() > expiresAt - 60_000
}

/**
 * Sessão local. Para o MVP usa SharedPreferences privadas do app.
 * Antes de distribuir o APK, migrar para EncryptedSharedPreferences/Keystore.
 */
class SessionStore(context: Context) {

    private val prefs = context.getSharedPreferences("openfy_session", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): StoredSession? {
        val access = prefs.getString("access_token", null) ?: return null
        val refresh = prefs.getString("refresh_token", null) ?: return null
        val userId = prefs.getString("user_id", null) ?: return null
        if (access.isBlank() || refresh.isBlank() || userId.isBlank()) return null
        return StoredSession(
            accessToken = access,
            refreshToken = refresh,
            userId = userId,
            email = prefs.getString("email", "") ?: "",
            expiresAt = prefs.getLong("expires_at", 0),
        )
    }

    fun save(session: AuthSession, previous: StoredSession? = null, emailFallback: String = ""): StoredSession {
        val userId = session.user?.id?.takeIf { it.isNotBlank() }
            ?: previous?.userId
            ?: ""
        val email = session.user?.email?.takeIf { it.isNotBlank() }
            ?: previous?.email
            ?: emailFallback
        val expiresAt = System.currentTimeMillis() +
            (if (session.expiresIn > 0) session.expiresIn else 3600L) * 1000L
        val stored = StoredSession(
            accessToken = session.accessToken,
            refreshToken = session.refreshToken.ifBlank { previous?.refreshToken ?: "" },
            userId = userId,
            email = email,
            expiresAt = expiresAt,
        )
        prefs.edit()
            .putString("access_token", stored.accessToken)
            .putString("refresh_token", stored.refreshToken)
            .putString("user_id", stored.userId)
            .putString("email", stored.email)
            .putLong("expires_at", stored.expiresAt)
            .apply()
        return stored
    }

    fun crossfadeSeconds(): Int =
        prefs.getInt("crossfade_seconds", 6).coerceIn(0, 12)

    fun setCrossfadeSeconds(seconds: Int) {
        prefs.edit().putInt("crossfade_seconds", seconds.coerceIn(0, 12)).apply()
    }

    fun clear() {
        val crossfade = crossfadeSeconds()
        val palette = palette()
        val layout = playerLayout()
        val gradient = coverGradient()
        val eqEnabled = eqEnabled()
        val eqGains = eqGains()
        val downloadLimit = downloadLimitMb()
        prefs.edit().clear()
            .putInt("crossfade_seconds", crossfade)
            .putString("ui_palette", palette.id)
            .putString("ui_player_layout", layout.id)
            .putBoolean("ui_cover_gradient", gradient)
            .putBoolean("eq_enabled", eqEnabled)
            .putString("eq_gains", eqGains.joinToString(","))
            .putInt("download_limit_mb", downloadLimit)
            .apply()
    }

    fun palette(): ColorPalette =
        ColorPalette.fromId(prefs.getString("ui_palette", null))

    fun setPalette(value: ColorPalette) {
        prefs.edit().putString("ui_palette", value.id).apply()
    }

    fun playerLayout(): PlayerLayout =
        PlayerLayout.fromId(prefs.getString("ui_player_layout", null))

    fun setPlayerLayout(value: PlayerLayout) {
        prefs.edit().putString("ui_player_layout", value.id).apply()
    }

    fun coverGradient(): Boolean = prefs.getBoolean("ui_cover_gradient", true)

    fun setCoverGradient(enabled: Boolean) {
        prefs.edit().putBoolean("ui_cover_gradient", enabled).apply()
    }

    fun eqEnabled(): Boolean = prefs.getBoolean("eq_enabled", false)

    fun setEqEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("eq_enabled", enabled).apply()
    }

    fun eqGains(): List<Int> = prefs.getString("eq_gains", null)
        ?.split(',')
        ?.mapNotNull { it.toIntOrNull() }
        ?.takeIf { it.size == 5 }
        ?: listOf(0, 0, 0, 0, 0)

    fun setEqGains(gains: List<Int>) {
        prefs.edit().putString("eq_gains", gains.joinToString(",")).apply()
    }

    fun profilePrefs(userId: String): ProfileExtras? = runCatching {
        val raw = prefs.getString("profile_prefs_$userId", null) ?: return null
        json.decodeFromString<ProfileExtras>(raw).sanitized()
    }.getOrNull()

    fun setProfilePrefs(userId: String, value: ProfileExtras) {
        prefs.edit()
            .putString("profile_prefs_$userId", json.encodeToString(value.sanitized()))
            .apply()
    }

    fun profileStats(userId: String): ProfileStatsData? = runCatching {
        val raw = prefs.getString("profile_stats_$userId", null) ?: return null
        json.decodeFromString<ProfileStatsData>(raw)
    }.getOrNull()

    fun setProfileStats(userId: String, value: ProfileStatsData) {
        prefs.edit()
            .putString("profile_stats_$userId", json.encodeToString(value))
            .apply()
    }

    fun recentTracks(): List<Track> = runCatching {
        json.decodeFromString<List<Track>>(prefs.getString("recent_tracks", null) ?: "[]")
    }.getOrDefault(emptyList())

    fun setRecentTracks(tracks: List<Track>) {
        prefs.edit()
            .putString("recent_tracks", json.encodeToString(tracks.take(24)))
            .apply()
    }

    fun playbackQueue(): List<Track> = runCatching {
        json.decodeFromString<List<Track>>(prefs.getString("playback_queue", null) ?: "[]")
    }.getOrDefault(emptyList())

    /** Limite de uso do armazenamento de downloads. Padrão 2 GB. */
    fun downloadLimitMb(): Int =
        prefs.getInt("download_limit_mb", DEFAULT_DOWNLOAD_LIMIT_MB).coerceIn(128, 20_480)

    fun setDownloadLimitMb(mb: Int) {
        prefs.edit()
            .putInt("download_limit_mb", mb.coerceIn(128, 20_480))
            .apply()
    }

    fun playbackIndex(): Int = prefs.getInt("playback_index", -1)

    fun playbackPosition(): Long = prefs.getLong("playback_position", 0L)

    fun shuffle(): Boolean = prefs.getBoolean("play_shuffle", false)

    fun setShuffle(enabled: Boolean) {
        prefs.edit().putBoolean("play_shuffle", enabled).apply()
    }

    fun repeatMode(): RepeatMode = runCatching {
        RepeatMode.valueOf(prefs.getString("play_repeat", null).orEmpty())
    }.getOrDefault(RepeatMode.OFF)

    fun setRepeatMode(mode: RepeatMode) {
        prefs.edit().putString("play_repeat", mode.name).apply()
    }

    fun shuffleSnapshotQueue(): List<Track> = runCatching {
        json.decodeFromString<List<Track>>(prefs.getString("shuffle_queue", null) ?: "[]")
    }.getOrDefault(emptyList())

    fun shuffleSnapshotIndex(): Int = prefs.getInt("shuffle_index", -1)

    fun setShuffleSnapshot(queue: List<Track>, index: Int) {
        prefs.edit()
            .putString("shuffle_queue", json.encodeToString(queue.take(400)))
            .putInt("shuffle_index", index.coerceAtMost(399))
            .apply()
    }

    fun clearShuffleSnapshot() {
        prefs.edit()
            .remove("shuffle_queue")
            .remove("shuffle_index")
            .apply()
    }

    fun setPlaybackSession(queue: List<Track>, index: Int, positionMs: Long) {
        if (queue.isEmpty() || index !in queue.indices) return
        prefs.edit()
            .putString("playback_queue", json.encodeToString(queue.take(400)))
            .putInt("playback_index", index.coerceAtMost(399))
            .putLong("playback_position", positionMs.coerceAtLeast(0L))
            .apply()
    }

    fun clearPlaybackSession() {
        prefs.edit()
            .remove("playback_queue")
            .remove("playback_index")
            .remove("playback_position")
            .apply()
    }

    /** ID estável do aparelho para a conta. */
    fun deviceId(): String {
        val current = prefs.getString("device_id", null)
        if (!current.isNullOrBlank()) return current
        val created = UUID.randomUUID().toString()
        prefs.edit().putString("device_id", created).apply()
        return created
    }

    fun deviceName(): String {
        val name = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
        return name.ifBlank { "Android" }
    }

    companion object {
        const val DEFAULT_DOWNLOAD_LIMIT_MB = 2048
    }
}
