package com.openfy.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Testes das regras puras do importador desktop (sem rede/DB). */
class ImporterTest {

    // ---------- parseYtmBrowseId ----------

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
        val url = "https://music.youtube.com/playlist?list=VLPLssd9BzJ9d"
        assertEquals("VLPLssd9BzJ9d", Importer.parseYtmBrowseId(url))
    }

    @Test
    fun `parseYtmBrowseId aceita IDs crus conhecidos`() {
        assertEquals("MPREb_abc123", Importer.parseYtmBrowseId("MPREb_abc123"))
        assertEquals("VLPLabc123", Importer.parseYtmBrowseId("VLPLabc123"))
        assertEquals("VLPLabc123", Importer.parseYtmBrowseId("PLabc123"))
        assertEquals("VLRDxyz-_9", Importer.parseYtmBrowseId("RDxyz-_9"))
        assertEquals(
            "VLRDCLAK5uy_mBePqrK7FtE",
            Importer.parseYtmBrowseId("RDCLAK5uy_mBePqrK7FtE"),
        )
    }

    @Test
    fun `parseYtmBrowseId retorna null para entradas invalidas`() {
        assertNull(Importer.parseYtmBrowseId(""))
        assertNull(Importer.parseYtmBrowseId("   "))
        assertNull(Importer.parseYtmBrowseId("https://youtube.com/watch?v=abc123"))
        assertNull(Importer.parseYtmBrowseId("pl12345"))
    }

    @Test
    fun `parseYtmBrowseId tolera espacos nas bordas`() {
        assertEquals("MPREb_abc", Importer.parseYtmBrowseId("  https://music.youtube.com/browse/MPREb_abc  "))
    }

    // ---------- parseCsv ----------

    private val exportifyCsv = """
        Track Name,Artist Name(s),Album Name,Duration (ms)
        "Bohemian Rhapsody","Queen","A Night at the Opera","354933"
        "Under Pressure","Queen, David Bowie","Hot Space","241266"
    """.trimIndent()

    @Test
    fun `parseCsv le exportify com colunas nomeadas`() {
        val rows = Importer.parseCsv(exportifyCsv)
        assertEquals(2, rows.size)
        assertEquals(
            Importer.CsvRow("Bohemian Rhapsody", "Queen", "A Night at the Opera", 354933),
            rows.first(),
        )
        assertEquals("Under Pressure", rows[1].title)
        assertEquals("Queen, David Bowie", rows[1].artist)
        assertEquals(241266L, rows[1].durationMs)
    }

    @Test
    fun `parseCsv remove BOM e ignora linhas em branco`() {
        val text = "\uFEFFTrack Name,Artist Name(s)\n\nSong,Artist\n\n"
        val rows = Importer.parseCsv(text)
        assertEquals(1, rows.size)
        assertEquals("Song", rows.first().title)
        assertEquals("Artist", rows.first().artist)
    }

    @Test
    fun `parseCsv descobre aspas com escape duplo`() {
        val text = "title,artist\n\"He said \"\"oi\"\"\",Whoever"
        val rows = Importer.parseCsv(text)
        assertEquals(1, rows.size)
        assertEquals("He said \"oi\"", rows.first().title)
    }

    @Test
    fun `parseCsv aceita cabecalhos alternativos`() {
        val text = "Name,Artists,Album,duration_ms\nAlpha,Beta,Gamma,120000"
        val rows = Importer.parseCsv(text)
        assertEquals(Importer.CsvRow("Alpha", "Beta", "Gamma", 120000), rows.first())
    }

    @Test
    fun `parseCsv sem titulo conhecido usa pares simples`() {
        val text = "foo,bar\nla,li"
        val rows = Importer.parseCsv(text)
        assertEquals(2, rows.size)
        assertEquals(Importer.CsvRow("foo", "bar", "", 0), rows.first())
        assertEquals(Importer.CsvRow("la", "li", "", 0), rows[1])
    }

    @Test
    fun `parseCsv descarta linha sem titulo e texto vazio`() {
        assertTrue(Importer.parseCsv("").isEmpty())
        val rows = Importer.parseCsv("title,artist\n,SomeArtist\nReal,Song2")
        assertEquals(listOf("Real"), rows.map { it.title })
    }

    @Test
    fun `parseCsv trata duracao invalida como zero e colunas faltando`() {
        val rows = Importer.parseCsv("title,artist,duration (ms)\nSong,X,abc\nShort,Y")
        assertEquals(0L, rows.first().durationMs)
        assertEquals("Short", rows[1].title)
        assertEquals("Y", rows[1].artist)
        assertEquals("", rows[1].album)
        assertEquals(0L, rows[1].durationMs)
    }

    // ---------- normalize ----------

    @Test
    fun `normalize remove acentos, caixa e pontuacao`() {
        assertEquals("cao", Importer.normalize("Cão"))
        assertEquals("rihanna diamonds live", Importer.normalize("Rihanna - Diamonds (Live)!"))
        assertEquals("a b", Importer.normalize("  a \t\n b  "))
        assertEquals("naive", Importer.normalize("Naïve"))
    }

    @Test
    fun `normalize de string vazia ou simbolica e vazio`() {
        assertEquals("", Importer.normalize(""))
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
        val row = Importer.CsvRow("Bohemian Rhapsody", "Queen", "", 354933)
        assertEquals("t1", Importer.bestMatch(row, catalog)?.id)
    }

    @Test
    fun `bestMatch casa artista bidirecional e duracao proxima`() {
        val row = Importer.CsvRow("Under Pressure", "Queen", "", 240000)
        assertEquals("t3", Importer.bestMatch(row, catalog)?.id)
    }

    @Test
    fun `bestMatch retorna null sem pontuacao suficiente`() {
        val row = Importer.CsvRow("Something Else", "Unknown Artist", "", 100000)
        assertNull(Importer.bestMatch(row, catalog))
    }

    @Test
    fun `bestMatch retorna null para catalogo vazio ou titulo vazio`() {
        val row = Importer.CsvRow("Bohemian Rhapsody", "Queen", "", 0)
        assertNull(Importer.bestMatch(row, emptyList()))
        assertNull(Importer.bestMatch(Importer.CsvRow("", "Queen", "", 0), catalog))
        assertNull(Importer.bestMatch(Importer.CsvRow("!!!", "Queen", "", 0), catalog))
    }

    @Test
    fun `bestMatch escolhe pontuacao mais alta entre concorrentes`() {
        val row = Importer.CsvRow("Another One Bites the Dust", "Queen", "", 214000)
        assertEquals("t2", Importer.bestMatch(row, catalog)?.id)
    }
}
