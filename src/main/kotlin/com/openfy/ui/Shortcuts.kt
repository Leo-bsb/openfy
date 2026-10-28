package com.openfy.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.openfy.core.AppController

/**
 * Atalhos globais da janela. Ligados em `Window.onPreviewKeyEvent`, ou seja,
 * funcionam mesmo quando um botao esta focado — exceto quando o usuario esta
 * digitando (campo de busca ou dialogo de playlist), que fica intacto.
 *
 *  Space           tocar/pausar
 *  ← / →           voltar/avancar 5s
 *  Ctrl+← / Ctrl+→ faixa anterior/próxima
 *  L               curtir faixa atual
 *  S               aleatório
 *  R               repetir (off → all → one)
 *  Q               abrir/fechar fila
 *  Ctrl+P          abrir/fechar player
 *  Ctrl+R          abrir recap
 *  Ctrl+F          focar busca
 *  Esc             fechar dialogo/player/fila (ou voltar)
 */
fun openfyKeyHandler(c: AppController): (KeyEvent) -> Boolean = handler@{ event ->
    if (event.type != KeyEventType.KeyDown) return@handler false
    val key = event.key
    val ctrl = event.isCtrlPressed

    when {
        c.playlistPrompt.value != null -> false
        InputFocus.active.value -> {
            if (key == Key.Escape && c.importDialogOpen.value) {
                c.importDialogOpen.value = false
                true
            } else {
                false
            }
        }
        key == Key.Escape -> {
            when {
                c.importDialogOpen.value -> c.importDialogOpen.value = false
                c.nowPlayingOpen.value -> c.closeNowPlaying()
                c.queueOpen.value -> c.closeQueue()
                else -> c.back()
            }
            true
        }
        ctrl && key == Key.F -> {
            c.requestSearchFocus()
            true
        }
        ctrl && key == Key.P -> {
            if (c.nowPlayingOpen.value) c.closeNowPlaying() else c.openNowPlaying()
            true
        }
        ctrl && key == Key.R -> {
            c.openRecap()
            true
        }
        key == Key.Spacebar -> {
            c.player.togglePlay()
            true
        }
        key == Key.DirectionLeft && !ctrl -> {
            c.player.seekBy(-5.0)
            true
        }
        key == Key.DirectionRight && !ctrl -> {
            c.player.seekBy(5.0)
            true
        }
        ctrl && key == Key.DirectionLeft -> {
            c.player.previous()
            true
        }
        ctrl && key == Key.DirectionRight -> {
            c.player.next()
            true
        }
        key == Key.L -> {
            c.toggleLikeCurrent()
            true
        }
        key == Key.S -> {
            c.player.toggleShuffle()
            true
        }
        key == Key.R -> {
            c.player.cycleRepeat()
            true
        }
        key == Key.Q -> {
            c.toggleQueue()
            true
        }
        else -> false
    }
}
