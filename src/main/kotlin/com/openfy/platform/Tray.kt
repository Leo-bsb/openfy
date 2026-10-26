package com.openfy.platform

import com.openfy.core.Log
import java.awt.Color
import java.awt.Image
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.RenderingHints
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.image.BufferedImage

/**
 * Ícone na bandeja do Windows com controles de reprodução, mostrar/ocultar e
 * sair. Puro AWT (JDK), sem dependência nativa extra.
 */
class Tray(
    private val onPlayPause: () -> Unit,
    private val onNext: () -> Unit,
    private val onPrevious: () -> Unit,
    private val onShow: () -> Unit,
    private val onExit: () -> Unit,
) {

    private var icon: TrayIcon? = null

    fun install(): Boolean {
        if (!SystemTray.isSupported()) {
            Log.write("tray: nao suportado")
            return false
        }
        return runCatching {
            val popup = PopupMenu()
            popup.add(item("Tocar/Pausar", onPlayPause))
            popup.add(item("Próxima", onNext))
            popup.add(item("Anterior", onPrevious))
            popup.addSeparator()
            popup.add(item("Mostrar Openfy", onShow))
            popup.add(item("Sair", onExit))

            val trayIcon = TrayIcon(iconImage(), "Openfy", popup)
            trayIcon.isImageAutoSize = true
            trayIcon.addActionListener { onShow() }
            SystemTray.getSystemTray().add(trayIcon)
            icon = trayIcon
            Log.write("tray: instalado")
            true
        }.onFailure {
            Log.write("tray: falha ao instalar: ${it.message}")
        }.getOrDefault(false)
    }

    fun setTooltip(text: String) {
        runCatching { icon?.toolTip = text }
    }

    fun notify(title: String, text: String) {
        runCatching { icon?.displayMessage(title, text, TrayIcon.MessageType.INFO) }
    }

    fun dispose() {
        runCatching { icon?.let { SystemTray.getSystemTray().remove(it) } }
        icon = null
    }

    private fun item(label: String, action: () -> Unit): MenuItem =
        MenuItem(label).apply { addActionListener { action() } }

    private fun iconImage(): Image {
        val img = BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = Color(0x9B, 0xC4, 0xFF)
        g.fillOval(2, 2, 28, 28)
        g.color = Color(0x06, 0x10, 0x1F)
        g.fillOval(11, 11, 10, 10)
        g.dispose()
        return img
    }
}
