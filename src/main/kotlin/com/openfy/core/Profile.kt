package com.openfy.core

import kotlinx.serialization.Serializable

/**
 * Conteúdo do perfil próprio que ainda não tem coluna dedicada no Supabase.
 *
 * Enquanto a migration social (0003) não é aplicada, o snapshot inteiro é
 * guardado em uma linha privada de `activities` (`type = profile_prefs`). Isso
 * mantém o perfil sincronizado entre PC e Android sem depender de schema novo
 * e sem expor os dados para seguidores: `is_public` fica sempre falso.
 */
@Serializable
data class ProfileExtras(
    val bio: String = "",
    val coverUrl: String = "",
    val accent: String = "openfy",
    val style: ProfileStyle = ProfileStyle(),
    val statsVisibility: String = ProfileStatsVisibility.PRIVATE,
    val statsOrder: List<String> = ProfileStatsCards.defaultOrder,
    val statsHidden: List<String> = emptyList(),
    val pins: List<ProfilePin> = emptyList(),
    val updatedAt: Long = 0L,
) {
    /** Limita o que o usuário consegue gravar, mesmo que a UI seja contornada. */
    fun sanitized(): ProfileExtras {
        val cleanBio = bio.replace('\u0000', ' ').trim().take(BIO_LIMIT)
        val cleanCover = coverUrl.trim().take(URL_LIMIT)
        val cleanAccent = accent.takeIf { id -> ColorPalette.entries.any { it.id == id } }
            ?: ColorPalette.OPENFY.id
        val cleanPins = pins
            .asSequence()
            .filter { it.id.isNotBlank() && it.title.isNotBlank() }
            .distinctBy { it.id }
            .take(PIN_LIMIT)
            .map { pin ->
                pin.copy(
                    id = pin.id.take(200),
                    title = pin.title.trim().take(200),
                    artist = pin.artist.trim().take(200),
                    album = pin.album.trim().take(200),
                    durationSec = pin.durationSec.coerceIn(0, 24 * 60 * 60),
                    thumbnail = pin.thumbnail.trim().take(URL_LIMIT),
                )
            }
            .toList()
        return copy(
            bio = cleanBio,
            coverUrl = cleanCover,
            accent = cleanAccent,
            statsVisibility = ProfileStatsVisibility.sanitize(statsVisibility),
            statsOrder = ProfileStatsCards.sanitizeOrder(statsOrder),
            statsHidden = ProfileStatsCards.sanitizeHidden(statsHidden),
            pins = cleanPins,
        )
    }

    companion object {
        const val BIO_LIMIT = 280
        const val PIN_LIMIT = 10
        const val URL_LIMIT = 2048
    }
}

@Serializable
data class ProfileStyle(
    val coverGradient: Boolean = true,
    val motion: Boolean = true,
    val glow: Boolean = true,
)

@Serializable
data class ProfilePin(
    val id: String,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val durationSec: Int = 0,
    val thumbnail: String = "",
) {
    fun toTrack(): Track = Track(
        id = id,
        title = title,
        artist = artist,
        album = album,
        durationSec = durationSec,
        thumbnail = thumbnail,
    )

    companion object {
        fun from(track: Track): ProfilePin = ProfilePin(
            id = track.id,
            title = track.title,
            artist = track.artist,
            album = track.album,
            durationSec = track.durationSec,
            thumbnail = track.thumbnail,
        )
    }
}

object ProfileStatsVisibility {
    const val PRIVATE = "private"
    const val FOLLOWERS = "followers"
    const val PUBLIC = "public"

    val all = listOf(PRIVATE, FOLLOWERS, PUBLIC)

    fun sanitize(value: String): String = value.takeIf { it in all } ?: PRIVATE

    fun label(value: String): String = when (sanitize(value)) {
        FOLLOWERS -> "Seguidores"
        PUBLIC -> "Público"
        else -> "Privado"
    }
}

object ProfileStatsCards {
    const val MINUTES = "minutes"
    const val PLAYS = "plays"
    const val LIKES = "likes"
    const val PLAYLISTS = "playlists"
    const val TOP_ARTISTS = "top_artists"
    const val TOP_TRACKS = "top_tracks"

    val all = listOf(MINUTES, PLAYS, LIKES, PLAYLISTS, TOP_ARTISTS, TOP_TRACKS)
    val defaultOrder = all

    fun label(id: String): String = when (id) {
        MINUTES -> "Minutos no mês"
        PLAYS -> "Execuções no mês"
        LIKES -> "Curtidas"
        PLAYLISTS -> "Playlists"
        TOP_ARTISTS -> "Top artistas"
        TOP_TRACKS -> "Top faixas"
        else -> id
    }

    fun sanitizeOrder(order: List<String>): List<String> {
        val valid = order.filter { it in all }.distinct()
        return valid + all.filter { it !in valid }
    }

    fun sanitizeHidden(hidden: List<String>): List<String> =
        hidden.filter { it in all }.distinct()
}

@Serializable
data class ProfileArtistStat(
    val name: String = "",
    val plays: Long = 0,
)

@Serializable
data class ProfileTrackStat(
    val track: Track,
    val plays: Long = 0,
)

@Serializable
data class ProfileStatsData(
    val month: String = "",
    val minutes: Long = 0,
    val plays: Long = 0,
    val uniqueTracks: Long = 0,
    val likes: Int = 0,
    val playlists: Int = 0,
    val topArtists: List<ProfileArtistStat> = emptyList(),
    val topTracks: List<ProfileTrackStat> = emptyList(),
    val updatedAt: Long = 0,
)
