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
import com.openfy.android.data.CachedPlaylist
import com.openfy.android.data.DownloadEngine
import com.openfy.android.data.DownloadEntry
import com.openfy.android.data.DownloadResult
import com.openfy.android.data.HistoryEntry
import com.openfy.android.data.HistoryStore
import com.openfy.android.data.HomeCache
import com.openfy.android.data.HomeSnapshot
import com.openfy.android.data.LyricLine
import com.openfy.android.data.LyricsClient
import com.openfy.android.data.PendingEvent
import com.openfy.android.data.PendingItem
import com.openfy.android.data.PendingPlaylist
import com.openfy.android.data.PlaylistCache
import com.openfy.android.data.MonthlyArtistRow
import com.openfy.android.data.MonthlyListeningRow
import com.openfy.android.data.PlaylistDto
import com.openfy.android.data.Profile
import com.openfy.android.data.PushPlayEvent
import com.openfy.android.data.PushPlaybackState
import com.openfy.android.data.PushPlaylist
import com.openfy.android.data.PushPlaylistItem
import com.openfy.android.data.PlaybackStateDto
import com.openfy.android.data.RemotePlaylistItem
import com.openfy.android.data.RemoteTrack
import com.openfy.android.data.SessionStore
import com.openfy.android.data.SpotifyClient
import com.openfy.android.data.StoredSession
import com.openfy.android.data.SupabaseApi
import com.openfy.android.data.SupabaseException
import com.openfy.android.data.SyncStore
import com.openfy.android.data.SyncedSettings
import com.openfy.android.playback.EqualizerController
import com.openfy.android.playback.PlaybackBridge
import com.openfy.android.playback.PlaybackService
import com.openfy.core.ColorPalette
import com.openfy.core.Artist
import com.openfy.core.ArtistPage
import com.openfy.core.ExploreFeed
import com.openfy.core.Importer
import com.openfy.core.MediaCard
import com.openfy.core.MediaKind
import com.openfy.core.PlayerLayout
import com.openfy.core.ProfileArtistStat
import com.openfy.core.ProfileExtras
import com.openfy.core.ProfilePin
import com.openfy.core.ProfileStatsCards
import com.openfy.core.ProfileStatsData
import com.openfy.core.ProfileTrackStat
import com.openfy.core.QueueLogic
import com.openfy.core.RemoteCollection
import com.openfy.core.RepeatMode
import com.openfy.core.SearchMode
import com.openfy.core.Track
import com.openfy.data.Media
import com.openfy.data.YouTubeEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
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

enum class LibraryPage(val title: String) {
    HISTORY("Histórico"),
    TOP_PLAYED("Mais ouvidas"),
    EXPLORE("Explorar"),
    DOWNLOADS("Downloads"),
    MIX("Seu mix"),
}

/** Snapshot da fila antes de "limpar próximas", para desfazer. */
data class QueueUndoState(val queue: List<Track>, val index: Int)

data class AuthUiState(
    val loading: Boolean = true,
    val busy: Boolean = false,
    val session: StoredSession? = null,
    val profile: Profile? = null,
    val profileExtras: ProfileExtras = ProfileExtras(),
    val profileStats: ProfileStatsData? = null,
    val profileStatsLoading: Boolean = false,
    val profileBusy: Boolean = false,
    val profileStatus: String? = null,
    val playlists: List<PlaylistDto> = emptyList(),
    val deviceName: String = "",
    val error: String? = null,
    val message: String? = null,
    val searchQuery: String = "",
    val searching: Boolean = false,
    val searchResults: List<Track> = emptyList(),
    val searchArtists: List<Artist> = emptyList(),
    val searchCards: List<MediaCard> = emptyList(),
    val searchError: String? = null,
    val searchMode: SearchMode = SearchMode.SONGS,
    val searchHasMore: Boolean = false,
    val searchLoadingMore: Boolean = false,
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
    val shuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val palette: ColorPalette = ColorPalette.OPENFY,
    val playerLayout: PlayerLayout = PlayerLayout.CENTER,
    val coverGradient: Boolean = true,
    val recentTracks: List<Track> = emptyList(),
    val topTracks: List<Track> = emptyList(),
    val history: List<Track> = emptyList(),
    val topPlayed: List<Track> = emptyList(),
    val libraryPage: LibraryPage? = null,
    val exploreFeed: ExploreFeed? = null,
    val charts: List<Track> = emptyList(),
    val exploreLoading: Boolean = false,
    val exploreError: String? = null,
    val mixLoading: Boolean = false,
    val audioQuality: String = "HIGH",
    val autoDownload: Boolean = false,
    val playbackRemote: PlaybackStateDto? = null,
    val settingsSyncedAt: Long = 0,
    val appVersion: String = com.openfy.android.BuildConfig.VERSION_NAME,
    val updateInfo: com.openfy.android.data.AppUpdater.UpdateInfo? = null,
    val updateChecking: Boolean = false,
    val updateDownloading: Float? = null,
    val queueUndo: QueueUndoState? = null,
    val pendingSyncCount: Int = 0,
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
    val hasNext: Boolean
        get() = queueIndex >= 0 && queue.isNotEmpty() &&
            (queueIndex < queue.lastIndex || repeatMode == RepeatMode.ALL)
    val downloadUsedBytes: Long get() = downloads.sumOf { it.sizeBytes }
}

@UnstableApi
class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val store = SessionStore(application)
    private val api = SupabaseApi()
    private val youtube = YouTubeEngine()
    private val homeCache = HomeCache(File(application.filesDir, "offline"))
    private val playlistCache = PlaylistCache(File(application.filesDir, "offline"))
    private val syncStore = SyncStore(File(application.filesDir, "offline"))
    private val historyStore = HistoryStore(File(application.filesDir, "offline"))
    private val downloadStore = DownloadEngine.storeFor(application)

    private val _state = MutableStateFlow(
        AuthUiState(
            deviceName = store.deviceName(),
            crossfadeSeconds = store.crossfadeSeconds(),
            shuffle = store.shuffle(),
            repeatMode = store.repeatMode(),
            palette = store.palette(),
            playerLayout = store.playerLayout(),
            coverGradient = store.coverGradient(),
            recentTracks = historyStore.recentTracks(24).ifEmpty { store.recentTracks() },
            history = historyStore.historyTracks(200),
            topPlayed = historyStore.topPlayed(emptySet(), 100),
            eqEnabled = store.eqEnabled(),
            eqGains = store.eqGains(),
            downloadLimitMb = store.downloadLimitMb(),
            audioQuality = store.audioQuality(),
            autoDownload = store.autoDownload(),
            settingsSyncedAt = store.settingsUpdatedAt(),
            pendingSyncCount = syncStore.pendingCount(),
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
                    if (!isPlaying) {
                        // Troca de faixa: o player antigo reporta pausa antes do
                        // novo começar. Não deixa a UI/notificação piscarem
                        // "pausado" com o áudio novo a caminho.
                        val now = System.currentTimeMillis()
                        if (_state.value.busy || (playRequested && now < suppressPauseUntil)) return
                    }
                    _state.update { it.copy(playing = isPlaying) }
                    requestPlaybackPush()
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
                    val ended = _state.value
                    if (ended.repeatMode == RepeatMode.ONE && ended.track != null) {
                        play(
                            ended.track,
                            ended.queue,
                            eventSource,
                            keepRadio = true,
                            queueIndex = ended.queueIndex,
                        )
                    } else {
                        next()
                    }
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
    private var searchContinuation: String? = null
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
    private var shuffleSnapshotQueue: List<Track>? =
        store.shuffleSnapshotQueue().takeIf { it.isNotEmpty() }
    private var shuffleSnapshotIndex = store.shuffleSnapshotIndex()
    private var radioContinuation: String? = null
    private var radioLoading = false
    private var radioSeed: Track? = null
    private var playlistContextId: String? = null
    private var radioJob: Job? = null
    private var radioEpoch = 0
    private val homeMutex = Mutex()
    private var lastHomeLoad = 0L
    private var playJob: Job? = null
    private var playRequested = true
    private var suppressPauseUntil = 0L
    private var radioAdvancePending = false
    private var lyricsJob: Job? = null
    private var sleepJob: Job? = null
    private var recapJob: Job? = null
    private val lyricsCache = mutableMapOf<String, List<LyricLine>>()
    private var lastSessionSave = 0L
    private var lastSavedTrack = ""
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private var prefsSaveJob: Job? = null
    private var settingsSaveJob: Job? = null
    private var playbackPushJob: Job? = null
    private var playlistSyncJob: Job? = null
    private var profileStatsJob: Job? = null
    private var prefsActivityId: String? = null
    private var playlistDownloadJob: Job? = null

    init {
        activePlayer = playerA
        DownloadEngine.init(getApplication())
        DownloadEngine.setLimitBytes(_state.value.downloadLimitMb.toLong() * BYTES_PER_MB)
        viewModelScope.launch {
            DownloadEngine.progress.collect { progress ->
                _state.update { it.copy(downloadProgress = progress) }
            }
        }
        viewModelScope.launch {
            DownloadEngine.events.collect { event ->
                when (val result = event.result) {
                    is DownloadResult.Success -> {
                        refreshDownloads()
                        _state.update { it.copy(message = "Baixada: ${event.track.title}") }
                    }
                    is DownloadResult.Failed -> {
                        _state.update {
                            it.copy(
                                error = if (result.expired) {
                                    "O link do áudio expirou. Tente baixar de novo."
                                } else {
                                    result.reason
                                },
                            )
                        }
                    }
                    DownloadResult.Cancelled -> Unit
                }
            }
        }
        viewModelScope.launch {
            delay(4_000)
            if (isOnline()) checkForUpdates(silent = true)
        }
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
        PlaybackBridge.onSeek = { position -> seekTo(position) }
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
                    current.positionMs,
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
                        shuffleSnapshotQueue?.let { snapshot ->
                            store.setShuffleSnapshot(snapshot, shuffleSnapshotIndex)
                        }
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
        val result = try {
            Result.success(refreshSession(current))
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Exception) {
            Result.failure(error)
        }
        val refreshed = result.getOrNull()
        if (refreshed != null) return refreshed
        // Null com sucesso = o servidor rejeitou o refresh (credencial inválida).
        // Falha de rede/servidor não pode deslogar: mantém a sessão para o cache offline.
        if (result.isSuccess) {
            store.clear()
            homeCache.clear()
            playlistCache.clear()
            _state.update {
                it.copy(
                    session = null,
                    profile = null,
                    profileExtras = ProfileExtras(),
                    profileStats = null,
                    profileStatus = null,
                    playlists = emptyList(),
                    likedIds = emptySet(),
                    likedTracks = emptyList(),
                    topTracks = emptyList(),
                    recentTracks = emptyList(),
                    openPlaylist = null,
                    playlistTracks = emptyList(),
                    playlistItems = emptyList(),
                    offline = false,
                    offlineSavedAtMs = 0,
                    downloadProgress = emptyMap(),
                )
            }
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
                profileExtras = withContext(Dispatchers.IO) {
                    store.profilePrefs(userId)
                } ?: it.profileExtras,
                profileStats = withContext(Dispatchers.IO) {
                    store.profileStats(userId)
                } ?: it.profileStats,
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
        val remoteItems = runCatching {
            api.allPlaylistItems(session.accessToken, playlistIds)
        }.getOrDefault(emptyList())
        val remoteLikes = runCatching { api.likeRows(session.accessToken) }
            .getOrDefault(emptyList())

        // O que ainda não subiu vence o remoto; o flush tenta logo abaixo.
        runCatching { flushOutbox(session) }
        val pendingPlaylists = syncStore.pendingPlaylists().associateBy { it.id }
        val pendingItems = syncStore.pendingItems()
            .associateBy { it.playlistId to it.trackId }
        val pendingLikes = syncStore.pendingLikes().associateBy { it.track.id }

        val trackIds = (
            remoteItems.filter { !it.deleted }.map { it.trackId } +
                pendingItems.values.filter { !it.deleted }.map { it.trackId } +
                remoteLikes.filter { !it.deleted }.map { it.trackId } +
                pendingLikes.values.filter { it.liked }.map { it.track.id }
            ).distinct()
        val trackMap = trackIds.chunked(80)
            .flatMap { api.tracks(session.accessToken, it) }
            .associateTo(mutableMapOf()) { it.id to it.toTrack() }
        historyStore.all().forEach { trackMap.putIfAbsent(it.track.id, it.track) }
        pendingItems.values.forEach { item -> item.track?.let { trackMap[it.id] = it } }
        pendingLikes.values.forEach { like -> trackMap[like.track.id] = like.track }

        val mergedPlaylists = mergePlaylists(playlists, pendingPlaylists)
        val mergedItems = mergeItems(remoteItems, pendingItems)
        val mergedLikes = mergeLikes(remoteLikes, pendingLikes, trackMap)

        val itemsByPlaylist = mergedItems.groupBy { it.playlistId }
        withContext(Dispatchers.IO) {
            mergedPlaylists.forEach { playlist ->
                val items = itemsByPlaylist[playlist.id].orEmpty()
                val tracks = items.mapNotNull { trackMap[it.trackId] }
                if (items.isNotEmpty() || playlistCache.get(playlist.id) != null) {
                    playlistCache.put(
                        CachedPlaylist(
                            id = playlist.id,
                            tracks = tracks,
                            items = items,
                            savedAtMs = System.currentTimeMillis(),
                        ),
                    )
                }
            }
        }

        pullPlayEvents(session, trackMap)
        val likedSet = mergedLikes.mapTo(HashSet()) { it.id }
        val topPlayed = historyStore.topPlayed(likedSet, 100)
        val history = historyStore.historyTracks(200)
        val recent = historyStore.recentTracks(24)
        val fallbackTop = if (topPlayed.isEmpty()) loadTopTracks(session) else null
        loadProfileExtras(session)
        pullSettings(session)
        pullPlaybackState(session)
        if (_state.value.session?.userId != session.userId) return
        _state.update {
            it.copy(
                loading = false,
                busy = false,
                profile = profile,
                playlists = mergedPlaylists,
                likedIds = likedSet,
                likedTracks = mergedLikes,
                topTracks = topPlayed.ifEmpty { fallbackTop ?: it.topTracks },
                topPlayed = topPlayed,
                history = history,
                recentTracks = recent.ifEmpty { it.recentTracks },
                deviceName = store.deviceName(),
                crossfadeSeconds = store.crossfadeSeconds(),
                audioQuality = store.audioQuality(),
                autoDownload = store.autoDownload(),
                offline = false,
                offlineSavedAtMs = 0,
                error = null,
                pendingSyncCount = syncStore.pendingCount(),
            )
        }
        store.setRecentTracks(recent)
        lastHomeLoad = System.currentTimeMillis()
        saveCurrentSnapshot()
        if (store.autoDownload()) runAutoDownload()
        val open = _state.value.openPlaylist
        if (open != null) {
            val fresh = mergedPlaylists.firstOrNull { it.id == open.id }
            if (fresh == null) {
                closePlaylist()
            } else {
                val items = itemsByPlaylist[fresh.id].orEmpty()
                val tracks = items.mapNotNull { trackMap[it.trackId] }
                _state.update { current ->
                    if (current.openPlaylist?.id != fresh.id) {
                        current
                    } else {
                        current.copy(
                            openPlaylist = fresh,
                            playlistTracks = tracks,
                            playlistItems = items,
                            playlistLoading = false,
                        )
                    }
                }
            }
        }
    }

    /** Junta playlists remotas com criações/renomeações/exclusões ainda na fila. */
    private fun mergePlaylists(
        remote: List<PlaylistDto>,
        pending: Map<String, PendingPlaylist>,
    ): List<PlaylistDto> {
        val out = LinkedHashMap<String, PlaylistDto>()
        remote.filter { !it.deleted }.forEach { out[it.id] = it }
        pending.values.forEach { entry ->
            if (entry.deleted) {
                out.remove(entry.id)
            } else {
                out[entry.id] = PlaylistDto(
                    id = entry.id,
                    name = entry.name,
                    updatedAt = Instant.ofEpochMilli(entry.updatedAt).toString(),
                    coverUrl = entry.coverUrl,
                    deleted = false,
                )
            }
        }
        return out.values.toList()
    }

    /** Aplica inclusões, remoções e posições que ainda estão no outbox. */
    private fun mergeItems(
        remote: List<RemotePlaylistItem>,
        pending: Map<Pair<String, String>, PendingItem>,
    ): List<RemotePlaylistItem> {
        val out = LinkedHashMap<Pair<String, String>, RemotePlaylistItem>()
        remote.filter { !it.deleted }.forEach { out[it.playlistId to it.trackId] = it }
        pending.values.forEach { entry ->
            val key = entry.playlistId to entry.trackId
            if (entry.deleted) {
                out.remove(key)
            } else {
                out[key] = RemotePlaylistItem(
                    id = entry.itemId ?: deterministicItemId(entry.playlistId, entry.trackId),
                    playlistId = entry.playlistId,
                    trackId = entry.trackId,
                    rank = rankFor(entry.position),
                    deleted = false,
                    updatedAt = Instant.ofEpochMilli(entry.updatedAt).toString(),
                )
            }
        }
        return out.values.sortedWith(compareBy({ it.playlistId }, { it.rank }, { it.id }))
    }

    private fun mergeLikes(
        remote: List<com.openfy.android.data.RemoteLikeDto>,
        pending: Map<String, com.openfy.android.data.PendingLike>,
        tracks: Map<String, Track>,
    ): List<Track> {
        val out = LinkedHashMap<String, Track>()
        remote.filter { !it.deleted }.forEach { row ->
            tracks[row.trackId]?.let { out[row.trackId] = it }
        }
        pending.values.forEach { like ->
            if (like.liked) out[like.track.id] = like.track else out.remove(like.track.id)
        }
        return out.values.toList()
    }

    /** Envia o outbox em ordem: faixas, playlists, itens, curtidas e escutas. */
    private suspend fun flushOutbox(session: StoredSession) {
        val token = session.accessToken
        val events = syncStore.pendingEvents()
        val likes = syncStore.pendingLikes()
        val playlists = syncStore.pendingPlaylists()
        val items = syncStore.pendingItems()

        val tracks = (
            events.map { it.track } +
                likes.map { it.track } +
                items.mapNotNull { it.track }
            ).distinctBy { it.id }
        if (tracks.isNotEmpty()) api.pushTracks(token, tracks)

        if (playlists.isNotEmpty()) {
            api.upsertPlaylists(
                token,
                playlists.map { PushPlaylist(it.id, it.name, it.coverUrl, it.deleted) },
            )
            syncStore.removePlaylists(playlists.map { it.id })
        }

        if (items.isNotEmpty()) {
            val pushed = items.map { item ->
                val itemId = item.itemId ?: deterministicItemId(item.playlistId, item.trackId)
                PushPlaylistItem(
                    id = itemId,
                    playlistId = item.playlistId,
                    trackId = item.trackId,
                    rank = rankFor(item.position),
                    deleted = item.deleted,
                )
            }
            api.upsertPlaylistItems(token, pushed)
            syncStore.removeItems(items.map { it.playlistId to it.trackId })
        }

        if (likes.isNotEmpty()) {
            likes.forEach { api.setLike(token, session.userId, it.track, it.liked) }
            syncStore.removeLikes(likes.map { it.track.id })
        }

        if (events.isNotEmpty()) {
            events.forEach { event ->
                api.pushPlayEvent(
                    token,
                    PushPlayEvent(
                        id = event.id,
                        userId = session.userId,
                        deviceId = store.deviceId(),
                        trackId = event.track.id,
                        startedAt = Instant.ofEpochMilli(event.startedAtMs).toString(),
                        playedMs = event.playedMs,
                        durationMs = event.durationMs,
                        completed = event.completed,
                        skipped = event.skipped,
                        source = event.source,
                        contextId = event.contextId,
                    ),
                )
            }
            syncStore.removeEvents(events.map { it.id })
        }
        _state.update { it.copy(pendingSyncCount = syncStore.pendingCount()) }
    }

    /**
     * Puxa as escutas da conta. O cursor é `updated_at`, então eventos que o
     * PC enviou depois (ou que este celular mandou offline) entram na próxima
     * página mesmo com started_at antigo.
     */
    private suspend fun pullPlayEvents(
        session: StoredSession,
        tracks: MutableMap<String, Track>,
    ) {
        val cursor = syncStore.eventCursor()
        val rows = runCatching {
            api.playEvents(session.accessToken, cursor, limit = 1000)
        }.getOrNull() ?: return
        if (rows.isEmpty()) return
        val missing = rows.filter { !it.deleted && it.trackId !in tracks }
            .map { it.trackId }
            .distinct()
        if (missing.isNotEmpty()) {
            runCatching {
                missing.chunked(80)
                    .flatMap { api.tracks(session.accessToken, it) }
                    .forEach { tracks[it.id] = it.toTrack() }
            }
        }
        val deleted = rows.filter { it.deleted }.map { it.id }.filter { it.isNotBlank() }
        if (deleted.isNotEmpty()) historyStore.remove(deleted)
        val entries = rows.filter { !it.deleted }.mapNotNull { row ->
            val track = tracks[row.trackId] ?: return@mapNotNull null
            HistoryEntry(
                id = row.id.ifBlank { "evt:${row.trackId}:${row.startedAt}" },
                track = track,
                at = parseTimestamp(row.startedAt) ?: System.currentTimeMillis(),
                playedMs = row.playedMs,
                durationMs = row.durationMs,
                completed = row.completed,
                skipped = row.skipped,
                source = row.source.ifBlank { "android" },
            )
        }
        historyStore.merge(entries)
        rows.mapNotNull { it.updatedAt }.maxOrNull()?.let { syncStore.setEventCursor(it) }
    }

    /** Snapshot usado quando o app abre sem rede. */
    private fun saveCurrentSnapshot() {
        val current = _state.value
        val userId = current.session?.userId ?: return
        val snapshot = HomeSnapshot(
            userId = userId,
            savedAtMs = System.currentTimeMillis(),
            profile = current.profile,
            playlists = current.playlists,
            likedIds = current.likedIds.toList(),
            likedTracks = current.likedTracks,
            topTracks = current.topPlayed.ifEmpty { current.topTracks },
            recentTracks = current.recentTracks,
        )
        viewModelScope.launch(Dispatchers.IO) { homeCache.save(snapshot) }
    }

    private fun deterministicItemId(playlistId: String, trackId: String): String =
        java.util.UUID.nameUUIDFromBytes(
            "openfy:$playlistId:$trackId".toByteArray(Charsets.UTF_8),
        ).toString()

    private fun rankFor(position: Int): String =
        String.format(java.util.Locale.ROOT, "%08d", position)

    private fun parseTimestamp(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return runCatching { Instant.parse(value).toEpochMilli() }
            .recoverCatching { java.time.OffsetDateTime.parse(value).toInstant().toEpochMilli() }
            .getOrNull()
    }

    // ------------------------------------------------------------------ ajustes/fila da conta

    private val settingsJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /**
     * Ajustes convergem como no desktop: o maior updatedAt vence. Quando este
     * aparelho é o mais novo, envia; quando o PC mudou algo, aplica aqui.
     */
    private suspend fun pullSettings(session: StoredSession) {
        val remote = runCatching { api.settingsPrefs(session.accessToken, session.userId) }.getOrNull()
        val remoteSettings = remote?.let { activity ->
            runCatching {
                settingsJson.decodeFromJsonElement<SyncedSettings>(activity.payload)
            }.getOrNull()
        }
        val remoteUpdated = remoteSettings?.updatedAt
            ?: parseTimestamp(remote?.createdAt)
            ?: 0L
        val localUpdated = store.settingsUpdatedAt()
        if (remoteSettings != null && remoteUpdated > localUpdated) {
            applySettings(remoteSettings)
            store.setSettingsUpdatedAt(remoteUpdated)
            store.setSettingsActivityId(remote?.id)
            _state.update { it.copy(settingsSyncedAt = remoteUpdated) }
        } else if (localUpdated > remoteUpdated || remote == null) {
            runCatching { pushSettingsNow(session) }
        }
    }

    private fun currentSettings(): SyncedSettings {
        val current = _state.value
        return SyncedSettings(
            palette = current.palette.id,
            playerLayout = current.playerLayout.id,
            coverGradient = current.coverGradient,
            autoDownload = current.autoDownload,
            audioQuality = current.audioQuality,
            crossfadeSeconds = current.crossfadeSeconds,
            eqEnabled = current.eqEnabled,
            eqGains = current.eqGains,
            autoplayRadio = true,
            updatedAt = store.settingsUpdatedAt(),
        )
    }

    private fun applySettings(settings: SyncedSettings) {
        val palette = ColorPalette.fromId(settings.palette)
        val layout = PlayerLayout.fromId(settings.playerLayout)
        val quality = if (settings.audioQuality == "LOW") "LOW" else "HIGH"
        val crossfade = settings.crossfadeSeconds.coerceIn(0, 15)
        val gains = settings.eqGains.takeIf { it.size == 5 } ?: listOf(0, 0, 0, 0, 0)
        store.setPalette(palette)
        store.setPlayerLayout(layout)
        store.setCoverGradient(settings.coverGradient)
        store.setAutoDownload(settings.autoDownload)
        store.setAudioQuality(quality)
        store.setCrossfadeSeconds(crossfade)
        store.setEqEnabled(settings.eqEnabled)
        store.setEqGains(gains)
        crossfadeSeconds = crossfade
        youtube.setQuality(quality)
        equalizer.setEnabled(settings.eqEnabled)
        gains.forEachIndexed { index, gain -> equalizer.setGain(index, gain) }
        _state.update {
            it.copy(
                palette = palette,
                playerLayout = layout,
                coverGradient = settings.coverGradient,
                autoDownload = settings.autoDownload,
                audioQuality = quality,
                crossfadeSeconds = crossfade,
                eqEnabled = settings.eqEnabled,
                eqGains = gains,
                settingsSyncedAt = settings.updatedAt,
            )
        }
        if (settings.autoDownload) runAutoDownload()
    }

    private suspend fun pushSettingsNow(session: StoredSession) {
        val payload = currentSettings().copy(updatedAt = System.currentTimeMillis())
        val previous = store.settingsActivityId()
        val json = settingsJson.encodeToJsonElement(payload)
        val id = api.saveSettingsPrefs(session.accessToken, session.userId, json)
        if (!previous.isNullOrBlank() && previous != id) {
            runCatching { api.deleteActivity(session.accessToken, previous) }
        }
        store.setSettingsActivityId(id)
        store.setSettingsUpdatedAt(payload.updatedAt)
        _state.update { it.copy(settingsSyncedAt = payload.updatedAt) }
    }

    /** Mudança local de ajuste: marca o relógio e envia com debounce. */
    private fun markSettingsChanged() {
        val now = System.currentTimeMillis()
        store.setSettingsUpdatedAt(now)
        _state.update { it.copy(settingsSyncedAt = now) }
        requestSettingsPush()
    }

    private fun requestSettingsPush() {
        if (_state.value.session == null) return
        settingsSaveJob?.cancel()
        settingsSaveJob = viewModelScope.launch {
            delay(4_000)
            runCatching { withSession { session -> pushSettingsNow(session) } }
        }
    }

    /** Puxa a fila do outro aparelho e devolve a deste para a conta. */
    private suspend fun pullPlaybackState(session: StoredSession) {
        val remote = runCatching { api.playbackState(session.accessToken) }.getOrNull() ?: return
        val localDevice = store.deviceId()
        val remoteMs = parseTimestamp(remote.updatedAt) ?: 0L
        val lastPush = store.playbackLastPushMs()
        val foreign = !remote.deviceId.isNullOrBlank() && remote.deviceId != localDevice
        val show = foreign && remoteMs > lastPush && remote.queue.isNotEmpty()
        _state.update { it.copy(playbackRemote = if (show) remote else null) }
        if (!show) pushPlaybackState(session)
    }

    private suspend fun pushPlaybackState(session: StoredSession) {
        val current = _state.value
        val track = current.track ?: return
        if (current.queue.isEmpty()) return
        val signature = listOf(
            track.id,
            current.queueIndex.toString(),
            current.queue.joinToString("|") { it.id },
            current.shuffle.toString(),
            current.repeatMode.name,
            current.playing.toString(),
        ).joinToString(";")
        if (signature == store.playbackSignature() && !current.playing) return
        api.pushPlaybackState(
            session.accessToken,
            PushPlaybackState(
                userId = session.userId,
                deviceId = store.deviceId(),
                trackId = track.id,
                queue = current.queue,
                queueIndex = current.queueIndex,
                positionMs = current.positionMs.coerceAtLeast(0L).toInt(),
                playing = current.playing,
                shuffle = current.shuffle,
                repeat = current.repeatMode.name,
            ),
        )
        store.setPlaybackSignature(signature)
        store.setPlaybackLastPushMs(System.currentTimeMillis())
    }

    /** Envia só a fila/posição, com debounce, depois de uma mudança no player. */
    private fun requestPlaybackPush() {
        if (_state.value.session == null) return
        playbackPushJob?.cancel()
        playbackPushJob = viewModelScope.launch {
            delay(2_500)
            runCatching { withSession { session -> pushPlaybackState(session) } }
        }
    }

    /** Descarta o convite de handoff sem tocar nada. */
    fun dismissRemotePlayback() {
        _state.update { it.copy(playbackRemote = null) }
    }

    /** Adota fila, faixa e posição que estavam no desktop. */
    fun continueOnOtherDevice() {
        val remote = _state.value.playbackRemote ?: return
        if (remote.queue.isEmpty()) {
            dismissRemotePlayback()
            return
        }
        val index = remote.queueIndex.coerceIn(0, remote.queue.lastIndex)
        val track = remote.queue[index]
        play(
            track = track,
            queue = remote.queue,
            source = "handoff",
            queueIndex = index,
            startAtMs = remote.positionMs.toLong(),
        )
        if (remote.shuffle && !_state.value.shuffle) toggleShuffle()
        if (remote.repeat != _state.value.repeatMode.name) {
            val mode = runCatching { RepeatMode.valueOf(remote.repeat) }.getOrDefault(RepeatMode.OFF)
            store.setRepeatMode(mode)
            _state.update { it.copy(repeatMode = mode) }
        }
        dismissRemotePlayback()
        _state.update { it.copy(message = "Retomando do outro aparelho.") }
    }

    /** Dispara o outbox sem bloquear a UI. */
    private fun requestFlush() {
        val session = _state.value.session ?: return
        viewModelScope.launch {
            runCatching { flushOutbox(session) }
            _state.update { it.copy(pendingSyncCount = syncStore.pendingCount()) }
        }
    }

    private suspend fun loadTopTracks(session: StoredSession): List<Track>? = runCatching {
        val month = YearMonth.now().atDay(1).toString()
        val rows = api.monthlyTopTracks(session.accessToken, month, 20)
        val byId = api.tracks(session.accessToken, rows.map { it.trackId }).associateBy { it.id }
        rows.mapNotNull { byId[it.trackId]?.toTrack() }
    }.getOrNull()

    // ------------------------------------------------------------------ profile

    /**
     * Carrega o snapshot privado do perfil. O cache local vence quando tem
     * `updatedAt` maior (edição offline); o remoto vence caso contrário.
     */
    private suspend fun loadProfileExtras(session: StoredSession) {
        val local = withContext(Dispatchers.IO) { store.profilePrefs(session.userId) }
        val remote = runCatching { api.profilePrefs(session.accessToken, session.userId) }.getOrNull()
        val remoteExtras = remote?.payload?.let { payload ->
            runCatching { json.decodeFromJsonElement<ProfileExtras>(payload) }.getOrNull()
        }
        if (_state.value.session?.userId != session.userId) return
        prefsActivityId = remote?.id
        val chosen = when {
            remoteExtras == null -> local ?: ProfileExtras()
            local == null -> remoteExtras
            local.updatedAt > remoteExtras.updatedAt -> local
            else -> remoteExtras
        }.sanitized()
        withContext(Dispatchers.IO) { store.setProfilePrefs(session.userId, chosen) }
        val cachedStats = withContext(Dispatchers.IO) { store.profileStats(session.userId) }
        _state.update {
            it.copy(
                profileExtras = chosen,
                profileStats = it.profileStats ?: cachedStats,
            )
        }
        if (local != null && local.updatedAt > (remoteExtras?.updatedAt ?: 0L)) {
            requestPrefsSave()
        }
    }

    /** Bio, cor, pins e cartões: salva local na hora e sincroniza com debounce. */
    fun updateProfileExtras(transform: (ProfileExtras) -> ProfileExtras) {
        val userId = _state.value.session?.userId
        val next = transform(_state.value.profileExtras)
            .copy(updatedAt = System.currentTimeMillis())
            .sanitized()
        _state.update { it.copy(profileExtras = next) }
        if (userId != null) store.setProfilePrefs(userId, next)
        requestPrefsSave()
    }

    private fun requestPrefsSave() {
        if (_state.value.session == null) return
        prefsSaveJob?.cancel()
        prefsSaveJob = viewModelScope.launch {
            delay(900)
            val session = _state.value.session ?: return@launch
            val extras = _state.value.profileExtras
            runCatching {
                withSession { active -> savePrefsRemote(active, extras) }
            }.onSuccess {
                if (_state.value.session?.userId == session.userId) {
                    _state.update { it.copy(profileStatus = "Perfil sincronizado.") }
                }
            }.onFailure {
                if (_state.value.session?.userId == session.userId) {
                    _state.update {
                        it.copy(
                            profileStatus = "Perfil salvo neste aparelho; sincroniza quando a rede voltar.",
                        )
                    }
                }
            }
        }
    }

    private suspend fun savePrefsRemote(session: StoredSession, extras: ProfileExtras) {
        val payload = json.encodeToJsonElement(extras.sanitized())
        val newId = api.saveProfilePrefs(session.accessToken, session.userId, payload)
        val oldId = prefsActivityId
        prefsActivityId = newId
        store.setProfilePrefs(session.userId, extras)
        if (oldId != null && oldId != newId) {
            runCatching { api.deleteActivity(session.accessToken, oldId) }
        }
    }

    /** Atualiza nome, avatar, capa e preferências do perfil. */
    fun saveProfile(
        displayName: String,
        extras: ProfileExtras,
        avatarUri: Uri? = null,
        coverUri: Uri? = null,
    ) {
        val session = _state.value.session ?: return
        if (_state.value.profileBusy) return
        _state.update {
            it.copy(profileBusy = true, profileStatus = "Salvando perfil…", error = null)
        }
        viewModelScope.launch {
            val cleanName = displayName.trim().take(60).ifBlank {
                _state.value.profile?.displayName.orEmpty()
            }
            var avatarUrl = _state.value.profile?.avatarUrl.orEmpty()
            var coverUrl = extras.coverUrl
            val result = runCatching {
                withSession { active ->
                    avatarUri?.let { uri ->
                        val (bytes, mime, ext) = readImage(uri)
                        avatarUrl = api.uploadProfileImage(
                            active.accessToken,
                            active.userId,
                            "avatars",
                            "avatar-${System.currentTimeMillis()}",
                            bytes,
                            mime,
                            ext,
                        )
                    }
                    coverUri?.let { uri ->
                        val (bytes, mime, ext) = readImage(uri)
                        coverUrl = api.uploadProfileImage(
                            active.accessToken,
                            active.userId,
                            "covers",
                            "profile-${System.currentTimeMillis()}",
                            bytes,
                            mime,
                            ext,
                        )
                    }
                    api.updateProfile(active.accessToken, active.userId, cleanName, avatarUrl)
                    val next = extras.copy(
                        coverUrl = coverUrl,
                        updatedAt = System.currentTimeMillis(),
                    ).sanitized()
                    savePrefsRemote(active, next)
                    api.profile(active.accessToken, active.userId) to next
                }
            }
            result.onSuccess { (profile, next) ->
                store.setProfilePrefs(session.userId, next)
                _state.update {
                    it.copy(
                        profile = profile ?: it.profile,
                        profileExtras = next,
                        profileBusy = false,
                        profileStatus = "Perfil atualizado.",
                    )
                }
            }.onFailure { error ->
                val next = extras.copy(
                    coverUrl = coverUrl,
                    updatedAt = System.currentTimeMillis(),
                ).sanitized()
                store.setProfilePrefs(session.userId, next)
                _state.update {
                    it.copy(
                        profileExtras = next,
                        profileBusy = false,
                        profileStatus = null,
                        error = error.message ?: "Não foi possível salvar o perfil.",
                    )
                }
            }
        }
    }

    private suspend fun readImage(uri: Uri): Triple<ByteArray, String, String> =
        withContext(Dispatchers.IO) {
            val resolver = getApplication<Application>().contentResolver
            val mime = resolver.getType(uri)?.lowercase() ?: "image/jpeg"
            val extension = when (mime) {
                "image/png" -> "png"
                "image/webp" -> "webp"
                "image/gif" -> "gif"
                else -> "jpg"
            }
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalArgumentException("Não foi possível ler a imagem escolhida.")
            require(bytes.isNotEmpty()) { "A imagem escolhida está vazia." }
            require(bytes.size <= 8 * 1024 * 1024) { "A imagem deve ter no máximo 8 MB." }
            Triple(bytes, mime, extension)
        }

    fun addProfilePin(track: Track) {
        updateProfileExtras { extras ->
            if (extras.pins.size >= ProfileExtras.PIN_LIMIT) {
                extras
            } else {
                extras.copy(
                    pins = (extras.pins + ProfilePin.from(track))
                        .distinctBy { it.id }
                        .take(ProfileExtras.PIN_LIMIT),
                )
            }
        }
    }

    fun removeProfilePin(id: String) {
        updateProfileExtras { extras ->
            extras.copy(pins = extras.pins.filterNot { it.id == id })
        }
    }

    fun moveProfilePin(id: String, delta: Int) {
        updateProfileExtras { extras ->
            val list = extras.pins.toMutableList()
            val from = list.indexOfFirst { it.id == id }
            val to = (from + delta).coerceIn(0, (list.size - 1).coerceAtLeast(0))
            if (from < 0 || from == to) {
                extras
            } else {
                list.add(to, list.removeAt(from))
                extras.copy(pins = list)
            }
        }
    }

    fun moveProfileCard(id: String, delta: Int) {
        updateProfileExtras { extras ->
            val order = ProfileStatsCards.sanitizeOrder(extras.statsOrder).toMutableList()
            val from = order.indexOf(id)
            val to = (from + delta).coerceIn(0, (order.size - 1).coerceAtLeast(0))
            if (from < 0 || from == to) {
                extras
            } else {
                order.add(to, order.removeAt(from))
                extras.copy(statsOrder = order)
            }
        }
    }

    fun toggleProfileCard(id: String, visible: Boolean) {
        updateProfileExtras { extras ->
            val hidden = if (visible) {
                extras.statsHidden - id
            } else {
                (extras.statsHidden + id).distinct()
            }
            extras.copy(statsHidden = hidden)
        }
    }

    /** Estatísticas do mês corrente, cache para abrir offline. */
    fun loadProfileStats(force: Boolean = false) {
        val session = _state.value.session ?: return
        if (_state.value.profileStatsLoading) return
        if (!force && _state.value.profileStats != null) return
        profileStatsJob?.cancel()
        _state.update { it.copy(profileStatsLoading = true) }
        profileStatsJob = viewModelScope.launch {
            val result = runCatching {
                withSession { active ->
                    val month = YearMonth.now().atDay(1).toString()
                    val listening = api.monthlyListening(active.accessToken, month)
                    val artists = api.monthlyTopArtists(active.accessToken, month, 8)
                    val topRows = api.monthlyTopTracks(active.accessToken, month, 5)
                    val byId = if (topRows.isEmpty()) {
                        emptyMap()
                    } else {
                        api.tracks(active.accessToken, topRows.map { it.trackId })
                            .associateBy { it.id }
                    }
                    val likes = api.likes(active.accessToken).size
                    val playlists = (
                        api.ownedPlaylistIds(active.accessToken, active.userId) +
                            api.memberPlaylistIds(active.accessToken, active.userId)
                        ).toSet().size
                    ProfileStatsData(
                        month = month,
                        minutes = (listening?.playedMs ?: 0L) / 60_000L,
                        plays = listening?.plays ?: 0L,
                        uniqueTracks = listening?.uniqueTracks ?: 0L,
                        likes = likes,
                        playlists = playlists,
                        topArtists = artists.map { ProfileArtistStat(it.artist, it.plays) },
                        topTracks = topRows.mapNotNull { row ->
                            byId[row.trackId]?.let { remote ->
                                ProfileTrackStat(
                                    Track(
                                        id = remote.id,
                                        title = remote.title,
                                        artist = remote.artist,
                                        album = remote.album,
                                        durationSec = remote.duration,
                                        thumbnail = remote.thumbnail,
                                    ),
                                    row.plays,
                                )
                            }
                        },
                        updatedAt = System.currentTimeMillis(),
                    )
                }
            }
            result.onSuccess { stats ->
                withContext(Dispatchers.IO) { store.setProfileStats(session.userId, stats) }
                if (_state.value.session?.userId == session.userId) {
                    _state.update {
                        it.copy(profileStats = stats, profileStatsLoading = false)
                    }
                }
            }.onFailure {
                val cached = withContext(Dispatchers.IO) { store.profileStats(session.userId) }
                if (_state.value.session?.userId == session.userId) {
                    _state.update {
                        it.copy(
                            profileStats = cached ?: it.profileStats,
                            profileStatsLoading = false,
                        )
                    }
                }
            }
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
            if (_state.value.error == null) {
                _state.update { it.copy(message = "Biblioteca sincronizada.") }
            }
        }
    }

    fun createPlaylist(name: String, track: Track? = null) {
        val clean = name.trim()
        if (clean.isBlank()) return
        if (_state.value.session == null) return
        val now = System.currentTimeMillis()
        val id = java.util.UUID.randomUUID().toString()
        val playlist = PlaylistDto(
            id = id,
            name = clean,
            updatedAt = Instant.ofEpochMilli(now).toString(),
            coverUrl = "",
            deleted = false,
        )
        syncStore.queuePlaylist(id, clean, updatedAt = now)
        if (track != null) {
            syncStore.queueItem(
                playlistId = id,
                trackId = track.id,
                itemId = deterministicItemId(id, track.id),
                position = 0,
                updatedAt = now,
                track = track,
            )
        }
        _state.update {
            it.copy(
                playlists = it.playlists + playlist,
                pendingSyncCount = syncStore.pendingCount(),
                message = if (track != null) {
                    "\"${track.title}\" adicionada a \"$clean\"."
                } else {
                    "Playlist \"$clean\" criada."
                },
                error = null,
            )
        }
        if (track != null) {
            cacheNewPlaylist(playlist, listOf(track))
        }
        if (isOnline()) requestFlush() else _state.update {
            it.copy(message = "Playlist salva no aparelho; sincroniza quando voltar a rede.")
        }
    }

    /** Atualiza o cache de uma playlist recém-criada para abrir sem rede. */
    private fun cacheNewPlaylist(playlist: PlaylistDto, tracks: List<Track>) {
        val item = RemotePlaylistItem(
            id = deterministicItemId(playlist.id, tracks.first().id),
            playlistId = playlist.id,
            trackId = tracks.first().id,
            rank = rankFor(0),
            deleted = false,
            updatedAt = playlist.updatedAt,
        )
        viewModelScope.launch(Dispatchers.IO) {
            playlistCache.put(
                CachedPlaylist(
                    id = playlist.id,
                    tracks = tracks,
                    items = listOf(item),
                    savedAtMs = System.currentTimeMillis(),
                ),
            )
        }
    }

    // ------------------------------------------------------------------ search
    fun search(query: String) {
        val clean = query.trim()
        _state.update { it.copy(searchQuery = clean, searchError = null) }
        searchJob?.cancel()
        searchContinuation = null
        if (clean.isBlank()) {
            _state.update {
                it.copy(
                    searchResults = emptyList(),
                    searchArtists = emptyList(),
                    searchCards = emptyList(),
                    searching = false,
                    searchHasMore = false,
                )
            }
            return
        }
        val mode = _state.value.searchMode
        searchJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    searching = true,
                    searchResults = emptyList(),
                    searchArtists = emptyList(),
                    searchCards = emptyList(),
                    searchHasMore = false,
                )
            }
            delay(300)
            runCatching { youtube.search(clean, mode) }
                .onSuccess { result ->
                    searchContinuation = result.continuation
                    _state.update {
                        it.copy(
                            searching = false,
                            searchResults = result.tracks,
                            searchArtists = result.artists,
                            searchCards = result.cards,
                            searchHasMore = result.continuation != null,
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

    /** Troca a aba da busca (músicas, artistas, álbuns, playlists, vídeos). */
    fun setSearchMode(mode: SearchMode) {
        if (_state.value.searchMode == mode) return
        _state.update { it.copy(searchMode = mode) }
        if (_state.value.searchQuery.isNotBlank()) search(_state.value.searchQuery)
    }

    /** Carrega a próxima página da busca atual. */
    fun loadMoreSearch() {
        val query = _state.value.searchQuery
        val continuation = searchContinuation ?: return
        if (query.isBlank() || _state.value.searchLoadingMore) return
        val mode = _state.value.searchMode
        searchJob = viewModelScope.launch {
            _state.update { it.copy(searchLoadingMore = true, searchError = null) }
            runCatching { youtube.searchNext(mode, continuation) }
                .onSuccess { page ->
                    searchContinuation = page.continuation
                    _state.update {
                        it.copy(
                            searching = false,
                            searchLoadingMore = false,
                            searchResults = (it.searchResults + page.tracks).distinctBy { row -> row.id },
                            searchArtists = (it.searchArtists + page.artists).distinctBy { row -> row.id },
                            searchCards = (it.searchCards + page.cards).distinctBy { row -> row.id },
                            searchHasMore = page.continuation != null,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            searchLoadingMore = false,
                            searchError = error.message ?: "Não foi possível carregar mais.",
                        )
                    }
                }
        }
    }

    // ------------------------------------------------------------------ biblioteca

    fun openLibraryPage(page: LibraryPage) {
        _state.update { it.copy(libraryPage = page, exploreError = null) }
        if (page == LibraryPage.EXPLORE && _state.value.exploreFeed == null) loadExplore()
        if (page == LibraryPage.DOWNLOADS) viewModelScope.launch { refreshDownloads() }
    }

    fun closeLibraryPage() {
        _state.update { it.copy(libraryPage = null) }
    }

    /** Explorar + Em alta, com cache no estado para não buscar duas vezes. */
    fun loadExplore(force: Boolean = false) {
        if (_state.value.exploreLoading) return
        if (!force && _state.value.exploreFeed != null && _state.value.charts.isNotEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(exploreLoading = true, exploreError = null) }
            val feed = runCatching { withContext(Dispatchers.IO) { youtube.explore() } }.getOrNull()
            val charts = runCatching { withContext(Dispatchers.IO) { youtube.charts() } }
                .getOrDefault(emptyList())
            _state.update {
                it.copy(
                    exploreLoading = false,
                    exploreFeed = feed ?: it.exploreFeed,
                    charts = charts.ifEmpty { it.charts },
                    exploreError = if (feed == null && it.exploreFeed == null) {
                        "Não foi possível carregar o Explorar."
                    } else {
                        null
                    },
                )
            }
        }
    }

    /**
     * "Seu mix": junta mais ouvidas, curtidas e recentes e intercala com
     * diversidade — tudo local, então funciona offline.
     */
    fun startPersonalMix() {
        val current = _state.value
        val top = current.topPlayed.ifEmpty { current.topTracks }
        val liked = current.likedTracks
        val recent = current.recentTracks
        val pages = listOf(top.take(40), liked.take(30), recent.take(30))
            .filter { it.isNotEmpty() }
        if (pages.isEmpty()) {
            _state.update { it.copy(error = "Ouça algumas faixas para o mix aprender seu gosto.") }
            return
        }
        _state.update { it.copy(mixLoading = true) }
        val recommender = com.openfy.core.Recommender(
            signals = historyStore.signals(),
            likedIds = current.likedIds,
        )
        val mix = recommender.interleave(pages, limit = 60)
        _state.update { it.copy(mixLoading = false) }
        val first = mix.firstOrNull() ?: return
        play(first, mix, "mix")
        _state.update { it.copy(message = "Seu mix: ${mix.size} faixas.") }
    }

    /** Remove as próximas faixas, mantendo a atual e guardando para desfazer. */
    fun clearUpcoming() {
        val current = _state.value
        val index = current.queueIndex
        if (index !in current.queue.indices || index == current.queue.lastIndex) {
            _state.update { it.copy(message = "Não há próximas faixas para limpar.") }
            return
        }
        val keep = current.queue.take(index + 1)
        _state.update {
            it.copy(
                queue = keep,
                queueUndo = QueueUndoState(current.queue, index),
                message = "Próximas removidas.",
            )
        }
        requestPlaybackPush()
    }

    fun undoClearUpcoming() {
        val undo = _state.value.queueUndo ?: return
        _state.update {
            it.copy(
                queue = undo.queue,
                queueIndex = undo.index.coerceIn(0, undo.queue.lastIndex.coerceAtLeast(0)),
                queueUndo = null,
                message = "Fila restaurada.",
            )
        }
        requestPlaybackPush()
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

    private fun startRadio(seed: Track, playlistId: String? = null, advanceAtEnd: Boolean = false) {
        resetRadio()
        radioSeed = seed
        radioAdvancePending = advanceAtEnd
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
            val stillCurrent = _state.value.track?.id == seed.id
            if (page == null || page.tracks.isEmpty() || !stillCurrent) {
                if (advanceAtEnd && stillCurrent) {
                    runCatching { player.pause() }
                    _state.update { it.copy(playing = false) }
                }
                return@launch
            }
            radioContinuation = page.continuation
            appendRadio(page.tracks.take(60), seed)
        }
    }

    /** Rádio a partir da faixa atual (botão do player). */
    fun startTrackRadio() {
        val track = _state.value.track ?: return
        startRadio(track, null)
        _state.update { it.copy(message = "Rádio a partir de ${track.title}.") }
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
        playlistContextId = playlistId
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
        val remoteItems = api.playlistItems(session.accessToken, playlistId)
        val pending = syncStore.pendingItems()
            .filter { it.playlistId == playlistId }
            .associateBy { it.playlistId to it.trackId }
        val items = mergeItems(remoteItems, pending)
        val ids = items.map { it.trackId }.distinct()
        val remoteTracks = ids.chunked(80).flatMap { api.tracks(session.accessToken, it) }
        val byId = remoteTracks.associateBy { it.id }.toMutableMap()
        pending.values.forEach { item -> item.track?.let { byId.putIfAbsent(it.id, it.toRemoteTrack()) } }
        val tracks = items.sortedBy { it.rank }.mapNotNull { item -> byId[item.trackId]?.toTrack() }
        return tracks to items
    }

    private fun Track.toRemoteTrack(): RemoteTrack = RemoteTrack(
        id = id,
        title = title,
        artist = artist,
        album = album,
        thumbnail = thumbnail,
        duration = durationSec,
    )

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
                    val pending = syncStore.pendingItems()
                        .filter { it.playlistId == playlist.id }
                        .associateBy { it.playlistId to it.trackId }
                    val items = mergeItems(cached.items, pending)
                    val byId = cached.tracks.associateBy { it.id }.toMutableMap()
                    pending.values.forEach { item -> item.track?.let { byId.putIfAbsent(it.id, it) } }
                    val tracks = items.sortedBy { it.rank }.mapNotNull { row -> byId[row.trackId] }
                    _state.update {
                        if (it.openPlaylist?.id != playlist.id) {
                            it
                        } else {
                            it.copy(
                                playlistTracks = tracks,
                                playlistItems = items,
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
        playlistContextId = _state.value.openPlaylist?.id
        play(first, tracks, "playlist")
    }

    fun playPlaylistTrack(track: Track) {
        val tracks = _state.value.playlistTracks
        if (tracks.isEmpty()) return
        playlistContextId = _state.value.openPlaylist?.id
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

    /** Fila avulsa para perfil/coleções que não têm tela própria no player. */
    fun playTracks(tracks: List<Track>, index: Int, source: String = "profile") {
        if (index !in tracks.indices) return
        play(tracks[index], tracks, source, queueIndex = index)
    }

    /** Histórico local inteiro vira fila, como em "Ouvir de novo" no desktop. */
    fun playHistory(track: Track) {
        val tracks = _state.value.history
        val queue = if (tracks.any { it.id == track.id }) tracks else listOf(track)
        play(track, queue, "history")
    }

    /** Mais ouvidas locais (com fallback para as do mês na conta). */
    fun playTopPlayed(track: Track) {
        val tracks = _state.value.topPlayed.ifEmpty { _state.value.topTracks }
        val queue = if (tracks.any { it.id == track.id }) tracks else listOf(track)
        play(track, queue, "top")
    }

    fun playChart(track: Track) {
        val tracks = _state.value.charts
        val queue = if (tracks.any { it.id == track.id }) tracks else listOf(track)
        play(track, queue, "charts")
    }

    /** Faixa já baixada: toca do arquivo local, sem radio/rede. */
    fun playDownloaded(entry: DownloadEntry) {
        val tracks = _state.value.downloads.map { it.track }
        val queue = if (tracks.any { it.id == entry.track.id }) tracks else listOf(entry.track)
        play(entry.track, queue, "downloads")
    }

    /** Abre um card de prateleira (álbum/playlist/artista/vídeo) na tela certa. */
    fun openMediaCard(card: MediaCard) {
        when (card.kind) {
            MediaKind.ARTIST -> openArtistById(card.id)
            else -> openCollection(card.id)
        }
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
        // Curtida otimista: entra no outbox mesmo sem rede e sobe no próximo flush.
        syncStore.queueLike(track, liked)
        _state.update {
            it.copy(
                pendingSyncCount = syncStore.pendingCount(),
                topPlayed = historyStore.topPlayed(
                    if (liked) it.likedIds + track.id else it.likedIds - track.id,
                    100,
                ),
            )
        }
        if (!isOnline()) {
            _state.update {
                it.copy(message = if (liked) "Curtida salva; sincroniza quando voltar a rede." else "Curtida removida; sincroniza depois.")
            }
            return
        }
        requestFlush()
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
        val now = System.currentTimeMillis()
        val position = nextPlaylistPosition(playlist.id)
        syncStore.queueItem(
            playlistId = playlist.id,
            trackId = track.id,
            itemId = deterministicItemId(playlist.id, track.id),
            position = position,
            updatedAt = now,
            track = track,
        )
        _state.update {
            val isOpen = it.openPlaylist?.id == playlist.id
            val item = RemotePlaylistItem(
                id = deterministicItemId(playlist.id, track.id),
                playlistId = playlist.id,
                trackId = track.id,
                rank = rankFor(position),
                deleted = false,
                updatedAt = Instant.ofEpochMilli(now).toString(),
            )
            it.copy(
                playlistItems = if (isOpen) it.playlistItems + item else it.playlistItems,
                playlistTracks = if (isOpen && it.playlistTracks.none { row -> row.id == track.id }) {
                    it.playlistTracks + track
                } else {
                    it.playlistTracks
                },
                pendingSyncCount = syncStore.pendingCount(),
                message = "\"${track.title}\" adicionada a ${playlist.name}.",
                error = null,
            )
        }
        if (isOnline()) requestFlush() else _state.update {
            it.copy(message = "Adicionada offline; sincroniza depois.")
        }
    }

    private fun nextPlaylistPosition(playlistId: String): Int {
        val opened = _state.value.openPlaylist
        if (opened?.id == playlistId) return _state.value.playlistItems.size
        val cached = runCatching { playlistCache.get(playlistId) }.getOrNull()
        return cached?.items?.size ?: 0
    }

    fun renamePlaylist(playlistId: String, name: String) {
        val clean = name.trim()
        if (clean.isBlank()) return
        val current = _state.value.playlists.firstOrNull { it.id == playlistId } ?: return
        val now = System.currentTimeMillis()
        syncStore.queuePlaylist(
            id = playlistId,
            name = clean,
            coverUrl = current.coverUrl,
            updatedAt = now,
        )
        _state.update {
            it.copy(
                playlists = it.playlists.map { row ->
                    if (row.id == playlistId) {
                        row.copy(name = clean, updatedAt = Instant.ofEpochMilli(now).toString())
                    } else {
                        row
                    }
                },
                openPlaylist = it.openPlaylist?.let { row ->
                    if (row.id == playlistId) row.copy(name = clean) else row
                },
                pendingSyncCount = syncStore.pendingCount(),
                message = "Playlist renomeada.",
                error = null,
            )
        }
        if (isOnline()) requestFlush()
    }

    /** Exclusão otimista; tombstones impedem que o pull ressuscite a playlist. */
    fun deletePlaylist(playlistId: String) {
        val current = _state.value.playlists.firstOrNull { it.id == playlistId } ?: return
        val now = System.currentTimeMillis()
        syncStore.queuePlaylist(
            id = playlistId,
            name = current.name,
            coverUrl = current.coverUrl,
            updatedAt = now,
            deleted = true,
        )
        _state.update {
            it.copy(
                playlists = it.playlists.filterNot { row -> row.id == playlistId },
                openPlaylist = if (it.openPlaylist?.id == playlistId) null else it.openPlaylist,
                playlistTracks = if (it.openPlaylist?.id == playlistId) emptyList() else it.playlistTracks,
                playlistItems = if (it.openPlaylist?.id == playlistId) emptyList() else it.playlistItems,
                pendingSyncCount = syncStore.pendingCount(),
                message = "Playlist \"${current.name}\" excluída.",
                error = null,
            )
        }
        viewModelScope.launch(Dispatchers.IO) { playlistCache.remove(playlistId) }
        if (isOnline()) requestFlush() else _state.update {
            it.copy(message = "Exclusão salva no aparelho; sincroniza depois.")
        }
    }

    /** Move uma faixa uma posição para cima/baixo e reescreve os ranks. */
    fun movePlaylistTrack(item: RemotePlaylistItem, delta: Int) {
        val items = _state.value.playlistItems.sortedBy { it.rank }
        val index = items.indexOfFirst { it.id == item.id }
        if (index < 0) return
        movePlaylistTrackTo(item, index + delta)
    }

    fun movePlaylistTrackTo(item: RemotePlaylistItem, targetIndex: Int) {
        val current = _state.value
        if (current.openPlaylist?.id != item.playlistId) return
        val items = current.playlistItems.sortedBy { it.rank }.toMutableList()
        val index = items.indexOfFirst { it.id == item.id }
        if (index < 0) return
        val target = targetIndex.coerceIn(0, items.lastIndex)
        if (target == index) return
        val moved = items.removeAt(index)
        items.add(target, moved)
        val now = System.currentTimeMillis()
        val byTrack = current.playlistTracks.associateBy { it.id }
        items.forEachIndexed { position, row ->
            syncStore.queueItem(
                playlistId = row.playlistId,
                trackId = row.trackId,
                itemId = row.id,
                position = position,
                updatedAt = now,
                track = byTrack[row.trackId],
            )
        }
        val reordered = items.mapIndexed { position, row -> row.copy(rank = rankFor(position)) }
        _state.update {
            it.copy(
                playlistItems = reordered,
                playlistTracks = reordered.mapNotNull { row -> byTrack[row.trackId] },
                pendingSyncCount = syncStore.pendingCount(),
                message = "Ordem atualizada.",
                error = null,
            )
        }
        if (isOnline()) requestFlush()
    }

    /** Salva a fila atual como playlist (mesma ideia do "Salvar fila" do desktop). */
    fun saveQueueAsPlaylist(name: String) {
        val clean = name.trim()
        if (clean.isBlank()) return
        val tracks = _state.value.queue
        if (tracks.isEmpty()) {
            _state.update { it.copy(error = "A fila está vazia.") }
            return
        }
        if (_state.value.session == null) return
        val now = System.currentTimeMillis()
        val id = java.util.UUID.randomUUID().toString()
        syncStore.queuePlaylist(id, clean, updatedAt = now)
        tracks.forEachIndexed { position, track ->
            syncStore.queueItem(
                playlistId = id,
                trackId = track.id,
                itemId = deterministicItemId(id, track.id),
                position = position,
                updatedAt = now,
                track = track,
            )
        }
        val playlist = PlaylistDto(
            id = id,
            name = clean,
            updatedAt = Instant.ofEpochMilli(now).toString(),
        )
        _state.update {
            it.copy(
                playlists = it.playlists + playlist,
                pendingSyncCount = syncStore.pendingCount(),
                message = "Fila salva em \"$clean\" (${tracks.size} faixas).",
                error = null,
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            playlistCache.put(
                CachedPlaylist(
                    id = id,
                    tracks = tracks,
                    items = tracks.mapIndexed { position, track ->
                        RemotePlaylistItem(
                            id = deterministicItemId(id, track.id),
                            playlistId = id,
                            trackId = track.id,
                            rank = rankFor(position),
                            deleted = false,
                            updatedAt = playlist.updatedAt,
                        )
                    },
                    savedAtMs = now,
                ),
            )
        }
        if (isOnline()) requestFlush()
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
        val index = _state.value.playlistItems.indexOfFirst { it.id == item.id }
        val track = _state.value.playlistTracks.firstOrNull { it.id == item.trackId }
        syncStore.queueItem(
            playlistId = playlist.id,
            trackId = item.trackId,
            itemId = item.id,
            position = if (index >= 0) index else 0,
            updatedAt = System.currentTimeMillis(),
            deleted = true,
            track = track,
        )
        _state.update {
            it.copy(
                playlistItems = it.playlistItems.filterNot { entry -> entry.id == item.id },
                playlistTracks = it.playlistTracks.filterNot { row -> row.id == item.trackId },
                pendingSyncCount = syncStore.pendingCount(),
                message = "Faixa removida da playlist.",
                error = null,
            )
        }
        val cached = runCatching { playlistCache.get(playlist.id) }.getOrNull()
        if (cached != null) {
            viewModelScope.launch(Dispatchers.IO) {
                playlistCache.put(
                    cached.copy(
                        tracks = cached.tracks.filterNot { it.id == item.trackId },
                        items = cached.items.filterNot { it.id == item.id },
                        savedAtMs = System.currentTimeMillis(),
                    ),
                )
            }
        }
        if (isOnline()) requestFlush()
    }

    fun setCrossfadeSeconds(seconds: Int) {
        val value = seconds.coerceIn(0, 15)
        crossfadeSeconds = value
        store.setCrossfadeSeconds(value)
        _state.update { it.copy(crossfadeSeconds = value) }
        if (value == 0) cancelCrossfade()
        markSettingsChanged()
    }

    fun setPalette(value: ColorPalette) {
        store.setPalette(value)
        _state.update { it.copy(palette = value) }
        markSettingsChanged()
    }

    fun setPlayerLayout(value: PlayerLayout) {
        store.setPlayerLayout(value)
        _state.update { it.copy(playerLayout = value) }
        markSettingsChanged()
    }

    fun setCoverGradient(enabled: Boolean) {
        store.setCoverGradient(enabled)
        _state.update { it.copy(coverGradient = enabled) }
        markSettingsChanged()
    }

    fun setAudioQuality(value: String) {
        val quality = if (value == "LOW") "LOW" else "HIGH"
        store.setAudioQuality(quality)
        youtube.setQuality(quality)
        _state.update { it.copy(audioQuality = quality) }
        markSettingsChanged()
        _state.update {
            it.copy(message = if (quality == "LOW") "Qualidade baixa (economiza dados)." else "Qualidade alta.")
        }
    }

    fun setAutoDownload(enabled: Boolean) {
        store.setAutoDownload(enabled)
        _state.update { it.copy(autoDownload = enabled) }
        markSettingsChanged()
        if (enabled) {
            runAutoDownload()
        } else {
            _state.update { it.copy(message = "Download automático desligado.") }
        }
    }

    /**
     * Baixa em segundo plano as mais ouvidas e curtidas que ainda não estão
     * no aparelho, respeitando o limite de espaço.
     */
    private fun runAutoDownload() {
        if (!_state.value.autoDownload || !isOnline()) return
        val limitBytes = _state.value.downloadLimitMb.toLong() * BYTES_PER_MB
        val wanted = (_state.value.topPlayed + _state.value.likedTracks + _state.value.recentTracks)
            .distinctBy { it.id }
            .filterNot {
                it.id in _state.value.downloadedIds ||
                    downloadStore.entry(it.id) != null ||
                    DownloadEngine.isQueued(it.id)
            }
            .take(20)
        if (wanted.isEmpty()) return
        if (downloadStore.usedBytes() >= limitBytes) {
            _state.update { it.copy(message = "Limite de downloads atingido; ajuste em Ajustes.") }
            return
        }
        downloadPlaylist(wanted)
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
        markSettingsChanged()
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
        markSettingsChanged()
    }

    fun applyEqPreset(name: String) {
        val preset = EqualizerController.PRESETS[name] ?: return
        equalizer.applyPreset(name)
        store.setEqEnabled(true)
        store.setEqGains(preset)
        _state.update { it.copy(eqEnabled = true, eqGains = preset) }
        markSettingsChanged()
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
            it.copy(queue = it.queue + track, message = "No fim da fila: ${track.title}")
        }
        invalidatePreload()
    }

    /** Liga/desliga o aleatório guardando a ordem original para restaurar depois. */
    fun toggleShuffle() {
        val current = _state.value
        if (current.shuffle) {
            val snapshot = shuffleSnapshotQueue
            val (restored, restoredIndex) = if (snapshot != null) {
                QueueLogic.restored(snapshot, current.queue, current.track?.id)
            } else {
                current.queue to current.queueIndex
            }
            shuffleSnapshotQueue = null
            shuffleSnapshotIndex = -1
            store.setShuffle(false)
            store.clearShuffleSnapshot()
            _state.update {
                it.copy(
                    queue = restored,
                    queueIndex = if (restored.isEmpty()) -1 else restoredIndex.coerceIn(0, restored.lastIndex),
                    shuffle = false,
                    message = "Aleatório desligado.",
                )
            }
        } else {
            val (shuffled, shuffledIndex) = QueueLogic.shuffled(current.queue, current.queueIndex)
            shuffleSnapshotQueue = current.queue
            shuffleSnapshotIndex = current.queueIndex
            store.setShuffle(true)
            store.setShuffleSnapshot(current.queue, current.queueIndex)
            _state.update {
                it.copy(
                    queue = shuffled,
                    queueIndex = shuffledIndex,
                    shuffle = true,
                    message = "Aleatório ligado.",
                )
            }
        }
        cancelCrossfade()
        invalidatePreload()
        requestPlaybackPush()
    }

    /** Off -> repetir tudo -> repetir uma -> off. */
    fun cycleRepeat() {
        val next = when (_state.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        store.setRepeatMode(next)
        _state.update {
            it.copy(
                repeatMode = next,
                message = when (next) {
                    RepeatMode.OFF -> "Repetição desligada."
                    RepeatMode.ALL -> "Repetir: toda a fila."
                    RepeatMode.ONE -> "Repetir: uma faixa."
                },
            )
        }
        invalidatePreload()
        requestPlaybackPush()
    }

    /** Reordena a fila; se a faixa atual for movida, o índice acompanha. */
    fun moveQueueIndex(from: Int, to: Int) {
        val current = _state.value
        if (from !in current.queue.indices) return
        val queue = QueueLogic.moved(current.queue, from, to)
        if (queue === current.queue) return
        val trackIndex = current.track?.id?.let { id -> queue.indexOfFirst { it.id == id } } ?: -1
        val newIndex = if (trackIndex >= 0) trackIndex else current.queueIndex.coerceIn(0, queue.lastIndex)
        _state.update { it.copy(queue = queue, queueIndex = newIndex) }
        invalidatePreload()
        requestPlaybackPush()
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
        val removed = queue.removeAt(index)
        if (shuffleSnapshotQueue != null) {
            shuffleSnapshotQueue = shuffleSnapshotQueue?.filterNot { it.id == removed.id }
            store.setShuffleSnapshot(shuffleSnapshotQueue.orEmpty(), shuffleSnapshotIndex)
        }
        val newIndex = when {
            queue.isEmpty() -> -1
            index < current.queueIndex -> current.queueIndex - 1
            else -> current.queueIndex.coerceAtMost(queue.lastIndex)
        }
        _state.update { it.copy(queue = queue, queueIndex = newIndex) }
        invalidatePreload()
        requestPlaybackPush()
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
        startAtMs: Long = 0L,
    ) {
        val session = _state.value.session
        if (session == null) {
            _state.update { it.copy(error = "Entre na conta para tocar.") }
            return
        }
        if (source != "playlist") playlistContextId = null
        cancelCrossfade()
        playJob?.cancel()
        PlaybackService.ensureStarted(getApplication())
        if (!keepRadio) resetRadio()
        val current = _state.value
        finishEvent(completed = false, skipped = true)
        if (queue.isEmpty()) return
        val requestedIndex = (queueIndex ?: queue.indexOfFirst { it.id == track.id })
            .coerceIn(0, queue.lastIndex)
        val sameQueue = current.queue.size == queue.size &&
            current.queue.indices.all { current.queue[it].id == queue[it].id }
        val queueToUse: List<Track>
        val index: Int
        if (current.shuffle && !sameQueue) {
            // Nova fila com aleatório ligado: mantém a faixa escolhida e
            // embaralha o resto, guardando a ordem original.
            val selectedIndex = queue.indexOfFirst { it.id == track.id }
            val rest = if (selectedIndex >= 0) {
                queue.filterIndexed { i, _ -> i != selectedIndex }
            } else {
                queue
            }
            queueToUse = listOf(track) + rest.shuffled()
            index = 0
            shuffleSnapshotQueue = queue
            shuffleSnapshotIndex = requestedIndex
            store.setShuffleSnapshot(queue, requestedIndex)
        } else {
            queueToUse = queue
            index = requestedIndex
            if (current.shuffle && shuffleSnapshotQueue == null) {
                shuffleSnapshotQueue = queue
                shuffleSnapshotIndex = index
                store.setShuffleSnapshot(queue, index)
            }
        }
        eventStartedAt = System.currentTimeMillis()
        eventTrack = track
        eventSource = source
        playRequested = true
        suppressPauseUntil = System.currentTimeMillis() + 1_500
        _state.update {
            it.copy(
                track = track,
                queue = queueToUse,
                queueIndex = index,
                // Mantém "tocando" enquanto o áudio novo é preparado; sem isso
                // a notificação e o player mostravam pausa com som no ar.
                playing = true,
                busy = true,
                positionMs = startAtMs.coerceAtLeast(0L),
                durationMs = track.durationSec * 1000L,
                error = null,
            )
        }
        requestPlaybackPush()
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
                if (startAtMs > 0) activePlayer.seekTo(startAtMs)
                activePlayer.playWhenReady = playRequested
                _state.update { it.copy(busy = false) }
                rememberRecent(track)
                return@launch
            }
            val media = withContext(Dispatchers.IO) { youtube.media(track.id) } as? Media.Remote
            if (_state.value.track?.id != track.id) return@launch
            if (media == null) {
                // Nada foi preparado: silencia o player anterior para não ficar
                // tocando a faixa velha com a tela dizendo "nada tocando".
                runCatching {
                    activePlayer.stop()
                    activePlayer.clearMediaItems()
                }
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
            if (startAtMs > 0) activePlayer.seekTo(startAtMs)
            activePlayer.playWhenReady = playRequested
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
        if (s.repeatMode == RepeatMode.ONE || s.queue.size < 2) return
        val nextIndex = QueueLogic.autoNextIndex(s.queueIndex, s.queue.size, s.repeatMode) ?: return
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
                val expected = QueueLogic.autoNextIndex(now.queueIndex, now.queue.size, now.repeatMode)
                if (crossfading ||
                    now.track?.id != currentId ||
                    expected != nextIndex ||
                    now.queue.getOrNull(nextIndex)?.id != track.id
                ) {
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
        val current = _state.value
        val track = current.track ?: return
        PlaybackService.ensureStarted(getApplication())
        if (crossfading) cancelCrossfade()
        if (player.isPlaying) {
            playRequested = false
            player.pause()
            _state.update { it.copy(playing = false) }
        } else {
            playRequested = true
            val idle = player.playbackState == Player.STATE_IDLE || player.mediaItemCount == 0
            if (idle) {
                // Player sem mídia (erro/restauração): remonta a faixa atual.
                play(
                    track,
                    current.queue.ifEmpty { listOf(track) },
                    eventSource,
                    keepRadio = true,
                    queueIndex = current.queueIndex,
                    startAtMs = current.positionMs,
                )
            } else {
                player.play()
                _state.update { it.copy(playing = true) }
            }
        }
        requestPlaybackPush()
    }

    fun next() {
        cancelCrossfade()
        val current = _state.value
        val next = current.queueIndex + 1
        if (next !in current.queue.indices) {
            finishEvent(completed = true, skipped = false)
            if (current.repeatMode == RepeatMode.ALL && current.queue.isNotEmpty()) {
                play(current.queue[0], current.queue, eventSource, keepRadio = true, queueIndex = 0)
                return
            }
            if (radioContinuation != null) {
                radioAdvancePending = true
                maybeAppendRadio()
                return
            }
            // Fim da playlist: continua no embalo com uma rádio baseada nela,
            // em vez de parar o som.
            val seed = current.track ?: current.queue.lastOrNull()
            if (seed != null && current.queue.size > 1) {
                startRadio(seed, playlistContextId, advanceAtEnd = true)
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
        requestPlaybackPush()
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
        val eventId = java.util.UUID.randomUUID().toString()
        val durationMs = (track.durationSec * 1000).coerceAtLeast(0)
        val entry = HistoryEntry(
            id = eventId,
            track = track,
            at = startedAt,
            playedMs = played.toInt(),
            durationMs = durationMs,
            completed = completed,
            skipped = skipped,
            source = eventSource,
        )
        historyStore.add(entry)
        val history = historyStore.historyTracks(200)
        val recent = historyStore.recentTracks(24)
        val top = historyStore.topPlayed(_state.value.likedIds, 100)
        _state.update {
            it.copy(
                history = history,
                recentTracks = recent,
                topPlayed = top,
                topTracks = top.ifEmpty { it.topTracks },
            )
        }
        store.setRecentTracks(recent)
        // A escuta entra no outbox na hora: sem rede, sobe depois sem perder.
        syncStore.addEvent(
            PendingEvent(
                id = eventId,
                track = track,
                startedAtMs = startedAt,
                playedMs = played.toInt(),
                durationMs = durationMs,
                completed = completed,
                skipped = skipped,
                source = eventSource,
                contextId = null,
            ),
        )
        _state.update { it.copy(pendingSyncCount = syncStore.pendingCount()) }
        viewModelScope.launch {
            runCatching { flushOutbox(session) }
            _state.update { it.copy(pendingSyncCount = syncStore.pendingCount()) }
        }
    }
    fun signOut() {
        val session = _state.value.session
        viewModelScope.launch {
            finishEvent(completed = false, skipped = true)
            resetRadio()
            cancelCrossfade()
            playlistDownloadJob?.cancel()
            DownloadEngine.cancelAllAndWait()
            playerA.stop()
            playerB.stop()
            playerA.clearMediaItems()
            playerB.clearMediaItems()
            if (session != null) runCatching { flushOutbox(session) }
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
            syncStore.clear()
            historyStore.clear()
            searchContinuation = null
            shuffleSnapshotQueue = null
            shuffleSnapshotIndex = -1
            lastHomeLoad = 0L
            _state.value = AuthUiState(
                loading = false,
                deviceName = store.deviceName(),
                crossfadeSeconds = store.crossfadeSeconds(),
                shuffle = store.shuffle(),
                repeatMode = store.repeatMode(),
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

    // ---------------------------------------------------------------- atualizações

    fun checkForUpdates(silent: Boolean = false) {
        if (_state.value.updateChecking || _state.value.updateDownloading != null) return
        _state.update { it.copy(updateChecking = true) }
        viewModelScope.launch {
            val info = runCatching { com.openfy.android.data.AppUpdater.check() }.getOrNull()
            _state.update {
                it.copy(
                    updateChecking = false,
                    updateInfo = info,
                    message = when {
                        info != null -> "Atualização ${info.version} disponível."
                        silent -> it.message
                        else -> "Você já está na versão mais recente (${it.appVersion})."
                    },
                )
            }
        }
    }

    fun downloadAndInstallUpdate() {
        val info = _state.value.updateInfo ?: return
        if (_state.value.updateDownloading != null) return
        _state.update { it.copy(updateDownloading = 0f) }
        viewModelScope.launch {
            val result = runCatching {
                val file = com.openfy.android.data.AppUpdater.download(getApplication(), info) { progress ->
                    _state.update { it.copy(updateDownloading = progress) }
                }
                com.openfy.android.data.AppUpdater.install(getApplication(), file)
            }
            _state.update {
                it.copy(
                    updateDownloading = null,
                    message = if (result.isSuccess) "Instalador aberto: confirme para atualizar." else null,
                    error = result.exceptionOrNull()?.let { error ->
                        "Falha ao atualizar: ${error.message ?: error.javaClass.simpleName}"
                    },
                )
            }
        }
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
        if (DownloadEngine.isQueued(track.id)) {
            _state.update { it.copy(message = "Esta faixa já está na fila de downloads.") }
            return
        }
        if (!isOnline()) {
            _state.update { it.copy(error = "Sem conexão para baixar esta faixa.") }
            return
        }
        val limitBytes = _state.value.downloadLimitMb.toLong() * BYTES_PER_MB
        val estimate = track.durationSec.coerceAtLeast(60).toLong() * ESTIMATED_AUDIO_BYTES_PER_SECOND
        if (downloadStore.usedBytes() + estimate > limitBytes) {
            _state.update {
                it.copy(error = "Limite de ${_state.value.downloadLimitMb} MB de downloads atingido. Ajuste em Ajustes › Downloads.")
            }
            return
        }
        DownloadEngine.enqueue(track)
    }

    fun cancelDownload(trackId: String) {
        DownloadEngine.cancel(trackId)
    }

    fun removeDownload(trackId: String) {
        DownloadEngine.cancel(trackId)
        viewModelScope.launch {
            val removed = withContext(Dispatchers.IO) { downloadStore.remove(trackId) }
            refreshDownloads()
            if (removed != null) {
                _state.update { it.copy(message = "Download removido.") }
            }
        }
    }

    /** Baixa as faixas ainda não baixadas de uma playlist, em lotes paralelos. */
    fun downloadPlaylist(tracks: List<Track>) {
        val pending = tracks.filterNot {
            it.id in _state.value.downloadedIds ||
                downloadStore.entry(it.id) != null ||
                DownloadEngine.isQueued(it.id)
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
                for (batch in pending.chunked(DOWNLOAD_BATCH)) {
                    if (!isActive) break
                    val results = batch.map { track ->
                        async { runCatching { DownloadEngine.download(track) }.getOrNull() }
                    }.awaitAll()
                    if (!isActive) break
                    results.forEach { result ->
                        if (result is DownloadResult.Success) done++ else failures++
                    }
                    if (failures >= 3 || !isOnline()) {
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
        playlistJob?.cancel()
        viewModelScope.launch {
            runCatching { playlistJob?.cancelAndJoin() }
            DownloadEngine.cancelAllAndWait()
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
        DownloadEngine.setLimitBytes(value.toLong() * BYTES_PER_MB)
        _state.update { it.copy(downloadLimitMb = value) }
        if (_state.value.downloadUsedBytes > value.toLong() * BYTES_PER_MB) {
            _state.update { it.copy(message = "Limite abaixo do espaço usado. Apague downloads se precisar.") }
        }
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
            shuffleSnapshotQueue?.let { snapshot ->
                store.setShuffleSnapshot(snapshot, shuffleSnapshotIndex)
            }
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

        /** Faixas por lote; o DownloadEngine limita as conexões simultâneas. */
        const val DOWNLOAD_BATCH = 4

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
