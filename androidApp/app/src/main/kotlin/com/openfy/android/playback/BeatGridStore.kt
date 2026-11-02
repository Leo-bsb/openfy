package com.openfy.android.playback

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import com.openfy.core.BeatAnalyzer
import com.openfy.core.BeatGrid
import com.openfy.core.Log
import java.nio.ByteOrder

/**
 * Grade ritmica no Android. Decodifica os primeiros segundos de um arquivo
 * local (ou baixado) com MediaCodec e roda o mesmo detector do desktop.
 *
 * Streams remotos nao passam por aqui: sem grade, o mix cai no N1 (fade
 * inteligente), que continua melhor que o crossfade linear antigo.
 *
 * O resultado fica em SharedPreferences, uma entrada por faixa; cada aparelho
 * analisa cada faixa uma unica vez.
 */
@UnstableApi
class BeatGridStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("openfy_beat_grid", Context.MODE_PRIVATE)

    fun cached(trackId: String): BeatGrid? {
        if (trackId.isBlank()) return null
        val raw = prefs.getString(trackId, null) ?: return null
        return runCatching { decode(raw) }.getOrNull()
    }

    fun store(trackId: String, grid: BeatGrid) {
        prefs.edit().putString(trackId, encode(grid)).apply()
    }

    /** Analisa [uri] e guarda o resultado. Retorna null quando nao da para confiar. */
    fun analyze(trackId: String, uri: Uri, durationSec: Int): BeatGrid? {
        cached(trackId)?.let { return it }
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        return try {
            extractor.setDataSource(appContext, uri, null)
            var trackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val candidate = extractor.getTrackFormat(i)
                val mime = candidate.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    trackIndex = i
                    format = candidate
                    break
                }
            }
            if (trackIndex < 0 || format == null) return null
            extractor.selectTrack(trackIndex)

            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            val sourceRate = intOf(format, MediaFormat.KEY_SAMPLE_RATE, 44100)
            val channels = intOf(format, MediaFormat.KEY_CHANNEL_COUNT, 2).coerceIn(1, 8)
            val maxFrames = sourceRate * MAX_SECONDS
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val info = MediaCodec.BufferInfo()
            val chunks = ArrayList<ShortArray>()
            var frames = 0
            var inputDone = false
            var outputDone = false
            var guard = 0
            while (!outputDone && frames < maxFrames && guard++ < 200_000) {
                if (!inputDone) {
                    val index = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (index >= 0) {
                        val buffer = codec.getInputBuffer(index) ?: continue
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(
                                index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                if (outIndex >= 0) {
                    if (info.size > 4) {
                        val output = codec.getOutputBuffer(outIndex)
                        if (output != null) {
                            output.position(info.offset)
                            output.limit(info.offset + info.size)
                            val shorts = ShortArray(info.size / 2)
                            output.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
                            chunks += shorts
                            frames += shorts.size / channels
                        }
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
            }
            if (frames < sourceRate * 6) return null

            val mono = FloatArray(frames)
            var write = 0
            for (chunk in chunks) {
                var i = 0
                while (i + channels <= chunk.size && write < mono.size) {
                    var sum = 0f
                    for (c in 0 until channels) sum += chunk[i + c] / 32768f
                    mono[write++] = sum / channels
                    i += channels
                }
            }
            val samples = resample(mono, sourceRate, TARGET_RATE)
            val grid = BeatAnalyzer.analyze(
                samples = samples,
                sampleRate = TARGET_RATE,
                startSec = 0.0,
                durationSec = if (durationSec > 0) durationSec.toDouble() else 0.0,
            ) ?: return null
            if (grid.confidence < 0.05) return null
            store(trackId, grid)
            grid
        } catch (e: Exception) {
            Log.write("dj: analise falhou para $trackId: ${e.message}")
            null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun intOf(format: MediaFormat, key: String, fallback: Int): Int =
        if (format.containsKey(key)) runCatching { format.getInteger(key) }.getOrDefault(fallback)
        else fallback

    private fun resample(input: FloatArray, from: Int, to: Int): FloatArray {
        if (from == to || from <= 0) return input
        val size = (input.size.toLong() * to / from).toInt().coerceAtLeast(1)
        val output = FloatArray(size)
        for (i in output.indices) {
            val position = i.toDouble() * from / to
            val low = position.toInt().coerceIn(0, input.lastIndex)
            val high = (low + 1).coerceAtMost(input.lastIndex)
            val fraction = position - low
            output[i] = (input[low] * (1 - fraction) + input[high] * fraction).toFloat()
        }
        return output
    }

    private fun encode(grid: BeatGrid): String = listOf(
        grid.bpm,
        grid.confidence,
        grid.firstBeatSec,
        grid.firstDownbeatSec,
        grid.beatsPerBar,
        grid.durationSec,
        grid.rmsDb,
    ).joinToString("|")

    private fun decode(raw: String): BeatGrid? {
        val parts = raw.split("|")
        if (parts.size < 7) return null
        return BeatGrid(
            bpm = parts[0].toDouble(),
            confidence = parts[1].toDouble(),
            firstBeatSec = parts[2].toDouble(),
            firstDownbeatSec = parts[3].toDouble(),
            beatsPerBar = parts[4].toInt(),
            durationSec = parts[5].toDouble(),
            rmsDb = parts[6].toDouble(),
        )
    }

    private companion object {
        const val TARGET_RATE = 22050
        const val MAX_SECONDS = 60
        const val TIMEOUT_US = 4_000L
    }
}
