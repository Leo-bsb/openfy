package com.openfy.core

import com.openfy.data.Db
import com.openfy.data.PushPlaybackState
import com.openfy.data.RemoteAuthSession
import com.openfy.data.RemotePlaybackState
import com.openfy.data.RemoteProfile
import com.openfy.data.SupabaseApi
import com.openfy.data.SupabaseException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID

@Serializable
data class StoredSession(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val email: String,
    val expiresAt: Long,
) {
    val isExpired: Boolean
        get() = expiresAt > 0 && System.currentTimeMillis() > expiresAt - 60_000
}

data class AccountState(
    val session: StoredSession? = null,
    val profile: RemoteProfile? = null,
    val profileExtras: ProfileExtras = ProfileExtras(),
    val profileStats: ProfileStatsData? = null,
    val profileBusy: Boolean = false,
    val profileStatsLoading: Boolean = false,
    val profileStatus: String? = null,
    val busy: Boolean = false,
    val status: String? = null,
    val error: String? = null,
    val lastSync: String? = null,
    val remotePlayback: RemotePlaybackState? = null,
)

/** Snapshot local de fila/posição; a conta completa com usuário e device. */
data class PlaybackSnapshot(
    val trackId: String?,
    val queue: List<Track>,
    val queueIndex: Int,
    val positionMs: Int,
    val playing: Boolean,
    val shuffle: Boolean,
    val repeat: String,
)

data class AccountRecap(
    val summary: RecapSummary,
    val topTracks: List<RecapTrack>,
    val topArtists: List<RecapArtist>,
)

/**
 * Conta + sync. O banco local continua sendo a fonte da UI; a nuvem é usada
 * para trocar playlists, itens e curtidas entre PC e Android.
 */
class AccountManager(
    private val db: Db,
    private val scope: CoroutineScope,
) {

    private val api = SupabaseApi()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val formatter = DateTimeFormatter.ofPattern("dd/MM HH:mm")

    private val _state = MutableStateFlow(
        AccountState(
            session = loadSession(),
            lastSync = db.get("account.lastSync"),
        ),
    )
    val state: StateFlow<AccountState> = _state.asStateFlow()

    /** Chamado quando um sync termina; o AppController atualiza a biblioteca. */
    var onSynced: (() -> Unit)? = null

    /** Snapshot da fila/posição do desktop, fornecido pelo AppController. */
    var playbackProvider: (() -> PlaybackSnapshot?)? = null

    /** Ajustes locais do desktop, fornecidos pelo AppController. */
    var settingsProvider: (() -> SyncedSettings?)? = null

    /** Aplica ajustes que chegaram do Android. */
    var settingsApplier: ((SyncedSettings) -> Unit)? = null

    private var requestJob: Job? = null
    private var playbackJob: Job? = null
    private var pendingSync = false
    private var prefsActivityId: String? = null
    private var prefsSaveJob: Job? = null
    private var settingsActivityId: String? = null
    private var settingsSaveJob: Job? = null
    private var profileStatsJob: Job? = null

    init {
        restore()
        scope.launch {
            while (true) {
                delay(120_000)
                if (_state.value.session != null && !_state.value.busy) {
                    syncNow()
                }
            }
        }
    }

    /** Agenda um sync curto depois de uma alteração local (debounce). */
    fun requestSync() {
        if (_state.value.session == null) return
        requestJob?.cancel()
        requestJob = scope.launch {
            delay(2_500)
            if (_state.value.busy) delay(3_000)
            syncNow()
        }
    }

    /** Empurra só a fila/posição depois de uma mudança no player. */
    fun requestPlaybackSync() {
        if (_state.value.session == null) return
        playbackJob?.cancel()
        playbackJob = scope.launch {
            delay(3_000)
            val session = ensureSession() ?: return@launch
            runCatching { pushPlaybackState(session) }
        }
    }

    /**
     * Agenda o envio dos ajustes depois de uma mudança local. O pull acontece
     * junto: se o celular mudou algo mais recente, ele vence e é aplicado aqui.
     */
    fun requestSettingsSync() {
        if (_state.value.session == null) return
        settingsSaveJob?.cancel()
        settingsSaveJob = scope.launch {
            delay(1_500)
            val session = ensureSession() ?: return@launch
            runCatching { syncSettings(session) }
        }
    }

    private suspend fun syncSettings(session: StoredSession) {
        val local = settingsProvider?.invoke()
        val remoteRow = runCatching {
            api.settingsPrefs(session.accessToken, session.userId)
        }.getOrNull()
        if (remoteRow != null) settingsActivityId = remoteRow.id
        val remote = remoteRow?.payload?.let { payload ->
            runCatching { json.decodeFromJsonElement<SyncedSettings>(payload) }.getOrNull()
        }
        val remoteUpdated = remote?.updatedAt?.takeIf { it > 0 } ?: epoch(remoteRow?.createdAt)
        val localUpdated = local?.updatedAt ?: 0L
        when {
            remote != null && remoteUpdated > localUpdated -> {
                runCatching { settingsApplier?.invoke(remote) }
            }
            local != null && (remote == null || localUpdated > remoteUpdated) -> {
                runCatching { pushSettings(session, local) }
            }
        }
    }

    private suspend fun pushSettings(session: StoredSession, settings: SyncedSettings) {
        val payloadSettings = settings.copy(updatedAt = System.currentTimeMillis())
        val newId = api.saveSettingsPrefs(
            session.accessToken,
            session.userId,
            json.encodeToJsonElement(payloadSettings),
        )
        val oldId = settingsActivityId
        settingsActivityId = newId
        runCatching { settingsApplier?.invoke(payloadSettings) }
        if (oldId != null && oldId != newId) {
            runCatching { api.deleteActivity(session.accessToken, oldId) }
        }
    }

    /** Some com o banner de "continuar do outro aparelho" sem alterar o remoto. */
    fun clearRemotePlayback() {
        _state.update { it.copy(remotePlayback = null) }
    }

    private fun loadSession(): StoredSession? =
        db.get("account.session")
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { json.decodeFromString<StoredSession>(it) }.getOrNull() }

    private fun saveSession(
        auth: RemoteAuthSession,
        previous: StoredSession? = null,
        emailFallback: String = "",
    ): StoredSession {
        val userId = auth.user?.id?.takeIf { it.isNotBlank() }
            ?: previous?.userId
            ?: throw SupabaseException(0, "A sessão não trouxe o usuário.")
        val email = auth.user?.email?.takeIf { it.isNotBlank() }
            ?: previous?.email
            ?: emailFallback
        val expiresAt = System.currentTimeMillis() +
            (if (auth.expiresIn > 0) auth.expiresIn else 3600L) * 1000L
        val stored = StoredSession(
            accessToken = auth.accessToken,
            refreshToken = auth.refreshToken.ifBlank { previous?.refreshToken ?: "" },
            userId = userId,
            email = email,
            expiresAt = expiresAt,
        )
        db.put("account.session", json.encodeToString(stored))
        ensureLibraryOwner(userId)
        return stored
    }

    /**
     * O desktop é local-first, mas a biblioteca local pertence a uma conta.
     * No primeiro login de um banco antigo ele é adotado; se outra conta entrar
     * no mesmo PC, a biblioteca local é limpa e repovoada pela nuvem.
     */
    private fun ensureLibraryOwner(userId: String) {
        val owner = db.libraryOwner()
        if (owner.isBlank()) {
            db.setLibraryOwner(userId)
        } else if (owner != userId) {
            db.resetLibrary()
            db.setLibraryOwner(userId)
        }
    }

    private fun clearSession() {
        db.put("account.session", "")
        _state.update { AccountState(lastSync = it.lastSync) }
    }

    private fun restore() {
        scope.launch {
            val current = _state.value.session
            if (current == null) {
                _state.update { it.copy(status = null) }
                return@launch
            }
            if (!current.isExpired) {
                loadProfile(current)
                syncNow()
                return@launch
            }
            if (current.refreshToken.isBlank()) {
                clearSession()
                return@launch
            }
            runCatching {
                val refreshed = api.refresh(current.refreshToken)
                saveSession(refreshed, previous = current)
            }.onSuccess { stored ->
                _state.update { it.copy(session = stored) }
                loadProfile(stored)
                syncNow()
            }.onFailure {
                clearSession()
            }
        }
    }

    private suspend fun ensureSession(): StoredSession? {
        val current = _state.value.session ?: return null
        if (!current.isExpired) return current
        if (current.refreshToken.isBlank()) {
            clearSession()
            return null
        }
        return runCatching {
            val refreshed = api.refresh(current.refreshToken)
            saveSession(refreshed, previous = current)
        }.onSuccess { stored ->
            _state.update { it.copy(session = stored) }
        }.onFailure {
            clearSession()
        }.getOrNull()
    }

    private suspend fun loadProfile(session: StoredSession) {
        runCatching { api.profile(session.accessToken, session.userId) }
            .onSuccess { profile ->
                if (profile != null) {
                    _state.update { it.copy(profile = profile) }
                }
            }
        loadProfileExtras(session)
    }

    /**
     * Carrega o snapshot privado do perfil. O cache local vence quando tem
     * updatedAt maior (edição feita offline); o remoto vence caso contrário.
     */
    private suspend fun loadProfileExtras(session: StoredSession) {
        val local = cachedExtras(session.userId)
        val remote = runCatching { api.profilePrefs(session.accessToken, session.userId) }.getOrNull()
        val remoteExtras = remote?.payload?.let { payload ->
            runCatching { json.decodeFromJsonElement<ProfileExtras>(payload) }.getOrNull()
        }
        prefsActivityId = remote?.id
        val chosen = when {
            remoteExtras == null -> local ?: ProfileExtras()
            local == null -> remoteExtras
            local.updatedAt > remoteExtras.updatedAt -> local
            else -> remoteExtras
        }.sanitized()
        _state.update { it.copy(profileExtras = chosen) }
        cacheExtras(session.userId, chosen)
        cachedStats(session.userId)?.let { stats ->
            _state.update { it.copy(profileStats = stats) }
        }
        if (local != null && local.updatedAt > (remoteExtras?.updatedAt ?: 0L)) {
            requestPrefsSave()
        }
    }

    private fun prefsKey(userId: String) = "profile.prefs.$userId"

    private fun statsKey(userId: String) = "profile.stats.$userId"

    private fun cachedExtras(userId: String): ProfileExtras? =
        db.get(prefsKey(userId))
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { json.decodeFromString<ProfileExtras>(it) }.getOrNull() }

    private fun cacheExtras(userId: String, value: ProfileExtras) {
        runCatching { db.put(prefsKey(userId), json.encodeToString(value)) }
    }

    private fun cachedStats(userId: String): ProfileStatsData? =
        db.get(statsKey(userId))
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { json.decodeFromString<ProfileStatsData>(it) }.getOrNull() }

    private fun cacheStats(userId: String, value: ProfileStatsData) {
        runCatching { db.put(statsKey(userId), json.encodeToString(value)) }
    }

    /** Atualiza o perfil inteiro: nome, avatar, capa e preferências. */
    fun saveProfile(
        displayName: String,
        extras: ProfileExtras,
        avatarFile: java.io.File? = null,
        coverFile: java.io.File? = null,
    ) {
        val session = _state.value.session
        if (session == null) {
            _state.update { it.copy(profileStatus = "Entre na conta para editar o perfil.") }
            return
        }
        if (_state.value.profileBusy) return
        scope.launch {
            _state.update {
                it.copy(profileBusy = true, profileStatus = "Salvando perfil…", error = null)
            }
            val cleanName = displayName.trim().take(60).ifBlank {
                _state.value.profile?.displayName.orEmpty()
            }
            var avatarUrl = _state.value.profile?.avatarUrl.orEmpty()
            var coverUrl = extras.coverUrl
            val result = runCatching {
                val active = ensureSession() ?: throw SupabaseException(0, "Sessão expirada.")
                if (avatarFile != null) {
                    avatarUrl = api.uploadProfileImage(
                        active.accessToken,
                        active.userId,
                        "avatars",
                        "avatar-${System.currentTimeMillis()}",
                        avatarFile,
                    )
                }
                if (coverFile != null) {
                    coverUrl = api.uploadProfileImage(
                        active.accessToken,
                        active.userId,
                        "covers",
                        "profile-${System.currentTimeMillis()}",
                        coverFile,
                    )
                }
                api.updateProfile(active.accessToken, active.userId, cleanName, avatarUrl)
                val next = extras.copy(
                    coverUrl = coverUrl,
                    updatedAt = System.currentTimeMillis(),
                ).sanitized()
                savePrefsRemote(active, next)
                val profile = api.profile(active.accessToken, active.userId)
                profile to next
            }
            result.onSuccess { (profile, next) ->
                if (profile != null) {
                    _state.update {
                        it.copy(
                            profile = profile,
                            profileExtras = next,
                            profileBusy = false,
                            profileStatus = "Perfil atualizado.",
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            profileExtras = next,
                            profileBusy = false,
                            profileStatus = "Perfil atualizado.",
                        )
                    }
                }
                cacheExtras(session.userId, next)
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        profileBusy = false,
                        profileStatus = null,
                        error = error.message ?: "Não foi possível salvar o perfil.",
                    )
                }
            }
        }
    }

    /** Pins, cor, bio e cartões de estatística: salva local e sincroniza em debounce. */
    fun updateProfileExtras(transform: (ProfileExtras) -> ProfileExtras) {
        val userId = _state.value.session?.userId
        val next = transform(_state.value.profileExtras)
            .copy(updatedAt = System.currentTimeMillis())
            .sanitized()
        _state.update { it.copy(profileExtras = next) }
        if (userId != null) cacheExtras(userId, next)
        requestPrefsSave()
    }

    private fun requestPrefsSave() {
        if (_state.value.session == null) return
        prefsSaveJob?.cancel()
        prefsSaveJob = scope.launch {
            delay(900)
            val active = ensureSession() ?: return@launch
            val extras = _state.value.profileExtras
            runCatching { savePrefsRemote(active, extras) }
                .onSuccess {
                    _state.update { it.copy(profileStatus = "Perfil sincronizado.") }
                }
                .onFailure {
                    _state.update {
                        it.copy(profileStatus = "Perfil salvo neste computador; sincroniza quando a rede voltar.")
                    }
                }
        }
    }

    private suspend fun savePrefsRemote(session: StoredSession, extras: ProfileExtras) {
        val payload = json.encodeToJsonElement(extras.sanitized())
        val newId = api.saveProfilePrefs(session.accessToken, session.userId, payload)
        val oldId = prefsActivityId
        prefsActivityId = newId
        cacheExtras(session.userId, extras)
        if (oldId != null && oldId != newId) {
            runCatching { api.deleteActivity(session.accessToken, oldId) }
        }
    }

    /** Estatísticas do mês corrente, agregadas no servidor; cache para abrir offline. */
    fun loadProfileStats(force: Boolean = false) {
        val session = _state.value.session ?: return
        if (_state.value.profileStatsLoading) return
        if (!force && _state.value.profileStats != null) return
        profileStatsJob?.cancel()
        profileStatsJob = scope.launch {
            _state.update { it.copy(profileStatsLoading = true) }
            val result = runCatching {
                val month = java.time.YearMonth.now()
                val listening = api.monthlyListening(session.accessToken, month).firstOrNull()
                val artists = api.monthlyTopArtists(session.accessToken, month, 8)
                val topRows = api.monthlyTopTracks(session.accessToken, month, 5)
                val byId = api.tracks(session.accessToken, topRows.map { it.trackId })
                    .associateBy { it.id }
                val likes = api.likes(session.accessToken).count { !it.deleted }
                val playlists = (
                    api.memberPlaylistIds(session.accessToken, session.userId) +
                        api.ownedPlaylistIds(session.accessToken, session.userId)
                    ).toSet().size
                ProfileStatsData(
                    month = month.atDay(1).toString(),
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
            result.onSuccess { stats ->
                cacheStats(session.userId, stats)
                _state.update { it.copy(profileStats = stats, profileStatsLoading = false) }
            }.onFailure {
                val cached = cachedStats(session.userId)
                _state.update {
                    it.copy(
                        profileStats = cached ?: it.profileStats,
                        profileStatsLoading = false,
                        profileStatus = if (cached != null) {
                            "Estatísticas offline (última atualização salva)."
                        } else {
                            "Não foi possível carregar as estatísticas."
                        },
                    )
                }
            }
        }
    }

    fun clearProfileStatus() {
        _state.update { it.copy(profileStatus = null) }
    }

    fun signIn(email: String, password: String) {
        if (email.isBlank() || password.length < 6) {
            _state.update { it.copy(error = "Informe e-mail e senha (mínimo 6 caracteres).") }
            return
        }
        scope.launch {
            _state.update { it.copy(busy = true, error = null, status = "Entrando…") }
            runCatching { api.signIn(email, password) }
                .onSuccess { auth ->
                    val stored = saveSession(auth, emailFallback = email)
                    _state.update {
                        it.copy(
                            session = stored,
                            busy = false,
                            status = "Conta conectada.",
                        )
                    }
                    loadProfile(stored)
                    syncNow()
                }
                .onFailure { error ->
                    _state.update { it.copy(busy = false, status = null, error = error.message) }
                }
        }
    }

    fun signUp(email: String, password: String) {
        if (email.isBlank() || password.length < 6) {
            _state.update { it.copy(error = "Informe e-mail e senha (mínimo 6 caracteres).") }
            return
        }
        scope.launch {
            _state.update { it.copy(busy = true, error = null, status = "Criando conta…") }
            runCatching {
                var auth = api.signUp(email, password)
                if (auth.accessToken.isBlank()) {
                    auth = api.signIn(email, password)
                }
                auth
            }.onSuccess { auth ->
                val stored = saveSession(auth, emailFallback = email)
                _state.update {
                    it.copy(
                        session = stored,
                        busy = false,
                        status = "Conta criada e conectada.",
                    )
                }
                loadProfile(stored)
                syncNow()
            }.onFailure { error ->
                _state.update { it.copy(busy = false, status = null, error = error.message) }
            }
        }
    }

    fun syncNow() {
        if (_state.value.session == null) return
        if (_state.value.busy) {
            pendingSync = true
            return
        }
        _state.update { it.copy(busy = true, error = null, status = "Sincronizando…") }
        scope.launch {
            val session = ensureSession()
            if (session == null) {
                _state.update {
                    it.copy(
                        busy = false,
                        status = null,
                        error = "Sessão expirada. Entre novamente.",
                    )
                }
                maybeRunPendingSync()
                return@launch
            }
            runCatching {
                SyncEngine(db, api).sync(session)
            }.onSuccess { result ->
                runCatching { syncPlaybackState(session) }
                runCatching { syncSettings(session) }
                if (_state.value.session?.userId != session.userId) {
                    _state.update { it.copy(busy = false, status = null) }
                } else {
                    val last = LocalDateTime.now().format(formatter)
                    db.put("account.lastSync", last)
                    _state.update {
                        it.copy(
                            busy = false,
                            session = session,
                            lastSync = last,
                            status = result.summary(),
                            error = null,
                        )
                    }
                    onSynced?.invoke()
                }
            }.onFailure { error ->
                if (error is SupabaseException && error.status == 401) {
                    clearSession()
                }
                _state.update {
                    it.copy(
                        busy = false,
                        status = null,
                        error = error.message ?: "Falha ao sincronizar.",
                    )
                }
            }
            maybeRunPendingSync()
        }
    }

    private fun maybeRunPendingSync() {
        if (!pendingSync) return
        pendingSync = false
        syncNow()
    }

    private fun deviceId(): String {
        val current = db.get("device.id")?.takeIf { it.isNotBlank() }
        if (current != null) return current
        val created = UUID.randomUUID().toString()
        db.put("device.id", created)
        return created
    }

    private fun epoch(value: String?): Long =
        runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }
            .recoverCatching { Instant.parse(value).toEpochMilli() }
            .getOrDefault(0L)

    private fun snapshotSignature(state: PlaybackSnapshot): String =
        listOf(
            state.trackId.orEmpty(),
            state.queueIndex.toString(),
            state.queue.joinToString("|") { it.id },
            state.shuffle.toString(),
            state.repeat,
            state.playing.toString(),
        ).joinToString(";")

    /** Envia a fila/posição local com upsert por usuário. */
    private suspend fun pushPlaybackState(session: StoredSession) {
        val local = playbackProvider?.invoke() ?: return
        val signature = snapshotSignature(local)
        val changed = signature != db.get("playback.lastPush")
        // Tocando, a posição anda mesmo com a assinatura igual; vale atualizar.
        if (!changed && !local.playing) return
        api.pushPlaybackState(
            session.accessToken,
            PushPlaybackState(
                userId = session.userId,
                deviceId = deviceId(),
                trackId = local.trackId,
                queue = local.queue,
                queueIndex = local.queueIndex,
                positionMs = local.positionMs,
                playing = local.playing,
                shuffle = local.shuffle,
                repeat = local.repeat,
            ),
        )
        db.put("playback.lastPush", signature)
        db.put("playback.lastPushMs", System.currentTimeMillis().toString())
    }

    /**
     * Handoff de fila/posição. O pull acontece antes do push; se o outro
     * aparelho tem um snapshot mais novo, ele é oferecido na Home em vez de ser
     * sobrescrito por uma sessão antiga e pausada deste PC.
     */
    private suspend fun syncPlaybackState(session: StoredSession) {
        val localDevice = deviceId()
        runCatching {
            api.registerDevice(
                session.accessToken,
                localDevice,
                "Openfy Desktop · ${System.getProperty("user.name") ?: "PC"}",
            )
        }
        val remote = runCatching { api.playbackState(session.accessToken) }.getOrNull()
        val remoteMs = epoch(remote?.updatedAt)
        val lastPushMs = db.get("playback.lastPushMs")?.toLongOrNull() ?: 0L
        val foreign = remote?.deviceId?.isNotBlank() == true && remote.deviceId != localDevice
        if (remote != null && foreign && remoteMs > lastPushMs) {
            _state.update { it.copy(remotePlayback = remote) }
        } else {
            _state.update { it.copy(remotePlayback = null) }
        }
        val local = playbackProvider?.invoke() ?: return
        val changed = snapshotSignature(local) != db.get("playback.lastPush")
        val remoteIsNewer = foreign && remoteMs > lastPushMs
        // Tocando, este PC é a sessão atual. Pausado, não sobrescreve uma fila
        // mais nova que acabou de vir do celular.
        if (changed && (local.playing || !remoteIsNewer)) {
            pushPlaybackState(session)
        }
    }

    fun signOut() {
        val session = _state.value.session
        scope.launch {
            session?.let { api.logout(it.accessToken) }
            clearSession()
        }
    }

    /** Garante que a playlist está no servidor e devolve o código de convite. */
    suspend fun shareCodeFor(playlistId: String): String? {
        val session = ensureSession() ?: return null
        var code = runCatching { api.shareCode(session.accessToken, playlistId) }.getOrNull()
        if (code == null) {
            runCatching { SyncEngine(db, api).sync(session) }
            code = runCatching { api.shareCode(session.accessToken, playlistId) }.getOrNull()
        }
        return code
    }

    /** Entra numa playlist por código/link e sincroniza o resultado. */
    suspend fun joinByCode(code: String): String? {
        val session = ensureSession() ?: return null
        val playlistId = api.joinPlaylist(session.accessToken, code)
        runCatching { SyncEngine(db, api).sync(session) }
        onSynced?.invoke()
        return playlistId
    }

    /** Sobe a capa para o Storage e devolve a URL pública (null se offline/sem conta). */
    suspend fun uploadCover(playlistId: String, file: java.io.File): String? {
        val session = ensureSession() ?: return null
        return runCatching {
            api.uploadCover(session.accessToken, session.userId, playlistId, file)
        }.getOrNull()
    }

    suspend fun fetchRecap(month: java.time.YearMonth): AccountRecap? {
        val session = ensureSession() ?: return null
        return runCatching {
            val listening = api.monthlyListening(session.accessToken, month).firstOrNull()
            val summary = RecapSummary(
                plays = listening?.plays?.toInt() ?: 0,
                playedMs = listening?.playedMs ?: 0,
                uniqueTracks = listening?.uniqueTracks?.toInt() ?: 0,
                uniqueArtists = api.monthlyTopArtists(session.accessToken, month, 50).size,
                completedPlays = listening?.completedPlays?.toInt() ?: 0,
                skippedPlays = listening?.skippedPlays?.toInt() ?: 0,
            )
            val remoteTracks = api.monthlyTopTracks(session.accessToken, month, 20)
            val byId = api.tracks(session.accessToken, remoteTracks.map { it.trackId }).associateBy { it.id }
            val topTracks = remoteTracks.mapNotNull { item ->
                byId[item.trackId]?.let { track ->
                    RecapTrack(
                        Track(
                            id = track.id,
                            title = track.title,
                            artist = track.artist,
                            album = track.album,
                            durationSec = track.duration,
                            thumbnail = track.thumbnail,
                        ),
                        plays = item.plays.toInt(),
                        playedMs = item.playedMs,
                    )
                }
            }
            val topArtists = api.monthlyTopArtists(session.accessToken, month, 8).map {
                RecapArtist(
                    name = it.artist,
                    plays = it.plays.toInt(),
                    playedMs = it.playedMs,
                )
            }
            AccountRecap(summary, topTracks, topArtists)
        }.getOrNull()
    }

    fun clearFeedback() {
        _state.update { it.copy(error = null, status = null) }
    }

    fun shutdown() {
        api.close()
    }
}
