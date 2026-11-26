package com.openfy.data

import java.util.Properties

/**
 * Credenciais públicas do app Spotify, geradas no build a partir de
 * `local.properties` (ou variáveis de ambiente). O Client Secret nunca entra
 * aqui: o fluxo é Authorization Code + PKCE.
 */
object SpotifyConfig {
    private val props = Properties().apply {
        SpotifyConfig::class.java.getResourceAsStream("/openfy-build.properties")
            ?.use { load(it) }
    }

    val clientId: String =
        System.getenv("OPENFY_SPOTIFY_CLIENT_ID")?.takeIf { it.isNotBlank() }
            ?: props.getProperty("spotify.clientId").orEmpty()

    val redirectUri: String =
        System.getenv("OPENFY_SPOTIFY_REDIRECT_URI")?.takeIf { it.isNotBlank() }
            ?: props.getProperty("spotify.redirectUri").orEmpty()
                .ifBlank { "http://127.0.0.1:8899/callback" }

    val configured: Boolean get() = clientId.isNotBlank()
}
