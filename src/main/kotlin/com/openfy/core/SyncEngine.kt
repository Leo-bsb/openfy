package com.openfy.core

import com.openfy.data.Db
import com.openfy.data.PushLike
import com.openfy.data.PushPlayEvent
import com.openfy.data.PushPlaylist
import com.openfy.data.PushPlaylistItem
import com.openfy.data.PushTrack
import com.openfy.data.RemoteLike
import com.openfy.data.RemotePlayEvent
import com.openfy.data.RemotePlaylist
import com.openfy.data.RemotePlaylistItem
import com.openfy.data.RemoteTrack
import com.openfy.data.SupabaseApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.OffsetDateTime
import java.util.Locale
import java.util.UUID

/**
 * Sync por conta com merge LWW e outbox local.
 *
 * Regras que evitam os bugs antigos:
 *  - tombstones remotos descem (playlist, item, curtida e evento);
 *  - só o que mudou neste aparelho sobe (`dirty=1`), então um push não
 *    ressuscita uma playlist que o outro aparelho apagou;
 *  - eventos de escuta sobem com id estável e descem de volta, o que dá ao
 *    recomendador e ao histórico a mesma base no PC e no Android;
 *  - a ordem das faixas usa o rank remoto, não o índice do momento.
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

        val remoteEvents = fetchPlayEvents(token)
        val trackIds = (
            remoteItems.map { it.trackId } +
                remoteLikes.map { it.trackId } +
                remoteEvents.map { it.trackId }
            ).toSet()
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
            remoteEvents.forEach {
                db.mergeRemotePlayEvent(
                    RemotePlayEventRow(
                        eventId = it.id,
                        trackId = it.trackId,
                        startedAt = parseTimestamp(it.startedAt) ?: System.currentTimeMillis(),
                        playedMs = it.playedMs,
                        durationMs = it.durationMs,
                        completed = it.completed,
                        skipped = it.skipped,
                        origin = it.source.ifBlank { "remote" },
                        contextId = it.contextId,
                        deleted = it.deleted,
                    ),
                )
            }
        }

        // Só o que mudou neste aparelho. O pull acima já pode ter limpado a
        // marca de linhas em que a nuvem venceu o LWW.
        val localTracks = withContext(Dispatchers.IO) { db.syncTracks() }
        val localPlaylists = withContext(Dispatchers.IO) { db.dirtyPlaylists() }
        val localItems = withContext(Dispatchers.IO) { db.dirtyPlaylistItems() }
        val localLikes = withContext(Dispatchers.IO) { db.dirtyLikes() }

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

        if (localPlaylists.isNotEmpty()) {
            api.pushPlaylists(
                token,
                localPlaylists.map { PushPlaylist(it.id, it.name, it.deleted, it.coverUrl) },
            )
            withContext(Dispatchers.IO) {
                localPlaylists.forEach { db.clearPlaylistDirty(it.id, it.updatedAt) }
            }
        }

        if (localItems.isNotEmpty()) {
            val pushedItems = localItems.map { item ->
                val remoteId = item.remoteItemId ?: deterministicItemId(item.playlistId, item.trackId)
                item to PushPlaylistItem(
                    id = remoteId,
                    playlistId = item.playlistId,
                    trackId = item.trackId,
                    rank = rankFor(item.position),
                    deleted = item.deleted,
                )
            }
            api.pushItems(token, pushedItems.map { it.second })
            withContext(Dispatchers.IO) {
                pushedItems.forEach { (item, pushed) ->
                    db.setRemoteItemId(item.playlistId, item.trackId, pushed.id)
                    db.clearPlaylistItemDirty(item.playlistId, item.trackId, item.updatedAt)
                }
            }
        }

        if (localLikes.isNotEmpty()) {
            api.pushLikes(
                token,
                localLikes.map { PushLike(session.userId, it.trackId, it.deleted) },
            )
            withContext(Dispatchers.IO) {
                localLikes.forEach { db.clearLikeDirty(it.trackId, it.updatedAt) }
            }
        }

        val pendingEvents = withContext(Dispatchers.IO) { db.pendingPlayEvents() }
        if (pendingEvents.isNotEmpty()) {
            api.pushPlayEvents(
                token,
                pendingEvents.map { event ->
                    PushPlayEvent(
                        id = event.eventId.ifBlank { UUID.randomUUID().toString() },
                        userId = session.userId,
                        trackId = event.trackId,
                        startedAt = Instant.ofEpochMilli(event.startedAt).toString(),
                        playedMs = event.playedMs,
                        durationMs = event.durationMs,
                        completed = event.completed,
                        skipped = event.skipped,
                        source = event.origin,
                        contextId = event.contextId,
                    )
                },
            )
            withContext(Dispatchers.IO) {
                db.markPlayEventsSynced(pendingEvents.map { it.seq })
            }
        }

        return SyncResult(
            remotePlaylists = remotePlaylists.count { !it.deleted },
            remoteItems = remoteItems.count { !it.deleted },
            remoteLikes = remoteLikes.count { !it.deleted },
            remoteEvents = remoteEvents.size,
            pushedPlaylists = localPlaylists.size,
            pushedItems = localItems.size,
            pushedLikes = localLikes.size,
            pushedEvents = pendingEvents.size,
        )
    }

    /**
     * Puxa eventos em páginas. O cursor é o updated_at do servidor; assim um
     * evento enviado offline pelo Android depois entra na próxima página mesmo
     * que started_at seja antigo.
     */
    private suspend fun fetchPlayEvents(token: String): List<RemotePlayEvent> {
        val cursor = db.get("sync.playEventsCursor")
        val first = api.playEvents(token, cursor, limit = if (cursor.isNullOrBlank()) 5000 else 1000)
        val result = first.toMutableList()
        var nextCursor = first.lastOrNull()?.updatedAt
        var pages = 1
        while (!cursor.isNullOrBlank() && first.size >= 1000 && pages < 5 && !nextCursor.isNullOrBlank()) {
            val page = api.playEvents(token, nextCursor, limit = 1000)
            if (page.isEmpty()) break
            result += page
            nextCursor = page.lastOrNull()?.updatedAt ?: nextCursor
            pages++
        }
        val newest = result.mapNotNull { it.updatedAt }.maxOrNull()
        if (!newest.isNullOrBlank()) db.put("sync.playEventsCursor", newest)
        return result
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
        updatedAt = parseTimestamp(updatedAt) ?: System.currentTimeMillis(),
        deleted = deleted,
        coverUrl = coverUrl,
    )

    private fun RemotePlaylistItem.toSyncItem(position: Int) = SyncPlaylistItem(
        playlistId = playlistId,
        trackId = trackId,
        position = position,
        updatedAt = parseTimestamp(updatedAt) ?: System.currentTimeMillis(),
        deleted = deleted,
        remoteItemId = id,
    )

    private fun RemoteLike.toSyncLike() = SyncLike(
        trackId = trackId,
        updatedAt = parseTimestamp(updatedAt) ?: System.currentTimeMillis(),
        deleted = deleted,
    )

    private fun parseTimestamp(value: String?): Long? =
        runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }.getOrNull()

    private fun deterministicItemId(playlistId: String, trackId: String): String =
        UUID.nameUUIDFromBytes("openfy:$playlistId:$trackId".toByteArray(Charsets.UTF_8)).toString()

    private fun rankFor(position: Int): String =
        String.format(Locale.ROOT, "%08d", position)
}
