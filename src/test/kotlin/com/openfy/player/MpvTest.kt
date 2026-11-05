package com.openfy.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MpvTest {

    @Test
    fun `loadfile com start inclui o indice inteiro antes das opcoes`() {
        val payload = Mpv.loadPayload("http://127.0.0.1:9000/t/abc", 13.293)
        assertEquals(
            """{"command":["loadfile","http://127.0.0.1:9000/t/abc","replace",-1,"start=13.293"]}""",
            payload,
        )
    }

    @Test
    fun `loadfile sem start nao adiciona opcoes`() {
        val payload = Mpv.loadPayload("http://127.0.0.1:9000/t/abc")
        assertEquals("""{"command":["loadfile","http://127.0.0.1:9000/t/abc","replace",-1]}""", payload)
    }

    @Test
    fun `start pequeno e ignorado`() {
        val payload = Mpv.loadPayload("http://x/t/a", 0.01)
        assertTrue(payload.endsWith(",\"replace\",-1]}"))
    }
}
