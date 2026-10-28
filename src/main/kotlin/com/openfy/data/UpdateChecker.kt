package com.openfy.data

import com.openfy.core.BuildInfo
import com.openfy.core.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** Release estável do desktop encontrada no GitHub. */
data class DesktopRelease(
    val version: String,
    val releaseUrl: String,
    val installerUrl: String?,
)

/** Estado da verificação de atualização exibido em Ajustes > Atualizações. */
sealed interface UpdateStatus {
    data object Checking : UpdateStatus
    data object UpToDate : UpdateStatus
    data class Available(val release: DesktopRelease) : UpdateStatus
    data class Error(val message: String) : UpdateStatus
}

/**
 * Consulta as releases do repositório no GitHub e compara com BuildInfo.VERSION.
 * Só entram releases estáveis do desktop: tag `vX.Y.Z` exata (sem sufixo
 * `-android`, sem prerelease, sem draft). Nenhuma falha vira exceção: rede,
 * rate limit, 404 e JSON inválido viram UpdateStatus.Error com mensagem curta.
 */
object UpdateChecker {

    private const val RELEASES_URL = "https://api.github.com/repos/Leo-bsb/openfy/releases?per_page=100"
    private const val ONE_DAY_MS = 24L * 3_600_000

    private val stableTag = Regex("""^v\d+\.\d+\.\d+$""")

    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** True se a última verificação automática ainda é válida (máximo 1x/dia). */
    fun shouldAutoCheck(lastCheckAt: Long?, now: Long = System.currentTimeMillis()): Boolean =
        lastCheckAt == null || now - lastCheckAt >= ONE_DAY_MS

    suspend fun check(current: String = BuildInfo.VERSION): UpdateStatus =
        withContext(Dispatchers.IO) {
            val currentParts = parseVersion(current)
                ?: return@withContext UpdateStatus.Error("Versão local inválida ($current).")

            val body = runCatching { get() }
            val (code, text) = when (val result = body.getOrNull()) {
                null -> return@withContext UpdateStatus.Error(
                    "Sem conexão com o GitHub. Tente novamente mais tarde.",
                ).also { Log.write("updates: falha de rede: ${body.exceptionOrNull()?.message}") }
                else -> result
            }
            when {
                code == 404 -> return@withContext UpdateStatus.Error("Nenhuma release publicada ainda.")
                code == 403 || code == 429 -> return@withContext UpdateStatus.Error(
                    "Muitas verificações no GitHub; tente de novo em alguns minutos.",
                )
                code !in 200..299 -> return@withContext UpdateStatus.Error("GitHub respondeu $code.")
            }

            val releases = runCatching { json.parseToJsonElement(text).jsonArray }.getOrNull()
                ?: return@withContext UpdateStatus.Error("Resposta inválida do GitHub.")

            var best: Pair<IntArray, DesktopRelease>? = null
            for (item in releases) {
                val obj = runCatching { item.jsonObject }.getOrNull() ?: continue
                if (obj["draft"]?.jsonPrimitive?.booleanOrNull == true) continue
                if (obj["prerelease"]?.jsonPrimitive?.booleanOrNull == true) continue
                val tag = obj["tag_name"]?.jsonPrimitive?.contentOrNull ?: continue
                if (!stableTag.matches(tag)) continue
                val version = tag.removePrefix("v")
                val parts = parseVersion(version) ?: continue
                if (compare(parts, currentParts) <= 0) continue
                val previous = best
                if (previous != null && compare(parts, previous.first) <= 0) continue
                val releaseUrl = obj["html_url"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val installerUrl = obj["assets"]?.jsonArray
                    ?.firstNotNullOfOrNull { asset ->
                        val a = runCatching { asset.jsonObject }.getOrNull() ?: return@firstNotNullOfOrNull null
                        val name = a["name"]?.jsonPrimitive?.contentOrNull ?: return@firstNotNullOfOrNull null
                        val link = a["browser_download_url"]?.jsonPrimitive?.contentOrNull
                            ?: return@firstNotNullOfOrNull null
                        if (name.endsWith(".exe", ignoreCase = true)) link else null
                    }
                best = parts to DesktopRelease(version, releaseUrl, installerUrl)
            }

            val newest = best
            if (newest == null) UpdateStatus.UpToDate
            else UpdateStatus.Available(newest.second)
        }

    private fun get(): Pair<Int, String> {
        val request = HttpRequest.newBuilder(URI.create(RELEASES_URL))
            .timeout(Duration.ofSeconds(12))
            .header("User-Agent", "Openfy/${BuildInfo.VERSION} (desktop updater)")
            .header("Accept", "application/vnd.github+json")
            .GET()
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        return response.statusCode() to response.body()
    }

    /** Semver simples: exatamente três números; qualquer outra forma é rejeitada. */
    private fun parseVersion(value: String): IntArray? {
        val parts = value.trim().split('.')
        if (parts.size != 3) return null
        return IntArray(3) { index ->
            parts[index].toIntOrNull() ?: return null
        }
    }

    private fun compare(a: IntArray, b: IntArray): Int {
        for (index in 0..2) {
            val diff = a[index].compareTo(b[index])
            if (diff != 0) return diff
        }
        return 0
    }
}
