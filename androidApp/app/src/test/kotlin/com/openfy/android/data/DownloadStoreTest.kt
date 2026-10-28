package com.openfy.android.data

import com.openfy.core.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class DownloadStoreTest {

    @Test
    fun putFindRemoveAndRepair() {
        val dir = Files.createTempDirectory("openfy-downloads").toFile()
        try {
            val store = DownloadStore(dir)
            val track = Track("abc", "Titulo", "Artista", durationSec = 10)
            val file = store.fileNameFor(track, "audio/webm")
            val target = java.io.File(dir, file)
            target.writeBytes(ByteArray(32) { 1 })

            store.put(DownloadEntry(track, file, target.length(), 123L))

            assertTrue(store.isDownloaded("abc"))
            assertEquals(file, store.fileFor("abc")?.name)
            assertEquals(32L, store.usedBytes())
            assertEquals(1, store.all().size)

            assertTrue(store.remove("abc").isDownloadedOrNull())
            assertFalse(java.io.File(dir, file).exists())
            assertNull(store.fileFor("abc"))
            assertEquals(1024L * 1024, store.availableBytes(1024L * 1024))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun namesAreSafeAndFollowContentType() {
        assertEquals("webm", DownloadStore.extensionFor("audio/webm; codecs=opus"))
        assertEquals("opus", DownloadStore.extensionFor("audio/ogg"))
        assertEquals("m4a", DownloadStore.extensionFor(null))
        assertEquals("a_b_c", DownloadStore.sanitizeId("a/b c"))
        assertEquals("___", DownloadStore.sanitizeId("///"))
    }

    @Test
    fun repairRemovesOrphanFilesAndTruncatedEntries() {
        val dir = Files.createTempDirectory("openfy-downloads-orphans").toFile()
        try {
            val store = DownloadStore(dir)
            val intact = Track("intacta", "Titulo", "Artista")
            val intactFile = java.io.File(dir, store.fileNameFor(intact, "audio/webm"))
            intactFile.writeBytes(ByteArray(16) { 1 })
            store.put(DownloadEntry(intact, intactFile.name, intactFile.length(), 1L))

            val truncated = Track("truncada", "Titulo", "Artista")
            val truncatedFile = java.io.File(dir, store.fileNameFor(truncated, null))
            truncatedFile.writeBytes(ByteArray(4) { 1 })
            store.put(DownloadEntry(truncated, truncatedFile.name, 32L, 1L))

            val orphan = java.io.File(dir, "orfao.webm")
            orphan.writeBytes(ByteArray(8) { 1 })

            val fixed = store.repair()

            assertTrue(fixed >= 2)
            assertEquals(listOf("intacta"), store.all().map { it.track.id })
            assertTrue(intactFile.isFile)
            assertFalse(truncatedFile.exists())
            assertFalse(orphan.exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun repairDropsMissingFilesAndPartialFiles() {
        val dir = Files.createTempDirectory("openfy-downloads-repair").toFile()
        try {
            val store = DownloadStore(dir)
            val track = Track("sumiu", "Titulo", "Artista")
            store.put(DownloadEntry(track, "sumiu.m4a", 10L, 1L))
            java.io.File(dir, "sumiu.m4a.part").writeText("parcial")

            assertEquals(2, store.repair())
            assertTrue(store.all().isEmpty())
            assertFalse(java.io.File(dir, "sumiu.m4a.part").exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun DownloadEntry?.isDownloadedOrNull(): Boolean = this != null
}
