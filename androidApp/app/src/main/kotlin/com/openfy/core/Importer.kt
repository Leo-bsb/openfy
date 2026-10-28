package com.openfy.core

/** Regras puras de importação compartilhadas com o desktop. */
object Importer {

    fun parseYtmBrowseId(url: String): String? {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return null
        Regex("""browse/(MPREb_[A-Za-z0-9_-]+)""").find(trimmed)?.groupValues?.get(1)?.let { return it }
        Regex("""[?&]list=([A-Za-z0-9_-]+)""").find(trimmed)?.groupValues?.get(1)?.let { list ->
            return if (list.startsWith("VL")) list else "VL$list"
        }
        if (trimmed.startsWith("MPREb_")) return trimmed
        if (trimmed.startsWith("VL")) return trimmed
        if (trimmed.startsWith("PL") || trimmed.startsWith("RD") ||
            trimmed.startsWith("OLAK5uy_") || trimmed.startsWith("RDCLAK")
        ) {
            return "VL$trimmed"
        }
        return null
    }

    /** Escolhe a melhor faixa do YouTube Music para um item do Spotify. */
    fun bestMatch(
        title: String,
        artist: String,
        durationMs: Long,
        candidates: List<Track>,
    ): Track? {
        val wantedTitle = normalize(title)
        if (wantedTitle.isEmpty()) return null
        val wantedArtist = normalize(artist)
        var best: Track? = null
        var bestScore = 0
        for (candidate in candidates) {
            val candidateTitle = normalize(candidate.title)
            val candidateArtist = normalize(candidate.artist)
            var score = 0
            if (candidateTitle == wantedTitle) {
                score += 4
            } else if (candidateTitle.contains(wantedTitle) || wantedTitle.contains(candidateTitle)) {
                score += 2
            }
            if (wantedArtist.isNotEmpty() &&
                (candidateArtist.contains(wantedArtist) || wantedArtist.contains(candidateArtist))
            ) {
                score += 3
            }
            if (durationMs > 0 && candidate.durationSec > 0) {
                val diff = kotlin.math.abs(durationMs / 1000 - candidate.durationSec)
                if (diff <= 4) score += 2 else if (diff <= 12) score += 1
            }
            if (score > bestScore) {
                bestScore = score
                best = candidate
            }
        }
        return if (bestScore >= 4) best else null
    }

    fun normalize(value: String): String =
        java.text.Normalizer.normalize(value.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
