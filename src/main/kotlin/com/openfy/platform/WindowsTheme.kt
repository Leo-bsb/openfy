package com.openfy.platform

import com.openfy.core.Log
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions

private interface DwmApi : StdCallLibrary {
    fun DwmSetWindowAttribute(hwnd: WinDef.HWND, attribute: Int, value: Pointer, size: Int): Int

    companion object {
        val INSTANCE: DwmApi = Native.load("dwmapi", DwmApi::class.java, W32APIOptions.DEFAULT_OPTIONS)
    }
}

/**
 * Deixa a barra de titulo do Windows escura, combinando com o tema.
 * DWMWA_USE_IMMERSIVE_DARK_MODE = 20 (Win10 2004+) e 19 (builds antigos).
 */
fun applyDarkTitleBar(title: String = "Openfy", attempts: Int = 10) {
    Thread {
        repeat(attempts) {
            val hwnd = runCatching { User32.INSTANCE.FindWindow(null, title) }.getOrNull()
            if (hwnd != null) {
                val ok = runCatching {
                    val memory = Memory(4)
                    memory.setInt(0, 1)
                    DwmApi.INSTANCE.DwmSetWindowAttribute(hwnd, 20, memory, 4)
                    DwmApi.INSTANCE.DwmSetWindowAttribute(hwnd, 19, memory, 4)
                    true
                }.getOrDefault(false)
                if (ok) {
                    Log.write("tema: barra de titulo escura aplicada")
                    return@Thread
                }
            }
            Thread.sleep(200)
        }
    }.apply { isDaemon = true; name = "openfy-dark-title" }.start()
}
