package com.openfy.player

import com.openfy.core.RadioPage
import com.openfy.core.Recommender
import com.openfy.core.RepeatMode
import com.openfy.core.SessionCodec
import com.openfy.core.Track
import com.openfy.data.Db
import com.openfy.data.StreamServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Fila + estado de reproducao. Dois mpv (A/B) permitem crossfade real: o proximo
 * e pré-carregado mudo no standby e, no fim, os volumes se cruzam por alguns
 * segundos. O "active" e quem manda no estado/UI; o outro fica de standby.
 */
class Player(
    private val mpvA: Mpv,
    private val mpvB: Mpv,
    private val stream: StreamServer,
    private val db: Db,
    private val scope: CoroutineScope,
) {

    val queue = MutableStateFlow<List<Track>>(emptyList())
    val index = MutableStateFlow(-1)
    val current = MutableStateFlow<Track?>(null)
    val playing = MutableStateFlow(false)
    val position = MutableStateFlow(0.0)
    val duration = MutableStateFlow(0.0)
    val volume = MutableStateFlow(80)
    val shuffle = MutableStateFlow(false)
    val repeat = MutableStateFlow(RepeatMode.OFF)
    val busy = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val likedIds = MutableStateFlow<Set<String>>(emptySet())
    val radioActive = MutableStateFlow(false)
    val radioLoading = MutableStateFlow(false)
    val queueTitle = MutableStateFlow("")
    val radioSeedTrack = MutableStateFlow<Track?>(null)
    val autoplayRadio = MutableStateFlow(true)
    val eqEnabled = MutableStateFlow(false)
    val eqGains = MutableStateFlow(List(EQ_FREQS.size) { 0 })
    val crossfadeSeconds = MutableStateFlow(0)
    val crossfading = MutableStateFlow(false)

    /** Fornecido pelo AppController: busca mais faixas da radio a partir do token. */
    var radioSource: (suspend (String) -> RadioPage?)? = null

    /** Inicia uma radio a partir de um videoId (usado no fim da fila). */
    var radioStarter: (suspend (String) -> RadioPage?)? = null
    private var radioContinuation: String? = null

    @Volatile private var active: Mpv = mpvA
    private fun standby(): Mpv = if (active === mpvA) mpvB else mpvA

    /** Restauracao de sessao: carrega pausado na posicao salva. */
    private var pendingPause = false
    private var lastPositionSave = 0L
    private var shuffleSnapshotQueue: List<Track>? = null
    private var shuffleSnapshotIndex = -1

    // evento de escuta ativo (telemetria/recap)
    private var activeEventSeq = -1L
    private var activeEventTrack: Track? = null
    private var queueOrigin = "desktop"
    private var queueContextId: String? = null
    private var queueContextTitle = ""
    private var radioStartIndex = -1
    private var radioEpoch = 0L
    private var radioJob: Job? = null
    private var pendingRadioAdvance = false
    private var radioEmptyPages = 0
    private var autoplaySuppressed = false
    private val recommender = Recommender(db)

    // crossfade
    private var crossfadeJob: Job? = null
    private var crossfadePrepared = false
    private var crossfadeReady = false
    private var crossfadeActive = false
    private var crossfadeOld: Mpv? = null
    private var crossfadePreparedIndex = -1
    private var crossfadePreparedTrack: Track? = null
    private var crossfadeDuration = 0.0

    init {
        scope.launch { observeMpv() }
        restoreEq()
        crossfadeSeconds.value = db.get("playback.crossfade")?.toIntOrNull()?.coerceIn(0, 12) ?: 0
        autoplayRadio.value = runCatching {
            db.get("playback.autoplayRadio")?.toBooleanStrict()
        }.getOrNull() ?: true
    }

    fun start() {
        active.start()
    }

    private suspend fun observeMpv() {
        scope.launch { mpvA.events.collect { event -> handleEvent(mpvA, event) } }
        scope.launch { mpvB.events.collect { event -> handleEvent(mpvB, event) } }
    }

    private fun handleEvent(source: Mpv, event: Mpv.Event) {
        if (source !== active) {
            // Eventos do standby so importam para preparar o crossfade.
            if (crossfadePrepared) {
                when (event) {
                    is Mpv.Event.Started -> {
                        source.pause(true)
                        crossfadeReady = true
                        if (event.duration > 0) crossfadeDuration = event.duration
                    }
                    else -> Unit
                }
            }
            return
        }

        when (event) {
            is Mpv.Event.Time -> {
                position.value = event.position
                if (event.duration > 0) duration.value = event.duration
                if (event.position > 0) busy.value = false
                checkCrossfade(event.position)
                val now = System.currentTimeMillis()
                if (now - lastPositionSave > 5000) {
                    lastPositionSave = now
                    save()
                }
            }
            is Mpv.Event.Started -> {
                if (event.duration > 0) {
                    duration.value = event.duration
                    persistDuration(event.duration)
                }
                if (pendingPause) {
                    pendingPause = false
                    source.pause(true)
                    playing.value = false
                } else {
                    playing.value = true
                }
                busy.value = false
            }
            is Mpv.Event.Paused -> {
                playing.value = !event.paused
                if (event.paused) cancelCrossfade()
            }
            is Mpv.Event.Volume -> {
                if (!crossfadeActive) volume.value = event.value
            }
            is Mpv.Event.Ended -> {
                busy.value = false
                finishActive(completed = true, skipped = false)
                onEnded()
            }
            is Mpv.Event.Failed -> {
                cancelCrossfade()
                finishActive(completed = false, skipped = true)
                busy.value = false
                playing.value = false
                val title = current.value?.title ?: "faixa"
                error.value = "Não foi possível tocar \"$title\"."
                com.openfy.core.Log.write("player: falha ao tocar ${current.value?.id}: ${event.reason}")
            }
            Mpv.Event.Closed -> {
                cancelCrossfade()
                finishActive(completed = false, skipped = true)
                playing.value = false
                busy.value = false
            }
        }
    }

    private fun onEnded() {
        val mode = repeat.value
        if (mode == RepeatMode.ONE) {
            current.value?.let { load(it, resetPlay = false) }
            return
        }
        next(auto = true)
    }

    private fun startActiveEvent(track: Track) {
        val isRadio = radioStartIndex >= 0 && index.value >= radioStartIndex
        val origin = if (isRadio) "radio" else queueOrigin
        val title = if (isRadio) {
            val seed = radioSeedTrack.value?.title
            if (seed.isNullOrBlank()) "Rádio" else "Rádio: $seed"
        } else {
            queueContextTitle
        }
        activeEventTrack = track
        activeEventSeq = runCatching {
            db.beginPlay(track, origin, queueContextId, title)
        }.getOrDefault(-1L)
    }

    private fun finishActive(
        completed: Boolean,
        skipped: Boolean,
        playedOverrideMs: Long? = null,
    ) {
        val seq = activeEventSeq
        val track = activeEventTrack
        if (seq < 0 || track == null) return
        val totalMs = maxOf(track.durationSec * 1000, (duration.value * 1000).toInt())
        val played = playedOverrideMs
            ?: if (completed) totalMs.toLong() else (position.value * 1000).toLong()
        activeEventSeq = -1
        activeEventTrack = null
        val safePlayed = played.coerceIn(0L, totalMs.toLong()).toInt()
        runCatching { db.finishPlay(seq, safePlayed, completed, skipped, totalMs) }
    }

    /** Define o contexto da fila para a UI e para os eventos de escuta. */
    fun setQueueContext(title: String, origin: String = "desktop", contextId: String? = null) {
        queueContextTitle = title
        queueOrigin = origin
        queueContextId = contextId
        queueTitle.value = title
    }

    fun setAutoplayRadio(enabled: Boolean) {
        autoplayRadio.value = enabled
        if (enabled) autoplaySuppressed = false
        scope.launch(Dispatchers.IO) { db.put("playback.autoplayRadio", enabled.toString()) }
    }

    fun next(auto: Boolean = false) {
        cancelCrossfade()
        val q = queue.value
        if (q.isEmpty()) return
        finishActive(
            completed = duration.value > 0 && position.value >= duration.value * 0.9,
            skipped = duration.value <= 0 || position.value < duration.value * 0.9,
        )
        val nextIndex = index.value + 1
        if (nextIndex > q.lastIndex) {
            if (radioActive.value && repeat.value == RepeatMode.OFF) {
                requestRadioAdvance()
                return
            }
            if (repeat.value == RepeatMode.ALL) {
                index.value = 0
                load(q[0])
                save()
                return
            }
            if (autoplayRadio.value && !autoplaySuppressed) startRadioFromCurrent()
            return
        }
        index.value = nextIndex
        load(q[nextIndex])
        save()
    }

    fun previous() {
        cancelCrossfade()
        val q = queue.value
        if (q.isEmpty()) return
        if (position.value > 4.0) {
            active.seekSeconds(0.0)
            return
        }
        if (index.value <= 0) {
            active.seekSeconds(0.0)
            return
        }
        finishActive(completed = false, skipped = true)
        val prev = index.value - 1
        index.value = prev
        load(q[prev])
        save()
    }

    fun togglePlay() {
        val track = current.value ?: return
        if (!active.ready.value) {
            load(track)
            return
        }
        val nowPlaying = playing.value
        if (nowPlaying) cancelCrossfade()
        if (!nowPlaying && activeEventSeq < 0) startActiveEvent(track)
        active.pause(nowPlaying)
        playing.value = !nowPlaying
    }

    fun playNow(tracks: List<Track>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        cancelCrossfade()
        stopRadio()
        autoplaySuppressed = false
        queue.value = tracks
        index.value = startIndex.coerceIn(0, tracks.lastIndex)
        // Uma nova fila invalida snapshots antigos; o desligar do shuffle
        // restaura esta mesma ordem em vez da fila de outro contexto.
        if (shuffle.value) {
            shuffleSnapshotQueue = tracks
            shuffleSnapshotIndex = index.value
        }
        load(queue.value[index.value])
        save()
    }

    /** Inicia uma radio ja com a primeira pagina de faixas e o token de continuacao. */
    fun startRadio(tracks: List<Track>, continuation: String?) {
        if (tracks.isEmpty()) return
        stopRadio()
        autoplaySuppressed = false
        cancelCrossfade()
        radioActive.value = true
        radioLoading.value = false
        radioContinuation = continuation
        radioStartIndex = 0
        radioSeedTrack.value = tracks.firstOrNull()
        if (queueContextTitle.isBlank()) setQueueContext("Rádio", "radio", tracks.firstOrNull()?.id)
        queue.value = tracks
        index.value = 0
        if (shuffle.value) {
            shuffleSnapshotQueue = tracks
            shuffleSnapshotIndex = 0
        }
        com.openfy.core.Log.write("player: radio iniciada com ${tracks.size} faixas")
        load(tracks[0])
    }

    fun playSingle(track: Track) = playNow(listOf(track), 0)

    /** Toca uma lista embaralhada e guarda a ordem original para o modo shuffle. */
    fun playShuffled(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        shuffle.value = true
        playNow(tracks.shuffled(), 0)
        shuffleSnapshotQueue = tracks
        shuffleSnapshotIndex = 0
        save()
    }

    fun addNext(track: Track) {
        cancelPrep()
        val q = queue.value.toMutableList()
        val at = (index.value + 1).coerceIn(0, q.size)
        q.add(at, track)
        if (radioStartIndex >= at) radioStartIndex++
        queue.value = q
        if (index.value < 0) {
            index.value = 0
            load(q[0])
        }
        save()
    }

    fun addToQueue(track: Track) {
        cancelPrep()
        queue.value = queue.value + track
        if (index.value < 0) {
            index.value = 0
            load(queue.value[0])
        }
        save()
    }

    fun toggleShuffle() {
        val enable = !shuffle.value
        if (enable) {
            shuffleSnapshotQueue = queue.value
            shuffleSnapshotIndex = index.value
            shuffle.value = true
            val q = queue.value
            val cur = q.getOrNull(index.value)
            if (cur != null) {
                val rest = q.filterIndexed { i, _ -> i != index.value }.shuffled()
                queue.value = listOf(cur) + rest
                index.value = 0
            }
        } else {
            shuffle.value = false
            restoreShuffleSnapshot()
        }
        cancelCrossfade()
        if (enable) radioStartIndex = -1
        save()
    }

    private fun restoreShuffleSnapshot() {
        val snapshot = shuffleSnapshotQueue ?: return
        val currentTrack = current.value
        val extras = queue.value.filter { track -> snapshot.none { it.id == track.id } }
        val restored = snapshot + extras
        if (restored.isNotEmpty()) {
            val at = currentTrack?.let { track ->
                restored.indexOfFirst { it.id == track.id }
            } ?: -1
            queue.value = restored
            index.value = if (at >= 0) at else index.value.coerceIn(0, restored.lastIndex)
        }
        shuffleSnapshotQueue = null
        shuffleSnapshotIndex = -1
    }

    fun cycleRepeat() {
        repeat.value = when (repeat.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        cancelPrep()
        save()
    }

    fun seekToFraction(fraction: Double) {
        cancelCrossfade()
        val total = duration.value
        if (total <= 0) return
        val target = (fraction.coerceIn(0.0, 1.0)) * total
        position.value = target
        active.seekSeconds(target)
    }

    /** Avanca/recua N segundos; usado pelos atalhos de teclado. */
    fun seekBy(deltaSeconds: Double) {
        cancelCrossfade()
        val total = duration.value
        if (total <= 0) return
        val target = (position.value + deltaSeconds).coerceIn(0.0, total)
        position.value = target
        active.seekSeconds(target)
    }

    fun setVolume(value: Int) {
        volume.value = value.coerceIn(0, 130)
        cancelCrossfade()
        active.volume(volume.value)
        save()
    }

    fun stopRadio(clearTail: Boolean = false) {
        if (!radioActive.value && radioContinuation == null && radioJob == null && radioStartIndex < 0) return
        radioEpoch++
        radioJob?.cancel()
        radioJob = null
        pendingRadioAdvance = false
        radioActive.value = false
        radioLoading.value = false
        radioContinuation = null
        radioSeedTrack.value = null
        radioEmptyPages = 0
        if (clearTail && radioStartIndex > 0) {
            val start = radioStartIndex
            queue.value = queue.value.take(start)
            if (index.value >= start) index.value = start - 1
            save()
        }
        radioStartIndex = -1
        com.openfy.core.Log.write("player: radio encerrada")
    }

    /** Toca a faixa agora e emenda a radio dela em seguida (clique na busca). */
    fun playThenRadio(track: Track) {
        setQueueContext("Rádio: ${track.title}", "radio", track.id)
        playNow(listOf(track), 0)
        radioSeedTrack.value = track
        radioEpoch++
        val epoch = radioEpoch
        radioLoading.value = true
        radioJob?.cancel()
        radioJob = scope.launch {
            val page = runCatching { radioStarter?.invoke(track.id) }.getOrNull()
            if (epoch != radioEpoch) return@launch
            radioLoading.value = false
            if (page == null || page.tracks.isEmpty() || current.value?.id != track.id) {
                radioActive.value = false
                return@launch
            }
            val start = queue.value.size
            val extra = recommender.rankRadio(page.tracks, track, queue.value, limit = RADIO_INITIAL)
            radioContinuation = page.continuation
            radioActive.value = extra.isNotEmpty() || page.continuation != null
            if (extra.isEmpty()) return@launch
            queue.value = queue.value + extra
            if (radioStartIndex < 0) radioStartIndex = start
            com.openfy.core.Log.write("player: radio emendada +${extra.size} (fila=${queue.value.size})")
            maybeLoadMoreRadio()
            save()
        }
    }

    /** No fim de uma fila comum, continua com a radio da ultima faixa. */
    private fun startRadioFromCurrent() {
        if (!autoplayRadio.value || autoplaySuppressed) return
        val seed = current.value ?: return
        setQueueContext("Rádio: ${seed.title}", "radio", seed.id)
        radioEpoch++
        val epoch = radioEpoch
        radioLoading.value = true
        radioJob?.cancel()
        radioJob = scope.launch {
            val page = runCatching { radioStarter?.invoke(seed.id) }.getOrNull()
            if (epoch != radioEpoch) return@launch
            radioLoading.value = false
            if (page == null || page.tracks.isEmpty() || current.value?.id != seed.id) {
                radioActive.value = false
                playing.value = false
                active.stop()
                return@launch
            }
            val expectedSize = queue.value.size
            val takeOver = expectedSize <= index.value + 1
            val extra = recommender.rankRadio(page.tracks, seed, queue.value, limit = RADIO_INITIAL)
            radioSeedTrack.value = seed
            radioContinuation = page.continuation
            radioActive.value = extra.isNotEmpty() || page.continuation != null
            if (extra.isEmpty()) {
                requestRadioAdvance()
                return@launch
            }
            queue.value = queue.value + extra
            if (radioStartIndex < 0) radioStartIndex = expectedSize
            trimQueue()
            com.openfy.core.Log.write("player: fim da fila -> radio +${extra.size}")
            if (takeOver) {
                index.value = expectedSize
                load(queue.value[index.value])
            }
        }
    }

    private fun load(track: Track, resetPlay: Boolean = true) {
        com.openfy.core.Log.write(
            "player: tocando ${track.id} \"${track.title}\" (mpvReady=${active.ready.value})"
        )
        cancelCrossfade()
        finishActive(completed = false, skipped = true)
        current.value = track
        pendingPause = false
        error.value = null
        busy.value = true
        if (resetPlay) {
            position.value = 0.0
            duration.value = track.durationSec.toDouble()
        }
        playing.value = true
        active.start()
        active.volume(volume.value)
        active.load(stream.urlFor(track.id))
        startActiveEvent(track)
        maybeLoadMoreRadio()
        save()
    }

    private fun appendRadio(
        page: RadioPage,
        seed: Track,
        epoch: Long,
        token: String,
        autoAdvance: Boolean,
    ) {
        if (epoch != radioEpoch) return
        radioLoading.value = false
        radioContinuation = if (page.continuation == token) null else page.continuation
        val extra = recommender.rankRadio(page.tracks, seed, queue.value, limit = RADIO_PAGE)
        val shouldAdvance = autoAdvance || pendingRadioAdvance
        if (extra.isEmpty()) {
            radioEmptyPages++
            radioActive.value = radioContinuation != null
            if (shouldAdvance && radioContinuation != null && radioEmptyPages <= 3) {
                requestRadioAdvance()
                return
            }
            if (shouldAdvance) {
                pendingRadioAdvance = false
                playing.value = false
                active.stop()
            }
            return
        }
        radioEmptyPages = 0
        pendingRadioAdvance = false
        val expectedSize = queue.value.size
        val takeOver = expectedSize <= index.value + 1
        val start = expectedSize
        queue.value = queue.value + extra
        if (radioStartIndex < 0) radioStartIndex = start
        radioActive.value = true
        trimQueue()
        if (shouldAdvance && takeOver) {
            index.value = start
            load(queue.value[index.value])
        }
        com.openfy.core.Log.write("player: radio +${extra.size} faixas (fila=${queue.value.size})")
        save()
    }

    /**
     * Mantem a radio abastecida: quando restam poucas faixas, pede a proxima
     * pagina ao controlador sem interromper a reproducao atual.
     */
    private fun maybeLoadMoreRadio() {
        if (!radioActive.value || radioLoading.value || radioJob?.isActive == true) return
        val token = radioContinuation ?: return
        if (queue.value.size - index.value > RADIO_PREFETCH) return
        val seed = radioSeedTrack.value ?: current.value ?: return
        val epoch = radioEpoch
        radioLoading.value = true
        radioJob = scope.launch {
            val page = runCatching { radioSource?.invoke(token) }.getOrNull()
            if (epoch != radioEpoch) return@launch
            if (page == null) {
                radioLoading.value = false
                return@launch
            }
            appendRadio(page, seed, epoch, token, autoAdvance = false)
        }
    }

    /** Evita que a fila de radio cresca sem limite numa sessao longa. */
    private fun trimQueue() {
        val q = queue.value
        val cur = index.value
        if (q.size <= RADIO_MAX_QUEUE || cur < 150) return
        cancelPrep()
        val drop = (cur - 100).coerceAtMost(radioStartIndex.coerceAtLeast(0))
        if (drop <= 0) return
        queue.value = q.drop(drop)
        index.value = cur - drop
        if (radioStartIndex >= 0) radioStartIndex = (radioStartIndex - drop).coerceAtLeast(0)
        com.openfy.core.Log.write("player: fila aparada em $drop faixas (agora ${queue.value.size})")
    }

    /** Fim da fila da radio: busca a proxima pagina e continua em vez de parar. */
    private fun requestRadioAdvance() {
        cancelCrossfade()
        if (radioLoading.value) {
            pendingRadioAdvance = true
            return
        }
        val token = radioContinuation
        if (token == null) {
            radioActive.value = false
            playing.value = false
            active.stop()
            return
        }
        val seed = radioSeedTrack.value ?: current.value ?: return
        val epoch = radioEpoch
        radioLoading.value = true
        radioJob = scope.launch {
            val page = runCatching { radioSource?.invoke(token) }.getOrNull()
            if (epoch != radioEpoch) return@launch
            if (page == null) {
                radioLoading.value = false
                pendingRadioAdvance = false
                playing.value = false
                active.stop()
                return@launch
            }
            appendRadio(page, seed, epoch, token, autoAdvance = true)
        }
    }

    fun clearError() {
        error.value = null
    }

    /**
     * A busca do YouTube Music nao devolve duracao; o mpv devolve ao carregar.
     * Guardamos para playlists e fila mostrarem o tempo certo nas proximas vezes.
     */
    private fun persistDuration(seconds: Double) {
        val track = current.value ?: return
        val rounded = seconds.toInt()
        if (rounded <= 1 || track.durationSec == rounded) return
        val updated = track.copy(durationSec = rounded)
        current.value = updated
        queue.value = queue.value.map { if (it.id == updated.id) updated else it }
        scope.launch(Dispatchers.IO) { db.remember(updated) }
    }

    suspend fun refreshLikes() {
        val ids = withContext(Dispatchers.IO) { db.likedIds().toSet() }
        likedIds.value = ids
    }

    suspend fun toggleLike(track: Track): Boolean {
        val nowLiked = withContext(Dispatchers.IO) {
            db.remember(track)
            db.toggleLike(track.id)
        }
        refreshLikes()
        return nowLiked
    }

    // ------------------------------------------------------------------ session
    /** Retoma a ultima sessao: fila, faixa, posicao (pausado), volume e modos. */
    fun restore() {
        val session = runCatching { SessionCodec.load(db) }.getOrNull() ?: return
        shuffle.value = session.shuffle
        shuffleSnapshotQueue = session.shuffleQueue.takeIf { it.isNotEmpty() }
        shuffleSnapshotIndex = session.shuffleIndex
        queueContextTitle = session.queueTitle
        queueOrigin = session.queueOrigin
        queueContextId = session.queueContextId
        queueTitle.value = queueContextTitle
        repeat.value = runCatching { RepeatMode.valueOf(session.repeat) }.getOrDefault(RepeatMode.OFF)
        volume.value = session.volume.coerceIn(0, 130)
        radioActive.value = false
        radioContinuation = null
        val q = session.queue
        queue.value = q
        val idx = if (q.isEmpty()) -1 else session.index.coerceIn(0, q.lastIndex)
        index.value = idx
        val track = q.getOrNull(idx)
        if (track == null) return
        current.value = track
        position.value = session.position
        duration.value = track.durationSec.toDouble()
        playing.value = false
        pendingPause = true
        active.start()
        active.volume(volume.value)
        active.load(stream.urlFor(track.id), startAt = session.position)
        com.openfy.core.Log.write("player: sessao restaurada (fila=${q.size}, faixa=${track.id})")
    }

    private var saveJob: Job? = null

    /** Salva em segundo plano e junta mudancas proximas (volume, faixa, posicao). */
    private fun save() {
        saveJob?.cancel()
        saveJob = scope.launch(Dispatchers.IO) {
            delay(250)
            persistSession()
        }
    }

    private fun persistSession() {
        runCatching {
            SessionCodec.save(
                db,
                com.openfy.core.SessionState(
                    queue = queue.value,
                    index = index.value,
                    position = position.value,
                    volume = volume.value,
                    shuffle = shuffle.value,
                    repeat = repeat.value.name,
                    shuffleQueue = shuffleSnapshotQueue.orEmpty(),
                    shuffleIndex = shuffleSnapshotIndex,
                    queueTitle = queueContextTitle,
                    queueOrigin = queueOrigin,
                    queueContextId = queueContextId,
                ),
            )
        }
    }

    private fun saveNow() {
        runCatching { persistSession() }
    }

    // ------------------------------------------------------------------ queue editing
    fun removeAt(at: Int) {
        cancelCrossfade()
        val q = queue.value
        if (at !in q.indices) return
        val new = q.toMutableList().apply { removeAt(at) }
        val cur = index.value
        if (at <= radioStartIndex) radioStartIndex = -1
        queue.value = new
        when {
            at < cur -> index.value = cur - 1
            at == cur -> {
                if (new.isEmpty()) {
                    index.value = -1
                    current.value = null
                    playing.value = false
                    active.stop()
                } else {
                    val next = at.coerceIn(0, new.lastIndex)
                    index.value = next
                    load(new[next])
                }
            }
        }
        save()
    }

    fun clearNext() {
        cancelPrep()
        val q = queue.value
        val cur = index.value
        if (cur < 0 || cur >= q.lastIndex) return
        queue.value = q.subList(0, cur + 1)
        radioStartIndex = -1
        radioActive.value = false
        radioContinuation = null
        save()
    }

    fun move(from: Int, to: Int) {
        cancelCrossfade()
        radioStartIndex = -1
        val q = queue.value.toMutableList()
        if (from !in q.indices || to !in q.indices || from == to) return
        val item = q.removeAt(from)
        q.add(to, item)
        val cur = index.value
        queue.value = q
        index.value = when {
            from == cur -> to
            from < cur && to >= cur -> cur - 1
            from > cur && to <= cur -> cur + 1
            else -> cur
        }
        save()
    }

    fun playAt(index: Int) {
        cancelCrossfade()
        val q = queue.value
        if (index !in q.indices) return
        this.index.value = index
        load(q[index])
    }

    fun restoreAt(at: Int, track: Track) {
        cancelPrep()
        val q = queue.value.toMutableList()
        val pos = at.coerceIn(0, q.size)
        q.add(pos, track)
        val cur = index.value
        queue.value = q
        index.value = if (cur >= pos) cur + 1 else cur
        save()
    }

    fun appendAll(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        cancelPrep()
        queue.value = queue.value + tracks
        if (index.value < 0 && queue.value.isNotEmpty()) {
            index.value = 0
            load(queue.value[0])
        }
        save()
    }

    // ------------------------------------------------------------------ equalizer
    fun setEqEnabled(enabled: Boolean) {
        eqEnabled.value = enabled
        applyEq()
        saveEq()
    }

    fun setEqGain(index: Int, gainDb: Int) {
        val current = eqGains.value.toMutableList()
        if (index !in current.indices) return
        current[index] = gainDb.coerceIn(-12, 12)
        eqGains.value = current
        eqEnabled.value = true
        applyEq()
        saveEq()
    }

    fun resetEq() {
        eqGains.value = List(EQ_FREQS.size) { 0 }
        applyEq()
        saveEq()
    }

    fun applyPreset(name: String) {
        val preset = EQ_PRESETS[name] ?: return
        eqEnabled.value = true
        eqGains.value = preset
        applyEq()
        saveEq()
    }

    private fun applyEq() {
        applyEqTo(mpvA)
        applyEqTo(mpvB)
    }

    private fun applyEqTo(target: Mpv) {
        val filters = mutableListOf<String>()
        if (eqEnabled.value) {
            filters += EQ_FREQS.zip(eqGains.value).map { (freq, gain) ->
                "equalizer=f=$freq:t=q:w=1:g=$gain"
            }
        }
        target.setAudioFilters(filters)
    }

    private fun saveEq() {
        scope.launch(Dispatchers.IO) {
            db.put("eq.enabled", eqEnabled.value.toString())
            db.put("eq.gains", eqGains.value.joinToString(","))
        }
    }

    private fun restoreEq() {
        eqEnabled.value = runCatching { db.get("eq.enabled")?.toBooleanStrict() }.getOrNull() ?: false
        val gains = db.get("eq.gains")?.split(',')?.mapNotNull { it.toIntOrNull() }
        if (gains != null && gains.size == EQ_FREQS.size) eqGains.value = gains
        if (eqEnabled.value) applyEq()
    }

    // ------------------------------------------------------------------ crossfade (2 players)
    fun setCrossfade(seconds: Int) {
        val value = seconds.coerceIn(0, 12)
        crossfadeSeconds.value = value
        if (value == 0) cancelCrossfade()
        scope.launch(Dispatchers.IO) { db.put("playback.crossfade", value.toString()) }
    }


    private fun checkCrossfade(position: Double) {
        val fade = crossfadeSeconds.value
        if (fade <= 0 || crossfadeActive) return
        if (!playing.value) return
        val total = duration.value
        if (total <= 0) return
        val remaining = total - position
        // Fixa a proxima escolhida no preparo. Sem isso, shuffle poderia
        // trocar o candidato a cada evento de tempo e reiniciar o crossfade.
        val prepared = crossfadePreparedTrack
        val next = if (prepared != null) {
            val preparedIndex = queue.value.indexOfFirst { it.id == prepared.id }
            if (preparedIndex >= 0) preparedIndex to prepared else null
        } else {
            crossfadeNext()
        } ?: run {
            cancelPrep()
            return
        }
        val (nextIndex, nextTrack) = next
        val same = crossfadePrepared &&
            crossfadePreparedIndex == nextIndex &&
            crossfadePreparedTrack?.id == nextTrack.id
        if (!same) {
            if (remaining <= fade + 3.0) prepareCrossfade(nextIndex, nextTrack)
            return
        }
        if (remaining <= fade && crossfadeReady) {
            startCrossfade(nextIndex, nextTrack, remaining)
        }
    }

    private fun crossfadeNext(): Pair<Int, Track>? {
        val q = queue.value
        if (q.size < 2) return null
        if (repeat.value == RepeatMode.ONE) return null
        val cur = index.value
        if (cur < 0) return null
        val next = cur + 1
        if (next <= q.lastIndex) return next to q[next]
        if (repeat.value == RepeatMode.ALL) return 0 to q[0]
        return null
    }

    private fun prepareCrossfade(nextIndex: Int, nextTrack: Track) {
        if (crossfadePrepared && crossfadePreparedIndex == nextIndex &&
            crossfadePreparedTrack?.id == nextTrack.id
        ) {
            return
        }
        cancelPrep()
        crossfadePreparedIndex = nextIndex
        crossfadePreparedTrack = nextTrack
        crossfadePrepared = true
        crossfadeReady = false
        crossfadeDuration = nextTrack.durationSec.toDouble()
        val s = standby()
        s.start()
        s.volume(0)
        applyEqTo(s)
        s.load(stream.urlFor(nextTrack.id))
        com.openfy.core.Log.write("player: crossfade preparando ${nextTrack.id}")
    }

    private fun startCrossfade(nextIndex: Int, nextTrack: Track, remaining: Double) {
        val fade = minOf(crossfadeSeconds.value.toDouble(), remaining).coerceAtLeast(0.5)
        val old = active
        val new = standby()
        crossfadeOld = old
        active = new
        crossfadeActive = true
        crossfading.value = true
        crossfadePrepared = false
        crossfadeReady = false
        crossfadePreparedIndex = -1
        crossfadePreparedTrack = null
        val oldDuration = duration.value
        val oldPlayedMs = ((oldDuration - remaining).coerceAtLeast(0.0) * 1000).toLong()
        finishActive(
            completed = oldDuration > 0 && oldPlayedMs >= oldDuration * 900,
            skipped = false,
            playedOverrideMs = oldPlayedMs,
        )
        index.value = nextIndex
        current.value = nextTrack
        position.value = 0.0
        duration.value = if (crossfadeDuration > 0) crossfadeDuration else nextTrack.durationSec.toDouble()
        playing.value = true
        busy.value = false
        pendingPause = false
        if (crossfadeDuration > 1) persistDuration(crossfadeDuration)
        new.pause(false)
        val target = volume.value
        startActiveEvent(nextTrack)
        save()
        maybeLoadMoreRadio()
        crossfadeJob?.cancel()
        crossfadeJob = scope.launch {
            val steps = (fade * 10).toInt().coerceAtLeast(5)
            for (i in 0..steps) {
                if (!crossfadeActive) return@launch
                val fraction = i.toDouble() / steps
                // Curva equal-power: no meio as duas faixas soam de verdade,
                // em vez de um "buraco" de volume do fade linear.
                val angle = fraction * Math.PI / 2.0
                old.volume((target * cos(angle)).roundToInt())
                new.volume((target * sin(angle)).roundToInt())
                delay(100)
            }
            if (!crossfadeActive) return@launch
            old.stop()
            new.volume(target)
            crossfadeOld = null
            crossfadeActive = false
            crossfading.value = false
            com.openfy.core.Log.write("player: crossfade concluido")
        }
        com.openfy.core.Log.write(
            "player: crossfade ${nextTrack.id} (${fade.roundToInt()}s, " +
                "equal-power, ${if (shuffle.value) "shuffle" else "sequencial"})"
        )
    }

    private fun cancelCrossfade() {
        if (crossfadeActive) {
            crossfadeJob?.cancel()
            crossfadeJob = null
            crossfadeActive = false
            crossfading.value = false
            crossfadeOld?.stop()
            crossfadeOld = null
            active.volume(volume.value)
        }
        cancelPrep()
    }

    private fun cancelPrep() {
        if (!crossfadePrepared) return
        crossfadePrepared = false
        crossfadeReady = false
        crossfadePreparedIndex = -1
        crossfadePreparedTrack = null
        standby().stop()
    }

    fun shutdown() {
        finishActive(completed = false, skipped = true)
        crossfadeJob?.cancel()
        saveNow()
        mpvA.shutdown()
        mpvB.shutdown()
    }

    companion object {
        val EQ_FREQS = listOf(60, 230, 910, 3600, 14000)

        val EQ_PRESETS = linkedMapOf(
            "Flat" to listOf(0, 0, 0, 0, 0),
            "Pop" to listOf(-1, 2, 3, 1, -1),
            "Rock" to listOf(4, 2, -1, 2, 4),
            "Graves" to listOf(7, 4, 0, -1, -2),
            "Vocal" to listOf(-3, -1, 4, 4, 1),
        )

        private const val RADIO_PREFETCH = 5
        private const val RADIO_INITIAL = 20
        private const val RADIO_PAGE = 20
        private const val RADIO_MAX_QUEUE = 400
    }
}
