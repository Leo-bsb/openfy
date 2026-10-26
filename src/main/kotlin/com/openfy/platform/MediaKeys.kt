package com.openfy.platform

import com.openfy.core.Log
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.platform.win32.WinUser.MSG

/**
 * Teclas de mídia do teclado (play/pause, próxima, anterior) via hotkeys globais
 * do Windows. Registra VK_MEDIA_* sem janela (o WM_HOTKEY cai na fila da thread)
 * e bombeia numa thread própria. Best-effort: falha silenciosa se já estiverem
 * tomadas por outro app.
 */
class MediaKeys(
    private val onPlayPause: () -> Unit,
    private val onNext: () -> Unit,
    private val onPrevious: () -> Unit,
) {

    private val ids = intArrayOf(1, 2, 3)
    @Volatile private var running = false
    private var thread: Thread? = null

    fun start(): Boolean {
        if (running) return true
        val user32 = User32.INSTANCE
        val keys = intArrayOf(VK_MEDIA_PLAY_PAUSE, VK_MEDIA_NEXT_TRACK, VK_MEDIA_PREV_TRACK)
        var ok = true
        for (i in keys.indices) {
            if (!user32.RegisterHotKey(null, ids[i], WinUser.MOD_NOREPEAT, keys[i])) {
                ok = false
            }
        }
        if (!ok) {
            ids.forEach { user32.UnregisterHotKey(null, it) }
            Log.write("mediakeys: registro parcial/falhou (talvez em uso por outro app)")
            return false
        }
        running = true
        thread = Thread {
            val msg = MSG()
            while (running) {
                val res = user32.GetMessage(msg, null, 0, 0)
                if (res <= 0) break
                if (msg.message == WinUser.WM_HOTKEY) {
                    when (msg.wParam.toString().toIntOrNull()) {
                        ids[0] -> onPlayPause()
                        ids[1] -> onNext()
                        ids[2] -> onPrevious()
                    }
                }
            }
        }.apply { isDaemon = true; name = "openfy-mediakeys" }
        thread!!.start()
        Log.write("mediakeys: registradas")
        return true
    }

    fun stop() {
        running = false
        ids.forEach { User32.INSTANCE.UnregisterHotKey(null, it) }
    }

    private companion object {
        const val VK_MEDIA_NEXT_TRACK = 0xB0
        const val VK_MEDIA_PREV_TRACK = 0xB1
        const val VK_MEDIA_PLAY_PAUSE = 0xB3
    }
}
