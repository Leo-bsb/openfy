package com.openfy.core

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/** Protocolo IPC do Discord sem precisar do cliente aberto. */
class DiscordPayloadTest {

    @Test
    fun `frame usa opcode e tamanho little endian`() {
        val payload = DiscordPayload.handshake("123456789012345678")
        val frame = DiscordPayload.frame(0, payload)
        val buffer = ByteBuffer.wrap(frame).order(ByteOrder.LITTLE_ENDIAN)

        assertEquals(0, buffer.int)
        val size = buffer.int
        assertEquals(frame.size - 8, size)
        val text = String(frame.copyOfRange(8, frame.size), StandardCharsets.UTF_8)
        assertEquals(payload.toString(), text)
    }

    @Test
    fun `handshake leva versao e application id`() {
        val payload = DiscordPayload.handshake("123456789012345678")

        assertEquals("1", payload["v"]?.jsonPrimitive?.content)
        assertEquals("123456789012345678", payload["client_id"]?.jsonPrimitive?.content)
    }

    @Test
    fun `set activity monta comando com pid e nonce`() {
        val activity = DiscordPresence.Activity(
            details = "Faixa",
            state = "Artista · Álbum",
            startedAtMs = 1_700_000_000_000,
            playing = true,
        )
        val payload = DiscordPayload.setActivity("123456789012345678", 42, activity)
        val args = payload["args"]?.jsonObject

        assertEquals("SET_ACTIVITY", payload["cmd"]?.jsonPrimitive?.content)
        assertEquals("42", args?.get("pid")?.jsonPrimitive?.content)
        assertTrue(payload["nonce"]?.jsonPrimitive?.content?.isNotBlank() == true)

        val body = args?.get("activity")?.jsonObject
        assertEquals("2", body?.get("type")?.jsonPrimitive?.content)
        assertEquals("Faixa", body?.get("details")?.jsonPrimitive?.content)
        assertEquals("Artista · Álbum", body?.get("state")?.jsonPrimitive?.content)
        assertEquals(
            "1700000000",
            body?.get("timestamps")?.jsonObject?.get("start")?.jsonPrimitive?.content,
        )
        assertEquals(
            "play",
            body?.get("assets")?.jsonObject?.get("small_image")?.jsonPrimitive?.content,
        )
    }

    @Test
    fun `pausa remove timestamp e limpar atividade envia null`() {
        val paused = DiscordPresence.Activity(
            details = "Faixa",
            state = "Artista",
            startedAtMs = 1_700_000_000_000,
            playing = false,
        )
        val body = DiscordPayload.activityJson(paused)
        assertNull(body["timestamps"])
        assertEquals(
            "pause",
            body["assets"]?.jsonObject?.get("small_image")?.jsonPrimitive?.content,
        )

        val cleared = DiscordPayload.setActivity("123456789012345678", 42, null)
        assertTrue(cleared["args"]?.jsonObject?.get("activity") is JsonNull)
    }

    @Test
    fun `textos longos sao cortados sem passar do limite do discord`() {
        val body = DiscordPayload.activityJson(
            DiscordPresence.Activity(
                details = "d".repeat(300),
                state = "s".repeat(300),
                startedAtMs = null,
                playing = false,
            ),
        )
        val details = body["details"]?.jsonPrimitive?.content.orEmpty()
        val state = body["state"]?.jsonPrimitive?.content.orEmpty()

        assertTrue(details.length <= 128)
        assertTrue(state.length <= 128)
        assertTrue(details.endsWith("…"))
    }
}
