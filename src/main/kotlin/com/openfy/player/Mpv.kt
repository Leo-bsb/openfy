package com.openfy.player

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.concurrent.thread

/**
 * Controle do mpv por named pipe (JSON IPC, uma mensagem por linha).
 * O mpv nunca recebe URL do Google: ele toca http://127.0.0.1:<porta>/t/<id>,
 * servido pelo StreamServer. URL expirada deixa de ser caso de erro.
 *
 * Comandos nunca sao escritos pela thread chamadora: entram numa fila e uma
 * thread dedicada escreve e drena a resposta de cada comando. Sem isso, o
 * canal de comandos (que ninguem lia) enchia de replies e o mpv parava de
 * ler, travando a UI no primeiro write seguinte.
 */
class Mpv(
    private val exe: Path,
    private val logFile: Path? = null,
    private val suffix: String = "",
) {

    sealed interface Event {
        data class Time(val position: Double, val duration: Double) : Event
        data class Paused(val paused: Boolean) : Event
        data class Ended(val reason: String) : Event
        data class Started(val duration: Double) : Event
        data class Volume(val value: Int) : Event
        data class Failed(val reason: String) : Event
        data object Closed : Event
    }

    val events = MutableSharedFlow<Event>(extraBufferCapacity = 256)
    val ready = MutableStateFlow(false)

    private val json = Json { ignoreUnknownKeys = true }
    private var process: Process? = null

    /*
     * Dois canais no named pipe: um so para eventos (reader) e outro so para
     * comandos (writer). O writer e lido por uma thread de comandos apenas
     * para consumir as respostas (acks), mantendo o buffer do pipe vazio.
     */
    private var reader: FileChannel? = null
    private var writer: FileChannel? = null
    private val outbox = ConcurrentLinkedQueue<String>()
    private var commandThread: Thread? = null
    @Volatile private var closed = false
    @Volatile private var connecting = false
    @Volatile private var lastDuration = 0.0
    private val pipePath = "\\\\.\\pipe\\openfy-mpv-${ProcessHandle.current().pid()}$suffix"

    /**
     * Garante que o mpv esteja vivo E com IPC conectado. Sem isso, uma queda do
     * pipe deixava o processo vivo e todos os comandos parados na fila para sempre.
     */
    fun start() {
        if (process?.isAlive == true) {
            if (!ready.value) ensureConnected()
            startCommandThread()
            return
        }
        closed = false
        val cmd = buildList {
            add(exe.toString())
            add("--no-video")
            add("--idle=yes")
            add("--no-terminal")
            add("--input-ipc-server=$pipePath")
            add("--audio-client-name=Openfy$suffix")
            add("--cache=yes")
            add("--demuxer-max-bytes=96MiB")
            add("--demuxer-readahead-secs=20")
            add("--gapless-audio=weak")
            add("--volume=80")
            add("--keep-open=no")
            add("--force-window=no")
            if (logFile != null) {
                runCatching { Files.deleteIfExists(logFile) }
                add("--log-file=$logFile")
            }
        }
        val pb = ProcessBuilder(cmd).redirectErrorStream(true)
        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD)
        val started = runCatching { pb.start() }
            .onFailure { com.openfy.core.Log.write("mpv: falha ao iniciar $exe: $it") }
            .getOrNull()
        process = started ?: return
        com.openfy.core.Log.write("mpv: pid=${started.pid()} pipe=$pipePath")
        ensureConnected()
        startCommandThread()
    }

    fun processId(): Long? = process?.pid()

    @Synchronized
    private fun ensureConnected() {
        if (ready.value || connecting || closed) return
        if (process?.isAlive != true) return
        connecting = true
        thread(name = "mpv-connect", isDaemon = true) {
            try {
                connect()
            } finally {
                connecting = false
            }
        }
    }

    private fun connect() {
        val deadline = System.currentTimeMillis() + 10_000
        var lastError: Exception? = null
        while (System.currentTimeMillis() < deadline && !closed) {
            if (process?.isAlive != true) {
                com.openfy.core.Log.write("mpv: processo morreu durante a conexao")
                break
            }
            var r: FileChannel? = null
            var w: FileChannel? = null
            try {
                r = FileChannel.open(
                    Path.of(pipePath),
                    StandardOpenOption.READ,
                    StandardOpenOption.WRITE,
                )
                w = FileChannel.open(
                    Path.of(pipePath),
                    StandardOpenOption.READ,
                    StandardOpenOption.WRITE,
                )
                reader = r
                writer = w
                observe(r)
                // O canal de comandos nao recebe eventos; a resposta deste
                // comando tambem e drenada pela thread de comandos.
                writeLine(w, """{"command":["disable_event","all"]}""")
                ready.value = true
                startCommandThread()
                com.openfy.core.Log.write("mpv: ipc conectado (2 canais)")
                readLoop(r)
                return
            } catch (e: Exception) {
                lastError = e
                if (reader === r) reader = null
                if (writer === w) writer = null
                runCatching { r?.close() }
                runCatching { w?.close() }
                Thread.sleep(120)
            }
        }
        com.openfy.core.Log.write(
            "mpv: ipc nao conectou em 10s (processo vivo=${process?.isAlive}, erro=${lastError?.message})"
        )
        events.tryEmit(Event.Closed)
    }

    private fun observe(channel: FileChannel) {
        writeLine(channel, """{"command":["observe_property",1,"time-pos"]}""")
        writeLine(channel, """{"command":["observe_property",2,"duration"]}""")
        writeLine(channel, """{"command":["observe_property",3,"pause"]}""")
        writeLine(channel, """{"command":["observe_property",4,"volume"]}""")
    }

    private fun readLoop(channel: FileChannel) {
        val buffer = ByteBuffer.allocate(8192)
        val line = ByteArrayOutputStream(2048)
        try {
            while (!closed) {
                buffer.clear()
                val count = channel.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                val bytes = buffer.array()
                for (i in 0 until count) {
                    val b = bytes[i].toInt() and 0xFF
                    when (b) {
                        '\n'.code -> {
                            val text = line.toString(Charsets.UTF_8.name())
                            line.reset()
                            if (text.isNotBlank()) handle(text)
                        }
                        '\r'.code -> Unit
                        else -> line.write(b)
                    }
                }
            }
        } catch (e: Exception) {
            if (!closed) com.openfy.core.Log.write("mpv: leitura do ipc falhou: ${e.message}")
        } finally {
            ready.value = false
            if (reader === channel) reader = null
            val w = writer
            writer = null
            runCatching { channel.close() }
            runCatching { w?.close() }
            com.openfy.core.Log.write("mpv: ipc desconectado (processo vivo=${process?.isAlive})")
            events.tryEmit(Event.Closed)
        }
    }

    private fun handle(line: String) {
        val obj = try {
            json.parseToJsonElement(line).jsonObject
        } catch (_: Exception) {
            return
        }
        when (obj["event"]?.text()) {
            "property-change" -> {
                val name = obj["name"]?.text() ?: return
                val data = obj["data"] ?: return
                when (name) {
                    "time-pos" -> {
                        val pos = data.number() ?: return
                        events.tryEmit(Event.Time(pos, lastDuration))
                    }
                    "duration" -> {
                        val d = data.number() ?: return
                        if (d > 0) {
                            lastDuration = d
                            events.tryEmit(Event.Started(d))
                        }
                    }
                    "pause" -> events.tryEmit(Event.Paused(data.bool() ?: false))
                    "volume" -> events.tryEmit(Event.Volume(data.number()?.toInt() ?: 0))
                }
            }
            // "stop" tambem chega ao trocar de faixa; so "eof" significa fim real.
            "end-file" -> when (val reason = obj["reason"]?.text()) {
                "eof" -> events.tryEmit(Event.Ended("eof"))
                "error" -> events.tryEmit(Event.Failed(reason))
                else -> Unit // stop/quit/redirect: troca de faixa ou encerramento normal
            }
            "start-file" -> lastDuration = 0.0
            "file-loaded" -> events.tryEmit(Event.Started(lastDuration))
        }
    }

    fun load(url: String, startAt: Double = 0.0) {
        val payload = buildJsonArray {
            add(JsonPrimitive("loadfile"))
            add(JsonPrimitive(url))
            add(JsonPrimitive("replace"))
            if (startAt > 1.0) {
                add(JsonPrimitive("start=" + String.format(Locale.ROOT, "%.1f", startAt)))
            }
        }
        com.openfy.core.Log.write("mpv: carregando ${url.substringAfterLast('/')} (ready=${ready.value})")
        send("""{"command":$payload}""")
    }

    fun pause(value: Boolean) = send("""{"command":["set_property","pause",$value]}""")

    fun seekSeconds(seconds: Double) =
        send("""{"command":["seek",${String.format(Locale.ROOT, "%.3f", seconds)},"absolute"]}""")

    fun volume(value: Int) = send("""{"command":["set_property","volume",${value.coerceIn(0, 130)}]}""")

    fun stop() = send("""{"command":["stop"]}""")

    /** Aplica uma cadeia de filtros FFmpeg (equalizador e/ou corte de graves). */
    fun setAudioFilters(filters: List<String>) {
        val chain = filters.filter { it.isNotBlank() }.joinToString(",")
        if (chain.isEmpty()) {
            send("""{"command":["set_property","af",""]}""")
            return
        }
        send("""{"command":["set_property","af","lavfi=[$chain]"]}""")
        com.openfy.core.Log.write("mpv: filtros aplicados ($chain)")
    }

    /** Equalizador por bandas via filtros do FFmpeg no mpv. */
    fun setEqualizer(frequencies: List<Int>, gains: List<Int>) {
        setAudioFilters(
            frequencies.zip(gains).map { (freq, gain) ->
                "equalizer=f=$freq:t=q:w=1:g=$gain"
            },
        )
    }

    fun clearEqualizer() {
        setAudioFilters(emptyList())
    }

    fun shutdown() {
        closed = true
        val r = reader
        val w = writer
        reader = null
        writer = null
        ready.value = false
        // Destruir o processo primeiro quebra o pipe e libera leituras bloqueadas.
        runCatching { process?.destroy() }
        process = null
        // Fechar os canais pode bloquear se a thread de comandos estiver lendo;
        // fazemos isso fora da thread chamadora (que pode ser a UI).
        thread(name = "mpv-shutdown", isDaemon = true) {
            runCatching { r?.close() }
            runCatching { w?.close() }
        }
    }

    /** Enfileira sem bloquear a thread chamadora (UI, midia keys, eventos). */
    private fun send(payload: String) {
        if (outbox.size < 1024) outbox.offer(payload)
        ensureConnected()
        startCommandThread()
    }

    @Synchronized
    private fun startCommandThread() {
        if (closed) return
        if (commandThread?.isAlive == true) return
        commandThread = thread(name = "mpv-command", isDaemon = true) {
            while (!closed) {
                val payload = outbox.poll()
                if (payload == null) {
                    Thread.sleep(20)
                    continue
                }
                val channel = writer
                if (channel == null || !ready.value) {
                    outbox.offer(payload)
                    Thread.sleep(40)
                    continue
                }
                // "async": a resposta volta assim que o mpv aceita o comando;
                // le-la evita que o buffer do pipe encha.
                val wire = payload.removeSuffix("}") + ",\"async\":true}"
                if (!writeLine(channel, wire)) {
                    outbox.offer(payload)
                    Thread.sleep(40)
                    continue
                }
                readReplyLine(channel)
            }
        }
    }

    /** Le uma linha de resposta do canal de comandos (descarta o conteudo). */
    private fun readReplyLine(channel: FileChannel): Boolean {
        val buffer = ByteBuffer.allocate(4096)
        val line = ByteArrayOutputStream(256)
        return try {
            while (!closed) {
                buffer.clear()
                val count = channel.read(buffer)
                if (count < 0) return false
                if (count == 0) continue
                val bytes = buffer.array()
                for (i in 0 until count) {
                    val b = bytes[i].toInt() and 0xFF
                    if (b == '\n'.code) return line.size() >= 0
                    line.write(b)
                }
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    private fun writeLine(channel: FileChannel, payload: String): Boolean =
        runCatching {
            val buffer = ByteBuffer.wrap((payload + "\n").toByteArray(Charsets.UTF_8))
            while (buffer.hasRemaining()) channel.write(buffer)
        }.onFailure {
            com.openfy.core.Log.write("mpv: falha ao escrever no ipc: ${it.message}")
        }.isSuccess

    companion object {
        /** mpv.exe ao lado do app empacotado primeiro; depois PATH. */
        fun locate(): Path? {
            val candidates = buildList {
                System.getProperty("compose.application.resources.dir")?.let {
                    add(Path.of(it, "mpv.exe"))
                    add(Path.of(it, "windows", "mpv.exe"))
                }
                add(Path.of(System.getProperty("openfy.home", "."), "mpv.exe"))
                add(Path.of(System.getProperty("user.dir"), "mpv.exe"))
                System.getenv("PATH")?.split(';')?.forEach { dir ->
                    if (dir.isNotBlank()) add(Path.of(dir, "mpv.exe"))
                }
            }
            return candidates.firstOrNull { Files.isRegularFile(it) }
        }
    }
}

private fun JsonElement.text(): String? =
    if (this is JsonNull) null else runCatching { jsonPrimitive.content }.getOrNull()

private fun JsonElement.number(): Double? = runCatching { jsonPrimitive.content.toDouble() }.getOrNull()

private fun JsonElement.bool(): Boolean? = runCatching { jsonPrimitive.content.toBooleanStrict() }.getOrNull()
