package com.openfy.core

import kotlinx.serialization.Serializable

@Serializable
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val durationSec: Int = 0,
    val thumbnail: String = "",
)

data class Playlist(
    val id: String,
    val name: String,
    val count: Int = 0,
    val cover: String = "",
)

enum class RepeatMode { OFF, ALL, ONE }

/** Abas da busca. Os filtros do YouTube Music evitam misturar karaokê/podcast nos resultados. */
enum class SearchMode(val label: String) {
    SONGS("Músicas"),
    ARTISTS("Artistas"),
    ALBUMS("Álbuns"),
    PLAYLISTS("Playlists"),
    VIDEOS("Vídeos"),
}

enum class MediaKind { ARTIST, ALBUM, PLAYLIST, TRACK }

/** Artista oficial retornado pela busca, por uma página de artista ou pelas paradas. */
data class Artist(
    val id: String,
    val name: String,
    val thumbnail: String = "",
    val subtitle: String = "",
)

/** Item de prateleira (álbum, playlist, artista ou vídeo). */
data class MediaCard(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val thumbnail: String = "",
    val kind: MediaKind,
)

data class MediaSection(
    val title: String,
    val cards: List<MediaCard> = emptyList(),
)

data class SearchResult(
    val tracks: List<Track> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val cards: List<MediaCard> = emptyList(),
    val continuation: String? = null,
)

data class ArtistPage(
    val id: String,
    val name: String,
    val avatar: String = "",
    val banner: String = "",
    val listeners: String = "",
    val description: String = "",
    val topTracks: List<Track> = emptyList(),
    val sections: List<MediaSection> = emptyList(),
    val radioVideoId: String? = null,
    val radioPlaylistId: String? = null,
)

enum class CollectionKind { ALBUM, PLAYLIST }

data class RemoteCollection(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val description: String = "",
    val thumbnail: String = "",
    val kind: CollectionKind = CollectionKind.PLAYLIST,
    val tracks: List<Track> = emptyList(),
    val continuation: String? = null,
)

data class RemoteTracksPage(
    val tracks: List<Track> = emptyList(),
    val continuation: String? = null,
)

data class ExploreFeed(
    val sections: List<MediaSection> = emptyList(),
    val topArtists: List<Artist> = emptyList(),
)

data class RadioPage(
    val tracks: List<Track> = emptyList(),
    val continuation: String? = null,
)

fun formatTime(seconds: Double): String {
    if (seconds.isNaN() || seconds < 0) return "0:00"
    val total = seconds.toInt()
    val m = total / 60
    val s = total % 60
    return "%d:%02d".format(m, s)
}
