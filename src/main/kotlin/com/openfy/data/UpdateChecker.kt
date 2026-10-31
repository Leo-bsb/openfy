package com.openfy.data

import com.openfy.core.BuildInfo
import com.openfy.core.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** Release estável do desktop encontrada no GitHub ou no manifesto do site. */
data class DesktopRelease(
    val version: String,
    val releaseUrl: String,
    val installerUrl: String?,
    val size: Long = 0L,
    val sha256: String = "",
)

/** Motivo da falha, para a UI distinguir manifesto ausente de rede fora. */
enum class UpdateFailureKind {
    NETWORK,
    NOT_FOUND,
    INVALID_JSON,
    HTTP,
    INVALID_VERSION,
}

/** Estado da verificação de atualização exibido em Ajustes > Atualizações. */
sealed interface UpdateStatus {
    data object Checking : UpdateStatus
    data object UpToDate : UpdateStatus
    data class Available(val release: DesktopRelease) : UpdateStatus
    data class Error(
        val message: String,
        val kind: UpdateFailureKind = UpdateFailureKind.NETWORK,
    ) : UpdateStatus
}

/** Manifesto publicado pelo site; vence as releases do GitHub quando responde. */
@Serializable
private data class UpdateManifest(
    val version: String = "",
    val url: String = "",
    val size: Long = 0,
    val sha256: String = "",
    val notes: String = "",
)

/**
 * Consulta primeiro o manifesto do site (`update-desktop.json`) e só cai nas
 * releases do GitHub quando o manifesto falha. Compara com BuildInfo.VERSION.
 * Nenhuma falha vira exceção: rede, rate limit, 404 e JSON inválido viram
 * UpdateStatus.Error com mensagem curta e o motivo em `kind`.
 */
object UpdateChecker {

    private const val MANIFEST_URL = "https://leo-bsb.github.io/openfy/update-desktop.json"
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
                ?: return@withContext UpdateStatus.Error(
                    "Versão local inválida ($current).",
                    UpdateFailureKind.INVALID_VERSION,
                )

            when (val manifest = fetchManifest()) {
                is ManifestResult.Ok -> {
                    val value = manifest.value
                    val parts = parseVersion(value.version.trim())
                    when {
                        parts == null -> {
                            Log.write("updates: manifesto com versão inválida (${value.version})")
                            github(currentParts, "versão inválida no manifesto")
                        }
                        compare(parts, currentParts) <= 0 -> UpdateStatus.UpToDate
                        value.url.isBlank() ->
                            github(currentParts, "manifesto sem link do instalador")
                        else -> {
                            val version = value.version.trim()
                            UpdateStatus.Available(
                                DesktopRelease(
                                    version = version,
                                    releaseUrl =
                                        "https://github.com/Leo-bsb/openfy/releases/tag/v$version",
                                    installerUrl = value.url.trim(),
                                    size = value.size,
                                    sha256 = value.sha256.trim().lowercase(),
                                ),
                            )
                        }
                    }
                }
                is ManifestResult.Failed -> {
                    Log.write(
                        "updates: manifesto indisponível (${manifest.kind}): ${manifest.message}",
                    )
                    github(currentParts, manifest.message)
                }
            }
        }

    /** Fallback: escolhe a maior release estável do desktop acima da versão atual. */
    private fun github(currentParts: IntArray, manifestNote: String?): UpdateStatus {
        val note = manifestNote?.takeIf { it.isNotBlank() }
            ?.let { " Manifesto do site: $it." }
            .orEmpty()
        val body = runCatching { get() }
        val (code, text) = when (val result = body.getOrNull()) {
            null -> return UpdateStatus.Error(
                "Sem conexão com o GitHub. Tente novamente mais tarde.$note",
                UpdateFailureKind.NETWORK,
            ).also { Log.write("updates: falha de rede: ${body.exceptionOrNull()?.message}") }
            else -> result
        }
        when {
            code == 404 -> return UpdateStatus.Error(
                "Nenhuma release publicada no GitHub ainda.$note",
                UpdateFailureKind.NOT_FOUND,
            )
            code == 403 || code == 429 -> return UpdateStatus.Error(
                "Muitas verificações no GitHub; tente de novo em alguns minutos.$note",
                UpdateFailureKind.HTTP,
            )
            code !in 200..299 -> return UpdateStatus.Error(
                "GitHub respondeu $code.$note",
                UpdateFailureKind.HTTP,
            )
        }

        val releases = runCatching { json.parseToJsonElement(text).jsonArray }.getOrNull()
            ?: return UpdateStatus.Error(
                "Resposta inválida do GitHub.$note",
                UpdateFailureKind.INVALID_JSON,
            )

        val newest = selectBest(releases, currentParts)
        return if (newest == null) UpdateStatus.UpToDate else UpdateStatus.Available(newest)
    }

    private sealed interface ManifestResult {
        data class Ok(val value: UpdateManifest) : ManifestResult
        data class Failed(val kind: UpdateFailureKind, val message: String) : ManifestResult
    }

    /** Busca o manifesto com cache-buster e timeout curto, sem lançar exceções. */
    private fun fetchManifest(): ManifestResult {
        val url = "$MANIFEST_URL?t=${System.currentTimeMillis()}"
        val response = runCatching {
            val request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(6))
                .header("User-Agent", "Openfy/${BuildInfo.VERSION} (desktop updater)")
                .header("Cache-Control", "no-cache")
                .GET()
                .build()
            http.send(request, HttpResponse.BodyHandlers.ofString())
        }.getOrElse { error ->
            return ManifestResult.Failed(
                UpdateFailureKind.NETWORK,
                error.message ?: "falha de rede",
            )
        }
        return when {
            response.statusCode() == 404 -> ManifestResult.Failed(
                UpdateFailureKind.NOT_FOUND,
                "arquivo não publicado (404)",
            )
            response.statusCode() !in 200..299 -> ManifestResult.Failed(
                UpdateFailureKind.HTTP,
                "HTTP ${response.statusCode()}",
            )
            else -> runCatching { json.decodeFromString<UpdateManifest>(response.body()) }
                .fold(
                    onSuccess = { manifest ->
                        if (parseVersion(manifest.version.trim()) == null) {
                            ManifestResult.Failed(
                                UpdateFailureKind.INVALID_JSON,
                                "versão \"${manifest.version}\" inválida",
                            )
                        } else {
                            ManifestResult.Ok(manifest)
                        }
                    },
                    onFailure = { error ->
                        ManifestResult.Failed(
                            UpdateFailureKind.INVALID_JSON,
                            error.message ?: "JSON inválido",
                        )
                    },
                )
        }
    }

    /** Escolhe a maior release estável do desktop acima da versão atual. */
    internal fun selectBest(releases: kotlinx.serialization.json.JsonArray, currentParts: IntArray): DesktopRelease? {
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
            val installer = obj["assets"]?.jsonArray
                ?.firstNotNullOfOrNull { asset ->
                    val a = runCatching { asset.jsonObject }.getOrNull() ?: return@firstNotNullOfOrNull null
                    val name = a["name"]?.jsonPrimitive?.contentOrNull ?: return@firstNotNullOfOrNull null
                    val link = a["browser_download_url"]?.jsonPrimitive?.contentOrNull
                        ?: return@firstNotNullOfOrNull null
                    val size = a["size"]?.jsonPrimitive?.longOrNull ?: 0L
                    if (name.endsWith(".exe", ignoreCase = true)) link to size else null
                }
            best = parts to DesktopRelease(
                version = version,
                releaseUrl = releaseUrl,
                installerUrl = installer?.first,
                size = installer?.second ?: 0L,
            )
        }
        return best?.second
    }

    /** Variante pura usada pelos testes: recebe o JSON cru do GitHub. */
    internal fun newestRelease(jsonText: String, current: String): DesktopRelease? {
        val parts = parseVersion(current) ?: return null
        val releases = runCatching { json.parseToJsonElement(jsonText).jsonArray }.getOrNull()
            ?: return null
        return selectBest(releases, parts)
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

    /**
     * Semver simples: exatamente três números; qualquer outra forma é rejeitada.
     */
    internal fun parseVersion(value: String): IntArray? {
        val parts = value.trim().split('.')
        if (parts.size != 3) return null
        return IntArray(3) { index ->
            if (!parts[index].matches(Regex("""\d+"""))) return null
            parts[index].toIntOrNull() ?: return null
        }
    }

    internal fun compare(a: IntArray, b: IntArray): Int {
        for (index in 0..2) {
            val diff = a[index].compareTo(b[index])
            if (diff != 0) return diff
        }
        return 0
    }
}
