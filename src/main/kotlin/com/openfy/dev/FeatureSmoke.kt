package com.openfy.dev

import com.openfy.core.SearchMode
import com.openfy.data.YouTubeEngine
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

/**
 * Verificacao headless das novas superficies: busca filtrada, perfil de artista,
 * playlists/albuns publicos, explorar e radio. Nao faz parte do produto.
 */
fun main(): Unit = runBlocking<Unit> {
    val engine = YouTubeEngine()
    var failures = 0

    fun check(ok: Boolean, message: String) {
        println((if (ok) "OK   " else "FAIL ") + message)
        if (!ok) failures++
    }

    println("== busca: musicas + artistas ==")
    val songs = runCatching { engine.search("copycat billie eilish", SearchMode.SONGS) }
        .onFailure { println("busca falhou: ${it.message}") }
        .getOrDefault(com.openfy.core.SearchResult())
    check(songs.tracks.isNotEmpty(), "faixas=${songs.tracks.size}")
    check(songs.artists.any { it.name.contains("Billie", ignoreCase = true) }, "artista na faixa: ${songs.artists.map { it.name }}")
    songs.tracks.take(4).forEach {
        println("   ${it.title} | ${it.artist} | ${it.album} | ${it.durationSec}s")
    }

    println("\n== busca: artistas ==")
    val artists = engine.search("billie eilish", SearchMode.ARTISTS)
    check(artists.artists.isNotEmpty(), "artistas=${artists.artists.size}")
    val official = artists.artists.firstOrNull { it.name.contains("Billie", ignoreCase = true) }
        ?: artists.artists.firstOrNull()
    check(official != null && official.id.startsWith("UC"), "perfil oficial=${official?.id}")
    official?.let { println("   ${it.name} | ${it.subtitle} | ${it.thumbnail.take(60)}") }

    println("\n== pagina do artista ==")
    val artistPage = official?.let { engine.artist(it.id) }
    check(artistPage != null && artistPage.name.isNotBlank(), "nome=${artistPage?.name}")
    check(artistPage?.topTracks?.isNotEmpty() == true, "populares=${artistPage?.topTracks?.size}")
    check(artistPage?.sections?.isNotEmpty() == true, "secoes=${artistPage?.sections?.map { it.title }}")
    check(artistPage?.radioVideoId != null && artistPage?.radioPlaylistId != null, "radio=${artistPage?.radioPlaylistId}")
    artistPage?.topTracks?.take(3)?.forEach {
        println("   ${it.title} | ${it.artist} | ${it.album} | ${it.durationSec}s")
    }
    artistPage?.sections?.forEach { section ->
        println("   [${section.title}] ${section.cards.size} cards: ${section.cards.take(2).map { it.title }}")
    }

    println("\n== busca: albuns ==")
    val albums = engine.search("billie eilish", SearchMode.ALBUMS)
    check(albums.cards.isNotEmpty(), "albuns=${albums.cards.size}")
    check(albums.cards.all { it.kind == com.openfy.core.MediaKind.ALBUM }, "todos sao albuns")
    albums.cards.take(3).forEach { println("   ${it.title} | ${it.subtitle} | ${it.id}") }

    println("\n== busca: playlists em destaque ==")
    val playlists = engine.search("hits", SearchMode.PLAYLISTS)
    check(playlists.cards.isNotEmpty(), "playlists=${playlists.cards.size}")
    playlists.cards.take(3).forEach { println("   ${it.title} | ${it.subtitle} | ${it.id}") }

    val playlist = playlists.cards.firstOrNull()
    if (playlist != null) {
        println("\n== playlist publica ==")
        val collection = engine.collection(playlist.id)
        check(collection.tracks.isNotEmpty(), "faixas=${collection.tracks.size}")
        check(collection.title.isNotBlank(), "titulo=${collection.title}")
        println("   ${collection.title} | ${collection.subtitle} | continuacao=${collection.continuation != null}")
        collection.tracks.take(3).forEach {
            println("   ${it.title} | ${it.artist} | ${it.album} | ${it.durationSec}s")
        }
        collection.continuation?.let { token ->
            val more = engine.collectionNext(token)
            check(more.tracks.isNotEmpty(), "continuacao +${more.tracks.size}")
            println("   proxima continuacao=${more.continuation != null}")
        }
    }

    println("\n== playlist longa (continuacao por itens) ==")
    val longPlaylist = engine.collection("VLRDCLAK5uy_mGxAOXlegKbqAYhZJlr8h5vfrdS4DH_OY")
    check(longPlaylist.tracks.size >= 50, "primeira pagina=${longPlaylist.tracks.size}")
    check(longPlaylist.continuation != null, "continuacao presente")
    longPlaylist.continuation?.let { token ->
        val more = engine.collectionNext(token)
        val known = longPlaylist.tracks.mapTo(HashSet()) { it.id }
        val extra = more.tracks.filter { it.id !in known }
        check(more.tracks.isNotEmpty(), "segunda pagina ${more.tracks.size} faixas")
        check(extra.isNotEmpty(), "faixas novas na segunda pagina=${extra.size}")
        println(
            "   ${longPlaylist.title} | unicas=${longPlaylist.tracks.size + extra.size} " +
                "| proxima continuacao=${more.continuation != null}",
        )
    }

    val album = albums.cards.firstOrNull()
    if (album != null) {
        println("\n== album publico ==")
        val collection = engine.collection(album.id)
        check(collection.tracks.isNotEmpty(), "faixas=${collection.tracks.size}")
        check(collection.kind == com.openfy.core.CollectionKind.ALBUM, "tipo=album")
        println("   ${collection.title} | ${collection.subtitle} | ${collection.kind}")
        collection.tracks.take(3).forEach {
            println("   ${it.title} | ${it.artist} | ${it.album} | ${it.durationSec}s")
        }
    }

    println("\n== album com artista no cabecalho ==")
    val hmhas = engine.collection("MPREb_Wp9Aj8HpTsB")
    check(hmhas.tracks.isNotEmpty(), "faixas=${hmhas.tracks.size}")
    check(
        hmhas.tracks.none { it.artist == "YouTube Music" },
        "artistas=${hmhas.tracks.map { it.artist }.distinct()}",
    )
    check(
        hmhas.tracks.all { it.album == hmhas.title },
        "album preenchido em ${hmhas.tracks.count { it.album == hmhas.title }}/${hmhas.tracks.size}",
    )

    println("\n== explorar ==")
    val explore = engine.explore()
    check(explore.sections.size >= 6, "secoes=${explore.sections.size}")
    check(explore.sections.all { it.cards.isNotEmpty() }, "todas as secoes tem cards")
    check(explore.sections.any { it.title == "Hits do Brasil" }, "secao Hits do Brasil presente")
    val exploreIds = explore.sections.flatMap { it.cards }.map { "${it.kind}:${it.id}" }
    check(exploreIds.size == exploreIds.toSet().size, "sem duplicatas (${exploreIds.size} cards)")
    check(explore.topArtists.isNotEmpty(), "top artistas=${explore.topArtists.size}")
    explore.sections.forEach { section ->
        println("   [${section.title}] ${section.cards.size}: ${section.cards.take(2).map { it.title }}")
    }
    println("   artistas: ${explore.topArtists.take(4).map { it.name }}")

    println("\n== charts (em alta) ==")
    val charts = runCatching { engine.charts() }
        .onFailure { println("charts falhou: ${it.message}") }
        .getOrDefault(emptyList())
    check(charts.isNotEmpty(), "faixas=${charts.size}")
    charts.take(3).forEach { println("   ${it.title} | ${it.artist}") }

    println("\n== sugestoes de busca ==")
    val sugg = runCatching { engine.searchSuggestions("billie") }.getOrDefault(emptyList())
    check(sugg.isNotEmpty(), "sugestoes=${sugg}")
    sugg.take(6).forEach { println("   $it") }

    println("\n== busca enxuta (importador) ==")
    val fast = runCatching { engine.searchSongs("billie eilish copycat", 5) }.getOrDefault(emptyList())
    check(fast.isNotEmpty(), "faixas=${fast.size}")
    fast.take(2).forEach { println("   ${it.title} | ${it.artist}") }

    println("\n== busca: continuacao ==")
    val firstPage = runCatching { engine.search("billie eilish", SearchMode.SONGS) }
        .getOrDefault(com.openfy.core.SearchResult())
    check(firstPage.continuation != null, "token presente")
    firstPage.continuation?.let { token ->
        val more = runCatching { engine.searchNext(SearchMode.SONGS, token) }.getOrNull()
        check(more != null && more.tracks.isNotEmpty(), "mais ${more?.tracks?.size ?: 0} faixas")
        more?.tracks?.take(2)?.forEach { println("   ${it.title} | ${it.artist}") }
    }

    println("\n== letras (LRCLIB) ==")
    val lyricLines = runCatching {
        com.openfy.data.LyricsClient.fetch(
            com.openfy.core.Track(
                "gBRi6aZJGj4",
                "Bellyache (Official Music Video)",
                "Billie Eilish",
                durationSec = 210,
            )
        )
    }.getOrDefault(emptyList())
    check(lyricLines.size >= 3, "linhas=${lyricLines.size} (titulo com sufixo)")
    lyricLines.take(3).forEach { println("   [${it.timeMs}] ${it.text}") }

    println("\n== BPM (modo DJ) ==")
    val bpm = runCatching {
        com.openfy.data.BpmClient.fetch(
            com.openfy.core.Track(
                "DyDfgMOUjCI",
                "bad guy",
                "Billie Eilish",
                durationSec = 194,
            )
        )
    }.getOrNull()
    check(bpm != null && bpm > 60.0, "bpm=$bpm")

    println("\n== radio da faixa ==")
    val seed = songs.tracks.firstOrNull()
    if (seed != null) {
        val radio = engine.radio(seed.id)
        check(radio.tracks.isNotEmpty(), "faixas=${radio.tracks.size}")
        check(radio.continuation != null, "continuacao presente")
        check(
            radio.tracks.none {
                "visualiza" in it.artist.lowercase() || "gostei" in it.artist.lowercase()
            },
            "artistas limpos",
        )
        radio.tracks.take(3).forEach {
            println("   ${it.title} | ${it.artist} | ${it.durationSec}s")
        }
        radio.continuation?.let { token ->
            val more = engine.radioNext(token)
            check(more.tracks.isNotEmpty(), "continuacao +${more.tracks.size}")
        }
    }

    println("\n== radio do artista ==")
    if (artistPage?.radioVideoId != null && artistPage.radioPlaylistId != null) {
        val radio = engine.radio(artistPage.radioVideoId, artistPage.radioPlaylistId)
        check(radio.tracks.isNotEmpty(), "faixas=${radio.tracks.size}")
        radio.tracks.take(3).forEach {
            println("   ${it.title} | ${it.artist} | ${it.durationSec}s")
        }
    }

    engine.close()
    println("\n== resultado: ${if (failures == 0) "TUDO OK" else "$failures FALHAS"} ==")
    exitProcess(if (failures == 0) 0 else 2)
}
