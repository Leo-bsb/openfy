package com.openfy.core

/**
 * Lista temporária da rádio (Spotify-like): a primeira página é montada sem
 * tocar nada e o usuário decide quando virar fila.
 */
object RadioList {

    const val MAX_TRACKS = 200

    fun titleForTrack(track: Track): String = "Rádio · ${track.title}"

    fun titleForArtist(name: String): String = "Rádio do artista · $name"

    fun subtitle(count: Int): String = "Playlist temporária · $count faixas"

    fun defaultPlaylistName(seedTitle: String?): String =
        "Rádio · ${seedTitle?.takeIf { it.isNotBlank() } ?: "Openfy"}"

    /** Junta páginas mantendo a ordem e ignorando ids e músicas repetidas. */
    fun merge(existing: List<Track>, extra: List<Track>, limit: Int = MAX_TRACKS): List<Track> {
        if (limit <= 0) return emptyList()
        val ids = HashSet<String>()
        val keys = HashSet<String>()
        val out = ArrayList<Track>(minOf(limit, existing.size + extra.size))
        for (track in existing + extra) {
            if (out.size >= limit) break
            if (track.id.isBlank() || track.title.isBlank()) continue
            val key = Recommender.canonicalKey(track)
            if (!ids.add(track.id) || !keys.add(key)) continue
            out += track
        }
        return out
    }
}
