package com.openfy.core

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Estimativa de BPM por envelope de onsets + autocorrelacao. Nao depende de
 * biblioteca externa: recebe PCM mono (float) e devolve o pulso dominante.
 * Feito para o laboratorio de mix; ainda nao entra no fluxo de reproducao.
 */
object BpmAnalyzer {

    data class Result(
        val bpm: Double,
        val confidence: Double,
        val lagFrames: Int,
    ) {
        val label: String get() = String.format("%.1f BPM", bpm)
    }

    fun analyze(
        samples: FloatArray,
        sampleRate: Int,
        minBpm: Double = 60.0,
        maxBpm: Double = 200.0,
    ): Result? {
        if (samples.size < sampleRate * 6) return null

        val frame = 1024
        val hop = 512
        val frames = (samples.size - frame) / hop
        if (frames < 64) return null

        val energy = DoubleArray(frames)
        for (i in 0 until frames) {
            var sum = 0.0
            val start = i * hop
            for (j in 0 until frame) {
                val value = samples[start + j].toDouble()
                sum += value * value
            }
            energy[i] = sqrt(sum / frame)
        }

        // Fluxo espectral aproximado: diferenca positiva da energia em log.
        val onset = DoubleArray(frames)
        for (i in 1 until frames) {
            onset[i] = max(0.0, ln(1e-7 + energy[i]) - ln(1e-7 + energy[i - 1]))
        }
        // Suaviza dois vizinhos e remove o nivel medio (autocorr sem DC).
        val smooth = DoubleArray(frames)
        for (i in 1 until frames - 1) {
            smooth[i] = (onset[i - 1] + onset[i] * 2.0 + onset[i + 1]) / 4.0
        }
        val mean = smooth.average()
        for (i in smooth.indices) smooth[i] -= mean

        val frameRate = sampleRate.toDouble() / hop
        val minLag = max(2, floor(frameRate * 60.0 / maxBpm).toInt())
        val maxLag = ceil(frameRate * 60.0 / minBpm).toInt()
        val acSize = maxLag * 3 + 2
        val ac = DoubleArray(acSize)
        for (lag in 2 until acSize) {
            var sum = 0.0
            for (i in lag until smooth.size) sum += smooth[i] * smooth[i - lag]
            ac[lag] = sum / (smooth.size - lag)
        }
        val zero = ac[0].coerceAtLeast(1e-9)
        for (i in ac.indices) ac[i] /= zero

        fun score(lag: Int): Double =
            ac[lag] +
                0.25 * ac.getOrElse(lag / 2) { 0.0 } +
                0.15 * ac.getOrElse(lag * 2) { 0.0 }

        // Picos equivalentes aparecem no dobro do intervalo (bar) e na metade
        // (subdivisao). Escolher o menor pico forte evita detectar 120 como 60.
        var peak = Double.NEGATIVE_INFINITY
        for (lag in minLag..maxLag) if (ac[lag] > peak) peak = ac[lag]
        val threshold = peak * 0.82
        var bestLag = -1
        for (lag in (minLag + 1) until maxLag) {
            if (ac[lag] < threshold) continue
            if (ac[lag] < ac[lag - 1] || ac[lag] < ac[lag + 1]) continue
            bestLag = lag
            break
        }
        if (bestLag < 0) {
            bestLag = minLag
            var best = Double.NEGATIVE_INFINITY
            for (lag in minLag..maxLag) {
                val value = score(lag)
                if (value > best) {
                    best = value
                    bestLag = lag
                }
            }
        }

        // Refina em torno do pico escolhido (a autocorrelacao e discreta).
        var refined = bestLag
        var refinedScore = Double.NEGATIVE_INFINITY
        for (lag in (bestLag - 2)..(bestLag + 2)) {
            if (lag !in minLag..maxLag) continue
            val value = score(lag)
            if (value > refinedScore) {
                refinedScore = value
                refined = lag
            }
        }

        val window = (minLag..maxLag).map { ac[it] }
        val average = window.average()
        val spread = sqrt(window.sumOf { (it - average) * (it - average) } / window.size)
            .coerceAtLeast(1e-9)
        val confidence = ((ac[refined] - average) / spread).coerceIn(0.0, 5.0) / 5.0

        val bpm = 60.0 * frameRate / refined
        return Result(
            bpm = (bpm * 10).roundToInt() / 10.0,
            confidence = confidence,
            lagFrames = refined,
        )
    }

    /** Ordena uma lista por proximidade de BPM (cadeia gulosa), para DJ set. */
    fun orderByBpm(items: List<Pair<String, Double>>): List<Pair<String, Double>> {
        if (items.size < 3) return items
        val remaining = items.toMutableList()
        val ordered = mutableListOf<Pair<String, Double>>()
        var current = remaining.removeAt(0)
        ordered += current
        while (remaining.isNotEmpty()) {
            val next = remaining.minByOrNull { abs(it.second - current.second) } ?: break
            remaining.remove(next)
            ordered += next
            current = next
        }
        return ordered
    }

    /** Distancia de BPM aceitavel para mixar sem esticar demais. */
    fun mixable(a: Double, b: Double): Boolean {
        val diff = abs(a - b)
        return diff <= 6.0 || abs(diff - a / 2) <= 4.0 || abs(diff - a * 2) <= 4.0
    }

    fun halfDouble(bpm: Double): List<Double> = listOf(bpm / 2, bpm, min(bpm * 2, 300.0))
}
