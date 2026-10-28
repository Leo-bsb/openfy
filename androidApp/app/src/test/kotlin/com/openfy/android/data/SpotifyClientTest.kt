package com.openfy.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * `SpotifyClient.playlistId` e parser puro de URL/URI.
 * Testamos so o parse, sem rede (playlist() continua exigindo Spotify).
 */
class SpotifyClientTest {

    @Test
    fun `extrai id de URL publica de playlist`() {
        assertEquals(
            "37i9dQZF1DXcBWIGoYBM5M",
            SpotifyClient.playlistId("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M?si=abc123"),
        )
    }

    @Test
    fun `extrai id de URL embed`() {
        assertEquals(
            "4aW9657lIS4cFo2d2FybNV",
            SpotifyClient.playlistId("https://open.spotify.com/embed/playlist/4aW9657lIS4cFo2d2FybNV"),
        )
    }

    @Test
    fun `extrai id de uri spotify playlist dot dois pontos`() {
        assertEquals(
            "37i9dQZF1DX0XUsuxWHRQd",
            SpotifyClient.playlistId("spotify:playlist:37i9dQZF1DX0XUsuxWHRQd"),
        )
    }

    @Test
    fun `extrai id mesmo com caminho extra antes de playlist`() {
        assertEquals(
            "2LewQRbYRPlnhjBpZbHhfj",
            SpotifyClient.playlistId("https://play.spotify.com/user/other/playlist/2LewQRbYRPlnhjBpZbHhfj"),
        )
    }

    @Test
    fun `tolera espacos nas bordas`() {
        assertEquals(
            "ABC123abc",
            SpotifyClient.playlistId("  https://open.spotify.com/playlist/ABC123abc  "),
        )
    }

    @Test
    fun `retorna null para URL que nao e playlist`() {
        assertNull(SpotifyClient.playlistId("https://open.spotify.com/track/6DCdBcOXTjMKcjwME7mnWL"))
        assertNull(SpotifyClient.playlistId("https://open.spotify.com/album/1FoMBIwiS1i7CEtADWz9da"))
        assertNull(SpotifyClient.playlistId(""))
        assertNull(SpotifyClient.playlistId("https://example.com"))
    }
}
