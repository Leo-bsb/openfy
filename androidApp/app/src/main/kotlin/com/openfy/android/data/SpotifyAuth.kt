package com.openfy.android.data

import android.net.Uri
import com.openfy.android.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * OAuth do Spotify no Android: Authorization Code + PKCE, sem Client Secret.
 * O navegador volta pelo callback do site (`.../oauth/spotify.html`), que
 * redireciona para `openfy://oauth/spotify`. O refresh token fica nas
 * preferências privadas do app.
 */
class SpotifyAuth(private val store: SessionStore) {

    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private val random = SecureRandom()

    val configured: Boolean
        get() = BuildConfig.SPOTIFY_CLIENT_ID.isNotBlank() &&
            BuildConfig.SPOTIFY_REDIRECT_URI.isNotBlank()

    val connected: Boolean get() = store.spotifyRefreshToken().isNotBlank()
    val user: String get() = store.spotifyUser()

    /** Monta a URL de autorização e guarda verifier/state para o retorno. */
    fun begin(): String {
        check(configured) {
            "Spotify não configurado. Preencha openfy.spotify.clientId no local.properties."
        }
        val verifier = randomToken(64)
        val state = randomToken(16)
        store.saveSpotifyPkce(verifier, state)
        return authorizeUrl(challenge(verifier), state)
    }

    /** Troca o `code` do deep link por tokens e devolve o nome do usuário. */
    suspend fun complete(callback: Uri): String = withContext(Dispatchers.IO) {
        val error = callback.getQueryParameter("error")
        if (!error.isNullOrBlank()) error("Autorização cancelada: $error")
        val code = callback.getQueryParameter("code").orEmpty()
        if (code.isBlank()) error("Código de autorização ausente.")
        val verifier = store.spotifyVerifier()
        val savedState = store.spotifyState()
        if (verifier.isBlank() || savedState.isBlank()) {
            error("Pedido de autorização expirado; tente conectar de novo.")
        }
        if (callback.getQueryParameter("state").orEmpty() != savedState) {
            error("Estado OAuth inválido; tente conectar de novo.")
        }
        val tokens = exchange(code, verifier)
        val name = runCatching { fetchUser(tokens.accessToken) }.getOrDefault("")
        store.saveSpotifyTokens(tokens.refreshToken, tokens.accessToken, tokens.expiresAt, name)
        store.clearSpotifyPkce()
        name.ifBlank { "Spotify" }
    }

    suspend fun accessToken(): String? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val refresh = store.spotifyRefreshToken()
            if (refresh.isBlank()) return@withLock null
            val current = store.spotifyAccessToken()
            val expiresAt = store.spotifyExpiresAt()
            if (current.isNotBlank() && expiresAt - 60_000 > System.currentTimeMillis()) {
                return@withLock current
            }
            runCatching { refresh(refresh) }.getOrNull()
        }
    }

    fun disconnect() {
        store.clearSpotifyTokens()
        store.clearSpotifyPkce()
    }

    private fun refresh(refreshToken: String): String {
        val body = postForm(
            TOKEN_URL,
            mapOf(
                "grant_type" to "refresh_token",
                "refresh_token" to refreshToken,
                "client_id" to BuildConfig.SPOTIFY_CLIENT_ID,
            ),
        )
        val access = body["access_token"]?.jsonPrimitive?.contentOrNull
            ?: error("Spotify não devolveu access_token.")
        val expiresIn = body["expires_in"]?.jsonPrimitive?.longOrNull ?: 3600L
        store.saveSpotifyTokens(
            refresh = body["refresh_token"]?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.isNotBlank() } ?: refreshToken,
            access = access,
            expiresAt = System.currentTimeMillis() + expiresIn * 1000L,
            user = store.spotifyUser(),
        )
        return access
    }

    private fun exchange(code: String, verifier: String): Tokens {
        val body = postForm(
            TOKEN_URL,
            mapOf(
                "grant_type" to "authorization_code",
                "code" to code,
                "redirect_uri" to BuildConfig.SPOTIFY_REDIRECT_URI,
                "client_id" to BuildConfig.SPOTIFY_CLIENT_ID,
                "code_verifier" to verifier,
            ),
        )
        val access = body["access_token"]?.jsonPrimitive?.contentOrNull
            ?: error("Spotify não devolveu access_token.")
        val refresh = body["refresh_token"]?.jsonPrimitive?.contentOrNull.orEmpty()
        check(refresh.isNotBlank()) {
            "Spotify não devolveu refresh_token; autorize o app novamente."
        }
        val expiresIn = body["expires_in"]?.jsonPrimitive?.longOrNull ?: 3600L
        return Tokens(
            accessToken = access,
            refreshToken = refresh,
            expiresAt = System.currentTimeMillis() + expiresIn * 1000L,
        )
    }

    private fun fetchUser(token: String): String {
        val body = getJson("$API/me?fields=display_name,id", token) ?: return ""
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return ""
        return root["display_name"]?.jsonPrimitive?.contentOrNull
            ?.takeIf { it.isNotBlank() }
            ?: root["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
    }

    private fun postForm(url: String, form: Map<String, String>): JsonObject {
        val payload = form.entries.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }.toByteArray(Charsets.UTF_8)
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            setRequestProperty("Accept", "application/json")
        }
        return try {
            connection.outputStream.use { it.write(payload) }
            val status = connection.responseCode
            val text = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()
            if (status !in 200..299) {
                val detail = runCatching {
                    json.parseToJsonElement(text).jsonObject["error_description"]
                        ?.jsonPrimitive?.contentOrNull
                }.getOrNull()
                error("Spotify recusou a autorização ($status): ${detail ?: "erro"}")
            }
            json.parseToJsonElement(text).jsonObject
        } finally {
            connection.disconnect()
        }
    }

    private fun getJson(url: String, token: String): String? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
        }
        return try {
            val status = connection.responseCode
            if (status !in 200..299) return null
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun authorizeUrl(challenge: String, state: String): String =
        "https://accounts.spotify.com/authorize" +
            "?client_id=${encode(BuildConfig.SPOTIFY_CLIENT_ID)}" +
            "&response_type=code" +
            "&redirect_uri=${encode(BuildConfig.SPOTIFY_REDIRECT_URI)}" +
            "&code_challenge_method=S256" +
            "&code_challenge=$challenge" +
            "&state=$state" +
            "&scope=${encode(SCOPES)}"

    private fun challenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    private fun randomToken(bytes: Int): String {
        val data = ByteArray(bytes)
        random.nextBytes(data)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data)
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8)

    private data class Tokens(
        val accessToken: String,
        val refreshToken: String,
        val expiresAt: Long,
    )

    private companion object {
        const val TOKEN_URL = "https://accounts.spotify.com/api/token"
        const val API = "https://api.spotify.com/v1"
        const val SCOPES =
            "playlist-read-private playlist-read-collaborative user-library-read"
    }
}

/** Entrega o deep link do OAuth para o ViewModel, mesmo se a Activity foi recriada. */
object SpotifyAuthLink {
    private val _uri = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val uri: kotlinx.coroutines.flow.StateFlow<String?> = _uri

    fun deliver(value: String?) {
        if (!value.isNullOrBlank() && value.startsWith("openfy://oauth/spotify")) {
            _uri.value = value
        }
    }

    fun consume() {
        _uri.value = null
    }
}
