package com.openfy.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioListTest {

    private fun track(id: String, title: String = "Musica $id", artist: String = "Artista") =
        Track(id = id, title = title, artist = artist)

    @Test
    fun `a semente abre a lista mesmo quando o rank a descartou`() {
        val seed = track("seed", "Minha musica")
        val ranked = listOf(track("a"), track("b"))
        val merged = RadioList.merge(listOf(seed), ranked)
        assertEquals(seed.id, merged.first().id)
        assertEquals(3, merged.size)
    }

    @Test
    fun `merge nao duplica a semente nem a mesma musica em outro video`() {
        val seed = track("seed", "Minha musica")
        val sameSong = track("outro-video", "Minha musica")
        val merged = RadioList.merge(listOf(seed), listOf(sameSong, track("b")))
        assertEquals(listOf("seed", "b"), merged.map { it.id })
        assertTrue(merged.size == 2)
    }

    @Test
    fun `merge respeita o limite`() {
        val seed = track("seed")
        val merged = RadioList.merge(listOf(seed), (1..10).map { track("t$it") }, limit = 4)
        assertEquals(4, merged.size)
        assertEquals("seed", merged.first().id)
    }
}
