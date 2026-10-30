package com.openfy.android.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import coil3.compose.AsyncImage
import com.openfy.android.R
import com.openfy.android.data.DownloadEntry
import com.openfy.android.data.PlaylistDto
import com.openfy.android.playback.EqualizerController
import com.openfy.core.Artist
import com.openfy.core.ArtistPage
import com.openfy.core.ColorPalette
import com.openfy.core.MediaCard
import com.openfy.core.MediaKind
import com.openfy.core.PlayerLayout
import com.openfy.core.ProfileExtras
import com.openfy.core.ProfilePin
import com.openfy.core.ProfileStatsCards
import com.openfy.core.ProfileStatsData
import com.openfy.core.ProfileStatsVisibility
import com.openfy.core.RemoteCollection
import com.openfy.core.RepeatMode
import com.openfy.core.SearchMode
import com.openfy.core.Track
import com.openfy.core.formatTime
import com.openfy.android.ui.theme.OpenfyAccent
import com.openfy.android.ui.theme.OpenfyAccent2
import com.openfy.android.ui.theme.OpenfyInk
import com.openfy.android.ui.theme.OpenfyHeroGradient
import com.openfy.android.ui.theme.OpenfyLine
import com.openfy.android.ui.theme.OpenfyPanel
import com.openfy.android.ui.theme.OpenfyPanelHigh
import com.openfy.android.ui.theme.OpenfyTextHigh
import com.openfy.android.ui.theme.OpenfyTextLow
import com.openfy.android.ui.theme.OpenfyTheme
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun OpenfyApp(viewModel: AuthViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) viewModel.refreshIfStale()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    OpenfyTheme(palette = state.palette) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when {
                state.loading -> LoadingScreen()
                state.session == null -> LoginScreen(state, viewModel)
                else -> MainShell(state, viewModel)
            }
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("openfy", color = OpenfyAccent, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(18.dp))
            CircularProgressIndicator(color = OpenfyAccent, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun MainShell(state: AuthUiState, viewModel: AuthViewModel) {
    var tab by remember { mutableStateOf(0) }
    var playerOpen by remember { mutableStateOf(false) }
    var profileOpen by remember { mutableStateOf(false) }
    var addToPlaylistTrack by remember { mutableStateOf<Track?>(null) }
    var createPlaylistFor by remember { mutableStateOf<Track?>(null) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.error, state.message) {
        val text = state.error ?: state.message
        if (text != null) {
            snackbar.showSnackbar(text)
            viewModel.clearFeedback()
        }
    }
    BackHandler(enabled = profileOpen) { profileOpen = false }
    BackHandler(enabled = state.libraryPage != null) { viewModel.closeLibraryPage() }
    BackHandler(enabled = state.openPlaylist != null) { viewModel.closePlaylist() }
    BackHandler(enabled = state.remoteCollectionId != null) { viewModel.closeCollection() }
    BackHandler(enabled = state.artistId != null) { viewModel.closeArtist() }
    val closeScreens = {
        profileOpen = false
        viewModel.closeLibraryPage()
        viewModel.closePlaylist()
        viewModel.closeCollection()
        viewModel.closeArtist()
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = OpenfyInk,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                Column {
                    if (state.track != null) {
                        MiniPlayer(state, viewModel) { playerOpen = true }
                    }
                    NavigationBar(containerColor = OpenfyPanel) {
                        NavigationBarItem(
                            selected = tab == 0 && state.openPlaylist == null,
                            onClick = {
                                closeScreens()
                                tab = 0
                            },
                            icon = { Icon(Icons.Filled.Home, null, modifier = Modifier.size(22.dp)) },
                            label = { Text("Início") },
                        )
                        NavigationBarItem(
                            selected = tab == 1 && state.openPlaylist == null,
                            onClick = {
                                closeScreens()
                                tab = 1
                            },
                            icon = { Icon(Icons.Filled.Search, null, modifier = Modifier.size(22.dp)) },
                            label = { Text("Buscar") },
                        )
                        NavigationBarItem(
                            selected = tab == 2 && state.openPlaylist == null,
                            onClick = {
                                closeScreens()
                                tab = 2
                            },
                            icon = { Icon(Icons.Filled.Favorite, null, modifier = Modifier.size(22.dp)) },
                            label = { Text("Curtidas") },
                        )
                        NavigationBarItem(
                            selected = tab == 3 && state.openPlaylist == null,
                            onClick = {
                                closeScreens()
                                tab = 3
                            },
                            icon = { Icon(Icons.Filled.Insights, null, modifier = Modifier.size(22.dp)) },
                            label = { Text("Recap") },
                        )
                        NavigationBarItem(
                            selected = tab == 4 && state.openPlaylist == null,
                            onClick = {
                                closeScreens()
                                tab = 4
                            },
                            icon = { Icon(Icons.Filled.Settings, null, modifier = Modifier.size(22.dp)) },
                            label = { Text("Ajustes") },
                        )
                    }
                }
            },
        ) { padding ->
            when {
                profileOpen -> ProfileScreen(
                    state,
                    viewModel,
                    Modifier.padding(padding),
                ) { profileOpen = false }
                state.libraryPage != null -> LibraryPageScreen(state, viewModel, Modifier.padding(padding)) {
                    addToPlaylistTrack = it
                }
                state.openPlaylist != null -> PlaylistDetailScreen(state, viewModel, Modifier.padding(padding)) {
                    addToPlaylistTrack = it
                }
                state.remoteCollectionId != null -> RemoteCollectionScreen(
                    state,
                    viewModel,
                    Modifier.padding(padding),
                ) { addToPlaylistTrack = it }
                state.artistId != null -> ArtistScreen(
                    state,
                    viewModel,
                    Modifier.padding(padding),
                ) { addToPlaylistTrack = it }
                tab == 0 -> HomeScreen(
                    state,
                    viewModel,
                    Modifier.padding(padding),
                    onAddToPlaylist = { addToPlaylistTrack = it },
                ) { profileOpen = true }
                tab == 1 -> SearchScreen(state, viewModel, Modifier.padding(padding)) {
                    addToPlaylistTrack = it
                }
                tab == 2 -> LikedScreen(state, viewModel, Modifier.padding(padding)) {
                    addToPlaylistTrack = it
                }
                tab == 3 -> RecapScreen(state, viewModel, Modifier.padding(padding)) {
                    addToPlaylistTrack = it
                }
                else -> SettingsScreen(
                    state,
                    viewModel,
                    Modifier.padding(padding),
                ) { profileOpen = true }
            }
        }
        if (playerOpen) {
            PlayerOverlay(
                state = state,
                viewModel = viewModel,
                onClose = { playerOpen = false },
                onAddToPlaylist = { addToPlaylistTrack = it },
                onArtist = { name ->
                    playerOpen = false
                    viewModel.openArtistFromName(name)
                },
            )
        }
        addToPlaylistTrack?.let { track ->
            AddToPlaylistDialog(
                track = track,
                playlists = state.playlists,
                onDismiss = { addToPlaylistTrack = null },
                onSelect = { playlist ->
                    viewModel.addToPlaylist(playlist, track)
                    addToPlaylistTrack = null
                },
                onCreateNew = {
                    addToPlaylistTrack = null
                    createPlaylistFor = track
                },
            )
        }
        createPlaylistFor?.let { pending ->
            NewPlaylistDialog(
                onDismiss = { createPlaylistFor = null },
                onCreate = { name ->
                    createPlaylistFor = null
                    viewModel.createPlaylist(name, pending)
                },
            )
        }
    }
}

@Composable
private fun HomeScreen(
    state: AuthUiState,
    viewModel: AuthViewModel,
    modifier: Modifier = Modifier,
    onAddToPlaylist: (Track) -> Unit = {},
    onOpenProfile: () -> Unit = {},
) {
    var newPlaylistOpen by remember { mutableStateOf(false) }
    var joinOpen by remember { mutableStateOf(false) }
    var importOpen by remember { mutableStateOf(false) }
    var playlistsMenuOpen by remember { mutableStateOf(false) }
    val session = state.session ?: return
    val title = state.profile?.displayName?.takeIf { it.isNotBlank() }
        ?: session.email.substringBefore("@").ifBlank { "Openfy" }

    LazyColumn(
        modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("openfy", color = OpenfyAccent, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Olá, $title",
                        color = OpenfyTextHigh,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    state.profile?.handle?.takeIf { it.isNotBlank() }?.let {
                        Text("@$it", color = OpenfyTextLow, fontSize = 11.sp)
                    }
                }
                TextButton(onClick = onOpenProfile) {
                    Text("Perfil", color = OpenfyAccent)
                }
                TextButton(onClick = { viewModel.signOut() }) {
                    Text("Sair", color = OpenfyTextLow)
                }
            }
        }

        if (state.offline) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = OpenfyPanel),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.CloudOff,
                            null,
                            tint = OpenfyTextLow,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Modo offline" +
                                if (state.offlineSavedAtMs > 0) {
                                    " · dados salvos em ${formatSavedAt(state.offlineSavedAtMs)}"
                                } else {
                                    ""
                                },
                            color = OpenfyTextLow,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }

        state.playbackRemote?.let { remote ->
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = OpenfyPanelHigh),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, OpenfyAccent.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val index = remote.queueIndex.coerceIn(0, remote.queue.lastIndex.coerceAtLeast(0))
                    val track = remote.queue.getOrNull(index)
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            "CONTINUAR NO OUTRO APARELHO",
                            color = OpenfyAccent,
                            fontSize = 10.sp,
                            letterSpacing = 1.2.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            track?.title ?: "Fila do outro aparelho",
                            color = OpenfyTextHigh,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        Text(
                            buildString {
                                track?.artist?.takeIf { it.isNotBlank() }?.let { append(it).append(" · ") }
                                append("${index + 1}/${remote.queue.size} faixas")
                            },
                            color = OpenfyTextLow,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(
                            Modifier.padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Button(onClick = { viewModel.continueOnOtherDevice() }) {
                                Icon(
                                    Icons.Filled.PlayArrow,
                                    null,
                                    tint = OpenfyInk,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("Continuar")
                            }
                            TextButton(onClick = { viewModel.dismissRemotePlayback() }) {
                                Text("Agora não", color = OpenfyTextLow)
                            }
                        }
                    }
                }
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.offline) {
                    Icon(
                        Icons.Filled.CloudOff,
                        null,
                        tint = OpenfyAccent2,
                        modifier = Modifier.size(13.dp),
                    )
                } else {
                    Box(Modifier.size(7.dp).background(OpenfyAccent, CircleShape))
                }
                Spacer(Modifier.width(7.dp))
                Text(
                    when {
                        state.busy -> "Sincronizando…"
                        state.pendingSyncCount > 0 -> "${state.pendingSyncCount} alterações pendentes"
                        else -> "Tudo sincronizado · ${state.deviceName}"
                    },
                    color = OpenfyTextLow,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = { viewModel.refreshLibrary() },
                    enabled = !state.busy,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                ) {
                    if (state.busy) {
                        CircularProgressIndicator(
                            Modifier.size(12.dp),
                            strokeWidth = 2.dp,
                            color = OpenfyTextLow,
                        )
                    } else {
                        Text("Sincronizar", color = OpenfyTextLow, fontSize = 11.sp)
                    }
                }
            }
        }

        item {
            Text(
                "Sua biblioteca",
                color = OpenfyTextHigh,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(LibraryPage.entries) { page ->
                    LibraryShortcut(page) { viewModel.openLibraryPage(page) }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = OpenfyPanelHigh),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().clickable { viewModel.startPersonalMix() },
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(44.dp).background(OpenfyAccent, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.PlaylistPlay,
                            null,
                            tint = OpenfyInk,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Seu mix",
                            color = OpenfyTextHigh,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Mais ouvidas + curtidas + recentes, sem repetir artistas seguidos.",
                            color = OpenfyTextLow,
                            fontSize = 11.sp,
                        )
                    }
                    Icon(
                        Icons.Filled.PlayArrow,
                        "Tocar seu mix",
                        tint = OpenfyAccent,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }

        if (state.recentTracks.isNotEmpty()) {
            item {
                Text(
                    "Ouvir de novo",
                    color = OpenfyTextHigh,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.recentTracks.take(12), key = { "recent-${it.id}" }) { track ->
                        TrackTile(
                            track = track,
                            onArtist = viewModel::openArtistFromName,
                            liked = track.id in state.likedIds,
                            onLike = { viewModel.toggleLike(track) },
                            onPlayNext = { viewModel.playNext(track) },
                            onAddQueue = { viewModel.addToQueue(track) },
                            onAddPlaylist = { onAddToPlaylist(track) },
                        ) { viewModel.playSearchResult(track) }
                    }
                }
            }
        }
        if (state.topTracks.isNotEmpty()) {
            item {
                Text(
                    "Mais ouvidas no mês",
                    color = OpenfyTextHigh,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.topTracks.take(12), key = { "top-${it.id}" }) { track ->
                        TrackTile(
                            track = track,
                            onArtist = viewModel::openArtistFromName,
                            liked = track.id in state.likedIds,
                            onLike = { viewModel.toggleLike(track) },
                            onPlayNext = { viewModel.playNext(track) },
                            onAddQueue = { viewModel.addToQueue(track) },
                            onAddPlaylist = { onAddToPlaylist(track) },
                        ) { viewModel.playTopTrack(track) }
                    }
                }
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Suas playlists",
                    color = OpenfyTextHigh,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { newPlaylistOpen = true },
                    enabled = !state.busy,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        Icons.Filled.Add,
                        "Nova playlist",
                        tint = OpenfyTextLow,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Box {
                    IconButton(
                        onClick = { playlistsMenuOpen = true },
                        enabled = !state.busy,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            Icons.Filled.MoreVert,
                            "Mais opções de playlists",
                            tint = OpenfyTextLow,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    DropdownMenu(
                        expanded = playlistsMenuOpen,
                        onDismissRequest = { playlistsMenuOpen = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Importar playlist") },
                            leadingIcon = { Icon(Icons.Filled.Download, null) },
                            onClick = {
                                playlistsMenuOpen = false
                                importOpen = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Entrar com código") },
                            leadingIcon = { Icon(Icons.Filled.Lock, null) },
                            onClick = {
                                playlistsMenuOpen = false
                                joinOpen = true
                            },
                        )
                    }
                }
            }
        }

        if (state.playlists.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = OpenfyPanel),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text(
                            "Nenhuma playlist sincronizada ainda.",
                            color = OpenfyTextHigh,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Crie uma aqui ou no PC; ela aparece nos dois aparelhos.",
                            color = OpenfyTextLow,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        } else {
            items(state.playlists, key = { it.id }) { playlist ->
                PlaylistCard(
                    playlist = playlist,
                    onOpen = { viewModel.openPlaylist(playlist) },
                    onPlay = { viewModel.playPlaylist(playlist.id) },
                )
            }
        }

        item {
            Text(
                "Toque para abrir a playlist; use ▶ para tocar direto.",
                color = OpenfyTextLow.copy(alpha = 0.7f),
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
            )
        }
    }

    if (newPlaylistOpen) {
        NewPlaylistDialog(
            onDismiss = { newPlaylistOpen = false },
            onCreate = {
                newPlaylistOpen = false
                viewModel.createPlaylist(it)
            },
        )
    }
    if (joinOpen) {
        JoinPlaylistDialog(
            onDismiss = { joinOpen = false },
            onJoin = {
                joinOpen = false
                viewModel.joinPlaylist(it)
            },
        )
    }
    if (importOpen) {
        ImportPlaylistDialog(
            onDismiss = { importOpen = false },
            onImport = {
                importOpen = false
                viewModel.importPlaylist(it)
            },
        )
    }
}

@Composable
private fun LibraryShortcut(page: LibraryPage, onClick: () -> Unit) {
    val icon = when (page) {
        LibraryPage.HISTORY -> Icons.Filled.History
        LibraryPage.TOP_PLAYED -> Icons.Filled.Insights
        LibraryPage.EXPLORE -> Icons.Filled.Explore
        LibraryPage.DOWNLOADS -> Icons.Filled.Download
        LibraryPage.MIX -> Icons.Filled.PlaylistPlay
    }
    Column(
        Modifier.width(92.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(OpenfyPanel)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = OpenfyAccent, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(6.dp))
        Text(
            page.title,
            color = OpenfyTextHigh,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LibraryPageScreen(
    state: AuthUiState,
    viewModel: AuthViewModel,
    modifier: Modifier = Modifier,
    onAddToPlaylist: (Track) -> Unit,
) {
    val page = state.libraryPage ?: return
    Column(modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { viewModel.closeLibraryPage() }) {
                Icon(Icons.Filled.ArrowBack, "Voltar", tint = OpenfyTextHigh)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    page.title,
                    color = OpenfyTextHigh,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    when (page) {
                        LibraryPage.HISTORY -> "${state.history.size} escutas neste aparelho"
                        LibraryPage.TOP_PLAYED -> "${state.topPlayed.size} faixas mais ouvidas"
                        LibraryPage.EXPLORE -> "Novidades, charts e artistas"
                        LibraryPage.DOWNLOADS -> "${state.downloads.size} faixas · ${formatBytes(state.downloadUsedBytes)}"
                        LibraryPage.MIX -> "Mistura automática do seu gosto"
                    },
                    color = OpenfyTextLow,
                    fontSize = 11.sp,
                )
            }
            when (page) {
                LibraryPage.EXPLORE -> IconButton(
                    onClick = { viewModel.loadExplore(force = true) },
                    enabled = !state.exploreLoading,
                ) {
                    Icon(
                        Icons.Filled.Explore,
                        "Atualizar",
                        tint = OpenfyTextLow,
                        modifier = Modifier.size(22.dp),
                    )
                }
                LibraryPage.DOWNLOADS -> TextButton(
                    onClick = { viewModel.clearDownloads() },
                    enabled = state.downloads.isNotEmpty(),
                ) {
                    Text("Limpar", color = OpenfyAccent, fontSize = 12.sp)
                }
                LibraryPage.MIX -> TextButton(onClick = { viewModel.startPersonalMix() }) {
                    Text("Tocar", color = OpenfyAccent, fontSize = 12.sp)
                }
                else -> Unit
            }
        }

        when (page) {
            LibraryPage.HISTORY -> {
                if (state.history.isEmpty()) {
                    EmptyLibraryHint(
                        "Nada no histórico ainda.",
                        "As faixas que você ouve ficam aqui, mesmo sem internet.",
                    )
                } else {
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.history, key = { "history-${it.id}" }) { track ->
                            TrackRow(
                                track = track,
                                isCurrent = state.track?.id == track.id,
                                liked = track.id in state.likedIds,
                                onPlay = { viewModel.playHistory(track) },
                                onLike = { viewModel.toggleLike(track) },
                                onPlayNext = { viewModel.playNext(track) },
                                onAddQueue = { viewModel.addToQueue(track) },
                                onAddPlaylist = { onAddToPlaylist(track) },
                                onArtist = viewModel::openArtistFromName,
                                downloaded = track.id in state.downloadedIds,
                                downloadProgress = state.downloadProgress[track.id],
                                onDownload = { viewModel.toggleDownload(track) },
                                onCancelDownload = { viewModel.cancelDownload(track.id) },
                            )
                        }
                        item { Spacer(Modifier.height(20.dp)) }
                    }
                }
            }

            LibraryPage.TOP_PLAYED -> {
                val tracks = state.topPlayed.ifEmpty { state.topTracks }
                if (tracks.isEmpty()) {
                    EmptyLibraryHint(
                        "Ainda não há mais ouvidas.",
                        "Ouça algumas faixas para o ranking aparecer.",
                    )
                } else {
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(tracks, key = { index, track -> "top-$index-${track.id}" }) { index, track ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${index + 1}",
                                    color = OpenfyAccent,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(22.dp),
                                )
                                Box(Modifier.weight(1f)) {
                                    TrackRow(
                                        track = track,
                                        isCurrent = state.track?.id == track.id,
                                        liked = track.id in state.likedIds,
                                        onPlay = { viewModel.playTopPlayed(track) },
                                        onLike = { viewModel.toggleLike(track) },
                                        onPlayNext = { viewModel.playNext(track) },
                                        onAddQueue = { viewModel.addToQueue(track) },
                                        onAddPlaylist = { onAddToPlaylist(track) },
                                        onArtist = viewModel::openArtistFromName,
                                        downloaded = track.id in state.downloadedIds,
                                        downloadProgress = state.downloadProgress[track.id],
                                        onDownload = { viewModel.toggleDownload(track) },
                                        onCancelDownload = { viewModel.cancelDownload(track.id) },
                                    )
                                }
                            }
                        }
                        item { Spacer(Modifier.height(20.dp)) }
                    }
                }
            }

            LibraryPage.EXPLORE -> {
                if (state.exploreLoading && state.exploreFeed == null) {
                    Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = OpenfyAccent, modifier = Modifier.size(26.dp))
                    }
                } else {
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        state.exploreError?.let { error ->
                            item {
                                Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                            }
                        }
                        if (state.charts.isNotEmpty()) {
                            item { SectionTitle("Em alta") }
                            itemsIndexed(state.charts, key = { i, t -> "chart-$i-${t.id}" }) { index, track ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "${index + 1}",
                                        color = OpenfyAccent,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.width(22.dp),
                                    )
                                    Box(Modifier.weight(1f)) {
                                        TrackRow(
                                            track = track,
                                            isCurrent = state.track?.id == track.id,
                                            liked = track.id in state.likedIds,
                                            onPlay = { viewModel.playChart(track) },
                                            onLike = { viewModel.toggleLike(track) },
                                            onPlayNext = { viewModel.playNext(track) },
                                            onAddQueue = { viewModel.addToQueue(track) },
                                            onAddPlaylist = { onAddToPlaylist(track) },
                                            onArtist = viewModel::openArtistFromName,
                                            downloaded = track.id in state.downloadedIds,
                                            downloadProgress = state.downloadProgress[track.id],
                                            onDownload = { viewModel.toggleDownload(track) },
                                            onCancelDownload = { viewModel.cancelDownload(track.id) },
                                        )
                                    }
                                }
                            }
                        }
                        state.exploreFeed?.sections.orEmpty().forEach { section ->
                            if (section.cards.isNotEmpty()) {
                                item { SectionTitle(section.title) }
                                item {
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        items(section.cards, key = { "explore-${it.kind}-${it.id}" }) { card ->
                                            ArtistMediaCard(card) { viewModel.openMediaCard(card) }
                                        }
                                    }
                                }
                            }
                        }
                        val topArtists = state.exploreFeed?.topArtists.orEmpty()
                        if (topArtists.isNotEmpty()) {
                            item { SectionTitle("Artistas em destaque") }
                            item {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    items(topArtists, key = { "explore-artist-${it.id}" }) { artist ->
                                        ArtistTile(artist) { viewModel.openArtistById(artist.id) }
                                    }
                                }
                            }
                        }
                        if (state.exploreFeed == null && state.charts.isEmpty() && !state.exploreLoading) {
                            item {
                                EmptyLibraryHint(
                                    "Nada carregado.",
                                    "Verifique a conexão e toque no ícone de atualizar.",
                                )
                            }
                        }
                        item { Spacer(Modifier.height(20.dp)) }
                    }
                }
            }

            LibraryPage.DOWNLOADS -> {
                if (state.downloads.isEmpty()) {
                    EmptyLibraryHint(
                        "Nenhum download.",
                        "Use ⋮ › Baixar em qualquer faixa ou baixe uma playlist inteira.",
                    )
                } else {
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.downloads, key = { "download-${it.track.id}" }) { entry ->
                            DownloadRow(
                                entry = entry,
                                isCurrent = state.track?.id == entry.track.id,
                                liked = entry.track.id in state.likedIds,
                                onPlay = { viewModel.playDownloaded(entry) },
                                onLike = { viewModel.toggleLike(entry.track) },
                                onPlayNext = { viewModel.playNext(entry.track) },
                                onAddQueue = { viewModel.addToQueue(entry.track) },
                                onAddPlaylist = { onAddToPlaylist(entry.track) },
                                onRemove = { viewModel.removeDownload(entry.track.id) },
                                onArtist = { name -> viewModel.openArtistFromName(name) },
                            )
                        }
                        item {
                            Text(
                                "Download fica neste aparelho e continua após sair da conta.",
                                color = OpenfyTextLow,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }
                        item { Spacer(Modifier.height(20.dp)) }
                    }
                }
            }

            LibraryPage.MIX -> {
                val preview = (state.topPlayed + state.likedTracks + state.recentTracks)
                    .distinctBy { it.id }
                    .take(20)
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = OpenfyPanelHigh),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth().clickable { viewModel.startPersonalMix() },
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    "Seu mix",
                                    color = OpenfyTextHigh,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    "Junta ${state.topPlayed.size} mais ouvidas, ${state.likedTracks.size} curtidas e ${state.recentTracks.size} recentes. " +
                                        "O recomendador evita repetir o mesmo artista em sequência.",
                                    color = OpenfyTextLow,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                                Button(
                                    onClick = { viewModel.startPersonalMix() },
                                    enabled = !state.mixLoading,
                                    modifier = Modifier.padding(top = 10.dp),
                                ) {
                                    if (state.mixLoading) {
                                        CircularProgressIndicator(
                                            Modifier.size(14.dp),
                                            strokeWidth = 2.dp,
                                            color = OpenfyInk,
                                        )
                                        Spacer(Modifier.width(6.dp))
                                    }
                                    Text("Iniciar mix")
                                }
                            }
                        }
                    }
                    if (preview.isEmpty()) {
                        item {
                            EmptyLibraryHint(
                                "O mix ainda não tem sementes.",
                                "Ouça, curta e baixe faixas para ele aprender.",
                            )
                        }
                    } else {
                        item { SectionTitle("Sementes do mix") }
                        items(preview, key = { "mix-${it.id}" }) { track ->
                            TrackRow(
                                track = track,
                                isCurrent = state.track?.id == track.id,
                                liked = track.id in state.likedIds,
                                onPlay = { viewModel.startPersonalMix() },
                                onLike = { viewModel.toggleLike(track) },
                                onPlayNext = { viewModel.playNext(track) },
                                onAddQueue = { viewModel.addToQueue(track) },
                                onAddPlaylist = { onAddToPlaylist(track) },
                                onArtist = viewModel::openArtistFromName,
                                downloaded = track.id in state.downloadedIds,
                                downloadProgress = state.downloadProgress[track.id],
                                onDownload = { viewModel.toggleDownload(track) },
                                onCancelDownload = { viewModel.cancelDownload(track.id) },
                            )
                        }
                    }
                    item { Spacer(Modifier.height(20.dp)) }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        color = OpenfyTextHigh,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun EmptyLibraryHint(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(top = 24.dp)) {
        Text(title, color = OpenfyTextHigh, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(
            subtitle,
            color = OpenfyTextLow,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun DownloadRow(
    entry: DownloadEntry,
    isCurrent: Boolean,
    liked: Boolean,
    onPlay: () -> Unit,
    onLike: () -> Unit,
    onPlayNext: () -> Unit,
    onAddQueue: () -> Unit,
    onAddPlaylist: () -> Unit,
    onRemove: () -> Unit,
    onArtist: (String) -> Unit,
) {
    val track = entry.track
    Card(
        colors = CardDefaults.cardColors(containerColor = OpenfyPanel),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onPlay),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 10.dp, top = 8.dp, bottom = 8.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoverImage(track.thumbnail, 42.dp, 8.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    track.title,
                    color = if (isCurrent) OpenfyAccent else OpenfyTextHigh,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                ArtistLinkText(
                    artist = track.artist,
                    color = OpenfyTextLow,
                    fontSize = 11.sp,
                    suffix = " · ${formatBytes(entry.sizeBytes)}",
                    onArtist = onArtist,
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    Icon(Icons.Filled.DownloadDone, null, tint = OpenfyAccent, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Baixada", color = OpenfyAccent, fontSize = 10.sp)
                }
            }
            IconButton(onClick = onLike) {
                Icon(
                    if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    if (liked) "Remover curtida" else "Curtir",
                    tint = if (liked) OpenfyAccent2 else OpenfyTextLow,
                    modifier = Modifier.size(21.dp),
                )
            }
            Box {
                var menuOpen by remember { mutableStateOf(false) }
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        "Mais ações",
                        tint = OpenfyTextLow,
                        modifier = Modifier.size(21.dp),
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Tocar em seguida") },
                        leadingIcon = { Icon(Icons.Filled.PlaylistPlay, null) },
                        onClick = {
                            menuOpen = false
                            onPlayNext()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Adicionar ao fim da fila") },
                        leadingIcon = { Icon(Icons.Filled.QueueMusic, null) },
                        onClick = {
                            menuOpen = false
                            onAddQueue()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Adicionar à playlist") },
                        leadingIcon = { Icon(Icons.Filled.PlaylistAdd, null) },
                        onClick = {
                            menuOpen = false
                            onAddPlaylist()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Remover download") },
                        leadingIcon = { Icon(Icons.Filled.Delete, null) },
                        onClick = {
                            menuOpen = false
                            onRemove()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistCard(playlist: PlaylistDto, onOpen: () -> Unit, onPlay: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = OpenfyPanel),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoverImage(playlist.coverUrl, 48.dp, 10.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    playlist.name,
                    color = OpenfyTextHigh,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text("Toque para abrir", color = OpenfyTextLow, fontSize = 11.sp)
            }
            IconButton(onClick = onPlay) {
                Icon(
                    Icons.Filled.PlayArrow,
                    "Tocar playlist",
                    tint = OpenfyAccent,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun ArtistLinkText(
    artist: String,
    color: Color,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    suffix: String = "",
    onArtist: (String) -> Unit,
) {
    val names = remember(artist) { splitArtistNames(artist) }
    val annotated = remember(artist, suffix) {
        buildAnnotatedString {
            if (names.isEmpty()) {
                append(artist)
            } else {
                names.forEachIndexed { index, name ->
                    if (index > 0) append(" · ")
                    withLink(
                        LinkAnnotation.Clickable(
                            tag = name,
                            linkInteractionListener = { onArtist(name) },
                        ),
                    ) {
                        append(name)
                    }
                }
            }
            if (suffix.isNotEmpty()) append(suffix)
        }
    }
    Text(
        annotated,
        color = color,
        fontSize = fontSize,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/**
 * "A & B", "A feat. B" e "A, B" viram nomes clicáveis separados. Vírgulas que
 * fazem parte do nome ("Tyler, The Creator") ficam inteiras.
 */
internal fun splitArtistNames(raw: String): List<String> {
    val text = raw.trim()
    if (text.isEmpty()) return emptyList()
    val commaParts = text.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    val keepTogether = commaParts.drop(1).any { part ->
        val lower = part.lowercase()
        lower.startsWith("the ") || lower.startsWith("and ") || lower.startsWith("of ")
    }
    val base = if (commaParts.size > 1 && !keepTogether) commaParts else listOf(text)
    val separator = Regex(
        "\\s*(?:&|\\bfeat\\.?\\b|\\bft\\.?\\b|\\bwith\\b|\\bx\\b)\\s*",
        RegexOption.IGNORE_CASE,
    )
    val names = base.flatMap { chunk ->
        chunk.split(separator).map { it.trim(' ', ',', '-', '–') }
    }.filter { it.isNotEmpty() }.distinct()
    return names.ifEmpty { listOf(text) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackTile(
    track: Track,
    onArtist: ((String) -> Unit)? = null,
    liked: Boolean = false,
    onLike: (() -> Unit)? = null,
    onPlayNext: (() -> Unit)? = null,
    onAddQueue: (() -> Unit)? = null,
    onAddPlaylist: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Column(
            Modifier.width(116.dp).combinedClickable(
                onClick = onClick,
                onLongClick = {
                    if (onLike != null || onPlayNext != null || onAddQueue != null || onAddPlaylist != null) {
                        menuOpen = true
                    }
                },
            ),
        ) {
            CoverImage(track.thumbnail, 116.dp, 12.dp)
            Text(
                track.title,
                color = OpenfyTextHigh,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
            if (onArtist != null) {
                ArtistLinkText(
                    artist = track.artist,
                    color = OpenfyTextLow,
                    fontSize = 10.sp,
                    onArtist = onArtist,
                )
            } else {
                Text(
                    track.artist,
                    color = OpenfyTextLow,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            onLike?.let { like ->
                DropdownMenuItem(
                    text = { Text(if (liked) "Remover curtida" else "Curtir") },
                    leadingIcon = {
                        Icon(
                            if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            null,
                        )
                    },
                    onClick = {
                        menuOpen = false
                        like()
                    },
                )
            }
            onPlayNext?.let { action ->
                DropdownMenuItem(
                    text = { Text("Tocar em seguida") },
                    leadingIcon = { Icon(Icons.Filled.PlaylistPlay, null) },
                    onClick = {
                        menuOpen = false
                        action()
                    },
                )
            }
            onAddQueue?.let { action ->
                DropdownMenuItem(
                    text = { Text("Adicionar ao fim da fila") },
                    leadingIcon = { Icon(Icons.Filled.QueueMusic, null) },
                    onClick = {
                        menuOpen = false
                        action()
                    },
                )
            }
            onAddPlaylist?.let { action ->
                DropdownMenuItem(
                    text = { Text("Adicionar à playlist") },
                    leadingIcon = { Icon(Icons.Filled.PlaylistAdd, null) },
                    onClick = {
                        menuOpen = false
                        action()
                    },
                )
            }
        }
    }
}

@Composable
private fun ArtistTile(artist: Artist, onClick: () -> Unit) {
    Column(
        Modifier.width(96.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CoverImage(artist.thumbnail, 84.dp, 42.dp)
        Text(
            artist.name,
            color = OpenfyTextHigh,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (artist.subtitle.isNotBlank()) {
            Text(
                artist.subtitle,
                color = OpenfyTextLow,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SearchScreen(
    state: AuthUiState,
    viewModel: AuthViewModel,
    modifier: Modifier = Modifier,
    onAddToPlaylist: (Track) -> Unit,
) {
    var query by remember { mutableStateOf(state.searchQuery) }
    Column(modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Text(
            "Buscar",
            color = OpenfyTextHigh,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 20.dp, bottom = 12.dp),
        )
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                if (it.length >= 2) viewModel.search(it)
            },
            label = { Text("Música, artista…") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, null, tint = OpenfyTextLow, modifier = Modifier.size(18.dp)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { viewModel.search(query) }),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { viewModel.search(query) }, enabled = query.isNotBlank() && !state.searching) {
                Text("Buscar")
            }
            Spacer(Modifier.width(10.dp))
            if (state.searching) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = OpenfyAccent)
            }
        }
        state.searchError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(SearchMode.entries, key = { it.name }) { mode ->
                AppearanceChip(mode.label, state.searchMode == mode) {
                    viewModel.setSearchMode(mode)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (state.searchResults.isEmpty() && state.searchArtists.isEmpty() &&
            state.searchCards.isEmpty() && !state.searching
        ) {
            Text(
                "Busque uma música ou artista e toque para ouvir. O catálogo é o YouTube Music, sem anúncios.",
                color = OpenfyTextLow,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.searchArtists.isNotEmpty()) {
                item {
                    Text(
                        "Artistas",
                        color = OpenfyTextHigh,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(state.searchArtists, key = { it.id }) { artist ->
                            ArtistTile(artist) { viewModel.openArtistById(artist.id) }
                        }
                    }
                }
                item {
                    Text(
                        "Músicas",
                        color = OpenfyTextHigh,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
            if (state.searchCards.isNotEmpty()) {
                item {
                    Text(
                        state.searchMode.label,
                        color = OpenfyTextHigh,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(state.searchCards, key = { "card-${it.kind}-${it.id}" }) { card ->
                            ArtistMediaCard(card) { viewModel.openMediaCard(card) }
                        }
                    }
                }
            }
            items(state.searchResults, key = { it.id }) { track ->
                TrackRow(
                    track = track,
                    isCurrent = state.track?.id == track.id,
                    liked = track.id in state.likedIds,
                    onPlay = { viewModel.playSearchResult(track) },
                    onLike = { viewModel.toggleLike(track) },
                    onPlayNext = { viewModel.playNext(track) },
                    onAddQueue = { viewModel.addToQueue(track) },
                    onAddPlaylist = { onAddToPlaylist(track) },
                    onArtist = viewModel::openArtistFromName,
                    downloaded = track.id in state.downloadedIds,
                    downloadProgress = state.downloadProgress[track.id],
                    onDownload = { viewModel.toggleDownload(track) },
                    onCancelDownload = { viewModel.cancelDownload(track.id) },
                )
            }
            if (state.searchHasMore) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        TextButton(
                            onClick = { viewModel.loadMoreSearch() },
                            enabled = !state.searchLoadingMore,
                        ) {
                            if (state.searchLoadingMore) {
                                CircularProgressIndicator(
                                    Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = OpenfyAccent,
                                )
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(
                                if (state.searchLoadingMore) "Carregando…" else "Carregar mais",
                                color = OpenfyAccent,
                            )
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

@Composable
private fun LikedScreen(
    state: AuthUiState,
    viewModel: AuthViewModel,
    modifier: Modifier = Modifier,
    onAddToPlaylist: (Track) -> Unit,
) {
    Column(modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Favorite, null, tint = OpenfyAccent2, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Curtidas", color = OpenfyTextHigh, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text(
                    "${state.likedTracks.size} faixas sincronizadas",
                    color = OpenfyTextLow,
                    fontSize = 12.sp,
                )
            }
            IconButton(
                onClick = {
                    state.likedTracks.firstOrNull()?.let { viewModel.playLiked(it) }
                },
                enabled = state.likedTracks.isNotEmpty(),
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    "Tocar curtidas",
                    tint = OpenfyAccent,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
        if (state.likedTracks.isEmpty()) {
            Text(
                "Toque no coração de uma faixa para guardar aqui. As curtidas aparecem no PC também.",
                color = OpenfyTextLow,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.likedTracks, key = { it.id }) { track ->
                TrackRow(
                    track = track,
                    isCurrent = state.track?.id == track.id,
                    liked = true,
                    onPlay = { viewModel.playLiked(track) },
                    onLike = { viewModel.toggleLike(track) },
                    onPlayNext = { viewModel.playNext(track) },
                    onAddQueue = { viewModel.addToQueue(track) },
                    onAddPlaylist = { onAddToPlaylist(track) },
                    onArtist = viewModel::openArtistFromName,
                    downloaded = track.id in state.downloadedIds,
                    downloadProgress = state.downloadProgress[track.id],
                    onDownload = { viewModel.toggleDownload(track) },
                    onCancelDownload = { viewModel.cancelDownload(track.id) },
                )
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

@Composable
private fun TrackRow(
    track: Track,
    isCurrent: Boolean,
    liked: Boolean,
    onPlay: () -> Unit,
    onLike: () -> Unit,
    onPlayNext: () -> Unit,
    onAddQueue: () -> Unit,
    onAddPlaylist: () -> Unit,
    onRemove: (() -> Unit)? = null,
    onArtist: ((String) -> Unit)? = null,
    downloaded: Boolean = false,
    downloadProgress: Float? = null,
    onDownload: (() -> Unit)? = null,
    onCancelDownload: (() -> Unit)? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(containerColor = OpenfyPanel),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onPlay),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 10.dp, top = 8.dp, bottom = 8.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoverImage(track.thumbnail, 42.dp, 8.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    track.title,
                    color = if (isCurrent) OpenfyAccent else OpenfyTextHigh,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (onArtist != null) {
                    ArtistLinkText(
                        artist = track.artist,
                        color = OpenfyTextLow,
                        fontSize = 11.sp,
                        suffix = " · ${formatTime(track.durationSec.toDouble())}",
                        onArtist = onArtist,
                    )
                } else {
                    Text(
                        "${track.artist} · ${formatTime(track.durationSec.toDouble())}",
                        color = OpenfyTextLow,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (downloaded) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp),
                    ) {
                        Icon(
                            Icons.Filled.DownloadDone,
                            null,
                            tint = OpenfyAccent,
                            modifier = Modifier.size(12.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Baixada", color = OpenfyAccent, fontSize = 10.sp)
                    }
                } else if (downloadProgress != null) {
                    if (downloadProgress < 0f) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(3.dp),
                            color = OpenfyAccent,
                            trackColor = OpenfyLine,
                        )
                    } else {
                        LinearProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(3.dp),
                            color = OpenfyAccent,
                            trackColor = OpenfyLine,
                        )
                    }
                }
            }
            IconButton(onClick = onLike) {
                Icon(
                    if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    if (liked) "Remover curtida" else "Curtir",
                    tint = if (liked) OpenfyAccent2 else OpenfyTextLow,
                    modifier = Modifier.size(21.dp),
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        "Mais ações",
                        tint = OpenfyTextLow,
                        modifier = Modifier.size(21.dp),
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Tocar em seguida") },
                        leadingIcon = { Icon(Icons.Filled.PlaylistPlay, null) },
                        onClick = {
                            menuOpen = false
                            onPlayNext()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Adicionar ao fim da fila") },
                        leadingIcon = { Icon(Icons.Filled.QueueMusic, null) },
                        onClick = {
                            menuOpen = false
                            onAddQueue()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Adicionar à playlist") },
                        leadingIcon = { Icon(Icons.Filled.PlaylistAdd, null) },
                        onClick = {
                            menuOpen = false
                            onAddPlaylist()
                        },
                    )
                    if (downloaded) {
                        DropdownMenuItem(
                            text = { Text("Remover download") },
                            leadingIcon = { Icon(Icons.Filled.DownloadDone, null) },
                            onClick = {
                                menuOpen = false
                                onDownload?.invoke()
                            },
                        )
                    } else if (downloadProgress != null) {
                        DropdownMenuItem(
                            text = { Text("Cancelar download") },
                            leadingIcon = { Icon(Icons.Filled.Close, null) },
                            onClick = {
                                menuOpen = false
                                onCancelDownload?.invoke()
                            },
                        )
                    } else if (onDownload != null) {
                        DropdownMenuItem(
                            text = { Text("Baixar") },
                            leadingIcon = { Icon(Icons.Filled.Download, null) },
                            onClick = {
                                menuOpen = false
                                onDownload()
                            },
                        )
                    }
                    if (onRemove != null) {
                        DropdownMenuItem(
                            text = { Text("Remover da playlist") },
                            leadingIcon = { Icon(Icons.Filled.Delete, null) },
                            onClick = {
                                menuOpen = false
                                onRemove()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistDetailScreen(
    state: AuthUiState,
    viewModel: AuthViewModel,
    modifier: Modifier = Modifier,
    onAddToPlaylist: (Track) -> Unit,
) {
    val playlist = state.openPlaylist ?: return
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.setPlaylistCover(playlist.id, it) }
    }
    var shareOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var deleteOpen by remember { mutableStateOf(false) }
    var orderMode by remember { mutableStateOf(false) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    val rowHeightPx = with(LocalDensity.current) { 63.dp.toPx() }
    val orderedItems = remember(state.playlistItems) { state.playlistItems.sortedBy { it.rank } }
    val tracksById = remember(state.playlistTracks) { state.playlistTracks.associateBy { it.id } }
    val liveItems by rememberUpdatedState(orderedItems)
    Column(modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { viewModel.closePlaylist() }) {
                Icon(Icons.Filled.ArrowBack, "Voltar", tint = OpenfyTextHigh)
            }
            CoverImage(
                playlist.coverUrl.ifBlank { state.playlistTracks.firstOrNull()?.thumbnail.orEmpty() },
                52.dp,
                10.dp,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    playlist.name,
                    color = OpenfyTextHigh,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (state.playlistLoading) "Carregando…" else "${state.playlistTracks.size} faixas",
                    color = OpenfyTextLow,
                    fontSize = 12.sp,
                )
            }
            IconButton(
                onClick = {
                    viewModel.requestShareCode(playlist.id)
                    shareOpen = true
                },
                enabled = !state.busy,
            ) {
                Icon(
                    Icons.Filled.Share,
                    "Compartilhar playlist",
                    tint = OpenfyTextLow,
                    modifier = Modifier.size(22.dp),
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        "Mais opções da playlist",
                        tint = OpenfyTextLow,
                        modifier = Modifier.size(24.dp),
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Renomear") },
                        leadingIcon = { Icon(Icons.Filled.Edit, null) },
                        onClick = {
                            menuOpen = false
                            renameOpen = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Trocar capa") },
                        leadingIcon = { Icon(Icons.Filled.Image, null) },
                        onClick = {
                            menuOpen = false
                            coverPicker.launch("image/*")
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Baixar playlist") },
                        leadingIcon = { Icon(Icons.Filled.Download, null) },
                        enabled = state.playlistTracks.isNotEmpty(),
                        onClick = {
                            menuOpen = false
                            viewModel.downloadPlaylist(state.playlistTracks)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Excluir playlist", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Delete,
                                null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            menuOpen = false
                            deleteOpen = true
                        },
                    )
                }
            }
            TextButton(onClick = { orderMode = !orderMode }) {
                Text(
                    if (orderMode) "Pronto" else "Ordenar",
                    color = if (orderMode) OpenfyAccent else OpenfyTextLow,
                    fontSize = 11.sp,
                )
            }
            IconButton(
                onClick = { viewModel.playOpenedPlaylist() },
                enabled = state.playlistTracks.isNotEmpty(),
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    "Tocar playlist",
                    tint = OpenfyAccent,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        if (state.playlistLoading) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = OpenfyAccent, modifier = Modifier.size(24.dp))
            }
        } else if (state.playlistTracks.isEmpty()) {
            Text(
                "Playlist vazia. Adicione faixas pela busca ou pelo player.",
                color = OpenfyTextLow,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        if (orderMode && !state.playlistLoading) {
            Text(
                "Segure o ⋮⋮ e arraste para reordenar. Solte para salvar.",
                color = OpenfyTextLow,
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(orderedItems, key = { item -> item.id }) { item ->
                val track = tracksById[item.trackId] ?: return@items
                val dragging = draggingId == item.id
                Row(
                    Modifier.fillMaxWidth()
                        .zIndex(if (dragging) 1f else 0f)
                        .offset { IntOffset(0, if (dragging) dragOffsetY.roundToInt() else 0) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (orderMode) {
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .pointerInput(item.id) {
                                    var startIndex = -1
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            draggingId = item.id
                                            dragOffsetY = 0f
                                            startIndex = liveItems.indexOfFirst { it.id == item.id }
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragOffsetY += amount.y
                                        },
                                        onDragEnd = {
                                            if (startIndex >= 0) {
                                                val steps = (dragOffsetY / rowHeightPx).roundToInt()
                                                if (steps != 0) {
                                                    val target = (startIndex + steps)
                                                        .coerceIn(0, liveItems.lastIndex)
                                                    viewModel.movePlaylistTrackTo(item, target)
                                                }
                                            }
                                            startIndex = -1
                                            draggingId = null
                                            dragOffsetY = 0f
                                        },
                                        onDragCancel = {
                                            startIndex = -1
                                            draggingId = null
                                            dragOffsetY = 0f
                                        },
                                    )
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.DragHandle,
                                "Arrastar para reordenar",
                                tint = OpenfyTextLow,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    Box(Modifier.weight(1f)) {
                        TrackRow(
                            track = track,
                            isCurrent = state.track?.id == track.id,
                            liked = track.id in state.likedIds,
                            onPlay = { viewModel.playPlaylistTrack(track) },
                            onLike = { viewModel.toggleLike(track) },
                            onPlayNext = { viewModel.playNext(track) },
                            onAddQueue = { viewModel.addToQueue(track) },
                            onAddPlaylist = { onAddToPlaylist(track) },
                            onRemove = { viewModel.removeFromPlaylist(item) },
                            onArtist = viewModel::openArtistFromName,
                            downloaded = track.id in state.downloadedIds,
                            downloadProgress = state.downloadProgress[track.id],
                            onDownload = { viewModel.toggleDownload(track) },
                            onCancelDownload = { viewModel.cancelDownload(track.id) },
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
    if (renameOpen) {
        RenamePlaylistDialog(
            current = playlist.name,
            onDismiss = { renameOpen = false },
            onRename = { name ->
                renameOpen = false
                viewModel.renamePlaylist(playlist.id, name)
            },
        )
    }
    if (deleteOpen) {
        AlertDialog(
            onDismissRequest = { deleteOpen = false },
            containerColor = OpenfyPanelHigh,
            title = { Text("Excluir playlist?", color = OpenfyTextHigh) },
            text = {
                Text(
                    "\"${playlist.name}\" será removida da conta e dos outros aparelhos. " +
                        "As faixas curtidas e os downloads deste aparelho continuam.",
                    color = OpenfyTextLow,
                    fontSize = 13.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    deleteOpen = false
                    viewModel.deletePlaylist(playlist.id)
                }) {
                    Text("Excluir", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteOpen = false }) {
                    Text("Cancelar", color = OpenfyTextLow)
                }
            },
        )
    }
}

@Composable
private fun RenamePlaylistDialog(
    current: String,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
) {
    var name by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = OpenfyPanelHigh,
        title = { Text("Renomear playlist", color = OpenfyTextHigh) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nome") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onRename(name) },
                enabled = name.isNotBlank() && name.trim() != current,
            ) {
                Text("Salvar", color = OpenfyAccent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = OpenfyTextLow)
            }
        },
    )
}

@Composable
private fun ArtistScreen(
    state: AuthUiState,
    viewModel: AuthViewModel,
    modifier: Modifier = Modifier,
    onAddToPlaylist: (Track) -> Unit,
) {
    val page = state.artistPage
    if (state.artistLoading && page == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = OpenfyAccent, modifier = Modifier.size(26.dp))
        }
        return
    }
    if (page == null) {
        Column(
            modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                state.artistError ?: "Artista não encontrado.",
                color = OpenfyTextLow,
                fontSize = 13.sp,
            )
            state.artistId?.let { id ->
                TextButton(onClick = { viewModel.openArtistById(id) }) {
                    Text("Tentar de novo", color = OpenfyAccent)
                }
            }
        }
        return
    }
    LazyColumn(
        modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { viewModel.closeArtist() }) {
                    Icon(Icons.Filled.ArrowBack, "Voltar", tint = OpenfyTextHigh)
                }
                Spacer(Modifier.width(4.dp))
                Column(Modifier.weight(1f)) {
                    Text("ARTISTA", color = OpenfyTextLow, fontSize = 10.sp, letterSpacing = 1.4.sp)
                    Text(
                        page.name,
                        color = OpenfyTextHigh,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (page.listeners.isNotBlank()) {
                        Text(page.listeners, color = OpenfyTextLow, fontSize = 11.sp)
                    }
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CoverImage(
                    page.avatar.ifBlank { page.banner },
                    108.dp,
                    54.dp,
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Button(
                        onClick = {
                            page.topTracks.firstOrNull()?.let { viewModel.playArtistTop(it) }
                        },
                        enabled = page.topTracks.isNotEmpty(),
                    ) {
                        Text("Tocar populares")
                    }
                    TextButton(
                        onClick = { viewModel.playArtistRadio() },
                        enabled = page.radioVideoId != null || page.topTracks.isNotEmpty(),
                    ) {
                        Text("Rádio do artista", color = OpenfyAccent)
                    }
                }
            }
        }
        if (page.description.isNotBlank()) {
            item {
                Text(
                    page.description,
                    color = OpenfyTextLow,
                    fontSize = 12.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (page.topTracks.isNotEmpty()) {
            item {
                Text(
                    "Populares",
                    color = OpenfyTextHigh,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            items(page.topTracks, key = { "artist-${it.id}" }) { track ->
                TrackRow(
                    track = track,
                    isCurrent = state.track?.id == track.id,
                    liked = track.id in state.likedIds,
                    onPlay = { viewModel.playArtistTop(track) },
                    onLike = { viewModel.toggleLike(track) },
                    onPlayNext = { viewModel.playNext(track) },
                    onAddQueue = { viewModel.addToQueue(track) },
                    onAddPlaylist = { onAddToPlaylist(track) },
                    downloaded = track.id in state.downloadedIds,
                    downloadProgress = state.downloadProgress[track.id],
                    onDownload = { viewModel.toggleDownload(track) },
                    onCancelDownload = { viewModel.cancelDownload(track.id) },
                )
            }
        }
        page.sections.forEach { section ->
            if (section.cards.isNotEmpty()) {
                item(key = "artist-section-${section.title}") {
                    Text(
                        section.title,
                        color = OpenfyTextHigh,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
                item(key = "artist-cards-${section.title}") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(section.cards, key = { "${it.kind}:${it.id}" }) { card ->
                            ArtistMediaCard(card) {
                                when (card.kind) {
                                    MediaKind.ARTIST -> viewModel.openArtistById(card.id)
                                    MediaKind.ALBUM, MediaKind.PLAYLIST -> viewModel.openCollection(card.id)
                                    MediaKind.TRACK -> viewModel.playSearchResult(
                                        Track(
                                            id = card.id,
                                            title = card.title,
                                            artist = page.name,
                                            thumbnail = card.thumbnail,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun ArtistMediaCard(card: com.openfy.core.MediaCard, onClick: () -> Unit) {
    Column(Modifier.width(116.dp).clickable(onClick = onClick)) {
        CoverImage(
            card.thumbnail,
            116.dp,
            if (card.kind == MediaKind.ARTIST) 58.dp else 12.dp,
        )
        Text(
            card.title,
            color = OpenfyTextHigh,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (card.subtitle.isNotBlank()) {
            Text(
                card.subtitle,
                color = OpenfyTextLow,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RemoteCollectionScreen(
    state: AuthUiState,
    viewModel: AuthViewModel,
    modifier: Modifier = Modifier,
    onAddToPlaylist: (Track) -> Unit,
) {
    val collection = state.remoteCollection
    if (state.collectionLoading && collection == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = OpenfyAccent, modifier = Modifier.size(26.dp))
        }
        return
    }
    if (collection == null) {
        Column(
            modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                state.collectionError ?: "Coleção não encontrada.",
                color = OpenfyTextLow,
                fontSize = 13.sp,
            )
            state.remoteCollectionId?.let { id ->
                TextButton(onClick = { viewModel.openCollection(id) }) {
                    Text("Tentar de novo", color = OpenfyAccent)
                }
            }
        }
        return
    }
    Column(modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { viewModel.closeCollection() }) {
                Icon(Icons.Filled.ArrowBack, "Voltar", tint = OpenfyTextHigh)
            }
            CoverImage(collection.thumbnail, 56.dp, 10.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    collection.title,
                    color = OpenfyTextHigh,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${collection.tracks.size} faixas",
                    color = OpenfyTextLow,
                    fontSize = 11.sp,
                )
            }
            IconButton(
                onClick = { viewModel.playCollection() },
                enabled = collection.tracks.isNotEmpty(),
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    "Tocar",
                    tint = OpenfyAccent,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(collection.tracks, key = { index, track -> "${track.id}-$index" }) { _, track ->
                TrackRow(
                    track = track,
                    isCurrent = state.track?.id == track.id,
                    liked = track.id in state.likedIds,
                    onPlay = { viewModel.playCollectionTrack(track) },
                    onLike = { viewModel.toggleLike(track) },
                    onPlayNext = { viewModel.playNext(track) },
                    onAddQueue = { viewModel.addToQueue(track) },
                    onAddPlaylist = { onAddToPlaylist(track) },
                    onArtist = viewModel::openArtistFromName,
                    downloaded = track.id in state.downloadedIds,
                    downloadProgress = state.downloadProgress[track.id],
                    onDownload = { viewModel.toggleDownload(track) },
                    onCancelDownload = { viewModel.cancelDownload(track.id) },
                )
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

@Composable
private fun RecapScreen(
    state: AuthUiState,
    viewModel: AuthViewModel,
    modifier: Modifier = Modifier,
    onAddToPlaylist: (Track) -> Unit,
) {
    LaunchedEffect(Unit) { viewModel.loadRecap() }
    LazyColumn(
        modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Insights,
                    null,
                    tint = OpenfyAccent,
                    modifier = Modifier.size(26.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        "Seu recap",
                        color = OpenfyTextHigh,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "O que você ouviu nesta conta, no PC e no celular.",
                        color = OpenfyTextLow,
                        fontSize = 12.sp,
                    )
                }
            }
        }
        item {
            val months = state.recapMonths.ifEmpty { listOf(state.recapMonth) }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(months) { month ->
                    AppearanceChip(recapMonthLabel(month), state.recapMonth == month) {
                        viewModel.selectRecapMonth(month)
                    }
                }
            }
        }
        if (state.recapLoading) {
            item {
                Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        Modifier.size(28.dp),
                        strokeWidth = 2.dp,
                        color = OpenfyAccent,
                    )
                }
            }
        } else {
            state.recapError?.let { error ->
                item {
                    Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
            val recap = state.recap
            val listening = recap?.listening
            if (recap == null || (listening == null && recap.topTracks.isEmpty() && recap.artists.isEmpty())) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = OpenfyPanel),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                "Ainda não há escutas neste mês.",
                                color = OpenfyTextHigh,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "Ouça algumas faixas no PC ou no celular para montar o recap.",
                                color = OpenfyTextLow,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            } else {
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RecapMetric("Minutos", ((listening?.playedMs ?: 0) / 60_000).toString(), Modifier.weight(1f))
                        RecapMetric("Execuções", (listening?.plays ?: 0).toString(), Modifier.weight(1f))
                        RecapMetric("Faixas", (listening?.uniqueTracks ?: 0).toString(), Modifier.weight(1f))
                    }
                }
                if (recap.topTracks.isNotEmpty()) {
                    item {
                        Text(
                            "Mais ouvidas",
                            color = OpenfyTextHigh,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    items(recap.topTracks, key = { "recap-${it.id}" }) { track ->
                        TrackRow(
                            track = track,
                            isCurrent = state.track?.id == track.id,
                            liked = track.id in state.likedIds,
                            onPlay = { viewModel.playRecapTrack(track) },
                            onLike = { viewModel.toggleLike(track) },
                            onPlayNext = { viewModel.playNext(track) },
                            onAddQueue = { viewModel.addToQueue(track) },
                            onAddPlaylist = { onAddToPlaylist(track) },
                            onArtist = viewModel::openArtistFromName,
                            downloaded = track.id in state.downloadedIds,
                            downloadProgress = state.downloadProgress[track.id],
                            onDownload = { viewModel.toggleDownload(track) },
                            onCancelDownload = { viewModel.cancelDownload(track.id) },
                        )
                    }
                }
                if (recap.artists.isNotEmpty()) {
                    item {
                        Text(
                            "Top artistas",
                            color = OpenfyTextHigh,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    items(recap.artists, key = { "recap-artist-${it.artist}" }) { artist ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = OpenfyPanel),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    artist.artist,
                                    color = OpenfyTextHigh,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    "${artist.plays} execuções",
                                    color = OpenfyTextLow,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(10.dp)) }
    }
}

@Composable
private fun RecapMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = OpenfyPanel),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
            Text(
                value,
                color = OpenfyAccent,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(label, color = OpenfyTextLow, fontSize = 10.sp)
        }
    }
}

private fun formatSavedAt(ms: Long): String {
    val date = java.time.Instant.ofEpochMilli(ms)
        .atZone(java.time.ZoneId.systemDefault())
    return java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm").format(date)
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 ->
        String.format(java.util.Locale.ROOT, "%.1f GB", bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024L * 1024 ->
        String.format(java.util.Locale.ROOT, "%.0f MB", bytes / (1024.0 * 1024))
    else -> "${bytes / 1024} KB"
}

private fun formatLimitLabel(mb: Int): String =
    if (mb >= 1024) String.format(java.util.Locale.ROOT, "%.1f GB", mb / 1024.0) else "$mb MB"

private fun recapMonthLabel(value: String): String {
    val month = runCatching { YearMonth.parse(value.take(7)) }.getOrNull() ?: return value
    val names = listOf(
        "janeiro", "fevereiro", "março", "abril", "maio", "junho",
        "julho", "agosto", "setembro", "outubro", "novembro", "dezembro",
    )
    return "${names[month.monthValue - 1]} de ${month.year}"
}

// ------------------------------------------------------------------ profile
private fun profileAccent(id: String): Color = when (ColorPalette.fromId(id)) {
    ColorPalette.OPENFY -> Color(0xFF2FBFA0)
    ColorPalette.VIOLET -> Color(0xFFA78BFA)
    ColorPalette.ROSE -> Color(0xFFF472B6)
    ColorPalette.AMBER -> Color(0xFFF5A524)
    ColorPalette.ICE -> Color(0xFF7DD3FC)
}

private fun formatProfileMinutes(minutes: Long): String {
    if (minutes < 60) return "$minutes min"
    val hours = minutes / 60
    val rest = minutes % 60
    return if (rest == 0L) "${hours}h" else "${hours}h ${rest}m"
}

@Composable
private fun ProfileScreen(
    state: AuthUiState,
    viewModel: AuthViewModel,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
) {
    val session = state.session ?: return
    val profile = state.profile
    val extras = state.profileExtras
    val stats = state.profileStats
    val accent = profileAccent(extras.accent)
    val displayName = profile?.displayName?.takeIf { it.isNotBlank() }
        ?: session.email.substringBefore("@").ifBlank { "Conta Openfy" }
    val handle = profile?.handle?.takeIf { it.isNotBlank() }?.let { "@$it" }.orEmpty()

    var editing by remember { mutableStateOf(false) }
    var name by remember(editing, profile?.displayName) {
        mutableStateOf(profile?.displayName.orEmpty())
    }
    var bio by remember(editing, extras.bio) { mutableStateOf(extras.bio) }
    var avatarUri by remember { mutableStateOf<Uri?>(null) }
    var coverUri by remember { mutableStateOf<Uri?>(null) }
    var pinPickerOpen by remember { mutableStateOf(false) }

    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) avatarUri = uri
    }
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) coverUri = uri
    }

    LaunchedEffect(Unit) { viewModel.loadProfileStats() }

    val order = ProfileStatsCards.sanitizeOrder(extras.statsOrder)
    val hidden = extras.statsHidden.toSet()
    val visibleCards = order.filter { it !in hidden }
    val topArtists = stats?.topArtists.orEmpty()
    val topTracks = stats?.topTracks.orEmpty()
    val topTrackList = topTracks.map { it.track }

    Column(
        modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, "Voltar", tint = OpenfyTextHigh)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "Seu perfil",
                    color = OpenfyTextHigh,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Sincroniza com o desktop. Visível só para você até o social ser liberado.",
                    color = OpenfyTextLow,
                    fontSize = 10.sp,
                )
            }
            TextButton(onClick = {
                if (editing) {
                    avatarUri = null
                    coverUri = null
                }
                editing = !editing
            }) {
                Text(if (editing) "Concluir" else "Editar", color = OpenfyAccent)
            }
        }

        Spacer(Modifier.height(12.dp))
        Box(
            Modifier.fillMaxWidth().height(176.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            accent.copy(alpha = if (extras.style.glow) 0.55f else 0.32f),
                            OpenfyPanelHigh,
                            OpenfyInk,
                        ),
                    ),
                ),
        ) {
            val coverModel = coverUri ?: extras.coverUrl.takeIf { it.isNotBlank() }
            if (coverModel != null && (coverUri != null || !extras.style.coverGradient)) {
                AsyncImage(
                    model = coverModel,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, OpenfyInk.copy(alpha = 0.82f)),
                    ),
                ),
            )
            Row(
                Modifier.align(Alignment.BottomStart).padding(14.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Box(
                    Modifier.size(82.dp).clip(CircleShape)
                        .background(accent.copy(alpha = 0.3f))
                        .padding(3.dp),
                ) {
                    Box(
                        Modifier.fillMaxSize().clip(CircleShape).background(OpenfyPanelHigh),
                        contentAlignment = Alignment.Center,
                    ) {
                        val avatarModel = avatarUri ?: profile?.avatarUrl?.takeIf { it.isNotBlank() }
                        if (avatarModel != null) {
                            AsyncImage(
                                model = avatarModel,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Text(
                                displayName.take(1).uppercase(),
                                color = accent,
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.padding(bottom = 4.dp)) {
                    Text(
                        if (editing) name.ifBlank { displayName } else displayName,
                        color = OpenfyTextHigh,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (handle.isNotBlank()) {
                        Text(handle, color = OpenfyTextLow, fontSize = 11.sp)
                    }
                }
            }
        }

        val shownBio = (if (editing) bio else extras.bio).trim()
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                shownBio.ifBlank {
                    "Sem bio ainda. Conte em uma linha o que você anda ouvindo."
                },
                color = if (shownBio.isBlank()) {
                    OpenfyTextLow.copy(alpha = 0.7f)
                } else {
                    OpenfyTextHigh
                },
                fontSize = 12.sp,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Icon(
                if (extras.statsVisibility == ProfileStatsVisibility.PRIVATE) {
                    Icons.Filled.Lock
                } else {
                    Icons.Filled.Public
                },
                null,
                tint = accent,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                ProfileStatsVisibility.label(extras.statsVisibility),
                color = OpenfyTextLow,
                fontSize = 10.sp,
            )
        }

        state.profileStatus?.let { status ->
            Text(
                status,
                color = if (state.error != null) Color(0xFFFF8A80) else accent,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        if (visibleCards.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Estatísticas do mês",
                    color = OpenfyTextHigh,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (state.profileStatsLoading) {
                    CircularProgressIndicator(
                        Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = accent,
                    )
                } else {
                    TextButton(onClick = { viewModel.loadProfileStats(force = true) }) {
                        Text("Atualizar", color = accent, fontSize = 11.sp)
                    }
                }
            }
            visibleCards.chunked(2).forEach { row ->
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.forEach { id ->
                        ProfileStatCard(id, stats, accent, Modifier.weight(1f))
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        if (ProfileStatsCards.TOP_ARTISTS !in hidden && topArtists.isNotEmpty()) {
            Text(
                "Top artistas",
                color = OpenfyTextHigh,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
            )
            topArtists.forEachIndexed { index, artist ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${index + 1}",
                        color = accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.width(22.dp),
                    )
                    Text(
                        artist.name.ifBlank { "Artista" },
                        color = OpenfyTextHigh,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text("${artist.plays} plays", color = OpenfyTextLow, fontSize = 10.sp)
                }
            }
        }

        if (ProfileStatsCards.TOP_TRACKS !in hidden && topTracks.isNotEmpty()) {
            Text(
                "Top faixas",
                color = OpenfyTextHigh,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
            )
            topTracks.forEachIndexed { index, row ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                        .clickable { viewModel.playTracks(topTrackList, index, "profile") }
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${index + 1}",
                        color = accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.width(22.dp),
                    )
                    CoverImage(row.track.thumbnail, 36.dp, 7.dp)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            row.track.title,
                            color = OpenfyTextHigh,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            row.track.artist,
                            color = OpenfyTextLow,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text("${row.plays} plays", color = OpenfyTextLow, fontSize = 10.sp)
                }
            }
        }

        if (extras.pins.isNotEmpty()) {
            Text(
                "Faixas fixadas",
                color = OpenfyTextHigh,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
            )
            extras.pins.forEachIndexed { index, pin ->
                ProfilePinRow(
                    pin = pin,
                    editing = editing,
                    accent = accent,
                    onPlay = {
                        viewModel.playTracks(extras.pins.map { it.toTrack() }, index, "profile")
                    },
                    onUp = { viewModel.moveProfilePin(pin.id, -1) },
                    onDown = { viewModel.moveProfilePin(pin.id, 1) },
                    onRemove = { viewModel.removeProfilePin(pin.id) },
                )
            }
        }

        if (editing) {
            Spacer(Modifier.height(8.dp))
            ProfileCard("Identidade") {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(60) },
                    label = { Text("Nome") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it.take(ProfileExtras.BIO_LIMIT) },
                    label = { Text("Bio") },
                    minLines = 2,
                    maxLines = 4,
                    supportingText = {
                        Text(
                            "${bio.length}/${ProfileExtras.BIO_LIMIT}",
                            color = OpenfyTextLow,
                            fontSize = 10.sp,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { avatarPicker.launch("image/*") }) {
                        Icon(Icons.Filled.Image, null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (avatarUri == null) "Trocar foto" else "Foto escolhida",
                            color = accent,
                            fontSize = 11.sp,
                        )
                    }
                    TextButton(onClick = { coverPicker.launch("image/*") }) {
                        Icon(Icons.Filled.Image, null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (coverUri == null) "Trocar capa" else "Capa escolhida",
                            color = accent,
                            fontSize = 11.sp,
                        )
                    }
                }
                if (avatarUri != null || coverUri != null) {
                    Text(
                        "As imagens sobem quando você salvar.",
                        color = OpenfyTextLow,
                        fontSize = 10.sp,
                    )
                }
            }

            ProfileCard("Cor e efeitos") {
                Text("Cor do perfil", color = OpenfyTextHigh, fontSize = 12.sp)
                LazyRow(
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(ColorPalette.entries) { preset ->
                        AppearanceChip(preset.label, extras.accent == preset.id) {
                            viewModel.updateProfileExtras { it.copy(accent = preset.id) }
                        }
                    }
                }
                ProfileSwitchRow(
                    label = "Gradiente na capa",
                    checked = extras.style.coverGradient,
                ) { value ->
                    viewModel.updateProfileExtras {
                        it.copy(style = it.style.copy(coverGradient = value))
                    }
                }
                ProfileSwitchRow(
                    label = "Movimento",
                    checked = extras.style.motion,
                ) { value ->
                    viewModel.updateProfileExtras {
                        it.copy(style = it.style.copy(motion = value))
                    }
                }
                ProfileSwitchRow(
                    label = "Brilho",
                    checked = extras.style.glow,
                ) { value ->
                    viewModel.updateProfileExtras {
                        it.copy(style = it.style.copy(glow = value))
                    }
                }
            }

            ProfileCard("Quem vê suas estatísticas") {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ProfileStatsVisibility.all.forEach { value ->
                        AppearanceChip(
                            ProfileStatsVisibility.label(value),
                            extras.statsVisibility == value,
                        ) {
                            viewModel.updateProfileExtras { it.copy(statsVisibility = value) }
                        }
                    }
                }
                Text(
                    "Privado mantém os números só neste perfil. Seguidores/público ficam " +
                        "prontos para quando o perfil social for liberado.",
                    color = OpenfyTextLow,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            ProfileCard("Cartões de estatística") {
                order.forEachIndexed { index, id ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            ProfileStatsCards.label(id),
                            color = OpenfyTextHigh,
                            fontSize = 11.sp,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { viewModel.moveProfileCard(id, -1) },
                            enabled = index > 0,
                            modifier = Modifier.size(26.dp),
                        ) {
                            Icon(
                                Icons.Filled.KeyboardArrowUp,
                                "Mover para cima",
                                tint = if (index > 0) OpenfyTextLow else OpenfyLine,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        IconButton(
                            onClick = { viewModel.moveProfileCard(id, 1) },
                            enabled = index < order.lastIndex,
                            modifier = Modifier.size(26.dp),
                        ) {
                            Icon(
                                Icons.Filled.KeyboardArrowDown,
                                "Mover para baixo",
                                tint = if (index < order.lastIndex) OpenfyTextLow else OpenfyLine,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        Switch(
                            checked = id !in hidden,
                            onCheckedChange = { viewModel.toggleProfileCard(id, it) },
                        )
                    }
                }
            }

            ProfileCard("Faixas fixadas") {
                Button(
                    onClick = { pinPickerOpen = true },
                    enabled = extras.pins.size < ProfileExtras.PIN_LIMIT,
                ) {
                    Text("Fixar uma curtida")
                }
                Text(
                    "${extras.pins.size}/${ProfileExtras.PIN_LIMIT} fixadas. " +
                        "Use as setas para mudar a ordem.",
                    color = OpenfyTextLow,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            Row(
                Modifier.padding(top = 12.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = {
                        viewModel.saveProfile(
                            name,
                            extras.copy(bio = bio),
                            avatarUri,
                            coverUri,
                        )
                    },
                    enabled = !state.profileBusy,
                ) {
                    if (state.profileBusy) {
                        CircularProgressIndicator(
                            Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = OpenfyInk,
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text("Salvar perfil")
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = {
                    name = profile?.displayName.orEmpty()
                    bio = extras.bio
                    avatarUri = null
                    coverUri = null
                    editing = false
                }) {
                    Text("Cancelar", color = OpenfyTextLow)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (pinPickerOpen) {
        ProfilePinPickerDialog(
            liked = state.likedTracks,
            existing = extras.pins.mapTo(HashSet()) { it.id },
            onPick = { track ->
                viewModel.addProfilePin(track)
                pinPickerOpen = false
            },
            onDismiss = { pinPickerOpen = false },
        )
    }
}

@Composable
private fun ProfileCard(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 12.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(OpenfyPanel)
            .padding(14.dp),
    ) {
        Text(
            title,
            color = OpenfyTextHigh,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        content()
    }
}

@Composable
private fun ProfileSwitchRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = OpenfyTextHigh,
            fontSize = 11.sp,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ProfileStatCard(
    id: String,
    stats: ProfileStatsData?,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val (value, detail) = when (id) {
        ProfileStatsCards.MINUTES -> formatProfileMinutes(stats?.minutes ?: 0L) to "no mês"
        ProfileStatsCards.PLAYS -> "${stats?.plays ?: 0}" to "execuções"
        ProfileStatsCards.LIKES -> "${stats?.likes ?: 0}" to "curtidas"
        ProfileStatsCards.PLAYLISTS -> "${stats?.playlists ?: 0}" to "playlists"
        ProfileStatsCards.TOP_ARTISTS ->
            (stats?.topArtists?.firstOrNull()?.name ?: "—") to "artista mais ouvido"
        ProfileStatsCards.TOP_TRACKS ->
            (stats?.topTracks?.firstOrNull()?.track?.title ?: "—") to "faixa mais ouvida"
        else -> "—" to ""
    }
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(OpenfyPanel)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(accent))
            Spacer(Modifier.width(5.dp))
            Text(ProfileStatsCards.label(id), color = OpenfyTextLow, fontSize = 10.sp)
        }
        Text(
            value,
            color = OpenfyTextHigh,
            fontSize = if (id == ProfileStatsCards.TOP_ARTISTS || id == ProfileStatsCards.TOP_TRACKS) {
                14.sp
            } else {
                20.sp
            },
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 3.dp),
        )
        Text(
            detail,
            color = OpenfyTextLow,
            fontSize = 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ProfilePinRow(
    pin: ProfilePin,
    editing: Boolean,
    accent: Color,
    onPlay: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverImage(pin.thumbnail, 40.dp, 7.dp)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                pin.title,
                color = OpenfyTextHigh,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                pin.artist,
                color = OpenfyTextLow,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            formatTime(pin.durationSec.toDouble()),
            color = OpenfyTextLow,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        if (editing) {
            IconButton(onClick = onUp, modifier = Modifier.size(26.dp)) {
                Icon(
                    Icons.Filled.KeyboardArrowUp,
                    "Mover para cima",
                    tint = OpenfyTextLow,
                    modifier = Modifier.size(16.dp),
                )
            }
            IconButton(onClick = onDown, modifier = Modifier.size(26.dp)) {
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    "Mover para baixo",
                    tint = OpenfyTextLow,
                    modifier = Modifier.size(16.dp),
                )
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(26.dp)) {
                Icon(
                    Icons.Filled.Delete,
                    "Remover do perfil",
                    tint = OpenfyTextLow,
                    modifier = Modifier.size(16.dp),
                )
            }
        } else {
            IconButton(onClick = onPlay, modifier = Modifier.size(30.dp)) {
                Icon(
                    Icons.Filled.PlayArrow,
                    "Tocar",
                    tint = accent,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun ProfilePinPickerDialog(
    liked: List<Track>,
    existing: Set<String>,
    onPick: (Track) -> Unit,
    onDismiss: () -> Unit,
) {
    val available = liked.filter { it.id !in existing }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = OpenfyPanelHigh,
        title = { Text("Fixar uma curtida", color = OpenfyTextHigh) },
        text = {
            if (available.isEmpty()) {
                Text(
                    "Nenhuma curtida disponível. Curta uma faixa primeiro.",
                    color = OpenfyTextLow,
                    fontSize = 12.sp,
                )
            } else {
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(available, key = { it.id }) { track ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { onPick(track) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CoverImage(track.thumbnail, 38.dp, 7.dp)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    track.title,
                                    color = OpenfyTextHigh,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    track.artist,
                                    color = OpenfyTextLow,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Icon(
                                Icons.Filled.Add,
                                "Fixar",
                                tint = OpenfyAccent,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fechar", color = OpenfyAccent)
            }
        },
    )
}

@Composable
private fun SettingsScreen(
    state: AuthUiState,
    viewModel: AuthViewModel,
    modifier: Modifier = Modifier,
    onOpenProfile: () -> Unit = {},
) {
    val session = state.session ?: return
    Column(
        modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
    ) {
        Text(
            "Ajustes",
            color = OpenfyTextHigh,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 20.dp, bottom = 12.dp),
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = OpenfyPanelHigh),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Conta", color = OpenfyTextHigh, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(session.email, color = OpenfyTextLow, fontSize = 12.sp)
                Text("Dispositivo: ${state.deviceName}", color = OpenfyTextLow, fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.refreshLibrary() }, enabled = !state.busy) {
                        if (state.busy) {
                            CircularProgressIndicator(
                                Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = OpenfyInk,
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text("Sincronizar")
                    }
                    TextButton(onClick = onOpenProfile) {
                        Text("Abrir perfil", color = OpenfyAccent2)
                    }
                    TextButton(onClick = { viewModel.signOut() }) {
                        Text("Sair", color = OpenfyTextLow)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = OpenfyPanel),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Downloads",
                        color = OpenfyTextHigh,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${state.downloads.size} faixas",
                        color = OpenfyAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    "${formatBytes(state.downloadUsedBytes)} usados de ${formatLimitLabel(state.downloadLimitMb)}",
                    color = OpenfyTextLow,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    "Os arquivos ficam neste aparelho e continuam após sair da conta.",
                    color = OpenfyTextLow,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Spacer(Modifier.height(10.dp))
                Text("Limite de armazenamento", color = OpenfyTextHigh, fontSize = 13.sp)
                LazyRow(
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(listOf(512, 1_024, 2_048, 5_120, 10_240)) { option ->
                        AppearanceChip(formatLimitLabel(option), state.downloadLimitMb == option) {
                            viewModel.setDownloadLimitMb(option)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = { viewModel.clearDownloads() },
                    enabled = state.downloads.isNotEmpty(),
                ) {
                    Text("Limpar downloads", color = OpenfyAccent)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = OpenfyPanel),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Reprodução e sincronização", color = OpenfyTextHigh, fontWeight = FontWeight.SemiBold)
                Text(
                    "Estes ajustes também vão para a conta e chegam ao PC.",
                    color = OpenfyTextLow,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Spacer(Modifier.height(10.dp))
                Text("Qualidade do áudio", color = OpenfyTextHigh, fontSize = 13.sp)
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    AppearanceChip("Alta", state.audioQuality == "HIGH") {
                        viewModel.setAudioQuality("HIGH")
                    }
                    AppearanceChip("Baixa (economiza dados)", state.audioQuality == "LOW") {
                        viewModel.setAudioQuality("LOW")
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Baixar automaticamente", color = OpenfyTextHigh, fontSize = 13.sp)
                        Text(
                            "Baixa em segundo plano as mais ouvidas e curtidas.",
                            color = OpenfyTextLow,
                            fontSize = 11.sp,
                        )
                    }
                    Switch(
                        checked = state.autoDownload,
                        onCheckedChange = { viewModel.setAutoDownload(it) },
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = { viewModel.openLibraryPage(LibraryPage.DOWNLOADS) },
                    ) {
                        Text("Abrir downloads", color = OpenfyAccent, fontSize = 12.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${state.pendingSyncCount} pendentes",
                        color = if (state.pendingSyncCount > 0) OpenfyAccent else OpenfyTextLow,
                        fontSize = 11.sp,
                    )
                }
                Text(
                    if (state.settingsSyncedAt > 0) {
                        "Ajustes sincronizados em ${formatSavedAt(state.settingsSyncedAt)}"
                    } else {
                        "Ajustes ainda não sincronizados nesta conta."
                    },
                    color = OpenfyTextLow.copy(alpha = 0.8f),
                    fontSize = 10.sp,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = OpenfyPanel),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Crossfade",
                        color = OpenfyTextHigh,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        if (state.crossfadeSeconds == 0) "Desligado" else "${state.crossfadeSeconds}s",
                        color = OpenfyAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    "Sobreposição entre a faixa atual e a próxima. 0 desliga.",
                    color = OpenfyTextLow,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Slider(
                    value = state.crossfadeSeconds.toFloat(),
                    onValueChange = { viewModel.setCrossfadeSeconds(it.roundToInt()) },
                    valueRange = 0f..15f,
                    steps = 14,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = OpenfyPanel),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Aparência", color = OpenfyTextHigh, fontWeight = FontWeight.SemiBold)
                Text(
                    "Aplicada em todo o app e salva para as próximas aberturas.",
                    color = OpenfyTextLow,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Spacer(Modifier.height(10.dp))
                Text("Paleta de cores", color = OpenfyTextHigh, fontSize = 13.sp)
                LazyRow(
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(ColorPalette.entries) { preset ->
                        AppearanceChip(preset.label, state.palette == preset) {
                            viewModel.setPalette(preset)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("Layout do player", color = OpenfyTextHigh, fontSize = 13.sp)
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    PlayerLayout.entries.forEach { option ->
                        AppearanceChip(option.label, state.playerLayout == option) {
                            viewModel.setPlayerLayout(option)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Fundo pela capa", color = OpenfyTextHigh, fontSize = 13.sp)
                        Text(
                            "No player cheio, o gradiente usa as cores dominantes da capa.",
                            color = OpenfyTextLow,
                            fontSize = 11.sp,
                        )
                    }
                    Switch(
                        checked = state.coverGradient,
                        onCheckedChange = { viewModel.setCoverGradient(it) },
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = OpenfyPanel),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Openfy Android", color = OpenfyTextHigh, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Mesma conta, playlists e curtidas do PC. O recap mensal usa os eventos de reprodução desta conta.",
                    color = OpenfyTextLow,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Fila: ${state.queue.size} faixas · ${state.playlists.size} playlists · ${state.likedTracks.size} curtidas",
                    color = OpenfyTextLow.copy(alpha = 0.8f),
                    fontSize = 11.sp,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun AppearanceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) OpenfyAccent.copy(alpha = 0.16f) else OpenfyPanelHigh,
        contentColor = if (selected) OpenfyAccent else OpenfyTextLow,
        border = BorderStroke(1.dp, if (selected) OpenfyAccent else OpenfyLine),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun MiniPlayer(state: AuthUiState, viewModel: AuthViewModel, onOpen: () -> Unit) {
    val track = state.track ?: return
    Surface(color = OpenfyPanelHigh, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(38.dp).background(OpenfyPanel, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                CoverImage(track.thumbnail, 38.dp, 8.dp)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    track.title,
                    color = OpenfyTextHigh,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (track.id in state.downloadedIds) {
                        Icon(
                            Icons.Filled.DownloadDone,
                            "Baixada",
                            tint = OpenfyAccent,
                            modifier = Modifier.size(11.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        track.artist,
                        color = OpenfyTextLow,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = { viewModel.togglePlay() }, enabled = !state.busy) {
                Icon(
                    if (state.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    if (state.playing) "Pausar" else "Tocar",
                    tint = OpenfyAccent,
                    modifier = Modifier.size(24.dp),
                )
            }
            IconButton(onClick = { viewModel.next() }, enabled = state.hasNext || state.playing) {
                Icon(
                    Icons.Filled.SkipNext,
                    "Próxima",
                    tint = OpenfyTextLow,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun PlayerOverlay(
    state: AuthUiState,
    viewModel: AuthViewModel,
    onClose: () -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onArtist: (String) -> Unit,
) {
    BackHandler(onBack = onClose)
    val track = state.track
    var dragging by remember { mutableStateOf<Float?>(null) }
    var queueOpen by remember { mutableStateOf(false) }
    var lyricsOpen by remember { mutableStateOf(false) }
    var sleepOpen by remember { mutableStateOf(false) }
    var eqOpen by remember { mutableStateOf(false) }
    val total = max(1L, state.durationMs)
    val fraction = dragging ?: (state.positionMs.toFloat() / total).coerceIn(0f, 1f)
    val liked = track?.id in state.likedIds
    val currentDownloaded = track?.let { it.id in state.downloadedIds } == true
    val currentDownloadProgress = track?.let { state.downloadProgress[it.id] }
    val coverPalette = rememberCoverPalette(track?.thumbnail.orEmpty(), state.coverGradient)
    val ink = OpenfyInk
    val fallback = OpenfyHeroGradient
    val backdrop = if (!state.coverGradient || coverPalette == null) {
        fallback
    } else {
        remember(coverPalette, ink) {
            val p = coverPalette!!
            Brush.verticalGradient(
                0f to lerp(p.primary, ink, 0.38f),
                0.55f to lerp(p.secondary, ink, 0.68f),
                1f to ink,
            )
        }
    }
    LaunchedEffect(track?.id, lyricsOpen) {
        if (lyricsOpen && track != null) viewModel.loadLyrics(track)
    }

    Surface(Modifier.fillMaxSize(), color = OpenfyInk) {
        Column(
            Modifier.fillMaxSize()
                .background(backdrop)
                .padding(horizontal = 26.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        "Fechar player",
                        tint = OpenfyTextHigh,
                        modifier = Modifier.size(30.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("TOCANDO AGORA", color = OpenfyTextLow, fontSize = 10.sp, letterSpacing = 1.5.sp)
                    Text("Openfy", color = OpenfyTextHigh, fontSize = 12.sp)
                }
                Spacer(Modifier.weight(1f))
                Text("${state.queueIndex + 1}/${state.queue.size}", color = OpenfyTextLow, fontSize = 11.sp)
                IconButton(
                    onClick = {
                        track?.let {
                            if (currentDownloaded) {
                                viewModel.removeDownload(it.id)
                            } else if (currentDownloadProgress == null) {
                                viewModel.startDownload(it)
                            }
                        }
                    },
                    enabled = track != null && currentDownloadProgress == null,
                ) {
                    if (currentDownloadProgress != null) {
                        if (currentDownloadProgress < 0f) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = OpenfyAccent,
                                strokeWidth = 2.dp,
                                trackColor = OpenfyLine,
                            )
                        } else {
                            CircularProgressIndicator(
                                progress = { currentDownloadProgress },
                                modifier = Modifier.size(22.dp),
                                color = OpenfyAccent,
                                strokeWidth = 2.dp,
                                trackColor = OpenfyLine,
                            )
                        }
                    } else {
                        Icon(
                            if (currentDownloaded) Icons.Filled.DownloadDone else Icons.Filled.Download,
                            if (currentDownloaded) "Remover download" else "Baixar",
                            tint = if (currentDownloaded) OpenfyAccent else OpenfyTextLow,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))
            if (state.playerLayout == PlayerLayout.SIDE) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CoverImage(track?.thumbnail.orEmpty(), 124.dp, 18.dp)
                    Spacer(Modifier.width(20.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            track?.title ?: "Nada tocando",
                            color = OpenfyTextHigh,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            track?.artist.orEmpty(),
                            color = OpenfyTextLow,
                            fontSize = 13.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable {
                                track?.artist?.takeIf { it.isNotBlank() }?.let(onArtist)
                            },
                        )
                    }
                }
            } else {
                CoverImage(track?.thumbnail.orEmpty(), 250.dp, 22.dp)
                Spacer(Modifier.height(24.dp))
                Text(
                    track?.title ?: "Nada tocando",
                    color = OpenfyTextHigh,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                val artistName = track?.artist.orEmpty()
                if (artistName.isNotBlank()) {
                    ArtistLinkText(
                        artist = artistName,
                        color = OpenfyTextLow,
                        fontSize = 14.sp,
                        onArtist = onArtist,
                    )
                } else {
                    Text(
                        "",
                        color = OpenfyTextLow,
                        fontSize = 14.sp,
                    )
                }
            }
            Row(
                Modifier.padding(top = 6.dp).horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { track?.let { viewModel.toggleLike(it) } },
                    enabled = track != null,
                ) {
                    Icon(
                        if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        if (liked) "Remover curtida" else "Curtir",
                        tint = if (liked) OpenfyAccent2 else OpenfyTextLow,
                        modifier = Modifier.size(24.dp),
                    )
                }
                IconButton(
                    onClick = { track?.let(onAddToPlaylist) },
                    enabled = track != null,
                ) {
                    Icon(
                        Icons.Filled.PlaylistAdd,
                        "Adicionar à playlist",
                        tint = OpenfyTextLow,
                        modifier = Modifier.size(24.dp),
                    )
                }
                IconButton(
                    onClick = { lyricsOpen = true },
                    enabled = track != null,
                ) {
                    Icon(
                        Icons.Filled.Lyrics,
                        "Letras",
                        tint = if (lyricsOpen) OpenfyAccent else OpenfyTextLow,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Box {
                    IconButton(onClick = { sleepOpen = true }) {
                        Icon(
                            Icons.Filled.Timer,
                            "Timer para dormir",
                            tint = if (state.sleepUntil != null) OpenfyAccent else OpenfyTextLow,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    DropdownMenu(
                        expanded = sleepOpen,
                        onDismissRequest = { sleepOpen = false },
                    ) {
                        listOf(15, 30, 45, 60).forEach { minutes ->
                            DropdownMenuItem(
                                text = { Text("$minutes minutos") },
                                onClick = {
                                    sleepOpen = false
                                    viewModel.setSleepTimer(minutes)
                                },
                            )
                        }
                        if (state.sleepUntil != null) {
                            DropdownMenuItem(
                                text = { Text("Desligar timer", color = OpenfyAccent) },
                                onClick = {
                                    sleepOpen = false
                                    viewModel.setSleepTimer(null)
                                },
                            )
                        }
                    }
                }
                IconButton(onClick = { eqOpen = true }) {
                    Icon(
                        Icons.Filled.Tune,
                        "Equalizador",
                        tint = if (state.eqEnabled) OpenfyAccent else OpenfyTextLow,
                        modifier = Modifier.size(24.dp),
                    )
                }
                IconButton(
                    onClick = { viewModel.startTrackRadio() },
                    enabled = track != null && !state.busy,
                ) {
                    Icon(
                        Icons.Filled.Radio,
                        "Rádio a partir desta faixa",
                        tint = OpenfyTextLow,
                        modifier = Modifier.size(24.dp),
                    )
                }
                TextButton(onClick = { queueOpen = true }) {
                    Icon(
                        Icons.Filled.QueueMusic,
                        null,
                        tint = OpenfyTextLow,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Fila ${state.queue.size}", color = OpenfyTextLow, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.weight(1f))

            Slider(
                value = fraction,
                onValueChange = { dragging = it },
                onValueChangeFinished = {
                    dragging?.let { viewModel.seekTo((it * total).toLong()) }
                    dragging = null
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth()) {
                Text(
                    formatTime((dragging?.toDouble()?.times(total) ?: state.positionMs.toDouble()) / 1000.0),
                    color = OpenfyTextLow,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.weight(1f))
                Text(formatTime(state.durationMs / 1000.0), color = OpenfyTextLow, fontSize = 11.sp)
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { viewModel.toggleShuffle() }, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Filled.Shuffle,
                        if (state.shuffle) "Desligar aleatório" else "Aleatório",
                        tint = if (state.shuffle) OpenfyAccent else OpenfyTextLow,
                        modifier = Modifier.size(22.dp),
                    )
                }
                IconButton(onClick = { viewModel.previous() }, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Filled.SkipPrevious,
                        "Anterior",
                        tint = OpenfyTextHigh,
                        modifier = Modifier.size(32.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Box(
                    Modifier.size(68.dp).background(OpenfyAccent, CircleShape)
                        .clickable(enabled = track != null && !state.busy) { viewModel.togglePlay() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.busy) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = OpenfyInk)
                    } else {
                        Icon(
                            if (state.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            if (state.playing) "Pausar" else "Tocar",
                            tint = OpenfyInk,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                IconButton(onClick = { viewModel.next() }, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Filled.SkipNext,
                        "Próxima",
                        tint = OpenfyTextHigh,
                        modifier = Modifier.size(32.dp),
                    )
                }
                IconButton(onClick = { viewModel.cycleRepeat() }, modifier = Modifier.size(40.dp)) {
                    Icon(
                        if (state.repeatMode == RepeatMode.ONE) {
                            Icons.Filled.RepeatOne
                        } else {
                            Icons.Filled.Repeat
                        },
                        when (state.repeatMode) {
                            RepeatMode.OFF -> "Repetir desligado"
                            RepeatMode.ALL -> "Repetir toda a fila"
                            RepeatMode.ONE -> "Repetir uma faixa"
                        },
                        tint = if (state.repeatMode == RepeatMode.OFF) OpenfyTextLow else OpenfyAccent,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Fila: ${state.queue.size} faixas · Media3",
                color = OpenfyTextLow.copy(alpha = 0.7f),
                fontSize = 10.sp,
            )
        }
    }
    if (queueOpen) {
        QueueDialog(state, viewModel, onDismiss = { queueOpen = false })
    }
    if (lyricsOpen) {
        LyricsDialog(state, viewModel, onDismiss = { lyricsOpen = false })
    }
    if (eqOpen) {
        EqualizerDialog(state, viewModel, onDismiss = { eqOpen = false })
    }
}

@Composable
private fun QueueDialog(
    state: AuthUiState,
    viewModel: AuthViewModel,
    onDismiss: () -> Unit,
) {
    var saveOpen by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = OpenfyPanelHigh,
        title = {
            Column {
                Text("Fila · ${state.queue.size} faixas", color = OpenfyTextHigh)
                Text(
                    "Use as setas para reordenar. Para incluir outra faixa, use ⋮ › Tocar em seguida ou Adicionar ao fim da fila.",
                    color = OpenfyTextLow,
                    fontSize = 10.sp,
                )
            }
        },
        text = {
            Column {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { viewModel.toggleShuffle() }) {
                        Icon(
                            Icons.Filled.Shuffle,
                            null,
                            tint = if (state.shuffle) OpenfyAccent else OpenfyTextLow,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (state.shuffle) "Aleatório ligado" else "Aleatório",
                            color = if (state.shuffle) OpenfyAccent else OpenfyTextLow,
                            fontSize = 11.sp,
                        )
                    }
                    TextButton(onClick = { viewModel.cycleRepeat() }) {
                        Icon(
                            if (state.repeatMode == RepeatMode.ONE) {
                                Icons.Filled.RepeatOne
                            } else {
                                Icons.Filled.Repeat
                            },
                            null,
                            tint = if (state.repeatMode == RepeatMode.OFF) OpenfyTextLow else OpenfyAccent,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            when (state.repeatMode) {
                                RepeatMode.OFF -> "Repetir"
                                RepeatMode.ALL -> "Repetindo fila"
                                RepeatMode.ONE -> "Repetindo faixa"
                            },
                            color = if (state.repeatMode == RepeatMode.OFF) OpenfyTextLow else OpenfyAccent,
                            fontSize = 11.sp,
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { saveOpen = true }, enabled = state.queue.isNotEmpty()) {
                        Icon(
                            Icons.Filled.PlaylistAdd,
                            null,
                            tint = OpenfyAccent,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Salvar fila", color = OpenfyAccent, fontSize = 11.sp)
                    }
                    TextButton(
                        onClick = { viewModel.clearUpcoming() },
                        enabled = state.queueIndex >= 0 && state.queueIndex < state.queue.lastIndex,
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            null,
                            tint = OpenfyTextLow,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Limpar próximas", color = OpenfyTextLow, fontSize = 11.sp)
                    }
                    if (state.queueUndo != null) {
                        TextButton(onClick = { viewModel.undoClearUpcoming() }) {
                            Text("Desfazer", color = OpenfyAccent, fontSize = 11.sp)
                        }
                    }
                }
                if (state.queue.isEmpty()) {
                    Text("A fila está vazia.", color = OpenfyTextLow, fontSize = 12.sp)
                } else {
                    LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    itemsIndexed(state.queue) { index, track ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable {
                                    viewModel.playQueueIndex(index)
                                    onDismiss()
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${index + 1}",
                                color = if (index == state.queueIndex) OpenfyAccent else OpenfyTextLow,
                                fontSize = 11.sp,
                                modifier = Modifier.width(24.dp),
                            )
                            CoverImage(track.thumbnail, 34.dp, 6.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    track.title,
                                    color = if (index == state.queueIndex) OpenfyAccent else OpenfyTextHigh,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    track.artist,
                                    color = OpenfyTextLow,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (track.id in state.downloadedIds) {
                                Icon(
                                    Icons.Filled.DownloadDone,
                                    "Baixada",
                                    tint = OpenfyAccent,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    Modifier.size(28.dp).clip(CircleShape)
                                        .clickable(enabled = index > 0) {
                                            viewModel.moveQueueIndex(index, index - 1)
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Filled.KeyboardArrowUp,
                                        "Mover para cima",
                                        tint = if (index > 0) OpenfyTextLow else OpenfyLine,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                Box(
                                    Modifier.size(28.dp).clip(CircleShape)
                                        .clickable(enabled = index < state.queue.lastIndex) {
                                            viewModel.moveQueueIndex(index, index + 1)
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Filled.KeyboardArrowDown,
                                        "Mover para baixo",
                                        tint = if (index < state.queue.lastIndex) OpenfyTextLow else OpenfyLine,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                            Box(
                                Modifier.size(36.dp).clip(CircleShape)
                                    .clickable { viewModel.removeQueueIndex(index) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Filled.Delete,
                                    "Remover da fila",
                                    tint = OpenfyTextLow,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fechar", color = OpenfyAccent)
            }
        },
    )
    if (saveOpen) {
        NewPlaylistDialog(
            onDismiss = { saveOpen = false },
            onCreate = { name ->
                saveOpen = false
                viewModel.saveQueueAsPlaylist(name)
            },
        )
    }
}

@Composable
private fun LyricsDialog(
    state: AuthUiState,
    viewModel: AuthViewModel,
    onDismiss: () -> Unit,
) {
    val track = state.track
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = OpenfyPanelHigh,
        title = {
            Column {
                Text("Letras", color = OpenfyTextHigh, fontWeight = FontWeight.SemiBold)
                if (track != null) {
                    Text(
                        "${track.title} · ${track.artist}",
                        color = OpenfyTextLow,
                        fontSize = 11.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        text = {
            when {
                state.lyricsLoading -> Box(
                    Modifier.fillMaxWidth().height(180.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        Modifier.size(26.dp),
                        strokeWidth = 2.dp,
                        color = OpenfyAccent,
                    )
                }
                state.lyrics.isEmpty() -> Text(
                    state.lyricsError ?: "Letra não encontrada.",
                    color = OpenfyTextLow,
                    fontSize = 12.sp,
                )
                else -> {
                    val listState = rememberLazyListState()
                    val current = state.lyrics.indexOfLast { line ->
                        line.timeMs >= 0 && line.timeMs <= state.positionMs
                    }
                    LaunchedEffect(current) {
                        if (current >= 0) listState.animateScrollToItem(current)
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.heightIn(max = 430.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(state.lyrics) { index, line ->
                            val active = index == current
                            Text(
                                line.text,
                                color = if (active) OpenfyAccent else OpenfyTextHigh.copy(alpha = 0.72f),
                                fontSize = if (active) 15.sp else 13.sp,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fechar", color = OpenfyAccent)
            }
        },
    )
}

@Composable
private fun EqualizerDialog(
    state: AuthUiState,
    viewModel: AuthViewModel,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = OpenfyPanelHigh,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Tune,
                    null,
                    tint = OpenfyAccent,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("Equalizador", color = OpenfyTextHigh, fontWeight = FontWeight.SemiBold)
            }
        },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Ativar", color = OpenfyTextHigh, fontSize = 13.sp)
                    Spacer(Modifier.weight(1f))
                    Switch(
                        checked = state.eqEnabled,
                        onCheckedChange = { viewModel.setEqEnabled(it) },
                    )
                }
                Spacer(Modifier.height(8.dp))
                EqualizerController.BAND_FREQS.forEachIndexed { index, freq ->
                    val label = if (freq >= 1000) "${freq / 1000} kHz" else "$freq Hz"
                    val gain = state.eqGains.getOrElse(index) { 0 }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            label,
                            color = OpenfyTextLow,
                            fontSize = 11.sp,
                            modifier = Modifier.width(54.dp),
                        )
                        Slider(
                            value = gain.toFloat(),
                            onValueChange = { viewModel.setEqGain(index, it.roundToInt()) },
                            valueRange = -12f..12f,
                            modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                        )
                        Text(
                            "${if (gain > 0) "+" else ""}$gain",
                            color = OpenfyTextLow,
                            fontSize = 11.sp,
                            modifier = Modifier.width(30.dp),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(EqualizerController.PRESETS.keys.toList()) { name ->
                        TextButton(onClick = { viewModel.applyEqPreset(name) }) {
                            Text(name, color = OpenfyAccent, fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fechar", color = OpenfyAccent)
            }
        },
    )
}

@Composable
private fun AddToPlaylistDialog(
    track: Track,
    playlists: List<PlaylistDto>,
    onDismiss: () -> Unit,
    onSelect: (PlaylistDto) -> Unit,
    onCreateNew: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = OpenfyPanelHigh,
        title = { Text("Adicionar à playlist", color = OpenfyTextHigh) },
        text = {
            Column {
                Text(
                    track.title,
                    color = OpenfyTextLow,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                if (playlists.isEmpty()) {
                    Column {
                        Text("Nenhuma playlist ainda.", color = OpenfyTextLow, fontSize = 12.sp)
                        TextButton(onClick = onCreateNew) {
                            Text("Criar nova playlist", color = OpenfyAccent2)
                        }
                    }
                } else {
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(playlists, key = { it.id }) { playlist ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable { onSelect(playlist) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CoverImage(playlist.coverUrl, 36.dp, 8.dp)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    playlist.name,
                                    color = OpenfyTextHigh,
                                    fontSize = 13.sp,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Icon(
                                    Icons.Filled.PlaylistAdd,
                                    null,
                                    tint = OpenfyAccent,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = onCreateNew) {
                        Text("Criar nova playlist", color = OpenfyAccent2)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = OpenfyTextLow)
            }
        },
    )
}

@Composable
private fun CoverImage(url: String, size: Dp, corner: Dp) {
    Box(
        Modifier.size(size)
            .clip(RoundedCornerShape(corner))
            .background(OpenfyPanelHigh),
        contentAlignment = Alignment.Center,
    ) {
        Text("♫", color = OpenfyAccent, fontSize = (size.value * 0.32f).sp)
        if (url.isNotBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun ImportPlaylistDialog(onDismiss: () -> Unit, onImport: (String) -> Unit) {
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = OpenfyPanelHigh,
        title = { Text("Importar playlist", color = OpenfyTextHigh) },
        text = {
            Column {
                Text(
                    "Cole o link de uma playlist ou álbum público do YouTube Music, ou de uma playlist pública do Spotify. As faixas entram na sua conta e aparecem no PC.",
                    color = OpenfyTextLow,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    singleLine = true,
                    label = { Text("Link do YouTube Music ou Spotify") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onImport(url) },
                enabled = url.isNotBlank(),
            ) {
                Text("Importar", color = OpenfyAccent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = OpenfyTextLow)
            }
        },
    )
}

@Composable
private fun JoinPlaylistDialog(onDismiss: () -> Unit, onJoin: (String) -> Unit) {
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = OpenfyPanelHigh,
        title = { Text("Entrar com código", color = OpenfyTextHigh) },
        text = {
            Column {
                Text(
                    "Use o código de convite de uma playlist compartilhada. No PC, abra a playlist e toque em Compartilhar para gerar o código. Você entra como editor e as faixas passam a sincronizar entre os aparelhos.",
                    color = OpenfyTextLow,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    singleLine = true,
                    label = { Text("Código ou link") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onJoin(code) },
                enabled = code.isNotBlank(),
            ) {
                Text("Entrar na playlist", color = OpenfyAccent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = OpenfyTextLow)
            }
        },
    )
}

@Composable
private fun SharePlaylistDialog(
    playlistName: String,
    code: String?,
    loading: Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val link = code?.let { "https://leo-bsb.github.io/openfy/join.html?code=$it" }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = OpenfyPanelHigh,
        title = { Text("Compartilhar \"$playlistName\"", color = OpenfyTextHigh) },
        text = {
            Column {
                when {
                    loading -> Box(
                        Modifier.fillMaxWidth().height(80.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = OpenfyAccent,
                        )
                    }
                    link == null -> Text(
                        "Não foi possível gerar o convite.",
                        color = OpenfyTextLow,
                        fontSize = 12.sp,
                    )
                    else -> {
                        Text("Código", color = OpenfyTextLow, fontSize = 11.sp)
                        Text(code, color = OpenfyAccent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            link,
                            color = OpenfyTextLow,
                            fontSize = 11.sp,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                        as ClipboardManager
                                    clipboard.setPrimaryClip(
                                        ClipData.newPlainText("Openfy", link),
                                    )
                                },
                            ) {
                                Text("Copiar")
                            }
                            Button(
                                onClick = {
                                    val send = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, link)
                                    }
                                    context.startActivity(
                                        Intent.createChooser(send, "Compartilhar playlist"),
                                    )
                                },
                            ) {
                                Text("Enviar")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fechar", color = OpenfyAccent)
            }
        },
    )
}

@Composable
private fun NewPlaylistDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = OpenfyPanelHigh,
        title = { Text("Nova playlist", color = OpenfyTextHigh) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nome") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onCreate(name) }) {
                Text("Criar", color = OpenfyAccent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = OpenfyTextLow)
            }
        },
    )
}

@Composable
private fun LoginScreen(state: AuthUiState, viewModel: AuthViewModel) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }

    Box(
        Modifier.fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to OpenfyPanel,
                    0.55f to OpenfyInk,
                    1f to OpenfyInk,
                )
            )
            .imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.openfy_logo),
                contentDescription = "Openfy",
                modifier = Modifier.size(96.dp),
            )
            Text("openfy", color = OpenfyAccent, fontSize = 40.sp, fontWeight = FontWeight.Bold)
            Text("Sua música, no PC e no celular.", color = OpenfyTextLow, fontSize = 13.sp)
            Spacer(Modifier.height(28.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = OpenfyPanelHigh),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        if (creating) "Criar conta" else "Entrar na conta",
                        color = OpenfyTextHigh,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "A conta sincroniza playlists, curtidas, histórico e recap. O modo local continua no PC.",
                        color = OpenfyTextLow,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 6.dp, bottom = 16.dp),
                    )
                    if (creating) {
                        OutlinedTextField(
                            value = displayName,
                            onValueChange = { displayName = it },
                            label = { Text("Nome de exibição") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("E-mail") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Senha") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    state.error?.let {
                        Spacer(Modifier.height(10.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(18.dp))
                    Button(
                        onClick = {
                            if (creating) viewModel.signUp(email, password, displayName)
                            else viewModel.signIn(email, password)
                        },
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (state.busy) {
                            CircularProgressIndicator(
                                Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = OpenfyInk,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (creating) "Criar conta" else "Entrar")
                    }
                    TextButton(
                        onClick = {
                            creating = !creating
                            viewModel.clearFeedback()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (creating) "Já tenho conta" else "Criar uma conta",
                            color = OpenfyAccent2,
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "Sem login, o Openfy do PC continua tocando sua biblioteca local.",
                color = OpenfyTextLow.copy(alpha = 0.8f),
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
    }
}
