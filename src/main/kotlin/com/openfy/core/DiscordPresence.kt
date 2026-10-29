package com.openfy.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.RandomAccessFile
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.UUID

/**
 * Rich Presence sem OAuth: fala com o Discord desktop pelo pipe local.
 *
 * Só é ativado quando o usuário informa um Application ID em Ajustes. Sem ID,
 * nada é conectado e nenhum dado sai do computador. O Discord precisa estar
 * aberto; se não estiver, o worker tenta de novo em segundo plano sem travar a UI.
 */
class DiscordPresence(
    private val scope: CoroutineScope,
) {

    data class Activity(
        val details: String,
        val state: String,
        val startedAtMs: Long?,
        val playing: Boolean,
    )

    private val _status = MutableStateFlow("Desligado")
    val status: StateFlow<String> = _status.asStateFlow()

    @Volatile private var enabled = false
    @Volatile private var appId = ""
    @Volatile private var privateMode = false
    @Volatile private var closed = false
    @Volatile private var currentPipe: DiscordPipe? = null

    private var worker: Job? = null
    private var lastTrack: Track? = null
    private var lastPlaying = false
    private var lastPositionSec = 0.0
    private var lastSent: Activity? = null
    private var lastSentPipeId: String? = null
    private val signal = Channel<Unit>(Channel.CONFLATED)

    fun configure(enabled: Boolean, appId: String, privateMode: Boolean) {
        val cleanId = appId.trim()
        val changed = this.enabled != enabled || this.appId != cleanId || this.privateMode != privateMode
        this.enabled = enabled
        this.appId = cleanId
        this.privateMode = privateMode
        if (!changed && worker?.isActive == true) return
        if (!enabled) {
            closePipe()
            _status.value = "Desligado"
            return
        }
        if (cleanId.isBlank()) {
            closePipe()
            _status.value = "Informe o Application ID do Discord."
            return
        }
        if (!validAppId(cleanId)) {
            closePipe()
            _status.value = "Application ID inválido: use apenas os números do portal."
            return
        }
        _status.value = "Conectando ao Discord…"
        startWorker()
        signal.trySend(Unit)
    }

    /** Chamado pelo controlador quando faixa, play/pause ou posição mudam. */
    fun update(track: Track?, playing: Boolean, positionSec: Double) {
        lastTrack = track
        lastPlaying = playing
        lastPositionSec = positionSec.coerceAtLeast(0.0)
        rebuildActivity()
    }

    fun refresh() {
        rebuildActivity()
        signal.trySend(Unit)
    }

    /** Botão "Testar conexão": força uma nova tentativa agora. */
    fun test() {
        if (!enabled) {
            _status.value = "Ligue o Rich Presence primeiro."
            return
        }
        if (!validAppId(appId)) {
            _status.value = if (appId.isBlank()) {
                "Informe o Application ID do Discord."
            } else {
                "Application ID inválido: use apenas os números do portal."
            }
            return
        }
        closePipe()
        _status.value = "Testando conexão…"
        startWorker()
        signal.trySend(Unit)
    }

    fun shutdown() {
        closed = true
        worker?.cancel()
        worker = null
        closePipe()
        _status.value = "Desligado"
    }

    private fun rebuildActivity() {
        val activity = buildActivity()
        if (activity.sameAs(lastSent) && worker?.isActive == true) return
        signal.trySend(Unit)
    }

    private fun buildActivity(): Activity? {
        val track = lastTrack ?: return null
        val details = if (privateMode) "Ouvindo música" else track.title.ifBlank { "Música" }
        val state = if (privateMode) {
            "Openfy"
        } else {
            listOf(track.artist, track.album)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
                .ifBlank { "Openfy" }
        }
        val started = System.currentTimeMillis() - (lastPositionSec * 1000).toLong()
        return Activity(
            details = details,
            state = state,
            startedAtMs = started.takeIf { lastPlaying },
            playing = lastPlaying,
        )
    }

    private fun Activity?.sameAs(other: Activity?): Boolean {
        if (this == null || other == null) return this == null && other == null
        return details == other.details &&
            state == other.state &&
            playing == other.playing &&
            (startedAtMs ?: 0L) / 1000L == (other.startedAtMs ?: 0L) / 1000L
    }

    private fun startWorker() {
        if (worker?.isActive == true) return
        worker = scope.launch(Dispatchers.IO) {
            while (isActive && !closed) {
                val id = appId
                if (!enabled || !validAppId(id)) {
                    closePipe()
                    _status.value = when {
                        !enabled -> "Desligado"
                        id.isBlank() -> "Informe o Application ID do Discord."
                        else -> "Application ID inválido: use apenas os números do portal."
                    }
                    delay(1_000)
                    continue
                }
                val pipe = runCatching { connect(id) }.getOrNull()
                if (pipe == null) {
                    _status.value = "Discord não encontrado. Abra o Discord e tente de novo."
                    delay(15_000)
                    continue
                }
                currentPipe = pipe
                lastSent = null
                lastSentPipeId = null
                _status.value = "Conectado ao Discord."
                try {
                    var active = true
                    while (isActive && !closed && enabled && appId == id && active) {
                        sendCurrent(pipe, id)
                        // Acorda quando a faixa/estado mudar; o timeout só evita
                        // que o worker fique parado para sempre sem checar config.
                        val woke = withTimeoutOrNull(30_000) { signal.receive() }
                        if (woke == null) {
                            // Sem mudanças: não reenvia o mesmo payload (o
                            // timestamp do Discord já anda sozinho).
                            continue
                        }
                        if (enabled && appId != id) active = false
                    }
                } catch (error: Exception) {
                    Log.write("discord: conexão encerrada: ${error.message}")
                    _status.value = "Conexão caiu; tentando de novo…"
                } finally {
                    closePipe()
                }
                delay(5_000)
            }
        }
    }

    private fun sendCurrent(pipe: DiscordPipe, id: String) {
        val activity = buildActivity()
        if (activity.sameAs(lastSent) && lastSentPipeId == pipe.id) return
        val payload = DiscordPayload.setActivity(id, ProcessHandle.current().pid(), activity)
        pipe.write(DiscordPayload.frame(1, payload))
        lastSent = activity
        lastSentPipeId = pipe.id
    }

    private fun validAppId(id: String): Boolean =
        id.length in 15..32 && id.all { it.isDigit() }

    private fun connect(id: String): DiscordPipe? {
        val handshake = DiscordPayload.frame(0, DiscordPayload.handshake(id))
        if (System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)) {
            for (index in 0..9) {
                for (base in listOf("\\\\.\\pipe\\discord-ipc-$index", "\\\\?\\pipe\\discord-ipc-$index")) {
                    val pipe = runCatching {
                        val file = RandomAccessFile(base, "rw")
                        try {
                            file.write(handshake)
                            DiscordPipe(id = base, file = file, channel = null)
                        } catch (error: Exception) {
                            runCatching { file.close() }
                            throw error
                        }
                    }.getOrNull()
                    if (pipe != null) return pipe
                }
            }
            return null
        }

        val dirs = listOfNotNull(
            System.getenv("XDG_RUNTIME_DIR"),
            System.getenv("TMPDIR"),
            System.getenv("TMP"),
            System.getenv("TEMP"),
            "/tmp",
        ).filter { it.isNotBlank() }.distinct()
        for (dir in dirs) {
            for (index in 0..9) {
                val candidates = listOf(
                    "$dir/discord-ipc-$index",
                    "$dir/app/com.discordapp.Discord/discord-ipc-$index",
                    "$dir/snap.discord/discord-ipc-$index",
                )
                for (path in candidates) {
                    val pipe = runCatching {
                        val channel = SocketChannel.open(UnixDomainSocketAddress.of(Path.of(path)))
                        try {
                            val buffer = ByteBuffer.wrap(handshake)
                            while (buffer.hasRemaining()) channel.write(buffer)
                            DiscordPipe(id = path, file = null, channel = channel)
                        } catch (error: Exception) {
                            runCatching { channel.close() }
                            throw error
                        }
                    }.getOrNull()
                    if (pipe != null) return pipe
                }
            }
        }
        return null
    }

    private fun closePipe() {
        val pipe = currentPipe
        currentPipe = null
        lastSentPipeId = null
        runCatching { pipe?.close() }
    }

    private class DiscordPipe(
        val id: String,
        private val file: RandomAccessFile?,
        private val channel: SocketChannel?,
    ) {
        fun write(bytes: ByteArray) {
            if (file != null) {
                file.write(bytes)
                return
            }
            val target = channel ?: error("pipe sem canal")
            val buffer = ByteBuffer.wrap(bytes)
            while (buffer.hasRemaining()) target.write(buffer)
        }

        fun close() {
            runCatching { file?.close() }
            runCatching { channel?.close() }
        }
    }
}

/** Payload puro do protocolo IPC, separado para poder testar sem Discord aberto. */
object DiscordPayload {

    fun handshake(appId: String): JsonObject = buildJsonObject {
        put("v", 1)
        put("client_id", appId)
    }

    fun setActivity(appId: String, pid: Long, activity: DiscordPresence.Activity?): JsonObject =
        buildJsonObject {
            put("cmd", "SET_ACTIVITY")
            putJsonObject("args") {
                put("pid", pid)
                if (activity == null) {
                    put("activity", JsonNull)
                } else {
                    put("activity", activityJson(activity))
                }
            }
            put("nonce", UUID.randomUUID().toString())
        }

    fun activityJson(activity: DiscordPresence.Activity): JsonObject = buildJsonObject {
        put("type", 2) // Listening
        put("details", truncate(activity.details))
        put("state", truncate(activity.state))
        activity.startedAtMs?.takeIf { activity.playing }?.let { started ->
            putJsonObject("timestamps") { put("start", started / 1000) }
        }
        putJsonObject("assets") {
            put("large_image", "openfy")
            put("large_text", "Openfy")
            put("small_image", if (activity.playing) "play" else "pause")
            put("small_text", if (activity.playing) "Tocando" else "Pausado")
        }
        put("instance", false)
    }

    /** Frame do Discord: opcode e tamanho em little-endian, depois JSON UTF-8. */
    fun frame(opcode: Int, payload: JsonObject): ByteArray {
        val bytes = payload.toString().toByteArray(StandardCharsets.UTF_8)
        val buffer = ByteBuffer.allocate(8 + bytes.size).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(opcode)
        buffer.putInt(bytes.size)
        buffer.put(bytes)
        return buffer.array()
    }

    private fun truncate(value: String): String {
        val clean = value.replace('\u0000', ' ').trim()
        if (clean.length <= 128) return clean
        return clean.take(125) + "…"
    }
}
