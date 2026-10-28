package com.openfy.android.data

import android.content.Context
import android.os.Build
import com.openfy.core.ColorPalette
import com.openfy.core.PlayerLayout
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
        prefs.edit().clear()
            .putInt("crossfade_seconds", crossfade)
            .putString("ui_palette", palette.id)
            .putString("ui_player_layout", layout.id)
            .putBoolean("ui_cover_gradient", gradient)
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

    fun recentTracks(): List<Track> = runCatching {
        json.decodeFromString<List<Track>>(prefs.getString("recent_tracks", null) ?: "[]")
    }.getOrDefault(emptyList())

    fun setRecentTracks(tracks: List<Track>) {
        prefs.edit()
            .putString("recent_tracks", json.encodeToString(tracks.take(24)))
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
}
