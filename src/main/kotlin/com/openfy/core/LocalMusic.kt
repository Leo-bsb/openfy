package com.openfy.core

import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import java.util.Locale

/**
 * Faixas que vivem em pastas do usuario (a pasta de downloads do proprio app
 * ou qualquer pasta escolhida). O id e `local:<caminho em base64url>`, entao o
 * StreamServer resolve o arquivo antes de tentar a rede e a faixa continua
 * valida entre sessoes sem precisar de tabela nova.
 */
object LocalMusic {

    const val PREFIX = "local:"

    val EXTENSIONS = setOf(
        "mp3", "m4a", "aac", "flac", "ogg", "opus", "wav", "webm", "weba", "wma",
    )

    fun isLocalId(id: String): Boolean = id.startsWith(PREFIX)

    fun idFor(path: Path): String {
        val normalized = path.toAbsolutePath().normalize().toString()
        val encoded = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(normalized.toByteArray(Charsets.UTF_8))
        return PREFIX + encoded
    }

    fun pathOf(id: String): Path? {
        if (!isLocalId(id)) return null
        return runCatching {
            val raw = Base64.getUrlDecoder().decode(id.removePrefix(PREFIX))
            Path.of(String(raw, Charsets.UTF_8))
        }.getOrNull()
    }

    /** Caminho existente em disco para um id local (null se sumiu). */
    fun existing(id: String): Path? = pathOf(id)?.takeIf { Files.isRegularFile(it) }

    fun isAudio(path: Path): Boolean =
        path.fileName.toString().substringAfterLast('.', "").lowercase(Locale.ROOT) in EXTENSIONS

    /**
     * Varre a pasta (recursivo). Tracks sao montados pelo nome do arquivo:
     * "Artista - Titulo.mp3" vira artista/titulo; sem separador o artista e a
     * pasta. Arquivos que ja existem na biblioteca (downloads do app) usam o
     * metadado real via [known] (chave = nome do arquivo sem extensao).
     */
    fun scan(
        root: Path,
        limit: Int = 5000,
        known: Map<String, Track> = emptyMap(),
    ): List<Track> {
        if (!Files.isDirectory(root)) return emptyList()
        val files = runCatching {
            Files.walk(root).use { stream ->
                stream.filter { Files.isRegularFile(it) && isAudio(it) }
                    .limit(limit.toLong() + 1L)
                    .toList()
            }
        }.getOrDefault(emptyList())
        return files.asSequence()
            .take(limit)
            .sortedBy { it.toString().lowercase(Locale.ROOT) }
            .map { file ->
                val key = file.fileName.toString().substringBeforeLast('.').lowercase(Locale.ROOT)
                known[key] ?: parse(file, root)
            }
            .distinctBy { it.id }
            .toList()
    }

    fun parse(path: Path, root: Path? = null): Track {
        val fileName = path.fileName.toString().substringBeforeLast('.')
        val cleaned = fileName.replace('_', ' ').trim().ifBlank { "Faixa local" }
        val parts = cleaned.split(" - ", limit = 2)
        val artist = if (parts.size == 2) {
            parts[0].trim()
        } else {
            val parent = path.parent?.fileName?.toString().orEmpty()
            if (parent.isBlank() || (root != null && path.parent == root)) "Arquivo local" else parent
        }
        val title = if (parts.size == 2) parts[1].trim() else cleaned
        val album = path.parent?.fileName?.toString().orEmpty()
        return Track(
            id = idFor(path),
            title = title.ifBlank { cleaned },
            artist = artist.ifBlank { "Arquivo local" },
            album = album,
        )
    }
}
