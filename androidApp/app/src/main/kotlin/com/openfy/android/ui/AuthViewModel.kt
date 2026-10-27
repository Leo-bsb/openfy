package com.openfy.android.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import com.openfy.android.data.AuthSession
import com.openfy.android.data.PlaylistDto
import com.openfy.android.data.Profile
import com.openfy.android.data.PushPlayEvent
import com.openfy.android.data.RemotePlaylistItem
import com.openfy.android.data.RemoteTrack
import com.openfy.android.data.SessionStore
import com.openfy.android.data.StoredSession
import com.openfy.android.data.SupabaseApi
import com.openfy.android.data.SupabaseException
import com.openfy.core.Track
import com.openfy.data.Media
import com.openfy.data.YouTubeEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import kotlin.math.cos
import kotlin.math.sin

data class AuthUiState(
    val loading: Boolean = true,
    val busy: Boolean = false,
    val session: StoredSession? = null,
    val profile: Profile? = null,
    val playlists: List<PlaylistDto> = emptyList(),
    val deviceName: String = "",
    val error: String? = null,
    val message: String? = null,
    val searchQuery: String = "",
    val searching: Boolean = false,
    val searchResults: List<Track> = emptyList(),
    val searchError: String? = null,
    val track: Track? = null,
    val queue: List<Track> = emptyList(),
    val queueIndex: Int = -1,
    val playing: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val likedIds: Set<String> = emptySet(),
    val likedTracks: List<Track> = emptyList(),
    val openPlaylist: PlaylistDto? = null,
    val playlistTracks: List<Track> = emptyList(),
    val playlistItems: List<RemotePlaylistItem> = emptyList(),
    val playlistLoading: Boolean = false,
    val crossfadeSeconds: Int = 6,
) {
    val hasPrevious: Boolean get() = queueIndex > 0
    val hasNext: Boolean get() = queueIndex >= 0 && queueIndex < queue.lastIndex
}

@UnstableApi
class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val store = SessionStore(application)
    private val api = SupabaseApi()
    private val youtube = YouTubeEngine()

    private val _state = MutableStateFlow(
        AuthUiState(
            deviceName = store.deviceName(),
            crossfadeSeconds = store.crossfadeSeconds(),
        ),
    )
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .build()

    private val playerA = createPlayer()
    private val playerB = createPlayer()
    private lateinit var activePlayer: ExoPlayer
    private val player: ExoPlayer get() = activePlayer
    private val standbyPlayer: ExoPlayer get() = if (activePlayer === playerA) playerB else playerA

    private fun createPlayer(): ExoPlayer {
        val p = ExoPlayer.Builder(getApplication()).build()
        p.setAudioAttributes(audioAttributes, false)
        p.setHandleAudioBecomingNoisy(true)
        p.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (p === activePlayer) {
                    _state.update { it.copy(playing = isPlaying) }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (p !== activePlayer) {
                    if (playbackState == Player.STATE_READY && p === standbyPlayer) {
                        crossfadeReady = true
                        crossfadeDurationMs = p.duration.coerceAtLeast(0L)
                    }
                    return
                }
                if (playbackState == Player.STATE_READY) {
                    _state.update { it.copy(durationMs = p.duration.coerceAtLeast(0)) }
                }
                if (playbackState == Player.STATE_ENDED) {
                    finishEvent(completed = true, skipped = false)
                    next()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (p === activePlayer) {
                    _state.update {
                        it.copy(
                            busy = false,
                            playing = false,
                            error = "Falha ao tocar: ${error.message ?: "erro desconhecido"}",
                        )
                    }
                }
            }
        })
        return p
    }

    private var searchJob: Job? = null
    private var eventStartedAt: Long = 0
    private var eventTrack: Track? = null
    private var eventSource: String = "unknown"
    private var preloadJob: Job? = null
    private var preloading = false
    private var preloadedTrack: Track? = null
    private var preloadedIndex: Int = -1
    private var crossfadePrepared = false
    private var crossfadeReady = false
    private var crossfading = false
    private var crossfadeJob: Job? = null
    private var crossfadeOld: ExoPlayer? = null
    private var crossfadeDurationMs: Long = 0
    private var crossfadeSeconds = store.crossfadeSeconds()

    init {
        activePlayer = playerA
        restore()
        viewModelScope.launch {
            while (isActive) {
                delay(250)
                if (player.isPlaying) {
                    val pos = player.currentPosition
                    val dur = player.duration.coerceAtLeast(0)
                    _state.update {
                        it.copy(
                            positionMs = pos,
                            durationMs = dur,
                        )
                    }
                    if (dur > 0 && crossfadeSeconds > 0 && !crossfading) {
                        val remaining = dur - pos
                        if (remaining <= crossfadeSeconds * 1000L + 4_000L && preloadedTrack == null) {
                            maybePreloadNext()
                        }
                        if (remaining <= crossfadeSeconds * 1000L && crossfadeReady) {
                            startCrossfade()
                        }
                    }
                }
            }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(120_000)
                val session = _state.value.session
                if (session != null && !_state.value.busy) {
                    loadHome(session)
                }
            }
        }
    }

    private fun restore() {
        viewModelScope.launch {
            val session = ensureSession()
            if (session == null) {
                _state.update { it.copy(loading = false, session = null) }
                return@launch
            }
            _state.update { it.copy(session = session, loading = true) }
            loadHome(session)
        }
    }

    private suspend fun ensureSession(): StoredSession? {
        val current = store.load() ?: return null
        if (!current.isExpired) return current
        return refreshSession(current) ?: run {
            store.clear()
            null
        }
    }

    private suspend fun refreshSession(current: StoredSession): StoredSession? {
        if (current.refreshToken.isBlank()) return null
        return runCatching {
            store.save(api.refresh(current.refreshToken), previous = current)
        }.getOrNull()
    }

    /** Executa uma chamada autenticada e, em caso de 401, renova o token uma vez. */
    private suspend fun <T> withSession(block: suspend (StoredSession) -> T): T {
        val session = ensureSession() ?: throw SupabaseException("Entre na conta para continuar.")
        return try {
            block(session)
        } catch (error: SupabaseException) {
            if (error.status != 401) throw error
            val refreshed = refreshSession(session) ?: throw error
            _state.update { it.copy(session = refreshed) }
            block(refreshed)
        }
    }

    private suspend fun loadHome(session: StoredSession) {
        try {
            loadHomeOnce(session)
        } catch (first: Exception) {
            val refreshed = ensureSession()
            if (refreshed != null && refreshed.accessToken != session.accessToken) {
                _state.update { it.copy(session = refreshed) }
                try {
                    loadHomeOnce(refreshed)
                    return
                } catch (second: Exception) {
                    _state.update { it.copy(loading = false, busy = false, error = second.message) }
                    return
                }
            }
            _state.update { it.copy(loading = false, busy = false, error = first.message) }
        }
    }

    private suspend fun loadHomeOnce(session: StoredSession) {
        api.upsertDevice(session.accessToken, store.deviceId(), store.deviceName())
        val profile = api.profile(session.accessToken, session.userId)
        val playlists = api.playlists(session.accessToken)
        val likedIds = runCatching { api.likes(session.accessToken) }.getOrDefault(emptySet())
        val likedTracks = runCatching {
            likedIds.chunked(80).flatMap { api.tracks(session.accessToken, it) }.map { it.toTrack() }
        }.getOrDefault(emptyList())
        _state.update {
            it.copy(
                loading = false,
                busy = false,
                profile = profile,
                playlists = playlists,
                likedIds = likedIds,
                likedTracks = likedTracks,
                deviceName = store.deviceName(),
                crossfadeSeconds = store.crossfadeSeconds(),
                error = null,
            )
        }
    }

    fun signIn(email: String, password: String) {
        if (email.isBlank() || password.length < 6) {
            _state.update { it.copy(error = "Informe e-mail e senha (mínimo 6 caracteres).") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            runCatching { api.signIn(email, password) }
                .onSuccess { auth ->
                    val stored = store.save(auth, emailFallback = email)
                    _state.update { it.copy(session = stored, loading = true, busy = false) }
                    loadHome(stored)
                }
                .onFailure { error ->
                    _state.update { it.copy(busy = false, error = error.message) }
                }
        }
    }

    fun signUp(email: String, password: String, displayName: String) {
        if (email.isBlank() || password.length < 6) {
            _state.update { it.copy(error = "Informe e-mail e senha (mínimo 6 caracteres).") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            runCatching {
                var auth: AuthSession = api.signUp(email, password, displayName)
                if (auth.accessToken.isBlank()) {
                    auth = api.signIn(email, password)
                }
                auth
            }.onSuccess { auth ->
                val stored = store.save(auth, emailFallback = email)
                _state.update { it.copy(session = stored, loading = true, busy = false) }
                loadHome(stored)
            }.onFailure { error ->
                _state.update { it.copy(busy = false, error = error.message) }
            }
        }
    }

    fun refreshLibrary() {
        val session = _state.value.session ?: return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            loadHome(session)
            _state.update { it.copy(message = "Biblioteca sincronizada.") }
        }
    }

    fun createPlaylist(name: String) {
        val session = _state.value.session ?: return
        if (name.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            runCatching { api.createPlaylist(session.accessToken, name) }
                .onSuccess {
                    loadHome(session)
                    _state.update { it.copy(message = "Playlist \"${name.trim()}\" criada.") }
                }
                .onFailure { error ->
                    _state.update { it.copy(busy = false, error = error.message) }
                }
        }
    }

    // ------------------------------------------------------------------ search
    fun search(query: String) {
        val clean = query.trim()
        _state.update { it.copy(searchQuery = clean, searchError = null) }
        searchJob?.cancel()
        if (clean.isBlank()) {
            _state.update { it.copy(searchResults = emptyList(), searching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            _state.update { it.copy(searching = true, searchResults = emptyList()) }
            delay(300)
            runCatching { youtube.searchSongs(clean, 20) }
                .onSuccess { tracks ->
                    _state.update {
                        it.copy(searching = false, searchResults = tracks, searchError = null)
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(searching = false, searchError = error.message ?: "Busca falhou.")
                    }
                }
        }
    }

    // ------------------------------------------------------------------ playback
    fun playSearchResult(track: Track) {
        play(track, _state.value.searchResults, "search")
    }

    fun playPlaylist(playlistId: String) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = "Carregando playlist…") }
            runCatching {
                withSession { session -> loadPlaylistTracks(session, playlistId).first }
            }.onSuccess { tracks ->
                _state.update { it.copy(busy = false, message = null) }
                val first = tracks.firstOrNull()
                if (first == null) {
                    _state.update { it.copy(error = "Essa playlist ainda está vazia.") }
                } else {
                    play(first, tracks, "playlist")
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(busy = false, message = null, error = error.message ?: "Playlist falhou.")
                }
            }
        }
    }

    private suspend fun loadPlaylistTracks(
        session: StoredSession,
        playlistId: String,
    ): Pair<List<Track>, List<RemotePlaylistItem>> {
        val items = api.playlistItems(session.accessToken, playlistId)
        val ids = items.map { it.trackId }.distinct()
        val remoteTracks = ids.chunked(80).flatMap { api.tracks(session.accessToken, it) }
        val byId = remoteTracks.associateBy { it.id }
        val tracks = items.sortedBy { it.rank }.mapNotNull { item -> byId[item.trackId]?.toTrack() }
        return tracks to items
    }

    fun openPlaylist(playlist: PlaylistDto) {
        _state.update {
            it.copy(
                openPlaylist = playlist,
                playlistTracks = emptyList(),
                playlistItems = emptyList(),
                playlistLoading = true,
                error = null,
                message = null,
            )
        }
        viewModelScope.launch {
            runCatching {
                withSession { session -> loadPlaylistTracks(session, playlist.id) }
            }.onSuccess { (tracks, items) ->
                _state.update {
                    if (it.openPlaylist?.id != playlist.id) {
                        it
                    } else {
                        it.copy(
                            playlistTracks = tracks,
                            playlistItems = items,
                            playlistLoading = false,
                        )
                    }
                }
            }.onFailure { error ->
                _state.update {
                    if (it.openPlaylist?.id != playlist.id) {
                        it
                    } else {
                        it.copy(
                            playlistLoading = false,
                            error = error.message ?: "Não foi possível abrir a playlist.",
                        )
                    }
                }
            }
        }
    }

    fun closePlaylist() {
        _state.update {
            it.copy(
                openPlaylist = null,
                playlistTracks = emptyList(),
                playlistItems = emptyList(),
                playlistLoading = false,
            )
        }
    }

    fun playOpenedPlaylist() {
        val tracks = _state.value.playlistTracks
        val first = tracks.firstOrNull() ?: return
        play(first, tracks, "playlist")
    }

    fun playPlaylistTrack(track: Track) {
        val tracks = _state.value.playlistTracks
        if (tracks.isEmpty()) return
        play(track, tracks, "playlist")
    }

    fun playLiked(track: Track) {
        play(track, _state.value.likedTracks, "likes")
    }

    fun toggleLike(track: Track) {
        val current = _state.value
        if (current.session == null) return
        val liked = track.id !in current.likedIds
        _state.update {
            it.copy(
                likedIds = if (liked) it.likedIds + track.id else it.likedIds - track.id,
                likedTracks = if (liked) {
                    listOf(track) + it.likedTracks.filter { item -> item.id != track.id }
                } else {
                    it.likedTracks.filter { item -> item.id != track.id }
                },
                error = null,
            )
        }
        viewModelScope.launch {
            runCatching {
                withSession { fresh -> api.setLike(fresh.accessToken, fresh.userId, track, liked) }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        likedIds = if (liked) it.likedIds - track.id else it.likedIds + track.id,
                        likedTracks = if (liked) {
                            it.likedTracks.filter { item -> item.id != track.id }
                        } else {
                            listOf(track) + it.likedTracks.filter { item -> item.id != track.id }
                        },
                        error = "Curtida não sincronizou: ${error.message}",
                    )
                }
            }
        }
    }

    fun addToPlaylist(playlist: PlaylistDto, track: Track) {
        viewModelScope.launch {
            runCatching {
                withSession { session -> api.addPlaylistItem(session.accessToken, playlist.id, track) }
            }.onSuccess {
                _state.update { it.copy(message = "\"${track.title}\" adicionada a ${playlist.name}.") }
                val opened = _state.value.openPlaylist
                if (opened?.id == playlist.id) openPlaylist(opened)
            }.onFailure { error ->
                _state.update { it.copy(error = error.message ?: "Não foi possível adicionar.") }
            }
        }
    }

    fun removeFromPlaylist(item: RemotePlaylistItem) {
        val playlist = _state.value.openPlaylist ?: return
        viewModelScope.launch {
            runCatching {
                withSession { session -> api.removePlaylistItem(session.accessToken, item.id) }
            }.onSuccess {
                _state.update {
                    it.copy(
                        playlistItems = it.playlistItems.filter { entry -> entry.id != item.id },
                        playlistTracks = it.playlistTracks.filter { track -> track.id != item.trackId },
                        message = "Faixa removida da playlist.",
                    )
                }
            }.onFailure { error ->
                _state.update { it.copy(error = error.message ?: "Não foi possível remover.") }
            }
        }
    }

    fun setCrossfadeSeconds(seconds: Int) {
        val value = seconds.coerceIn(0, 12)
        crossfadeSeconds = value
        store.setCrossfadeSeconds(value)
        _state.update { it.copy(crossfadeSeconds = value) }
        if (value == 0) cancelCrossfade()
    }

    // ------------------------------------------------------------------ queue editing
    fun playNext(track: Track) {
        val current = _state.value
        if (current.track == null) {
            play(track, listOf(track), "queue")
            return
        }
        val queue = current.queue.toMutableList()
        val at = (current.queueIndex + 1).coerceIn(0, queue.size)
        queue.add(at, track)
        _state.update { it.copy(queue = queue, message = "Toca em seguida: ${track.title}") }
        invalidatePreload()
    }

    fun addToQueue(track: Track) {
        val current = _state.value
        if (current.track == null) {
            play(track, listOf(track), "queue")
            return
        }
        _state.update {
            it.copy(queue = it.queue + track, message = "Na fila: ${track.title}")
        }
        invalidatePreload()
    }

    fun playQueueIndex(index: Int) {
        val current = _state.value
        if (index !in current.queue.indices) return
        play(current.queue[index], current.queue, "queue")
    }

    fun removeQueueIndex(index: Int) {
        val current = _state.value
        if (index !in current.queue.indices) return
        val queue = current.queue.toMutableList()
        val wasCurrent = index == current.queueIndex
        queue.removeAt(index)
        val newIndex = when {
            queue.isEmpty() -> -1
            index < current.queueIndex -> current.queueIndex - 1
            else -> current.queueIndex.coerceAtMost(queue.lastIndex)
        }
        _state.update { it.copy(queue = queue, queueIndex = newIndex) }
        invalidatePreload()
        if (wasCurrent) {
            if (newIndex < 0) {
                cancelCrossfade()
                player.stop()
                player.clearMediaItems()
                _state.update {
                    it.copy(track = null, playing = false, positionMs = 0, durationMs = 0)
                }
            } else {
                play(queue[newIndex], queue, "queue")
            }
        }
    }

    private fun invalidatePreload() {
        if (crossfading) return
        preloadJob?.cancel()
        preloadJob = null
        preloading = false
        preloadedTrack = null
        preloadedIndex = -1
        crossfadePrepared = false
        crossfadeReady = false
        crossfadeDurationMs = 0
        if (::activePlayer.isInitialized) {
            standbyPlayer.stop()
            standbyPlayer.clearMediaItems()
        }
    }

    private fun play(track: Track, queue: List<Track>, source: String) {
        val session = _state.value.session
        if (session == null) {
            _state.update { it.copy(error = "Entre na conta para tocar.") }
            return
        }
        cancelCrossfade()
        val current = _state.value
        finishEvent(completed = false, skipped = true)
        val index = queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        eventStartedAt = System.currentTimeMillis()
        eventTrack = track
        eventSource = source
        _state.update {
            it.copy(
                track = track,
                queue = queue,
                queueIndex = index,
                playing = false,
                busy = true,
                positionMs = 0,
                durationMs = track.durationSec * 1000L,
                error = null,
            )
        }
        viewModelScope.launch {
            val media = withContext(Dispatchers.IO) { youtube.media(track.id) } as? Media.Remote
            if (media == null) {
                _state.update {
                    it.copy(
                        busy = false,
                        track = null,
                        playing = false,
                        error = "Não foi possível extrair o áudio desta faixa.",
                    )
                }
                return@launch
            }
            standbyPlayer.stop()
            standbyPlayer.clearMediaItems()
            standbyPlayer.setAudioAttributes(audioAttributes, false)
            activePlayer.setAudioAttributes(audioAttributes, true)
            activePlayer.volume = 1f
            activePlayer.setMediaSource(mediaSource(track, media))
            activePlayer.prepare()
            activePlayer.playWhenReady = true
            _state.update { it.copy(busy = false) }
        }
    }

    private fun mediaSource(track: Track, media: Media.Remote): MediaSource {
        val dataSource = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(media.headers)
            .setAllowCrossProtocolRedirects(true)
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .apply {
                if (track.thumbnail.isNotBlank()) {
                    setArtworkUri(Uri.parse(track.thumbnail))
                }
            }
            .build()
        val item = MediaItem.Builder()
            .setUri(Uri.parse(media.url))
            .setMediaId(track.id)
            .setMediaMetadata(metadata)
            .build()
        return DefaultMediaSourceFactory(dataSource).createMediaSource(item)
    }

    private fun maybePreloadNext() {
        if (crossfadeSeconds <= 0 || preloading || preloadedTrack != null || crossfading) return
        val s = _state.value
        val nextIndex = s.queueIndex + 1
        if (nextIndex !in s.queue.indices) return
        val track = s.queue[nextIndex]
        val currentId = s.track?.id
        com.openfy.core.Log.write("player: preparando crossfade ${track.id}")
        preloading = true
        preloadJob?.cancel()
        preloadJob = viewModelScope.launch {
            try {
                val media = withContext(Dispatchers.IO) { youtube.media(track.id) } as? Media.Remote
                    ?: return@launch
                val now = _state.value
                if (crossfading || now.track?.id != currentId || now.queueIndex + 1 != nextIndex) {
                    return@launch
                }
                val standby = standbyPlayer
                standby.stop()
                standby.setAudioAttributes(audioAttributes, false)
                standby.volume = 0f
                standby.setMediaSource(mediaSource(track, media))
                standby.prepare()
                standby.playWhenReady = false
                preloadedTrack = track
                preloadedIndex = nextIndex
                crossfadePrepared = true
                crossfadeReady = false
            } finally {
                preloading = false
            }
        }
    }

    private fun startCrossfade() {
        if (crossfadeSeconds <= 0) return
        val nextTrack = preloadedTrack ?: return
        val nextIndex = preloadedIndex
        if (nextIndex !in _state.value.queue.indices) return
        val old = activePlayer
        val new = standbyPlayer
        val oldPosition = old.currentPosition.coerceAtLeast(0L)
        finishEvent(
            completed = false,
            skipped = false,
            playedOverride = oldPosition,
        )
        crossfading = true
        crossfadeOld = old
        activePlayer = new
        old.setAudioAttributes(audioAttributes, false)
        com.openfy.core.Log.write("player: crossfade ${nextTrack.id} (${crossfadeSeconds}s)")
        eventStartedAt = System.currentTimeMillis()
        eventTrack = nextTrack
        new.setAudioAttributes(audioAttributes, true)
        new.volume = 0f
        new.playWhenReady = true
        _state.update {
            it.copy(
                track = nextTrack,
                queueIndex = nextIndex,
                positionMs = 0,
                durationMs = if (crossfadeDurationMs > 0) crossfadeDurationMs else nextTrack.durationSec * 1000L,
                playing = true,
                busy = false,
            )
        }
        preloadedTrack = null
        preloadedIndex = -1
        crossfadePrepared = false
        crossfadeReady = false
        crossfadeDurationMs = 0
        crossfadeJob?.cancel()
        crossfadeJob = viewModelScope.launch {
            val steps = (crossfadeSeconds * 10).coerceAtLeast(5)
            for (i in 0..steps) {
                if (!crossfading) return@launch
                val fraction = i.toDouble() / steps
                val angle = fraction * Math.PI / 2.0
                old.volume = cos(angle).toFloat()
                new.volume = sin(angle).toFloat()
                delay(100)
            }
            if (!crossfading) return@launch
            old.stop()
            old.clearMediaItems()
            old.volume = 1f
            new.volume = 1f
            crossfadeOld = null
            crossfading = false
            com.openfy.core.Log.write("player: crossfade concluido")
            maybePreloadNext()
        }
    }

    private fun cancelCrossfade() {
        preloadJob?.cancel()
        preloadJob = null
        preloading = false
        preloadedTrack = null
        preloadedIndex = -1
        crossfadePrepared = false
        crossfadeReady = false
        crossfadeDurationMs = 0
        crossfadeJob?.cancel()
        crossfadeJob = null
        if (crossfading) {
            crossfadeOld?.stop()
            crossfadeOld = null
            crossfading = false
        }
        if (::activePlayer.isInitialized) {
            activePlayer.setAudioAttributes(audioAttributes, true)
            activePlayer.volume = 1f
            standbyPlayer.stop()
            standbyPlayer.clearMediaItems()
            standbyPlayer.setAudioAttributes(audioAttributes, false)
        }
    }

    fun togglePlay() {
        if (_state.value.track == null) return
        if (crossfading) cancelCrossfade()
        if (player.isPlaying) player.pause() else player.play()
    }

    fun next() {
        cancelCrossfade()
        val current = _state.value
        val next = current.queueIndex + 1
        if (next !in current.queue.indices) {
            finishEvent(completed = true, skipped = false)
            player.pause()
            _state.update { it.copy(playing = false) }
            return
        }
        finishEvent(completed = false, skipped = true)
        play(current.queue[next], current.queue, eventSource)
    }

    fun previous() {
        cancelCrossfade()
        if (player.currentPosition > 3_000) {
            player.seekTo(0)
            _state.update { it.copy(positionMs = 0) }
            return
        }
        val current = _state.value
        val prev = current.queueIndex - 1
        if (prev !in current.queue.indices) return
        finishEvent(completed = false, skipped = true)
        play(current.queue[prev], current.queue, eventSource)
    }

    fun seekTo(positionMs: Long) {
        if (_state.value.track == null) return
        if (crossfading) cancelCrossfade()
        val target = positionMs.coerceIn(0, _state.value.durationMs.coerceAtLeast(0))
        player.seekTo(target)
        _state.update { it.copy(positionMs = target) }
    }

    fun clearPlayerError() {
        _state.update { it.copy(error = null) }
    }

    private fun finishEvent(
        completed: Boolean,
        skipped: Boolean,
        playedOverride: Long? = null,
    ) {
        val track = eventTrack ?: return
        val session = _state.value.session ?: return
        val startedAt = eventStartedAt
        val played = playedOverride ?: if (completed) {
            _state.value.durationMs.coerceAtLeast(0L)
        } else {
            player.currentPosition.coerceAtLeast(0L)
        }
        eventTrack = null
        eventStartedAt = 0
        if (played < 1_000) return
        viewModelScope.launch {
            runCatching {
                api.pushTrack(session.accessToken, track)
                api.pushPlayEvent(
                    session.accessToken,
                    PushPlayEvent(
                        userId = session.userId,
                        deviceId = store.deviceId(),
                        trackId = track.id,
                        startedAt = Instant.ofEpochMilli(startedAt).toString(),
                        playedMs = played.toInt(),
                        durationMs = track.durationSec * 1000,
                        completed = completed,
                        skipped = skipped,
                        source = eventSource,
                        contextId = null,
                    ),
                )
            }
        }
    }

    fun signOut() {
        val session = _state.value.session
        viewModelScope.launch {
            finishEvent(completed = false, skipped = true)
            cancelCrossfade()
            playerA.stop()
            playerB.stop()
            playerA.clearMediaItems()
            playerB.clearMediaItems()
            session?.let { api.logout(it.accessToken) }
            store.clear()
            _state.value = AuthUiState(
                loading = false,
                deviceName = store.deviceName(),
                crossfadeSeconds = store.crossfadeSeconds(),
            )
        }
    }

    fun clearFeedback() {
        _state.update { it.copy(error = null, message = null, searchError = null) }
    }

    override fun onCleared() {
        runCatching { finishEvent(completed = false, skipped = true) }
        runCatching { cancelCrossfade() }
        playerA.release()
        playerB.release()
        youtube.close()
        api.close()
        super.onCleared()
    }
}

private fun RemoteTrack.toTrack() = Track(
    id = id,
    title = title,
    artist = artist,
    album = album,
    durationSec = duration,
    thumbnail = thumbnail,
)
