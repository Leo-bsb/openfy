package com.openfy.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regras puras do perfil: o que a UI mostra nunca deve escapar do sanitizado. */
class ProfileTest {

    @Test
    fun `bio e capa sao limitadas e normalizadas`() {
        val extras = ProfileExtras(
            bio = "  " + "a".repeat(500) + "\u0000  ",
            coverUrl = "  https://exemplo/capa.jpg  ",
            accent = "inexistente",
        ).sanitized()

        assertEquals(ProfileExtras.BIO_LIMIT, extras.bio.length)
        assertTrue(extras.bio.none { it == '\u0000' })
        assertEquals("https://exemplo/capa.jpg", extras.coverUrl)
        assertEquals(ColorPalette.OPENFY.id, extras.accent)
    }

    @Test
    fun `pins ignoram vazios duplicados e limitam a dez`() {
        val pins = buildList {
            add(ProfilePin(id = "", title = "Sem id"))
            add(ProfilePin(id = "a", title = "Primeira"))
            add(ProfilePin(id = "a", title = "Duplicada"))
            repeat(12) { index -> add(ProfilePin(id = "t$index", title = "Faixa $index")) }
        }
        val clean = ProfileExtras(pins = pins).sanitized().pins

        assertEquals(ProfileExtras.PIN_LIMIT, clean.size)
        assertEquals("a", clean.first().id)
        assertEquals(clean.size, clean.map { it.id }.distinct().size)
        assertTrue(clean.all { it.id.isNotBlank() && it.title.isNotBlank() })
    }

    @Test
    fun `ordem dos cartoes completa e remove repetidos`() {
        val order = ProfileStatsCards.sanitizeOrder(
            listOf(
                ProfileStatsCards.TOP_TRACKS,
                ProfileStatsCards.MINUTES,
                ProfileStatsCards.TOP_TRACKS,
                "desconhecido",
            ),
        )

        assertEquals(ProfileStatsCards.TOP_TRACKS, order.first())
        assertEquals(ProfileStatsCards.MINUTES, order[1])
        assertEquals(ProfileStatsCards.all.size, order.size)
        assertEquals(ProfileStatsCards.all.toSet(), order.toSet())
    }

    @Test
    fun `visibilidade invalida cai para privado`() {
        assertEquals(
            ProfileStatsVisibility.PRIVATE,
            ProfileExtras(statsVisibility = "amigos").sanitized().statsVisibility,
        )
        assertEquals(
            ProfileStatsVisibility.PUBLIC,
            ProfileExtras(statsVisibility = "public").sanitized().statsVisibility,
        )
    }

    @Test
    fun `pin converte para faixa e volta sem perder metadados`() {
        val track = Track(
            id = "abc",
            title = "Música",
            artist = "Artista",
            album = "Álbum",
            durationSec = 215,
            thumbnail = "https://img/abc",
        )

        assertEquals(track, ProfilePin.from(track).toTrack())
    }

    @Test
    fun `cartao oculto invalido e descartado`() {
        val clean = ProfileExtras(
            statsHidden = listOf("likes", "likes", "nope"),
        ).sanitized()

        assertEquals(listOf("likes"), clean.statsHidden)
    }
}
