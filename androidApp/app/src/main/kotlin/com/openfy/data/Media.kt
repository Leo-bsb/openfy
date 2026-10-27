package com.openfy.data

/** Resultado da extração de áudio; no Android o ExoPlayer consome a URL direta. */
sealed interface Media {
    data class Remote(
        val url: String,
        val headers: Map<String, String> = emptyMap(),
    ) : Media
}
