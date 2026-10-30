package com.openfy.android.playback

import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import kotlin.math.abs

/**
 * Equalizador de 5 bandas sobre o AudioEffect do Android. Cada ExoPlayer
 * (A/B do crossfade) tem seu próprio efeito, mas os ganhos são compartilhados.
 *
 * O AudioEffect do sistema costuma baixar o volume geral quando entra em ação
 * (proteção contra clipping do SoundAlive/Samsung). Para compensar, um
 * LoudnessEnhancer na mesma sessão devolve parte do nível. Com a curva flat o
 * efeito fica desligado, então ligar o EQ sem mexer em nada não muda o som.
 */
class EqualizerController {

    private val effects = mutableMapOf<Any, Equalizer>()
    private val enhancers = mutableMapOf<Any, LoudnessEnhancer>()
    private var enabled = false
    private var gains = DEFAULT_GAINS

    fun attach(key: Any, sessionId: Int) {
        release(key)
        if (sessionId <= 0) return
        val effect = runCatching { Equalizer(0, sessionId) }.getOrNull() ?: return
        effects[key] = effect
        runCatching { effect.enabled = enabled && !isFlat() }
        if (enabled && !isFlat()) apply(effect)
        val enhancer = runCatching { LoudnessEnhancer(sessionId) }.getOrNull()
        if (enhancer != null) {
            enhancers[key] = enhancer
            applyCompensation(enhancer)
        }
    }

    fun setEnabled(value: Boolean) {
        enabled = value
        effects.values.forEach { effect ->
            runCatching {
                effect.enabled = value && !isFlat()
                if (value) apply(effect)
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
        effects.values.forEach { effect ->
            runCatching {
                effect.enabled = !isFlat()
                apply(effect)
            }
        }
        enhanceAll()
    }

    fun applyPreset(name: String) {
        val preset = PRESETS[name] ?: return
        gains = preset
        enabled = true
        effects.values.forEach { effect ->
            runCatching {
                effect.enabled = !isFlat()
                apply(effect)
            }
        }
        enhanceAll()
    }

    fun applySaved(enabled: Boolean, gains: List<Int>) {
        this.enabled = enabled
        if (gains.size == BANDS) this.gains = gains.map { it.coerceIn(-12, 12) }
        effects.values.forEach { effect ->
            runCatching {
                effect.enabled = enabled && !isFlat()
                if (enabled) apply(effect)
            }
        }
        enhanceAll()
    }

    fun release(key: Any) {
        effects.remove(key)?.let { runCatching { it.release() } }
        enhancers.remove(key)?.let { runCatching { it.release() } }
    }

    fun releaseAll() {
        effects.values.forEach { runCatching { it.release() } }
        effects.clear()
        enhancers.values.forEach { runCatching { it.release() } }
        enhancers.clear()
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

    private fun apply(effect: Equalizer) {
        val number = effect.numberOfBands.toInt().coerceAtLeast(1)
        val range = effect.bandLevelRange
        val min = range.getOrElse(0) { -1500 }.toInt()
        val max = range.getOrElse(1) { 1500 }.toInt()
        val used = mutableSetOf<Int>()
        BAND_FREQS.forEachIndexed { index, target ->
            val band = (0 until number)
                .filterNot { it in used }
                .minByOrNull { abs(effect.getCenterFreq(it.toShort()) / 1000 - target) }
                ?: return@forEachIndexed
            used += band
            val level = (gains.getOrElse(index) { 0 } * 100).coerceIn(min, max)
            runCatching { effect.setBandLevel(band.toShort(), level.toShort()) }
        }
    }

    companion object {
        val BAND_FREQS = listOf(60, 230, 910, 3_600, 14_000)
        val PRESETS = linkedMapOf(
            "Flat" to listOf(0, 0, 0, 0, 0),
            "Pop" to listOf(-1, 2, 3, 1, -1),
            "Rock" to listOf(4, 2, -1, 2, 4),
            "Graves" to listOf(7, 4, 0, -1, -2),
            "Vocal" to listOf(-3, -1, 4, 4, 1),
        )
        private const val BANDS = 5

        /** Compensação em milibéis (300 = +3 dB). */
        private const val COMPENSATION_MB = 300
        private val DEFAULT_GAINS = List(BANDS) { 0 }
    }
}
