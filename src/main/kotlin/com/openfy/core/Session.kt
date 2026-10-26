package com.openfy.core

import com.openfy.data.Db
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Estado restauravel entre sessoes: fila, faixa atual, posicao, volume e modos.
 * Vive no SQLite (tabela `setting`) como JSON; o `RepeatMode` vai como string
 * para nao depender de serializacao de enum.
 */
@Serializable
data class SessionState(
    val queue: List<Track> = emptyList(),
    val index: Int = -1,
    val position: Double = 0.0,
    val volume: Int = 80,
    val shuffle: Boolean = false,
    val repeat: String = "OFF",
)

object SessionCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun load(db: Db): SessionState? {
        val raw = db.get(KEY) ?: return null
        return runCatching { json.decodeFromString<SessionState>(raw) }.getOrNull()
    }

    fun save(db: Db, state: SessionState) {
        runCatching { db.put(KEY, json.encodeToString(state)) }
    }

    const val KEY = "session.state"
}
