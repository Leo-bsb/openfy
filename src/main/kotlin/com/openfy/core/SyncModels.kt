package com.openfy.core

/** Linha de playlist pronta para o sync (inclui tombstones). */
data class SyncPlaylist(
    val id: String,
    val name: String,
    val updatedAt: Long,
    val deleted: Boolean,
)

/** Item de playlist no formato local. O id remoto é preservado quando existe. */
data class SyncPlaylistItem(
    val playlistId: String,
    val trackId: String,
    val position: Int,
    val updatedAt: Long,
    val deleted: Boolean,
    val remoteItemId: String? = null,
)

/** Curtida local pronta para o sync (inclui tombstones). */
data class SyncLike(
    val trackId: String,
    val updatedAt: Long,
    val deleted: Boolean,
)

data class SyncResult(
    val remotePlaylists: Int = 0,
    val remoteItems: Int = 0,
    val remoteLikes: Int = 0,
    val pushedPlaylists: Int = 0,
    val pushedItems: Int = 0,
    val pushedLikes: Int = 0,
) {
    fun summary(): String =
        "Sync: $remotePlaylists playlists, $remoteItems itens e $remoteLikes curtidas recebidos; " +
            "$pushedPlaylists playlists, $pushedItems itens e $pushedLikes curtidas enviados."
}
