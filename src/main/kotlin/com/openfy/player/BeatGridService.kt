package com.openfy.player

import com.openfy.core.AppPaths
import com.openfy.core.BeatAnalyzer
import com.openfy.core.BeatGrid
import com.openfy.core.Log
import com.openfy.core.PcmDecoder
import com.openfy.core.Track
import com.openfy.data.Db
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * Cache de grades ritmicas do Modo DJ.
 *
 * A analise e feita em segundo plano com o proprio mpv (decodifica um trecho
 * para WAV, sem tocar nada) e fica no SQLite: cada faixa e analisada uma vez
 * por aparelho. Enquanto nao existe grade, o mix cai no N1 (fade inteligente).
 */
class BeatGridService(
    private val db: Db,
    private val scope: CoroutineScope,
    private val workDir: Path = AppPaths.home.resolve("dj"),
) {

    private val memory = ConcurrentHashMap<String, BeatGrid>()
    private val unavailable = ConcurrentHashMap.newKeySet<String>()
    private val running = ConcurrentHashMap<String, Job>()

    /** Grade ja conhecida (memoria ou banco), sem disparar analise. */
    fun cached(trackId: String): BeatGrid? {
        memory[trackId]?.let { return it }
        if (unavailable.contains(trackId)) return null
        val stored = runCatching { db.beatGrid(trackId) }.getOrNull() ?: return null
        memory[trackId] = stored
        return stored
    }

    /** Garante a grade em segundo plano. [onReady] roda fora da thread da UI. */
    fun ensure(track: Track, sourceUrl: String, onReady: (() -> Unit)? = null) {
        if (memory.containsKey(track.id) || unavailable.contains(track.id)) return
        if (running.containsKey(track.id)) return
        val job = scope.launch(Dispatchers.IO) {
            val stored = runCatching { db.beatGrid(track.id) }.getOrNull()
            if (stored != null) {
                memory[track.id] = stored
                return@launch
            }
            val mpv = Mpv.locate()
            if (mpv == null) {
                unavailable += track.id
                return@launch
            }
            val audio = PcmDecoder.decode(mpv, sourceUrl, workDir)
            val grid = audio?.let {
                BeatAnalyzer.analyze(
                    samples = it.samples,
                    sampleRate = it.sampleRate,
                    startSec = 0.0,
                    durationSec = track.durationSec.toDouble(),
                )
            }
            if (grid == null || grid.confidence < 0.05) {
                unavailable += track.id
                Log.write("dj: sem grade para ${track.id} (${track.title.take(40)})")
                return@launch
            }
            memory[track.id] = grid
            runCatching { db.putBeatGrid(track.id, grid) }
            Log.write(
                "dj: grade ${track.id} ${String.format("%.1f", grid.bpm)} BPM " +
                    "conf=${String.format("%.2f", grid.confidence)} " +
                    "downbeat=${String.format("%.2f", grid.firstDownbeatSec)}s " +
                    "rms=${String.format("%.1f", grid.rmsDb)}dB"
            )
        }
        running[track.id] = job
        job.invokeOnCompletion {
            running.remove(track.id)
            if (memory.containsKey(track.id)) onReady?.invoke()
        }
    }

    fun clear() {
        memory.clear()
        unavailable.clear()
    }
}
