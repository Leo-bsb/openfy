package com.openfy.android.data

import com.openfy.core.Track
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI

/** Registro de uma faixa baixada no armazenamento interno do app. */
@Serializable
data class DownloadEntry(
    val track: Track,
    val fileName: String,
    val sizeBytes: Long = 0,
    val downloadedAtMs: Long = 0,
)

/**
 * Índice dos downloads em `filesDir/downloads` (JSON `index.json` + áudios ao lado).
 * Sem novas dependências: só java.io + kotlinx.serialization, testável em JVM puro.
 */
class DownloadStore(private val dir: File) {

    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()
    private var index: LinkedHashMap<String, DownloadEntry>? = null

    val downloadsDir: File
        get() = dir.also { if (!it.isDirectory) it.mkdirs() }

    private fun indexFile(): File = File(dir, INDEX_NAME)

    private fun loadedIndex(): LinkedHashMap<String, DownloadEntry> =
        index ?: loadIndex().also { index = it }

    private fun loadIndex(): LinkedHashMap<String, DownloadEntry> {
        val file = indexFile()
        val text = runCatching { file.readText() }.getOrNull() ?: return LinkedHashMap()
        val list = runCatching { json.decodeFromString<List<DownloadEntry>>(text) }.getOrNull()
        if (list == null) {
            // Índice ilegível: preserva o arquivo para diagnóstico e nunca trata
            // os áudios como órfãos. Sem isso, uma queda de energia poderia
            // apagar todos os downloads no próximo boot.
            runCatching { file.renameTo(File(dir, "$INDEX_NAME.corrupt")) }
            return LinkedHashMap()
        }
        return LinkedHashMap(list.associateBy { it.track.id })
    }

    /** Grava o índice de forma atômica. Retorna false se o arquivo não puder ser salvo. */
    private fun persist(): Boolean = runCatching {
        if (!dir.isDirectory) dir.mkdirs()
        val temp = File(dir, "$INDEX_NAME.tmp")
        val bytes = json.encodeToString(loadedIndex().values.toList()).toByteArray()
        FileOutputStream(temp).use { output ->
            output.write(bytes)
            output.flush()
            output.fd.sync()
        }
        runCatching {
            java.nio.file.Files.move(
                temp.toPath(),
                indexFile().toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE,
            )
        }.onFailure {
            java.nio.file.Files.move(
                temp.toPath(),
                indexFile().toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
            )
        }
        true
    }.getOrDefault(false)

    fun all(): List<DownloadEntry> = synchronized(lock) { loadedIndex().values.toList() }

    fun ids(): Set<String> = synchronized(lock) { loadedIndex().keys.toSet() }

    fun entry(trackId: String): DownloadEntry? = synchronized(lock) { loadedIndex()[trackId] }

    /** Arquivo local pronto para tocar, ou null se não baixado/ausente. */
    fun fileFor(trackId: String): File? {
        val entry = entry(trackId) ?: return null
        val file = File(dir, entry.fileName)
        return if (file.isFile && file.length() > 0) file else null
    }

    fun isDownloaded(trackId: String): Boolean = fileFor(trackId) != null

    fun usedBytes(): Long = synchronized(lock) { loadedIndex().values.sumOf { it.sizeBytes } }

    /** Espaço ainda disponível dentro do limite. */
    fun availableBytes(limitBytes: Long): Long = synchronized(lock) {
        (limitBytes - loadedIndex().values.sumOf { it.sizeBytes }).coerceAtLeast(0)
    }

    fun fileNameFor(track: Track, contentType: String?): String =
        "${sanitizeId(track.id)}.${extensionFor(contentType)}"

    /**
     * Registra o download concluído no índice. Retorna false se o índice não
     * puder ser gravado (o chamador deve apagar o arquivo para não deixá-lo órfão).
     */
    fun put(entry: DownloadEntry): Boolean = synchronized(lock) {
        val previous = loadedIndex().put(entry.track.id, entry)
        val ok = persist()
        if (!ok) {
            if (previous != null) loadedIndex()[entry.track.id] = previous else loadedIndex().remove(entry.track.id)
        }
        ok
    }

    /** Remove o registro e o arquivo; ignora se não existir. */
    fun remove(trackId: String): DownloadEntry? = synchronized(lock) {
        val removed = loadedIndex().remove(trackId) ?: return null
        persist()
        runCatching { File(dir, removed.fileName).delete() }
        runCatching { File(dir, partialNameFor(removed.fileName)).delete() }
        removed
    }

    /** Apaga todos os áudios e o índice; devolve quantos existiam. */
    fun clearAll(): Int = synchronized(lock) {
        val count = loadedIndex().size
        loadedIndex().clear()
        persist()
        dir.listFiles()?.forEach { f ->
            if (f.name != INDEX_NAME) runCatching { f.delete() }
        }
        count
    }

    /**
     * Chamado na inicialização: remove `.part` órfãos e entradas cujo arquivo
     * sumiu/truncou. Arquivos de áudio que não estão no índice NÃO são apagados:
     * um índice ilegível não pode virar perda de todos os downloads.
     */
    fun repair(): Int {
        var fixed = 0
        dir.listFiles()?.forEach { f ->
            if (f.isFile && f.name.endsWith(PART_SUFFIX)) {
                runCatching { f.delete() }
                fixed++
            }
        }
        val missing = synchronized(lock) {
            loadedIndex().values.filter { entry ->
                val file = File(dir, entry.fileName)
                !(file.isFile && file.length() > 0) ||
                    (entry.sizeBytes > 0 && file.length() != entry.sizeBytes)
            }
        }
        if (missing.isNotEmpty()) {
            synchronized(lock) {
                missing.forEach { loadedIndex().remove(it.track.id) }
                persist()
            }
            // Só apaga arquivo truncado de entrada conhecida; arquivos fora do
            // índice continuam intocados para não transformar índice ilegível
            // em perda de downloads.
            missing.forEach { entry ->
                val file = File(dir, entry.fileName)
                if (file.isFile) runCatching { file.delete() }
            }
            fixed += missing.size
        }
        return fixed
    }

    companion object {
        const val INDEX_NAME = "index.json"
        const val PART_SUFFIX = ".part"

        fun partialNameFor(fileName: String): String = "$fileName$PART_SUFFIX"

        fun sanitizeId(id: String): String =
            id.map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else '_' }
                .joinToString("")
                .ifBlank { "track" }

        fun extensionFor(contentType: String?): String = when {
            contentType == null -> "m4a"
            contentType.contains("webm", ignoreCase = true) -> "webm"
            contentType.contains("ogg", ignoreCase = true) ||
                contentType.contains("opus", ignoreCase = true) -> "opus"
            else -> "m4a"
        }
    }
}

sealed interface DownloadResult {
    data class Success(val entry: DownloadEntry) : DownloadResult
    data object Cancelled : DownloadResult
    data class Failed(val reason: String, val expired: Boolean = false) : DownloadResult
}

/**
 * Baixa o áudio com java.net.HttpURLConnection para dentro de `filesDir/downloads`,
 * escrevendo em arquivo temporário `.part` e renomeando no fim.
 * Nunca deixa parcial para trás em cancelamento/erro. Não sobrescreve download existente.
 */
class AudioDownloader(
    private val store: DownloadStore,
    private val limitBytes: () -> Long,
) {

    private val connections = java.util.concurrent.ConcurrentHashMap<String, HttpURLConnection>()

    /** Interrompe a conexão de uma faixa (ou todas, se `trackId` for nulo). */
    fun cancel(trackId: String? = null) {
        if (trackId == null) {
            connections.values.forEach { runCatching { it.disconnect() } }
            connections.clear()
        } else {
            connections.remove(trackId)?.let { runCatching { it.disconnect() } }
        }
    }

    /** Executa em IO. `isCancelled` é checado a cada bloco lido. */
    fun download(
        track: Track,
        url: String,
        headers: Map<String, String>,
        onProgress: (fraction: Float) -> Unit,
        isCancelled: () -> Boolean,
    ): DownloadResult {
        val existing = store.fileFor(track.id)
        if (existing != null) {
            return DownloadResult.Success(
                store.entry(track.id) ?: DownloadEntry(track, existing.name, existing.length()),
            )
        }
        val dir = store.downloadsDir
        val finalName = store.fileNameFor(track, null)
        val temp = File(dir, "$finalName${DownloadStore.PART_SUFFIX}")
        runCatching { if (temp.exists()) temp.delete() }
        val budget = minOf(
            store.availableBytes(limitBytes()),
            (dir.usableSpace - DISK_SAFETY_BYTES).coerceAtLeast(0),
        )
        if (budget <= MIN_FREE_BYTES) {
            com.openfy.core.Log.write(
                "download: ${track.id} sem orçamento (usado=${store.usedBytes()}, limite=${limitBytes()})",
            )
            return DownloadResult.Failed("Limite de armazenamento atingido. Apague downloads em Ajustes.")
        }
        var connection: HttpURLConnection? = null
        return try {
            connection = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 15_000
                instanceFollowRedirects = true
                headers.forEach { (key, value) -> setRequestProperty(key, value) }
                if (getRequestProperty("User-Agent").isNullOrBlank()) {
                    setRequestProperty("User-Agent", DEFAULT_USER_AGENT)
                }
            }
            connections[track.id] = connection
            val code = connection.responseCode
            com.openfy.core.Log.write(
                "download: ${track.id} resposta $code " +
                    "(${connection.contentType.orEmpty()}, ${connection.contentLengthLong} bytes)",
            )
            if (isCancelled()) return DownloadResult.Cancelled
            if (code == 401 || code == 403) {
                // Google devolve 403 quando o link temporário expira.
                return DownloadResult.Failed("O link do áudio expirou.", expired = true)
            }
            if (code !in 200..299) {
                return DownloadResult.Failed("Servidor respondeu $code ao baixar o áudio.")
            }
            val declared = connection.contentLengthLong.coerceAtLeast(0L)
            if (declared > 0 && declared > budget) {
                return DownloadResult.Failed(
                    "Sem espaço: faltam ${formatMegabytes(declared - budget)} dentro do limite atual.",
                )
            }
            if (declared <= 0) onProgress(UNKNOWN_PROGRESS)
            var written = 0L
            FileOutputStream(temp).use { file ->
                val output = file.buffered(64 * 1024)
                connection.inputStream.use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        if (isCancelled()) {
                            temp.deleteQuietly()
                            return DownloadResult.Cancelled
                        }
                        val read = input.read(buffer)
                        if (read < 0) break
                        written += read
                        if (written > budget) {
                            temp.deleteQuietly()
                            return DownloadResult.Failed(
                                "Limite de armazenamento atingido durante o download.",
                            )
                        }
                        output.write(buffer, 0, read)
                        if (declared > 0) {
                            onProgress((written.toFloat() / declared.toFloat()).coerceIn(0f, 1f))
                        }
                    }
                }
                output.flush()
                // fsync evita arquivo truncado por queda de energia.
                runCatching { file.fd.sync() }
                runCatching { output.close() }
            }
            if (declared > 0 && written != declared) {
                temp.deleteQuietly()
                return DownloadResult.Failed("Download incompleto ($written de $declared bytes).")
            }
            val realName = store.fileNameFor(track, connection.contentType)
            val finalFile = File(dir, realName)
            if (!temp.renameTo(finalFile)) {
                runCatching { temp.copyTo(finalFile, overwrite = true) }
                temp.deleteQuietly()
            }
            if (!finalFile.isFile || finalFile.length() != written || written <= 0) {
                finalFile.deleteQuietly()
                return DownloadResult.Failed("Download terminou vazio ou corrompido.")
            }
            val entry = DownloadEntry(
                track = track,
                fileName = finalFile.name,
                sizeBytes = finalFile.length(),
                downloadedAtMs = System.currentTimeMillis(),
            )
            if (!store.put(entry)) {
                finalFile.deleteQuietly()
                com.openfy.core.Log.write("download: falha ao gravar índice de ${track.id}")
                return DownloadResult.Failed("Não foi possível salvar o índice de downloads.")
            }
            com.openfy.core.Log.write("download: ${track.id} ok ($written bytes em ${finalFile.name})")
            DownloadResult.Success(entry)
        } catch (error: Exception) {
            com.openfy.core.Log.write(
                "download: ${track.id} falhou (${error.javaClass.simpleName}: ${error.message})",
            )
            if (isCancelled()) {
                DownloadResult.Cancelled
            } else {
                DownloadResult.Failed(error.message ?: "Falha ao baixar o áudio.")
            }
        } finally {
            connections.remove(track.id)
            runCatching { connection?.disconnect() }
            if (temp.exists()) temp.deleteQuietly()
        }
    }

    private fun File.deleteQuietly() {
        runCatching { delete() }
    }

    private fun formatMegabytes(bytes: Long): String =
        "${(bytes.toDouble() / (1024 * 1024)).toInt()} MB"

    private companion object {
        const val MIN_FREE_BYTES = 1L
        const val DISK_SAFETY_BYTES = 32L * 1024 * 1024
        const val UNKNOWN_PROGRESS = -1f
        const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0.0.0 Safari/537.36"
    }
}
