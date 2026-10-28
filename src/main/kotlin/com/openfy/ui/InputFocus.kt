package com.openfy.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged

/** Foco global de campo de texto, para os atalhos não roubarem letras/setas. */
object InputFocus {
    val active = mutableStateOf(false)
}

fun Modifier.trackInputFocus(): Modifier = composed {
    onFocusChanged { state ->
        InputFocus.active.value = state.isFocused
    }
}
