package com.openfy.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class QueueLogicTest {

    private fun track(id: String) = Track(id, "Faixa $id", "Artista")

    private fun queue(vararg ids: String) = ids.map(::track)

    @Test
    fun shuffleKeepsCurrentTrackFirst() {
        val original = queue("a", "b", "c", "d", "e")
        val (shuffled, index) = QueueLogic.shuffled(original, currentIndex = 2, random = Random(7))

        assertEquals(0, index)
        assertEquals("c", shuffled.first().id)
        assertEquals(original.map { it.id }.toSet(), shuffled.map { it.id }.toSet())
    }

    @Test
    fun shuffleWithSingleTrackKeepsIt() {
        val (shuffled, index) = QueueLogic.shuffled(queue("a"), currentIndex = 0, random = Random(1))
        assertEquals(listOf("a"), shuffled.map { it.id })
        assertEquals(0, index)
    }

    @Test
    fun restoreKeepsCurrentTrackAndExtras() {
        val snapshot = queue("a", "b", "c")
        val current = queue("c", "a", "b", "d")
        val (restored, index) = QueueLogic.restored(snapshot, current, currentTrackId = "b")

        assertEquals(listOf("a", "b", "c", "d"), restored.map { it.id })
        assertEquals(1, index)
    }

    @Test
    fun moveReordersAndClampsTarget() {
        val original = queue("a", "b", "c", "d")
        assertEquals(listOf("b", "a", "c", "d"), QueueLogic.moved(original, 1, 0).map { it.id })
        assertEquals(listOf("a", "c", "d", "b"), QueueLogic.moved(original, 1, 99).map { it.id })
        assertEquals(original, QueueLogic.moved(original, 9, 0))
    }

    @Test
    fun autoNextWrapsOnlyWithRepeatAll() {
        assertEquals(2, QueueLogic.autoNextIndex(1, 3, RepeatMode.OFF))
        assertEquals(0, QueueLogic.autoNextIndex(2, 3, RepeatMode.ALL))
        assertNull(QueueLogic.autoNextIndex(2, 3, RepeatMode.OFF))
        assertNull(QueueLogic.autoNextIndex(2, 3, RepeatMode.ONE))
        assertNull(QueueLogic.autoNextIndex(-1, 0, RepeatMode.ALL))
        assertTrue(QueueLogic.autoNextIndex(0, 1, RepeatMode.ALL) == 0)
    }
}
