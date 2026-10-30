package com.openfy.android.data

import kotlinx.serialization.Serializable

/**
 * Ajustes sincronizados pela conta. O desktop grava o mesmo payload em
 * `activities` (type `settings_prefs`); o maior `updatedAt` vence.
 */
@Serializable
data class SyncedSettings(
    val palette: String = "openfy",
    val playerLayout: String = "center",
    val coverGradient: Boolean = true,
    val autoDownload: Boolean = false,
    val audioQuality: String = "HIGH",
    val crossfadeSeconds: Int = 6,
    val eqEnabled: Boolean = false,
    val eqGains: List<Int> = listOf(0, 0, 0, 0, 0),
    val autoplayRadio: Boolean = true,
    val updatedAt: Long = 0L,
)
