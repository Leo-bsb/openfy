package com.openfy.android.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.openfy.android.data.AuthSession
import com.openfy.android.data.PlaylistDto
import com.openfy.android.data.Profile
import com.openfy.android.data.SessionStore
import com.openfy.android.data.StoredSession
import com.openfy.android.data.SupabaseApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val loading: Boolean = true,
    val busy: Boolean = false,
    val session: StoredSession? = null,
    val profile: Profile? = null,
    val playlists: List<PlaylistDto> = emptyList(),
    val deviceName: String = "",
    val error: String? = null,
    val message: String? = null,
)

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val store = SessionStore(application)
    private val api = SupabaseApi()

    private val _state = MutableStateFlow(
        AuthUiState(deviceName = store.deviceName()),
    )
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    init {
        restore()
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
            // Token pode ter expirado entre o restore e a chamada; tenta uma vez.
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

    fun signOut() {
        val session = _state.value.session
        viewModelScope.launch {
            session?.let { api.logout(it.accessToken) }
            store.clear()
            _state.value = AuthUiState(loading = false, deviceName = store.deviceName())
        }
    }

    fun clearFeedback() {
        _state.update { it.copy(error = null, message = null) }
    }

    override fun onCleared() {
        api.close()
        super.onCleared()
    }
}
