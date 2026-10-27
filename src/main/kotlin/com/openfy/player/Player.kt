package com.openfy.player

import com.openfy.core.RadioPage
import com.openfy.core.RepeatMode
import com.openfy.core.SessionCodec
import com.openfy.core.Track
import com.openfy.data.BpmClient
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
    val eqEnabled = MutableStateFlow(false)
    val eqGains = MutableStateFlow(List(EQ_FREQS.size) { 0 })
    val crossfadeSeconds = MutableStateFlow(0)
    /** CROSSFADE = sobreposicao equal-power; BLEND = tambem faz handoff de graves. */
    val transitionStyle = MutableStateFlow(TRANSITION_CROSSFADE)
    val djMode = MutableStateFlow(false)
    val crossfading = MutableStateFlow(false)
    /** Mensagem curta do modo DJ (reordenado, BPM insuficiente, etc.). */
    val djStatus = MutableStateFlow<String?>(null)

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
    private val shuffleRecent = ArrayDeque<String>()
    private var shuffleSnapshotQueue: List<Track>? = null
    private var shuffleSnapshotIndex = -1
    private var djSnapshotQueue: List<Track>? = null
    private var djJob: Job? = null

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
        transitionStyle.value = db.get("playback.transition")
            ?.takeIf { it == TRANSITION_BLEND } ?: TRANSITION_CROSSFADE
        djMode.value = runCatching { db.get("playback.dj")?.toBooleanStrict() }.getOrNull() ?: false
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
                onEnded()
            }
            is Mpv.Event.Failed -> {
                cancelCrossfade()
                busy.value = false
                playing.value = false
                val title = current.value?.title ?: "faixa"
                error.value = "Não foi possível tocar \"$title\"."
                com.openfy.core.Log.write("player: falha ao tocar ${current.value?.id}: ${event.reason}")
            }
            Mpv.Event.Closed -> {
                cancelCrossfade()
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

    fun playNow(tracks: List<Track>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        cancelCrossfade()
        stopRadio()
        queue.value = tracks
        index.value = startIndex.coerceIn(0, tracks.lastIndex)
        load(queue.value[index.value])
        save()
    }

    /** Inicia uma radio ja com a primeira pagina de faixas e o token de continuacao. */
    fun startRadio(tracks: List<Track>, continuation: String?) {
        if (tracks.isEmpty()) return
        cancelCrossfade()
        radioActive.value = true
        radioLoading.value = false
        radioContinuation = continuation
        queue.value = tracks
        index.value = 0
        com.openfy.core.Log.write("player: radio iniciada com ${tracks.size} faixas")
        load(tracks[0])
    }

    fun stopRadio() {
        if (!radioActive.value && radioContinuation == null) return
        radioActive.value = false
        radioLoading.value = false
        radioContinuation = null
        com.openfy.core.Log.write("player: radio encerrada")
    }

    /** Toca a faixa agora e emenda a radio dela em seguida (clique na busca). */
    fun playThenRadio(track: Track) {
        playNow(listOf(track), 0)
        radioLoading.value = true
        scope.launch {
            val page = runCatching { radioStarter?.invoke(track.id) }.getOrNull()
            radioLoading.value = false
            if (page == null || page.tracks.isEmpty()) return@launch
            if (current.value?.id != track.id) return@launch
            val known = queue.value.mapTo(HashSet()) { it.id }
            val extra = page.tracks.filter { it.id !in known }
            if (extra.isEmpty()) return@launch
            radioActive.value = true
            radioContinuation = page.continuation
            queue.value = queue.value + extra
            com.openfy.core.Log.write("player: radio emendada +${extra.size} (fila=${queue.value.size})")
            maybeLoadMoreRadio()
            save()
        }
    }

    /** No fim de uma fila comum, continua com a radio da ultima faixa. */
    private fun startRadioFromCurrent() {
        val seed = current.value ?: return
        if (radioLoading.value) return
        radioLoading.value = true
        scope.launch {
            val page = runCatching { radioStarter?.invoke(seed.id) }.getOrNull()
            radioLoading.value = false
            if (page == null || page.tracks.isEmpty()) {
                radioActive.value = false
                playing.value = false
                active.stop()
                return@launch
            }
            val known = queue.value.mapTo(HashSet()) { it.id }
            val extra = page.tracks.filter { it.id !in known }
            radioActive.value = true
            radioContinuation = page.continuation
            if (extra.isEmpty()) {
                if (page.continuation != null) requestRadioAdvance()
                return@launch
            }
            val start = queue.value.size
            queue.value = queue.value + extra
            index.value = start
            trimQueue()
            com.openfy.core.Log.write("player: fim da fila -> radio +${extra.size}")
            load(queue.value[index.value])
        }
    }

    fun playSingle(track: Track) = playNow(listOf(track), 0)

    /** Toca uma lista embaralhada e guarda a ordem original para o modo shuffle. */
    fun playShuffled(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        if (djMode.value) setDjMode(false)
        shuffleSnapshotQueue = tracks
        shuffleSnapshotIndex = 0
        shuffle.value = true
        playNow(tracks.shuffled(), 0)
        save()
    }

    fun addNext(track: Track) {
        cancelPrep()
        val q = queue.value.toMutableList()
        val at = (index.value + 1).coerceIn(0, q.size)
        q.add(at, track)
        queue.value = q
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

    fun next(auto: Boolean = false) {
        cancelCrossfade()
        val q = queue.value
        if (q.isEmpty()) return
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
            // Fim de playlist/album/mix: emenda uma radio na ultima faixa.
            startRadioFromCurrent()
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
        val prev = (index.value - 1).let { if (it < 0) q.lastIndex else it }
        index.value = prev
        load(q[prev])
        save()
    }

    fun togglePlay() {
        val track = current.value ?: return
        if (!active.ready.value) {
            // mpv caiu ou ainda esta conectando: recarrega em vez de mandar pause ao vazio.
            load(track)
            return
        }
        val nowPlaying = playing.value
        if (nowPlaying) cancelCrossfade()
        active.pause(nowPlaying)
        playing.value = !nowPlaying
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

    fun toggleShuffle() {
        val enable = !shuffle.value
        if (enable && djMode.value) setDjMode(false)
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

    private fun load(track: Track, resetPlay: Boolean = true) {
        com.openfy.core.Log.write(
            "player: tocando ${track.id} \"${track.title}\" (mpvReady=${active.ready.value})"
        )
        cancelCrossfade()
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
        scope.launch(Dispatchers.IO) { db.recordPlay(track.id) }
        maybeLoadMoreRadio()
        applyDjLater()
        save()
    }

    /**
     * Mantem a radio abastecida: quando restam poucas faixas, pede a proxima
     * pagina ao controlador sem interromper a reproducao atual.
     */
    private fun maybeLoadMoreRadio() {
        if (!radioActive.value || radioLoading.value) return
        val token = radioContinuation ?: return
        if (queue.value.size - index.value > RADIO_PREFETCH) return
        radioLoading.value = true
        scope.launch {
            val page = runCatching { radioSource?.invoke(token) }.getOrNull()
            radioLoading.value = false
            if (page == null) {
                radioContinuation = null
                return@launch
            }
            if (page.continuation == token) {
                radioContinuation = null
                return@launch
            }
            radioContinuation = page.continuation
            val known = queue.value.mapTo(HashSet()) { it.id }
            val extra = page.tracks.filter { it.id !in known }
            queue.value = queue.value + extra
            trimQueue()
            com.openfy.core.Log.write("player: radio +${extra.size} faixas (fila=${queue.value.size})")
        }
    }

    /** Evita que a fila de radio cresca sem limite numa sessao longa. */
    private fun trimQueue() {
        val q = queue.value
        val cur = index.value
        if (q.size <= 600 || cur < 150) return
        cancelPrep()
        val drop = cur - 100
        queue.value = q.drop(drop)
        index.value = cur - drop
        com.openfy.core.Log.write("player: fila aparada em $drop faixas (agora ${queue.value.size})")
    }

    /** Fim da fila da radio: busca a proxima pagina e continua em vez de parar. */
    private fun requestRadioAdvance() {
        cancelCrossfade()
        val token = radioContinuation
        if (token == null || radioLoading.value) {
            radioActive.value = false
            playing.value = false
            active.stop()
            return
        }
        radioLoading.value = true
        scope.launch {
            val page = runCatching { radioSource?.invoke(token) }.getOrNull()
            radioLoading.value = false
            val tracks = page?.tracks.orEmpty()
            if (tracks.isEmpty()) {
                radioActive.value = false
                radioContinuation = null
                playing.value = false
                active.stop()
                return@launch
            }
            radioContinuation = page?.continuation
            val known = queue.value.mapTo(HashSet()) { it.id }
            val extra = tracks.filter { it.id !in known }
            if (extra.isEmpty()) {
                radioActive.value = false
                radioContinuation = null
                playing.value = false
                active.stop()
                return@launch
            }
            val start = queue.value.size
            queue.value = queue.value + extra
            index.value = start
            trimQueue()
            load(queue.value[index.value])
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
        val nowLiked = withContext(Dispatchers.IO) { db.toggleLike(track.id) }
        refreshLikes()
        return nowLiked
    }

    // ------------------------------------------------------------------ session
    /** Retoma a ultima sessao: fila, faixa, posicao (pausado), volume e modos. */
    fun restore() {
        val session = runCatching { SessionCodec.load(db) }.getOrNull() ?: return
        shuffle.value = session.shuffle
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
        save()
    }

    fun move(from: Int, to: Int) {
        cancelCrossfade()
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

    private fun applyEqTo(target: Mpv, cutLows: Boolean = false) {
        val filters = mutableListOf<String>()
        if (eqEnabled.value) {
            filters += EQ_FREQS.zip(eqGains.value).map { (freq, gain) ->
                "equalizer=f=$freq:t=q:w=1:g=$gain"
            }
        }
        // Handoff de graves do modo DJ blend: a faixa que sai perde o low-end,
        // deixando o corpo/graves da proxima aparecer. Nao separa vocais.
        if (cutLows) filters += "highpass=f=120"
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

    fun setTransitionStyle(style: String) {
        val value = if (style == TRANSITION_BLEND) TRANSITION_BLEND else TRANSITION_CROSSFADE
        transitionStyle.value = value
        scope.launch(Dispatchers.IO) { db.put("playback.transition", value) }
    }

    private fun checkCrossfade(position: Double) {
        val fade = crossfadeSeconds.value
        if (fade <= 0 || crossfadeActive) return
        if (!playing.value) return
        val total = duration.value
        if (total <= 0) return
        val remaining = total - position
        // Fixa a proxima escolhida no preparo. Sem isso, shuffle/DJ poderiam
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
        index.value = nextIndex
        current.value = nextTrack
        position.value = 0.0
        duration.value = if (crossfadeDuration > 0) crossfadeDuration else nextTrack.durationSec.toDouble()
        playing.value = true
        busy.value = false
        pendingPause = false
        if (crossfadeDuration > 1) persistDuration(crossfadeDuration)
        if (shuffle.value) {
            shuffleRecent.addLast(nextTrack.id)
            while (shuffleRecent.size > minOf(queue.value.size - 1, 40)) shuffleRecent.removeFirst()
        }
        new.pause(false)
        val target = volume.value
        scope.launch(Dispatchers.IO) { db.recordPlay(nextTrack.id) }
        save()
        maybeLoadMoreRadio()
        applyDjLater()
        crossfadeJob?.cancel()
        val blend = transitionStyle.value == TRANSITION_BLEND
        crossfadeJob = scope.launch {
            val steps = (fade * 10).toInt().coerceAtLeast(5)
            var lowsCut = false
            for (i in 0..steps) {
                if (!crossfadeActive) return@launch
                val fraction = i.toDouble() / steps
                // Curva equal-power: no meio as duas faixas soam de verdade,
                // em vez de um "buraco" de volume do fade linear.
                val angle = fraction * Math.PI / 2.0
                old.volume((target * cos(angle)).roundToInt())
                new.volume((target * sin(angle)).roundToInt())
                if (blend && !lowsCut && fraction >= 0.5) {
                    lowsCut = true
                    applyEqTo(old, cutLows = true)
                    com.openfy.core.Log.write("player: blend - graves liberados para a proxima")
                }
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
                "${if (blend) "dj blend" else "equal-power"}, " +
                "${if (shuffle.value) "shuffle" else "sequencial"})"
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

    // ------------------------------------------------------------------ DJ por BPM
    fun toggleDj() = setDjMode(!djMode.value)

    fun setDjMode(enabled: Boolean) {
        if (enabled && shuffle.value) {
            // DJ precisa da ordem real para reordenar; sair do shuffle primeiro.
            toggleShuffle()
        }
        djMode.value = enabled
        scope.launch(Dispatchers.IO) { db.put("playback.dj", enabled.toString()) }
        if (enabled) {
            djSnapshotQueue = queue.value
            djStatus.value = "Modo DJ: analisando BPM das próximas faixas…"
            applyDjLater()
        } else {
            djJob?.cancel()
            restoreDjSnapshot()
            djStatus.value = "Modo DJ desligado."
        }
        save()
    }

    private fun restoreDjSnapshot() {
        val snapshot = djSnapshotQueue ?: return
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
        djSnapshotQueue = null
    }

    private fun applyDjLater() {
        if (!djMode.value || shuffle.value) return
        djJob?.cancel()
        djJob = scope.launch {
            delay(1500)
            if (!djMode.value || shuffle.value) return@launch
            val snapshot = queue.value
            val currentIndex = index.value
            if (currentIndex < 0 || currentIndex >= snapshot.lastIndex) {
                djStatus.value = "Modo DJ: sem faixas suficientes depois da atual."
                return@launch
            }
            val window = snapshot.subList(
                currentIndex + 1,
                minOf(snapshot.size, currentIndex + 12),
            )
            if (window.size < 3) {
                djStatus.value = "Modo DJ: são necessárias pelo menos 3 faixas na frente."
                return@launch
            }
            val referenceBpm = withContext(Dispatchers.IO) { bpmFor(snapshot[currentIndex]) }
                ?: run {
                    djStatus.value = "Modo DJ: BPM da faixa atual indisponível."
                    return@launch
                }
            val scored = withContext(Dispatchers.IO) {
                window.map { track ->
                    val bpm = bpmFor(track)
                    track to if (bpm == null) Double.MAX_VALUE else kotlin.math.abs(bpm - referenceBpm)
                }
            }
            val known = scored.count { it.second != Double.MAX_VALUE }
            if (known < 3) {
                com.openfy.core.Log.write("dj: BPM insuficiente (${known}/${scored.size})")
                djStatus.value = "Modo DJ: BPM insuficiente; ordem mantida."
                return@launch
            }
            if (!djMode.value || index.value != currentIndex || queue.value.size != snapshot.size) {
                return@launch
            }
            cancelPrep()
            val sorted = scored.sortedBy { it.second }.map { it.first }
            val newQueue = snapshot.toMutableList()
            sorted.forEachIndexed { offset, track -> newQueue[currentIndex + 1 + offset] = track }
            queue.value = newQueue
            save()
            djStatus.value = "Modo DJ: próximas ${sorted.size} faixas reordenadas por BPM."
            com.openfy.core.Log.write(
                "dj: fila reordenada por BPM (ref=${referenceBpm.roundToInt()}, conhecidos=$known)"
            )
        }
    }

    private suspend fun bpmFor(track: Track): Double? {
        db.bpm(track.id)?.let { return it }
        val bpm = BpmClient.fetch(track) ?: return null
        delay(100)
        db.setBpm(track.id, bpm)
        return bpm
    }

    private fun pickShuffleIndex(q: List<Track>, current: Int): Int {
        val recent = shuffleRecent.toHashSet()
        var candidates = q.indices.filter { it != current && q[it].id !in recent }
        if (candidates.isEmpty()) {
            shuffleRecent.clear()
            candidates = q.indices.filter { it != current }
        }
        if (candidates.isEmpty()) return current
        val weights = candidates.map { if (q[it].id in likedIds.value) 3 else 1 }
        var roll = (1..weights.sum()).random()
        for (i in candidates.indices) {
            roll -= weights[i]
            if (roll <= 0) return candidates[i]
        }
        return candidates.last()
    }

    fun shutdown() {
        crossfadeJob?.cancel()
        djJob?.cancel()
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
        const val TRANSITION_CROSSFADE = "CROSSFADE"
        const val TRANSITION_BLEND = "BLEND"
    }
}
