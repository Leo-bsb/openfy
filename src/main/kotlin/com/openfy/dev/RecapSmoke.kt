package com.openfy.dev

import com.openfy.core.Track
import com.openfy.data.Db
import java.nio.file.Files
import java.time.YearMonth
import java.time.ZoneId
import kotlin.system.exitProcess

/** Valida as consultas mensais do recap e a migração de eventos. */
fun main() {
    val dir = Files.createTempDirectory("openfy-recap")
    val db = Db(dir.resolve("openfy.db"))
    var failures = 0
    fun check(ok: Boolean, message: String) {
        println((if (ok) "OK   " else "FAIL ") + message)
        if (!ok) failures++
    }

    try {
        val month = YearMonth.now()
        val zone = ZoneId.systemDefault()
        val start = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val prevStart = month.minusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val trackA = Track("ra", "Recap A", "Artist A", durationSec = 180)
        val trackB = Track("rb", "Recap B", "Artist B", durationSec = 240)
        db.remember(trackA)
        db.remember(trackB)

        fun play(track: Track, at: Long, playedMs: Int, completed: Boolean, skipped: Boolean) {
            val seq = db.beginPlay(track, "test", "ctx", "Contexto", at)
            db.finishPlay(seq, playedMs, completed, skipped)
        }

        play(trackA, start + 3_600_000L, 180_000, completed = true, skipped = false)
        play(trackA, start + 2 * 3_600_000L, 180_000, completed = true, skipped = false)
        play(trackB, start + 26 * 3_600_000L, 120_000, completed = false, skipped = true)
        play(trackB, start + 50 * 3_600_000L, 240_000, completed = true, skipped = false)
        play(trackA, prevStart + 3_600_000L, 180_000, completed = true, skipped = false)

        val summary = db.recapSummary(start, end)
        check(summary.plays == 4, "4 escutas no mês (${summary.plays})")
        check(summary.uniqueTracks == 2, "2 faixas únicas (${summary.uniqueTracks})")
        check(summary.skippedPlays == 1, "1 skip (${summary.skippedPlays})")
        check(summary.playedMs == 720_000L, "duração somada (${summary.playedMs})")

        val top = db.recapTopTracks(start, end, 10)
        check(top.firstOrNull()?.track?.id == trackA.id, "top faixa A")
        check(top.firstOrNull()?.plays == 2, "top faixa A com 2 plays")

        val artists = db.recapTopArtists(start, end, 10)
        check(artists.firstOrNull()?.name == "Artist A", "top artista A")

        val days = db.recapDays(start, end)
        check(days.size >= 3, "atividade diária (${days.size} dias)")

        val keys = db.recapMonthKeys()
        check(keys.contains(month.toString()), "mês atual listado")

        val empty = db.recapSummary(end, end + 86_400_000L)
        check(empty.plays == 0, "mês vazio retorna zero")

        val first = db.recapFirstTimeTracks(start, end, 10)
        check(first.any { it.track.id == trackB.id }, "descoberta do mês inclui B")
    } finally {
        db.close()
        runCatching { dir.toFile().deleteRecursively() }
    }

    println("\n== resultado: ${if (failures == 0) "TUDO OK" else "$failures FALHAS"} ==")
    exitProcess(if (failures == 0) 0 else 2)
}
