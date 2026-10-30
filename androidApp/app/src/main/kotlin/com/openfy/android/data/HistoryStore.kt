package com.openfy.android.data

import com.openfy.core.PlaybackSignal
import com.openfy.core.Recommender
import com.openfy.core.Track
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** Uma escuta concluída, local ou recebida da conta. */
@Serializable
data class HistoryEntry(
    val id: String,
    val track: Track,
    val at: Long,
    val playedMs: Int = 0,
    val durationMs: Int = 0,
    val completed: Boolean = false,
    val skipped: Boolean = false,
    val source: String = "android",
)

/**
 * Histórico local em JSON. A conta é a fonte da verdade, mas escutas feitas
 * offline entram aqui na hora e são deduplicadas pelo id quando voltam.
 */
class HistoryStore(dir: File) {

    private val dir = dir.also { if (!it.isDirectory) it.mkdirs() }
    private val file = File(this.dir, "history.json")
    private val temp = File(this.dir, "history.json.tmp")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val lock = Any()
    private var entries: List<HistoryEntry> = load()

    private fun load(): List<HistoryEntry> {
        val text = runCatching { file.readText() }.getOrNull() ?: return emptyList()
        return runCatching { json.decodeFromString<List<HistoryEntry>>(text) }
            .getOrDefault(emptyList())
    }

    private fun persist() {
        runCatching {
            temp.writeText(json.encodeToString(entries))
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

    fun all(): List<HistoryEntry> = synchronized(lock) { entries }

    fun add(entry: HistoryEntry) = merge(listOf(entry))

    /** Mescla por id; um evento reaparece igual vindo da nuvem e não duplica. */
    fun merge(incoming: List<HistoryEntry>): List<HistoryEntry> {
        if (incoming.isEmpty()) return all()
        synchronized(lock) {
            val byId = LinkedHashMap<String, HistoryEntry>()
            entries.forEach { byId[it.id] = it }
            incoming.forEach { entry ->
                val previous = byId[entry.id]
                if (previous == null || entry.at >= previous.at) byId[entry.id] = entry
            }
            entries = byId.values
                .sortedWith(compareByDescending<HistoryEntry> { it.at }.thenBy { it.id })
                .take(MAX_ENTRIES)
            persist()
            return entries
        }
    }

    fun remove(ids: Collection<String>) {
        if (ids.isEmpty()) return
        synchronized(lock) {
            val remove = ids.toSet()
            entries = entries.filterNot { it.id in remove }
            persist()
        }
    }

    fun recentTracks(limit: Int = 24): List<Track> =
        historyTracks(limit)

    /** Faixas únicas, das mais recentes para as mais antigas. */
    fun historyTracks(limit: Int = 200): List<Track> = synchronized(lock) {
        val best = mutableMapOf<String, HistoryEntry>()
        entries.forEach { entry ->
            val current = best[entry.track.id]
            if (current == null || entry.at > current.at) best[entry.track.id] = entry
        }
        best.values
            .sortedByDescending { it.at }
            .map { it.track }
            .take(limit)
    }

    fun topPlayed(likedIds: Set<String>, limit: Int = 100): List<Track> {
        val snapshot = all()
        if (snapshot.isEmpty()) return emptyList()
        val tracks = snapshot.associate { it.track.id to it.track }
        return Recommender(signals(snapshot), likedIds).topPlayed(tracks, limit = limit)
    }

    fun signals(source: List<HistoryEntry> = all()): List<PlaybackSignal> = source.map {
        PlaybackSignal(
            trackId = it.track.id,
            at = it.at,
            playedMs = it.playedMs,
            durationMs = it.durationMs,
            completed = it.completed,
            skipped = it.skipped,
        )
    }

    fun clear() {
        synchronized(lock) {
            entries = emptyList()
            runCatching { file.delete() }
            runCatching { temp.delete() }
        }
    }

    companion object {
        private const val MAX_ENTRIES = 5_000
    }
}
