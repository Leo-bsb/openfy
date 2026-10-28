package com.openfy.core

import com.openfy.data.Db
import com.openfy.data.Downloader
import com.openfy.data.LyricLine
import com.openfy.data.LyricsClient
import com.openfy.data.UpdateChecker
import com.openfy.data.UpdateStatus
import com.openfy.data.YouTubeEngine
import com.openfy.player.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.YearMonth
import java.time.ZoneId
import java.nio.file.Files
import java.nio.file.StandardCopyOption

sealed interface Screen {
    data object Home : Screen
    data object Search : Screen
    data object Explore : Screen
    data object Charts : Screen
    data object Liked : Screen
    data object History : Screen
    data object TopPlayed : Screen
    data object Recap : Screen
    data object Downloads : Screen
    data object Settings : Screen
    data class PlaylistView(val id: String) : Screen
    data class ArtistView(val id: String) : Screen
    data class RemoteCollection(val id: String) : Screen
}

/** Diálogo pendente da UI; o estado vive no controlador para não atravessar 4 camadas de lambdas. */
sealed interface PlaylistPrompt {
    data object Create : PlaylistPrompt
    data class Rename(val id: String, val current: String) : PlaylistPrompt
    data class Delete(val id: String, val name: String) : PlaylistPrompt
    data object SaveQueue : PlaylistPrompt
}

/** Operação de fila desfeita pelo botão "Desfazer" do toast. */
sealed interface QueueUndo {
    data class Remove(val track: Track, val index: Int) : QueueUndo
    data class ClearNext(val tracks: List<Track>) : QueueUndo
}

class AppController(
    private val db: Db,
    private val yt: YouTubeEngine,
    val player: Player,
    private val downloader: Downloader,
    private val scope: CoroutineScope,
) {

    val screen = MutableStateFlow<Screen>(Screen.Home)
    val searchQuery = MutableStateFlow("")
    val committedSearch = MutableStateFlow("")
    val searchMode = MutableStateFlow(SearchMode.SONGS)
    val results = MutableStateFlow<List<Track>>(emptyList())
    val searchArtists = MutableStateFlow<List<Artist>>(emptyList())
    val searchCards = MutableStateFlow<List<MediaCard>>(emptyList())
    val searching = MutableStateFlow(false)
    val searchError = MutableStateFlow<String?>(null)
    val searchLoadingMore = MutableStateFlow(false)
    val searchHasMore = MutableStateFlow(false)
    val recentSearches = MutableStateFlow<List<String>>(emptyList())
    val suggestions = MutableStateFlow<List<String>>(emptyList())

    val chartsTracks = MutableStateFlow<List<Track>>(emptyList())
    val chartsLoading = MutableStateFlow(false)
    val chartsError = MutableStateFlow<String?>(null)

    val exploreFeed = MutableStateFlow<ExploreFeed?>(null)
    val exploreLoading = MutableStateFlow(false)
    val exploreError = MutableStateFlow<String?>(null)

    val artistPage = MutableStateFlow<ArtistPage?>(null)
    val artistLoading = MutableStateFlow(false)
    val artistError = MutableStateFlow<String?>(null)

    val remoteCollection = MutableStateFlow<RemoteCollection?>(null)
    val remoteLoading = MutableStateFlow(false)
    val remoteError = MutableStateFlow<String?>(null)
    val remoteLoadingMore = MutableStateFlow(false)

    val playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val liked = MutableStateFlow<List<Track>>(emptyList())
    val recent = MutableStateFlow<List<Track>>(emptyList())
    val history = MutableStateFlow<List<Track>>(emptyList())

    val downloadedTracks = MutableStateFlow<List<Track>>(emptyList())
    val downloadedIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadingIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadsSize = MutableStateFlow(0L)
    val autoDownload = MutableStateFlow(false)
    val audioQuality = MutableStateFlow("HIGH")
    val closeToTray = MutableStateFlow(true)
    val palette = MutableStateFlow(ColorPalette.OPENFY)
    val playerLayout = MutableStateFlow(PlayerLayout.CENTER)
    val coverGradient = MutableStateFlow(true)

    // ------------------------------------------------------------------ updates
    val updateAutoCheck = MutableStateFlow(true)
    val updateStatus = MutableStateFlow<UpdateStatus?>(null)

    // ------------------------------------------------------------------ import / lyrics / stats
    val importDialogOpen = MutableStateFlow(false)
    val importBusy = MutableStateFlow(false)
    val importStatus = MutableStateFlow<String?>(null)

    val lyrics = MutableStateFlow<List<LyricLine>>(emptyList())
    val lyricsLoading = MutableStateFlow(false)
    val lyricsTrackId = MutableStateFlow<String?>(null)

    val sleepUntil = MutableStateFlow<Long?>(null)
    val stats = MutableStateFlow<Stats?>(null)
    val topPlayed = MutableStateFlow<List<Track>>(emptyList())
    val recapMonth = MutableStateFlow(YearMonth.now())
    val recapMonths = MutableStateFlow<List<YearMonth>>(emptyList())
    val recap = MutableStateFlow<RecapSnapshot?>(null)
    val recapLoading = MutableStateFlow(false)
    val recapError = MutableStateFlow<String?>(null)
    val recapFromAccount = MutableStateFlow(false)
    private var recapGeneration = 0
    private var lastRecapAuto = 0L

    data class Stats(
        val totalPlays: Int = 0,
        val totalSeconds: Long = 0,
        val topArtists: List<Pair<String, Int>> = emptyList(),
    )

    val openPlaylist = MutableStateFlow<Playlist?>(null)
    val openPlaylistTracks = MutableStateFlow<List<Track>>(emptyList())

    val message = MutableStateFlow<String?>(null)
    val playlistPrompt = MutableStateFlow<PlaylistPrompt?>(null)
    val queueUndo = MutableStateFlow<QueueUndo?>(null)

    /** Sem conexão (ou rede instável) detectada nas chamadas ao YouTube. */
    val offline = MutableStateFlow(false)

    // ------------------------------------------------------------------ navigation
    private val backStack = ArrayDeque<Screen>()
    private val forwardStack = ArrayDeque<Screen>()
    val canBack = MutableStateFlow(false)
    val canForward = MutableStateFlow(false)

    // ------------------------------------------------------------------ ui state (janela)
    val queueOpen = MutableStateFlow(false)
    val nowPlayingOpen = MutableStateFlow(false)
    val searchFieldFocused = MutableStateFlow(false)
    val searchFocusRequest = MutableStateFlow(0)

    private var searchGeneration = 0
    private var searchContinuation: String? = null
    private var suggestionGeneration = 0
    private var pendingAddTrack: Track? = null
    private var sleepJob: Job? = null
    private val backupJson = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }

    /** Conta Supabase + sync de playlists/curtidas com o Android. */
    val account = AccountManager(db, scope)
    private val recommender = Recommender(db)

    init {
        player.radioSource = { token -> yt.radioNext(token) }
        player.radioStarter = { id -> yt.radio(id, "RDAMVM$id") }
        account.onSynced = {
            refreshLibrary()
            openPlaylist.value?.id?.let { loadPlaylist(it) }
        }
        autoDownload.value = runCatching { db.get("download.auto")?.toBooleanStrict() }.getOrNull() ?: true
        audioQuality.value = db.get("audio.quality") ?: "HIGH"
        yt.setQuality(audioQuality.value)
        closeToTray.value = runCatching { db.get("window.closeToTray")?.toBooleanStrict() }.getOrNull() ?: true
        palette.value = ColorPalette.fromId(db.get("ui.palette"))
        playerLayout.value = PlayerLayout.fromId(db.get("ui.player.layout"))
        coverGradient.value = runCatching {
            db.get("ui.player.coverGradient")?.toBooleanStrict()
        }.getOrNull() ?: true
        updateAutoCheck.value = runCatching {
            db.get("update.autoCheck")?.toBooleanStrict()
        }.getOrNull() ?: true
        scope.launch { autoUpdateCheck() }
        scope.launch { autoDownloadLoop() }
        scope.launch { refreshLibrary() }
        scope.launch {
            player.error.collect { text ->
                if (text != null) message.value = text
            }
        }
        scope.launch { yt.prewarm() }
        scope.launch { refreshRecentSearches() }
        scope.launch { refreshDownloads() }
        scope.launch { loadStats() }
        scope.launch { loadTopPlayed() }
        scope.launch {
            var lastId: String? = null
            player.current.collect { track ->
                val id = track?.id
                if (id != null && id != lastId) {
                    lastId = id
                    refreshListening()
                }
            }
        }
    }

    fun go(target: Screen) {
        if (screen.value == target) return
        backStack.addLast(screen.value)
        forwardStack.clear()
        screen.value = target
        loadFor(target)
        syncNav()
        persistScreen()
    }

    /** Troca a tela atual sem empilhar (usado quando a tela deixa de existir). */
    fun replace(target: Screen) {
        screen.value = target
        loadFor(target)
        syncNav()
        persistScreen()
    }

    fun back(): Boolean {
        if (backStack.isEmpty()) return false
        forwardStack.addLast(screen.value)
        screen.value = backStack.removeLast()
        loadFor(screen.value)
        syncNav()
        persistScreen()
        return true
    }

    fun forward(): Boolean {
        if (forwardStack.isEmpty()) return false
        backStack.addLast(screen.value)
        screen.value = forwardStack.removeLast()
        loadFor(screen.value)
        syncNav()
        persistScreen()
        return true
    }

    private fun syncNav() {
        canBack.value = backStack.isNotEmpty()
        canForward.value = forwardStack.isNotEmpty()
    }

    private fun loadFor(target: Screen) {
        when (target) {
            is Screen.PlaylistView -> if (openPlaylist.value?.id != target.id) loadPlaylist(target.id)
            is Screen.ArtistView -> if (artistPage.value?.id != target.id) loadArtist(target.id)
            is Screen.RemoteCollection -> if (remoteCollection.value?.id != target.id) {
                loadRemoteCollection(target.id)
            }
            Screen.Explore -> loadExplore()
            Screen.Charts -> loadCharts()
            Screen.Downloads -> refreshDownloads()
            Screen.Settings -> loadSettings()
            Screen.Search -> refreshRecentSearches()
            Screen.Home -> loadStats()
            Screen.TopPlayed -> loadTopPlayed()
            Screen.Recap -> loadRecap()
            else -> Unit
        }
    }

    private fun persistScreen() {
        runCatching { db.put("screen", tokenOf(screen.value)) }
    }

    private fun tokenOf(s: Screen): String = when (s) {
        Screen.Home -> "home"
        Screen.Search -> "search"
        Screen.Explore -> "explore"
        Screen.Charts -> "charts"
        Screen.Liked -> "liked"
        Screen.History -> "history"
        Screen.TopPlayed -> "top"
        Screen.Recap -> "recap"
        Screen.Downloads -> "downloads"
        Screen.Settings -> "settings"
        is Screen.PlaylistView -> "playlist:${s.id}"
        is Screen.ArtistView -> "artist:${s.id}"
        is Screen.RemoteCollection -> "remote:${s.id}"
    }

    private fun parseScreen(token: String): Screen? = when {
        token == "home" -> Screen.Home
        token == "search" -> Screen.Search
        token == "explore" -> Screen.Explore
        token == "charts" -> Screen.Charts
        token == "liked" -> Screen.Liked
        token == "history" -> Screen.History
        token == "top" -> Screen.TopPlayed
        token == "recap" -> Screen.Recap
        token == "downloads" -> Screen.Downloads
        token == "settings" -> Screen.Settings
        token.startsWith("playlist:") -> Screen.PlaylistView(token.removePrefix("playlist:"))
        token.startsWith("artist:") -> Screen.ArtistView(token.removePrefix("artist:"))
        token.startsWith("remote:") -> Screen.RemoteCollection(token.removePrefix("remote:"))
        else -> null
    }

    /** Retoma a ultima tela visitada (apos a sessao do player). */
    fun restoreLastScreen() {
        val token = db.get("screen") ?: return
        val target = parseScreen(token) ?: return
        screen.value = target
        loadFor(target)
        syncNav()
    }

    fun refreshLibrary() {
        scope.launch {
            val snapshot = withContext(Dispatchers.IO) {
                Snapshot(
                    playlists = db.playlists(),
                    liked = db.likedTracks(),
                    recent = db.recentlyPlayed(20),
                    history = db.history(200),
                )
            }
            playlists.value = snapshot.playlists
            liked.value = snapshot.liked
            recent.value = snapshot.recent
            history.value = snapshot.history
            player.refreshLikes()
        }
    }

    private data class Snapshot(
        val playlists: List<Playlist>,
        val liked: List<Track>,
        val recent: List<Track>,
        val history: List<Track>,
    )

    // ------------------------------------------------------------------ search
    fun search(query: String, record: Boolean = true) {
        val clean = query.trim()
        searchQuery.value = query
        if (clean.isEmpty()) {
            clearSearch()
            return
        }
        committedSearch.value = clean
        val mode = searchMode.value
        val generation = ++searchGeneration
        searchContinuation = null
        results.value = emptyList()
        searchArtists.value = emptyList()
        searchCards.value = emptyList()
        scope.launch {
            searching.value = true
            searchError.value = null
            val found = runCatching { withContext(Dispatchers.IO) { yt.search(clean, mode) } }
            if (generation != searchGeneration) return@launch
            found.onSuccess { result ->
                results.value = result.tracks
                searchArtists.value = result.artists
                searchCards.value = result.cards
                searchContinuation = result.continuation
                searchHasMore.value = result.continuation != null
                offline.value = false
                if (result.tracks.isEmpty() && result.artists.isEmpty() && result.cards.isEmpty()) {
                    searchError.value = "Nada encontrado."
                }
                if (record) {
                    withContext(Dispatchers.IO) { db.addSearchTerm(clean) }
                    refreshRecentSearches()
                }
            }.onFailure {
                searchError.value = it.message ?: "Falha na busca."
                offline.value = isNetworkError(it)
                com.openfy.core.Log.write("controller: busca falhou: ${it::class.simpleName}: ${it.message}")
            }
            searching.value = false
        }
    }

    fun loadMoreSearch() {
        val token = searchContinuation ?: return
        if (searching.value || searchLoadingMore.value) return
        val mode = searchMode.value
        val generation = searchGeneration
        searchLoadingMore.value = true
        scope.launch {
            val found = runCatching { withContext(Dispatchers.IO) { yt.searchNext(mode, token) } }
            if (generation != searchGeneration) {
                searchLoadingMore.value = false
                return@launch
            }
            found.onSuccess { more ->
                val knownTracks = results.value.mapTo(HashSet()) { it.id }
                val knownArtists = searchArtists.value.mapTo(HashSet()) { it.id }
                val knownCards = searchCards.value.mapTo(HashSet()) { "${it.kind}:${it.id}" }
                results.value = results.value + more.tracks.filter { it.id !in knownTracks }
                searchArtists.value = searchArtists.value + more.artists.filter { it.id !in knownArtists }
                searchCards.value = searchCards.value + more.cards.filter { "${it.kind}:${it.id}" !in knownCards }
                searchContinuation = more.continuation?.takeIf { it != token }
                searchHasMore.value = searchContinuation != null
                offline.value = false
            }.onFailure {
                searchError.value = "Não foi possível carregar mais resultados."
                offline.value = isNetworkError(it)
            }
            searchLoadingMore.value = false
        }
    }

    fun retrySearch() {
        val query = searchQuery.value.trim()
        if (query.isNotEmpty()) search(query)
    }

    fun setSearchMode(mode: SearchMode) {
        if (searchMode.value == mode) return
        searchMode.value = mode
        val query = searchQuery.value.trim()
        if (query.isEmpty()) {
            clearSearch()
        } else {
            search(searchQuery.value)
        }
    }

    fun clearSearch() {
        searchGeneration++
        results.value = emptyList()
        searchArtists.value = emptyList()
        searchCards.value = emptyList()
        searchError.value = null
        searching.value = false
        searchContinuation = null
        searchHasMore.value = false
        committedSearch.value = ""
        suggestions.value = emptyList()
    }

    private fun refreshRecentSearches() {
        scope.launch {
            val terms = withContext(Dispatchers.IO) { db.recentSearchTerms() }
            recentSearches.value = terms
        }
    }

    /** Sugestoes enquanto digita; a geracao descarta respostas antigas. */
    fun fetchSuggestions(query: String) {
        val clean = query.trim()
        if (clean.isEmpty()) {
            suggestions.value = emptyList()
            return
        }
        val generation = ++suggestionGeneration
        scope.launch {
            val found = runCatching { withContext(Dispatchers.IO) { yt.searchSuggestions(clean) } }
            if (generation != suggestionGeneration) return@launch
            suggestions.value = found.getOrDefault(emptyList())
        }
    }

    private fun isNetworkError(t: Throwable): Boolean {
        var cur: Throwable? = t
        while (cur != null) {
            if (cur is java.io.IOException) return true
            val name = cur::class.simpleName.orEmpty()
            if (name.contains("Timeout") || name.contains("Connect") ||
                name.contains("Socket") || name.contains("UnknownHost")
            ) {
                return true
            }
            cur = cur.cause
        }
        return false
    }

    // ------------------------------------------------------------------ artist
    fun loadArtist(id: String) {
        scope.launch {
            artistLoading.value = true
            artistError.value = null
            artistPage.value = null
            val found = runCatching { withContext(Dispatchers.IO) { yt.artist(id) } }
            if ((screen.value as? Screen.ArtistView)?.id != id) return@launch
            found.onSuccess {
                artistPage.value = it
                offline.value = false
            }.onFailure {
                artistError.value = "Não foi possível carregar o artista."
                offline.value = isNetworkError(it)
                com.openfy.core.Log.write("controller: artista falhou: ${it::class.simpleName}: ${it.message}")
            }
            artistLoading.value = false
        }
    }

    // ------------------------------------------------------------------ explore
    fun loadExplore(force: Boolean = false) {
        if (exploreLoading.value) return
        if (!force && exploreFeed.value != null) return
        scope.launch {
            exploreLoading.value = true
            exploreError.value = null
            val found = runCatching { withContext(Dispatchers.IO) { yt.explore() } }
            found.onSuccess {
                exploreFeed.value = it
                offline.value = false
            }.onFailure {
                exploreError.value = "Não foi possível carregar o Explorar."
                offline.value = isNetworkError(it)
                com.openfy.core.Log.write("controller: explorar falhou: ${it::class.simpleName}: ${it.message}")
            }
            exploreLoading.value = false
        }
    }

    // ------------------------------------------------------------------ charts
    fun loadCharts(force: Boolean = false) {
        if (chartsLoading.value) return
        if (!force && chartsTracks.value.isNotEmpty()) return
        scope.launch {
            chartsLoading.value = true
            chartsError.value = null
            val found = runCatching { withContext(Dispatchers.IO) { yt.charts() } }
            found.onSuccess {
                chartsTracks.value = it
                offline.value = false
            }.onFailure {
                chartsError.value = "Não foi possível carregar as músicas em alta."
                offline.value = isNetworkError(it)
                com.openfy.core.Log.write("controller: charts falhou: ${it::class.simpleName}: ${it.message}")
            }
            chartsLoading.value = false
        }
    }

    // ------------------------------------------------------------------ remote collection
    fun loadRemoteCollection(id: String) {
        scope.launch {
            remoteLoading.value = true
            remoteError.value = null
            remoteCollection.value = null
            val found = runCatching { withContext(Dispatchers.IO) { yt.collection(id) } }
            if ((screen.value as? Screen.RemoteCollection)?.id != id) return@launch
            found.onSuccess {
                remoteCollection.value = it
                offline.value = false
            }.onFailure {
                remoteError.value = "Não foi possível carregar esta coleção."
                offline.value = isNetworkError(it)
                com.openfy.core.Log.write("controller: colecao falhou: ${it::class.simpleName}: ${it.message}")
            }
            remoteLoading.value = false
        }
    }

    fun loadMoreRemote() {
        val page = remoteCollection.value ?: return
        val token = page.continuation ?: return
        val targetId = page.id
        if (remoteLoadingMore.value) return
        scope.launch {
            remoteLoadingMore.value = true
            val found = runCatching { withContext(Dispatchers.IO) { yt.collectionNext(token) } }
            found.onSuccess { next ->
                val current = remoteCollection.value ?: return@onSuccess
                if (current.id != targetId) return@onSuccess
                val known = current.tracks.mapTo(HashSet()) { it.id }
                val extra = next.tracks.filter { it.id !in known }
                // Algumas playlists devolvem uma pagina repetida com token novo;
                // sem faixas novas nao adianta continuar pedindo.
                val nextToken = next.continuation?.takeIf { it != token && extra.isNotEmpty() }
                remoteCollection.value = current.copy(
                    tracks = current.tracks + extra,
                    continuation = nextToken,
                )
                offline.value = false
            }.onFailure {
                remoteError.value = "Não foi possível carregar mais faixas."
                offline.value = isNetworkError(it)
                com.openfy.core.Log.write("controller: continuacao falhou: ${it::class.simpleName}: ${it.message}")
            }
            remoteLoadingMore.value = false
        }
    }

    fun openMedia(card: MediaCard) {
        when (card.kind) {
            MediaKind.ARTIST -> go(Screen.ArtistView(card.id))
            MediaKind.ALBUM, MediaKind.PLAYLIST -> go(Screen.RemoteCollection(card.id))
            MediaKind.TRACK -> playTracks(
                listOf(
                    Track(
                        id = card.id,
                        title = card.title,
                        artist = card.subtitle.substringBefore(" • ").ifBlank { "YouTube Music" },
                        thumbnail = card.thumbnail,
                    ),
                ),
            )
        }
    }

    // ------------------------------------------------------------------ radio
    fun startTrackRadio(track: Track) {
        scope.launch(Dispatchers.IO) { db.remember(track) }
        player.playThenRadio(track)
        message.value = "Rádio: ${track.title}"
    }

    fun startArtistRadio() {
        val page = artistPage.value ?: return
        val videoId = page.radioVideoId ?: page.topTracks.firstOrNull()?.id ?: return
        val playlistId = page.radioPlaylistId ?: "RDAMVM$videoId"
        startRadio(videoId, playlistId, page.name)
    }

    private fun startRadio(videoId: String, playlistId: String, label: String) {
        val expectedCurrent = player.current.value?.id
        scope.launch {
            message.value = "Preparando rádio..."
            val found = runCatching { withContext(Dispatchers.IO) { yt.radio(videoId, playlistId) } }
            found.onSuccess { page ->
                if (player.current.value?.id != expectedCurrent) return@onSuccess
                if (page.tracks.isEmpty()) {
                    message.value = "A rádio não retornou faixas."
                    return@onSuccess
                }
                player.setQueueContext("Rádio: $label", "radio", videoId)
                val seed = page.tracks.first()
                val selected = recommender.rankRadio(
                    page.tracks,
                    seed,
                    emptyList(),
                    limit = 20,
                    policy = Recommender.DiversityPolicy(recentArtistWindow = 3, maxPerArtist = 2),
                )
                player.startRadio(selected.ifEmpty { page.tracks }, page.continuation)
                message.value = "Rádio: $label"
                offline.value = false
            }.onFailure {
                message.value = "Não foi possível iniciar a rádio."
                offline.value = isNetworkError(it)
                com.openfy.core.Log.write("controller: radio falhou: ${it::class.simpleName}: ${it.message}")
            }
        }
    }

    fun stopRadio() {
        player.stopRadio(clearTail = true)
        message.value = "Rádio encerrada."
    }

    /**
     * "Seu mix" multi-semente: junta ate 3 sementes (curtidas + recentes) e
     * alterna as faixas das radios de cada uma. A continuacao da ultima radio
     * alimenta a fila quando chegar ao fim.
     */
    fun startPersonalMix() {
        val expectedCurrent = player.current.value?.id
        scope.launch {
            message.value = "Montando seu mix..."
            val seeds = withContext(Dispatchers.IO) {
                val top = recommender.topPlayed(limit = 5, windowDays = 180)
                val liked = db.likedTracks(5)
                val recent = db.recentlyPlayed(8)
                (top + liked + recent)
                    .distinctBy { Recommender.canonicalKey(it) }
                    .take(4)
            }
            if (seeds.isEmpty()) {
                message.value = "Curta ou ouça algo primeiro para montar o mix."
                return@launch
            }
            val pages = seeds.map { seed ->
                async(Dispatchers.IO) {
                    seed to runCatching { yt.radio(seed.id, "RDAMVM${seed.id}") }.getOrNull()
                }
            }.awaitAll().mapNotNull { (seed, page) -> page?.let { seed to it } }
            if (player.current.value?.id != expectedCurrent) return@launch
            val ranked = pages.map { (seed, page) ->
                recommender.rankRadio(
                    page.tracks,
                    seed,
                    emptyList(),
                    limit = 20,
                    policy = Recommender.DiversityPolicy(
                        recentArtistWindow = 4,
                        maxPerArtist = 2,
                        maxPerAlbum = 3,
                    ),
                )
            }
            val blended = recommender.interleave(ranked, limit = 60)
            if (blended.isEmpty()) {
                message.value = "O mix não retornou faixas."
                offline.value = true
                return@launch
            }
            val continuation = pages.lastOrNull { it.second.continuation != null }?.second?.continuation
            player.setQueueContext("Seu mix", "mix")
            player.startRadio(blended, continuation)
            message.value = "Seu mix: ${seeds.joinToString(", ") { it.artist }}."
            offline.value = false
        }
    }

    // ------------------------------------------------------------------ local library
    fun loadPlaylist(id: String) {
        scope.launch {
            val (meta, tracks) = withContext(Dispatchers.IO) {
                val name = db.playlistName(id)
                val list = db.playlistTracks(id)
                val cover = db.playlistCover(id).orEmpty()
                    .ifBlank { list.firstOrNull()?.thumbnail.orEmpty() }
                (if (name != null) Playlist(id, name, list.size, cover) else null) to list
            }
            if ((screen.value as? Screen.PlaylistView)?.id != id) return@launch
            openPlaylist.value = meta
            openPlaylistTracks.value = tracks
        }
    }

    fun createPlaylist(name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        val pending = pendingAddTrack
        pendingAddTrack = null
        scope.launch {
            val playlist = withContext(Dispatchers.IO) { db.createPlaylist(clean) }
            if (pending != null) {
                withContext(Dispatchers.IO) { db.addToPlaylist(playlist.id, pending) }
            }
            refreshLibrary()
            account.requestSync()
            message.value = if (pending != null) {
                "\"${pending.title}\" adicionada a \"$clean\"."
            } else {
                "Playlist \"$clean\" criada."
            }
        }
    }

    fun askCreatePlaylist() {
        pendingAddTrack = null
        playlistPrompt.value = PlaylistPrompt.Create
    }

    fun askCreatePlaylistFor(track: Track) {
        pendingAddTrack = track
        playlistPrompt.value = PlaylistPrompt.Create
    }

    fun askSaveQueueAsPlaylist() {
        playlistPrompt.value = PlaylistPrompt.SaveQueue
    }

    fun askRenamePlaylist(playlist: Playlist) {
        playlistPrompt.value = PlaylistPrompt.Rename(playlist.id, playlist.name)
    }

    fun askDeletePlaylist(playlist: Playlist) {
        playlistPrompt.value = PlaylistPrompt.Delete(playlist.id, playlist.name)
    }

    fun dismissPlaylistPrompt() {
        pendingAddTrack = null
        playlistPrompt.value = null
    }

    fun submitPlaylistPrompt(text: String) {
        when (val prompt = playlistPrompt.value) {
            null -> return
            PlaylistPrompt.Create -> createPlaylist(text)
            PlaylistPrompt.SaveQueue -> saveQueueAsPlaylist(text)
            is PlaylistPrompt.Rename -> renamePlaylist(prompt.id, text)
            is PlaylistPrompt.Delete -> deletePlaylist(prompt.id)
        }
        playlistPrompt.value = null
    }

    fun addToPlaylist(playlistId: String, track: Track) {
        scope.launch {
            val added = withContext(Dispatchers.IO) { db.addToPlaylist(playlistId, track) }
            refreshLibrary()
            account.requestSync()
            if (openPlaylist.value?.id == playlistId) loadPlaylist(playlistId)
            message.value = if (added) "Adicionada à playlist." else "Já estava nessa playlist."
        }
    }

    fun removeFromPlaylist(playlistId: String, trackId: String) {
        scope.launch {
            withContext(Dispatchers.IO) { db.removeFromPlaylist(playlistId, trackId) }
            loadPlaylist(playlistId)
            refreshLibrary()
            account.requestSync()
        }
    }

    fun deletePlaylist(id: String) {
        scope.launch {
            withContext(Dispatchers.IO) { db.deletePlaylist(id) }
            if (openPlaylist.value?.id == id) {
                openPlaylist.value = null
                openPlaylistTracks.value = emptyList()
                replace(Screen.Home)
            }
            refreshLibrary()
            account.requestSync()
        }
    }

    fun renamePlaylist(id: String, name: String) {
        if (name.isBlank()) return
        scope.launch {
            withContext(Dispatchers.IO) { db.renamePlaylist(id, name.trim()) }
            loadPlaylist(id)
            refreshLibrary()
            account.requestSync()
        }
    }

    /** Copia a imagem escolhida, usa localmente e sobe para o Storage quando há conta. */
    fun setPlaylistCover(id: String, source: File) {
        scope.launch {
            val target = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = com.openfy.core.AppPaths.home.resolve("covers")
                    Files.createDirectories(dir)
                    val ext = source.extension.ifBlank { "img" }
                    dir.resolve("$id.$ext").toFile().also { file ->
                        Files.copy(source.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    }
                }.getOrNull()
            }
            if (target == null) {
                message.value = "Não foi possível usar essa imagem."
                return@launch
            }
            withContext(Dispatchers.IO) { db.setPlaylistCover(id, target.toString()) }
            loadPlaylist(id)
            refreshLibrary()
            message.value = "Capa atualizada."
            val url = runCatching { account.uploadCover(id, target) }.getOrNull()
            if (url != null) {
                withContext(Dispatchers.IO) { db.setPlaylistCoverUrl(id, url) }
                loadPlaylist(id)
                refreshLibrary()
                account.requestSync()
                message.value = "Capa atualizada e enviada para a conta."
            }
        }
    }

    fun clearPlaylistCover(id: String) {
        scope.launch {
            withContext(Dispatchers.IO) {
                db.setPlaylistCover(id, "")
                db.setPlaylistCoverUrl(id, "")
            }
            loadPlaylist(id)
            refreshLibrary()
            account.requestSync()
        }
    }

    fun toggleLike(track: Track) {
        scope.launch {
            val nowLiked = player.toggleLike(track)
            refreshLibrary()
            account.requestSync()
            message.value = if (nowLiked) "Curtida." else "Removida das curtidas."
        }
    }

    fun toggleLikeCurrent() {
        player.current.value?.let { toggleLike(it) }
    }

    fun playTracks(tracks: List<Track>, startIndex: Int = 0, title: String? = null) {
        if (tracks.isEmpty()) return
        val (contextTitle, origin) = playbackContext(title)
        player.setQueueContext(contextTitle, origin)
        player.playNow(tracks, startIndex)
        scope.launch(Dispatchers.IO) { tracks.forEach { db.remember(it) } }
    }

    /** Toca a lista embaralhada imediatamente (botoes "Aleatorio" dos cabecalhos). */
    fun playShuffled(tracks: List<Track>, title: String? = null) {
        if (tracks.isEmpty()) return
        val (contextTitle, origin) = playbackContext(title)
        player.setQueueContext(contextTitle, origin)
        player.playShuffled(tracks)
        scope.launch(Dispatchers.IO) { tracks.forEach { db.remember(it) } }
        message.value = "Aleatório: ${tracks.size} faixas."
    }

    private fun playbackContext(title: String? = null): Pair<String, String> {
        if (title != null) return title to "manual"
        return when (screen.value) {
            is Screen.PlaylistView -> (openPlaylist.value?.name ?: "Playlist") to "playlist"
            is Screen.RemoteCollection -> (remoteCollection.value?.title ?: "Álbum") to "album"
            is Screen.ArtistView -> (artistPage.value?.name ?: "Artista") to "artist"
            Screen.Charts -> "Em alta" to "charts"
            Screen.Liked -> "Curtidas" to "liked"
            Screen.History -> "Histórico" to "history"
            Screen.Downloads -> "Downloads" to "downloads"
            Screen.Search -> "Busca" to "search"
            Screen.Explore -> "Explorar" to "explore"
            else -> "Openfy" to "library"
        }
    }

    /** Icone de aleatorio: liga/desliga e, se nada toca, inicia o contexto atual. */
    fun toggleShuffle() {
        val enable = !player.shuffle.value
        player.toggleShuffle()
        if (enable && player.current.value == null) {
            val context = playContext()
            if (context.isNotEmpty()) playShuffled(context)
        }
    }

    private fun playContext(): List<Track> {
        (screen.value as? Screen.PlaylistView)?.let {
            if (openPlaylistTracks.value.isNotEmpty()) return openPlaylistTracks.value
        }
        (screen.value as? Screen.RemoteCollection)?.let {
            val tracks = remoteCollection.value?.tracks.orEmpty()
            if (tracks.isNotEmpty()) return tracks
        }
        (screen.value as? Screen.ArtistView)?.let {
            val tracks = artistPage.value?.topTracks.orEmpty()
            if (tracks.isNotEmpty()) return tracks
        }
        if (liked.value.isNotEmpty()) return liked.value
        if (recent.value.isNotEmpty()) return recent.value
        if (chartsTracks.value.isNotEmpty()) return chartsTracks.value
        return emptyList()
    }

    /** Clique num resultado de busca: toca a faixa e emenda a radio dela. */
    fun playSearchTrack(track: Track) {
        scope.launch(Dispatchers.IO) { db.remember(track) }
        player.playThenRadio(track)
    }

    fun playLater(track: Track) {
        scope.launch(Dispatchers.IO) { db.remember(track) }
        player.addToQueue(track)
        message.value = "Adicionada à fila."
    }

    fun playNext(track: Track) {
        scope.launch(Dispatchers.IO) { db.remember(track) }
        player.addNext(track)
        message.value = "Toca em seguida."
    }

    // ------------------------------------------------------------------ downloads
    fun refreshDownloads() {
        scope.launch {
            val (tracks, ids, size) = withContext(Dispatchers.IO) {
                Triple(
                    db.downloadedTracks(),
                    db.downloadedIds().toSet(),
                    db.downloadedSize(),
                )
            }
            downloadedTracks.value = tracks
            downloadedIds.value = ids
            downloadsSize.value = size
        }
    }

    fun downloadTrack(track: Track) {
        if (track.id in downloadedIds.value || track.id in downloadingIds.value) return
        scope.launch {
            downloadingIds.value = downloadingIds.value + track.id
            val result = withContext(Dispatchers.IO) {
                runCatching { downloader.download(track) }
            }
            downloadingIds.value = downloadingIds.value - track.id
            result.onSuccess { path ->
                if (path != null) {
                    withContext(Dispatchers.IO) {
                        db.addDownload(track, path.toString(), runCatching { java.nio.file.Files.size(path) }.getOrDefault(0L))
                    }
                    refreshDownloads()
                    message.value = "Baixada: \"${track.title}\"."
                } else {
                    message.value = "Não foi possível baixar \"${track.title}\"."
                }
            }.onFailure {
                offline.value = isNetworkError(it)
                message.value = "Falha ao baixar \"${track.title}\"."
                com.openfy.core.Log.write("controller: download falhou: ${it::class.simpleName}: ${it.message}")
            }
        }
    }

    /** Baixa uma lista inteira (álbum/playlist); usa concorrência limitada. */
    fun downloadTracks(tracks: List<Track>, announce: Boolean = true) {
        val todo = tracks.filter { it.id !in downloadedIds.value && it.id !in downloadingIds.value }
        if (todo.isEmpty()) return
        val todoIds = todo.mapTo(HashSet()) { it.id }
        scope.launch {
            downloadingIds.value = downloadingIds.value + todoIds
            if (announce) message.value = "Baixando ${todo.size} faixas..."
            val semaphore = Semaphore(3)
            val jobs = todo.map { track ->
                launch(Dispatchers.IO) {
                    semaphore.withPermit {
                        val path = runCatching { downloader.download(track) }.getOrNull()
                        if (path != null) {
                            db.addDownload(
                                track,
                                path.toString(),
                                runCatching { java.nio.file.Files.size(path) }.getOrDefault(0L),
                            )
                        }
                    }
                }
            }
            jobs.forEach { it.join() }
            downloadingIds.value = downloadingIds.value - todoIds
            refreshDownloads()
            if (announce) message.value = "Download concluído."
        }
    }

    fun setAutoDownload(enabled: Boolean) {
        autoDownload.value = enabled
        scope.launch(Dispatchers.IO) { db.put("download.auto", enabled.toString()) }
        if (enabled) scope.launch { runAutoDownload() }
    }

    /** Roda de tempos em tempos: mais ouvidas + similares, sem avisar na UI. */
    private suspend fun autoDownloadLoop() {
        delay(60_000)
        while (true) {
            if (autoDownload.value) runAutoDownload()
            delay(AUTO_INTERVAL_MS)
        }
    }

    private suspend fun runAutoDownload() {
        if (!autoDownload.value) return
        if (downloadedIds.value.size >= AUTO_MAX_DOWNLOADS) return
        val top = withContext(Dispatchers.IO) { db.topPlayed(AUTO_PLAYED) }
        if (top.isEmpty()) return
        val missing = top.filter { it.id !in downloadedIds.value && it.id !in downloadingIds.value }
        if (missing.isNotEmpty()) downloadTracks(missing, announce = false)
        val seed = top.first()
        val page = runCatching {
            withContext(Dispatchers.IO) { yt.radio(seed.id, "RDAMVM${seed.id}") }
        }.getOrNull() ?: return
        val similar = page.tracks
            .filter { it.id !in downloadedIds.value && it.id !in downloadingIds.value && it.id != seed.id }
            .take(AUTO_SIMILAR)
        if (similar.isNotEmpty()) downloadTracks(similar, announce = false)
    }

    fun removeDownload(id: String) {
        scope.launch {
            val path = withContext(Dispatchers.IO) { db.removeDownload(id) }
            withContext(Dispatchers.IO) {
                path?.let { downloader.delete(id) }
            }
            refreshDownloads()
        }
    }

    // ------------------------------------------------------------------ import / backup
    fun openImport() {
        importDialogOpen.value = true
        importStatus.value = null
    }

    fun closeImport() {
        importDialogOpen.value = false
    }

    /** Entra numa playlist colaborativa por código/link de convite. */
    fun joinSharedPlaylist(code: String) {
        val raw = code.trim()
        val clean = if (raw.contains("code=")) {
            raw.substringAfter("code=").substringBefore("&").trim()
        } else {
            raw
        }
        if (clean.isBlank()) return
        scope.launch {
            if (account.state.value.session == null) {
                message.value = "Entre na conta para entrar numa playlist compartilhada."
                return@launch
            }
            val id = runCatching { account.joinByCode(clean) }.getOrNull()
            if (id == null) {
                message.value = "Código inválido ou sessão expirada."
                return@launch
            }
            closeImport()
            refreshLibrary()
            message.value = "Você entrou na playlist colaborativa."
            go(Screen.PlaylistView(id))
        }
    }

    /** Gera e copia o link de convite (GitHub Pages + código). */
    fun copyPlaylistInvite(playlistId: String) {
        scope.launch {
            if (account.state.value.session == null) {
                message.value = "Entre na conta para gerar um convite."
                return@launch
            }
            val code = runCatching { account.shareCodeFor(playlistId) }.getOrNull()
            if (code == null) {
                message.value = "Não foi possível gerar o convite."
                return@launch
            }
            val link = "https://leo-bsb.github.io/openfy/join.html?code=$code"
            runCatching {
                java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(
                    java.awt.datatransfer.StringSelection(link),
                    null,
                )
            }
            message.value = "Convite copiado: $link"
        }
    }

    fun importYtmUrl(url: String) {
        val browse = Importer.parseYtmBrowseId(url)
        if (browse == null) {
            importStatus.value = "Link inválido. Cole a URL de uma playlist ou álbum do YouTube Music."
            return
        }
        scope.launch {
            importBusy.value = true
            importStatus.value = "Carregando playlist..."
            val first = runCatching { withContext(Dispatchers.IO) { yt.collection(browse) } }.getOrNull()
            if (first == null) {
                importBusy.value = false
                importStatus.value = "Não foi possível carregar a playlist."
                return@launch
            }
            val tracks = first.tracks.toMutableList()
            var token = first.continuation
            var pages = 0
            while (token != null && tracks.size < 500 && pages < 8) {
                val next = runCatching {
                    withContext(Dispatchers.IO) { yt.collectionNext(token) }
                }.getOrNull() ?: break
                val known = tracks.mapTo(HashSet()) { it.id }
                val extra = next.tracks.filter { it.id !in known }
                if (extra.isEmpty()) break
                tracks += extra
                token = next.continuation
                pages++
                importStatus.value = "Carregando... ${tracks.size} faixas"
            }
            if (tracks.isEmpty()) {
                importBusy.value = false
                importStatus.value = "A playlist está vazia."
                return@launch
            }
            val title = first.title.ifBlank { "Importada do YouTube Music" }
            withContext(Dispatchers.IO) {
                val playlist = db.createPlaylist(title)
                tracks.forEach { db.addToPlaylist(playlist.id, it) }
            }
            refreshLibrary()
            account.requestSync()
            importBusy.value = false
            importStatus.value = "\"$title\" importada com ${tracks.size} faixas."
        }
    }

    fun importCsvFile(file: File) {
        scope.launch {
            importBusy.value = true
            importStatus.value = "Lendo CSV..."
            val rows = runCatching {
                withContext(Dispatchers.IO) { Importer.parseCsv(file.readText()) }
            }.getOrDefault(emptyList())
            if (rows.isEmpty()) {
                importBusy.value = false
                importStatus.value = "CSV vazio ou em formato não reconhecido."
                return@launch
            }
            val name = file.nameWithoutExtension.ifBlank { "Importada do Spotify" }
            val playlist = withContext(Dispatchers.IO) { db.createPlaylist(name) }
            var matched = 0
            rows.forEachIndexed { index, row ->
                importStatus.value = "Casando ${index + 1}/${rows.size}: ${row.title}"
                val found = runCatching {
                    withContext(Dispatchers.IO) { yt.searchSongs("${row.artist} ${row.title}", 6) }
                }.getOrDefault(emptyList())
                val best = Importer.bestMatch(row, found)
                if (best != null) {
                    withContext(Dispatchers.IO) { db.addToPlaylist(playlist.id, best) }
                    matched++
                }
                delay(120)
            }
            refreshLibrary()
            account.requestSync()
            importBusy.value = false
            importStatus.value = "\"$name\": $matched de ${rows.size} faixas encontradas."
        }
    }

    fun exportBackupFile(file: File) {
        scope.launch {
            importBusy.value = true
            importStatus.value = "Gerando backup..."
            val backup = withContext(Dispatchers.IO) {
                Backup(
                    exportedAt = System.currentTimeMillis(),
                    playlists = db.playlists().map { p ->
                        BackupPlaylist(p.name, db.playlistTracks(p.id))
                    },
                    liked = db.likedTracks(10_000),
                )
            }
            val ok = runCatching {
                withContext(Dispatchers.IO) { file.writeText(backupJson.encodeToString(backup)) }
            }.isSuccess
            importBusy.value = false
            importStatus.value = if (ok) "Backup salvo em ${file.name}." else "Não foi possível salvar o backup."
        }
    }

    fun importBackupFile(file: File) {
        scope.launch {
            importBusy.value = true
            importStatus.value = "Lendo backup..."
            val backup = runCatching {
                withContext(Dispatchers.IO) { backupJson.decodeFromString<Backup>(file.readText()) }
            }.getOrNull()
            if (backup == null) {
                importBusy.value = false
                importStatus.value = "Backup inválido."
                return@launch
            }
            withContext(Dispatchers.IO) {
                backup.playlists.forEach { p ->
                    val created = db.createPlaylist(p.name.ifBlank { "Importada" })
                    p.tracks.forEach { db.addToPlaylist(created.id, it) }
                }
                backup.liked.forEach { track ->
                    db.remember(track)
                    db.like(track.id)
                }
            }
            refreshLibrary()
            account.requestSync()
            importBusy.value = false
            importStatus.value =
                "Backup importado: ${backup.playlists.size} playlists, ${backup.liked.size} curtidas."
        }
    }

    /** Exporta uma playlist para outra pessoa abrir no Openfy. */
    fun exportPlaylistFile(id: String, file: File) {
        scope.launch {
            val shared = withContext(Dispatchers.IO) {
                val meta = db.playlists().firstOrNull { it.id == id }
                SharedPlaylist(
                    name = meta?.name ?: "Playlist",
                    exportedAt = System.currentTimeMillis(),
                    tracks = db.playlistTracks(id),
                )
            }
            if (shared.tracks.isEmpty()) {
                message.value = "A playlist está vazia."
                return@launch
            }
            val ok = runCatching {
                withContext(Dispatchers.IO) { file.writeText(backupJson.encodeToString(shared)) }
            }.isSuccess
            message.value = if (ok) {
                "Playlist pronta para compartilhar: ${file.name}"
            } else {
                "Não foi possível salvar a playlist."
            }
        }
    }

    /** Importa um arquivo .openfyplaylist criado por outro Openfy. */
    fun importPlaylistFile(file: File) {
        scope.launch {
            importBusy.value = true
            importStatus.value = "Lendo playlist do Openfy..."
            val shared = runCatching {
                withContext(Dispatchers.IO) {
                    backupJson.decodeFromString<SharedPlaylist>(file.readText())
                }
            }.getOrNull()
            if (shared == null || shared.tracks.isEmpty()) {
                importBusy.value = false
                importStatus.value = "Arquivo de playlist inválido ou vazio."
                return@launch
            }
            val name = shared.name.ifBlank {
                file.nameWithoutExtension.ifBlank { "Playlist compartilhada" }
            }
            withContext(Dispatchers.IO) {
                val created = db.createPlaylist(name)
                shared.tracks.forEach { db.addToPlaylist(created.id, it) }
            }
            refreshLibrary()
            account.requestSync()
            importBusy.value = false
            importStatus.value = "\"$name\" importada com ${shared.tracks.size} faixas."
        }
    }

    // ------------------------------------------------------------------ lyrics / sleep / stats
    fun loadLyrics(track: Track) {
        if (lyricsTrackId.value == track.id && (lyrics.value.isNotEmpty() || lyricsLoading.value)) return
        lyricsTrackId.value = track.id
        lyrics.value = emptyList()
        scope.launch {
            lyricsLoading.value = true
            val found = runCatching {
                withContext(Dispatchers.IO) { LyricsClient.fetch(track) }
            }.getOrDefault(emptyList())
            if (lyricsTrackId.value != track.id) return@launch
            lyrics.value = found
            lyricsLoading.value = false
        }
    }

    fun clearLyrics() {
        lyricsTrackId.value = null
        lyrics.value = emptyList()
        lyricsLoading.value = false
    }

    fun setSleepTimer(minutes: Int?) {
        sleepJob?.cancel()
        if (minutes == null) {
            sleepUntil.value = null
            message.value = "Timer desligado."
            return
        }
        sleepUntil.value = System.currentTimeMillis() + minutes * 60_000L
        message.value = "Timer: $minutes min."
        sleepJob = scope.launch {
            delay(minutes * 60_000L)
            sleepUntil.value = null
            if (player.playing.value) player.togglePlay()
            message.value = "Timer encerrado: reprodução pausada."
        }
    }

    fun loadStats() {
        scope.launch {
            val value = withContext(Dispatchers.IO) {
                Stats(
                    totalPlays = db.totalPlays(),
                    totalSeconds = db.totalListenedSeconds(),
                    topArtists = db.topArtists(5),
                )
            }
            stats.value = value
        }
    }

    fun loadTopPlayed() {
        scope.launch {
            val tracks = withContext(Dispatchers.IO) {
                val weighted = Recommender(db).topPlayed(limit = 100)
                if (weighted.isNotEmpty()) weighted else db.topPlayed(100)
            }
            topPlayed.value = tracks
        }
    }

    private fun monthBounds(month: YearMonth): Pair<Long, Long> {
        val zone = ZoneId.systemDefault()
        val start = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return start to end
    }

    fun selectRecapMonth(month: YearMonth) {
        if (recapMonth.value == month && recap.value != null) return
        recapMonth.value = month
        loadRecap(month, force = true)
    }

    fun openRecap() {
        if (screen.value == Screen.Recap) loadRecap(force = true) else go(Screen.Recap)
    }

    private fun _stateAccountHasSession(): Boolean = account.state.value.session != null

    fun loadRecap(month: YearMonth = recapMonth.value, force: Boolean = false) {
        if (recapLoading.value && !force) return
        val generation = ++recapGeneration
        recapLoading.value = true
        recapError.value = null
        recapFromAccount.value = false
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val months = db.recapMonthKeys().mapNotNull { key ->
                        runCatching { YearMonth.parse(key) }.getOrNull()
                    }
                    val withCurrent = (months + month + YearMonth.now())
                        .distinct()
                        .sortedDescending()
                        .take(12)
                    val (start, end) = monthBounds(month)
                    val (prevStart, prevEnd) = monthBounds(month.minusMonths(1))
                    Triple(
                        withCurrent,
                        RecapSnapshot(
                            summary = db.recapSummary(start, end),
                            previous = db.recapSummary(prevStart, prevEnd),
                            topTracks = db.recapTopTracks(start, end, 10),
                            topArtists = db.recapTopArtists(start, end, 8),
                            days = db.recapDays(start, end),
                            firstTimeTracks = db.recapFirstTimeTracks(start, end, 8),
                            liked = db.recapLiked(start, end),
                        ),
                        null,
                    )
                }.getOrElse { Triple(emptyList(), null, it.message ?: "Falha ao carregar o recap.") }
            }
            if (generation != recapGeneration) return@launch
            recapMonths.value = result.first
            val localSnapshot = result.second
            recap.value = localSnapshot
            recapError.value = result.third
            recapLoading.value = false
            if (localSnapshot != null && _stateAccountHasSession()) {
                val remote = account.fetchRecap(month)
                if (generation == recapGeneration && remote != null &&
                    remote.summary.plays >= localSnapshot.summary.plays
                ) {
                    recap.value = localSnapshot.copy(
                        summary = remote.summary,
                        topTracks = remote.topTracks.ifEmpty { localSnapshot.topTracks },
                        topArtists = remote.topArtists.ifEmpty { localSnapshot.topArtists },
                    )
                    recapFromAccount.value = true
                }
            } else {
                recapFromAccount.value = false
            }
        }
    }

    /** Atualiza listas e números quando a faixa atual muda, sem recarregar toda a biblioteca. */
    private fun refreshListening() {
        scope.launch {
            delay(700)
            val (recentTracks, historyTracks, statsValue) = withContext(Dispatchers.IO) {
                Triple(
                    db.recentlyPlayed(20),
                    db.history(200),
                    Stats(
                        totalPlays = db.totalPlays(),
                        totalSeconds = db.totalListenedSeconds(),
                        topArtists = db.topArtists(5),
                    ),
                )
            }
            recent.value = recentTracks
            history.value = historyTracks
            stats.value = statsValue
            loadTopPlayed()
            val now = System.currentTimeMillis()
            if (screen.value == Screen.Recap && now - lastRecapAuto > 10_000) {
                lastRecapAuto = now
                loadRecap(force = true)
            }
        }
    }

    // ------------------------------------------------------------------ queue editing
    fun removeFromQueue(index: Int) {
        val track = player.queue.value.getOrNull(index) ?: return
        player.removeAt(index)
        queueUndo.value = QueueUndo.Remove(track, index)
        message.value = "Removida da fila."
    }

    fun clearQueueNext() {
        val q = player.queue.value
        val idx = player.index.value
        if (idx < 0) return
        val removed = q.drop(idx + 1)
        if (removed.isEmpty()) {
            message.value = "Nada depois da faixa atual."
            return
        }
        player.clearNext()
        queueUndo.value = QueueUndo.ClearNext(removed)
        message.value = "Próximas removidas."
    }

    fun undoQueue() {
        when (val u = queueUndo.value) {
            is QueueUndo.Remove -> player.restoreAt(u.index, u.track)
            is QueueUndo.ClearNext -> player.appendAll(u.tracks)
            null -> return
        }
        queueUndo.value = null
        message.value = "Desfeito."
    }

    fun moveInQueue(from: Int, to: Int) = player.move(from, to)

    fun playQueueAt(index: Int) = player.playAt(index)

    fun saveQueueAsPlaylist(name: String) {
        val clean = name.trim()
        val tracks = player.queue.value
        if (clean.isEmpty() || tracks.isEmpty()) return
        scope.launch {
            val id = withContext(Dispatchers.IO) { db.createPlaylist(clean).id }
            withContext(Dispatchers.IO) { tracks.forEach { db.addToPlaylist(id, it) } }
            refreshLibrary()
            account.requestSync()
            message.value = "Fila salva como \"$clean\"."
        }
    }

    // ------------------------------------------------------------------ ui helpers
    fun toggleQueue() {
        queueOpen.value = !queueOpen.value
    }

    fun closeQueue() {
        queueOpen.value = false
    }

    fun openNowPlaying() {
        nowPlayingOpen.value = true
    }

    fun closeNowPlaying() {
        nowPlayingOpen.value = false
    }

    fun requestSearchFocus() {
        searchFocusRequest.value++
    }

    // ------------------------------------------------------------------ settings
    fun loadSettings() {
        audioQuality.value = db.get("audio.quality") ?: "HIGH"
        yt.setQuality(audioQuality.value)
        closeToTray.value = runCatching { db.get("window.closeToTray")?.toBooleanStrict() }.getOrNull() ?: true
        refreshDownloads()
    }

    fun setAudioQuality(name: String) {
        val value = if (name == "LOW") "LOW" else "HIGH"
        audioQuality.value = value
        yt.setQuality(value)
        scope.launch(Dispatchers.IO) { db.put("audio.quality", value) }
    }

    fun setCloseToTray(enabled: Boolean) {
        closeToTray.value = enabled
        scope.launch(Dispatchers.IO) { db.put("window.closeToTray", enabled.toString()) }
    }

    fun setPalette(value: ColorPalette) {
        palette.value = value
        scope.launch(Dispatchers.IO) { db.put("ui.palette", value.id) }
    }

    fun setPlayerLayout(value: PlayerLayout) {
        playerLayout.value = value
        scope.launch(Dispatchers.IO) { db.put("ui.player.layout", value.id) }
    }

    fun setCoverGradient(enabled: Boolean) {
        coverGradient.value = enabled
        scope.launch(Dispatchers.IO) { db.put("ui.player.coverGradient", enabled.toString()) }
    }

    // ------------------------------------------------------------------ updates
    /** Botao "Verificar agora" dos Ajustes; erro aparece no card. */
    fun checkForUpdate() = runUpdateCheck(manual = true)

    fun setUpdateAutoCheck(enabled: Boolean) {
        updateAutoCheck.value = enabled
        scope.launch(Dispatchers.IO) { db.put("update.autoCheck", enabled.toString()) }
    }

    fun openReleasePage() {
        val release = (updateStatus.value as? UpdateStatus.Available)?.release ?: return
        browseUrl(release.releaseUrl)
    }

    fun openInstaller() {
        val url = (updateStatus.value as? UpdateStatus.Available)?.release?.installerUrl ?: return
        browseUrl(url)
    }

    /** Verificação ao abrir: respeita o toggle e no máximo 1x/dia; silenciosa offline. */
    private suspend fun autoUpdateCheck() {
        if (!updateAutoCheck.value) return
        val lastCheck = withContext(Dispatchers.IO) { db.get("update.lastCheck")?.toLongOrNull() }
        if (!UpdateChecker.shouldAutoCheck(lastCheck)) return
        runUpdateCheck(manual = false)
    }

    private fun runUpdateCheck(manual: Boolean) {
        if (updateStatus.value is UpdateStatus.Checking) return
        if (manual) updateStatus.value = UpdateStatus.Checking
        scope.launch {
            val status = UpdateChecker.check(BuildInfo.VERSION)
            if (status !is UpdateStatus.Error) {
                withContext(Dispatchers.IO) {
                    db.put("update.lastCheck", System.currentTimeMillis().toString())
                }
            }
            // Automatica nao mostra erro: quem esta offline nao ve notificacao falsa.
            updateStatus.value = if (!manual && status is UpdateStatus.Error) null else status
        }
    }

    private fun browseUrl(url: String) {
        runCatching {
            if (java.awt.Desktop.isDesktopSupported() &&
                java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)
            ) {
                java.awt.Desktop.getDesktop().browse(java.net.URI.create(url))
            } else {
                message.value = "Nao foi possivel abrir o navegador."
            }
        }.onFailure {
            Log.write("updates: falha ao abrir $url: ${it.message}")
            message.value = "Nao foi possivel abrir o navegador."
        }
    }

    fun openDataFolder() {
        runCatching {
            val dir = com.openfy.core.AppPaths.home
            Files.createDirectories(dir)
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().open(dir.toFile())
            }
        }
    }

    fun consumeMessage() {
        message.value = null
        queueUndo.value = null
        player.clearError()
    }

    private companion object {
        const val AUTO_PLAYED = 8
        const val AUTO_SIMILAR = 6
        const val AUTO_MAX_DOWNLOADS = 250
        const val AUTO_INTERVAL_MS = 2 * 60 * 60 * 1000L
    }
}
