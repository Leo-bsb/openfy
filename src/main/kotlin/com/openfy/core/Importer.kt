package com.openfy.core

/**
 * Regras puras do importador: parse de URL do YouTube Music, leitura de CSV
 * (Exportify) e casamento de faixa no catalogo. Sem rede/DB, para ser testavel.
 */
object Importer {

    data class CsvRow(
        val title: String,
        val artist: String,
        val album: String,
        val durationMs: Long,
    )

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

    fun parseCsv(text: String): List<CsvRow> {
        val lines = text.removePrefix("\uFEFF").lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()

        fun split(line: String): List<String> {
            val out = mutableListOf<String>()
            val sb = StringBuilder()
            var quoted = false
            var i = 0
            while (i < line.length) {
                val c = line[i]
                when {
                    c == '"' && quoted && i + 1 < line.length && line[i + 1] == '"' -> {
                        sb.append('"'); i++
                    }
                    c == '"' -> quoted = !quoted
                    c == ',' && !quoted -> {
                        out += sb.toString(); sb.clear()
                    }
                    else -> sb.append(c)
                }
                i++
            }
            out += sb.toString()
            return out
        }

        val header = split(lines.first()).map { it.trim().lowercase() }
        fun indexOf(vararg names: String): Int =
            header.indexOfFirst { h -> names.any { it == h } }
        val titleIndex = indexOf("track name", "title", "name")
        val artistIndex = indexOf("artist name(s)", "artist", "artists")
        val albumIndex = indexOf("album name", "album")
        val durationIndex = indexOf("duration (ms)", "duration_ms", "duration")

        if (titleIndex < 0) {
            return lines.mapNotNull { line ->
                val cols = split(line)
                if (cols.size < 2) null else CsvRow(cols[0].trim(), cols[1].trim(), "", 0)
            }
        }
        return lines.drop(1).mapNotNull { line ->
            val cols = split(line)
            val title = cols.getOrNull(titleIndex)?.trim().orEmpty()
            if (title.isEmpty()) return@mapNotNull null
            CsvRow(
                title = title,
                artist = cols.getOrNull(artistIndex)?.trim().orEmpty(),
                album = if (albumIndex >= 0) cols.getOrNull(albumIndex)?.trim().orEmpty() else "",
                durationMs = if (durationIndex >= 0) {
                    cols.getOrNull(durationIndex)?.trim()?.toLongOrNull() ?: 0
                } else 0,
            )
        }
    }

    fun bestMatch(row: CsvRow, candidates: List<Track>): Track? {
        val title = normalize(row.title)
        if (title.isEmpty()) return null
        val artist = normalize(row.artist)
        var best: Track? = null
        var bestScore = 0
        for (candidate in candidates) {
            val candidateTitle = normalize(candidate.title)
            val candidateArtist = normalize(candidate.artist)
            var score = 0
            if (candidateTitle == title) {
                score += 4
            } else if (candidateTitle.contains(title) || title.contains(candidateTitle)) {
                score += 2
            }
            if (artist.isNotEmpty() &&
                (candidateArtist.contains(artist) || artist.contains(candidateArtist))
            ) {
                score += 3
            }
            if (row.durationMs > 0 && candidate.durationSec > 0) {
                val diff = kotlin.math.abs(row.durationMs / 1000 - candidate.durationSec)
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
