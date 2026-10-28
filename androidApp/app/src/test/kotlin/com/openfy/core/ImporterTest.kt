package com.openfy.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regras puras do importador Android: parse de URL/album/playlist e matching. */
class ImporterTest {

    // ---------- parseYtmBrowseId: album, playlist e URL ----------

    @Test
    fun `parseYtmBrowseId extrai MPREb de URL de album`() {
        val url = "https://music.youtube.com/browse/MPREb_1pqacLtUfnB"
        assertEquals("MPREb_1pqacLtUfnB", Importer.parseYtmBrowseId(url))
    }

    @Test
    fun `parseYtmBrowseId extrai e prefixa VL do param list`() {
        val url = "https://music.youtube.com/watch?v=dQw4w9WgXcQ&list=OLAK5uy_nMr9h2VlS-2PULNz3M3XVXQj_P3C2bqaY"
        assertEquals("VLOLAK5uy_nMr9h2VlS-2PULNz3M3XVXQj_P3C2bqaY", Importer.parseYtmBrowseId(url))
    }

    @Test
    fun `parseYtmBrowseId mantem list que ja comeca com VL`() {
        assertEquals("VLPLssd9BzJ9d", Importer.parseYtmBrowseId("https://music.youtube.com/playlist?list=VLPLssd9BzJ9d"))
    }

    @Test
    fun `parseYtmBrowseId aceita IDs crus de playlist e radio`() {
        assertEquals("MPREb_abc123", Importer.parseYtmBrowseId("MPREb_abc123"))
        assertEquals("VLPLabc123", Importer.parseYtmBrowseId("PLabc123"))
        assertEquals("VLRDxyz-_9", Importer.parseYtmBrowseId("RDxyz-_9"))
        assertEquals("VLOLAK5uy_abc", Importer.parseYtmBrowseId("OLAK5uy_abc"))
        assertEquals("VLRDCLAK5uy_def", Importer.parseYtmBrowseId("RDCLAK5uy_def"))
    }

    @Test
    fun `parseYtmBrowseId retorna null para entradas invalidas`() {
        assertNull(Importer.parseYtmBrowseId(""))
        assertNull(Importer.parseYtmBrowseId("   "))
        assertNull(Importer.parseYtmBrowseId("https://youtube.com/watch?v=abc123"))
        assertNull(Importer.parseYtmBrowseId("pl12345"))
    }

    // ---------- normalize ----------

    @Test
    fun `normalize remove acentos, caixa e pontuacao`() {
        assertEquals("cao", Importer.normalize("Cão"))
        assertEquals("rihanna diamonds live", Importer.normalize("Rihanna - Diamonds (Live)!"))
        assertEquals("naive", Importer.normalize("Naïve"))
        assertEquals("", Importer.normalize("!!! ???"))
    }

    // ---------- bestMatch ----------

    private fun track(id: String, title: String, artist: String, durationSec: Int = 0) =
        Track(id = id, title = title, artist = artist, durationSec = durationSec)

    private val catalog = listOf(
        track("t1", "Bohemian Rhapsody", "Queen", 355),
        track("t2", "Another One Bites the Dust", "Queen", 215),
        track("t3", "Under Pressure", "Queen, David Bowie", 241),
    )

    @Test
    fun `bestMatch encontra titulo e artista exatos`() {
        assertEquals("t1", Importer.bestMatch("Bohemian Rhapsody", "Queen", 354933, catalog)?.id)
    }

    @Test
    fun `bestMatch casa artista parcial e duracao proxima`() {
        assertEquals("t3", Importer.bestMatch("Under Pressure", "Queen", 240000, catalog)?.id)
    }

    @Test
    fun `bestMatch retorna null sem pontuacao suficiente`() {
        assertNull(Importer.bestMatch("Something Else", "Unknown Artist", 100000, catalog))
    }

    @Test
    fun `bestMatch retorna null para titulo vazio ou catalogo vazio`() {
        assertNull(Importer.bestMatch("", "Queen", 0, catalog))
        assertNull(Importer.bestMatch("!!!", "Queen", 0, catalog))
        assertNull(Importer.bestMatch("Bohemian Rhapsody", "Queen", 0, emptyList()))
        assertTrue(catalog.size == 3)
    }

    @Test
    fun `bestMatch escolhe pontuacao mais alta entre concorrentes`() {
        assertEquals("t2", Importer.bestMatch("Another One Bites the Dust", "Queen", 214000, catalog)?.id)
    }
}
