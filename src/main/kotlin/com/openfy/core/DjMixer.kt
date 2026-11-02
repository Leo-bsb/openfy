package com.openfy.core

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Plano de uma transicao do Modo DJ.
 *
 * [beatmatched] = true significa N2: o tempo da faixa que entra foi casado com
 * o da que sai e a entrada acontece num downbeat. Caso contrario e N1 (smart
 * fade): ainda usa ganho e frase quando ha grade, mas sem esticar o tempo.
 */
data class MixPlan(
    val beatmatched: Boolean,
    val fadeSeconds: Double,
    val speed: Double,
    val entrySeconds: Double,
    val oldGain: Double,
    val newGain: Double,
    val outgoingBpm: Double,
    val incomingBpm: Double,
    val reason: String,
) {
    val bars: Int get() = if (outgoingBpm <= 0.0) 0 else (fadeSeconds / (4 * 60.0 / outgoingBpm)).roundToInt()
}

/**
 * Decide como a faixa atual sai e a proxima entra (N1 e N2).
 *
 * Regras:
 *  - N2 so com as duas grades confiaveis e diferenca de tempo <= 8% (com
 *    ajuste de meio/dobro tempo, que continua batendo);
 *  - o fade dura um numero inteiro de compassos e termina no fim da faixa,
 *    comecando num downbeat (frase);
 *  - a faixa que entra comeca no primeiro downbeat audivel;
 *  - o ganho da que sai acompanha a loudness da que entra, sem estourar.
 */
object DjMixer {

    /** Tolerancia de tempo do N2 (mpv usa scaletempo2, entao o tom se mantem). */
    const val MAX_SPEED_DELTA = 0.08

    fun plan(
        outgoing: BeatGrid?,
        incoming: BeatGrid?,
        durationSec: Double,
        remainingSec: Double,
        fadeSeconds: Int,
    ): MixPlan {
        val configured = (if (fadeSeconds <= 0) 6 else fadeSeconds).toDouble()
        val room = remainingSec.coerceAtLeast(0.0)
        var fade = minOf(configured, room.coerceAtLeast(1.0)).coerceAtLeast(1.0)

        val outOk = outgoing != null &&
            outgoing.confidence >= BeatGrid.MIN_CONFIDENCE &&
            outgoing.bpm in 50.0..220.0
        val inOk = incoming != null &&
            incoming.confidence >= BeatGrid.MIN_CONFIDENCE &&
            incoming.bpm in 50.0..220.0

        val outBpm = if (outOk) outgoing!!.bpm else 0.0
        val inBpm = if (inOk) incoming!!.bpm else 0.0

        var speed = 1.0
        var entry = 0.0
        var beatmatched = false
        var reason = "N1"

        if (outOk && inOk) {
            val rate = outBpm / inBpm
            // Meio/dobro tempo tambem casa o pulso: 60 BPM entra em 120 BPM.
            val candidate = listOf(rate, rate * 2.0, rate / 2.0)
                .firstOrNull { abs(it - 1.0) <= MAX_SPEED_DELTA }
            if (candidate != null) {
                speed = candidate
                entry = incoming!!.firstDownbeatSec.coerceIn(0.0, 8.0)
                beatmatched = true
                reason = "N2 ${"%.1f".format(outBpm)}→${"%.1f".format(inBpm)} BPM"
                val bar = 4 * 60.0 / outBpm
                val bars = (fade / bar).roundToInt().coerceIn(2, 16)
                fade = bars * bar
            } else {
                reason = "N1 (${"%.1f".format(outBpm)} x ${"%.1f".format(inBpm)} BPM)"
            }
        } else if (!outOk && incoming != null) {
            // Sem grade da faixa atual, mas com a da proxima: entra no downbeat.
            entry = incoming.firstDownbeatSec.coerceIn(0.0, 8.0)
        }

        // Encaixa o fade em compassos quando a grade da faixa atual existe:
        // comeca num downbeat e termina junto com o fim da faixa.
        if (outOk && durationSec > 0.0) {
            val start = outgoing!!.nearestBar((durationSec - fade).coerceIn(0.0, durationSec))
            val snapped = durationSec - start
            if (snapped in 2.0..30.0) fade = snapped
        }
        // Nao limitamos ao tempo restante: o plano define quando o mix deve
        // comecar (remaining <= fade). Se a faixa ja estiver no fim, o
        // startCrossfade corta o fade para caber.
        fade = fade.coerceIn(1.0, 30.0)

        // Loudness: a faixa que sai e ajustada para o nivel da que entra. A que
        // entra fica em 1.0, entao o volume final do app nao muda no handoff.
        val outLoud = if (outgoing != null) outgoing.rmsDb else BeatGrid.TARGET_DB
        val inLoud = if (incoming != null) incoming.rmsDb else BeatGrid.TARGET_DB
        val oldGain = 10.0.pow((inLoud - outLoud) / 20.0).coerceIn(0.5, 1.25)

        return MixPlan(
            beatmatched = beatmatched,
            fadeSeconds = fade,
            speed = speed,
            entrySeconds = entry,
            oldGain = oldGain,
            newGain = 1.0,
            outgoingBpm = outBpm,
            incomingBpm = inBpm,
            reason = reason,
        )
    }
}
