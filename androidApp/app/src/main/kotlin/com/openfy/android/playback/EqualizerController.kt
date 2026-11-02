package com.openfy.android.playback

import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Equalizador de 10 bandas lógicas sobre o AudioEffect do Android. Cada
 * ExoPlayer (A/B do crossfade) tem seu próprio efeito, mas os ganhos são
 * compartilhados.
 *
 * O AudioEffect do sistema costuma baixar o volume geral quando entra em ação
 * (proteção contra clipping do SoundAlive/Samsung). Para compensar, um
 * LoudnessEnhancer na mesma sessão devolve parte do nível. Com a curva flat o
 * efeito fica desligado, então ligar o EQ sem mexer em nada não muda o som.
 *
 * Aparelhos com menos bandas físicas (5, por exemplo) recebem a curva de 10
 * interpolada por distância, em vez de descartar as bandas extras.
 */
class EqualizerController {

    private val effects = mutableMapOf<Any, Equalizer>()
    private val enhancers = mutableMapOf<Any, LoudnessEnhancer>()
    private val bassCuts = mutableMapOf<Any, Int>()
    private var enabled = false
    private var gains = DEFAULT_GAINS

    fun attach(key: Any, sessionId: Int) {
        val cut = bassCuts[key] ?: 0
        release(key)
        if (sessionId <= 0) return
        val effect = runCatching { Equalizer(0, sessionId) }.getOrNull() ?: return
        effects[key] = effect
        if (cut != 0) bassCuts[key] = cut
        val active = (enabled && !isFlat()) || cut != 0
        runCatching { effect.enabled = active }
        if (active) apply(effect, cut)
        val enhancer = runCatching { LoudnessEnhancer(sessionId) }.getOrNull()
        if (enhancer != null) {
            enhancers[key] = enhancer
            applyCompensation(enhancer)
        }
    }

    /**
     * Corta os graves deste player (dB negativo). Usado pelo Modo DJ no bass
     * swap: a faixa que entra fica sem graves ate a metade do fade.
     */
    fun setBassCut(key: Any, db: Int) {
        val cut = db.coerceIn(-12, 0)
        if (cut == 0) bassCuts.remove(key) else bassCuts[key] = cut
        val effect = effects[key] ?: return
        runCatching {
            val active = (enabled && !isFlat()) || cut != 0
            effect.enabled = active
            if (active) apply(effect, cut)
        }
    }

    fun setEnabled(value: Boolean) {
        enabled = value
        effects.forEach { (key, effect) ->
            runCatching {
                val cut = bassCuts[key] ?: 0
                val active = (value && !isFlat()) || cut != 0
                effect.enabled = active
                if (active) apply(effect, cut)
            }
        }
        enhanceAll()
    }

    fun setGain(index: Int, db: Int) {
        if (index !in gains.indices) return
        val next = gains.toMutableList()
        next[index] = db.coerceIn(-12, 12)
        gains = next
        enabled = true
        effects.forEach { (key, effect) ->
            runCatching {
                val cut = bassCuts[key] ?: 0
                val active = !isFlat() || cut != 0
                effect.enabled = active
                if (active) apply(effect, cut)
            }
        }
        enhanceAll()
    }

    fun applyPreset(name: String) {
        val preset = PRESETS[name] ?: return
        gains = preset
        enabled = true
        effects.forEach { (key, effect) ->
            runCatching {
                val cut = bassCuts[key] ?: 0
                val active = !isFlat() || cut != 0
                effect.enabled = active
                if (active) apply(effect, cut)
            }
        }
        enhanceAll()
    }

    /** Aceita curvas de 5 (versões antigas) ou 10 bandas e reamostra para 10. */
    fun applySaved(enabled: Boolean, gains: List<Int>) {
        this.enabled = enabled
        this.gains = resampleGains(gains)
        effects.forEach { (key, effect) ->
            runCatching {
                val cut = bassCuts[key] ?: 0
                val active = (enabled && !isFlat()) || cut != 0
                effect.enabled = active
                if (active) apply(effect, cut)
            }
        }
        enhanceAll()
    }

    fun release(key: Any) {
        effects.remove(key)?.let { runCatching { it.release() } }
        enhancers.remove(key)?.let { runCatching { it.release() } }
        bassCuts.remove(key)
    }

    fun releaseAll() {
        effects.values.forEach { runCatching { it.release() } }
        effects.clear()
        enhancers.values.forEach { runCatching { it.release() } }
        enhancers.clear()
        bassCuts.clear()
    }

    /** Curva toda em zero: não vale a pena ligar o processamento. */
    private fun isFlat(): Boolean = gains.all { it == 0 }

    /**
     * Compensa a perda de nível típica do efeito. 3 dB fixos quando há curva
     * ativa; sem curva, o LoudnessEnhancer fica desligado.
     */
    private fun applyCompensation(enhancer: LoudnessEnhancer) {
        runCatching {
            val active = enabled && !isFlat()
            enhancer.setTargetGain(if (active) COMPENSATION_MB else 0)
            enhancer.enabled = active
        }
    }

    private fun enhanceAll() {
        enhancers.values.forEach { enhancer -> applyCompensation(enhancer) }
    }

    /**
     * Para cada banda física do aparelho, interpola a curva lógica pela
     * distância entre as duas bandas vizinhas mais próximas.
     */
    private fun apply(effect: Equalizer, bassCut: Int = 0) {
        val number = effect.numberOfBands.toInt().coerceAtLeast(1)
        val range = effect.bandLevelRange
        val min = range.getOrElse(0) { -1500 }.toInt()
        val max = range.getOrElse(1) { 1500 }.toInt()
        for (band in 0 until number) {
            val freq = runCatching { effect.getCenterFreq(band.toShort()) / 1000.0 }
                .getOrDefault(0.0)
            // O bass swap corta so o extremo grave, com meia forca em 250 Hz.
            val cut = when {
                bassCut == 0 -> 0
                freq <= 125.0 -> bassCut
                freq <= 300.0 -> bassCut / 2
                else -> 0
            }
            val level = ((gainAt(freq) + cut) * 100).coerceIn(min, max)
            runCatching { effect.setBandLevel(band.toShort(), level.toShort()) }
        }
    }

    /** Ganho da curva de 10 bandas na frequência pedida (interpolação linear). */
    private fun gainAt(freqHz: Double): Int {
        if (gains.isEmpty()) return 0
        if (freqHz <= BAND_FREQS.first()) return gains.first()
        if (freqHz >= BAND_FREQS.last()) return gains.last()
        val upper = BAND_FREQS.indexOfFirst { it >= freqHz }
        if (upper <= 0) return gains.first()
        val lowFreq = BAND_FREQS[upper - 1].toDouble()
        val highFreq = BAND_FREQS[upper].toDouble()
        val fraction = ((freqHz - lowFreq) / (highFreq - lowFreq)).coerceIn(0.0, 1.0)
        val low = gains[upper - 1]
        val high = gains[upper]
        return (low + (high - low) * fraction).roundToInt().coerceIn(-12, 12)
    }

    companion object {
        val BAND_FREQS = listOf(31, 62, 125, 250, 500, 1_000, 2_000, 4_000, 8_000, 16_000)

        val PRESETS = linkedMapOf(
            "Flat" to listOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
            "Pop" to listOf(-1, 0, 2, 3, 3, 2, 0, -1, -1, -2),
            "Rock" to listOf(4, 3, 2, 0, -1, -1, 1, 2, 3, 4),
            "Graves" to listOf(7, 6, 5, 3, 1, 0, -1, -2, -2, -3),
            "Vocal" to listOf(-3, -3, -2, -1, 1, 4, 5, 4, 2, 0),
            "Eletrônica" to listOf(5, 4, 1, 0, -2, 1, 2, 3, 5, 6),
            "Noturno" to listOf(-4, -3, -2, 0, 1, 2, 1, 0, -2, -4),
        )

        val BANDS = BAND_FREQS.size
        val DEFAULT_GAINS = List(BANDS) { 0 }

        /**
         * Reamostra qualquer curva (5 bandas antigas inclusive) para as 10
         * bandas atuais, usando interpolação linear.
         */
        fun resampleGains(values: List<Int>): List<Int> {
            val clean = values.map { it.coerceIn(-12, 12) }
            if (clean.size == BANDS) return clean
            if (clean.size < 2) return DEFAULT_GAINS
            return List(BANDS) { index ->
                val position = index * (clean.size - 1).toDouble() / (BANDS - 1)
                val low = floor(position).toInt().coerceIn(0, clean.lastIndex)
                val high = ceil(position).toInt().coerceIn(0, clean.lastIndex)
                val fraction = position - low
                (clean[low] + (clean[high] - clean[low]) * fraction)
                    .roundToInt()
                    .coerceIn(-12, 12)
            }
        }

        /** Compensação em milibéis (300 = +3 dB). */
        private const val COMPENSATION_MB = 300
    }
}
