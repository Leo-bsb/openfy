package com.openfy.dev

import com.openfy.data.YouTubeEngine
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path

/**
 * Sonda temporaria: salva respostas cruas do YouTube Music em %TEMP%\openfy-probe
 * para inspecionar o formato real de filtros, artista, playlist e radio.
 *
 * Uso: gradle probe
 */
fun main(): Unit = runBlocking<Unit> {
    val dir = Path.of(System.getenv("TEMP") ?: ".", "openfy-probe")
    Files.createDirectories(dir)
    val engine = YouTubeEngine()
    val query = "copycat billie eilish"

    suspend fun save(name: String, block: suspend () -> String) {
        val started = System.nanoTime()
        val result = runCatching { block() }
        val elapsed = (System.nanoTime() - started) / 1_000_000
        result.onSuccess { body ->
            Files.writeString(dir.resolve("$name.json"), body)
            println("$name: ${body.length} bytes em ${elapsed}ms")
        }.onFailure {
            println("$name: FALHOU em ${elapsed}ms: ${it::class.simpleName}: ${it.message}")
        }
    }

    val params = mapOf(
        "songs" to "EgWKAQIIAWoKEAkQBRAKEAMQBA==",
        "artists" to "EgWKAQIgAWoKEAkQChAFEAMQBA==",
        "albums" to "EgWKAQIYAWoKEAkQChAFEAMQBA==",
        "playlists" to "EgWKAQIoAWoKEAkQChAFEAMQBA==",
        "videos" to "EgWKAQIQAWoKEAkQChAFEAMQBA==",
    )

    save("search-all") { engine.searchRaw(query) }
    for ((name, value) in params) {
        save("search-$name") { engine.searchRaw(query, value) }
    }
    save("browse-home") { engine.browseRaw("FEmusic_home") }
    save("browse-charts") { engine.browseRaw("FEmusic_charts") }
    save("browse-explore") { engine.browseRaw("FEmusic_explore") }
    save("browse-artist") { engine.browseRaw("UCERrDZ8oN0U_n9MphMKERcg") }
    save("browse-playlist") { engine.browseRaw("VLPL2Yj1RDbbPoTzXPotfLXJ108v2OuTGirm") }
    save("browse-playlist-big") {
        engine.browseRaw("VLRDCLAK5uy_n9Fbdw7e6ap-98_A-8JYBmPv64v-Uaq1g")
    }
    save("browse-playlist-long") {
        engine.browseRaw("VLRDCLAK5uy_mGxAOXlegKbqAYhZJlr8h5vfrdS4DH_OY")
    }
    save("browse-playlist-big-next") {
        engine.browseRaw(
            browseId = "",
            continuation = "4qmFsgI9Ei1WTFJEQ0xBSzV1eV9uOUZiZHc3ZTZhcC05OF9BLThKWUJtUHY2NHYtVWFxMWcaDGtnRURDTTBHOEFFQQ%3D%3D",
        )
    }
    save("browse-playlist-long-next") {
        engine.browseRaw(
            browseId = "",
            continuation = "4qmFsgKNARItVkxSRENMQUs1dXlfbUd4QU9YbGVnS2JxQVloWkpscjhoNXZmcmRTNERIX09ZGi5laFZRVkRwRlozTjVVVEZDU1ZsclZrcFJlbHBHVkZHU0FRTUl1Z1R3QVFBJTNEmgIrUkRDTEFLNXV5X21HeEFPWGxlZ0ticUFZaFpKbHI4aDV2ZnJkUzRESF9PWQ%3D%3D",
        )
    }
    save("browse-playlist-long-next2") {
        engine.browseRaw(
            browseId = "VLRDCLAK5uy_mGxAOXlegKbqAYhZJlr8h5vfrdS4DH_OY",
            continuation = "4qmFsgKNARItVkxSRENMQUs1dXlfbUd4QU9YbGVnS2JxQVloWkpscjhoNXZmcmRTNERIX09ZGi5laFZRVkRwRlozTjVVVEZDU1ZsclZrcFJlbHBHVkZHU0FRTUl1Z1R3QVFBJTNEmgIrUkRDTEFLNXV5X21HeEFPWGxlZ0ticUFZaFpKbHI4aDV2ZnJkUzRESF9PWQ%3D%3D",
        )
    }
    save("browse-playlist-long-next3") {
        engine.browseRaw(
            browseId = "",
            continuation = "4qmFsgKNARItVkxSRENMQUs1dXlfbUd4QU9YbGVnS2JxQVloWkpscjhoNXZmcmRTNERIX09ZGi5laFZRVkRwRlozTXpWa2hXZFZWV1ZrdGFTR3Q0VWxHU0FRTUl1Z1R3QVFBJTNEmgIrUkRDTEFLNXV5X21HeEFPWGxlZ0ticUFZaFpKbHI4aDV2ZnJkUzRESF9PWQ%3D%3D",
        )
    }
    save("next-playlist-long") {
        engine.nextRaw(playlistId = "RDCLAK5uy_mGxAOXlegKbqAYhZJlr8h5vfrdS4DH_OY")
    }
    save("next-playlist-long2") {
        engine.nextRaw(playlistId = "VLRDCLAK5uy_mGxAOXlegKbqAYhZJlr8h5vfrdS4DH_OY")
    }
    save("browse-playlist-long-next4") {
        engine.browseRaw(
            browseId = "",
            continuation = "4qmFsgKNARItVkxSRENMQUs1dXlfbUd4QU9YbGVnS2JxQVloWkpscjhoNXZmcmRTNERIX09ZGi5laFZRVkRwRlozTjVVVEZDU1ZsclZrcFJlbHBHVkZHU0FRTUl1Z1R3QVFBJTNEmgIrUkRDTEFLNXV5X21HeEFPWGxlZ0ticUFZaFpKbHI4aDV2ZnJkUzRESF9PWQ==",
        )
    }
    save("browse-album") { engine.browseRaw("MPREb_1Ju8u992G4O") }
    save("browse-album-hmhas") { engine.browseRaw("MPREb_Wp9Aj8HpTsB") }
    save("next-radio") { engine.nextRaw("rdKK3OMyLxs", "RDAMVMrdKK3OMyLxs") }
    save("search-featured-hits") {
        engine.searchRaw("hits", "EgeKAQQoADgBahIQBBADEAUQCRAKEBUQEBAOEBE=")
    }
    for ((name, query) in listOf(
        "seed-hits-brasil" to "hits brasil",
        "seed-pop" to "pop",
        "seed-sertanejo" to "sertanejo",
        "seed-rock" to "rock",
        "seed-rap" to "rap",
        "seed-funk" to "funk",
        "seed-mpb" to "mpb",
        "seed-pop-brasil" to "pop brasil",
        "seed-rock-brasil" to "rock brasil",
        "seed-rap-brasil" to "rap brasil",
        "seed-mpb-brasil" to "mpb brasil",
    )) {
        save(name) {
            engine.searchRaw(query, "EgeKAQQoADgBahIQBBADEAUQCRAKEBUQEBAOEBE=")
        }
    }

    println("arquivos em $dir")
    engine.close()
}
