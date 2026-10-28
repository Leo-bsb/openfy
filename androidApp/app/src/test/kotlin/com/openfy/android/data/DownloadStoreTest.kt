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
