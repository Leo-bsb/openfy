package com.openfy.dev

import com.openfy.core.AppPaths
import com.openfy.core.BpmAnalyzer
import com.openfy.core.LocalMusic
import com.openfy.core.Log
import com.openfy.player.Mpv
import java.io.DataInputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

/**
 * Laboratorio de BPM: decodifica arquivos locais com o mpv, estima o pulso e
 * gera um relatorio HTML em build/bpm-report. Nada disso entra no app ainda;
 * serve para avaliar a qualidade da deteccao antes do modo DJ.
 *
 * Uso:
 *   gradlew bpmProbe
 *   gradlew bpmProbe -Pdir="D:\caminho\com\musicas"
 */
fun main(args: Array<String>) {
    val root = when {
        args.isNotEmpty() && args[0].isNotBlank() -> Path.of(args[0])
        System.getProperty("bpm.dir")?.isNotBlank() == true -> Path.of(System.getProperty("bpm.dir")!!)
        else -> AppPaths.home.resolve("downloads")
    }
    val mpv = Mpv.locate() ?: Path.of("D:\\scripts\\openfy\\.tools\\mpv\\mpv.exe")
    val files = findAudio(root).take(MAX_TRACKS)
    println("BPM probe: pasta=$root")
    println("BPM probe: mpv=$mpv arquivos=${files.size}")

    val work = Path.of("build", "bpm-work").toAbsolutePath()
    Files.createDirectories(work)

    val calibration = listOf(90.0, 120.0, 128.0, 174.0).map { expected ->
        val samples = clickTrack(expected, seconds = 12, sampleRate = 22050)
        val result = BpmAnalyzer.analyze(samples, 22050)
        println(
            "calibracao ${expected.toInt()} -> ${result?.label ?: "falhou"} " +
                "(erro ${result?.let { String.format("%.1f", it.bpm - expected) } ?: "-"})",
        )
        expected to result
    }

    val results = files.map { file ->
        val started = System.nanoTime()
        val decoded = decodeToSamples(mpv, file, work)
        val elapsed = (System.nanoTime() - started) / 1_000_000
        val analysis = decoded?.let { BpmAnalyzer.analyze(it, 22050) }
        println(
            String.format(
                "%-42s %-12s conf=%.2f (%dms)",
                file.fileName.toString().take(42),
                analysis?.label ?: "sem analise",
                analysis?.confidence ?: 0.0,
                elapsed,
            ),
        )
        AudioFile(file, analysis)
    }

    val report = Path.of("build", "bpm-report", "index.html").toAbsolutePath()
    Files.createDirectories(report.parent)
    Files.writeString(report, renderHtml(root, calibration, results), Charsets.UTF_8)
    Log.write("bpm: relatorio em $report")
    println("relatorio: $report")
}

private data class AudioFile(
    val path: Path,
    val analysis: BpmAnalyzer.Result?,
)

private fun findAudio(root: Path): List<Path> {
    if (!Files.isDirectory(root)) return emptyList()
    return runCatching {
        Files.walk(root).use { stream ->
            stream.filter { Files.isRegularFile(it) && LocalMusic.isAudio(it) }
                .sorted()
                .toList()
        }
    }.getOrDefault(emptyList())
}

/** mpv grava WAV mono 22050 Hz; o corte de 80 s basta para achar o pulso. */
private fun decodeToSamples(mpv: Path, source: Path, work: Path): FloatArray? {
    val wav = work.resolve("probe-${source.fileName.toString().hashCode()}.wav")
    runCatching { Files.deleteIfExists(wav) }
    val command = listOf(
        mpv.toString(),
        "--no-config",
        "--no-video",
        "--ao=pcm",
        "--ao-pcm-file=${wav.toAbsolutePath()}",
        "--audio-samplerate=22050",
        "--audio-channels=mono",
        "--start=10",
        "--end=90",
        "--really-quiet",
        source.toAbsolutePath().toString(),
    )
    val process = runCatching {
        ProcessBuilder(command).redirectErrorStream(true).start()
    }.getOrNull() ?: return null
    val finished = runCatching { process.waitFor() == 0 }.getOrDefault(false)
    if (!finished) {
        runCatching { process.destroyForcibly() }
        return null
    }
    if (!Files.isRegularFile(wav)) return null
    return readWav(wav)
}

private fun readWav(path: Path): FloatArray? {
    val bytes = Files.readAllBytes(path)
    if (bytes.size < 44) return null
    val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    if (String(bytes, 0, 4) != "RIFF" || String(bytes, 8, 4) != "WAVE") return null

    var offset = 12
    var format = 1
    var channels = 1
    var sampleRate = 22050
    var bits = 16
    var dataOffset = -1
    var dataSize = 0
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
                // WAVE_FORMAT_EXTENSIBLE (0xFFFE): o mpv grava float32 e o
                // subtipo real fica no GUID; tratar como IEEE float.
                if (format == 0xFFFE && bits == 32) format = 3
            }
            "data" -> {
                dataOffset = body
                dataSize = size.coerceAtMost(bytes.size - body)
            }
        }
        offset = body + size + (size and 1)
    }
    if (dataOffset < 0 || dataSize <= 0) return null
    val channelsSafe = channels.coerceAtLeast(1)
    val frames = dataSize / (bits / 8) / channelsSafe
    val samples = FloatArray(frames)
    var index = dataOffset
    repeat(frames) { frame ->
        var mixed = 0f
        repeat(channelsSafe) {
            val value = when {
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
            mixed += value
            index += bits / 8
        }
        samples[frame] = mixed / channelsSafe
    }
    return samples
}

private fun clickTrack(bpm: Double, seconds: Int, sampleRate: Int): FloatArray {
    val samples = FloatArray(sampleRate * seconds)
    val beat = 60.0 / bpm
    var time = 0.15
    while (time < seconds - 0.1) {
        val start = (time * sampleRate).toInt()
        val length = (0.035 * sampleRate).toInt()
        for (i in 0 until length) {
            val position = (start + i).coerceAtMost(samples.size - 1)
            val envelope = 1.0 - i.toDouble() / length
            samples[position] += (sin(2 * PI * 1200 * i / sampleRate) * envelope * 0.7).toFloat()
        }
        time += beat
    }
    return samples
}

private fun renderHtml(
    root: Path,
    calibration: List<Pair<Double, BpmAnalyzer.Result?>>,
    files: List<AudioFile>,
): String {
    val analyzed = files.mapNotNull { item ->
        item.analysis?.let { item.path.fileName.toString() to it.bpm }
    }
    val ordered = BpmAnalyzer.orderByBpm(analyzed)
    val valid = files.mapNotNull { it.analysis?.bpm }
    val average = if (valid.isEmpty()) 0.0 else valid.average()
    val calibrationRows = calibration.joinToString("\n") { (expected, result) ->
        val error = result?.let { it.bpm - expected }
        val status = when {
            result == null -> "falhou"
            error != null && kotlin.math.abs(error) < 1.0 -> "boa"
            error != null && kotlin.math.abs(error) < 4.0 -> "aceitavel"
            else -> "ruim"
        }
        """
        <tr><td>${expected.toInt()} BPM</td><td>${result?.label ?: "-"}</td>
        <td>${error?.let { String.format("%+.1f", it) } ?: "-"}</td><td class="$status">$status</td></tr>
        """.trimIndent()
    }
    val rows = files.sortedByDescending { it.analysis?.bpm ?: 0.0 }.joinToString("\n") { item ->
        val result = item.analysis
        val confidence = result?.confidence ?: 0.0
        val css = when {
            result == null -> "ruim"
            confidence >= 0.45 -> "boa"
            confidence >= 0.2 -> "aceitavel"
            else -> "ruim"
        }
        """
        <tr><td class="file">${escape(item.path.fileName.toString())}</td>
        <td class="bpm">${result?.bpm?.let { String.format("%.1f", it) } ?: "-"}</td>
        <td><div class="bar"><i style="width:${(confidence * 100).toInt()}%"></i></div></td>
        <td class="$css">${(confidence * 100).toInt()}%</td></tr>
        """.trimIndent()
    }
    val orderRows = ordered.joinToString("\n") { (name, bpm) ->
        "<li><span>${escape(name)}</span><b>${String.format("%.1f", bpm)} BPM</b></li>"
    }
    return """
<!doctype html>
<html lang="pt-BR"><head><meta charset="utf-8">
<title>Openfy · Laboratorio de BPM</title>
<style>
:root{color-scheme:dark}
body{margin:0;background:#060909;color:#eef6f3;font-family:Segoe UI,system-ui,sans-serif}
main{max-width:1040px;margin:0 auto;padding:48px 28px 80px}
h1{font-size:42px;margin:0 0 6px;background:linear-gradient(90deg,#2FBFA0,#19D3E6);-webkit-background-clip:text;background-clip:text;color:transparent}
p.sub{color:#8ca6a0;margin:0 0 34px}
.grid{display:grid;grid-template-columns:repeat(3,1fr);gap:14px;margin-bottom:34px}
.card{background:#0b1312;border:1px solid #1d2c28;border-radius:14px;padding:16px}
.card small{color:#8ca6a0;text-transform:uppercase;letter-spacing:.1em;font-size:10px}
.card b{display:block;font-size:28px;margin-top:6px}
section{background:#0b1312;border:1px solid #1d2c28;border-radius:16px;padding:20px 22px;margin-bottom:24px}
h2{font-size:16px;margin:0 0 14px;color:#cfe3dd}
table{width:100%;border-collapse:collapse;font-size:13px}
th{text-align:left;color:#8ca6a0;font-weight:500;padding:6px 8px;border-bottom:1px solid #1d2c28}
td{padding:9px 8px;border-bottom:1px solid #14201d}
td.bpm{font-weight:700;color:#2FBFA0;font-variant-numeric:tabular-nums}
td.file{max-width:480px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.boa{color:#5fe3bd}.aceitavel{color:#f5c451}.ruim{color:#ff8a80}
.bar{height:6px;background:#14201d;border-radius:4px;overflow:hidden;min-width:90px}
.bar i{display:block;height:100%;background:linear-gradient(90deg,#2FBFA0,#19D3E6)}
ol{padding-left:22px;margin:0}
li{display:flex;justify-content:space-between;gap:18px;padding:7px 0;border-bottom:1px solid #14201d;font-size:13px}
li span{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
li b{color:#2FBFA0;font-variant-numeric:tabular-nums}
.note{color:#8ca6a0;font-size:12px;line-height:1.6}
code{background:#14201d;padding:2px 6px;border-radius:5px}
</style></head><body><main>
<h1>Laboratorio de BPM</h1>
<p class="sub">Detecao offline por onsets + autocorrelacao · ${files.size} arquivos · pasta <code>${escape(root.toString())}</code></p>
<div class="grid">
<div class="card"><small>Faixas analisadas</small><b>${files.count { it.analysis != null }}</b></div>
<div class="card"><small>BPM medio</small><b>${String.format("%.1f", average)}</b></div>
<div class="card"><small>Acima de 45% de confianca</small><b>${files.count { (it.analysis?.confidence ?: 0.0) >= 0.45 }}</b></div>
</div>

<section><h2>Calibracao com pulso sintetico</h2>
<table><thead><tr><th>Esperado</th><th>Detectado</th><th>Erro</th><th>Resultado</th></tr></thead>
<tbody>$calibrationRows</tbody></table></section>

<section><h2>Arquivos</h2>
<table><thead><tr><th>Arquivo</th><th>BPM</th><th>Confianca</th><th></th></tr></thead>
<tbody>$rows</tbody></table></section>

<section><h2>Ordem sugerida (transicao mais suave)</h2>
<ol>$orderRows</ol></section>

<section><h2>Como isso seria usado</h2>
<p class="note">O modo DJ usaria o BPM local (nunca na nuvem) para ordenar a fila e escolher o crossfade.
O proximo passo e o time-stretch de 4 a 8% para casar o pulso, e so depois um crossfade com
entrada no compasso. Mashup com separacao de stems fica de fora desta fase.</p></section>
</main></body></html>
""".trimIndent()
}

private fun escape(value: String): String = value
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")

private const val MAX_TRACKS = 24
