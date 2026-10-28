package com.openfy.android.data

import com.openfy.core.Track
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** Playlist já aberta online, guardada para abrir e tocar sem rede. */
@Serializable
data class CachedPlaylist(
    val id: String = "",
    val tracks: List<Track> = emptyList(),
    val items: List<RemotePlaylistItem> = emptyList(),
    val savedAtMs: Long = 0L,
)

class PlaylistCache(dir: File) {

    private val dir = dir.also { if (!it.isDirectory) it.mkdirs() }
    private val file = File(this.dir, "playlists.json")
    private val temp = File(this.dir, "playlists.json.tmp")
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()
    private var cache: Map<String, CachedPlaylist> = load()

    private fun load(): Map<String, CachedPlaylist> {
        val text = runCatching { file.readText() }.getOrNull() ?: return emptyMap()
        return runCatching { json.decodeFromString<Map<String, CachedPlaylist>>(text) }
            .getOrDefault(emptyMap())
    }

    fun get(playlistId: String): CachedPlaylist? = synchronized(lock) { cache[playlistId] }

    @Synchronized
    fun put(playlist: CachedPlaylist) {
        synchronized(lock) {
            cache = cache + (playlist.id to playlist)
            persist()
        }
    }

    fun clear() {
        synchronized(lock) {
            cache = emptyMap()
            runCatching { file.delete() }
            runCatching { temp.delete() }
        }
    }

    private fun persist() {
        runCatching {
            temp.writeText(json.encodeToString(cache))
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
}
