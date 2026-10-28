package com.openfy.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun parseVersionAcceptsOnlyThreeNumbers() {
        assertEquals(listOf(0, 2, 8), UpdateChecker.parseVersion("0.2.8")?.toList())
        assertEquals(listOf(10, 0, 12), UpdateChecker.parseVersion(" 10.0.12 ")?.toList())
        assertNull(UpdateChecker.parseVersion("0.2"))
        assertNull(UpdateChecker.parseVersion("0.2.8-beta"))
        assertNull(UpdateChecker.parseVersion("v0.2.8"))
        assertNull(UpdateChecker.parseVersion("0.x.8"))
    }

    @Test
    fun compareOrdersByMajorThenMinorThenPatch() {
        assertTrue(UpdateChecker.compare(intArrayOf(0, 3, 0), intArrayOf(0, 2, 9)) > 0)
        assertTrue(UpdateChecker.compare(intArrayOf(1, 0, 0), intArrayOf(0, 99, 99)) > 0)
        assertEquals(0, UpdateChecker.compare(intArrayOf(0, 2, 8), intArrayOf(0, 2, 8)))
        assertTrue(UpdateChecker.compare(intArrayOf(0, 2, 8), intArrayOf(0, 2, 10)) < 0)
    }

    @Test
    fun autoCheckRunsAtMostOncePerDay() {
        val now = 1_800_000_000_000L
        assertTrue(UpdateChecker.shouldAutoCheck(null, now))
        assertFalse(UpdateChecker.shouldAutoCheck(now - 1_000, now))
        assertFalse(UpdateChecker.shouldAutoCheck(now - 23 * 3_600_000L, now))
        assertTrue(UpdateChecker.shouldAutoCheck(now - 24 * 3_600_000L, now))
    }
}
