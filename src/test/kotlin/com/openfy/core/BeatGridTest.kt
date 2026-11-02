package com.openfy.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class BeatGridTest {

    /**
     * Pulso sintetico: tempo forte (grave, mais alto) a cada 4 batidas, como
     * um compasso 4/4. Serve para validar pulso, fase e downbeat sem depender
     * de arquivo de audio.
     */
    private fun clickTrack(
        bpm: Double,
        seconds: Int = 24,
        sampleRate: Int = 22050,
        start: Double = 0.15,
        beatsPerBar: Int = 4,
    ): FloatArray {
        val samples = FloatArray(sampleRate * seconds)
        val beat = 60.0 / bpm
        var time = start
        var index = 0
        while (time < seconds - 0.1) {
            val startIndex = (time * sampleRate).toInt()
            val length = (0.06 * sampleRate).toInt()
            val accent = index % beatsPerBar == 0
            val amplitude = if (accent) 0.9 else 0.45
            val frequency = if (accent) 90.0 else 1200.0
            for (i in 0 until length) {
                val position = startIndex + i
                if (position >= samples.size) break
                val envelope = 1.0 - i.toDouble() / length
                samples[position] +=
                    (sin(2 * PI * frequency * i / sampleRate) * envelope * amplitude).toFloat()
            }
            time += beat
            index++
        }
        return samples
    }

    /** Distancia circular ate [target] dentro de um compasso. */
    private fun distanceInBar(value: Double, target: Double, bar: Double): Double {
        val wrapped = BeatGrid.wrap(value - target, bar)
        return minOf(wrapped, bar - wrapped)
    }

    @Test
    fun `detecta pulso e fase de um compasso sintetico`() {
        val grid = BeatAnalyzer.analyze(clickTrack(128.0), 22050)
        assertNotNull(grid)
        assertEquals(128.0, grid!!.bpm, 1.5)
        assertTrue("confianca baixa: ${grid.confidence}", grid.confidence > 0.3)
        assertEquals(0.15, grid.firstBeatSec, 0.09)
    }

    @Test
    fun `acha o downbeat acentuado`() {
        val grid = BeatAnalyzer.analyze(clickTrack(120.0), 22050)
        assertNotNull(grid)
        val bar = grid!!.barSec
        assertTrue(
            "downbeat ${grid.firstDownbeatSec} fora do compasso (barra=$bar)",
            distanceInBar(grid.firstDownbeatSec, 0.15, bar) < 0.12,
        )
    }

    @Test
    fun `calibra bpm fase e downbeat em varios tempos`() {
        for (bpm in listOf(90.0, 120.0, 128.0, 174.0)) {
            val grid = BeatAnalyzer.analyze(clickTrack(bpm), 22050)
            assertNotNull("sem grade para $bpm BPM", grid)
            assertEquals("BPM $bpm", bpm, grid!!.bpm, 0.15)
            assertEquals("fase $bpm", 0.15, grid.firstBeatSec, 0.03)
            val bar = grid.barSec
            assertTrue(
                "downbeat $bpm em ${grid.firstDownbeatSec}s",
                distanceInBar(grid.firstDownbeatSec, 0.15, bar) < 0.05,
            )
        }
    }

    @Test
    fun `estima loudness para o ganho do mix`() {
        // Sinal continuo: o RMS do trecho fica estavel e o teste mede mesmo o
        // ganho, sem depender da densidade dos cliques.
        val loud = FloatArray(22050 * 12) {
            (sin(2 * PI * 220.0 * it / 22050) * 0.30).toFloat()
        }
        val quiet = FloatArray(loud.size) { loud[it] * 0.08f }
        val loudGrid = BeatAnalyzer.analyze(loud, 22050)
        val quietGrid = BeatAnalyzer.analyze(quiet, 22050)
        assertNotNull(loudGrid)
        assertNotNull(quietGrid)
        assertTrue(
            "rms ${loudGrid!!.rmsDb} x ${quietGrid!!.rmsDb}",
            quietGrid.rmsDb < loudGrid.rmsDb - 10.0,
        )
        assertTrue(quietGrid.gain() > loudGrid.gain())
        assertTrue(quietGrid.gain(maxDb = 6.0) <= 2.01)
    }

    @Test
    fun `trecho curto demais nao vira grade`() {
        assertNull(BeatAnalyzer.analyze(FloatArray(22050 * 3), 22050))
    }
}
