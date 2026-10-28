package com.openfy.dev

import com.openfy.core.Recommender
import com.openfy.core.Track
import com.openfy.data.Db
import java.nio.file.Files
import kotlin.system.exitProcess

/** Valida ranking ponderado, no-repeat e diversidade da rádio. */
fun main() {
    val dir = Files.createTempDirectory("openfy-recommendation")
    val db = Db(dir.resolve("openfy.db"))
    var failures = 0
    fun check(ok: Boolean, message: String) {
        println((if (ok) "OK   " else "FAIL ") + message)
        if (!ok) failures++
    }

    try {
        val now = System.currentTimeMillis()
        val a = Track("a", "Song A", "Artist One", album = "Album A", durationSec = 200)
        val a2 = Track("a2", "Song A (Remastered)", "Artist One", album = "Album A", durationSec = 200)
        val b = Track("b", "Song B", "Artist Two", album = "Album B", durationSec = 180)
        val c = Track("c", "Song C", "Artist Three", album = "Album C", durationSec = 240)
        val d = Track("d", "Song D", "Artist One", album = "Album A", durationSec = 210)
        listOf(a, a2, b, c, d).forEach { db.remember(it) }
        db.like(a.id)

        fun play(track: Track, at: Long, playedSec: Int, completed: Boolean, skipped: Boolean) {
            val seq = db.beginPlay(track, "test", null, "Teste", at)
            db.finishPlay(seq, playedSec * 1000, completed, skipped)
        }

        repeat(3) { index ->
            play(a, now - (index + 1) * 3_600_000L, 190, completed = true, skipped = false)
        }
        repeat(5) { index ->
            play(b, now - (index + 40) * 86_400_000L, 20, completed = false, skipped = true)
        }
        repeat(2) { index ->
            play(c, now - (index + 2) * 86_400_000L, 240, completed = true, skipped = false)
        }
        play(a2, now - 2 * 3_600_000L, 200, completed = true, skipped = false)
        play(d, now - 3 * 3_600_000L, 210, completed = true, skipped = false)

        val recommender = Recommender(db)
        val top = recommender.topPlayed(limit = 10)
        check(top.isNotEmpty(), "ranking não vazio")
        check(top.first().id == a.id, "faixa curtida/recente lidera (${top.firstOrNull()?.title})")
        check(top.map { Recommender.canonicalKey(it) }.toSet().size == top.size, "sem duplicata canônica")
        check(top.any { it.id == b.id } && top.indexOfFirst { it.id == b.id } > 0, "faixa pulada perde prioridade")

        val candidates = listOf(
            Track("r1", "Radio 1", "Artist One"),
            Track("r2", "Radio 2", "Artist One"),
            Track("r3", "Radio 3", "Artist Two"),
            Track("r4", "Radio 4", "Artist Three"),
            Track("r5", "Radio 5", "Artist Four"),
            Track("r6", "Radio 6 (Live)", "Artist Two"),
            Track("r7", "Radio 7", "Artist Five"),
            Track("r8", "Radio 8", "Artist Six"),
        )
        val radio = recommender.rankRadio(candidates, seed = a, existing = emptyList(), limit = 6)
        check(radio.isNotEmpty(), "rádio selecionou faixas")
        check(radio.map { it.id }.toSet().size == radio.size, "rádio sem ids repetidos")
        check(radio.map { Recommender.canonicalKey(it) }.toSet().size == radio.size, "rádio sem duplicata canônica")
        check(radio.take(4).map { Recommender.artistKey(it) }.toSet().size == radio.take(4).size, "diversidade de artista na janela")
        check(radio.none { it.id == a.id }, "semente não repetida na rádio")
    } finally {
        db.close()
        runCatching { dir.toFile().deleteRecursively() }
    }

    println("\n== resultado: ${if (failures == 0) "TUDO OK" else "$failures FALHAS"} ==")
    exitProcess(if (failures == 0) 0 else 2)
}
