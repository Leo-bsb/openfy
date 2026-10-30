package com.openfy.core

import kotlinx.serialization.Serializable

/**
 * Ajustes compartilhados entre desktop e Android.
 *
 * O desktop grava o mesmo payload em `activities` (type `settings_prefs`);
 * o maior `updatedAt` vence. O Android usa um data class idêntico.
 */
@Serializable
data class SyncedSettings(
    val palette: String = "openfy",
    val playerLayout: String = "center",
    val coverGradient: Boolean = true,
    val autoDownload: Boolean = false,
    val audioQuality: String = "HIGH",
    val crossfadeSeconds: Int = 0,
    val eqEnabled: Boolean = false,
    val eqGains: List<Int> = listOf(0, 0, 0, 0, 0),
    val autoplayRadio: Boolean = true,
    val updatedAt: Long = 0L,
)
