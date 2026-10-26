package com.openfy.data

import com.metrolist.innertubex.InnerTube
import com.metrolist.innertubex.InnerTubeLogEvent
import com.metrolist.innertubex.InnerTubeLogger
import com.metrolist.innertubex.cipher.PlayerConfigRepository
import com.metrolist.innertubex.cipher.RemotePlayerConfigStore
import com.metrolist.innertubex.cipher.YouTubeCipherService
import com.metrolist.innertubex.extraction.AudioQuality
import com.metrolist.innertubex.extraction.ContentHints
import com.metrolist.innertubex.extraction.ExtractedStream
import com.metrolist.innertubex.extraction.InnerTubeExtractor
import com.metrolist.innertubex.extraction.PoTokenResult
import com.metrolist.innertubex.extraction.TokenProvider
import com.metrolist.innertubex.extraction.YtConfigParserImpl
import com.metrolist.innertubex.extraction.generateClientPlaybackNonce
import com.metrolist.innertubex.models.YouTubeClient
import com.openfy.core.Artist
import com.openfy.core.ArtistPage
import com.openfy.core.CollectionKind
import com.openfy.core.ExploreFeed
import com.openfy.core.MediaCard
import com.openfy.core.MediaKind
import com.openfy.core.MediaSection
import com.openfy.core.RadioPage
import com.openfy.core.RemoteCollection
import com.openfy.core.RemoteTracksPage
import com.openfy.core.SearchMode
import com.openfy.core.SearchResult
import com.openfy.core.Track
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

/**
 * Ponte entre o app e o innertubex.
 *
 * Busca/browse: chamadas InnerTube cruas, resposta lida como JSON — o modelo interno
 * da lib muda com frequencia e o formato do YouTube Music e estavel.
 * Playback: InnerTubeExtractor devolve URL + headers; o StreamServer entrega ao mpv.
 */
class YouTubeEngine {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val http = HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 30_000
        }
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true })
        }
    }.also { com.openfy.core.Log.write("yt: http pronto") }

    private val logger = InnerTubeLogger { event: InnerTubeLogEvent ->
        if (event.level == com.metrolist.innertubex.InnerTubeLogLevel.ERROR) {
            com.openfy.core.Log.write("[innertube] ${event.message}")
        }
    }

    private val innerTube = InnerTube(http, logger = logger)
        .also { com.openfy.core.Log.write("yt: innertube pronto") }

    private val configStore = RemotePlayerConfigStore(
        http,
        PlayerConfigRepository.disabled(),
        logger,
    ).also { com.openfy.core.Log.write("yt: config store pronto") }

    private val cipher = YouTubeCipherService(http, configStore, logger)
        .also { com.openfy.core.Log.write("yt: cipher pronto") }

    private val parser = YtConfigParserImpl(http, innerTube, configStore, logger, cipher)
        .also { com.openfy.core.Log.write("yt: parser pronto") }

    private val extractor = InnerTubeExtractor(
        configParser = parser,
        cipherService = cipher,
        innerTube = innerTube,
        tokenProvider = NoTokens,
        logger = logger,
    ).also { com.openfy.core.Log.write("yt: extractor pronto") }

    private val streamCache = ConcurrentHashMap<String, CachedStream>()
    private val extractMutex = Mutex()

    /** Qualidade pedida ao extrator; HIGH e o teto desta engine. */
    @Volatile private var quality: AudioQuality = AudioQuality.HIGH

    fun setQuality(name: String) {
        val value = runCatching { AudioQuality.valueOf(name) }.getOrDefault(AudioQuality.HIGH)
        if (value == quality) return
        quality = value
        streamCache.clear()
        com.openfy.core.Log.write("yt: qualidade -> $value")
    }

    private data class CachedStream(val stream: ExtractedStream, val at: Long)

    fun close() {
        runCatching { http.close() }
    }

    suspend fun prewarm() {
        val started = System.nanoTime()
        com.openfy.core.Log.write("yt: prewarm iniciado")
        val result = runCatching { extractor.prewarm() }
        val elapsed = (System.nanoTime() - started) / 1_000_000
        result.onFailure {
            com.openfy.core.Log.write(
                "yt: prewarm falhou em ${elapsed}ms: ${it::class.simpleName}: ${it.message}"
            )
        }.onSuccess {
            com.openfy.core.Log.write("yt: prewarm concluido em ${elapsed}ms")
        }
    }

    // ------------------------------------------------------------------ diagnostics
    /** Diagnostico: corpo cru da busca, usado pelo smoke test e pela sonda. */
    suspend fun searchRaw(query: String, params: String? = null): String =
        innerTube.search(
            YouTubeClient.WEB_REMIX,
            query = query,
            params = params,
            continuation = null,
            setLogin = false,
        ).bodyAsText()

    /** Diagnostico: corpo cru de browse (artista, playlist, home). */
    suspend fun browseRaw(
        browseId: String,
        params: String? = null,
        continuation: String? = null,
    ): String =
        innerTube.browse(
            YouTubeClient.WEB_REMIX,
            browseId = browseId.ifBlank { null },
            params = params,
            continuation = continuation,
            setLogin = false,
        ).bodyAsText()

    /** Diagnostico: fila/radio crua do endpoint next. */
    suspend fun nextRaw(
        videoId: String? = null,
        playlistId: String? = null,
        continuation: String? = null,
    ): String =
        innerTube.next(
            YouTubeClient.WEB_REMIX,
            videoId = videoId,
            playlistId = playlistId,
            playlistSetVideoId = null,
            index = null,
            params = null,
            continuation = continuation,
        ).bodyAsText()

    // ------------------------------------------------------------------ search
    /** Compatibilidade com o smoke test antigo: busca limpa, apenas musicas. */
    suspend fun search(query: String, limit: Int = 40): List<Track> =
        search(query, SearchMode.SONGS).tracks.take(limit)

    /**
     * Busca em uma aba especifica. Na aba de musicas tambem buscamos artistas em
     * paralelo para montar a faixa de perfis oficiais acima dos resultados.
     */
    suspend fun search(query: String, mode: SearchMode): SearchResult = coroutineScope {
        if (query.isBlank()) return@coroutineScope SearchResult()
        val started = System.nanoTime()
        val result = when (mode) {
            SearchMode.SONGS -> {
                val tracksBody = async { searchRaw(query, SEARCH_PARAMS.getValue(SearchMode.SONGS)) }
                val artistsBody = async { searchRaw(query, SEARCH_PARAMS.getValue(SearchMode.ARTISTS)) }
                val tracksBodyText = tracksBody.await()
                SearchResult(
                    tracks = parseTracks(tracksBodyText, SearchMode.SONGS),
                    artists = parseArtists(artistsBody.await()),
                    continuation = parseSearchContinuation(tracksBodyText),
                )
            }

            SearchMode.ARTISTS -> {
                val body = searchRaw(query, SEARCH_PARAMS.getValue(SearchMode.ARTISTS))
                SearchResult(
                    artists = parseArtists(body),
                    continuation = parseSearchContinuation(body),
                )
            }

            SearchMode.ALBUMS -> {
                val body = searchRaw(query, SEARCH_PARAMS.getValue(SearchMode.ALBUMS))
                SearchResult(
                    cards = parseCards(body, SearchMode.ALBUMS),
                    continuation = parseSearchContinuation(body),
                )
            }

            SearchMode.PLAYLISTS -> {
                val body = searchRaw(query, SEARCH_PARAMS.getValue(SearchMode.PLAYLISTS))
                SearchResult(
                    cards = parseCards(body, SearchMode.PLAYLISTS),
                    continuation = parseSearchContinuation(body),
                )
            }

            SearchMode.VIDEOS -> {
                val body = searchRaw(query, SEARCH_PARAMS.getValue(SearchMode.VIDEOS))
                SearchResult(
                    tracks = parseTracks(body, SearchMode.VIDEOS),
                    continuation = parseSearchContinuation(body),
                )
            }
        }
        val elapsed = (System.nanoTime() - started) / 1_000_000
        com.openfy.core.Log.write(
            "yt: busca \"$query\" [${mode.name}] em ${elapsed}ms " +
                "(faixas=${result.tracks.size}, artistas=${result.artists.size}, cards=${result.cards.size})"
        )
        result
    }

    /** Busca somente faixas (sem a requisicao extra de artistas) para casar imports. */
    suspend fun searchSongs(query: String, limit: Int = 8): List<Track> {
        if (query.isBlank()) return emptyList()
        val body = searchRaw(query, SEARCH_PARAMS.getValue(SearchMode.SONGS))
        return parseTracks(body, SearchMode.SONGS).take(limit)
    }

    /** Pagina seguinte da busca; o token vem da resposta anterior. */
    suspend fun searchNext(mode: SearchMode, continuation: String): SearchResult {
        if (continuation.isBlank()) return SearchResult()
        val body = searchRawContinuation(continuation, SEARCH_PARAMS.getValue(mode))
        val root = parseRoot(body) ?: return SearchResult()
        val result = when (mode) {
            SearchMode.SONGS, SearchMode.VIDEOS -> SearchResult(
                tracks = parseTracks(body, mode),
                continuation = parseSearchContinuation(root),
            )
            SearchMode.ARTISTS -> SearchResult(
                artists = parseArtists(body),
                continuation = parseSearchContinuation(root),
            )
            SearchMode.ALBUMS, SearchMode.PLAYLISTS -> SearchResult(
                cards = parseCards(body, mode),
                continuation = parseSearchContinuation(root),
            )
        }
        com.openfy.core.Log.write(
            "yt: busca [${mode.name}] +${result.tracks.size + result.artists.size + result.cards.size} itens"
        )
        return result
    }

    private suspend fun searchRawContinuation(continuation: String, params: String?): String =
        innerTube.search(
            YouTubeClient.WEB_REMIX,
            query = "",
            params = params,
            continuation = continuation,
            setLogin = false,
        ).bodyAsText()

    private fun parseSearchContinuation(body: String): String? {
        val root = parseRoot(body) ?: return null
        return parseSearchContinuation(root)
    }

    private fun parseSearchContinuation(root: JsonObject): String? = root.continuationToken()

    /** Sugestoes do YouTube Music enquanto digita (get_search_suggestions). */
    suspend fun searchSuggestions(query: String): List<String> {
        val clean = query.trim()
        if (clean.isBlank()) return emptyList()
        val started = System.nanoTime()
        val body = runCatching {
            innerTube.getSearchSuggestions(YouTubeClient.WEB_REMIX, clean, false).bodyAsText()
        }.getOrNull() ?: return emptyList()
        val root = parseRoot(body) ?: return emptyList()
        val out = root.findAllObjects("searchSuggestionRenderer")
            .mapNotNull { it.obj("suggestion")?.textContent()?.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(8)
        val elapsed = (System.nanoTime() - started) / 1_000_000
        com.openfy.core.Log.write("yt: sugestoes \"$clean\" -> ${out.size} em ${elapsed}ms")
        return out
    }

    private fun parseTracks(body: String, mode: SearchMode): List<Track> {
        val root = parseRoot(body) ?: return emptyList()
        val out = LinkedHashMap<String, Track>()
        for (renderer in root.findAllObjects("musicResponsiveListItemRenderer")) {
            if (mode == SearchMode.SONGS && !isMusicTrack(renderer)) continue
            val track = parseTrack(renderer) ?: continue
            if (mode == SearchMode.SONGS && looksLikeClutter(track.title)) continue
            out.putIfAbsent(track.id, track)
            if (out.size >= (if (mode == SearchMode.VIDEOS) 60 else 40)) break
        }
        return out.values.toList()
    }

    private fun parseArtists(body: String): List<Artist> {
        val root = parseRoot(body) ?: return emptyList()
        val out = LinkedHashMap<String, Artist>()
        for (renderer in root.findAllObjects("musicResponsiveListItemRenderer")) {
            val artist = parseArtist(renderer) ?: continue
            out.putIfAbsent(artist.id, artist)
            if (out.size >= 20) break
        }
        return out.values.toList()
    }

    private fun parseCards(body: String, mode: SearchMode): List<MediaCard> {
        val root = parseRoot(body) ?: return emptyList()
        val out = LinkedHashMap<String, MediaCard>()
        for (renderer in root.findAllObjects("musicResponsiveListItemRenderer")) {
            val card = parseResponsiveCard(renderer) ?: continue
            out.putIfAbsent("${card.kind}:${card.id}", card)
        }
        for (renderer in root.findAllObjects("musicTwoRowItemRenderer")) {
            val card = parseTwoRowCard(renderer) ?: continue
            out.putIfAbsent("${card.kind}:${card.id}", card)
        }
        return out.values.filter { card ->
            val rightKind = when (mode) {
                SearchMode.ALBUMS -> card.kind == MediaKind.ALBUM
                SearchMode.PLAYLISTS -> card.kind == MediaKind.PLAYLIST
                else -> true
            }
            rightKind && !looksLikeClutter(card.title)
        }.take(40)
    }

    // ------------------------------------------------------------------ artist
    suspend fun artist(browseId: String): ArtistPage {
        val started = System.nanoTime()
        val root = parseRootOrThrow(browseRaw(browseId), "artista")
        val header = root.findAllObjects("musicImmersiveHeaderRenderer").firstOrNull()
        val micro = root.obj("microformat")?.obj("microformatDataRenderer")
        val name = header?.obj("title").textContent().trim()
            .ifBlank { micro?.str("title").orEmpty() }
            .ifBlank { "Artista" }
        val avatar = micro?.thumbUrl().orEmpty().ifBlank { header?.thumbUrl().orEmpty() }
        val banner = header?.thumbUrl().orEmpty()
        val listeners = header?.obj("monthlyListenerCount").textContent().trim()
        val description = header?.obj("description").textContent().trim()
        val radio = header?.obj("startRadioButton")
            ?.obj("buttonRenderer")
            ?.obj("navigationEndpoint")
            ?.obj("watchEndpoint")

        val topShelf = root.findAllObjects("musicShelfRenderer").firstOrNull()
        val topTracks = topShelf?.arr("contents").orEmpty()
            .mapNotNull { it.obj()?.obj("musicResponsiveListItemRenderer") }
            .filter(::isMusicTrack)
            .mapNotNull(::parseTrack)
            .map { track ->
                if (track.artist == UNKNOWN_ARTIST) track.copy(artist = name) else track
            }
            .distinctBy { it.id }
            .take(10)

        val sections = root.findAllObjects("musicCarouselShelfRenderer").mapNotNull { carousel ->
            val title = carousel.obj("header")
                ?.obj("musicCarouselShelfBasicHeaderRenderer")
                ?.obj("title")
                .textContent()
                .trim()
            if (title.isBlank() || isVideoSection(title)) return@mapNotNull null
            val cards = carousel.arr("contents").orEmpty()
                .mapNotNull(::parseCardElement)
                .filter { it.kind != MediaKind.TRACK }
                .distinctBy { "${it.kind}:${it.id}" }
                .take(24)
            if (cards.isEmpty()) null else MediaSection(title, cards)
        }

        val radioVideoId = radio?.str("videoId") ?: topTracks.firstOrNull()?.id
        val radioPlaylistId = radio?.str("playlistId") ?: radioVideoId?.let { "RDAMVM$it" }
        val elapsed = (System.nanoTime() - started) / 1_000_000
        com.openfy.core.Log.write(
            "yt: artista $browseId em ${elapsed}ms (top=${topTracks.size}, secoes=${sections.size})"
        )
        return ArtistPage(
            id = browseId,
            name = name,
            avatar = avatar,
            banner = banner,
            listeners = listeners,
            description = description,
            topTracks = topTracks,
            sections = sections,
            radioVideoId = radioVideoId,
            radioPlaylistId = radioPlaylistId,
        )
    }

    // ------------------------------------------------------------------ collections
    suspend fun collection(browseId: String): RemoteCollection {
        val started = System.nanoTime()
        val root = parseRootOrThrow(browseRaw(browseId), "colecao")
        val header = root.findAllObjects("musicResponsiveHeaderRenderer").firstOrNull()
            ?: root.findAllObjects("musicDetailHeaderRenderer").firstOrNull()
            ?: root.findAllObjects("musicEditablePlaylistDetailHeaderRenderer").firstOrNull()
        val micro = root.obj("microformat")?.obj("microformatDataRenderer")
        val title = header?.obj("title").textContent().trim()
            .ifBlank { micro?.str("title").orEmpty() }
            .ifBlank { "Coleção" }
        val subtitle = listOf(
            header?.obj("subtitle").textContent().trim(),
            header?.obj("secondSubtitle").textContent().trim(),
        ).filter { it.isNotBlank() }.joinToString(" • ")
        val description = header?.obj("description").textContent().trim()
        val thumbnail = header?.thumbUrl().orEmpty().ifBlank { micro?.thumbUrl().orEmpty() }
        val strapline = header?.obj("straplineTextOne").textContent().trim()

        val isAlbum = browseId.startsWith("MPREb_") ||
            subtitle.startsWith("Álbum") ||
            subtitle.startsWith("Album") ||
            subtitle.startsWith("Single")

        val shelf = root.findAllObjects("musicPlaylistShelfRenderer").firstOrNull()
            ?: root.findAllObjects("musicShelfRenderer").firstOrNull()
        // Em alguns álbuns a coluna do artista vem vazia; o cabeçalho sempre traz
        // o artista em straplineTextOne. Isso evita "YouTube Music" como artista.
        val tracks = parseShelfTracks(
            shelf = shelf,
            fallbackArtist = strapline.takeIf { isAlbum }.orEmpty(),
            fallbackAlbum = title.takeIf { isAlbum }.orEmpty(),
            // Faixas de album nao trazem thumbnail proprio; a capa da colecao
            // e o que o proprio YouTube Music mostra na lista.
            fallbackThumbnail = thumbnail,
        )
        val continuation = shelf?.continuationToken()
        val elapsed = (System.nanoTime() - started) / 1_000_000
        com.openfy.core.Log.write(
            "yt: colecao $browseId em ${elapsed}ms (faixas=${tracks.size}, continuacao=${continuation != null})"
        )
        return RemoteCollection(
            id = browseId,
            title = title,
            subtitle = subtitle,
            description = description,
            thumbnail = thumbnail,
            kind = if (isAlbum) CollectionKind.ALBUM else CollectionKind.PLAYLIST,
            tracks = tracks,
            continuation = continuation,
        )
    }

    suspend fun collectionNext(continuation: String): RemoteTracksPage {
        val root = parseRootOrThrow(
            browseRaw(browseId = "", continuation = continuation),
            "continuacao da colecao",
        )
        val shelf = root.obj("continuationContents")?.obj("musicPlaylistShelfContinuation")
            ?: root.obj("continuationContents")?.obj("musicShelfContinuation")
        // Playlists grandes respondem com appendContinuationItemsAction em vez
        // de continuationContents; os itens ficam na mesma forma de sempre.
        val tracks = parseShelfTracks(shelf).ifEmpty { parseContinuationItems(root) }
        val next = shelf?.continuationToken()
            ?.takeIf { it != continuation }
            ?: root.findAllObjects("continuationCommand").firstOrNull()
                ?.str("token")
                ?.takeIf { it != continuation }
            ?: root.continuationToken()?.takeIf { it != continuation }
        com.openfy.core.Log.write("yt: colecao +${tracks.size} faixas")
        return RemoteTracksPage(tracks = tracks, continuation = next)
    }

    private fun parseContinuationItems(root: JsonObject): List<Track> {
        val items = root.findAllObjects("appendContinuationItemsAction").firstOrNull()
            ?.arr("continuationItems")
            ?: root.findAllObjects("reloadContinuationItemsCommand").firstOrNull()
                ?.arr("continuationItems")
            ?: return emptyList()
        return items.mapNotNull { it.obj()?.obj("musicResponsiveListItemRenderer") }
            .filter(::isMusicTrack)
            .mapNotNull(::parseTrack)
            .distinctBy { it.id }
    }

    private fun parseShelfTracks(
        shelf: JsonObject?,
        fallbackArtist: String = "",
        fallbackAlbum: String = "",
        fallbackThumbnail: String = "",
    ): List<Track> {
        if (shelf == null) return emptyList()
        val renderers = shelf.arr("contents").orEmpty()
            .mapNotNull { it.obj()?.obj("musicResponsiveListItemRenderer") }
            .ifEmpty { shelf.findAllObjects("musicResponsiveListItemRenderer") }
        return renderers
            .filter(::isMusicTrack)
            .mapNotNull(::parseTrack)
            .map { track ->
                track.copy(
                    artist = if (track.artist == UNKNOWN_ARTIST && fallbackArtist.isNotBlank()) {
                        fallbackArtist
                    } else {
                        track.artist
                    },
                    album = track.album.ifBlank { fallbackAlbum },
                    thumbnail = track.thumbnail.ifBlank { fallbackThumbnail },
                )
            }
            .distinctBy { it.id }
    }

    // ------------------------------------------------------------------ explore
    private data class ExploreSeed(val query: String, val title: String)

    /**
     * O Explorar combina fontes estaveis (busca com filtro de playlists em
     * destaque) com os lancamentos e as playlists da comunidade da home.
     * A home sozinha e personalizada e muda de ordem, por isso nao e a base.
     */
    suspend fun explore(): ExploreFeed = coroutineScope {
        val started = System.nanoTime()
        val playlistParams = SEARCH_PARAMS.getValue(SearchMode.PLAYLISTS)
        val seedRequests = EXPLORE_SEEDS.map { seed ->
            seed to async {
                runCatching { searchRaw(seed.query, playlistParams) }.getOrNull()
            }
        }
        val chartsDeferred = async { runCatching { browseRaw("FEmusic_charts") }.getOrNull() }
        val newDeferred = async { runCatching { browseRaw("FEmusic_explore") }.getOrNull() }
        val homeDeferred = async { runCatching { browseRaw("FEmusic_home") }.getOrNull() }

        val sections = mutableListOf<MediaSection>()
        val seen = HashSet<String>()

        fun addSection(
            title: String,
            cards: List<MediaCard>,
            limit: Int,
            albumOnly: Boolean = false,
        ) {
            val picked = cards.asSequence()
                .filter { it.kind == MediaKind.ALBUM || it.kind == MediaKind.PLAYLIST }
                .filter { !albumOnly || it.kind == MediaKind.ALBUM }
                .filterNot { looksLikeClutter(it.title) }
                .filter { seen.add("${it.kind}:${it.id}") }
                .take(limit)
                .toList()
            if (picked.isNotEmpty()) sections += MediaSection(title, picked)
        }

        for ((seed, deferred) in seedRequests) {
            val body = deferred.await() ?: continue
            addSection(seed.title, parseCards(body, SearchMode.PLAYLISTS), limit = 12)
        }

        fun carouselTitle(carousel: JsonObject): String =
            carousel.obj("header")
                ?.obj("musicCarouselShelfBasicHeaderRenderer")
                ?.obj("title")
                .textContent()
                .trim()

        fun addAlbumSections(body: String?) {
            val root = body?.let(::parseRoot) ?: return
            for (carousel in root.findAllObjects("musicCarouselShelfRenderer")) {
                val title = carouselTitle(carousel)
                if (title.isBlank() || isMusicOrVideoSection(title)) continue
                val cards = carousel.arr("contents").orEmpty()
                    .mapNotNull(::parseCardElement)
                addSection(title, cards, limit = 24, albumOnly = true)
            }
        }
        addAlbumSections(newDeferred.await())

        fun addCommunitySection(body: String?) {
            val root = body?.let(::parseRoot) ?: return
            for (carousel in root.findAllObjects("musicCarouselShelfRenderer")) {
                val title = carouselTitle(carousel)
                if (!title.contains("comunidade", ignoreCase = true)) continue
                val cards = carousel.arr("contents").orEmpty()
                    .mapNotNull(::parseCardElement)
                addSection("Playlists da comunidade", cards, limit = 10)
            }
        }
        addCommunitySection(homeDeferred.await())

        val topArtists = chartsDeferred.await()?.let(::parseRoot)
            ?.findAllObjects("musicResponsiveListItemRenderer")
            ?.mapNotNull(::parseArtist)
            ?.distinctBy { it.id }
            ?.take(20)
            .orEmpty()

        val elapsed = (System.nanoTime() - started) / 1_000_000
        com.openfy.core.Log.write(
            "yt: explorar em ${elapsed}ms (secoes=${sections.size}, artistas=${topArtists.size})"
        )
        ExploreFeed(
            sections = sections,
            topArtists = topArtists,
        )
    }

    // ------------------------------------------------------------------ charts
    /**
     * "Em alta": a playlist de musicas em alta do YouTube Music. A pagina
     * FEmusic_charts nao traz as faixas direto (so artistas e cards); as
     * musicas em alta ficam numa playlist PL4fGSI1pDJn... que descobrimos
     * ali e navegamos em seguida.
     */
    suspend fun charts(): List<Track> {
        val started = System.nanoTime()
        val root = parseRootOrThrow(browseRaw("FEmusic_charts"), "charts")
        val chartBrowse = root.findAllObjects("browseEndpoint")
            .mapNotNull { it.str("browseId") }
            .firstOrNull { it.startsWith("VLPL4fGSI1pDJn") }
        val tracks = if (chartBrowse != null) {
            runCatching { collection(chartBrowse) }.getOrNull()?.tracks.orEmpty()
        } else {
            emptyList()
        }
        val elapsed = (System.nanoTime() - started) / 1_000_000
        com.openfy.core.Log.write("yt: charts $chartBrowse -> ${tracks.size} faixas em ${elapsed}ms")
        return tracks
    }

    /** Descarta podcasts, videos e episodios das prateleiras de lancamentos. */
    private fun isMusicOrVideoSection(title: String): Boolean {
        if (isVideoSection(title)) return true
        val lower = title.lowercase()
        return lower.contains("podcast") ||
            lower.contains("episód") ||
            lower.contains("episode")
    }

    // ------------------------------------------------------------------ radio
    suspend fun radio(videoId: String, playlistId: String? = "RDAMVM$videoId"): RadioPage {
        val started = System.nanoTime()
        val root = parseRootOrThrow(nextRaw(videoId, playlistId), "radio")
        val page = parseRadio(root)
        val elapsed = (System.nanoTime() - started) / 1_000_000
        com.openfy.core.Log.write(
            "yt: radio $videoId em ${elapsed}ms (faixas=${page.tracks.size}, continuacao=${page.continuation != null})"
        )
        return page
    }

    suspend fun radioNext(continuation: String): RadioPage {
        val root = parseRootOrThrow(nextRaw(continuation = continuation), "radio (continuacao)")
        val page = parseRadio(root)
        com.openfy.core.Log.write("yt: radio +${page.tracks.size} faixas")
        return page
    }

    private fun parseRadio(root: JsonObject): RadioPage {
        val tracks = root.findAllObjects("playlistPanelVideoRenderer")
            .mapNotNull(::parseRadioTrack)
            .distinctBy { it.id }
        val continuation = root.findAllObjects("nextRadioContinuationData").firstOrNull()?.str("continuation")
            ?: root.findAllObjects("nextContinuationData").firstOrNull()?.str("continuation")
        return RadioPage(tracks = tracks, continuation = continuation)
    }

    private fun parseRadioTrack(renderer: JsonObject): Track? {
        val videoId = renderer.str("videoId")
            ?: renderer.obj("navigationEndpoint")?.obj("watchEndpoint")?.str("videoId")
            ?: return null
        val title = renderer.obj("title").textContent().trim()
        if (title.isBlank()) return null
        // O byline da radio mistura artista, album, ano e "visualizacoes/gostei".
        // Ficamos com o primeiro run que aponta para um artista.
        val byline = renderer.obj("longBylineText") ?: renderer.obj("shortBylineText")
        val runs = byline?.arr("runs").orEmpty()
        val artist = runs.firstOrNull { run ->
            run.obj()?.obj("navigationEndpoint")?.obj("browseEndpoint") != null
        }?.obj()?.str("text")
            ?: runs.firstOrNull()?.obj()?.str("text")
            ?: ""
        val length = renderer.obj("lengthText").textContent().trim()
        return Track(
            id = videoId,
            title = title,
            artist = artist.ifBlank { "Rádio" },
            durationSec = if (DURATION.matches(length)) toSeconds(length) else 0,
            thumbnail = renderer.thumbUrl(),
        )
    }

    // ------------------------------------------------------------------ item parsers
    private fun parseTrack(renderer: JsonObject): Track? {
        val videoId = renderer.obj("playlistItemData")?.str("videoId")
            ?: renderer.obj("navigationEndpoint")?.obj("watchEndpoint")?.str("videoId")
            ?: renderer.findAllObjects("watchEndpoint").firstOrNull()?.str("videoId")
            ?: return null
        val columns = renderer.arr("flexColumns") ?: return null
        val title = columns.firstOrNull()
            ?.obj()
            ?.obj("musicResponsiveListItemFlexColumnRenderer")
            ?.obj("text")
            .textContent()
            .trim()
        if (title.isBlank()) return null

        val info = mutableListOf<List<String>>()
        for (column in columns.drop(1)) {
            val text = column.obj()
                ?.obj("musicResponsiveListItemFlexColumnRenderer")
                ?.obj("text")
                ?: continue
            info += splitRuns(text.runsTexts())
        }
        for (column in renderer.arr("fixedColumns").orEmpty()) {
            val text = column.obj()
                ?.obj("musicResponsiveListItemFixedColumnRenderer")
                ?.obj("text")
                ?: continue
            info += splitRuns(text.runsTexts())
        }

        val groups = info.map(::groupText)
        val artistIndex = groups.indexOfFirst(::isInfoText)
        val artist = if (artistIndex >= 0) groups[artistIndex] else ""
        val album = groups.drop(artistIndex + 1)
            .firstOrNull { isInfoText(it) && it != artist }
            .orEmpty()
        val duration = groups.firstOrNull { DURATION.matches(it) }?.let(::toSeconds) ?: 0

        return Track(
            id = videoId,
            title = title,
            artist = artist.ifBlank { "YouTube Music" },
            album = album,
            durationSec = duration,
            thumbnail = renderer.thumbUrl(),
        )
    }

    private fun parseArtist(renderer: JsonObject): Artist? {
        val browse = renderer.obj("navigationEndpoint")?.obj("browseEndpoint") ?: return null
        val id = browse.str("browseId") ?: return null
        val pageType = browse.obj("browseEndpointContextSupportedConfigs")
            ?.obj("browseEndpointContextMusicConfig")
            ?.str("pageType")
        if (pageType != "MUSIC_PAGE_TYPE_ARTIST" && !id.startsWith("UC")) return null

        val columns = renderer.arr("flexColumns") ?: return null
        val name = columns.firstOrNull()
            ?.obj()
            ?.obj("musicResponsiveListItemFlexColumnRenderer")
            ?.obj("text")
            .textContent()
            .trim()
        if (name.isBlank()) return null

        val subtitle = mutableListOf<String>()
        for (column in columns.drop(1)) {
            val text = column.obj()
                ?.obj("musicResponsiveListItemFlexColumnRenderer")
                ?.obj("text")
                ?: continue
            for (group in splitRuns(text.runsTexts())) {
                val value = groupText(group)
                if (value.isNotBlank() && value !in ARTIST_TYPE_LABELS) subtitle += value
            }
        }
        return Artist(
            id = id,
            name = name,
            thumbnail = renderer.thumbUrl(),
            subtitle = subtitle.distinct().joinToString(" • "),
        )
    }

    private fun parseResponsiveCard(renderer: JsonObject): MediaCard? {
        val columns = renderer.arr("flexColumns") ?: return null
        val title = columns.firstOrNull()
            ?.obj()
            ?.obj("musicResponsiveListItemFlexColumnRenderer")
            ?.obj("text")
            .textContent()
            .trim()
        if (title.isBlank()) return null

        val parts = mutableListOf<String>()
        for (column in columns.drop(1)) {
            val text = column.obj()
                ?.obj("musicResponsiveListItemFlexColumnRenderer")
                ?.obj("text")
                ?: continue
            for (group in splitRuns(text.runsTexts())) {
                val value = groupText(group)
                if (value.isNotBlank()) parts += value
            }
        }

        val browse = renderer.obj("navigationEndpoint")?.obj("browseEndpoint") ?: return null
        val id = browse.str("browseId") ?: return null
        val kind = kindFrom(browse, id) ?: return null
        return MediaCard(
            id = id,
            title = title,
            subtitle = cardSubtitle(parts),
            thumbnail = renderer.thumbUrl(),
            kind = kind,
        )
    }

    private fun parseTwoRowCard(renderer: JsonObject): MediaCard? {
        val title = renderer.obj("title").textContent().trim()
        if (title.isBlank()) return null
        val subtitle = renderer.obj("subtitle").textContent().trim()
        val nav = renderer.obj("navigationEndpoint")
        val browse = nav?.obj("browseEndpoint")
        val browseId = browse?.str("browseId")
        val playlistId = nav?.obj("watchPlaylistEndpoint")?.str("playlistId")
        val videoId = nav?.obj("watchEndpoint")?.str("videoId")
        val id = browseId ?: playlistId ?: videoId ?: return null
        val kind = when {
            browse != null -> kindFrom(browse, id)
            playlistId != null -> MediaKind.PLAYLIST
            videoId != null -> MediaKind.TRACK
            else -> null
        } ?: return null
        return MediaCard(
            id = id,
            title = title,
            subtitle = subtitle,
            thumbnail = renderer.thumbUrl(),
            kind = kind,
        )
    }

    private fun parseCardElement(element: JsonElement): MediaCard? {
        val box = element as? JsonObject ?: return null
        box.obj("musicTwoRowItemRenderer")?.let { return parseTwoRowCard(it) }
        box.obj("musicResponsiveListItemRenderer")?.let { return parseResponsiveCard(it) }
        return null
    }

    private fun kindFrom(browse: JsonObject, id: String): MediaKind? {
        val pageType = browse.obj("browseEndpointContextSupportedConfigs")
            ?.obj("browseEndpointContextMusicConfig")
            ?.str("pageType")
        return when {
            pageType == "MUSIC_PAGE_TYPE_ARTIST" || id.startsWith("UC") -> MediaKind.ARTIST
            pageType == "MUSIC_PAGE_TYPE_ALBUM" || id.startsWith("MPREb_") -> MediaKind.ALBUM
            pageType == "MUSIC_PAGE_TYPE_PLAYLIST" ||
                id.startsWith("VL") ||
                id.startsWith("PL") ||
                id.startsWith("RD") ||
                id.startsWith("OLAK5uy_") -> MediaKind.PLAYLIST
            else -> null
        }
    }

    private fun cardSubtitle(parts: List<String>): String =
        parts.filter { it.isNotBlank() && it !in CARD_TYPE_LABELS }
            .distinct()
            .joinToString(" • ")

    private fun isMusicTrack(renderer: JsonObject): Boolean {
        val type = renderer.findAllObjects("watchEndpointMusicConfig")
            .firstOrNull()
            ?.str("musicVideoType")
            ?: return true
        return type in MUSIC_TYPES
    }

    private fun isVideoSection(title: String): Boolean {
        val lower = title.lowercase()
        return VIDEO_SECTION_WORDS.any { lower.contains(it) }
    }

    private fun looksLikeClutter(title: String): Boolean {
        val lower = title.lowercase()
        return CLUTTER_WORDS.any { lower.contains(it) }
    }

    private fun JsonObject.continuationToken(): String? {
        findAllObjects("nextRadioContinuationData").firstOrNull()?.str("continuation")?.let { return it }
        findAllObjects("nextContinuationData").firstOrNull()?.str("continuation")?.let { return it }
        // Formato novo: o token fica em continuationItemRenderer.continuationEndpoint.
        findAllObjects("continuationCommand").firstOrNull()?.str("token")?.let { return it }
        for (entry in arr("continuations").orEmpty()) {
            entry.obj()?.str("continuation")?.let { return it }
            entry.obj()?.obj("nextRadioContinuationData")?.str("continuation")?.let { return it }
            entry.obj()?.obj("nextContinuationData")?.str("continuation")?.let { return it }
            entry.obj()?.obj("continuationCommand")?.str("token")?.let { return it }
        }
        return null
    }

    // ------------------------------------------------------------------ playback
    /**
     * Chamado pela thread do StreamServer. Bloqueia ate a extracao terminar.
     * A URL expira, mas quem consome e sempre o servidor local, que reextrai quando
     * preciso — por isso o cache curto aqui e so economia de quota.
     */
    fun media(videoId: String): Media? {
        val cached = streamCache[videoId]
        if (cached != null && System.currentTimeMillis() - cached.at < STREAM_TTL_MS) {
            val url = cached.stream.audioUrl
            if (url.isBlank()) return null
            return Media.Remote(url, cached.stream.headers)
        }
        val started = System.nanoTime()
        com.openfy.core.Log.write("yt: extraindo $videoId")
        val stream = runCatching {
            kotlinx.coroutines.runBlocking {
                withTimeout(EXTRACT_TIMEOUT_MS) {
                    extractMutex.withLock {
                        extractor.extract(
                            videoId,
                            ContentHints().withStreamCapabilities(
                                allowHls = false,
                                allowSabr = false,
                                allowBoundedRange = true,
                            ),
                            emptySet(),
                            quality,
                            generateClientPlaybackNonce(),
                        )
                    }
                }
            }
        }.onFailure {
            val elapsed = (System.nanoTime() - started) / 1_000_000
            com.openfy.core.Log.write(
                "yt: extracao falhou $videoId em ${elapsed}ms: " +
                    "${it::class.simpleName}: ${it.message}"
            )
        }.getOrNull() ?: return null

        val elapsed = (System.nanoTime() - started) / 1_000_000
        val url = stream.audioUrl
        if (url.isBlank()) {
            com.openfy.core.Log.write("yt: extracao sem audio $videoId em ${elapsed}ms")
            return null
        }
        com.openfy.core.Log.write("yt: extracao ok $videoId em ${elapsed}ms (${URI(url).host})")
        streamCache[videoId] = CachedStream(stream, System.currentTimeMillis())
        return Media.Remote(url, stream.headers)
    }

    fun forgetStream(videoId: String) {
        streamCache.remove(videoId)
    }

    // ------------------------------------------------------------------ json helpers
    private fun parseRoot(text: String): JsonObject? {
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
        if (root == null) {
            com.openfy.core.Log.write("yt: resposta nao e JSON: ${text.take(200)}")
            return null
        }
        if (root["error"] != null) {
            com.openfy.core.Log.write("yt: erro do servidor: ${root["error"]}")
            return null
        }
        return root
    }

    private fun parseRootOrThrow(text: String, what: String): JsonObject {
        val root = parseRoot(text)
        if (root == null) throw IllegalStateException("Resposta inválida do YouTube Music ($what).")
        return root
    }

    private fun JsonElement?.obj(): JsonObject? = this as? JsonObject
    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
    private fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotEmpty() }

    private fun JsonElement?.textContent(): String = when (this) {
        is JsonPrimitive -> contentOrNull.orEmpty()
        is JsonObject -> str("simpleText")
            ?: arr("runs")?.joinToString("") { run -> run.obj()?.str("text").orEmpty() }.orEmpty()
        else -> ""
    }

    private fun JsonObject.runsTexts(): List<String> =
        arr("runs")?.mapNotNull { it.obj()?.str("text") }
            ?: listOfNotNull(str("simpleText"))

    private fun JsonObject.thumbUrl(): String {
        val direct = obj("thumbnail")
        val renderer = obj("thumbnailRenderer")
        val holder = direct?.obj("musicThumbnailRenderer")
            ?: direct
            ?: renderer?.obj("musicThumbnailRenderer")
        val thumbs = holder?.obj("thumbnail")?.arr("thumbnails")
            ?: holder?.arr("thumbnails")
            ?: direct?.arr("thumbnails")
            ?: return ""
        return thumbs.lastOrNull()?.obj()?.str("url")?.let(::normalizeUrl).orEmpty()
    }

    private fun JsonObject.findAllObjects(key: String): List<JsonObject> {
        val out = mutableListOf<JsonObject>()
        fun visit(element: JsonElement) {
            when (element) {
                is JsonObject -> {
                    for ((name, value) in element) {
                        if (name == key && value is JsonObject) out += value
                        visit(value)
                    }
                }
                is JsonArray -> for (value in element) visit(value)
                else -> Unit
            }
        }
        visit(this)
        return out
    }

    private fun splitRuns(runs: List<String>): List<List<String>> {
        val groups = mutableListOf<List<String>>()
        var current = mutableListOf<String>()
        for (run in runs) {
            val trimmed = run.trim()
            if (trimmed == "•" || trimmed == "·") {
                if (current.isNotEmpty()) {
                    groups += current
                    current = mutableListOf()
                }
            } else if (run.isNotBlank()) {
                current += run
            }
        }
        if (current.isNotEmpty()) groups += current
        return groups
    }

    private fun groupText(group: List<String>): String =
        group.joinToString("").trim().replace(Regex("\\s+"), " ")

    private fun isInfoText(value: String): Boolean =
        value.length > 1 &&
            value.any { it.isLetterOrDigit() } &&
            value !in TYPE_LABELS &&
            !DURATION.matches(value) &&
            !YEAR.matches(value) &&
            COUNT_WORDS.none { value.contains(it, ignoreCase = true) } &&
            !value.equals("Explicit", ignoreCase = true)

    private fun normalizeUrl(url: String): String = when {
        url.isBlank() -> ""
        url.startsWith("//") -> "https:$url"
        else -> url
    }

    private fun toSeconds(value: String): Int {
        val parts = value.split(":")
        return when (parts.size) {
            2 -> (parts[0].toIntOrNull() ?: 0) * 60 + (parts[1].toIntOrNull() ?: 0)
            3 -> (parts[0].toIntOrNull() ?: 0) * 3600 +
                (parts[1].toIntOrNull() ?: 0) * 60 + (parts[2].toIntOrNull() ?: 0)
            else -> 0
        }
    }

    private companion object {
        const val UNKNOWN_ARTIST = "YouTube Music"

        val DURATION = Regex("^\\d{1,2}:\\d{2}$")
        val YEAR = Regex("^\\d{4}$")
        val TYPE_LABELS = setOf(
            "Música", "Musica", "Song", "Single", "Álbum", "Album", "EP",
            "Playlist", "Vídeo", "Video", "Artista", "Artist",
        )
        val ARTIST_TYPE_LABELS = setOf("Artista", "Artist")
        val CARD_TYPE_LABELS = setOf(
            "Música", "Musica", "Song", "Single", "Álbum", "Album", "EP",
            "Playlist", "Vídeo", "Video", "Artista", "Artist",
        )
        val COUNT_WORDS = listOf(
            "Tocou", "visualiza", "ouvintes", "inscritos", "views",
            "plays", "listeners", "subscribers",
        )
        val MUSIC_TYPES = setOf(
            "MUSIC_VIDEO_TYPE_ATV",
            "MUSIC_VIDEO_TYPE_OMV",
            "MUSIC_VIDEO_TYPE_OFFICIAL_SOURCE_MUSIC",
        )
        val VIDEO_SECTION_WORDS = listOf(
            "vídeo", "video", "shorts", "ao vivo", "live", "apresenta",
            "podcast", "episódio", "episode",
        )
        val CLUTTER_WORDS = listOf(
            "karaoke", "backing track", "made famous", "tribute",
            "lyrics", "legendado", "tradução", "reaction", "8d audio",
            "slowed + reverb", "sped up", "podcast", "episódio",
        )

        val SEARCH_PARAMS = mapOf(
            SearchMode.SONGS to "EgWKAQIIAWoKEAkQBRAKEAMQBA==",
            SearchMode.ARTISTS to "EgWKAQIgAWoKEAkQChAFEAMQBA==",
            SearchMode.ALBUMS to "EgWKAQIYAWoKEAkQChAFEAMQBA==",
            SearchMode.PLAYLISTS to "EgeKAQQoADgBahIQBBADEAUQCRAKEBUQEBAOEBE=",
            SearchMode.VIDEOS to "EgWKAQIQAWoKEAkQChAFEAMQBA==",
        )

        /** Sementes de playlists em destaque; mantem o Explorar previsivel. */
        val EXPLORE_SEEDS = listOf(
            ExploreSeed("hits brasil", "Hits do Brasil"),
            ExploreSeed("pop brasil", "Pop"),
            ExploreSeed("sertanejo", "Sertanejo"),
            ExploreSeed("rock brasil", "Rock"),
            ExploreSeed("rap brasil", "Rap & Trap"),
            ExploreSeed("funk", "Funk & Pagode"),
            ExploreSeed("mpb brasil", "MPB & Bossa"),
        )

        const val STREAM_TTL_MS = 4 * 60 * 1000L
        const val EXTRACT_TIMEOUT_MS = 60_000L
    }
}

/**
 * Sem WebView no desktop, nenhum PoToken e gerado. O extrator entao escolhe
 * clientes que funcionam sem atestacao — e por isso que a extracao e a parte
 * que precisa de teste real antes de qualquer outra coisa.
 */
private object NoTokens : TokenProvider {
    override suspend fun getPoToken(
        videoId: String,
        visitorData: String,
        cookie: String?,
    ): PoTokenResult? = null
}
