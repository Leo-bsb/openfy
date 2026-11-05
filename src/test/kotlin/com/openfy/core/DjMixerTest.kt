package com.openfy.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.round

class DjMixerTest {

    private fun grid(
        bpm: Double,
        confidence: Double = 0.6,
        downbeat: Double = 0.28,
        rms: Double = BeatGrid.TARGET_DB,
        duration: Double = 240.0,
    ) = BeatGrid(
        bpm = bpm,
        confidence = confidence,
        firstBeatSec = downbeat,
        firstDownbeatSec = downbeat,
        beatsPerBar = 4,
        durationSec = duration,
        rmsDb = rms,
    )

    @Test
    fun `casa o tempo quando a diferenca esta dentro de oito por cento`() {
        val plan = DjMixer.plan(grid(128.0), grid(124.0), 240.0, 8.0, 8)
        assertTrue(plan.beatmatched)
        assertEquals(128.0 / 124.0, plan.speed, 0.001)
        assertEquals(0.28, plan.entrySeconds, 0.001)
        assertTrue(plan.fadeSeconds >= 2.0)
    }

    @Test
    fun `cai no n1 quando o tempo nao casa`() {
        val plan = DjMixer.plan(grid(128.0), grid(100.0), 240.0, 8.0, 8)
        assertFalse(plan.beatmatched)
        assertEquals(1.0, plan.speed, 0.0001)
    }

    @Test
    fun `meio tempo tambem casa o pulso`() {
        val plan = DjMixer.plan(grid(128.0), grid(64.0), 240.0, 8.0, 8)
        assertTrue(plan.beatmatched)
        assertEquals(1.0, plan.speed, 0.001)
    }

    @Test
    fun `grade sem confianca nao entra no beatmatch`() {
        val plan = DjMixer.plan(grid(128.0), grid(127.0, confidence = 0.05), 240.0, 8.0, 8)
        assertFalse(plan.beatmatched)
    }

    @Test
    fun `ganho abaixa a faixa que sai quando ela e mais alta`() {
        val plan = DjMixer.plan(
            grid(128.0, rms = -8.0),
            grid(128.0, rms = -15.0),
            durationSec = 240.0,
            remainingSec = 8.0,
            fadeSeconds = 8,
        )
        assertTrue("ganho ${plan.oldGain}", plan.oldGain < 0.9)
        assertEquals(1.0, plan.newGain, 0.0001)
    }

    @Test
    fun `fade encaixa no compasso quando ha grade`() {
        val plan = DjMixer.plan(grid(120.0), grid(120.0), 240.0, 9.0, 9)
        val bar = 4 * 60.0 / 120.0
        // O fade termina no fim da faixa e comeca num downbeat (frase).
        val start = 240.0 - plan.fadeSeconds
        val wrapped = BeatGrid.wrap(start - 0.28, bar)
        val distance = minOf(wrapped, bar - wrapped)
        assertTrue("inicio ${start}s fora do compasso ($bar s)", distance < 0.15)
        assertEquals(9.0, plan.fadeSeconds, 1.5)
    }

    @Test
    fun `sem grades usa o fade configurado`() {
        val plan = DjMixer.plan(null, null, 240.0, 6.0, 6)
        assertFalse(plan.beatmatched)
        assertEquals(1.0, plan.speed, 0.0001)
        assertEquals(6.0, plan.fadeSeconds, 0.2)
    }

    @Test
    fun `bass swap termina na metade do fade sem saltos`() {
        assertEquals(0.0, DjMixer.exitBassDb(0.0), 0.001)
        assertEquals(-8.0, DjMixer.exitBassDb(0.5), 0.001)
        assertEquals(-8.0, DjMixer.exitBassDb(1.0), 0.001)
        assertEquals(-12.0, DjMixer.entryBassDb(0.0), 0.001)
        assertEquals(0.0, DjMixer.entryBassDb(0.5), 0.001)

        var previous = DjMixer.exitBassDb(0.0)
        for (step in 1..50) {
            val value = DjMixer.exitBassDb(step / 100.0)
            assertTrue("rampa subiu em $step", value <= previous + 1e-9)
            previous = value
        }
    }

    @Test
    fun `filtros do mpv acompanham a rampa e somem no meio`() {
        assertEquals("highpass=f=190:width_type=q:w=0.7", DjMixer.entryFilter(0.0))
        assertNull(DjMixer.entryFilter(0.5))
        assertNull(DjMixer.exitFilter(0.0))
        assertEquals("lowshelf=f=190:g=-8.0", DjMixer.exitFilter(0.5))
    }
}
