package com.openfy.android.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.openfy.android.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest

/**
 * Atualização dentro do app: consulta o manifesto do site e as releases do
 * GitHub, baixa o APK mais novo e abre o instalador do Android. Só funciona com
 * o mesmo certificado de assinatura do app instalado (é o caso dos APKs gerados
 * no projeto).
 */
object AppUpdater {

    @Serializable
    private data class Release(
        val tag_name: String = "",
        val body: String = "",
        val draft: Boolean = false,
        val prerelease: Boolean = false,
        val assets: List<Asset> = emptyList(),
    )

    @Serializable
    private data class Asset(
        val name: String = "",
        val size: Long = 0,
        val browser_download_url: String = "",
    )

    data class UpdateInfo(
        val version: String,
        val notes: String,
        val apkUrl: String,
        val sizeBytes: Long,
        val sha256: String = "",
    )

    private const val RELEASES_URL = "https://api.github.com/repos/Leo-bsb/openfy/releases?per_page=20"
    private val manifestUrl: String
        get() = BuildConfig.SITE_URL.trimEnd('/') + "/update-android.json"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Serializable
    private data class Manifest(
        val version: String = "",
        val url: String = "",
        val notes: String = "",
        val size: Long = 0,
        val sha256: String = "",
    )

    private class HttpStatusException(val code: Int, message: String) : Exception(message)

    val currentVersion: String get() = BuildConfig.VERSION_NAME

    /**
     * Retorna a atualização mais nova que a versão instalada, ou null quando
     * já está atualizado. Lança exceção quando a consulta falha (rede, CDN ou
     * resposta inválida), para a UI distinguir falha de "versão mais recente".
     * Primeiro consulta o manifesto publicado no site (sem limite de API) e,
     * se ele não existir, cai para as releases do GitHub.
     */
    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        var manifestAvailable = false
        var failure: Exception? = null
        val manifestInfo = try {
            fromManifest().also { manifestAvailable = it.first }.second
        } catch (error: Exception) {
            failure = error
            null
        }
        val info = manifestInfo ?: try {
            fromReleases()
        } catch (error: Exception) {
            if (failure == null) failure = error
            null
        }
        if (info == null && failure != null && !manifestAvailable) {
            com.openfy.core.Log.write(
                "updates: falha ao verificar: " +
                    (failure.message ?: failure.javaClass.simpleName),
            )
            throw failure
        }
        com.openfy.core.Log.write(
            "updates: " + (
                info?.let { "versao ${it.version} disponivel" }
                    ?: "sem atualizacao (atual $currentVersion)"
                ),
        )
        info
    }

    /** Primeiro valor: o manifesto existe; segundo: atualização encontrada. */
    private fun fromManifest(): Pair<Boolean, UpdateInfo?> {
        val body = try {
            fetch(cacheBuster(manifestUrl), "application/json")
        } catch (error: HttpStatusException) {
            // Sem manifesto publicado: tenta as releases normalmente.
            if (error.code == 404) return false to null else throw error
        }
        val manifest = runCatching { json.decodeFromString<Manifest>(body) }.getOrNull()
            ?: return false to null
        if (manifest.version.isBlank() || manifest.url.isBlank()) return false to null
        if (compareVersions(manifest.version, currentVersion) <= 0) return true to null
        return true to UpdateInfo(
            version = manifest.version,
            notes = manifest.notes.trim(),
            apkUrl = manifest.url.trim(),
            sizeBytes = manifest.size,
            sha256 = manifest.sha256.trim().lowercase(),
        )
    }

    private fun fromReleases(): UpdateInfo? {
        val body = fetch(cacheBuster(RELEASES_URL), "application/vnd.github+json")
        val releases = runCatching { json.decodeFromString<List<Release>>(body) }.getOrNull()
            ?: return null
        return releases.asSequence()
            .filterNot { it.draft || it.prerelease }
            .mapNotNull { release ->
                val apk = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                    ?: return@mapNotNull null
                val version = versionFromTag(release.tag_name) ?: return@mapNotNull null
                UpdateInfo(version, release.body.trim(), apk.browser_download_url, apk.size)
            }
            .filter { compareVersions(it.version, currentVersion) > 0 }
            .maxByOrNull { it.version.split('.').map { part -> part.toIntOrNull() ?: 0 }
                .fold(0L) { acc, value -> acc * 1_000 + value } }
    }

    /** Baixa o APK em `cache/updates`; devolve o arquivo pronto para instalar. */
    suspend fun download(
        context: Context,
        info: UpdateInfo,
        onProgress: (Float) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val target = File(dir, "Openfy-${info.version}.apk")
        // Já baixado numa tentativa anterior (ex.: faltou permitir a instalação).
        if (target.isFile && target.length() > 0 &&
            (info.sizeBytes <= 0 || target.length() == info.sizeBytes)
        ) {
            val cachedHash = if (info.sha256.isNotBlank()) sha256(target) else ""
            if (info.sha256.isBlank() || cachedHash.equals(info.sha256, ignoreCase = true)) {
                return@withContext target
            }
            runCatching { target.delete() }
        }
        val temp = File(dir, "${target.name}.part")
        runCatching { temp.delete() }
        val connection = (URI(info.apkUrl).toURL().openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Openfy-Android")
            setRequestProperty("Accept", "application/octet-stream")
            setRequestProperty("Cache-Control", "no-cache")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) error("GitHub respondeu $code ao baixar o APK.")
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: info.sizeBytes
            var written = 0L
            FileOutputStream(temp).use { output ->
                val buffer = ByteArray(64 * 1024)
                connection.inputStream.use { input ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        written += read
                        output.write(buffer, 0, read)
                        if (total > 0) onProgress((written.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
                output.flush()
                runCatching { output.fd.sync() }
            }
            if (total > 0 && written != total) {
                temp.delete()
                error("Download incompleto ($written de $total bytes).")
            }
            if (info.sizeBytes > 0 && temp.length() != info.sizeBytes) {
                val actual = temp.length()
                temp.delete()
                error(
                    "O APK baixado tem $actual bytes; o manifesto informa ${info.sizeBytes}.",
                )
            }
            if (info.sha256.isNotBlank() &&
                !sha256(temp).equals(info.sha256, ignoreCase = true)
            ) {
                temp.delete()
                error("O APK baixado não confere com o hash do manifesto.")
            }
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
            target
        } finally {
            runCatching { connection.disconnect() }
            if (temp.exists() && !target.exists()) temp.delete()
        }
    }

    /**
     * Abre o instalador do Android para o APK baixado.
     * Retorna false quando o app ainda não pode instalar apps desconhecidos;
     * nesse caso abre a tela de permissão para o usuário liberar.
     */
    fun install(context: Context, apk: File): Boolean {
        if (!context.packageManager.canRequestPackageInstalls()) {
            val settings = Intent(
                android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(settings)
            return false
        }
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return true
    }

    /** `v0.6.0-android` -> `0.6.0`. */
    internal fun versionFromTag(tag: String): String? =
        Regex("""(\d+(?:\.\d+){0,2})""").find(tag)?.value

    /** >0 se `a` for mais novo que `b`; compara cada bloco numérico. */
    internal fun compareVersions(a: String, b: String): Int {
        val left = a.split('.').map { it.toIntOrNull() ?: 0 }
        val right = b.split('.').map { it.toIntOrNull() ?: 0 }
        for (index in 0 until maxOf(left.size, right.size)) {
            val diff = (left.getOrNull(index) ?: 0) - (right.getOrNull(index) ?: 0)
            if (diff != 0) return diff
        }
        return 0
    }

    /** Evita manifesto/API velhos em cache do GitHub Pages/CDN. */
    private fun cacheBuster(url: String): String =
        url + (if ('?' in url) "&" else "?") + "t=" + System.currentTimeMillis()

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun fetch(url: String, accept: String): String {
        val connection = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            connectTimeout = 9_000
            readTimeout = 9_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Openfy-Android")
            setRequestProperty("Accept", accept)
            setRequestProperty("Cache-Control", "no-cache")
            setRequestProperty("Pragma", "no-cache")
        }
        return try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw HttpStatusException(code, "Servidor respondeu $code ao verificar a atualização.")
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            runCatching { connection.disconnect() }
        }
    }
}
