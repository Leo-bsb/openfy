package com.openfy.android.data

import com.openfy.core.Track
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
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
    private var index: LinkedHashMap<String, DownloadEntry> = loadIndex()

    val downloadsDir: File
        get() = dir.also { if (!it.isDirectory) it.mkdirs() }

    private fun indexFile(): File = File(dir, INDEX_NAME)

    private fun loadIndex(): LinkedHashMap<String, DownloadEntry> {
        val text = runCatching { indexFile().readText() }.getOrNull() ?: return LinkedHashMap()
        val list = runCatching { json.decodeFromString<List<DownloadEntry>>(text) }.getOrNull()
            ?: return LinkedHashMap()
        return LinkedHashMap(list.associateBy { it.track.id })
    }

    private fun persist() {
        runCatching {
            if (!dir.isDirectory) dir.mkdirs()
            val temp = File(dir, "$INDEX_NAME.tmp")
            temp.writeText(json.encodeToString(index.values.toList()))
            if (indexFile().exists()) indexFile().delete()
            temp.renameTo(indexFile())
        }
    }

    fun all(): List<DownloadEntry> = synchronized(lock) { index.values.toList() }

    fun ids(): Set<String> = synchronized(lock) { index.keys.toSet() }

    fun entry(trackId: String): DownloadEntry? = synchronized(lock) { index[trackId] }

    /** Arquivo local pronto para tocar, ou null se não baixado/ausente. */
    fun fileFor(trackId: String): File? {
        val entry = entry(trackId) ?: return null
        val file = File(dir, entry.fileName)
        return if (file.isFile && file.length() > 0) file else null
    }

    fun isDownloaded(trackId: String): Boolean = fileFor(trackId) != null

    fun usedBytes(): Long = synchronized(lock) { index.values.sumOf { it.sizeBytes } }

    fun fileNameFor(track: Track, contentType: String?): String =
        "${sanitizeId(track.id)}.${extensionFor(contentType)}"

    /** Registra o download concluído no índice e devolve o arquivo final. */
    fun put(entry: DownloadEntry): DownloadEntry = synchronized(lock) {
        index[entry.track.id] = entry
        persist()
        entry
    }

    /** Remove o registro e o arquivo; ignora se não existir. */
    fun remove(trackId: String): DownloadEntry? = synchronized(lock) {
        val removed = index.remove(trackId) ?: return null
        persist()
        runCatching { File(dir, removed.fileName).delete() }
        runCatching { File(dir, partialNameFor(removed.fileName)).delete() }
        removed
    }

    /** Apaga todos os áudios e o índice; devolve quantos existiam. */
    fun clearAll(): Int = synchronized(lock) {
        val count = index.size
        index.clear()
        persist()
        dir.listFiles()?.forEach { f ->
            if (f.name != INDEX_NAME) runCatching { f.delete() }
        }
        count
    }

    /** Chamado na inicialização: remove `.part` órfãos e entradas cujo arquivo sumiu. */
    fun repair(): Int {
        var fixed = 0
        dir.listFiles()?.forEach { f ->
            if (f.isFile && f.name.endsWith(PART_SUFFIX)) {
                runCatching { f.delete() }
                fixed++
            }
        }
        val missing = synchronized(lock) {
            index.values.filter { entry ->
                val file = File(dir, entry.fileName)
                !(file.isFile && file.length() > 0)
            }
        }
        if (missing.isNotEmpty()) {
            synchronized(lock) {
                missing.forEach { index.remove(it.track.id) }
                persist()
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
        val budget = limitBytes() - store.usedBytes()
        if (budget <= MIN_FREE_BYTES) {
            return DownloadResult.Failed("Limite de armazenamento atingido. Apague downloads em Ajustes.")
        }
        var connection: HttpURLConnection? = null
        return try {
            connection = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                headers.forEach { (key, value) -> setRequestProperty(key, value) }
                if (getRequestProperty("User-Agent").isNullOrBlank()) {
                    setRequestProperty("User-Agent", DEFAULT_USER_AGENT)
                }
            }
            val code = connection.responseCode
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
            connection.inputStream.use { input ->
                temp.outputStream().buffered(64 * 1024).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var written = 0L
                    while (true) {
                        if (isCancelled()) return DownloadResult.Cancelled.also { temp.deleteQuietly() }
                        val read = input.read(buffer)
                        if (read < 0) break
                        written += read
                        if (written > budget) {
                            return DownloadResult.Failed(
                                "Limite de armazenamento atingido durante o download.",
                            ).also { temp.deleteQuietly() }
                        }
                        output.write(buffer, 0, read)
                        if (declared > 0) onProgress((written.toFloat() / declared.toFloat()).coerceIn(0f, 1f))
                    }
                }
            }
            val realName = store.fileNameFor(track, connection.contentType)
            val finalFile = File(dir, realName)
            if (!temp.renameTo(finalFile)) {
                runCatching { temp.copyTo(finalFile, overwrite = true) }
                temp.deleteQuietly()
            }
            if (!finalFile.isFile || finalFile.length() <= 0) {
                finalFile.deleteQuietly()
                return DownloadResult.Failed("Download terminou vazio ou corrompido.")
            }
            DownloadResult.Success(
                store.put(
                    DownloadEntry(
                        track = track,
                        fileName = finalFile.name,
                        sizeBytes = finalFile.length(),
                        downloadedAtMs = System.currentTimeMillis(),
                    ),
                ),
            )
        } catch (error: Exception) {
            DownloadResult.Failed(error.message ?: "Falha ao baixar o áudio.")
        } finally {
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
        const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0.0.0 Safari/537.36"
    }
}
