package com.openfy.data

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.IOException
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.Executors

/** O que o mpv pode receber: um arquivo baixado ou um upstream remoto. */
sealed interface Media {
    data class Remote(val url: String, val headers: Map<String, String> = emptyMap()) : Media
    data class Local(val path: Path) : Media
}

/**
 * Servidor HTTP local que o mpv consome. Existe por tres motivos:
 *  1. o mpv nunca ve URL do Google, entao expiracao de URL nao quebra playback;
 *  2. seek funciona mesmo quando o upstream exige nova assinatura (o resolve() e chamado de novo);
 *  3. o mesmo caminho serve arquivo baixado e faixa em streaming.
 */
class StreamServer(private val resolve: (String) -> Media?) {

    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(12))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)

    val port: Int get() = server.address.port

    init {
        server.executor = Executors.newFixedThreadPool(6) { r ->
            Thread(r, "openfy-stream").apply { isDaemon = true }
        }
        server.createContext("/t/") { exchange -> handle(exchange) }
    }

    fun start() = server.start()

    fun stop() = server.stop(0)

    fun urlFor(videoId: String): String =
        "http://127.0.0.1:$port/t/" + videoId

    private fun handle(exchange: HttpExchange) {
        try {
            val id = exchange.requestURI.path.removePrefix("/t/").trim('/')
            if (id.isEmpty()) return fail(exchange, 404)
            val range = exchange.requestHeaders.getFirst("Range")
            com.openfy.core.Log.write("stream: GET $id range=${range ?: "-"}")
            val started = System.nanoTime()
            val media = runCatching { resolve(id) }
                .onFailure {
                    com.openfy.core.Log.write(
                        "stream: resolve falhou para $id: ${it::class.simpleName}: ${it.message}"
                    )
                }
                .getOrNull()
            val elapsed = (System.nanoTime() - started) / 1_000_000
            when (media) {
                null -> {
                    com.openfy.core.Log.write("stream: sem midia para $id (${elapsed}ms)")
                    fail(exchange, 502)
                }
                is Media.Local -> serveFile(exchange, media.path)
                is Media.Remote -> {
                    com.openfy.core.Log.write("stream: midia pronta para $id em ${elapsed}ms")
                    proxy(exchange, media)
                }
            }
        } catch (e: Exception) {
            com.openfy.core.Log.write("stream: erro no request: ${e::class.simpleName}: ${e.message}")
            runCatching { fail(exchange, 500) }
        } finally {
            runCatching { exchange.close() }
        }
    }

    private fun proxy(exchange: HttpExchange, media: Media.Remote) {
        val range = exchange.requestHeaders.getFirst("Range")
        val builder = HttpRequest.newBuilder(URI.create(media.url))
            .timeout(Duration.ofSeconds(30))
            .header("User-Agent", UA)
            .header("Accept-Encoding", "identity")
            .GET()
        media.headers.forEach { (key, value) ->
            if (!key.equals("host", true) && !key.equals("content-length", true)) {
                runCatching { builder.header(key, value) }
            }
        }
        if (range != null) builder.header("Range", range)

        val upstream = http.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream())
        val status = upstream.statusCode()
        val body = upstream.body()

        if (status !in 200..299) {
            com.openfy.core.Log.write("stream: upstream respondeu $status")
            body.close()
            fail(exchange, 502)
            return
        }

        val contentLength = upstream.headers().firstValueAsLong("Content-Length").orElse(-1L)
        exchange.responseHeaders.apply {
            set("Content-Type", upstream.headers().firstValue("Content-Type").orElse("audio/webm"))
            upstream.headers().firstValue("Content-Range").ifPresent { set("Content-Range", it) }
            set("Accept-Ranges", "bytes")
            set("Cache-Control", "no-store")
        }
        // length 0 = chunked; -1 = sem corpo (erro). O valor real mantem o
        // Content-Length que o mpv usa para calcular o tamanho do stream.
        exchange.sendResponseHeaders(status, if (contentLength > 0) contentLength else 0)
        exchange.responseBody.use { out -> body.use { it.copyTo(out, 128 * 1024) } }
    }

    private fun serveFile(exchange: HttpExchange, path: Path) {
        if (!Files.isRegularFile(path)) return fail(exchange, 404)
        val total = Files.size(path)
        val range = exchange.requestHeaders.getFirst("Range")

        exchange.responseHeaders.set("Content-Type", "audio/webm")
        exchange.responseHeaders.set("Accept-Ranges", "bytes")

        if (range == null) {
            exchange.responseHeaders.set("Content-Length", total.toString())
            exchange.sendResponseHeaders(200, total)
            Files.newInputStream(path).use { it.copyTo(exchange.responseBody, 128 * 1024) }
            return
        }

        val (start, end) = parseRange(range, total)
        val length = end - start + 1
        exchange.responseHeaders.set("Content-Range", "bytes $start-$end/$total")
        exchange.responseHeaders.set("Content-Length", length.toString())
        exchange.sendResponseHeaders(206, length)
        Files.newByteChannel(path).use { channel ->
            channel.position(start)
            val buffer = java.nio.ByteBuffer.allocate(128 * 1024)
            var remaining = length
            while (remaining > 0) {
                buffer.clear()
                buffer.limit(minOf(buffer.capacity().toLong(), remaining).toInt())
                val read = channel.read(buffer)
                if (read <= 0) break
                exchange.responseBody.write(buffer.array(), 0, read)
                remaining -= read
            }
        }
    }

    private fun parseRange(header: String, total: Long): Pair<Long, Long> {
        val spec = header.substringAfter("bytes=").substringBefore(',')
        val left = spec.substringBefore('-').trim()
        val right = spec.substringAfter('-').trim()
        return if (left.isEmpty()) {
            val suffix = right.toLongOrNull() ?: total
            (total - suffix).coerceAtLeast(0) to total - 1
        } else {
            val start = left.toLongOrNull() ?: 0
            val end = right.toLongOrNull() ?: (total - 1)
            start.coerceIn(0, total - 1) to end.coerceIn(start, total - 1)
        }
    }

    private fun fail(exchange: HttpExchange, code: Int) {
        try {
            exchange.sendResponseHeaders(code, -1)
        } catch (_: IOException) {
        }
    }

    private companion object {
        const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/131.0.0.0 Safari/537.36"
    }
}
