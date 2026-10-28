package com.openfy.core

import com.openfy.data.Db
import com.openfy.data.RemoteAuthSession
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
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

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
    val busy: Boolean = false,
    val status: String? = null,
    val error: String? = null,
    val lastSync: String? = null,
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

    private var requestJob: Job? = null
    private var pendingSync = false

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
        return stored
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
