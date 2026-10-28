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
}
