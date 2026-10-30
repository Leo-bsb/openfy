package com.openfy.android.data

import com.openfy.core.Track
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/** Evento de escuta que ainda vai subir para a conta (ou que subiu e já voltou). */
@Serializable
data class PendingEvent(
    val id: String,
    val track: Track,
    val startedAtMs: Long,
    val playedMs: Int,
    val durationMs: Int,
    val completed: Boolean,
    val skipped: Boolean,
    val source: String,
    val contextId: String? = null,
)

/** Curtida/descurtida offline. O maior updatedAt vence. */
@Serializable
data class PendingLike(
    val track: Track,
    val liked: Boolean,
    val updatedAt: Long,
)

/** Alteração local de playlist (criar, renomear, capa ou excluir). */
@Serializable
data class PendingPlaylist(
    val id: String,
    val name: String,
    val coverUrl: String = "",
    val updatedAt: Long,
    val deleted: Boolean = false,
)

/** Item de playlist alterado offline: inclusão, remoção ou nova posição. */
@Serializable
data class PendingItem(
    val playlistId: String,
    val trackId: String,
    val itemId: String? = null,
    val position: Int,
    val updatedAt: Long,
    val deleted: Boolean = false,
    val track: Track? = null,
)

@Serializable
private data class Outbox(
    val events: List<PendingEvent> = emptyList(),
    val likes: List<PendingLike> = emptyList(),
    val playlists: List<PendingPlaylist> = emptyList(),
    val items: List<PendingItem> = emptyList(),
    val eventCursor: String? = null,
)

/**
 * Fila de operações do Android. O app não tem banco relacional; este arquivo
 * garante que escutas, curtidas e mudanças de playlist feitas sem rede subam
 * depois, com ids estáveis, sem duplicar nem ressuscitar exclusões.
 */
class SyncStore(dir: File) {

    private val dir = dir.also { if (!it.isDirectory) it.mkdirs() }
    private val file = File(this.dir, "outbox.json")
    private val temp = File(this.dir, "outbox.json.tmp")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val lock = Any()
    private var state: Outbox = load()

    private fun load(): Outbox {
        val text = runCatching { file.readText() }.getOrNull() ?: return Outbox()
        return runCatching { json.decodeFromString<Outbox>(text) }.getOrDefault(Outbox())
    }

    private fun persist() {
        runCatching {
            temp.writeText(json.encodeToString(state))
            runCatching {
                java.nio.file.Files.move(
                    temp.toPath(),
                    file.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                )
            }.onFailure {
                java.nio.file.Files.move(
                    temp.toPath(),
                    file.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                )
            }
        }
    }

    // ------------------------------------------------------------------ eventos
    fun newEventId(): String = UUID.randomUUID().toString()

    fun addEvent(event: PendingEvent) {
        synchronized(lock) {
            val merged = (state.events.filterNot { it.id == event.id } + event)
                .sortedWith(compareBy({ it.startedAtMs }, { it.id }))
                .takeLast(MAX_EVENTS)
            state = state.copy(events = merged)
            persist()
        }
    }

    fun pendingEvents(): List<PendingEvent> = synchronized(lock) {
        state.events.sortedWith(compareBy({ it.startedAtMs }, { it.id }))
    }

    fun removeEvents(ids: Collection<String>) {
        if (ids.isEmpty()) return
        synchronized(lock) {
            val remove = ids.toSet()
            state = state.copy(events = state.events.filterNot { it.id in remove })
            persist()
        }
    }

    fun eventCursor(): String? = synchronized(lock) { state.eventCursor }

    fun setEventCursor(cursor: String?) {
        synchronized(lock) {
            state = state.copy(eventCursor = cursor)
            persist()
        }
    }

    // ------------------------------------------------------------------ curtidas
    fun queueLike(track: Track, liked: Boolean, updatedAt: Long = System.currentTimeMillis()) {
        synchronized(lock) {
            val existing = state.likes.firstOrNull { it.track.id == track.id }
            val entry = if (existing != null && existing.updatedAt > updatedAt) {
                existing
            } else {
                PendingLike(track, liked, updatedAt)
            }
            state = state.copy(
                likes = state.likes.filterNot { it.track.id == track.id } + entry,
            )
            persist()
        }
    }

    fun pendingLikes(): List<PendingLike> = synchronized(lock) { state.likes }

    fun removeLikes(trackIds: Collection<String>) {
        if (trackIds.isEmpty()) return
        synchronized(lock) {
            val remove = trackIds.toSet()
            state = state.copy(likes = state.likes.filterNot { it.track.id in remove })
            persist()
        }
    }

    // ------------------------------------------------------------------ playlists
    fun queuePlaylist(
        id: String,
        name: String,
        coverUrl: String = "",
        updatedAt: Long = System.currentTimeMillis(),
        deleted: Boolean = false,
    ) {
        synchronized(lock) {
            val existing = state.playlists.firstOrNull { it.id == id }
            val entry = if (existing != null && existing.updatedAt > updatedAt) {
                existing
            } else {
                PendingPlaylist(id, name, coverUrl, updatedAt, deleted)
            }
            state = state.copy(
                playlists = state.playlists.filterNot { it.id == id } + entry,
            )
            persist()
        }
    }

    fun pendingPlaylists(): List<PendingPlaylist> = synchronized(lock) { state.playlists }

    fun removePlaylists(ids: Collection<String>) {
        if (ids.isEmpty()) return
        synchronized(lock) {
            val remove = ids.toSet()
            state = state.copy(playlists = state.playlists.filterNot { it.id in remove })
            persist()
        }
    }

    // ------------------------------------------------------------------ itens
    fun queueItem(
        playlistId: String,
        trackId: String,
        itemId: String?,
        position: Int,
        updatedAt: Long = System.currentTimeMillis(),
        deleted: Boolean = false,
        track: Track? = null,
    ) {
        synchronized(lock) {
            val key = playlistId to trackId
            val existing = state.items.firstOrNull { it.playlistId to it.trackId == key }
            val entry = if (existing != null && existing.updatedAt > updatedAt) {
                existing
            } else {
                PendingItem(
                    playlistId = playlistId,
                    trackId = trackId,
                    itemId = itemId ?: existing?.itemId,
                    position = position,
                    updatedAt = updatedAt,
                    deleted = deleted,
                    track = track ?: existing?.track,
                )
            }
            state = state.copy(
                items = state.items.filterNot { it.playlistId to it.trackId == key } + entry,
            )
            persist()
        }
    }

    fun pendingItems(): List<PendingItem> = synchronized(lock) { state.items }

    fun removeItems(keys: Collection<Pair<String, String>>) {
        if (keys.isEmpty()) return
        synchronized(lock) {
            val remove = keys.toSet()
            state = state.copy(
                items = state.items.filterNot { it.playlistId to it.trackId in remove },
            )
            persist()
        }
    }

    // ------------------------------------------------------------------ ciclo de vida
    fun pendingCount(): Int = synchronized(lock) {
        state.events.size + state.likes.size + state.playlists.size + state.items.size
    }

    fun clear() {
        synchronized(lock) {
            state = Outbox()
            runCatching { file.delete() }
            runCatching { temp.delete() }
        }
    }

    companion object {
        private const val MAX_EVENTS = 2_000
    }
}
