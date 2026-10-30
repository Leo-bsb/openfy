package com.openfy.android.data

import android.content.Context
import com.openfy.core.Track
import com.openfy.data.Media
import com.openfy.data.YouTubeEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

/**
 * Fila de downloads fora do ciclo de vida da tela. Enquanto houver download em
 * andamento o [DownloadService] mantém o processo em primeiro plano, então
 * abrir outro app (ou apagar a tarefa) não interrompe o áudio sendo baixado.
 *
 * O motor é dono do [DownloadStore]; o ViewModel só lê o mesmo índice e recebe
 * progresso/eventos por Flow.
 *
 * Baixa até [MAX_PARALLEL] faixas ao mesmo tempo. Cada faixa usa uma URL
 * extraída só para ela, então o limite do Google por link não é atingido; o
 * orçamento de espaço é reservado antes de abrir cada conexão para duas faixas
 * não estourarem o limite juntas.
 */
object DownloadEngine {

    data class Event(val track: Track, val result: DownloadResult)

    private const val DEFAULT_LIMIT_BYTES = 2_048L * 1024L * 1024L
    private const val MIN_FREE_BYTES = 1L
    private const val DISK_SAFETY_BYTES = 32L * 1024 * 1024

    /** Downloads simultâneos. 2–3 aproveita a banda sem brigar com o player. */
    private const val MAX_PARALLEL = 2

    /** Estimativa ~128 kbps para reservar orçamento antes de baixar. */
    private const val ESTIMATED_BYTES_PER_SECOND = 16_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val slots = Semaphore(MAX_PARALLEL)
    private val jobs = ConcurrentHashMap<String, Job>()

    /**
     * Trava por faixa: a fila automática e o lote da playlist podem pedir a
     * mesma faixa; sem isso as duas conexões baixariam o mesmo arquivo.
     */
    private val trackLocks = ConcurrentHashMap<String, Mutex>()

    private val budgetLock = Any()
    private var reservedBytes = 0L

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var limitBytes: Long = DEFAULT_LIMIT_BYTES

    private val _progress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val progress: StateFlow<Map<String, Float>> = _progress.asStateFlow()

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 64)
    val events: SharedFlow<Event> = _events.asSharedFlow()

    private val store: DownloadStore by lazy {
        DownloadStore(File(requireContext().filesDir, "downloads")).also { it.repair() }
    }

    /** URL extraída só para o download: nunca compartilha o link da reprodução. */
    private val youtube: YouTubeEngine by lazy { YouTubeEngine() }

    private val downloader: AudioDownloader by lazy {
        AudioDownloader(store) { limitBytes }
    }

    fun init(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    /** Índice compartilhado; inicializa o motor se ainda não foi feito. */
    fun storeFor(context: Context): DownloadStore {
        init(context)
        return store
    }

    fun setLimitBytes(bytes: Long) {
        limitBytes = bytes.coerceAtLeast(1L)
    }

    fun isQueued(trackId: String): Boolean = jobs[trackId]?.isActive == true

    fun activeCount(): Int = jobs.values.count { it.isActive }

    fun enqueue(track: Track) {
        if (isQueued(track.id)) return
        DownloadService.ensureStarted(requireContext())
        val job = scope.launch(start = CoroutineStart.LAZY) { download(track) }
        jobs[track.id] = job
        job.invokeOnCompletion {
            if (jobs[track.id] === job) jobs.remove(track.id)
        }
        job.start()
    }

    /** Baixa uma faixa e espera o resultado, respeitando o limite de paralelismo. */
    suspend fun download(track: Track): DownloadResult {
        DownloadService.ensureStarted(requireContext())
        val lock = trackLocks.computeIfAbsent(track.id) { Mutex() }
        _progress.update { it + (track.id to 0f) }
        val result = try {
            lock.withLock {
                slots.withPermit { downloadGuarded(track, coroutineContext[Job]) }
            }
        } finally {
            _progress.update { it - track.id }
        }
        _events.tryEmit(Event(track, result))
        return result
    }

    private suspend fun downloadGuarded(track: Track, job: Job?): DownloadResult {
        val existing = store.fileFor(track.id)
        if (existing != null) {
            return DownloadResult.Success(
                store.entry(track.id) ?: DownloadEntry(track, existing.name, existing.length()),
            )
        }
        if (!reserve(track)) {
            com.openfy.core.Log.write(
                "download: ${track.id} sem orçamento " +
                    "(usado=${store.usedBytes()}, reservado=$reservedBytes, limite=$limitBytes)",
            )
            return DownloadResult.Failed("Limite de armazenamento atingido. Apague downloads em Ajustes.")
        }
        return try {
            var outcome = attempt(track, job)
            if ((outcome as? DownloadResult.Failed)?.expired == true) {
                // Link expirado (401/403): descarta o cache e extrai de novo uma vez.
                withContext(Dispatchers.IO) { youtube.forgetStream(track.id) }
                outcome = attempt(track, job)
            }
            outcome
        } finally {
            release(track)
        }
    }

    private fun estimateBytes(track: Track): Long =
        track.durationSec.coerceAtLeast(60).toLong() * ESTIMATED_BYTES_PER_SECOND

    /** Reserva espaço estimado para a faixa; evita duas conexões estourarem o limite. */
    private fun reserve(track: Track): Boolean = synchronized(budgetLock) {
        val estimate = estimateBytes(track)
        if (store.usedBytes() + reservedBytes + estimate > limitBytes) return false
        reservedBytes += estimate
        true
    }

    private fun release(track: Track) {
        synchronized(budgetLock) {
            reservedBytes = (reservedBytes - estimateBytes(track)).coerceAtLeast(0L)
        }
    }

    private suspend fun attempt(track: Track, job: Job?): DownloadResult {
        com.openfy.core.Log.write("download: ${track.id} extraindo áudio")
        val media = withContext(Dispatchers.IO) { youtube.media(track.id) } as? Media.Remote
            ?: run {
                com.openfy.core.Log.write("download: ${track.id} sem stream remoto")
                return DownloadResult.Failed("Não foi possível obter o áudio desta faixa.")
            }
        val budget = minOf(
            store.availableBytes(limitBytes),
            (store.downloadsDir.usableSpace - DISK_SAFETY_BYTES).coerceAtLeast(0L),
        )
        if (budget <= MIN_FREE_BYTES) {
            com.openfy.core.Log.write(
                "download: ${track.id} sem espaço em disco (usado=${store.usedBytes()}, limite=$limitBytes)",
            )
            return DownloadResult.Failed("Limite de armazenamento atingido. Apague downloads em Ajustes.")
        }
        var lastUpdate = 0L
        return withContext(Dispatchers.IO) {
            downloader.download(
                track = track,
                url = media.url,
                headers = media.headers,
                onProgress = { fraction ->
                    val now = System.currentTimeMillis()
                    if (now - lastUpdate >= 150 || fraction < 0f) {
                        lastUpdate = now
                        _progress.update { it + (track.id to fraction) }
                    }
                },
                isCancelled = { job?.isActive != true },
            )
        }
    }

    fun cancel(trackId: String) {
        jobs[trackId]?.cancel()
        downloader.cancel(trackId)
    }

    /** Cancela tudo e espera os jobs pararem antes de apagar arquivos. */
    suspend fun cancelAllAndWait() {
        val pending = jobs.values.toList()
        pending.forEach { it.cancel() }
        downloader.cancel(null)
        pending.forEach { runCatching { it.join() } }
        jobs.clear()
        _progress.value = emptyMap()
    }

    private fun requireContext(): Context = appContext
        ?: error("DownloadEngine.init() não foi chamado")
}
