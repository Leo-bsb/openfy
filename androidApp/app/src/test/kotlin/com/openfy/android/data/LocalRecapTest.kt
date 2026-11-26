package com.openfy.android.data

import com.openfy.core.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth
import java.time.ZoneId

class LocalRecapTest {

    private fun at(month: YearMonth, day: Int, hour: Int = 12): Long =
        month.atDay(day).atTime(hour, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun entry(
        id: String,
        trackId: String,
        title: String,
        artist: String,
        month: YearMonth,
        day: Int,
        playedMs: Int = 180_000,
        skipped: Boolean = false,
    ) = HistoryEntry(
        id = id,
        track = Track(trackId, title, artist, durationSec = 200),
        at = at(month, day),
        playedMs = playedMs,
        durationMs = 200_000,
        completed = !skipped,
        skipped = skipped,
    )

    @Test
    fun `monta resumo local sem servidor`() {
        val month = YearMonth.of(2026, 11)
        val entries = listOf(
            entry("1", "a", "A", "Artista X", month, 3),
            entry("2", "a", "A", "Artista X", month, 4),
            entry("3", "b", "B", "Artista Y", month, 5, playedMs = 0, skipped = true),
            entry("old", "c", "C", "Artista Z", month.minusMonths(1), 10),
        )

        val snapshot = LocalRecap.build(entries, month.atDay(1).toString())

        assertEquals(3, snapshot.listening.plays.toInt())
        assertEquals(2, snapshot.listening.uniqueTracks.toInt())
        assertEquals(2, snapshot.listening.completedPlays.toInt())
        assertEquals(1, snapshot.listening.skippedPlays.toInt())
        assertEquals(180_000L * 2, snapshot.listening.playedMs)
        assertEquals("A", snapshot.topTracks.first().title)
        assertEquals("Artista X", snapshot.artists.first().artist)
        assertTrue(snapshot.months.contains(month.atDay(1).toString()))
        assertTrue(snapshot.months.contains(month.minusMonths(1).atDay(1).toString()))
    }

    @Test
    fun `mes vazio nao inventa numeros`() {
        val snapshot = LocalRecap.build(emptyList(), "2026-11-01")
        assertEquals(0L, snapshot.listening.plays)
        assertEquals(0L, snapshot.listening.playedMs)
        assertTrue(snapshot.topTracks.isEmpty())
        assertTrue(snapshot.artists.isEmpty())
    }
}
