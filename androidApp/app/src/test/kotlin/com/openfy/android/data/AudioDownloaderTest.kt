package com.openfy.android.data

import com.openfy.core.Track
import com.sun.net.httpserver.HttpServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress
import java.nio.file.Files

class AudioDownloaderTest {

    @Test
    fun downloadsFileAndRecordsEntry() {
        val dir = Files.createTempDirectory("openfy-audio").toFile()
        val server = server { exchange ->
            val body = ByteArray(4096) { (it % 251).toByte() }
            exchange.responseHeaders.add("Content-Type", "audio/webm")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        try {
            val store = DownloadStore(dir)
            val progress = mutableListOf<Float>()
            val result = AudioDownloader(store) { 10L * 1024 * 1024 }.download(
                track = Track("abc", "Titulo", "Artista"),
                url = "http://127.0.0.1:${server.address.port}/audio",
                headers = emptyMap(),
                onProgress = { progress += it },
                isCancelled = { false },
            )

            assertTrue(result is DownloadResult.Success)
            val entry = (result as DownloadResult.Success).entry
            assertEquals(4096L, entry.sizeBytes)
            assertTrue(entry.fileName.endsWith(".webm"))
            assertTrue(java.io.File(dir, entry.fileName).isFile)
            assertEquals(4096L, store.usedBytes())
            assertTrue(progress.any { it == 1f })
            assertFalse(dir.listFiles().orEmpty().any { it.name.endsWith(".part") })
        } finally {
            server.stop(0)
            dir.deleteRecursively()
        }
    }

    @Test
    fun expiredLinkIsReported() {
        val dir = Files.createTempDirectory("openfy-audio-expired").toFile()
        val server = server { exchange ->
            exchange.sendResponseHeaders(403, -1)
            exchange.close()
        }
        try {
            val result = AudioDownloader(DownloadStore(dir)) { 10L * 1024 * 1024 }.download(
                Track("abc", "Titulo", "Artista"),
                "http://127.0.0.1:${server.address.port}/audio",
                emptyMap(),
                {},
                { false },
            )
            assertTrue(result is DownloadResult.Failed)
            assertTrue((result as DownloadResult.Failed).expired)
        } finally {
            server.stop(0)
            dir.deleteRecursively()
        }
    }

    @Test
    fun cancelLeavesNoPartialOrEntry() {
        val dir = Files.createTempDirectory("openfy-audio-cancel").toFile()
        val server = server { exchange ->
            val body = ByteArray(64 * 1024) { 1 }
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        try {
            val store = DownloadStore(dir)
            val result = AudioDownloader(store) { 10L * 1024 * 1024 }.download(
                Track("abc", "Titulo", "Artista"),
                "http://127.0.0.1:${server.address.port}/audio",
                emptyMap(),
                {},
                { true },
            )
            assertTrue(result is DownloadResult.Cancelled)
            assertFalse(store.isDownloaded("abc"))
            assertFalse(dir.listFiles().orEmpty().any { it.name.endsWith(".part") })
        } finally {
            server.stop(0)
            dir.deleteRecursively()
        }
    }

    @Test
    fun refusesWhenLimitIsFull() {
        val dir = Files.createTempDirectory("openfy-audio-limit").toFile()
        try {
            val result = AudioDownloader(DownloadStore(dir)) { 0L }.download(
                Track("abc", "Titulo", "Artista"),
                "http://127.0.0.1:1/audio",
                emptyMap(),
                {},
                { false },
            )
            assertTrue(result is DownloadResult.Failed)
            assertNotNull((result as DownloadResult.Failed).reason)
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun server(handler: (com.sun.net.httpserver.HttpExchange) -> Unit): HttpServer {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/audio", handler)
        server.start()
        return server
    }
}
