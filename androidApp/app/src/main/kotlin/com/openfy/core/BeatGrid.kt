package com.openfy.core

import kotlin.math.ceil
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Grade ritmica de uma faixa, usada pelo Modo DJ.
 *
 * - [bpm] e o pulso dominante e [firstBeatSec] a fase (onde cai a primeira
 *   batida depois do inicio do arquivo).
 * - [firstDownbeatSec] marca o "1" do compasso de [beatsPerBar] tempos.
 * - [rmsDb] e a loudness tipica do trecho analisado: serve para o ganho de
 *   entrada/saida do mix nao deixar uma faixa mais alta que a outra.
 */
data class BeatGrid(
    val bpm: Double,
    val confidence: Double,
    val firstBeatSec: Double,
    val firstDownbeatSec: Double,
    val beatsPerBar: Int = 4,
    val durationSec: Double = 0.0,
    val rmsDb: Double = TARGET_DB,
) {

    val beatSec: Double get() = 60.0 / bpm.coerceAtLeast(20.0)
    val barSec: Double get() = beatSec * beatsPerBar.coerceAtLeast(1)

    /** Fase do compasso, sempre dentro de um compasso. */
    val phaseSec: Double get() = wrap(firstDownbeatSec, barSec)

    fun beatAt(index: Int): Double = firstBeatSec + index * beatSec

    fun barAt(index: Int): Double = firstDownbeatSec + index * barSec

    /** Proxima batida estritamente depois de [seconds]. */
    fun nextBeat(seconds: Double): Double {
        val period = beatSec
        val n = ceil((seconds - firstBeatSec) / period - 1e-6)
        return firstBeatSec + n * period
    }

    /** Proximo inicio de compasso (downbeat) depois de [seconds]. */
    fun nextBar(seconds: Double): Double {
        val period = barSec
        val n = ceil((seconds - firstDownbeatSec) / period - 1e-6)
        return firstDownbeatSec + n * period
    }

    /** Compasso mais proximo de [seconds]. */
    fun nearestBar(seconds: Double): Double {
        val n = ((seconds - firstDownbeatSec) / barSec).roundToInt()
        return firstDownbeatSec + n * barSec
    }

    /** Ganho linear que aproxima esta faixa do alvo de loudness. */
    fun gain(maxDb: Double = 6.0): Double {
        val db = (TARGET_DB - rmsDb).coerceIn(-maxDb, maxDb)
        return 10.0.pow(db / 20.0)
    }

    companion object {
        /** Loudness alvo (RMS do trecho) usada para casar as faixas. */
        const val TARGET_DB = -14.0

        /** Abaixo disso o beatmatch e arriscado; caimos no N1. */
        const val MIN_CONFIDENCE = 0.28

        fun wrap(value: Double, period: Double): Double {
            if (period <= 0.0 || !value.isFinite()) return 0.0
            val r = value % period
            return if (r < 0) r + period else r
        }
    }
}
