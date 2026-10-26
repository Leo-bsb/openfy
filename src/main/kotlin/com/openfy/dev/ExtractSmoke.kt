package com.openfy.dev

import com.openfy.data.StreamServer
import com.openfy.data.YouTubeEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Mede o tempo real de extracao de um videoId especifico.
 * Uso: gradle extract -Pid=<videoId> [-Pprewarm=true]
 *
 * Com prewarm=true reproduz a inicializacao do app: o prewarm roda em paralelo
 * com a primeira extracao.
 */
fun main(args: Array<String>): Unit = runBlocking<Unit> {
    val id = args.firstOrNull()?.takeIf { it.isNotBlank() }
        ?: error("passe o videoId como argumento")
    val prewarm = args.getOrNull(1)?.toBooleanStrictOrNull() == true
    val engine = YouTubeEngine()

    val prewarmJob = if (prewarm) {
        launch(Dispatchers.Default) {
            val at = System.nanoTime()
            engine.prewarm()
            println("prewarm terminou em ${(System.nanoTime() - at) / 1_000_000}ms")
        }
    } else {
        null
    }
    if (prewarm) kotlinx.coroutines.delay(100)

    println("== extract $id ==")
    val started = System.nanoTime()
    val media = engine.media(id)
    val elapsed = (System.nanoTime() - started) / 1_000_000
    println("media=${media?.javaClass?.simpleName} elapsed=${elapsed}ms")
    prewarmJob?.join()
    if (media == null) {
        println("extracao devolveu null")
        kotlin.system.exitProcess(2)
    }

    val server = StreamServer { engine.media(it) }
    server.start()
    val url = server.urlFor(id)
    val conn = java.net.URI(url).toURL().openConnection() as java.net.HttpURLConnection
    conn.setRequestProperty("Range", "bytes=0-262143")
    conn.connectTimeout = 15_000
    conn.readTimeout = 45_000
    val reqStarted = System.nanoTime()
    val code = runCatching { conn.responseCode }.getOrElse { -1 }
    println("GET $url -> $code em ${(System.nanoTime() - reqStarted) / 1_000_000}ms")
    val bytes = runCatching { conn.inputStream.use { it.readBytes() } }.getOrNull()
    println("bytes=${bytes?.size ?: 0}")

    engine.forgetStream(id)
    server.stop()
    kotlin.system.exitProcess(if ((bytes?.size ?: 0) > 0) 0 else 2)
}
