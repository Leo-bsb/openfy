package com.openfy.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.openfy.android.R
import com.openfy.android.data.PlaylistDto
import com.openfy.core.ColorPalette
import com.openfy.core.PlayerLayout
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
    BackHandler(enabled = state.openPlaylist != null) { viewModel.closePlaylist() }

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
                                viewModel.closePlaylist()
                                tab = 0
                            },
                            icon = { Icon(Icons.Filled.Home, null, modifier = Modifier.size(22.dp)) },
                            label = { Text("Início") },
                        )
                        NavigationBarItem(
                            selected = tab == 1 && state.openPlaylist == null,
                            onClick = {
                                viewModel.closePlaylist()
                                tab = 1
                            },
                            icon = { Icon(Icons.Filled.Search, null, modifier = Modifier.size(22.dp)) },
                            label = { Text("Buscar") },
                        )
                        NavigationBarItem(
                            selected = tab == 2 && state.openPlaylist == null,
                            onClick = {
                                viewModel.closePlaylist()
                                tab = 2
                            },
                            icon = { Icon(Icons.Filled.Favorite, null, modifier = Modifier.size(22.dp)) },
                            label = { Text("Curtidas") },
                        )
                        NavigationBarItem(
                            selected = tab == 3 && state.openPlaylist == null,
                            onClick = {
                                viewModel.closePlaylist()
                                tab = 3
                            },
                            icon = { Icon(Icons.Filled.Settings, null, modifier = Modifier.size(22.dp)) },
                            label = { Text("Ajustes") },
                        )
                    }
                }
            },
        ) { padding ->
            when {
                state.openPlaylist != null -> PlaylistDetailScreen(state, viewModel, Modifier.padding(padding)) {
                    addToPlaylistTrack = it
                }
                tab == 0 -> HomeScreen(state, viewModel, Modifier.padding(padding))
                tab == 1 -> SearchScreen(state, viewModel, Modifier.padding(padding)) {
                    addToPlaylistTrack = it
                }
                tab == 2 -> LikedScreen(state, viewModel, Modifier.padding(padding)) {
                    addToPlaylistTrack = it
                }
                else -> SettingsScreen(state, viewModel, Modifier.padding(padding))
            }
        }
        if (playerOpen) {
            PlayerOverlay(
                state = state,
                viewModel = viewModel,
                onClose = { playerOpen = false },
                onAddToPlaylist = { addToPlaylistTrack = it },
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
private fun HomeScreen(state: AuthUiState, viewModel: AuthViewModel, modifier: Modifier = Modifier) {
    var newPlaylistOpen by remember { mutableStateOf(false) }
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
                TextButton(onClick = { viewModel.signOut() }) {
                    Text("Sair", color = OpenfyTextLow)
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = OpenfyPanelHigh),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(9.dp).background(OpenfyAccent, CircleShape))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Conta sincronizada",
                            color = OpenfyTextHigh,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                        )
                    }
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
                        TextButton(onClick = { newPlaylistOpen = true }, enabled = !state.busy) {
                            Text("Nova playlist", color = OpenfyAccent2)
                        }
                    }
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
                        TrackTile(track) { viewModel.playSearchResult(track) }
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
                        TrackTile(track) { viewModel.playTopTrack(track) }
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
                Text("${state.playlists.size}", color = OpenfyTextLow, fontSize = 12.sp)
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
private fun TrackTile(track: Track, onClick: () -> Unit) {
    Column(Modifier.width(116.dp).clickable(onClick = onClick)) {
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
        Text(
            track.artist,
            color = OpenfyTextLow,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
        Spacer(Modifier.height(12.dp))
        if (state.searchResults.isEmpty() && !state.searching) {
            Text(
                "Busque uma música e toque para ouvir. O catálogo é o YouTube Music, sem anúncios.",
                color = OpenfyTextLow,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                )
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
                Text(
                    "${track.artist} · ${formatTime(track.durationSec.toDouble())}",
                    color = OpenfyTextLow,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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
                        text = { Text("Adicionar à fila") },
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
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(
                state.playlistTracks,
                key = { index, track -> "${track.id}-$index" },
            ) { index, track ->
                val item = state.playlistItems.firstOrNull { it.trackId == track.id }
                TrackRow(
                    track = track,
                    isCurrent = state.track?.id == track.id,
                    liked = track.id in state.likedIds,
                    onPlay = { viewModel.playPlaylistTrack(track) },
                    onLike = { viewModel.toggleLike(track) },
                    onPlayNext = { viewModel.playNext(track) },
                    onAddQueue = { viewModel.addToQueue(track) },
                    onAddPlaylist = { onAddToPlaylist(track) },
                    onRemove = item?.let { entry ->
                        { viewModel.removeFromPlaylist(entry) }
                    },
                )
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

@Composable
private fun SettingsScreen(
    state: AuthUiState,
    viewModel: AuthViewModel,
    modifier: Modifier = Modifier,
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
                    valueRange = 0f..12f,
                    steps = 11,
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
                Text(
                    track.artist,
                    color = OpenfyTextLow,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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
) {
    BackHandler(onBack = onClose)
    val track = state.track
    var dragging by remember { mutableStateOf<Float?>(null) }
    var queueOpen by remember { mutableStateOf(false) }
    val total = max(1L, state.durationMs)
    val fraction = dragging ?: (state.positionMs.toFloat() / total).coerceIn(0f, 1f)
    val liked = track?.id in state.likedIds
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
                Text(
                    track?.artist.orEmpty(),
                    color = OpenfyTextLow,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                Modifier.padding(top = 6.dp),
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
                IconButton(onClick = { viewModel.previous() }) {
                    Icon(
                        Icons.Filled.SkipPrevious,
                        "Anterior",
                        tint = OpenfyTextHigh,
                        modifier = Modifier.size(32.dp),
                    )
                }
                Spacer(Modifier.width(22.dp))
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
                Spacer(Modifier.width(22.dp))
                IconButton(onClick = { viewModel.next() }) {
                    Icon(
                        Icons.Filled.SkipNext,
                        "Próxima",
                        tint = OpenfyTextHigh,
                        modifier = Modifier.size(32.dp),
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
}

@Composable
private fun QueueDialog(
    state: AuthUiState,
    viewModel: AuthViewModel,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = OpenfyPanelHigh,
        title = { Text("Fila · ${state.queue.size} faixas", color = OpenfyTextHigh) },
        text = {
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
                            IconButton(onClick = { viewModel.removeQueueIndex(index) }) {
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
