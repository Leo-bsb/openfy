package com.openfy.android.data

import com.openfy.core.Track
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Snapshot da Home salvo no aparelho (JSON em arquivos internos).
 * Usado para abrir o app sem rede com perfil, playlists, curtidas,
 * top tracks e recentes ainda visíveis. Nunca é apagado em falha de rede.
 */
@Serializable
data class HomeSnapshot(
    val userId: String = "",
    val savedAtMs: Long = 0,
    val profile: Profile? = null,
    val playlists: List<PlaylistDto> = emptyList(),
    val likedIds: List<String> = emptyList(),
    val likedTracks: List<Track> = emptyList(),
    val topTracks: List<Track> = emptyList(),
    val recentTracks: List<Track> = emptyList(),
)

class HomeCache(dir: File) {

    private val dir = dir.also { if (!it.isDirectory) it.mkdirs() }
    private val file = File(this.dir, "home_snapshot.json")
    private val temp = File(this.dir, "home_snapshot.json.tmp")
    private val json = Json { ignoreUnknownKeys = true }

    /** Grava o snapshot de forma atômica (tmp + rename). Falha não apaga o cache anterior. */
    @Synchronized
    fun save(snapshot: HomeSnapshot) {
        val ok = runCatching {
            temp.writeText(json.encodeToString(snapshot))
            moveReplacing(temp, file)
        }.isSuccess
        if (!ok) runCatching { temp.delete() }
    }

    private fun moveReplacing(from: File, to: File) {
        runCatching {
            java.nio.file.Files.move(
                from.toPath(),
                to.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE,
            )
        }.onFailure {
            java.nio.file.Files.move(
                from.toPath(),
                to.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    fun load(): HomeSnapshot? {
        val text = runCatching { file.readText() }.getOrNull() ?: return null
        return runCatching { json.decodeFromString<HomeSnapshot>(text) }.getOrNull()
    }

    fun clear() {
        runCatching { file.delete() }
    }
}
