package com.openfy.dev

import com.openfy.core.AppPaths
import com.openfy.core.BeatAnalyzer
import com.openfy.core.BeatGrid
import com.openfy.core.BpmAnalyzer
import com.openfy.core.LocalMusic
import com.openfy.core.Log
import com.openfy.core.PcmDecoder
import com.openfy.player.Mpv
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

/**
 * Laboratorio de BPM: decodifica arquivos locais com o mpv, estima pulso, fase
 * e downbeat e gera um relatorio HTML em build/bpm-report. Serve para avaliar a
 * mesma analise que o Modo DJ usa no player.
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
        val samples = clickTrack(expected, seconds = 24, sampleRate = 22050)
        val grid = BeatAnalyzer.analyze(samples, 22050)
        println(
            String.format(
                "calibracao %3d -> %-10s erro %+5.2f  fase %.3fs  downbeat %.3fs  conf %.2f",
                expected.toInt(),
                grid?.let { String.format("%.2f BPM", it.bpm) } ?: "falhou",
                grid?.let { it.bpm - expected } ?: 0.0,
                grid?.firstBeatSec ?: 0.0,
                grid?.firstDownbeatSec ?: 0.0,
                grid?.confidence ?: 0.0,
            ),
        )
        expected to grid
    }

    val results = files.map { file ->
        val started = System.nanoTime()
        val decoded = decodeToSamples(mpv, file, work)
        val elapsed = (System.nanoTime() - started) / 1_000_000
        val grid = decoded?.let { BeatAnalyzer.analyze(it, 22050, startSec = 10.0) }
        println(
            String.format(
                "%-42s %-10s conf=%.2f fase=%.3fs down=%.3fs (%dms)",
                file.fileName.toString().take(42),
                grid?.let { String.format("%.1f BPM", it.bpm) } ?: "sem analise",
                grid?.confidence ?: 0.0,
                grid?.firstBeatSec ?: 0.0,
                grid?.firstDownbeatSec ?: 0.0,
                elapsed,
            ),
        )
        AudioFile(file, grid)
    }

    val report = Path.of("build", "bpm-report", "index.html").toAbsolutePath()
    Files.createDirectories(report.parent)
    Files.writeString(report, renderHtml(root, calibration, results), Charsets.UTF_8)
    Log.write("bpm: relatorio em $report")
    println("relatorio: $report")
}

private data class AudioFile(
    val path: Path,
    val grid: BeatGrid?,
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
private fun decodeToSamples(mpv: Path, source: Path, work: Path): FloatArray? =
    PcmDecoder.decode(
        mpv = mpv,
        source = source.toAbsolutePath().toString(),
        work = work,
        startSec = 10,
        endSec = 90,
    )?.samples

private fun clickTrack(bpm: Double, seconds: Int, sampleRate: Int): FloatArray {
    val samples = FloatArray(sampleRate * seconds)
    val beat = 60.0 / bpm
    var time = 0.15
    var index = 0
    while (time < seconds - 0.1) {
        val start = (time * sampleRate).toInt()
        val length = (0.05 * sampleRate).toInt()
        // O "1" do compasso e grave e mais forte; os outros tempos sao agudos.
        val accent = index % 4 == 0
        val frequency = if (accent) 90.0 else 1200.0
        val amplitude = if (accent) 0.9 else 0.45
        for (i in 0 until length) {
            val position = (start + i).coerceAtMost(samples.size - 1)
            val envelope = 1.0 - i.toDouble() / length
            samples[position] +=
                (sin(2 * PI * frequency * i / sampleRate) * envelope * amplitude).toFloat()
        }
        time += beat
        index++
    }
    return samples
}

private fun renderHtml(
    root: Path,
    calibration: List<Pair<Double, BeatGrid?>>,
    files: List<AudioFile>,
): String {
    val analyzed = files.mapNotNull { item ->
        item.grid?.let { item.path.fileName.toString() to it.bpm }
    }
    val ordered = BpmAnalyzer.orderByBpm(analyzed)
    val valid = files.mapNotNull { it.grid?.bpm }
    val average = if (valid.isEmpty()) 0.0 else valid.average()
    val calibrationRows = calibration.joinToString("\n") { (expected, grid) ->
        val error = grid?.let { it.bpm - expected }
        val status = when {
            grid == null -> "falhou"
            error != null && kotlin.math.abs(error) < 1.0 -> "boa"
            error != null && kotlin.math.abs(error) < 4.0 -> "aceitavel"
            else -> "ruim"
        }
        """
        <tr><td>${expected.toInt()} BPM</td>
        <td>${grid?.let { String.format("%.2f BPM", it.bpm) } ?: "-"}</td>
        <td>${error?.let { String.format("%+.2f", it) } ?: "-"}</td>
        <td>${grid?.let { String.format("%.3fs", it.firstBeatSec) } ?: "-"}</td>
        <td>${grid?.let { String.format("%.3fs", it.firstDownbeatSec) } ?: "-"}</td>
        <td class="$status">$status</td></tr>
        """.trimIndent()
    }
    val rows = files.sortedByDescending { it.grid?.bpm ?: 0.0 }.joinToString("\n") { item ->
        val grid = item.grid
        val confidence = grid?.confidence ?: 0.0
        val css = when {
            grid == null -> "ruim"
            confidence >= 0.45 -> "boa"
            confidence >= 0.2 -> "aceitavel"
            else -> "ruim"
        }
        """
        <tr><td class="file">${escape(item.path.fileName.toString())}</td>
        <td class="bpm">${grid?.bpm?.let { String.format("%.1f", it) } ?: "-"}</td>
        <td>${grid?.let { String.format("%.3f", it.firstBeatSec) } ?: "-"}</td>
        <td>${grid?.let { String.format("%.3f", it.firstDownbeatSec) } ?: "-"}</td>
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
<p class="sub">Pulso, fase e downbeat calculados offline · ${files.size} arquivos · pasta <code>${escape(root.toString())}</code></p>
<div class="grid">
<div class="card"><small>Faixas analisadas</small><b>${files.count { it.grid != null }}</b></div>
<div class="card"><small>BPM medio</small><b>${String.format("%.1f", average)}</b></div>
<div class="card"><small>Acima de 45% de confianca</small><b>${files.count { (it.grid?.confidence ?: 0.0) >= 0.45 }}</b></div>
</div>

<section><h2>Calibracao com pulso sintetico</h2>
<table><thead><tr><th>Esperado</th><th>Detectado</th><th>Erro</th><th>Fase</th><th>Downbeat</th><th>Resultado</th></tr></thead>
<tbody>$calibrationRows</tbody></table></section>

<section><h2>Arquivos</h2>
<table><thead><tr><th>Arquivo</th><th>BPM</th><th>Fase (s)</th><th>Downbeat (s)</th><th>Confianca</th><th></th></tr></thead>
<tbody>$rows</tbody></table></section>

<section><h2>Ordem sugerida (transicao mais suave)</h2>
<ol>$orderRows</ol></section>

<section><h2>Como isso seria usado</h2>
<p class="note">O Modo DJ usa BPM, fase e downbeat locais (nunca na nuvem) para entrar no compasso,
casar o tempo em ate 8% e trocar os graves no meio do fade. A fase mostra onde cai a primeira
batida; o downbeat, onde comeca o compasso. Faixas com confianca baixa caem no N1 (smart fade).</p></section>
</main></body></html>
""".trimIndent()
}

private fun escape(value: String): String = value
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")

private const val MAX_TRACKS = 24
