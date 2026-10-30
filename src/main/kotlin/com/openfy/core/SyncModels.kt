package com.openfy.core

/** Linha de playlist pronta para o sync (inclui tombstones). */
data class SyncPlaylist(
    val id: String,
    val name: String,
    val updatedAt: Long,
    val deleted: Boolean,
    val coverUrl: String = "",
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

/** Evento de escuta local aguardando envio ao recap da conta. */
data class PendingPlayEvent(
    val seq: Int,
    val eventId: String,
    val trackId: String,
    val startedAt: Long,
    val playedMs: Int,
    val durationMs: Int,
    val completed: Boolean,
    val skipped: Boolean,
    val origin: String,
    val contextId: String?,
    val contextTitle: String,
)

/** Evento de escuta vindo do Supabase (PC ou Android). */
data class RemotePlayEventRow(
    val eventId: String,
    val trackId: String,
    val startedAt: Long,
    val playedMs: Int,
    val durationMs: Int,
    val completed: Boolean,
    val skipped: Boolean,
    val origin: String,
    val contextId: String?,
    val deleted: Boolean,
)

/** Linha bruta usada pelo recomendador e pelo recap. */
data class PlayEventRow(
    val trackId: String,
    val at: Long,
    val playedMs: Int,
    val durationMs: Int,
    val completed: Boolean,
    val skipped: Boolean,
    val origin: String,
    val contextId: String?,
    val contextTitle: String,
)

data class SyncResult(
    val remotePlaylists: Int = 0,
    val remoteItems: Int = 0,
    val remoteLikes: Int = 0,
    val remoteEvents: Int = 0,
    val pushedPlaylists: Int = 0,
    val pushedItems: Int = 0,
    val pushedLikes: Int = 0,
    val pushedEvents: Int = 0,
) {
    fun summary(): String =
        "Sync: $remotePlaylists playlists, $remoteItems itens, $remoteLikes curtidas e " +
            "$remoteEvents escutas recebidos; $pushedPlaylists playlists, $pushedItems itens, " +
            "$pushedLikes curtidas e $pushedEvents escutas enviados."
}
