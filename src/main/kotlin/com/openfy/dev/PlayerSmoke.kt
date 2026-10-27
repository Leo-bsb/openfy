package com.openfy.dev.player

import com.openfy.core.Track
import com.openfy.data.Db
import com.openfy.data.StreamServer
import com.openfy.data.YouTubeEngine
import com.openfy.player.Mpv
import com.openfy.player.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.file.Files
import java.nio.file.Path

/**
 * Exercita o caminho real de reproducao (Player -> mpv IPC -> StreamServer)
 * com volume zero. Prova que o formato das mensagens IPC carrega a faixa.
 */
fun main(): Unit = runBlocking {
    val mpvExe = Path.of("D:\\scripts\\openfy\\.tools\\mpv\\mpv.exe")
    val dbFile = Path.of(System.getProperty("java.io.tmpdir"), "opencode", "openfy-smoke.db")
    Files.createDirectories(dbFile.parent)
    Files.deleteIfExists(dbFile)

    val mpv = Mpv(mpvExe)
    val mpvB = Mpv(mpvExe, null, "-b")
    val db = Db(dbFile)
    val engine = YouTubeEngine()
    val stream = StreamServer { id -> engine.media(id) }
    stream.start()
    println("stream na porta ${stream.port}")

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val player = Player(mpv, mpvB, stream, db, scope)
    player.radioStarter = { id -> engine.radio(id, "RDAMVM$id") }
    player.radioSource = { token -> engine.radioNext(token) }
    player.start()

    val ready = withTimeoutOrNull(15_000) { mpv.ready.first { it } } ?: false
    println("mpv ready=$ready")
    if (!ready) {
        player.shutdown(); stream.stop(); db.close()
        kotlin.system.exitProcess(1)
    }

    player.setVolume(0)
    player.playNow(listOf(Track("lYBUbBu4W08", "Never Gonna Give You Up", "Rick Astley")))
    val position = withTimeoutOrNull(30_000) { player.position.first { it > 2.0 } } ?: 0.0
    println("pos=$position dur=${player.duration.value} playing=${player.playing.value}")

    // Comandos depois que a readLoop ja esta bloqueada (o caso do clique na UI).
    player.seekToFraction(0.5)
    val seeked = withTimeoutOrNull(10_000) { player.position.first { it > 90.0 } }
    println("seek=$seeked")

    player.togglePlay()
    kotlinx.coroutines.delay(600)
    println("pausado=${!player.playing.value}")
    player.togglePlay()
    kotlinx.coroutines.delay(600)
    println("retomado=${player.playing.value}")

    // Troca de faixa em tempo real, como clicar em outro resultado.
    player.playSingle(Track("rA8xvaObO3c", "COPYCAT", "Billie Eilish"))
    val second = withTimeoutOrNull(30_000) { player.position.first { it > 1.5 } } ?: 0.0
    println("segunda=$second dur=${player.duration.value} playing=${player.playing.value}")

    // Busca -> radio: toca a faixa e emenda a radio dela.
    player.setVolume(0)
    player.playThenRadio(Track("rA8xvaObO3c", "COPYCAT", "Billie Eilish"))
    val radioAfterTrack = withTimeoutOrNull(30_000) { player.queue.first { it.size > 1 } }
    println("busca->radio fila=${radioAfterTrack?.size} radio=${player.radioActive.value}")

    // Fim de fila -> radio: playlist de 1 faixa termina e emenda a radio.
    player.playNow(listOf(Track("rA8xvaObO3c", "COPYCAT", "Billie Eilish")))
    withTimeoutOrNull(20_000) { player.duration.first { it > 0 } }
    kotlinx.coroutines.delay(1500)
    player.seekToFraction(0.99)
    val radioAtEnd = withTimeoutOrNull(30_000) { player.queue.first { it.size > 1 } }
    println("fim->radio fila=${radioAtEnd?.size} radio=${player.radioActive.value}")

    // Download real (mesmo caminho do app).
    val dlDir = Files.createTempDirectory("openfy-dl")
    val downloader = com.openfy.data.Downloader(engine, dlDir)
    val dlPath = downloader.download(Track("rA8xvaObO3c", "COPYCAT", "Billie Eilish"))
    val dlSize = dlPath?.let { runCatching { Files.size(it) }.getOrDefault(0L) } ?: 0L
    println("download=${dlPath?.fileName} size=$dlSize")

    // Crossfade real: dois mpv, o proximo entra sobreposto antes do fim.
    player.setVolume(0)
    player.setCrossfade(4)
    player.setTransitionStyle(Player.TRANSITION_BLEND)
    player.shuffle.value = true
    val fadeA = Track("lYBUbBu4W08", "Never Gonna Give You Up", "Rick Astley")
    val fadeB = Track("rA8xvaObO3c", "COPYCAT", "Billie Eilish")
    player.playNow(listOf(fadeA, fadeB), 0)
    withTimeoutOrNull(30_000) { player.position.first { it > 2.0 } }
    kotlinx.coroutines.delay(500)
    player.seekToFraction(0.97)
    val switched = withTimeoutOrNull(25_000) {
        player.current.first { it?.id == fadeB.id }
    }
    val fadeDone = withTimeoutOrNull(15_000) { player.crossfading.first { !it } } != null
    val crossfadeOk = switched != null && player.playing.value && mpvB.processId() != null && fadeDone
    println(
        "crossfade=${crossfadeOk} faixa=${player.current.value?.title} " +
            "pos=${player.position.value} mpvB=${mpvB.processId()} fim=${fadeDone}"
    )
    player.setCrossfade(0)
    player.setTransitionStyle(Player.TRANSITION_CROSSFADE)
    player.shuffle.value = false

    // Shuffle deve reordenar a fila visivel e restaurar a ordem ao desligar.
    val shuffleTracks = listOf(
        Track("s1", "A", "teste"),
        Track("s2", "B", "teste"),
        Track("s3", "C", "teste"),
        Track("s4", "D", "teste"),
    )
    player.queue.value = shuffleTracks
    player.index.value = 0
    player.current.value = shuffleTracks[0]
    player.shuffle.value = false
    player.toggleShuffle()
    val shuffledOk = player.shuffle.value &&
        player.queue.value.size == 4 &&
        player.queue.value.first().id == "s1" &&
        player.queue.value.map { it.id }.toSet() == shuffleTracks.map { it.id }.toSet()
    player.toggleShuffle()
    val restoredOk = !player.shuffle.value &&
        player.queue.value.map { it.id } == shuffleTracks.map { it.id }
    println("shuffle=$shuffledOk restaurado=$restoredOk")

    player.playSingle(Track("aaaaaaaaaaa", "Faixa invalida", "teste"))
    val failure = withTimeoutOrNull(15_000) { player.error.first { it != null } }
    println("erro=$failure busy=${player.busy.value} playing=${player.playing.value}")

    val autoplayOk = (radioAfterTrack?.size ?: 0) > 1 && (radioAtEnd?.size ?: 0) > 1
    val downloadOk = dlSize > 1_000_000
    val ok = position > 2.0 && seeked != null && second > 1.5 && failure != null &&
        autoplayOk && downloadOk && crossfadeOk && shuffledOk && restoredOk
    println("autoplay=$autoplayOk download=$downloadOk crossfade=$crossfadeOk")
    runCatching { dlDir.toFile().deleteRecursively() }
    player.shutdown()
    stream.stop()
    db.close()
    kotlin.system.exitProcess(if (ok) 0 else 2)
}
