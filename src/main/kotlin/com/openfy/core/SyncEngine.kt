package com.openfy.core

import com.openfy.data.Db
import com.openfy.data.PushLike
import com.openfy.data.PushPlaylist
import com.openfy.data.PushPlaylistItem
import com.openfy.data.PushTrack
import com.openfy.data.RemoteLike
import com.openfy.data.RemotePlaylist
import com.openfy.data.RemotePlaylistItem
import com.openfy.data.RemoteTrack
import com.openfy.data.SupabaseApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.OffsetDateTime
import java.util.Locale
import java.util.UUID

/**
 * Sync inicial: pull do servidor, merge no SQLite local e push do que existe
 * localmente. A comparação é por updated_at (LWW); a evolução para outbox +
 * server_rev fica para o próximo passo.
 */
class SyncEngine(
    private val db: Db,
    private val api: SupabaseApi,
) {

    suspend fun sync(session: StoredSession): SyncResult {
        val token = session.accessToken
        val playlistIds = (api.memberPlaylistIds(token, session.userId) +
            api.ownedPlaylistIds(token, session.userId)).toSet()

        val remotePlaylists = playlistIds.chunked(50)
            .flatMap { api.playlists(token, it) }
        val remoteItems = playlistIds.chunked(50)
            .flatMap { api.items(token, it) }
        val remoteLikes = api.likes(token)

        val trackIds = (remoteItems.map { it.trackId } + remoteLikes.map { it.trackId }).toSet()
        val remoteTracks = trackIds.chunked(80)
            .flatMap { api.tracks(token, it) }

        withContext(Dispatchers.IO) {
            remoteTracks.forEach { db.remember(it.toTrack()) }
            remotePlaylists.forEach { db.mergeRemotePlaylist(it.toSyncPlaylist()) }
            remoteItems
                .groupBy { it.playlistId }
                .forEach { (_, items) ->
                    items.sortedWith(compareBy({ it.rank }, { it.id }))
                        .forEachIndexed { index, item ->
                            db.mergeRemotePlaylistItem(item.toSyncItem(index))
                        }
                }
            remoteLikes.forEach { db.mergeRemoteLike(it.toSyncLike()) }
        }

        val localTracks = withContext(Dispatchers.IO) { db.syncTracks() }
        val localPlaylists = withContext(Dispatchers.IO) { db.syncPlaylists() }
        val localItems = withContext(Dispatchers.IO) { db.syncPlaylistItems() }
        val localLikes = withContext(Dispatchers.IO) { db.syncLikes() }

        api.pushTracks(
            token,
            localTracks.map {
                PushTrack(
                    id = it.id,
                    title = it.title,
                    artist = it.artist,
                    album = it.album,
                    duration = it.durationSec,
                    thumbnail = it.thumbnail,
                )
            },
        )
        api.pushPlaylists(
            token,
            localPlaylists.map { PushPlaylist(it.id, it.name, it.deleted) },
        )

        val pushedItems = localItems.map { item ->
            PushPlaylistItem(
                id = item.remoteItemId ?: deterministicItemId(item.playlistId, item.trackId),
                playlistId = item.playlistId,
                trackId = item.trackId,
                rank = rankFor(item.position),
                deleted = item.deleted,
            )
        }
        api.pushItems(token, pushedItems)
        api.pushLikes(
            token,
            localLikes.map { PushLike(session.userId, it.trackId, it.deleted) },
        )

        withContext(Dispatchers.IO) {
            localItems.forEach { item ->
                if (item.remoteItemId == null) {
                    db.setRemoteItemId(
                        item.playlistId,
                        item.trackId,
                        deterministicItemId(item.playlistId, item.trackId),
                    )
                }
            }
        }

        return SyncResult(
            remotePlaylists = remotePlaylists.size,
            remoteItems = remoteItems.size,
            remoteLikes = remoteLikes.size,
            pushedPlaylists = localPlaylists.size,
            pushedItems = localItems.size,
            pushedLikes = localLikes.size,
        )
    }

    private fun RemoteTrack.toTrack() = Track(
        id = id,
        title = title,
        artist = artist,
        album = album,
        durationSec = duration,
        thumbnail = thumbnail,
    )

    private fun RemotePlaylist.toSyncPlaylist() = SyncPlaylist(
        id = id,
        name = name,
        updatedAt = parseTimestamp(updatedAt),
        deleted = deleted,
    )

    private fun RemotePlaylistItem.toSyncItem(position: Int) = SyncPlaylistItem(
        playlistId = playlistId,
        trackId = trackId,
        position = position,
        updatedAt = parseTimestamp(updatedAt),
        deleted = deleted,
        remoteItemId = id,
    )

    private fun RemoteLike.toSyncLike() = com.openfy.core.SyncLike(
        trackId = trackId,
        updatedAt = parseTimestamp(updatedAt),
        deleted = deleted,
    )

    private fun parseTimestamp(value: String?): Long =
        runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }
            .getOrDefault(System.currentTimeMillis())

    private fun deterministicItemId(playlistId: String, trackId: String): String =
        UUID.nameUUIDFromBytes("openfy:$playlistId:$trackId".toByteArray(Charsets.UTF_8)).toString()

    private fun rankFor(position: Int): String =
        String.format(Locale.ROOT, "%08d", position)
}
