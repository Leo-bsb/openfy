package com.openfy.core

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Resumo de um mês de escuta. */
data class RecapSummary(
    val plays: Int = 0,
    val playedMs: Long = 0,
    val uniqueTracks: Int = 0,
    val uniqueArtists: Int = 0,
    val completedPlays: Int = 0,
    val skippedPlays: Int = 0,
    val firstAt: Long? = null,
    val lastAt: Long? = null,
) {
    val minutes: Long get() = playedMs / 60_000
    val skipRate: Double get() = if (plays == 0) 0.0 else skippedPlays.toDouble() / plays
    val completionRate: Double get() = if (plays == 0) 0.0 else completedPlays.toDouble() / plays
}

data class RecapTrack(val track: Track, val plays: Int, val playedMs: Long)

data class RecapArtist(val name: String, val plays: Int, val playedMs: Long, val thumbnail: String = "")

data class RecapDay(val day: String, val plays: Int, val playedMs: Long)

data class RecapSnapshot(
    val summary: RecapSummary = RecapSummary(),
    val previous: RecapSummary = RecapSummary(),
    val topTracks: List<RecapTrack> = emptyList(),
    val topArtists: List<RecapArtist> = emptyList(),
    val days: List<RecapDay> = emptyList(),
    val firstTimeTracks: List<RecapTrack> = emptyList(),
    val liked: Int = 0,
)

/** Dia com mais minutos ouvidos; empate fica com quem teve mais execuções. */
fun RecapSnapshot.mostPlayedDay(): RecapDay? =
    days.filter { it.plays > 0 }
        .maxWithOrNull(compareBy<RecapDay> { it.playedMs }.thenBy { it.plays })

/** Dias do mês em que alguma música foi ouvida. */
fun RecapSnapshot.activeDays(): Int = days.count { it.plays > 0 }

/** Média de minutos por dia ativo; 0 quando o mês não teve escuta. */
fun RecapSnapshot.averageMinutesPerActiveDay(): Long {
    val active = activeDays()
    if (active <= 0) return 0
    return summary.minutes / active
}

/** Variação percentual de execuções contra o mês anterior; null sem base. */
fun RecapSnapshot.monthOverMonthPercent(): Int? {
    if (previous.plays <= 0) return null
    val diff = summary.plays - previous.plays
    return Math.round(diff.toDouble() / previous.plays * 100.0).toInt()
}

fun RecapSnapshot.artistOfMonth(): RecapArtist? = topArtists.firstOrNull()

/** Rótulo curto do dia (`dd/MM`) para os cartões do recap. */
fun RecapDay.dateLabel(): String =
    runCatching { LocalDate.parse(day).format(DateTimeFormatter.ofPattern("dd/MM")) }
        .getOrDefault(day)
