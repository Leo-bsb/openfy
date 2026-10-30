package com.openfy.core

import java.text.Normalizer
import kotlin.math.exp
import kotlin.math.ln
import kotlin.random.Random

/** Sinal de escuta mínimo para o recomendador Android. */
data class PlaybackSignal(
    val trackId: String,
    val at: Long,
    val playedMs: Int = 0,
    val durationMs: Int = 0,
    val completed: Boolean = false,
    val skipped: Boolean = false,
)

/**
 * Recomendação local compartilhada com o desktop, sem depender de SQLite.
 * Usa curtidas, frequência, recência, conclusão e skip para ordenar rádio,
 * mix e mais ouvidas.
 */
class Recommender(
    private val signals: List<PlaybackSignal>,
    private val likedIds: Set<String> = emptySet(),
    private val random: Random = Random.Default,
) {

    data class TrackStat(
        val plays: Int,
        val lastAt: Long,
        val completion: Double,
        val skipRate: Double,
        val liked: Boolean,
    )

    data class ScoredTrack(val track: Track, val score: Double)

    data class DiversityPolicy(
        val recentArtistWindow: Int = 6,
        val maxPerArtist: Int = 2,
        val maxPerAlbum: Int = 4,
        val avoidHours: Int = 6,
    )

    /** Mais ouvidas com peso por recência/completion e sem repetição. */
    fun topPlayed(
        tracksById: Map<String, Track>,
        limit: Int = 100,
        windowDays: Int = 365,
        policy: DiversityPolicy = DiversityPolicy(
            recentArtistWindow = 3,
            maxPerArtist = 3,
            maxPerAlbum = 5,
            avoidHours = 0,
        ),
    ): List<Track> {
        val stats = stats(windowDays)
        if (stats.isEmpty()) return emptyList()
        val scored = stats.entries.mapNotNull { (id, stat) ->
            tracksById[id]?.let { ScoredTrack(it, scoreOf(stat, id in likedIds || stat.liked)) }
        }.sortedByDescending { it.score }
        return selectDiverse(scored, emptyList(), emptySet(), policy).take(limit)
    }

    /** Ordena e filtra os candidatos de uma página de rádio. */
    fun rankRadio(
        candidates: List<Track>,
        seed: Track,
        existing: List<Track>,
        limit: Int = 20,
        policy: DiversityPolicy = DiversityPolicy(),
    ): List<Track> {
        if (candidates.isEmpty()) return emptyList()
        val stats = stats(windowDays = 180)
        val recent = signals.filter {
            it.at >= System.currentTimeMillis() - policy.avoidHours * 3_600_000L
        }
        val avoidIds = recent.mapTo(HashSet()) { it.trackId }
        val seedArtist = artistKey(seed)
        val now = System.currentTimeMillis()

        val scored = candidates.mapIndexedNotNull { index, track ->
            val stat = stats[track.id]
            val affinity = if (stat == null) 0.45 else scoreOf(stat, track.id in likedIds)
            val sameArtist = if (artistKey(track) == seedArtist) 1.0 else 0.35
            val novelty = when {
                stat == null -> 1.0
                now - stat.lastAt > 30L * 86_400_000L -> 0.65
                else -> 0.2
            }
            val rankScore = 1.0 / (1.0 + index / 10.0)
            val score = 0.50 * affinity +
                0.20 * sameArtist +
                0.15 * novelty +
                0.10 * rankScore +
                0.05 * random.nextDouble()
            ScoredTrack(track, score)
        }.sortedByDescending { it.score }

        return selectDiverse(scored, existing, avoidIds, policy).take(limit)
    }

    /** Mistura várias sementes sem deixar uma rádio dominar a lista. */
    fun interleave(
        pages: List<List<Track>>,
        limit: Int = 60,
        policy: DiversityPolicy = DiversityPolicy(recentArtistWindow = 4, maxPerArtist = 2),
    ): List<Track> {
        val queues = pages.map { it.toMutableList() }
        val scored = mutableListOf<ScoredTrack>()
        var added = true
        var round = 0
        while (added && scored.size < limit * 3) {
            added = false
            queues.forEach { queue ->
                val next = queue.removeFirstOrNull() ?: return@forEach
                scored += ScoredTrack(next, 1.0 - (round % 10) * 0.01)
                added = true
            }
            round++
        }
        return selectDiverse(scored, emptyList(), emptySet(), policy).take(limit)
    }

    fun stats(windowDays: Int = 180): Map<String, TrackStat> {
        val since = System.currentTimeMillis() - windowDays * 86_400_000L
        val rows = signals.filter { it.at >= since }
        if (rows.isEmpty()) return emptyMap()
        return rows.groupBy { it.trackId }.mapValues { (id, events) ->
            val plays = events.size
            val completed = events.count { it.completed }
            val skipped = events.count { it.skipped }
            val completion = events.sumOf { event ->
                if (event.durationMs > 0) {
                    (event.playedMs.toDouble() / event.durationMs).coerceIn(0.0, 1.0)
                } else if (event.completed) {
                    1.0
                } else {
                    0.2
                }
            } / plays
            TrackStat(
                plays = plays,
                lastAt = events.maxOf { it.at },
                completion = completion,
                skipRate = skipped.toDouble() / plays,
                liked = id in likedIds,
            )
        }
    }

    private fun scoreOf(stat: TrackStat, liked: Boolean): Double {
        val ageDays = ((System.currentTimeMillis() - stat.lastAt).coerceAtLeast(0L)) / 86_400_000.0
        val recency = exp(-ageDays / 45.0)
        val frequency = (ln(1.0 + stat.plays) / ln(21.0)).coerceIn(0.0, 1.0)
        return 0.30 * (if (liked || stat.liked) 1.0 else 0.0) +
            0.25 * recency +
            0.20 * stat.completion.coerceIn(0.0, 1.0) +
            0.15 * (1.0 - stat.skipRate.coerceIn(0.0, 1.0)) +
            0.10 * frequency
    }

    private fun selectDiverse(
        scored: List<ScoredTrack>,
        existing: List<Track>,
        avoidIds: Set<String>,
        policy: DiversityPolicy,
    ): List<Track> {
        val out = mutableListOf<Track>()
        val ids = existing.mapTo(HashSet()) { it.id }
        val keys = existing.mapTo(HashSet()) { canonicalKey(it) }
        val artists = ArrayDeque(existing.takeLast(policy.recentArtistWindow).map { artistKey(it) })
        val artistCounts = mutableMapOf<String, Int>()
        val albumCounts = mutableMapOf<String, Int>()

        fun add(track: Track): Boolean {
            val id = track.id
            val key = canonicalKey(track)
            if (id in ids || key in keys || id in avoidIds) return false
            val artist = artistKey(track)
            val album = albumKey(track)
            ids += id
            keys += key
            out += track
            artists.addLast(artist)
            while (artists.size > policy.recentArtistWindow) artists.removeFirst()
            artistCounts[artist] = (artistCounts[artist] ?: 0) + 1
            albumCounts[album] = (albumCounts[album] ?: 0) + 1
            return true
        }

        fun pass(relaxArtistGap: Boolean, relaxCaps: Boolean) {
            for (candidate in scored) {
                val track = candidate.track
                if (track.id in ids || canonicalKey(track) in keys || track.id in avoidIds) continue
                val artist = artistKey(track)
                val album = albumKey(track)
                if (!relaxArtistGap && artist in artists) continue
                if (!relaxCaps && (artistCounts[artist] ?: 0) >= policy.maxPerArtist) continue
                if (!relaxCaps && (albumCounts[album] ?: 0) >= policy.maxPerAlbum) continue
                add(track)
            }
        }

        pass(relaxArtistGap = false, relaxCaps = false)
        pass(relaxArtistGap = true, relaxCaps = false)
        pass(relaxArtistGap = true, relaxCaps = true)
        return out
    }

    companion object {
        fun canonicalKey(track: Track): String = titleKey(track.title) + "|" + artistKey(track)

        fun artistKey(track: Track): String {
            var value = track.artist
            value = value.substringBefore(",")
            value = value.substringBefore("&")
            value = Regex("(?i)\\b(feat|ft|featuring)\\b.*").replace(value, " ")
            return normalize(value)
        }

        fun albumKey(track: Track): String =
            if (track.album.isBlank()) "" else normalize(track.album)

        fun titleKey(title: String): String {
            val original = title
            var value = title
            value = value.replace(Regex("\\([^)]*\\)"), " ")
            value = value.replace(Regex("\\[[^]]*]"), " ")
            value = value.substringBefore(" - ")
            value = value.replace(Regex("(?i)\\b(feat|ft|featuring)\\b.*"), " ")
            value = value.replace(
                Regex("(?i)\\s+(remaster(ed)?|live|official video|official audio|video oficial)\\s*$"),
                " ",
            )
            val key = normalize(value)
            return key.ifBlank { normalize(original) }
        }

        private fun normalize(value: String): String {
            val decomposed = Normalizer.normalize(value, Normalizer.Form.NFD)
            return decomposed
                .replace(Regex("\\p{M}+"), "")
                .lowercase()
                .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
                .trim()
                .replace(Regex("\\s+"), " ")
        }
    }
}
