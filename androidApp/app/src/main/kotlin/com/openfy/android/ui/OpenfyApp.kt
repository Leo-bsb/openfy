package com.openfy.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.openfy.android.R
import com.openfy.android.data.PlaylistDto
import com.openfy.core.Track
import com.openfy.core.formatTime
import com.openfy.android.ui.theme.OpenfyAccent
import com.openfy.android.ui.theme.OpenfyAccent2
import com.openfy.android.ui.theme.OpenfyInk
import com.openfy.android.ui.theme.OpenfyPanel
import com.openfy.android.ui.theme.OpenfyPanelHigh
import com.openfy.android.ui.theme.OpenfyTextHigh
import com.openfy.android.ui.theme.OpenfyTextLow
import kotlin.math.max

@Composable
fun OpenfyApp(viewModel: AuthViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when {
            state.loading -> LoadingScreen()
            state.session == null -> LoginScreen(state, viewModel)
            else -> MainShell(state, viewModel)
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

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = OpenfyInk,
            bottomBar = {
                Column {
                    if (state.track != null) {
                        MiniPlayer(state, viewModel) { playerOpen = true }
                    }
                    NavigationBar(containerColor = OpenfyPanel) {
                        NavigationBarItem(
                            selected = tab == 0,
                            onClick = { tab = 0 },
                            icon = { Text("⌂", fontSize = 20.sp) },
                            label = { Text("Início") },
                        )
                        NavigationBarItem(
                            selected = tab == 1,
                            onClick = { tab = 1 },
                            icon = { Text("⌕", fontSize = 20.sp) },
                            label = { Text("Buscar") },
                        )
                    }
                }
            },
        ) { padding ->
            when (tab) {
                0 -> HomeScreen(state, viewModel, Modifier.padding(padding))
                else -> SearchScreen(state, viewModel, Modifier.padding(padding))
            }
        }
        if (playerOpen) {
            PlayerOverlay(state, viewModel) { playerOpen = false }
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

        state.error?.let { error ->
            item { Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
        }
        state.message?.let { message ->
            item { Text(message, color = OpenfyAccent, fontSize = 12.sp) }
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
                PlaylistCard(playlist) { viewModel.playPlaylist(playlist.id) }
            }
        }

        item {
            Text(
                "Toque numa playlist para tocar. O player usa Media3/ExoPlayer.",
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
private fun PlaylistCard(playlist: PlaylistDto, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = OpenfyPanel),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoverImage(playlist.coverUrl, 42.dp, 10.dp)
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
                Text("Toque para tocar", color = OpenfyTextLow, fontSize = 11.sp)
            }
            Text("▶", color = OpenfyAccent, fontSize = 16.sp)
        }
    }
}

@Composable
private fun SearchScreen(state: AuthUiState, viewModel: AuthViewModel, modifier: Modifier = Modifier) {
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
            leadingIcon = { Text("⌕", color = OpenfyTextLow, fontSize = 18.sp) },
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
                SearchResultRow(track, isCurrent = state.track?.id == track.id) {
                    viewModel.playSearchResult(track)
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

@Composable
private fun SearchResultRow(track: Track, isCurrent: Boolean, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = OpenfyPanel),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoverImage(track.thumbnail, 38.dp, 8.dp)
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
                    track.artist,
                    color = OpenfyTextLow,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(formatTime(track.durationSec.toDouble()), color = OpenfyTextLow, fontSize = 11.sp)
        }
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
            TextButton(onClick = { viewModel.togglePlay() }, enabled = !state.busy) {
                Text(if (state.playing) "⏸" else "▶", color = OpenfyAccent, fontSize = 22.sp)
            }
            TextButton(onClick = { viewModel.next() }, enabled = state.hasNext || state.playing) {
                Text("⏭", color = OpenfyTextLow, fontSize = 18.sp)
            }
        }
    }
}

@Composable
private fun PlayerOverlay(state: AuthUiState, viewModel: AuthViewModel, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val track = state.track
    var dragging by remember { mutableStateOf<Float?>(null) }
    val total = max(1L, state.durationMs)
    val fraction = dragging ?: (state.positionMs.toFloat() / total).coerceIn(0f, 1f)

    Surface(Modifier.fillMaxSize(), color = OpenfyInk) {
        Column(
            Modifier.fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to OpenfyPanelHigh,
                        0.55f to OpenfyInk,
                        1f to OpenfyInk,
                    )
                )
                .padding(horizontal = 26.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onClose) {
                    Text("⌄", color = OpenfyTextHigh, fontSize = 30.sp)
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
                TextButton(onClick = { viewModel.previous() }) {
                    Text("⏮", color = OpenfyTextHigh, fontSize = 28.sp)
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
                        Text(if (state.playing) "⏸" else "▶", color = OpenfyInk, fontSize = 32.sp)
                    }
                }
                Spacer(Modifier.width(22.dp))
                TextButton(onClick = { viewModel.next() }) {
                    Text("⏭", color = OpenfyTextHigh, fontSize = 28.sp)
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
