package com.openfy.android.data

import com.openfy.core.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.file.Files

class HomeCacheTest {

    @Test
    fun savesLoadsAndClearsSnapshot() {
        val dir = Files.createTempDirectory("openfy-home-cache").toFile()
        try {
            val cache = HomeCache(dir)
            val snapshot = HomeSnapshot(
                userId = "user-1",
                savedAtMs = 42L,
                recentTracks = listOf(Track("t1", "Titulo", "Artista")),
            )
            cache.save(snapshot)
            val loaded = cache.load()
            assertNotNull(loaded)
            assertEquals("user-1", loaded?.userId)
            assertEquals(42L, loaded?.savedAtMs)
            assertEquals("t1", loaded?.recentTracks?.firstOrNull()?.id)

            cache.clear()
            assertNull(cache.load())
        } finally {
            dir.deleteRecursively()
        }
    }
}
