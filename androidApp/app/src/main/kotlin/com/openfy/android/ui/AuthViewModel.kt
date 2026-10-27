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
import com.openfy.android.data.AuthSession
import com.openfy.android.data.PlaylistDto
import com.openfy.android.data.Profile
import com.openfy.android.data.PushPlayEvent
import com.openfy.android.data.RemoteTrack
import com.openfy.android.data.SessionStore
import com.openfy.android.data.StoredSession
import com.openfy.android.data.SupabaseApi
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
        AuthUiState(deviceName = store.deviceName()),
    )
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    private val player: ExoPlayer = ExoPlayer.Builder(application).build().apply {
        val attributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        setAudioAttributes(attributes, true)
        setHandleAudioBecomingNoisy(true)
        addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.update { it.copy(playing = isPlaying) }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    _state.update { it.copy(durationMs = duration.coerceAtLeast(0)) }
                }
                if (playbackState == Player.STATE_ENDED) {
                    finishEvent(completed = true, skipped = false)
                    next()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                _state.update {
                    it.copy(
                        busy = false,
                        playing = false,
                        error = "Falha ao tocar: ${error.message ?: "erro desconhecido"}",
                    )
                }
            }
        })
    }

    private var searchJob: Job? = null
    private var eventStartedAt: Long = 0
    private var eventTrack: Track? = null
    private var eventSource: String = "unknown"

    init {
        restore()
        viewModelScope.launch {
            while (isActive) {
                delay(500)
                if (player.isPlaying) {
                    _state.update {
                        it.copy(
                            positionMs = player.currentPosition,
                            durationMs = player.duration.coerceAtLeast(0),
                        )
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
        if (current.refreshToken.isBlank()) {
            store.clear()
            return null
        }
        return runCatching {
            val refreshed = api.refresh(current.refreshToken)
            store.save(refreshed, previous = current)
        }.onFailure {
            store.clear()
        }.getOrNull()
    }

    private suspend fun loadHome(session: StoredSession) {
        val run: suspend () -> Unit = {
            api.upsertDevice(session.accessToken, store.deviceId(), store.deviceName())
            val profile = api.profile(session.accessToken, session.userId)
            val playlists = api.playlists(session.accessToken)
            _state.update {
                it.copy(
                    loading = false,
                    busy = false,
                    profile = profile,
                    playlists = playlists,
                    deviceName = store.deviceName(),
                    error = null,
                )
            }
        }
        try {
            run()
        } catch (first: Exception) {
            val refreshed = ensureSession()
            if (refreshed != null && refreshed.accessToken != session.accessToken) {
                _state.update { it.copy(session = refreshed) }
                try {
                    run()
                    return
                } catch (second: Exception) {
                    _state.update { it.copy(loading = false, busy = false, error = second.message) }
                    return
                }
            }
            _state.update { it.copy(loading = false, busy = false, error = first.message) }
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
        val session = _state.value.session ?: return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = "Carregando playlist…") }
            runCatching {
                val items = api.playlistItems(session.accessToken, playlistId)
                val ids = items.map { it.trackId }.distinct()
                val remoteTracks = ids.chunked(80)
                    .flatMap { api.tracks(session.accessToken, it) }
                val byId = remoteTracks.associateBy { it.id }
                items.sortedBy { it.rank }.mapNotNull { item ->
                    byId[item.trackId]?.toTrack()
                }
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

    private fun play(track: Track, queue: List<Track>, source: String) {
        val session = _state.value.session
        if (session == null) {
            _state.update { it.copy(error = "Entre na conta para tocar.") }
            return
        }
        val current = _state.value
        if (current.track?.id != track.id) {
            finishEvent(completed = false, skipped = true)
        }
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
            player.setMediaSource(DefaultMediaSourceFactory(dataSource).createMediaSource(item))
            player.prepare()
            player.playWhenReady = true
            _state.update { it.copy(busy = false) }
        }
    }

    fun togglePlay() {
        if (_state.value.track == null) return
        if (player.isPlaying) player.pause() else player.play()
    }

    fun next() {
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
        val target = positionMs.coerceIn(0, _state.value.durationMs.coerceAtLeast(0))
        player.seekTo(target)
        _state.update { it.copy(positionMs = target) }
    }

    fun clearPlayerError() {
        _state.update { it.copy(error = null) }
    }

    private fun finishEvent(completed: Boolean, skipped: Boolean) {
        val track = eventTrack ?: return
        val session = _state.value.session ?: return
        val startedAt = eventStartedAt
        val played = if (completed) {
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
            player.stop()
            player.clearMediaItems()
            session?.let { api.logout(it.accessToken) }
            store.clear()
            _state.value = AuthUiState(loading = false, deviceName = store.deviceName())
        }
    }

    fun clearFeedback() {
        _state.update { it.copy(error = null, message = null, searchError = null) }
    }

    override fun onCleared() {
        runCatching { finishEvent(completed = false, skipped = true) }
        player.release()
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
