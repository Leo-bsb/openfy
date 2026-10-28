package com.openfy.android.ui

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import com.openfy.android.data.AuthSession
import com.openfy.android.data.AudioDownloader
import com.openfy.android.data.CachedPlaylist
import com.openfy.android.data.DownloadEntry
import com.openfy.android.data.DownloadResult
import com.openfy.android.data.DownloadStore
import com.openfy.android.data.HomeCache
import com.openfy.android.data.HomeSnapshot
import com.openfy.android.data.LyricLine
import com.openfy.android.data.LyricsClient
import com.openfy.android.data.PlaylistCache
import com.openfy.android.data.MonthlyArtistRow
import com.openfy.android.data.MonthlyListeningRow
import com.openfy.android.data.PlaylistDto
import com.openfy.android.data.Profile
import com.openfy.android.data.PushPlayEvent
import com.openfy.android.data.RemotePlaylistItem
import com.openfy.android.data.RemoteTrack
import com.openfy.android.data.SessionStore
import com.openfy.android.data.SpotifyClient
import com.openfy.android.data.StoredSession
import com.openfy.android.data.SupabaseApi
import com.openfy.android.data.SupabaseException
import com.openfy.android.playback.EqualizerController
import com.openfy.android.playback.PlaybackBridge
import com.openfy.android.playback.PlaybackService
import com.openfy.core.ColorPalette
import com.openfy.core.Artist
import com.openfy.core.ArtistPage
import com.openfy.core.Importer
import com.openfy.core.PlayerLayout
import com.openfy.core.RemoteCollection
import com.openfy.core.SearchMode
import com.openfy.core.Track
import com.openfy.data.Media
import com.openfy.data.YouTubeEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.YearMonth
import kotlin.coroutines.coroutineContext
import kotlin.math.cos
import kotlin.math.sin

data class RecapData(
    val month: String,
    val listening: MonthlyListeningRow?,
    val topTracks: List<Track>,
    val artists: List<MonthlyArtistRow>,
)

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
    val searchArtists: List<Artist> = emptyList(),
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
    val palette: ColorPalette = ColorPalette.OPENFY,
    val playerLayout: PlayerLayout = PlayerLayout.CENTER,
    val coverGradient: Boolean = true,
    val recentTracks: List<Track> = emptyList(),
    val topTracks: List<Track> = emptyList(),
    val lyrics: List<LyricLine> = emptyList(),
    val lyricsLoading: Boolean = false,
    val lyricsError: String? = null,
    val sleepUntil: Long? = null,
    val eqEnabled: Boolean = false,
    val eqGains: List<Int> = listOf(0, 0, 0, 0, 0),
    val recapMonth: String = YearMonth.now().atDay(1).toString(),
    val recapMonths: List<String> = emptyList(),
    val recap: RecapData? = null,
    val recapLoading: Boolean = false,
    val recapError: String? = null,
    val shareCode: String? = null,
    val artistId: String? = null,
    val artistPage: ArtistPage? = null,
    val artistLoading: Boolean = false,
    val artistError: String? = null,
    val remoteCollectionId: String? = null,
    val remoteCollection: RemoteCollection? = null,
    val collectionLoading: Boolean = false,
    val collectionError: String? = null,
    val offline: Boolean = false,
    val offlineSavedAtMs: Long = 0,
    val downloadedIds: Set<String> = emptySet(),
    val downloads: List<DownloadEntry> = emptyList(),
    val downloadProgress: Map<String, Float> = emptyMap(),
    val downloadLimitMb: Int = SessionStore.DEFAULT_DOWNLOAD_LIMIT_MB,
) {
    val hasPrevious: Boolean get() = queueIndex > 0
    val hasNext: Boolean get() = queueIndex >= 0 && queueIndex < queue.lastIndex
    val downloadUsedBytes: Long get() = downloads.sumOf { it.sizeBytes }
}

@UnstableApi
class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val store = SessionStore(application)
    private val api = SupabaseApi()
    private val youtube = YouTubeEngine()
    private val homeCache = HomeCache(File(application.filesDir, "offline"))
    private val playlistCache = PlaylistCache(File(application.filesDir, "offline"))
    private val downloadStore = DownloadStore(File(application.filesDir, "downloads"))

    private val _state = MutableStateFlow(
        AuthUiState(
            deviceName = store.deviceName(),
            crossfadeSeconds = store.crossfadeSeconds(),
            palette = store.palette(),
            playerLayout = store.playerLayout(),
            coverGradient = store.coverGradient(),
            recentTracks = store.recentTracks(),
            eqEnabled = store.eqEnabled(),
            eqGains = store.eqGains(),
            downloadLimitMb = store.downloadLimitMb(),
        ),
    )
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .build()

    private val equalizer = EqualizerController()
    private val playerA = createPlayer()
    private val playerB = createPlayer()
    private lateinit var activePlayer: ExoPlayer
    private val player: ExoPlayer get() = activePlayer
    private val standbyPlayer: ExoPlayer get() = if (activePlayer === playerA) playerB else playerA

    private fun createPlayer(): ExoPlayer {
        val p = ExoPlayer.Builder(getApplication())
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        p.setAudioAttributes(audioAttributes, false)
        p.setHandleAudioBecomingNoisy(true)
        p.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                equalizer.attach(p, audioSessionId)
            }

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
    private var artistJob: Job? = null
    private var collectionJob: Job? = null
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
    private var radioContinuation: String? = null
    private var radioLoading = false
    private var radioSeed: Track? = null
    private var radioJob: Job? = null
    private var radioEpoch = 0
    private val homeMutex = Mutex()
    private var lastHomeLoad = 0L
    private var playJob: Job? = null
    private var radioAdvancePending = false
    private var lyricsJob: Job? = null
    private var sleepJob: Job? = null
    private var recapJob: Job? = null
    private val lyricsCache = mutableMapOf<String, List<LyricLine>>()
    private var lastSessionSave = 0L
    private var lastSavedTrack = ""
    private val downloader = AudioDownloader(downloadStore) {
        _state.value.downloadLimitMb.toLong() * BYTES_PER_MB
    }
    private val downloadJobs = mutableMapOf<String, Job>()
    private var playlistDownloadJob: Job? = null
    private val downloadMutex = Mutex()

    @Volatile
    private var activeDownloadId: String? = null

    init {
        activePlayer = playerA
        equalizer.applySaved(_state.value.eqEnabled, _state.value.eqGains)
        PlaybackBridge.onToggle = { togglePlay() }
        PlaybackBridge.onPlay = {
            PlaybackService.ensureStarted(getApplication())
            if (!player.isPlaying) player.play()
        }
        PlaybackBridge.onPause = {
            if (player.isPlaying) player.pause()
        }
        PlaybackBridge.onNext = { next() }
        PlaybackBridge.onPrevious = { previous() }
        PlaybackBridge.onStop = {
            runCatching {
                cancelCrossfade()
                player.pause()
            }
            _state.update { it.copy(playing = false) }
        }
        viewModelScope.launch {
            state.collect { current ->
                PlaybackBridge.update(
                    current.track,
                    current.playing,
                    current.durationMs,
                    current.hasPrevious,
                    current.hasNext,
                )
                val track = current.track
                if (track != null && current.queue.isNotEmpty()) {
                    val now = System.currentTimeMillis()
                    if (track.id != lastSavedTrack || now - lastSessionSave > 5_000) {
                        lastSavedTrack = track.id
                        lastSessionSave = now
                        store.setPlaybackSession(
                            current.queue,
                            current.queueIndex,
                            current.positionMs,
                        )
                    }
                }
            }
        }
        restore()
        viewModelScope.launch(Dispatchers.IO) {
            // Carrega o índice de downloads fora da main thread e limpa temporários órfãos.
            downloadStore.repair()
            val entries = downloadStore.all()
            _state.update {
                it.copy(
                    downloads = entries,
                    downloadedIds = entries.mapTo(HashSet()) { entry -> entry.track.id },
                )
            }
        }
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
                    maybeAppendRadio()
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
            restorePlaybackSession()
        }
    }

    private fun restorePlaybackSession() {
        if (_state.value.track != null) return
        val queue = store.playbackQueue()
        val index = store.playbackIndex()
        val track = queue.getOrNull(index) ?: return
        val position = store.playbackPosition().coerceAtLeast(0L)
        _state.update {
            it.copy(
                track = track,
                queue = queue,
                queueIndex = index,
                positionMs = position,
                durationMs = track.durationSec * 1000L,
                playing = false,
                busy = true,
            )
        }
        viewModelScope.launch {
            val local = withContext(Dispatchers.IO) { downloadStore.fileFor(track.id) }
            if (local != null) {
                activePlayer.stop()
                activePlayer.clearMediaItems()
                activePlayer.setAudioAttributes(audioAttributes, false)
                activePlayer.setMediaSource(mediaSourceLocal(track, local))
                activePlayer.prepare()
                activePlayer.seekTo(position)
                activePlayer.playWhenReady = false
                _state.update {
                    it.copy(
                        busy = false,
                        playing = false,
                        durationMs = activePlayer.duration.coerceAtLeast(0L),
                    )
                }
                return@launch
            }
            val media = withContext(Dispatchers.IO) { youtube.media(track.id) } as? Media.Remote
            if (_state.value.track?.id != track.id || _state.value.session == null) return@launch
            if (media == null) {
                val offlineNotice = if (!isOnline()) {
                    "Você está offline. Toque em uma faixa baixada para continuar ouvindo."
                } else {
                    null
                }
                _state.update { it.copy(busy = false, track = null, error = offlineNotice ?: it.error) }
                return@launch
            }
            activePlayer.stop()
            activePlayer.clearMediaItems()
            activePlayer.setAudioAttributes(audioAttributes, false)
            activePlayer.setMediaSource(mediaSource(track, media))
            activePlayer.prepare()
            activePlayer.seekTo(position)
            activePlayer.playWhenReady = false
            _state.update {
                it.copy(
                    busy = false,
                    playing = false,
                    durationMs = activePlayer.duration.coerceAtLeast(0L),
                )
            }
        }
    }

    private suspend fun ensureSession(): StoredSession? {
        val current = store.load() ?: return null
        if (!current.isExpired) return current
        val result = runCatching { refreshSession(current) }
        val refreshed = result.getOrNull()
        if (refreshed != null) return refreshed
        // Null com sucesso = o servidor rejeitou o refresh (credencial inválida).
        // Falha de rede/servidor não pode deslogar: mantém a sessão para o cache offline.
        if (result.isSuccess) {
            store.clear()
            return null
        }
        return current
    }

    private suspend fun refreshSession(current: StoredSession): StoredSession? {
        if (current.refreshToken.isBlank()) return null
        return try {
            store.save(api.refresh(current.refreshToken), previous = current)
        } catch (error: SupabaseException) {
            if (error.status == 400 || error.status == 401) null else throw error
        }
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

    /** Conexão de internet ativa (usa ACCESS_NETWORK_STATE, permissão de instalação). */
    private fun isOnline(): Boolean {
        val manager = getApplication<Application>()
            .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private suspend fun loadHome(session: StoredSession) {
        homeMutex.withLock {
            if (!isOnline()) {
                // Sem rede: abre o último snapshot salvo e não tenta chamadas ao servidor.
                if (!applyHomeSnapshot(session.userId)) {
                    _state.update {
                        it.copy(
                            loading = false,
                            busy = false,
                            error = "Sem conexão e nenhum dado salvo neste aparelho.",
                        )
                    }
                }
                return@withLock
            }
            try {
                loadHomeOnce(session)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (first: Exception) {
                val refreshed = ensureSession()
                if (refreshed != null && refreshed.accessToken != session.accessToken) {
                    _state.update { it.copy(session = refreshed) }
                    try {
                        loadHomeOnce(refreshed)
                        return
                    } catch (cancel: CancellationException) {
                        throw cancel
                    } catch (second: Exception) {
                        if (!applyHomeSnapshot(refreshed.userId)) {
                            _state.update { it.copy(loading = false, busy = false, error = second.message) }
                        }
                        return
                    }
                }
                if (!applyHomeSnapshot(session.userId)) {
                    _state.update { it.copy(loading = false, busy = false, error = first.message) }
                }
            }
        }
    }

    /**
     * Restaura o snapshot da Home salvo no aparelho (modo offline).
     * O cache nunca é apagado por falha de rede; só é substituído por um load com sucesso.
     * Retorna false quando não existe snapshot para este usuário.
     */
    private suspend fun applyHomeSnapshot(userId: String): Boolean {
        if (userId.isBlank()) return false
        val snapshot = withContext(Dispatchers.IO) { homeCache.load() } ?: return false
        if (snapshot.userId != userId) return false
        _state.update {
            it.copy(
                loading = false,
                busy = false,
                offline = true,
                offlineSavedAtMs = snapshot.savedAtMs,
                profile = snapshot.profile ?: it.profile,
                playlists = snapshot.playlists,
                likedIds = snapshot.likedIds.toSet(),
                likedTracks = snapshot.likedTracks,
                topTracks = snapshot.topTracks,
                recentTracks = snapshot.recentTracks.ifEmpty { it.recentTracks },
                error = null,
            )
        }
        return true
    }

    /** Atualiza ao voltar ao app, com throttle para não bater no servidor a cada composição. */
    fun refreshIfStale() {
        val session = _state.value.session ?: return
        if (System.currentTimeMillis() - lastHomeLoad < 30_000) return
        viewModelScope.launch { loadHome(session) }
    }

    private suspend fun loadHomeOnce(session: StoredSession) {
        if (_state.value.session?.userId != session.userId) return
        api.upsertDevice(session.accessToken, store.deviceId(), store.deviceName())
        val profile = api.profile(session.accessToken, session.userId)
        val playlistIds = (
            api.ownedPlaylistIds(session.accessToken, session.userId) +
                api.memberPlaylistIds(session.accessToken, session.userId)
            ).toSet()
        val playlists = api.playlists(session.accessToken, playlistIds)
        val likedIds = runCatching { api.likes(session.accessToken) }.getOrNull()
        val likedTracks = likedIds?.let { ids ->
            runCatching {
                ids.chunked(80).flatMap { api.tracks(session.accessToken, it) }.map { it.toTrack() }
            }.getOrNull()
        }
        val topTracks = loadTopTracks(session)
        if (_state.value.session?.userId != session.userId) return
        _state.update {
            it.copy(
                loading = false,
                busy = false,
                profile = profile,
                playlists = playlists,
                likedIds = likedIds ?: it.likedIds,
                likedTracks = likedTracks ?: it.likedTracks,
                topTracks = topTracks ?: it.topTracks,
                deviceName = store.deviceName(),
                crossfadeSeconds = store.crossfadeSeconds(),
                offline = false,
                offlineSavedAtMs = 0,
                error = null,
            )
        }
        lastHomeLoad = System.currentTimeMillis()
        // Salva o snapshot para uso offline. Nunca bloqueia a UI nem apaga cache em falha.
        val snapshot = HomeSnapshot(
            userId = session.userId,
            savedAtMs = System.currentTimeMillis(),
            profile = profile,
            playlists = playlists,
            likedIds = _state.value.likedIds.toList(),
            likedTracks = _state.value.likedTracks,
            topTracks = _state.value.topTracks,
            recentTracks = store.recentTracks(),
        )
        viewModelScope.launch(Dispatchers.IO) { homeCache.save(snapshot) }
        val open = _state.value.openPlaylist
        if (open != null) {
            val fresh = playlists.firstOrNull { it.id == open.id }
            if (fresh == null) {
                closePlaylist()
            } else if (fresh != open) {
                openPlaylist(fresh)
            }
        }
    }

    private suspend fun loadTopTracks(session: StoredSession): List<Track>? = runCatching {
        val month = YearMonth.now().atDay(1).toString()
        val rows = api.monthlyTopTracks(session.accessToken, month, 20)
        val byId = api.tracks(session.accessToken, rows.map { it.trackId }).associateBy { it.id }
        rows.mapNotNull { byId[it.trackId]?.toTrack() }
    }.getOrNull()

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
            if (_state.value.error == null) {
                _state.update { it.copy(message = "Biblioteca sincronizada.") }
            }
        }
    }

    fun createPlaylist(name: String, track: Track? = null) {
        val clean = name.trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            var created: PlaylistDto? = null
            runCatching {
                val playlist = withSession { session ->
                    api.createPlaylist(session.accessToken, clean)
                        ?: throw SupabaseException("O servidor não devolveu a playlist criada.")
                }
                created = playlist
                if (track != null) {
                    withSession { session ->
                        api.addPlaylistItem(session.accessToken, playlist.id, track)
                    }
                }
                playlist
            }.onSuccess { playlist ->
                _state.value.session?.let { loadHome(it) }
                _state.update {
                    it.copy(
                        busy = false,
                        message = if (track != null) {
                            "\"${track.title}\" adicionada a \"${playlist.name}\"."
                        } else {
                            "Playlist \"${playlist.name}\" criada."
                        },
                    )
                }
            }.onFailure { error ->
                val partial = created
                if (partial != null) {
                    _state.value.session?.let { loadHome(it) }
                    _state.update {
                        it.copy(
                            busy = false,
                            error = "Playlist \"${partial.name}\" criada, mas a faixa não foi adicionada: ${error.message}",
                        )
                    }
                } else {
                    _state.update {
                        it.copy(busy = false, error = error.message ?: "Não foi possível criar a playlist.")
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ search
    fun search(query: String) {
        val clean = query.trim()
        _state.update { it.copy(searchQuery = clean, searchError = null) }
        searchJob?.cancel()
        if (clean.isBlank()) {
            _state.update {
                it.copy(searchResults = emptyList(), searchArtists = emptyList(), searching = false)
            }
            return
        }
        searchJob = viewModelScope.launch {
            _state.update {
                it.copy(searching = true, searchResults = emptyList(), searchArtists = emptyList())
            }
            delay(300)
            runCatching { youtube.search(clean, SearchMode.SONGS) }
                .onSuccess { result ->
                    _state.update {
                        it.copy(
                            searching = false,
                            searchResults = result.tracks,
                            searchArtists = result.artists,
                            searchError = null,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(searching = false, searchError = error.message ?: "Busca falhou.")
                    }
                }
        }
    }

    fun openArtistById(id: String) {
        val clean = id.trim()
        if (clean.isBlank()) return
        artistJob?.cancel()
        _state.update {
            it.copy(
                artistId = clean,
                artistPage = null,
                artistLoading = true,
                artistError = null,
                remoteCollectionId = null,
                remoteCollection = null,
            )
        }
        artistJob = viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { youtube.artist(clean) } }
                .onSuccess { page ->
                    if (_state.value.artistId != clean) return@onSuccess
                    _state.update {
                        it.copy(artistPage = page, artistLoading = false, artistError = null)
                    }
                }
                .onFailure { error ->
                    if (_state.value.artistId != clean) return@onFailure
                    _state.update {
                        it.copy(
                            artistLoading = false,
                            artistError = error.message ?: "Não foi possível carregar o artista.",
                        )
                    }
                }
        }
    }

    fun openArtistFromName(name: String) {
        val clean = name.substringBefore("•").substringBefore(",").trim()
        if (clean.isBlank()) return
        artistJob?.cancel()
        _state.update {
            it.copy(
                artistId = clean,
                artistPage = null,
                artistLoading = true,
                artistError = null,
                remoteCollectionId = null,
                remoteCollection = null,
            )
        }
        artistJob = viewModelScope.launch {
            val found = runCatching {
                withContext(Dispatchers.IO) { youtube.search(clean, SearchMode.ARTISTS) }
            }.getOrNull()
            val artist = found?.artists?.firstOrNull()
            if (artist == null) {
                if (_state.value.artistId == clean) {
                    _state.update {
                        it.copy(artistLoading = false, artistError = "Artista não encontrado.")
                    }
                }
                return@launch
            }
            openArtistById(artist.id)
        }
    }

    fun closeArtist() {
        artistJob?.cancel()
        _state.update {
            it.copy(
                artistId = null,
                artistPage = null,
                artistLoading = false,
                artistError = null,
            )
        }
    }

    fun openCollection(id: String) {
        val clean = id.trim()
        if (clean.isBlank()) return
        collectionJob?.cancel()
        _state.update {
            it.copy(
                remoteCollectionId = clean,
                remoteCollection = null,
                collectionLoading = true,
                collectionError = null,
            )
        }
        collectionJob = viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { youtube.collection(clean) } }
                .onSuccess { collection ->
                    if (_state.value.remoteCollectionId != clean) return@onSuccess
                    _state.update {
                        it.copy(
                            remoteCollection = collection,
                            collectionLoading = false,
                            collectionError = null,
                        )
                    }
                }
                .onFailure { error ->
                    if (_state.value.remoteCollectionId != clean) return@onFailure
                    _state.update {
                        it.copy(
                            collectionLoading = false,
                            collectionError = error.message ?: "Não foi possível carregar.",
                        )
                    }
                }
        }
    }

    fun closeCollection() {
        collectionJob?.cancel()
        _state.update {
            it.copy(
                remoteCollectionId = null,
                remoteCollection = null,
                collectionLoading = false,
                collectionError = null,
            )
        }
    }

    fun playArtistTop(track: Track) {
        val tracks = _state.value.artistPage?.topTracks.orEmpty()
        play(track, tracks.ifEmpty { listOf(track) }, "artist")
    }

    fun playArtistRadio() {
        val page = _state.value.artistPage ?: return
        val id = page.radioVideoId ?: page.topTracks.firstOrNull()?.id ?: return
        val seed = Track(
            id = id,
            title = page.name,
            artist = page.name,
            thumbnail = page.avatar,
        )
        play(seed, listOf(seed), "artist")
        startRadio(seed, page.radioPlaylistId)
    }

    fun playCollectionTrack(track: Track) {
        val tracks = _state.value.remoteCollection?.tracks.orEmpty()
        play(track, tracks.ifEmpty { listOf(track) }, "collection")
    }

    fun playCollection() {
        val tracks = _state.value.remoteCollection?.tracks.orEmpty()
        if (tracks.isNotEmpty()) play(tracks.first(), tracks, "collection")
    }

    // ------------------------------------------------------------------ playback
    fun playSearchResult(track: Track) {
        play(track, listOf(track), "search")
        startRadio(track)
    }

    private fun resetRadio() {
        radioEpoch++
        radioJob?.cancel()
        radioJob = null
        radioLoading = false
        radioContinuation = null
        radioSeed = null
        radioAdvancePending = false
    }

    private fun startRadio(seed: Track, playlistId: String? = null) {
        resetRadio()
        radioSeed = seed
        val epoch = radioEpoch
        radioLoading = true
        radioJob = viewModelScope.launch {
            val page = runCatching {
                withContext(Dispatchers.IO) {
                    if (playlistId == null) youtube.radio(seed.id) else youtube.radio(seed.id, playlistId)
                }
            }.getOrNull()
            if (epoch != radioEpoch) return@launch
            radioLoading = false
            if (page == null || page.tracks.isEmpty() || _state.value.track?.id != seed.id) return@launch
            radioContinuation = page.continuation
            appendRadio(page.tracks.take(60), seed)
        }
    }

    private fun maybeAppendRadio() {
        val s = _state.value
        if (radioLoading || radioJob?.isActive == true) return
        val token = radioContinuation ?: return
        if (s.queueIndex < s.queue.lastIndex - 2) return
        val seed = radioSeed ?: s.track ?: return
        val epoch = radioEpoch
        radioLoading = true
        radioJob = viewModelScope.launch {
            val page = runCatching { withContext(Dispatchers.IO) { youtube.radioNext(token) } }.getOrNull()
            if (epoch != radioEpoch) return@launch
            radioLoading = false
            if (page == null || page.tracks.isEmpty()) {
                radioContinuation = null
                return@launch
            }
            radioContinuation = page.continuation
            appendRadio(page.tracks, seed)
        }
    }

    private fun appendRadio(candidates: List<Track>, seed: Track) {
        val current = _state.value
        val ids = current.queue.mapTo(HashSet()) { it.id }
        val keys = current.queue.mapTo(HashSet()) { radioKey(it) }
        val recentArtists = current.queue.takeLast(4).mapTo(HashSet()) { radioArtist(it) }
        val selected = candidates
            .filter {
                it.id !in ids &&
                    it.id != seed.id &&
                    radioKey(it) !in keys &&
                    radioArtist(it) !in recentArtists
            }
            .distinctBy { radioKey(it) }
            .take(20)
        if (selected.isEmpty()) return
        val wasAtEnd = current.track != null && current.queueIndex >= current.queue.lastIndex
        val advance = radioAdvancePending
        radioAdvancePending = false
        val newQueue = current.queue + selected
        _state.update { it.copy(queue = newQueue) }
        com.openfy.core.Log.write("player: radio +${selected.size} faixas")
        if (wasAtEnd && advance && !current.busy) {
            play(selected.first(), newQueue, eventSource, keepRadio = true, queueIndex = current.queue.size)
        }
    }

    private fun radioKey(track: Track): String {
        val title = track.title
            .replace(Regex("\\([^)]*\\)"), " ")
            .replace(Regex("\\[[^]]*]"), " ")
            .substringBefore(" - ")
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
        val artist = radioArtist(track)
        return "$title|$artist"
    }

    private fun radioArtist(track: Track): String =
        track.artist
            .substringBefore(",")
            .substringBefore("&")
            .replace(Regex("(?i)\\b(feat|ft|featuring)\\b.*"), " ")
            .lowercase()
            .trim()

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
                val cached = withContext(Dispatchers.IO) { playlistCache.get(playlistId) }
                if (cached != null && cached.tracks.isNotEmpty()) {
                    _state.update {
                        it.copy(
                            busy = false,
                            message = "Playlist offline salva em ${formatCacheTime(cached.savedAtMs)}.",
                            error = null,
                            offline = true,
                            offlineSavedAtMs = cached.savedAtMs,
                        )
                    }
                    play(cached.tracks.first(), cached.tracks, "playlist")
                } else {
                    _state.update {
                        it.copy(
                            busy = false,
                            message = null,
                            error = error.message ?: "Playlist falhou.",
                        )
                    }
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
                withContext(Dispatchers.IO) {
                    playlistCache.put(
                        CachedPlaylist(
                            id = playlist.id,
                            tracks = tracks,
                            items = items,
                            savedAtMs = System.currentTimeMillis(),
                        ),
                    )
                }
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
                val cached = withContext(Dispatchers.IO) { playlistCache.get(playlist.id) }
                if (cached != null) {
                    _state.update {
                        if (it.openPlaylist?.id != playlist.id) {
                            it
                        } else {
                            it.copy(
                                playlistTracks = cached.tracks,
                                playlistItems = cached.items,
                                playlistLoading = false,
                                offline = true,
                                offlineSavedAtMs = cached.savedAtMs,
                                message = "Playlist offline salva em ${formatCacheTime(cached.savedAtMs)}.",
                                error = null,
                            )
                        }
                    }
                } else {
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

    /** Toca a lista de mais ouvidas em ordem, como no desktop. */
    fun playTopTrack(track: Track) {
        val tracks = _state.value.topTracks
        val queue = if (tracks.any { it.id == track.id }) tracks else listOf(track)
        play(track, queue, "top")
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
        val opened = _state.value.openPlaylist
        if (opened?.id == playlist.id &&
            _state.value.playlistItems.any { it.trackId == track.id }
        ) {
            _state.update {
                it.copy(message = "\"${track.title}\" já estava em ${playlist.name}.")
            }
            return
        }
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

    fun setPlaylistCover(playlistId: String, uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            runCatching {
                withSession { session ->
                    val resolver = getApplication<Application>().contentResolver
                    val mime = resolver.getType(uri) ?: "image/jpeg"
                    val declaredSize = resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
                    if (declaredSize > 8 * 1024 * 1024) {
                        throw SupabaseException("A imagem precisa ter no máximo 8 MB.")
                    }
                    val extension = when {
                        mime.contains("png", ignoreCase = true) -> "png"
                        mime.contains("webp", ignoreCase = true) -> "webp"
                        mime.contains("gif", ignoreCase = true) -> "gif"
                        else -> "jpg"
                    }
                    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw SupabaseException("Não foi possível ler a imagem.")
                    if (bytes.size > 8 * 1024 * 1024) {
                        throw SupabaseException("A imagem precisa ter no máximo 8 MB.")
                    }
                    val url = api.uploadCover(
                        session.accessToken,
                        session.userId,
                        playlistId,
                        bytes,
                        mime,
                        extension,
                    )
                    api.setPlaylistCover(session.accessToken, playlistId, url)
                    url
                }
            }.onSuccess { url ->
                _state.update { current ->
                    current.copy(
                        busy = false,
                        playlists = current.playlists.map {
                            if (it.id == playlistId) it.copy(coverUrl = url) else it
                        },
                        openPlaylist = current.openPlaylist?.let {
                            if (it.id == playlistId) it.copy(coverUrl = url) else it
                        },
                        message = "Capa atualizada.",
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        busy = false,
                        error = error.message ?: "Falha ao atualizar a capa.",
                    )
                }
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

    fun setPalette(value: ColorPalette) {
        store.setPalette(value)
        _state.update { it.copy(palette = value) }
    }

    fun setPlayerLayout(value: PlayerLayout) {
        store.setPlayerLayout(value)
        _state.update { it.copy(playerLayout = value) }
    }

    fun setCoverGradient(enabled: Boolean) {
        store.setCoverGradient(enabled)
        _state.update { it.copy(coverGradient = enabled) }
    }

    fun loadLyrics(track: Track?) {
        lyricsJob?.cancel()
        if (track == null) {
            _state.update { it.copy(lyrics = emptyList(), lyricsLoading = false, lyricsError = null) }
            return
        }
        val cached = lyricsCache[track.id]
        if (cached != null) {
            _state.update { it.copy(lyrics = cached, lyricsLoading = false, lyricsError = null) }
            return
        }
        _state.update { it.copy(lyrics = emptyList(), lyricsLoading = true, lyricsError = null) }
        lyricsJob = viewModelScope.launch {
            val lines = LyricsClient.fetch(track)
            if (_state.value.track?.id != track.id) return@launch
            if (lines.isNotEmpty()) lyricsCache[track.id] = lines
            _state.update {
                it.copy(
                    lyrics = lines,
                    lyricsLoading = false,
                    lyricsError = if (lines.isEmpty()) "Letra não encontrada." else null,
                )
            }
        }
    }

    fun setSleepTimer(minutes: Int?) {
        sleepJob?.cancel()
        if (minutes == null) {
            _state.update { it.copy(sleepUntil = null) }
            return
        }
        val until = System.currentTimeMillis() + minutes * 60_000L
        _state.update { it.copy(sleepUntil = until) }
        sleepJob = viewModelScope.launch {
            delay(minutes * 60_000L)
            if (_state.value.sleepUntil != until) return@launch
            runCatching {
                cancelCrossfade()
                player.pause()
            }
            _state.update {
                it.copy(sleepUntil = null, playing = false, message = "Timer encerrado.")
            }
        }
    }

    fun setEqEnabled(enabled: Boolean) {
        store.setEqEnabled(enabled)
        equalizer.setEnabled(enabled)
        _state.update { it.copy(eqEnabled = enabled) }
    }

    fun setEqGain(index: Int, gainDb: Int) {
        val value = gainDb.coerceIn(-12, 12)
        equalizer.setGain(index, value)
        val gains = _state.value.eqGains.toMutableList()
        if (index !in gains.indices) return
        gains[index] = value
        store.setEqEnabled(true)
        store.setEqGains(gains)
        _state.update { it.copy(eqEnabled = true, eqGains = gains) }
    }

    fun applyEqPreset(name: String) {
        val preset = EqualizerController.PRESETS[name] ?: return
        equalizer.applyPreset(name)
        store.setEqEnabled(true)
        store.setEqGains(preset)
        _state.update { it.copy(eqEnabled = true, eqGains = preset) }
    }

    fun selectRecapMonth(month: String) {
        loadRecap(month, force = true)
    }

    fun loadRecap(month: String? = null, force: Boolean = false) {
        val session = _state.value.session ?: return
        val target = month ?: _state.value.recapMonth
        if (!force && _state.value.recap?.month == target && !_state.value.recapLoading) return
        recapJob?.cancel()
        _state.update { it.copy(recapMonth = target, recapLoading = true, recapError = null) }
        recapJob = viewModelScope.launch {
            runCatching {
                withSession { current ->
                    val months = api.monthlyMonths(current.accessToken)
                    val listening = api.monthlyListening(current.accessToken, target)
                    val rows = api.monthlyTopTracks(current.accessToken, target, 10)
                    val byId = if (rows.isEmpty()) {
                        emptyMap()
                    } else {
                        api.tracks(current.accessToken, rows.map { it.trackId })
                            .associateBy { it.id }
                    }
                    val tracks = rows.mapNotNull { byId[it.trackId]?.toTrack() }
                    val artists = api.monthlyTopArtists(current.accessToken, target, 8)
                    RecapData(
                        month = target,
                        listening = listening,
                        topTracks = tracks,
                        artists = artists,
                    ) to months
                }
            }.onSuccess { (data, months) ->
                if (_state.value.session?.userId != session.userId) return@onSuccess
                _state.update {
                    it.copy(
                        recap = data,
                        recapMonths = months.ifEmpty { listOf(target) },
                        recapLoading = false,
                        recapError = null,
                    )
                }
            }.onFailure { error ->
                if (_state.value.session?.userId != session.userId) return@onFailure
                _state.update {
                    it.copy(
                        recapLoading = false,
                        recapError = error.message ?: "Falha ao carregar o recap.",
                    )
                }
            }
        }
    }

    fun playRecapTrack(track: Track) {
        val tracks = _state.value.recap?.topTracks ?: emptyList()
        play(track, tracks.ifEmpty { listOf(track) }, "recap")
    }

    fun requestShareCode(playlistId: String) {
        viewModelScope.launch {
            _state.update { it.copy(shareCode = null, busy = true, error = null) }
            runCatching {
                withSession { session -> api.shareCode(session.accessToken, playlistId) }
            }.onSuccess { code ->
                _state.update {
                    it.copy(
                        busy = false,
                        shareCode = code,
                        error = if (code == null) "Não foi possível gerar o código." else null,
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(busy = false, error = error.message ?: "Falha ao compartilhar.")
                }
            }
        }
    }

    fun clearShareCode() {
        _state.update { it.copy(shareCode = null) }
    }

    fun importPlaylist(url: String) {
        if (SpotifyClient.playlistId(url) != null) {
            importSpotifyPlaylist(url)
            return
        }
        val browse = Importer.parseYtmBrowseId(url)
        if (browse == null) {
            _state.update {
                it.copy(error = "Link inválido. Cole a URL de uma playlist ou álbum do YouTube Music.")
            }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = "Importando playlist…") }
            var partial: PlaylistDto? = null
            runCatching {
                withSession { session ->
                    val first = withContext(Dispatchers.IO) { youtube.collection(browse) }
                    val tracks = first.tracks.toMutableList()
                    var token = first.continuation
                    var pages = 0
                    while (token != null && tracks.size < 500 && pages < 8) {
                        val page = withContext(Dispatchers.IO) { youtube.collectionNext(token) }
                        tracks += page.tracks.filterNot { candidate ->
                            tracks.any { it.id == candidate.id }
                        }
                        token = page.continuation
                        pages++
                    }
                    if (tracks.isEmpty()) throw SupabaseException("A playlist está vazia.")
                    val name = first.title.ifBlank { "Importada do YouTube Music" }
                    val playlist = api.createPlaylist(session.accessToken, name)
                        ?: throw SupabaseException("O servidor não devolveu a playlist criada.")
                    partial = playlist
                    val added = api.addPlaylistItems(session.accessToken, playlist.id, tracks)
                    playlist to added
                }
            }.onSuccess { (playlist, added) ->
                _state.value.session?.let { loadHome(it) }
                _state.update {
                    it.copy(
                        busy = false,
                        message = "\"${playlist.name}\" importada com $added faixas.",
                    )
                }
            }.onFailure { error ->
                val created = partial
                if (created != null) {
                    _state.value.session?.let { loadHome(it) }
                    _state.update {
                        it.copy(
                            busy = false,
                            error = "\"${created.name}\" foi criada, mas as faixas não entraram: ${error.message}",
                        )
                    }
                } else {
                    _state.update {
                        it.copy(busy = false, error = error.message ?: "Falha ao importar a playlist.")
                    }
                }
            }
        }
    }

    private fun importSpotifyPlaylist(url: String) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = "Lendo playlist do Spotify…") }
            var partial: PlaylistDto? = null
            runCatching {
                withSession { session ->
                    val spotify = SpotifyClient.playlist(url)
                        ?: throw SupabaseException("Não foi possível ler a playlist pública do Spotify.")
                    if (spotify.tracks.isEmpty()) {
                        throw SupabaseException("A playlist do Spotify está vazia.")
                    }
                    val name = spotify.name.ifBlank { "Importada do Spotify" }
                    val playlist = api.createPlaylist(session.accessToken, name)
                        ?: throw SupabaseException("O servidor não devolveu a playlist criada.")
                    partial = playlist
                    val matched = mutableListOf<Track>()
                    spotify.tracks.take(200).forEachIndexed { index, item ->
                        val candidates = runCatching {
                            withContext(Dispatchers.IO) {
                                youtube.searchSongs("${item.artist} ${item.title}", 6)
                            }
                        }.getOrDefault(emptyList())
                        Importer.bestMatch(item.title, item.artist, item.durationMs, candidates)
                            ?.let { track ->
                                if (matched.none { it.id == track.id }) matched += track
                            }
                        if (index % 5 == 0) {
                            _state.update {
                                it.copy(message = "Casando ${index + 1}/${spotify.tracks.size}…")
                            }
                        }
                        delay(80)
                    }
                    if (matched.isEmpty()) {
                        throw SupabaseException("Nenhuma faixa foi encontrada no YouTube Music.")
                    }
                    val added = api.addPlaylistItems(session.accessToken, playlist.id, matched)
                    playlist to (spotify.tracks.size to added)
                }
            }.onSuccess { (playlist, counts) ->
                _state.value.session?.let { loadHome(it) }
                val (total, added) = counts
                _state.update {
                    it.copy(
                        busy = false,
                        message = "\"${playlist.name}\" importada: $added de $total faixas encontradas.",
                    )
                }
            }.onFailure { error ->
                android.util.Log.e("OpenfySpotify", "import falhou: ${error.message}", error)
                val created = partial
                if (created != null) {
                    _state.value.session?.let { loadHome(it) }
                    _state.update {
                        it.copy(
                            busy = false,
                            error = "\"${created.name}\" foi criada, mas as faixas não entraram: ${error.message}",
                        )
                    }
                } else {
                    _state.update {
                        it.copy(busy = false, error = error.message ?: "Falha ao importar do Spotify.")
                    }
                }
            }
        }
    }

    fun joinPlaylist(code: String) {
        val clean = code.trim().substringAfterLast("/").substringAfter("code=").trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            runCatching {
                withSession { session -> api.joinPlaylist(session.accessToken, clean) }
            }.onSuccess {
                _state.value.session?.let { loadHome(it) }
                _state.update {
                    it.copy(busy = false, message = "Playlist adicionada à sua biblioteca.")
                }
            }.onFailure { error ->
                val message = error.message ?: "Código inválido."
                _state.update {
                    it.copy(
                        busy = false,
                        error = if (message.contains("invalid_code", ignoreCase = true) ||
                            message.contains("not found", ignoreCase = true)
                        ) {
                            "Código inválido ou expirado."
                        } else {
                            message
                        },
                    )
                }
            }
        }
    }

    private fun rememberRecent(track: Track) {
        val updated = (listOf(track) + _state.value.recentTracks.filterNot { it.id == track.id })
            .take(24)
        store.setRecentTracks(updated)
        _state.update { it.copy(recentTracks = updated) }
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
        play(current.queue[index], current.queue, "queue", queueIndex = index)
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
                play(queue[newIndex], queue, "queue", queueIndex = newIndex)
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

    private fun play(
        track: Track,
        queue: List<Track>,
        source: String,
        keepRadio: Boolean = false,
        queueIndex: Int? = null,
    ) {
        val session = _state.value.session
        if (session == null) {
            _state.update { it.copy(error = "Entre na conta para tocar.") }
            return
        }
        cancelCrossfade()
        playJob?.cancel()
        PlaybackService.ensureStarted(getApplication())
        if (!keepRadio) resetRadio()
        val current = _state.value
        finishEvent(completed = false, skipped = true)
        val index = (queueIndex ?: queue.indexOfFirst { it.id == track.id }).coerceAtLeast(0)
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
        playJob = viewModelScope.launch {
            val local = withContext(Dispatchers.IO) { downloadStore.fileFor(track.id) }
            if (local != null) {
                // Faixa baixada: toca direto do aparelho, sem rede.
                standbyPlayer.stop()
                standbyPlayer.clearMediaItems()
                standbyPlayer.setAudioAttributes(audioAttributes, false)
                activePlayer.setAudioAttributes(audioAttributes, true)
                activePlayer.volume = 1f
                activePlayer.setMediaSource(mediaSourceLocal(track, local))
                activePlayer.prepare()
                activePlayer.playWhenReady = true
                _state.update { it.copy(busy = false) }
                rememberRecent(track)
                return@launch
            }
            val media = withContext(Dispatchers.IO) { youtube.media(track.id) } as? Media.Remote
            if (_state.value.track?.id != track.id) return@launch
            if (media == null) {
                _state.update {
                    it.copy(
                        busy = false,
                        track = null,
                        playing = false,
                        error = if (!isOnline()) {
                            "Sem conexão. Baixe a faixa (menu ▸ Baixar) para ouvir offline."
                        } else {
                            "Não foi possível extrair o áudio desta faixa."
                        },
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
            rememberRecent(track)
        }
    }

    private fun mediaItem(track: Track, uri: Uri): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .apply {
                if (track.thumbnail.isNotBlank()) {
                    setArtworkUri(Uri.parse(track.thumbnail))
                }
            }
            .build()
        return MediaItem.Builder()
            .setUri(uri)
            .setMediaId(track.id)
            .setMediaMetadata(metadata)
            .build()
    }

    private fun mediaSource(track: Track, media: Media.Remote): MediaSource {
        val dataSource = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(media.headers)
            .setAllowCrossProtocolRedirects(true)
        return DefaultMediaSourceFactory(dataSource)
            .createMediaSource(mediaItem(track, Uri.parse(media.url)))
    }

    /** Fonte de áudio local para faixa baixada; funciona sem rede. */
    private fun mediaSourceLocal(track: Track, file: File): MediaSource {
        val dataSource = DefaultDataSource.Factory(getApplication())
        return DefaultMediaSourceFactory(dataSource)
            .createMediaSource(mediaItem(track, Uri.fromFile(file)))
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
                // Baixada? Usa o arquivo local; senão extrai o stream remoto.
                val source = withContext(Dispatchers.IO) {
                    val local = downloadStore.fileFor(track.id)
                    if (local != null) {
                        return@withContext mediaSourceLocal(track, local)
                    }
                    val media = youtube.media(track.id) as? Media.Remote
                        ?: return@withContext null
                    mediaSource(track, media)
                } ?: return@launch
                val now = _state.value
                if (crossfading || now.track?.id != currentId || now.queueIndex + 1 != nextIndex) {
                    return@launch
                }
                val standby = standbyPlayer
                standby.stop()
                standby.setAudioAttributes(audioAttributes, false)
                standby.volume = 0f
                standby.setMediaSource(source)
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
        rememberRecent(nextTrack)
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
        PlaybackService.ensureStarted(getApplication())
        if (crossfading) cancelCrossfade()
        if (player.isPlaying) player.pause() else player.play()
    }

    fun next() {
        cancelCrossfade()
        val current = _state.value
        val next = current.queueIndex + 1
        if (next !in current.queue.indices) {
            finishEvent(completed = true, skipped = false)
            if (radioContinuation != null) {
                radioAdvancePending = true
                maybeAppendRadio()
                return
            }
            player.pause()
            _state.update { it.copy(playing = false) }
            return
        }
        finishEvent(completed = false, skipped = true)
        play(current.queue[next], current.queue, eventSource, keepRadio = true, queueIndex = next)
    }

    fun previous() {
        cancelCrossfade()
        if (player.currentPosition > 3_000) {
            player.seekTo(0)
            _state.update { it.copy(positionMs = 0) }
            return
        }
        val current = _state.value
        if (current.queueIndex <= 0) {
            player.seekTo(0)
            _state.update { it.copy(positionMs = 0) }
            return
        }
        val prev = current.queueIndex - 1
        if (prev !in current.queue.indices) return
        finishEvent(completed = false, skipped = true)
        play(current.queue[prev], current.queue, eventSource, keepRadio = true, queueIndex = prev)
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
            resetRadio()
            cancelCrossfade()
            playlistDownloadJob?.cancel()
            downloadJobs.values.forEach { it.cancel() }
            downloadJobs.clear()
            downloader.cancelCurrent()
            playerA.stop()
            playerB.stop()
            playerA.clearMediaItems()
            playerB.clearMediaItems()
            session?.let { api.logout(it.accessToken) }
            lyricsJob?.cancel()
            sleepJob?.cancel()
            recapJob?.cancel()
            artistJob?.cancel()
            collectionJob?.cancel()
            lyricsCache.clear()
            PlaybackService.stop(getApplication())
            store.clear()
            homeCache.clear()
            playlistCache.clear()
            lastHomeLoad = 0L
            _state.value = AuthUiState(
                loading = false,
                deviceName = store.deviceName(),
                crossfadeSeconds = store.crossfadeSeconds(),
                palette = store.palette(),
                playerLayout = store.playerLayout(),
                coverGradient = store.coverGradient(),
                eqEnabled = store.eqEnabled(),
                eqGains = store.eqGains(),
                downloadLimitMb = store.downloadLimitMb(),
            )
            // Arquivos baixados continuam no aparelho; o índice volta a popular o estado.
            refreshDownloads()
        }
    }

    fun clearFeedback() {
        _state.update { it.copy(error = null, message = null, searchError = null) }
    }

    // ------------------------------------------------------------------ downloads

    fun toggleDownload(track: Track) {
        if (track.id in _state.value.downloadedIds || downloadStore.entry(track.id) != null) {
            removeDownload(track.id)
        } else {
            startDownload(track)
        }
    }

    fun startDownload(track: Track) {
        if (downloadStore.entry(track.id) != null || track.id in _state.value.downloadedIds) {
            _state.update { it.copy(message = "Esta faixa já está baixada.") }
            return
        }
        if (downloadJobs[track.id]?.isActive == true) return
        launchDownload(track)
    }

    fun cancelDownload(trackId: String) {
        if (activeDownloadId == trackId) downloader.cancelCurrent()
        downloadJobs[trackId]?.cancel()
    }

    fun removeDownload(trackId: String) {
        val job = downloadJobs[trackId]
        if (activeDownloadId == trackId) downloader.cancelCurrent()
        job?.cancel()
        viewModelScope.launch {
            job?.cancelAndJoin()
            val removed = withContext(Dispatchers.IO) { downloadStore.remove(trackId) }
            refreshDownloads()
            if (removed != null) {
                _state.update { it.copy(message = "Download removido.") }
            }
        }
    }

    /** Baixa as faixas ainda não baixadas de uma playlist, em fila. */
    fun downloadPlaylist(tracks: List<Track>) {
        val pending = tracks.filterNot {
            it.id in _state.value.downloadedIds ||
                downloadStore.entry(it.id) != null ||
                downloadJobs[it.id]?.isActive == true
        }
        if (pending.isEmpty()) {
            _state.update { it.copy(message = "Todas as faixas desta playlist já estão baixadas.") }
            return
        }
        playlistDownloadJob?.cancel()
        playlistDownloadJob = viewModelScope.launch {
            _state.update { it.copy(message = "Baixando ${pending.size} faixas…") }
            var done = 0
            var failures = 0
            coroutineScope {
                for (track in pending) {
                    if (!isActive) break
                    val job = async { runDownload(track) }
                    downloadJobs[track.id] = job
                    val ok = runCatching { job.await() }.getOrDefault(false)
                    if (ok) {
                        done++
                    } else {
                        failures++
                        if (!isOnline()) break
                    }
                    if (failures >= 3) {
                        _state.update {
                            it.copy(error = "Parei a fila de downloads após várias falhas. Verifique a conexão.")
                        }
                        break
                    }
                }
            }
            playlistDownloadJob = null
            _state.update { it.copy(message = "Playlist: $done de ${pending.size} faixas baixadas.") }
        }
    }

    fun clearDownloads() {
        val playlistJob = playlistDownloadJob
        playlistDownloadJob = null
        val jobs = downloadJobs.values.toList()
        downloadJobs.clear()
        downloader.cancelCurrent()
        playlistJob?.cancel()
        jobs.forEach { it.cancel() }
        viewModelScope.launch {
            playlistJob?.cancelAndJoin()
            jobs.forEach { it.cancelAndJoin() }
            val count = withContext(Dispatchers.IO) { downloadStore.clearAll() }
            _state.update { it.copy(downloadProgress = emptyMap()) }
            refreshDownloads()
            _state.update {
                it.copy(message = if (count > 0) "$count downloads apagados." else "Nenhum download para apagar.")
            }
        }
    }

    fun setDownloadLimitMb(mb: Int) {
        val value = mb.coerceIn(128, 20_480)
        store.setDownloadLimitMb(value)
        _state.update { it.copy(downloadLimitMb = value) }
        if (_state.value.downloadUsedBytes > value.toLong() * BYTES_PER_MB) {
            _state.update { it.copy(message = "Limite abaixo do espaço usado. Apague downloads se precisar.") }
        }
    }

    private fun launchDownload(track: Track): Job {
        val job = viewModelScope.launch { runDownload(track) }
        downloadJobs[track.id] = job
        return job
    }

    private suspend fun refreshDownloads() {
        val entries = withContext(Dispatchers.IO) { downloadStore.all() }
        _state.update {
            it.copy(
                downloads = entries,
                downloadedIds = entries.mapTo(HashSet()) { entry -> entry.track.id },
            )
        }
    }

    private suspend fun runDownload(track: Track): Boolean =
        downloadMutex.withLock { runDownloadLocked(track) }

    /** Um download por vez: o saldo do limite não é lido por duas conexões ao mesmo tempo. */
    private suspend fun runDownloadLocked(track: Track): Boolean {
        val self = coroutineContext[Job] ?: return false
        if (downloadStore.entry(track.id) != null || track.id in _state.value.downloadedIds) {
            _state.update { it.copy(message = "Esta faixa já está baixada.") }
            return false
        }
        if (downloadJobs[track.id]?.isActive == true && downloadJobs[track.id] !== self) return false
        val limitBytes = _state.value.downloadLimitMb.toLong() * BYTES_PER_MB
        val estimate = track.durationSec.coerceAtLeast(60).toLong() * ESTIMATED_AUDIO_BYTES_PER_SECOND
        if (downloadStore.usedBytes() + estimate > limitBytes) {
            _state.update {
                it.copy(error = "Limite de ${_state.value.downloadLimitMb} MB de downloads atingido. Ajuste em Ajustes › Downloads.")
            }
            return false
        }
        if (!isOnline()) {
            _state.update { it.copy(error = "Sem conexão para baixar esta faixa.") }
            return false
        }
        downloadJobs[track.id] = self
        _state.update { it.copy(downloadProgress = it.downloadProgress + (track.id to 0f)) }
        try {
            var outcome = attemptDownload(track, self)
            if ((outcome as? DownloadResult.Failed)?.expired == true) {
                // URL expirada (401/403): descarta o stream em cache e reextrai uma vez.
                withContext(Dispatchers.IO) { youtube.forgetStream(track.id) }
                outcome = attemptDownload(track, self)
            }
            return when (outcome) {
                is DownloadResult.Success -> {
                    refreshDownloads()
                    _state.update { it.copy(message = "Baixada: ${track.title}") }
                    true
                }
                is DownloadResult.Failed -> {
                    _state.update {
                        it.copy(
                            error = if (outcome.expired) {
                                "O link do áudio expirou. Tente baixar de novo."
                            } else {
                                outcome.reason
                            },
                        )
                    }
                    false
                }
                DownloadResult.Cancelled -> false
            }
        } finally {
            if (downloadJobs[track.id] === self) downloadJobs.remove(track.id)
            _state.update { it.copy(downloadProgress = it.downloadProgress - track.id) }
        }
    }

    private suspend fun attemptDownload(track: Track, job: Job): DownloadResult {
        val media = withContext(Dispatchers.IO) { youtube.media(track.id) } as? Media.Remote
            ?: return DownloadResult.Failed("Não foi possível obter o áudio desta faixa.")
        var lastUpdate = 0L
        activeDownloadId = track.id
        return try {
            withContext(Dispatchers.IO) {
                downloader.download(
                    track = track,
                    url = media.url,
                    headers = media.headers,
                    onProgress = { fraction ->
                        val now = System.currentTimeMillis()
                        if (now - lastUpdate >= 150 || fraction < 0f) {
                            lastUpdate = now
                            _state.update { it.copy(downloadProgress = it.downloadProgress + (track.id to fraction)) }
                        }
                    },
                    isCancelled = { !job.isActive },
                )
            }
        } finally {
            if (activeDownloadId == track.id) activeDownloadId = null
        }
    }

    private fun formatCacheTime(ms: Long): String =
        java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm")
            .format(Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()))

    override fun onCleared() {
        runCatching { finishEvent(completed = false, skipped = true) }
        runCatching { resetRadio() }
        runCatching { cancelCrossfade() }
        val current = _state.value
        current.track?.let { track ->
            store.setPlaybackSession(current.queue, current.queueIndex, current.positionMs)
        }
        lyricsJob?.cancel()
        sleepJob?.cancel()
        recapJob?.cancel()
        artistJob?.cancel()
        collectionJob?.cancel()
        PlaybackService.stop(getApplication())
        PlaybackBridge.clear()
        equalizer.releaseAll()
        playerA.release()
        playerB.release()
        youtube.close()
        api.close()
        super.onCleared()
    }

    private companion object {
        const val BYTES_PER_MB = 1024L * 1024L

        /** Estimativa ~128 kbps para checar o limite antes de começar o download. */
        const val ESTIMATED_AUDIO_BYTES_PER_SECOND = 16_000L
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
