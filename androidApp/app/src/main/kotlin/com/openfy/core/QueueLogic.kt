package com.openfy.core

import kotlin.random.Random

/** Operações puras da fila usadas pelo player Android. */
object QueueLogic {

    /**
     * Embaralha mantendo a faixa atual na posição 0, para o player não trocar
     * de música ao ligar o aleatório. Devolve também o novo índice.
     */
    fun shuffled(
        queue: List<Track>,
        currentIndex: Int,
        random: Random = Random.Default,
    ): Pair<List<Track>, Int> {
        if (queue.isEmpty()) return emptyList<Track>() to -1
        val current = queue.getOrNull(currentIndex)
            ?: return queue.shuffled(random) to 0
        val rest = queue.filterIndexed { index, _ -> index != currentIndex }.shuffled(random)
        return (listOf(current) + rest) to 0
    }

    /**
     * Restaura a ordem original guardada ao desligar o aleatório. Faixas que
     * entraram na fila depois do snapshot são mantidas no fim.
     */
    fun restored(
        snapshot: List<Track>,
        currentQueue: List<Track>,
        currentTrackId: String?,
    ): Pair<List<Track>, Int> {
        val extras = currentQueue.filter { track -> snapshot.none { it.id == track.id } }
        val restored = snapshot + extras
        if (restored.isEmpty()) return emptyList<Track>() to -1
        val at = currentTrackId?.let { id -> restored.indexOfFirst { it.id == id } } ?: -1
        return restored to (if (at >= 0) at else 0)
    }

    /** Move uma faixa dentro da fila, com índices fora da faixa sendo ignorados. */
    fun moved(queue: List<Track>, from: Int, to: Int): List<Track> {
        if (from !in queue.indices) return queue
        val target = to.coerceIn(0, queue.lastIndex)
        if (from == target) return queue
        val copy = queue.toMutableList()
        val track = copy.removeAt(from)
        copy.add(target, track)
        return copy
    }

    /** Próximo índice automático respeitando repetição; null quando não há próximo. */
    fun autoNextIndex(currentIndex: Int, size: Int, repeat: RepeatMode): Int? {
        if (size <= 0) return null
        val next = currentIndex + 1
        return when {
            next in 0 until size -> next
            repeat == RepeatMode.ALL -> 0
            else -> null
        }
    }
}
