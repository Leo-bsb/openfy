package com.openfy.dev

import com.openfy.data.StreamServer
import com.openfy.data.YouTubeEngine
import kotlinx.coroutines.runBlocking
import java.net.HttpURLConnection
import java.net.URI

/**
 * Verificacao headless do motor: busca -> extracao -> servidor local.
 * Nao faz parte do produto; existe para provar que o innertubex funciona
 * sem WebView/PoToken antes de investir na UI.
 */
fun main() = runBlocking {
    val engine = YouTubeEngine()
    println("== search ==")
    val results = runCatching { engine.search("never gonna give you up") }
        .onFailure { println("search FAILED: ${it.stackTraceToString().lineSequence().take(6).joinToString("\n")}") }
        .getOrNull()
        .orEmpty()
    results.take(3).forEach { println("${it.id} | ${it.title} | ${it.artist} | ${it.durationSec}s") }
    if (results.isEmpty()) {
        val raw = runCatching { engine.searchRaw("never gonna give you up") }.getOrElse { "ERRO: $it" }
        java.nio.file.Files.writeString(java.nio.file.Path.of("search-raw.json"), raw)
        println("raw salvo em search-raw.json (${raw.length} chars)")
        println(raw.take(600))
        return@runBlocking
    }

    val track = results.first()
    println("== extract ${track.id} ==")
    when (val media = engine.media(track.id)) {
        is com.openfy.data.Media.Remote -> {
            val params = URI(media.url).rawQuery?.split('&')?.map { it.substringBefore('=') }.orEmpty()
            println("url params: $params")
            println("headers: ${media.headers.keys}")
        }
        null -> println("extracao devolveu null")
        else -> println("media: $media")
    }

    val server = StreamServer { id -> engine.media(id) }
    server.start()
    println("server on ${server.port}")

    val url = server.urlFor(track.id)
    val conn = URI(url).toURL().openConnection() as HttpURLConnection
    conn.setRequestProperty("Range", "bytes=0-262143")
    conn.connectTimeout = 30_000
    conn.readTimeout = 45_000
    val code = runCatching { conn.responseCode }.getOrElse { -1 }
    println("GET $url -> $code ${conn.contentType} len=${conn.getHeaderField("Content-Length")}")
    val bytes = runCatching { conn.inputStream.use { it.readBytes() } }
        .onFailure { println("read FAILED: ${it.message}") }
        .getOrNull()
    println("bytes=${bytes?.size ?: 0} head=${bytes?.take(4)?.joinToString(" ") { b -> "%02x".format(b) }}")

    engine.forgetStream(track.id)
    server.stop()
    println("== done ==")
    kotlin.system.exitProcess(if ((bytes?.size ?: 0) > 0) 0 else 2)
}
