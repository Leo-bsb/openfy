package com.openfy.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Parse de link e conversão de id base62 para GID, sem rede. */
class SpotifyClientTest {

    @Test
    fun `extrai id de link e uri`() {
        assertEquals(
            "37i9dQZF1DXcBWIGoYBM5M",
            SpotifyClient.playlistId("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M?si=abc"),
        )
        assertEquals(
            "37i9dQZF1DXcBWIGoYBM5M",
            SpotifyClient.playlistId("spotify:playlist:37i9dQZF1DXcBWIGoYBM5M"),
        )
        assertNull(SpotifyClient.playlistId("https://open.spotify.com/track/6HSXNV0b4M4cLJ7ljgVVeh"))
    }

    @Test
    fun `converte id base62 para gid hexadecimal`() {
        assertEquals(
            "dc661595a3d848f4a682c2f379459ff1",
            SpotifyClient.trackGid("6HSXNV0b4M4cLJ7ljgVVeh"),
        )
        assertNull(SpotifyClient.trackGid(""))
    }

    @Test
    fun `pkce s256 segue o vetor da rfc 7636`() {
        assertEquals(
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            spotifyPkceChallenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"),
        )
    }
}
