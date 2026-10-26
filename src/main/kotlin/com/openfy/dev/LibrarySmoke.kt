package com.openfy.dev

import com.openfy.core.Backup
import com.openfy.core.BackupPlaylist
import com.openfy.core.Importer
import com.openfy.core.SharedPlaylist
import com.openfy.core.Track
import com.openfy.data.Db
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import kotlin.system.exitProcess

/** Verificacao headless do importador (URL/CSV/match) e do backup JSON. */
fun main() {
    var failures = 0

    fun check(ok: Boolean, message: String) {
        println((if (ok) "OK   " else "FAIL ") + message)
        if (!ok) failures++
    }

    println("== URL do YouTube Music ==")
    check(
        Importer.parseYtmBrowseId("https://music.youtube.com/playlist?list=PL123") == "VLPL123",
        "playlist PL",
    )
    check(
        Importer.parseYtmBrowseId("https://music.youtube.com/playlist?list=RDCLAK5uy_x") == "VLRDCLAK5uy_x",
        "playlist RD",
    )
    check(
        Importer.parseYtmBrowseId("https://music.youtube.com/browse/MPREb_Wp9Aj8HpTsB") == "MPREb_Wp9Aj8HpTsB",
        "album",
    )
    check(Importer.parseYtmBrowseId("lixo") == null, "link inválido")

    println("\n== CSV (Exportify) ==")
    val csv = """
        Track URI,Track Name,Album Name,Artist Name(s),Duration (ms)
        spotify:track:1,Bellyache,Bellyache EP,Billie Eilish,210000
        spotify:track:2,"Title, With Comma",Album,"Artist A, Artist B",195000
    """.trimIndent()
    val rows = Importer.parseCsv(csv)
    check(rows.size == 2, "linhas=${rows.size}")
    check(rows[0].title == "Bellyache" && rows[0].artist == "Billie Eilish", "campos")
    check(rows[1].title == "Title, With Comma", "vírgula entre aspas")
    rows.forEach { println("   ${it.title} | ${it.artist} | ${it.durationMs}ms") }

    println("\n== casamento ==")
    val candidates = listOf(
        Track("a", "Bellyache", "Billie Eilish", durationSec = 210),
        Track("b", "Bellyache (Live)", "Billie Eilish", durationSec = 220),
    )
    val match = Importer.bestMatch(rows[0], candidates)
    check(match?.id == "a", "escolhida=${match?.id}")

    println("\n== backup JSON ==")
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    val backup = Backup(
        exportedAt = 1,
        playlists = listOf(BackupPlaylist("Minha", listOf(Track("a", "Bellyache", "Billie Eilish")))),
        liked = listOf(Track("b", "bad guy", "Billie Eilish")),
    )
    val encoded = json.encodeToString(backup)
    val decoded = json.decodeFromString<Backup>(encoded)
    check(
        decoded.playlists.size == 1 &&
            decoded.playlists[0].tracks.size == 1 &&
            decoded.liked.size == 1,
        "round-trip (${encoded.length} bytes)",
    )

    println("\n== playlist compartilhada (.openfyplaylist) ==")
    val shared = SharedPlaylist(
        name = "Compartilhada",
        exportedAt = 1,
        tracks = listOf(Track("a", "Bellyache", "Billie Eilish")),
    )
    val sharedEncoded = json.encodeToString(shared)
    val sharedDecoded = json.decodeFromString<SharedPlaylist>(sharedEncoded)
    check(
        sharedDecoded.name == "Compartilhada" && sharedDecoded.tracks.single().id == "a",
        "round-trip (${sharedEncoded.length} bytes)",
    )

    println("\n== backup do banco (VACUUM INTO) ==")
    val temp = Files.createTempDirectory("openfy-backup-smoke")
    runCatching {
        val db = Db(temp.resolve("openfy.db"))
        val playlist = db.createPlaylist("Backup Test")
        db.addToPlaylist(playlist.id, Track("x", "Faixa", "Artista"))
        val target = temp.resolve("openfy-backup.db")
        db.backupTo(target)
        db.close()
        val restored = Db(target)
        val names = restored.playlists().map { it.name }
        restored.close()
        check(names.contains("Backup Test") && Files.size(target) > 0, "backup legível (${Files.size(target)} bytes)")
    }.onFailure { check(false, "backup: ${it.message}") }
    runCatching { temp.toFile().deleteRecursively() }

    println("\n== resultado: ${if (failures == 0) "TUDO OK" else "$failures FALHAS"} ==")
    exitProcess(if (failures == 0) 0 else 2)
}
