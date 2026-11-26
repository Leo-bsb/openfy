package com.openfy.data

import com.openfy.core.Log
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.awt.Desktop
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.util.Base64
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@Serializable
data class SpotifyTokens(
    val refreshToken: String = "",
    val accessToken: String = "",
    val expiresAt: Long = 0L,
    val scope: String = "",
    val user: String = "",
)

/**
 * OAuth do Spotify no desktop: Authorization Code + PKCE, sem Client Secret.
 * Abre o navegador, recebe o `code` no loopback (127.0.0.1) e guarda o
 * refresh token no SQLite local. Com a conta conectada, a importação usa a
 * Web API oficial e não tem o teto de 500 faixas do caminho anônimo.
 */
class SpotifyAuth(private val db: Db) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()
    private val mutex = Mutex()
    private val random = SecureRandom()

    @Volatile
    private var cached: SpotifyTokens? = load()

    val connected: Boolean get() = cached?.refreshToken?.isNotBlank() == true
    val user: String? get() = cached?.user?.takeIf { it.isNotBlank() }

    fun disconnect() {
        cached = null
        db.put(KEY, "")
    }

    suspend fun connect(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            check(SpotifyConfig.configured) {
                "Spotify não configurado. Preencha openfy.spotify.clientId no local.properties."
            }
            val redirect = URI.create(SpotifyConfig.redirectUri)
            val host = redirect.host.orEmpty()
            check(host == "127.0.0.1" || host == "localhost") {
                "No desktop, openfy.spotify.redirectUri precisa apontar para 127.0.0.1."
            }
            val port = if (redirect.port > 0) redirect.port else 80
            val path = redirect.path?.takeIf { it.isNotBlank() } ?: "/callback"

            val verifier = randomToken(64)
            val challenge = spotifyPkceChallenge(verifier)
            val state = randomToken(16)
            val pending = CompletableFuture<OAuthCallback>()
            val server = startServer(host, port, path, pending)
            try {
                val url = authorizeUrl(challenge, state)
                Log.write("spotify: abrindo autorização no navegador")
                if (!Desktop.isDesktopSupported() ||
                    !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)
                ) {
                    error("Não foi possível abrir o navegador para autorizar o Spotify.")
                }
                Desktop.getDesktop().browse(URI.create(url))
                val callback = try {
                    pending.get(180, TimeUnit.SECONDS)
                } catch (timeout: TimeoutException) {
                    error("A autorização do Spotify expirou. Tente novamente.")
                } catch (failure: ExecutionException) {
                    throw failure.cause ?: failure
                }
                check(callback.state == state) { "Estado OAuth inválido; tente conectar de novo." }

                val tokens = exchange(callback.code, verifier)
                val name = runCatching { fetchUser(tokens.accessToken) }.getOrDefault("")
                val stored = tokens.copy(user = name)
                save(stored)
                stored.user.ifBlank { "Spotify" }
            } finally {
                server.stop(0)
            }
        }
    }

    /** Token válido para a Web API; renova sozinho quando está perto de expirar. */
    suspend fun accessToken(): String? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = cached ?: return@withLock null
            if (current.refreshToken.isBlank()) return@withLock null
            val now = System.currentTimeMillis()
            if (current.accessToken.isNotBlank() && current.expiresAt - 60_000 > now) {
                return@withLock current.accessToken
            }
            runCatching { refresh(current) }
                .onFailure { Log.write("spotify: refresh falhou: ${it.message}") }
                .getOrNull()
                ?.accessToken
                ?.takeIf { it.isNotBlank() }
        }
    }

    private fun load(): SpotifyTokens? =
        db.get(KEY)
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { json.decodeFromString<SpotifyTokens>(it) }.getOrNull() }
            ?.takeIf { it.refreshToken.isNotBlank() }

    private fun save(tokens: SpotifyTokens) {
        cached = tokens
        db.put(KEY, json.encodeToString(tokens))
    }

    private fun refresh(current: SpotifyTokens): SpotifyTokens {
        val body = tokenRequest(
            mapOf(
                "grant_type" to "refresh_token",
                "refresh_token" to current.refreshToken,
                "client_id" to SpotifyConfig.clientId,
            ),
        )
        val access = body["access_token"]?.jsonPrimitive?.contentOrNull
            ?: error("Spotify não devolveu access_token.")
        val expiresIn = body["expires_in"]?.jsonPrimitive?.longOrNull ?: 3600L
        val updated = current.copy(
            accessToken = access,
            refreshToken = body["refresh_token"]?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.isNotBlank() }
                ?: current.refreshToken,
            expiresAt = System.currentTimeMillis() + expiresIn * 1000L,
            scope = body["scope"]?.jsonPrimitive?.contentOrNull ?: current.scope,
        )
        save(updated)
        return updated
    }

    private fun exchange(code: String, verifier: String): SpotifyTokens {
        val body = tokenRequest(
            mapOf(
                "grant_type" to "authorization_code",
                "code" to code,
                "redirect_uri" to SpotifyConfig.redirectUri,
                "client_id" to SpotifyConfig.clientId,
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
        return SpotifyTokens(
            refreshToken = refresh,
            accessToken = access,
            expiresAt = System.currentTimeMillis() + expiresIn * 1000L,
            scope = body["scope"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        )
    }

    private fun tokenRequest(form: Map<String, String>): kotlinx.serialization.json.JsonObject {
        val body = form.entries.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }
        val request = HttpRequest.newBuilder(URI.create(TOKEN_URL))
            .timeout(Duration.ofSeconds(20))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body, Charsets.UTF_8))
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString(Charsets.UTF_8))
        val text = response.body()
        if (response.statusCode() !in 200..299) {
            val detail = runCatching {
                json.parseToJsonElement(text).jsonObject["error_description"]
                    ?.jsonPrimitive?.contentOrNull
            }.getOrNull()
            error("Spotify recusou a autorização (${response.statusCode()}): ${detail ?: "erro"}")
        }
        return json.parseToJsonElement(text).jsonObject
    }

    private fun fetchUser(token: String): String {
        val request = HttpRequest.newBuilder(URI.create("$API/me?fields=display_name,id"))
            .timeout(Duration.ofSeconds(15))
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString(Charsets.UTF_8))
        if (response.statusCode() !in 200..299) return ""
        val body = runCatching { json.parseToJsonElement(response.body()).jsonObject }.getOrNull()
            ?: return ""
        return body["display_name"]?.jsonPrimitive?.contentOrNull
            ?.takeIf { it.isNotBlank() }
            ?: body["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
    }

    private fun authorizeUrl(challenge: String, state: String): String =
        "https://accounts.spotify.com/authorize" +
            "?client_id=${encode(SpotifyConfig.clientId)}" +
            "&response_type=code" +
            "&redirect_uri=${encode(SpotifyConfig.redirectUri)}" +
            "&code_challenge_method=S256" +
            "&code_challenge=$challenge" +
            "&state=$state" +
            "&scope=${encode(SCOPES)}"

    private fun startServer(
        host: String,
        port: Int,
        path: String,
        pending: CompletableFuture<OAuthCallback>,
    ): HttpServer {
        val server = try {
            HttpServer.create(InetSocketAddress(host, port), 0)
        } catch (error: Exception) {
            throw IllegalStateException(
                "A porta $port está em uso. Feche o outro programa e tente de novo.",
                error,
            )
        }
        server.createContext(path) { exchange ->
            val query = parseQuery(exchange.requestURI.rawQuery)
            val error = query["error"]
            val code = query["code"]
            val message = when {
                error != null -> "Autorização cancelada: $error"
                code.isNullOrBlank() -> "Código de autorização ausente."
                else -> null
            }
            val html = if (message == null) {
                page("Spotify conectado", "Pode voltar para o Openfy.")
            } else {
                page("Não foi possível conectar", message)
            }
            val bytes = html.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
            if (message == null && code != null) {
                pending.complete(OAuthCallback(code, query["state"].orEmpty()))
            } else {
                pending.completeExceptionally(IllegalStateException(message))
            }
        }
        server.executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "openfy-spotify-oauth").apply { isDaemon = true }
        }
        server.start()
        return server
    }

    private fun parseQuery(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return raw.split('&').mapNotNull { pair ->
            val index = pair.indexOf('=')
            if (index <= 0) return@mapNotNull null
            val key = URLDecoder.decode(pair.substring(0, index), Charsets.UTF_8)
            val value = URLDecoder.decode(pair.substring(index + 1), Charsets.UTF_8)
            key to value
        }.toMap()
    }

    private fun page(title: String, message: String): String =
        "<!doctype html><html lang=\"pt-BR\"><head><meta charset=\"utf-8\">" +
            "<title>Openfy</title></head><body style=\"font-family:system-ui,sans-serif;" +
            "background:#0f1115;color:#f5f5f5;display:flex;align-items:center;" +
            "justify-content:center;height:100vh;margin:0\"><main style=\"text-align:center\">" +
            "<h1 style=\"font-size:22px\">${escapeHtml(title)}</h1>" +
            "<p style=\"color:#b8bcc8\">${escapeHtml(message)}</p>" +
            "</main></body></html>"

    private fun escapeHtml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private fun challenge(verifier: String): String = spotifyPkceChallenge(verifier)

    private fun randomToken(bytes: Int): String {
        val data = ByteArray(bytes)
        random.nextBytes(data)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data)
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8)

    private data class OAuthCallback(val code: String, val state: String)

    private companion object {
        const val KEY = "spotify.tokens"
        const val TOKEN_URL = "https://accounts.spotify.com/api/token"
        const val API = "https://api.spotify.com/v1"
        const val SCOPES =
            "playlist-read-private playlist-read-collaborative user-library-read"
    }
}

/** PKCE S256: base64url(SHA-256(verifier)), sem padding. */
internal fun spotifyPkceChallenge(verifier: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(verifier.toByteArray(Charsets.US_ASCII))
    return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
}
