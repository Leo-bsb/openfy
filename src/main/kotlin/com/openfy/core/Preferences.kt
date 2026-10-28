package com.openfy.core

/** Paletas prontas do app. O id é o que fica salvo no banco. */
enum class ColorPalette(val id: String, val label: String) {
    OPENFY("openfy", "Openfy"),
    VIOLET("violet", "Violeta"),
    ROSE("rose", "Rosé"),
    AMBER("amber", "Âmbar"),
    ICE("ice", "Gelo");

    companion object {
        fun fromId(id: String?): ColorPalette =
            entries.firstOrNull { it.id == id } ?: OPENFY
    }
}

/** Layout da tela cheia do player. */
enum class PlayerLayout(val id: String, val label: String) {
    CENTER("center", "Central"),
    SIDE("side", "Lateral");

    companion object {
        fun fromId(id: String?): PlayerLayout =
            entries.firstOrNull { it.id == id } ?: CENTER
    }
}
