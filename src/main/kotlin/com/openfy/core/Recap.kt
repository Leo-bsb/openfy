package com.openfy.core

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
