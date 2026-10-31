package com.openfy.data

import com.openfy.core.AppPaths
import com.openfy.core.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Duration

/**
 * Baixa o instalador (.exe) da release e o executa depois que o Openfy fecha.
 *
 * No Windows não dá para substituir o app em execução, então o instalador é
 * salvo em `%LOCALAPPDATA%\OpenfyData\updates` e um `.bat` auxiliar espera o
 * processo atual terminar para abrir o instalador. O app fecha em seguida.
 */
object UpdateInstaller {

    sealed interface Status {
        data class Downloading(val progress: Float) : Status
        data class Ready(val installer: Path) : Status
        data class Error(val message: String) : Status
    }

    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    suspend fun download(
        release: DesktopRelease,
        onProgress: (Float) -> Unit,
    ): Path = withContext(Dispatchers.IO) {
        val url = release.installerUrl
            ?: error("Esta release não tem instalador .exe.")
        val dir = AppPaths.home.resolve("updates").also { Files.createDirectories(it) }
        val target = dir.resolve("Openfy-${release.version}.exe")
        val temp = dir.resolve("Openfy-${release.version}.exe.part")
        Files.deleteIfExists(temp)

        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofMinutes(5))
            .header("User-Agent", "Openfy/${com.openfy.core.BuildInfo.VERSION} (desktop updater)")
            .header("Accept", "application/octet-stream")
            .GET()
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofInputStream())
        if (response.statusCode() !in 200..299) {
            error("GitHub respondeu ${response.statusCode()} ao baixar o instalador.")
        }
        val total = response.headers().firstValueAsLong("content-length").orElse(0L)
        var written = 0L
        response.body().use { input ->
            Files.newOutputStream(temp).use { output ->
                val buffer = ByteArray(128 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    written += read
                    output.write(buffer, 0, read)
                    if (total > 0) onProgress((written.toFloat() / total).coerceIn(0f, 1f))
                }
                output.flush()
            }
        }
        if (total > 0 && written != total) {
            Files.deleteIfExists(temp)
            error("Download incompleto ($written de $total bytes).")
        }
        if (release.size > 0 && written != release.size) {
            Files.deleteIfExists(temp)
            error("O instalador tem $written bytes; o manifesto informa ${release.size}.")
        }
        if (release.sha256.isNotBlank() &&
            !sha256(temp).equals(release.sha256, ignoreCase = true)
        ) {
            Files.deleteIfExists(temp)
            error("O instalador baixado não confere com o hash do manifesto.")
        }
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
        } catch (error: Exception) {
            Files.deleteIfExists(temp)
            error("Não foi possível salvar o instalador: ${error.message}")
        }
        Log.write("updates: instalador ${release.version} salvo em $target ($written bytes)")
        target
    }

    /** SHA-256 do arquivo em hex minúsculo, usado para conferir o manifesto. */
    private fun sha256(file: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(file).use { input ->
            val buffer = ByteArray(128 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Cria o auxiliar que espera o PID atual sair e abre o instalador. O
     * instalador do jpackage é interativo: mostra o assistente normal.
     */    fun launchAfterExit(installer: Path): Boolean {
        val pid = ProcessHandle.current().pid()
        val dir = installer.parent ?: return false
        val script = dir.resolve("instalar-openfy.bat")
        val text = """
            @echo off
            :wait
            tasklist /FI "PID eq $pid" | find "$pid" >nul
            if not errorlevel 1 (
              timeout /t 1 /nobreak >nul
              goto wait
            )
            start "" "${installer.toAbsolutePath()}"
            del "%~f0"
        """.trimIndent()
        return runCatching {
            Files.writeString(script, text)
            ProcessBuilder("cmd", "/c", "start", "", script.toAbsolutePath().toString())
                .redirectErrorStream(true)
                .start()
            Log.write("updates: auxiliar de instalação iniciado ($script)")
            true
        }.getOrElse { error ->
            Log.write("updates: falha ao iniciar instalador: ${error.message}")
            false
        }
    }
}
