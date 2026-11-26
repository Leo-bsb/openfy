package com.openfy.android.data

import com.openfy.core.Track
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/**
 * Recap calculado no proprio aparelho a partir do histórico local.
 *
 * O Android antes só mostrava o recap vindo do Supabase. Se o sync atrasava,
 * o aparelho ficava sem rede ou um evento não subia, a tela parava de atualizar
 * (e no modo offline ficava vazia para sempre). O histórico local já contém as
 * escutas deste aparelho e as que desceram da conta, então ele é uma fonte
 * imediata; o servidor continua sendo usado quando tem números maiores.
 */
object LocalRecap {

    data class Snapshot(
        val month: String,
        val listening: MonthlyListeningRow,
        val topTracks: List<Track>,
        val artists: List<MonthlyArtistRow>,
        val months: List<String>,
    )

    fun months(entries: List<HistoryEntry>, limit: Int = 12): List<String> =
        entries.asSequence()
            .filter { it.at > 0 }
            .map { monthOf(it.at) }
            .distinct()
            .sortedDescending()
            .take(limit)
            .toList()

    fun build(entries: List<HistoryEntry>, month: String): Snapshot {
        val yearMonth = runCatching { YearMonth.parse(month.take(7)) }
            .getOrElse { YearMonth.now() }
        val zone = ZoneId.systemDefault()
        val start = yearMonth.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = yearMonth.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val inMonth = entries.filter { it.at >= start && it.at < end }

        var playedMs = 0L
        var completed = 0L
        var skipped = 0L
        val byTrack = LinkedHashMap<String, TrackPlays>()
        val byArtist = LinkedHashMap<String, ArtistPlays>()
        inMonth.forEach { entry ->
            val durationMs = entry.durationMs.coerceAtLeast(0)
            val listened = entry.playedMs.coerceAtLeast(0).let { if (it > 0) it else 0 }
            val effective = listened.takeIf { it > 0 } ?: if (entry.skipped) 0 else durationMs
            playedMs += effective
            if (entry.completed) completed++
            if (entry.skipped) skipped++
            val row = byTrack.getOrPut(entry.track.id) { TrackPlays(entry.track) }
            row.plays++
            row.playedMs += effective
            val artist = entry.track.artist.trim().ifBlank { "Artista desconhecido" }
            val artistRow = byArtist.getOrPut(artist) { ArtistPlays(artist) }
            artistRow.plays++
            artistRow.playedMs += effective
        }

        val topTracks = byTrack.values
            .sortedWith(compareByDescending<TrackPlays> { it.plays }.thenByDescending { it.playedMs })
            .take(10)
            .map { it.track }
        val artists = byArtist.values
            .sortedWith(compareByDescending<ArtistPlays> { it.plays }.thenByDescending { it.playedMs })
            .take(8)
            .map { MonthlyArtistRow(it.name, it.plays, it.playedMs) }

        return Snapshot(
            month = month,
            listening = MonthlyListeningRow(
                month = month,
                playedMs = playedMs,
                plays = inMonth.size.toLong(),
                uniqueTracks = byTrack.size.toLong(),
                completedPlays = completed,
                skippedPlays = skipped,
            ),
            topTracks = topTracks,
            artists = artists,
            months = months(entries),
        )
    }

    fun monthOf(atMs: Long): String {
        val date = Instant.ofEpochMilli(atMs).atZone(ZoneId.systemDefault())
        return YearMonth.from(date).atDay(1).toString()
    }

    private data class TrackPlays(val track: Track, var plays: Long = 0, var playedMs: Long = 0)

    private data class ArtistPlays(
        val name: String,
        var plays: Long = 0,
        var playedMs: Long = 0,
    )
}
