package com.openfy.core

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path

/** PCM mono ja decodificado, pronto para o laboratorio de BPM e o Modo DJ. */
data class PcmAudio(val samples: FloatArray, val sampleRate: Int) {
    val durationSec: Double get() = if (sampleRate <= 0) 0.0 else samples.size / sampleRate.toDouble()
}

/**
 * Leitor de WAV independente de biblioteca externa. Aceita o que o mpv grava
 * (`--ao=pcm`: float32 em WAVE_FORMAT_EXTENSIBLE) e tambem 16/24/32 bits PCM,
 * sempre misturando os canais para mono.
 */
object WavReader {

    fun read(path: Path): PcmAudio? {
        val bytes = runCatching { Files.readAllBytes(path) }.getOrNull() ?: return null
        if (bytes.size < 44) return null
        if (String(bytes, 0, 4) != "RIFF" || String(bytes, 8, 4) != "WAVE") return null
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        var format = 1
        var channels = 1
        var sampleRate = 22050
        var bits = 16
        var dataOffset = -1
        var dataSize = 0
        var offset = 12
        while (offset + 8 <= bytes.size) {
            val id = String(bytes, offset, 4)
            val size = buffer.getInt(offset + 4)
            val body = offset + 8
            when (id) {
                "fmt " -> {
                    format = buffer.getShort(body).toInt() and 0xffff
                    channels = buffer.getShort(body + 2).toInt() and 0xffff
                    sampleRate = buffer.getInt(body + 4)
                    bits = buffer.getShort(body + 14).toInt() and 0xffff
                    // WAVE_FORMAT_EXTENSIBLE (0xFFFE): o subtipo real fica no
                    // GUID; o mpv grava float32, entao tratamos como IEEE float.
                    if (format == 0xFFFE && bits == 32) format = 3
                }
                "data" -> {
                    dataOffset = body
                    dataSize = size.coerceAtMost(bytes.size - body)
                }
            }
            offset = body + size + (size and 1)
        }
        if (dataOffset < 0 || dataSize <= 0 || sampleRate <= 0) return null
        val channelsSafe = channels.coerceAtLeast(1)
        val bytesPerSample = (bits / 8).coerceAtLeast(1)
        val frames = dataSize / bytesPerSample / channelsSafe
        if (frames <= 0) return null
        val samples = FloatArray(frames)
        var index = dataOffset
        repeat(frames) { frame ->
            var mixed = 0f
            repeat(channelsSafe) {
                mixed += when {
                    format == 3 && bits == 32 -> buffer.getFloat(index)
                    format == 1 && bits == 16 -> buffer.getShort(index) / 32768f
                    format == 1 && bits == 32 -> buffer.getInt(index) / 2147483648f
                    format == 1 && bits == 24 -> {
                        val b0 = bytes[index].toInt() and 0xff
                        val b1 = bytes[index + 1].toInt() and 0xff
                        val b2 = bytes[index + 2].toInt()
                        ((b2 shl 16) or (b1 shl 8) or b0) / 8388608f
                    }
                    else -> 0f
                }
                index += bytesPerSample
            }
            samples[frame] = mixed / channelsSafe
        }
        return PcmAudio(samples, sampleRate)
    }
}

/**
 * Decodifica um trecho de audio com o proprio mpv, sem passar pelo player.
 * Usado para analisar a grade ritmica em segundo plano; nunca toca som.
 */
object PcmDecoder {

    fun decode(
        mpv: Path,
        source: String,
        work: Path,
        startSec: Int = 0,
        endSec: Int = 72,
        sampleRate: Int = 22050,
    ): PcmAudio? {
        runCatching { Files.createDirectories(work) }
        val wav = work.resolve(
            "decode-${source.hashCode()}-${System.nanoTime()}.wav"
        )
        return try {
            val command = listOf(
                mpv.toString(),
                "--no-config",
                "--no-video",
                "--ao=pcm",
                "--ao-pcm-file=${wav.toAbsolutePath()}",
                "--audio-samplerate=$sampleRate",
                "--audio-channels=mono",
                "--start=$startSec",
                "--end=$endSec",
                "--really-quiet",
                source,
            )
            val process = runCatching {
                ProcessBuilder(command).redirectErrorStream(true).start()
            }.getOrNull() ?: return null
            // Evita travar a analise para sempre se o upstream nao responder.
            val finished = runCatching { process.waitFor(90, java.util.concurrent.TimeUnit.SECONDS) }
                .getOrDefault(false)
            if (!finished) {
                runCatching { process.destroyForcibly() }
                return null
            }
            if (process.exitValue() != 0 || !Files.isRegularFile(wav)) return null
            WavReader.read(wav)
        } finally {
            runCatching { Files.deleteIfExists(wav) }
        }
    }
}
