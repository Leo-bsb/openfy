package com.openfy.core

import kotlinx.serialization.Serializable

/** Backup local de playlists e curtidas (metadados, sem audio). */
@Serializable
data class Backup(
    val version: Int = 1,
    val exportedAt: Long = 0,
    val playlists: List<BackupPlaylist> = emptyList(),
    val liked: List<Track> = emptyList(),
)

@Serializable
data class BackupPlaylist(
    val name: String,
    val tracks: List<Track> = emptyList(),
)

/**
 * Playlist exportada para outra pessoa que tambem usa o Openfy.
 * Arquivo .openfyplaylist: metadados e videoIds, sem audio.
 */
@Serializable
data class SharedPlaylist(
    val version: Int = 1,
    val name: String = "",
    val exportedAt: Long = 0,
    val tracks: List<Track> = emptyList(),
)
