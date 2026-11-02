package com.openfy.core

import kotlin.math.log10
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Detecta pulso, fase e inicio de compasso de um trecho PCM mono.
 *
 * Usa o mesmo envelope de onsets do laboratorio de BPM, mas vai alem do pulso:
 * testa todas as fases possiveis para achar onde as batidas realmente caem e,
 * dentro delas, qual das quatro posicoes e o "1" do compasso. Tambem mede a
 * loudness tipica do trecho para o controle de ganho do Modo DJ.
 */
object BeatAnalyzer {

    private const val FRAME = 1024
    private const val HOP = 512

    /** Passo fino usado para a fase: 128 amostras = 5,8 ms a 22,05 kHz. */
    private const val FINE_HOP = 128

    fun analyze(
        samples: FloatArray,
        sampleRate: Int,
        startSec: Double = 0.0,
        durationSec: Double = 0.0,
        minBpm: Double = 70.0,
        maxBpm: Double = 185.0,
    ): BeatGrid? {
        if (sampleRate <= 0 || samples.size < sampleRate * 6) return null
        val tempo = BpmAnalyzer.analyze(samples, sampleRate, minBpm, maxBpm) ?: return null
        if (!tempo.bpm.isFinite() || tempo.bpm <= 0.0) return null

        val onset = onsetEnvelope(samples, FRAME, HOP)
        if (onset.size < 48) return null

        // A autocorrelacao trabalha em lags inteiros: em ~120 BPM um frame de
        // erro ja significa ~2,5 BPM. Por isso a busca de fase cobre ±3,5% em
        // passos de 0,05% e fica com o BPM que deixa as batidas mais nitidas.
        var bestBpm = tempo.bpm
        var best = phaseSearch(onset, bestBpm, sampleRate, HOP)
        for (step in -70..70) {
            if (step == 0) continue
            val candidateBpm = tempo.bpm * (1.0 + step * 0.0005)
            if (candidateBpm !in minBpm..maxBpm) continue
            val candidate = phaseSearch(onset, candidateBpm, sampleRate, HOP)
            if (candidate.score > best.score) {
                best = candidate
                bestBpm = candidateBpm
            }
        }
        val coarseBpm = bestBpm
        for (step in -10..10) {
            if (step == 0) continue
            val candidateBpm = coarseBpm * (1.0 + step * 0.00005)
            if (candidateBpm !in minBpm..maxBpm) continue
            val candidate = phaseSearch(onset, candidateBpm, sampleRate, HOP)
            if (candidate.score > best.score) {
                best = candidate
                bestBpm = candidateBpm
            }
        }
        if (!bestBpm.isFinite() || bestBpm !in minBpm..maxBpm) return null

        // O envelope de 11,6 ms erra a posicao da batida em quase um frame.
        // A fase final usa um envelope de 5,8 ms para entrada e bass swap
        // cairem no tempo certo, e ajusta o BPM em ±0,1% nessa resolucao.
        val fineOnset = onsetEnvelope(samples, FRAME, FINE_HOP)
        var fine = phaseSearch(fineOnset, bestBpm, sampleRate, FINE_HOP)
        for (step in -20..20) {
            if (step == 0) continue
            val candidateBpm = bestBpm * (1.0 + step * 0.00005)
            if (candidateBpm !in minBpm..maxBpm) continue
            val candidate = phaseSearch(fineOnset, candidateBpm, sampleRate, FINE_HOP)
            if (candidate.score > fine.score) {
                fine = candidate
                bestBpm = candidateBpm
            }
        }
        val periodFrames = fine.periodFrames
        val phaseConfidence = (fine.z / (fine.z + 3.0)).coerceIn(0.0, 1.0)

        // Downbeat: entre as quatro posicoes do compasso, a que acumula mais
        // ataque de graves costuma ser o "1" (kick). A banda baixa evita que
        // um hi-hat ou uma guitarra no contratempo engane a frase.
        val downOnset = lowBandOnset(samples, sampleRate, FRAME, FINE_HOP)
        val down = if (downOnset.size >= 48) downOnset else fineOnset
        var bestOffset = 0
        var bestOffsetScore = Double.NEGATIVE_INFINITY
        for (offset in 0 until 4) {
            var sum = 0.0
            var beat = fine.phaseFrames + offset * periodFrames
            while (beat < down.size) {
                sum += strength(down, beat)
                beat += periodFrames * 4
            }
            if (sum > bestOffsetScore) {
                bestOffsetScore = sum
                bestOffset = offset
            }
        }

        val beatSec = 60.0 / bestBpm
        // O envelope usa a energia da janela [i*hop, i*hop+FRAME]; o ataque
        // que o diferencia entra pela borda direita, entao o instante real da
        // batida e i*hop + FRAME, nao o inicio da janela.
        val firstBeat = startSec + (fine.phaseFrames * FINE_HOP + FRAME).toDouble() / sampleRate
        val firstDownbeat = firstBeat + bestOffset * beatSec
        val confidence = (tempo.confidence * 0.55 + phaseConfidence * 0.45).coerceIn(0.0, 1.0)

        return BeatGrid(
            bpm = (bestBpm * 100).roundToInt() / 100.0,
            confidence = confidence,
            firstBeatSec = firstBeat,
            firstDownbeatSec = firstDownbeat,
            beatsPerBar = 4,
            durationSec = if (durationSec > 0.0) durationSec else samples.size / sampleRate.toDouble(),
            rmsDb = typicalRmsDb(samples, sampleRate),
        )
    }

    private class Phase(val phaseFrames: Int, val z: Double, val score: Double, val periodFrames: Double)

    /**
     * Melhor fase para um BPM. [score] e a soma dos ataques que caem nas
     * batidas (quanto mais alto, melhor o BPM); [z] mede o contraste entre a
     * melhor fase e as outras, usado como confianca.
     */
    private fun phaseSearch(onset: DoubleArray, bpm: Double, sampleRate: Int, hop: Int): Phase {
        val periodFrames = 60.0 / bpm * sampleRate / hop
        if (periodFrames < 4.0) return Phase(0, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, periodFrames)
        val steps = periodFrames.roundToInt().coerceAtLeast(4)
        val scores = DoubleArray(steps)
        for (phase in 0 until steps) {
            var sum = 0.0
            var beat = phase.toDouble()
            while (beat < onset.size) {
                sum += strength(onset, beat)
                beat += periodFrames
            }
            scores[phase] = sum
        }
        var bestIndex = 0
        for (i in 1 until steps) if (scores[i] > scores[bestIndex]) bestIndex = i
        val mean = scores.average()
        val spread = sqrt(scores.sumOf { (it - mean) * (it - mean) } / scores.size)
            .coerceAtLeast(1e-9)
        return Phase(
            bestIndex,
            (scores[bestIndex] - mean) / spread,
            scores[bestIndex],
            periodFrames,
        )
    }

    /** Envelope de onsets (diferenca positiva da energia em log, suavizada). */
    private fun onsetEnvelope(samples: FloatArray, frame: Int, hop: Int): DoubleArray {
        val frames = (samples.size - frame).coerceAtLeast(0) / hop
        if (frames <= 0) return DoubleArray(0)
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
        val onset = DoubleArray(frames)
        for (i in 1 until frames) {
            onset[i] = max(0.0, lnSafe(energy[i]) - lnSafe(energy[i - 1]))
        }
        val smooth = DoubleArray(frames)
        for (i in 1 until frames - 1) {
            smooth[i] = (onset[i - 1] + onset[i] * 2.0 + onset[i + 1]) / 4.0
        }
        // Remove o nivel medio para a comparacao entre fases nao ser dominada
        // pelo volume absoluto do trecho.
        val mean = smooth.average()
        for (i in smooth.indices) smooth[i] = (smooth[i] - mean).coerceAtLeast(0.0)
        return smooth
    }

    private fun lnSafe(value: Double): Double = kotlin.math.ln(1e-7 + value)

    /**
     * Envelope de ataques so na banda baixa (~180 Hz): usado para achar o
     * downbeat pelo kick, sem o brilho de caixas e pratos.
     */
    private fun lowBandOnset(
        samples: FloatArray,
        sampleRate: Int,
        frame: Int,
        hop: Int,
    ): DoubleArray {
        val frames = (samples.size - frame).coerceAtLeast(0) / hop
        if (frames <= 0) return DoubleArray(0)
        val rc = 1.0 / (2.0 * Math.PI * 180.0)
        val dt = 1.0 / sampleRate
        val alpha = dt / (rc + dt)
        val energy = DoubleArray(frames)
        var filtered = 0.0
        var next = 0
        for (i in 0 until frames) {
            val start = i * hop
            var sum = 0.0
            while (next < start + frame) {
                filtered += alpha * (samples[next] - filtered)
                sum += filtered * filtered
                next++
            }
            energy[i] = sqrt(sum / hop)
        }
        val onset = DoubleArray(frames)
        for (i in 1 until frames) {
            onset[i] = max(0.0, lnSafe(energy[i]) - lnSafe(energy[i - 1]))
        }
        val smooth = DoubleArray(frames)
        for (i in 1 until frames - 1) {
            smooth[i] = (onset[i - 1] + onset[i] * 2.0 + onset[i + 1]) / 4.0
        }
        val mean = smooth.average()
        for (i in smooth.indices) smooth[i] = (smooth[i] - mean).coerceAtLeast(0.0)
        return smooth
    }

    /** Ataque na posicao [at], tolerando um frame de erro de arredondamento. */
    private fun strength(onset: DoubleArray, at: Double): Double {
        val i = at.roundToInt()
        if (i < 0 || i >= onset.size) return 0.0
        var best = onset[i]
        if (i > 0) best = max(best, onset[i - 1])
        if (i + 1 < onset.size) best = max(best, onset[i + 1])
        return best
    }

    /**
     * RMS tipico (percentil 70 dos frames) em dBFS: ignora introducoes silenciosas
     * e silencios deixa a comparacao de volume entre faixas mais justa.
     */
    private fun typicalRmsDb(samples: FloatArray, sampleRate: Int): Double {
        val step = (sampleRate / 10).coerceAtLeast(1)
        val values = ArrayList<Double>(samples.size / step + 1)
        var index = 0
        while (index + step <= samples.size) {
            var sum = 0.0
            for (i in 0 until step) {
                val value = samples[index + i].toDouble()
                sum += value * value
            }
            values += sqrt(sum / step)
            index += step
        }
        if (values.isEmpty()) return BeatGrid.TARGET_DB
        values.sort()
        val pick = (values.size * 0.7).toInt().coerceIn(0, values.lastIndex)
        val rms = values[pick].coerceAtLeast(1e-5)
        return (20.0 * log10(rms)).coerceIn(-60.0, 0.0)
    }
}
