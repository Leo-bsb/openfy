package com.openfy.android.playback

import android.media.audiofx.Equalizer
import kotlin.math.abs

/**
 * Equalizador de 5 bandas sobre o AudioEffect do Android. Cada ExoPlayer
 * (A/B do crossfade) tem seu próprio efeito, mas os ganhos são compartilhados.
 */
class EqualizerController {

    private val effects = mutableMapOf<Any, Equalizer>()
    private var enabled = false
    private var gains = DEFAULT_GAINS

    fun attach(key: Any, sessionId: Int) {
        release(key)
        if (sessionId <= 0) return
        val effect = runCatching { Equalizer(0, sessionId) }.getOrNull() ?: return
        effects[key] = effect
        runCatching {
            effect.enabled = enabled
            if (enabled) apply(effect)
        }
    }

    fun setEnabled(value: Boolean) {
        enabled = value
        effects.values.forEach { effect ->
            runCatching {
                effect.enabled = value
                if (value) apply(effect)
            }
        }
    }

    fun setGain(index: Int, db: Int) {
        if (index !in gains.indices) return
        val next = gains.toMutableList()
        next[index] = db.coerceIn(-12, 12)
        gains = next
        enabled = true
        effects.values.forEach { effect ->
            runCatching {
                effect.enabled = true
                apply(effect)
            }
        }
    }

    fun applyPreset(name: String) {
        val preset = PRESETS[name] ?: return
        gains = preset
        enabled = true
        effects.values.forEach { effect ->
            runCatching {
                effect.enabled = true
                apply(effect)
            }
        }
    }

    fun applySaved(enabled: Boolean, gains: List<Int>) {
        this.enabled = enabled
        if (gains.size == BANDS) this.gains = gains.map { it.coerceIn(-12, 12) }
        effects.values.forEach { effect ->
            runCatching {
                effect.enabled = enabled
                if (enabled) apply(effect)
            }
        }
    }

    fun release(key: Any) {
        effects.remove(key)?.let { runCatching { it.release() } }
    }

    fun releaseAll() {
        effects.values.forEach { runCatching { it.release() } }
        effects.clear()
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
        private val DEFAULT_GAINS = List(BANDS) { 0 }
    }
}
