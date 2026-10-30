package com.openfy.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.openfy.core.AppController
import com.openfy.core.Artist
import com.openfy.core.ArtistPage
import com.openfy.core.BuildInfo
import com.openfy.core.CollectionKind
import com.openfy.core.ColorPalette
import com.openfy.core.MediaCard
import com.openfy.core.MediaKind
import com.openfy.core.PlayerLayout
import com.openfy.core.Playlist
import com.openfy.core.PlaylistPrompt
import com.openfy.core.ProfileArtistStat
import com.openfy.core.ProfileExtras
import com.openfy.core.ProfilePin
import com.openfy.core.ProfileStatsCards
import com.openfy.core.ProfileStatsData
import com.openfy.core.ProfileStatsVisibility
import com.openfy.core.ProfileStyle
import com.openfy.core.RecapArtist
import com.openfy.core.RecapDay
import com.openfy.core.RecapSnapshot
import com.openfy.core.RecapTrack
import com.openfy.core.RepeatMode
import com.openfy.core.Screen
import com.openfy.core.SearchMode
import com.openfy.core.Track
import com.openfy.core.formatTime
import com.openfy.data.LyricLine
import com.openfy.data.UpdateInstaller
import com.openfy.data.UpdateStatus
import com.openfy.player.Player
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

// ------------------------------------------------------------------ artwork
@Composable
fun Cover(url: String, size: Dp, radius: Dp = 8.dp, width: Dp = size, height: Dp = size) {
    var bitmap by remember(url) { mutableStateOf(ArtworkCache.peek(url)) }
    LaunchedEffect(url) {
        if (url.isBlank() || bitmap != null) return@LaunchedEffect
        bitmap = ArtworkCache.get(url)
    }
    Box(
        Modifier.width(width).height(height).clip(RoundedCornerShape(radius)).background(Tokens.panelHigh),
        contentAlignment = Alignment.Center,
    ) {
        val img = bitmap
        if (img != null) {
            Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(
                Icons.Filled.MusicNote,
                null,
                tint = Tokens.textLow,
                modifier = Modifier.size(minOf(width, height) / 3),
            )
        }
    }
}

@Composable
fun LocalCover(path: String, size: Dp, radius: Dp = 8.dp, width: Dp = size, height: Dp = size) {
    var bitmap by remember(path) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(path) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = Files.readAllBytes(Path.of(path))
                org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()
            }.getOrNull()
        }
    }
    Box(
        Modifier.width(width).height(height).clip(RoundedCornerShape(radius)).background(Tokens.panelHigh),
        contentAlignment = Alignment.Center,
    ) {
        val img = bitmap
        if (img != null) {
            Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(
                Icons.Filled.MusicNote,
                null,
                tint = Tokens.textLow,
                modifier = Modifier.size(minOf(width, height) / 3),
            )
        }
    }
}

/** Capa de playlist: URL da primeira faixa ou arquivo local escolhido. */
@Composable
fun CoverSource(source: String, size: Dp, radius: Dp = 8.dp, width: Dp = size, height: Dp = size) {
    when {
        source.startsWith("http") -> Cover(source, size, radius, width, height)
        source.isNotBlank() -> LocalCover(source, size, radius, width, height)
        else -> Cover("", size, radius, width, height)
    }
}

@Composable
private fun openfySliderColors() = SliderDefaults.colors(
    thumbColor = Tokens.accent,
    activeTrackColor = Tokens.accent,
    inactiveTrackColor = Tokens.line,
    activeTickColor = Color.Transparent,
    inactiveTickColor = Color.Transparent,
)

/** Rotulo ao passar o mouse; usado para explicar icones e atalhos. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Tip(text: String, content: @Composable () -> Unit) {
    TooltipArea(
        tooltip = {
            Surface(
                color = Tokens.panelHigh,
                shape = RoundedCornerShape(8.dp),
                shadowElevation = 6.dp,
            ) {
                Text(
                    text,
                    color = Tokens.textHigh,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                )
            }
        },
        delayMillis = 400,
        tooltipPlacement = TooltipPlacement.ComponentRect(
            anchor = Alignment.TopCenter,
            alignment = Alignment.BottomCenter,
            offset = DpOffset(0.dp, (-8).dp),
        ),
        content = content,
    )
}

/**
 * Botao de icone com estado ligado/desligado. O estado ativo usa fundo solido
 * (nao apenas cor), para funcionar tambem para quem tem daltonismo.
 */
@Composable
private fun ToggleIconButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    size: Dp = 30.dp,
    iconSize: Dp = 16.dp,
    enabled: Boolean = true,
    tooltip: String = label,
    activeColor: Color = Tokens.active,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Tip(tooltip) {
        Box(
            Modifier.size(size)
                .clip(CircleShape)
                .background(
                    if (hovered && enabled) Tokens.panelHigh else Color.Transparent
                )
                .hoverable(interaction)
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                label,
                tint = when {
                    !enabled -> Tokens.textLow.copy(alpha = 0.35f)
                    active -> activeColor
                    else -> Tokens.textLow
                },
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

/** Cores de switch com trilho claramente diferente entre ligado e desligado. */
@Composable
private fun openfySwitchColors() = androidx.compose.material3.SwitchDefaults.colors(
    checkedThumbColor = Tokens.ink,
    checkedTrackColor = Tokens.accent,
    checkedBorderColor = Tokens.accent,
    uncheckedThumbColor = Tokens.textLow,
    uncheckedTrackColor = Tokens.panelHigh,
    uncheckedBorderColor = Tokens.line,
)

// ------------------------------------------------------------------ shell
@Composable
fun OpenfyApp(c: AppController, onExit: () -> Unit) {
    val screen by c.screen.collectAsState()
    val playlists by c.playlists.collectAsState()
    val message by c.message.collectAsState()
    val queue by c.player.queue.collectAsState()
    val playlistPrompt by c.playlistPrompt.collectAsState()
    val queueOpen by c.queueOpen.collectAsState()
    val nowPlayingOpen by c.nowPlayingOpen.collectAsState()
    val undo by c.queueUndo.collectAsState()
    val offline by c.offline.collectAsState()
    val importOpen by c.importDialogOpen.collectAsState()
    val palette by c.palette.collectAsState()

    // Instalador do desktop pronto: fecha o app para o auxiliar rodar o .exe.
    val exitForUpdate by c.updateExitRequested.collectAsState()
    LaunchedEffect(exitForUpdate) {
        if (exitForUpdate) {
            delay(1500)
            onExit()
        }
    }

    LaunchedEffect(message) {
        if (message != null) {
            delay(if (undo != null) 5000 else 2400)
            c.consumeMessage()
        }
    }

    LaunchedEffect(nowPlayingOpen) {
        if (nowPlayingOpen) c.closeQueue()
    }

    var lastMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(message) {
        if (message != null) lastMessage = message
    }

    OpenfyTheme(palette) {
        Box(Modifier.fillMaxSize().background(Tokens.ink)) {
            // Brilho ambiente no topo, no tom da marca.
            Box(
                Modifier.fillMaxWidth().height(340.dp).background(
                    Brush.verticalGradient(
                        listOf(Tokens.accent.copy(alpha = 0.10f), Color.Transparent),
                    ),
                ),
            )

            Column(Modifier.fillMaxSize()) {
                OfflineBanner(offline)
                Row(Modifier.weight(1f).fillMaxWidth().padding(start = 8.dp, top = 8.dp, end = 8.dp)) {
                    Sidebar(c, screen, playlists)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        TopBar(c, queueOpen, onExit) { c.toggleQueue() }
                        Box(Modifier.weight(1f)) {
                            Crossfade(
                                targetState = screen,
                                animationSpec = tween(180),
                                label = "screen",
                            ) { target ->
                                Content(c, target)
                            }
                        }
                    }
                    AnimatedVisibility(
                        visible = queueOpen,
                        enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(tween(160)),
                        exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(tween(140)),
                    ) {
                        Row {
                            Spacer(Modifier.width(8.dp))
                            QueuePanel(c, queue) { c.closeQueue() }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                NowPlayingBar(c, queueOpen) { c.toggleQueue() }
            }

            AnimatedVisibility(
                visible = message != null,
                enter = fadeIn(tween(160)) + slideInVertically(initialOffsetY = { it / 2 }),
                exit = fadeOut(tween(140)) + slideOutVertically(targetOffsetY = { it / 2 }),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 126.dp),
            ) {
                lastMessage?.let { text ->
                    Surface(
                        color = Tokens.panelHigh,
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        ) {
                            Text(text, color = Tokens.textHigh, fontSize = 13.sp)
                            if (undo != null) {
                                Spacer(Modifier.width(12.dp))
                                TextButton(onClick = { c.undoQueue() }) {
                                    Text("Desfazer", color = Tokens.accent, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }

            playlistPrompt?.let { prompt -> PlaylistPromptDialog(c, prompt) }

            if (importOpen) ImportDialog(c)

            AnimatedVisibility(
                visible = nowPlayingOpen,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(tween(220)),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(tween(180)),
            ) {
                NowPlayingScreen(c)
            }
        }
    }
}

@Composable
private fun OfflineBanner(offline: Boolean) {
    if (!offline) return
    Row(
        Modifier.fillMaxWidth()
            .background(Color(0xFF2A2A2E))
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(Tokens.accent))
        Spacer(Modifier.width(8.dp))
        Text(
            "Sem conexão — mostrando o que já está salvo neste computador.",
            color = Tokens.textHigh,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun PlaylistPromptDialog(c: AppController, prompt: PlaylistPrompt) {
    var text by remember(prompt) {
        mutableStateOf((prompt as? PlaylistPrompt.Rename)?.current.orEmpty())
    }
    val deleting = prompt is PlaylistPrompt.Delete

    AlertDialog(
        onDismissRequest = { c.dismissPlaylistPrompt() },
        containerColor = Tokens.panelHigh,
        titleContentColor = Tokens.textHigh,
        textContentColor = Tokens.textLow,
        title = {
            Text(
                when (prompt) {
                    PlaylistPrompt.Create -> "Nova playlist"
                    PlaylistPrompt.SaveQueue -> "Salvar fila como playlist"
                    is PlaylistPrompt.Rename -> "Renomear playlist"
                    is PlaylistPrompt.Delete -> "Excluir playlist"
                },
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            if (deleting) {
                Text(
                    "Excluir \"${prompt.name}\"? " +
                        "As músicas continuam no catálogo, só a lista some.",
                    fontSize = 13.sp,
                )
            } else {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    label = { Text("Nome") },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.width(320.dp).trackInputFocus(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { c.submitPlaylistPrompt(text) },
                enabled = deleting || text.isNotBlank(),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(if (deleting) "Excluir" else "Salvar", fontSize = 13.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = { c.dismissPlaylistPrompt() }) {
                Text("Cancelar", fontSize = 13.sp, color = Tokens.textLow)
            }
        },
    )
}

@Composable
private fun ImportDialog(c: AppController) {
    val busy by c.importBusy.collectAsState()
    val status by c.importStatus.collectAsState()
    val account by c.account.state.collectAsState()
    var url by remember { mutableStateOf("") }
    var joinCode by remember { mutableStateOf("") }

    fun pickCsv() {
        val chooser = javax.swing.JFileChooser().apply {
            dialogTitle = "Escolha o CSV exportado do Spotify (Exportify)"
            fileFilter = javax.swing.filechooser.FileNameExtensionFilter("CSV", "csv")
        }
        if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) {
            chooser.selectedFile?.let { c.importCsvFile(it) }
        }
    }

    fun pickBackup() {
        val chooser = javax.swing.JFileChooser().apply {
            dialogTitle = "Escolha o backup JSON"
            fileFilter = javax.swing.filechooser.FileNameExtensionFilter("JSON", "json")
        }
        if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) {
            chooser.selectedFile?.let { c.importBackupFile(it) }
        }
    }

    fun saveBackup() {
        val chooser = javax.swing.JFileChooser().apply {
            dialogTitle = "Salvar backup"
            fileFilter = javax.swing.filechooser.FileNameExtensionFilter("JSON", "json")
            selectedFile = File("openfy-backup.json")
        }
        if (chooser.showSaveDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) {
            chooser.selectedFile?.let { c.exportBackupFile(it) }
        }
    }

    fun pickShared() {
        val chooser = javax.swing.JFileChooser().apply {
            dialogTitle = "Escolha a playlist do Openfy"
            fileFilter = javax.swing.filechooser.FileNameExtensionFilter(
                "Playlist do Openfy (*.openfyplaylist)", "openfyplaylist", "json",
            )
        }
        if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) {
            chooser.selectedFile?.let { c.importPlaylistFile(it) }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) c.closeImport() },
        modifier = Modifier.width(560.dp),
        containerColor = Tokens.panelHigh,
        titleContentColor = Tokens.textHigh,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.UploadFile, null, tint = Tokens.accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Importar, backup e colaboração", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        },
        text = {
            Column {
                Text(
                    "YouTube Music por link",
                    color = Tokens.textHigh,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Playlist ou álbum público. Ex.: music.youtube.com/playlist?list=...",
                    color = Tokens.textLow,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        singleLine = true,
                        placeholder = {
                            Text("Cole o link aqui", color = Tokens.textLow, fontSize = 12.sp)
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).trackInputFocus(),
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { c.importYtmUrl(url) },
                        enabled = !busy && url.isNotBlank(),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text("Importar", fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "Playlist colaborativa",
                    color = Tokens.textHigh,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Cole o código ou o link de convite (…/join.html?code=…) para entrar como editor.",
                    color = Tokens.textLow,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = joinCode,
                        onValueChange = { joinCode = it },
                        singleLine = true,
                        placeholder = {
                            Text("Código ou link do convite", color = Tokens.textLow, fontSize = 12.sp)
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).trackInputFocus(),
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { c.joinSharedPlaylist(joinCode) },
                        enabled = !busy && joinCode.isNotBlank() && account.session != null,
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text("Entrar", fontSize = 12.sp)
                    }
                }
                if (account.session == null) {
                    Text(
                        "Entre na conta em Ajustes → Conta e sincronização para colaborar.",
                        color = Tokens.textLow,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    "Spotify (CSV do Exportify), backup ou playlist do Openfy",
                    color = Tokens.textHigh,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "O CSV é casado com o catálogo do YouTube Music. O backup JSON guarda playlists e curtidas. " +
                        "O arquivo .openfyplaylist é uma playlist pronta para outra pessoa abrir no Openfy.",
                    color = Tokens.textLow,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { pickCsv() }, enabled = !busy) {
                        Text("CSV do Spotify", fontSize = 12.sp)
                    }
                    TextButton(onClick = { pickBackup() }, enabled = !busy) {
                        Text("Importar backup", fontSize = 12.sp)
                    }
                    TextButton(onClick = { saveBackup() }, enabled = !busy) {
                        Text("Exportar backup", fontSize = 12.sp)
                    }
                    TextButton(onClick = { pickShared() }, enabled = !busy) {
                        Text("Playlist Openfy", fontSize = 12.sp)
                    }
                }
                status?.let { text ->
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (busy) {
                            CircularProgressIndicator(
                                Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = Tokens.accent,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(text, color = Tokens.textLow, fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { c.closeImport() }, enabled = !busy) {
                Text("Fechar", color = Tokens.accent, fontSize = 13.sp)
            }
        },
    )
}

private fun pickPlaylistCover(c: AppController, id: String) {
    val chooser = javax.swing.JFileChooser().apply {
        dialogTitle = "Escolha a capa da playlist"
        fileFilter = javax.swing.filechooser.FileNameExtensionFilter(
            "Imagens", "png", "jpg", "jpeg", "webp", "bmp",
        )
    }
    if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile?.let { c.setPlaylistCover(id, it) }
    }
}

/** Salva a playlist num arquivo .openfyplaylist para enviar a outra pessoa. */
private fun sharePlaylist(c: AppController, id: String, name: String) {
    val safe = name.replace(Regex("""[\\/:*?"<>|]"""), "-").trim().ifBlank { "playlist" }
    val chooser = javax.swing.JFileChooser().apply {
        dialogTitle = "Compartilhar playlist"
        fileFilter = javax.swing.filechooser.FileNameExtensionFilter(
            "Playlist do Openfy (*.openfyplaylist)", "openfyplaylist",
        )
        selectedFile = File("$safe.openfyplaylist")
    }
    if (chooser.showSaveDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile?.let { chosen ->
            val target = if (chosen.extension.isBlank()) {
                File(chosen.parentFile, "${chosen.name}.openfyplaylist")
            } else {
                chosen
            }
            c.exportPlaylistFile(id, target)
        }
    }
}

@Composable
private fun Sidebar(c: AppController, screen: Screen, playlists: List<Playlist>) {
    Column(
        Modifier.width(236.dp).fillMaxHeight()
            .clip(RoundedCornerShape(14.dp))
            .background(Tokens.panelGradient)
            .padding(vertical = 18.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 18.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Logo(30.dp)
            Spacer(Modifier.width(9.dp))
            Text(
                "openfy",
                style = TextStyle(
                    brush = Tokens.brandGradient,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
        Spacer(Modifier.height(14.dp))

        NavItem("Início", Icons.Filled.Home, screen is Screen.Home) { c.go(Screen.Home) }
        NavItem("Buscar", Icons.Filled.Search, screen is Screen.Search) { c.go(Screen.Search) }
        NavItem("Explorar", Icons.Filled.Explore, screen is Screen.Explore) { c.go(Screen.Explore) }
        NavItem("Em alta", Icons.AutoMirrored.Filled.TrendingUp, screen is Screen.Charts) { c.go(Screen.Charts) }
        NavItem("Perfil", Icons.Filled.Person, screen is Screen.Profile) { c.openProfile() }

        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "SUA BIBLIOTECA",
                fontSize = 11.sp,
                color = Tokens.textLow,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { c.askCreatePlaylist() }, modifier = Modifier.size(26.dp)) {
                Icon(Icons.Filled.Add, "Nova playlist", tint = Tokens.textLow, modifier = Modifier.size(18.dp))
            }
        }
        NavItem("Curtidas", Icons.Filled.Favorite, screen is Screen.Liked) { c.go(Screen.Liked) }
        NavItem("Downloads", Icons.Filled.Download, screen is Screen.Downloads) { c.go(Screen.Downloads) }
        NavItem("Histórico", Icons.Filled.History, screen is Screen.History) { c.go(Screen.History) }
        NavItem("Mais ouvidas", Icons.Filled.Leaderboard, screen is Screen.TopPlayed) { c.go(Screen.TopPlayed) }
        NavItem("Recap", Icons.Filled.Insights, screen is Screen.Recap) { c.openRecap() }

        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = Tokens.line, modifier = Modifier.padding(horizontal = 14.dp))
        Spacer(Modifier.height(4.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(playlists, key = { it.id }) { playlist ->
                val selected = (screen as? Screen.PlaylistView)?.id == playlist.id
                SidebarRow(
                    title = playlist.name,
                    subtitle = "${playlist.count} faixas",
                    selected = selected,
                ) { c.go(Screen.PlaylistView(playlist.id)) }
            }
            item {
                SidebarRow(
                    title = "Criar playlist",
                    subtitle = "",
                    selected = false,
                    accent = true,
                ) { c.askCreatePlaylist() }
            }
        }
    }
}

@Composable
private fun SidebarRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    accent: Boolean = false,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val background by animateColorAsState(
        targetValue = when {
            selected -> Tokens.panelHigh
            hovered -> Tokens.panelHigh.copy(alpha = 0.55f)
            else -> Color.Transparent
        },
        animationSpec = tween(140),
        label = "row-bg",
    )
    val titleColor by animateColorAsState(
        targetValue = when {
            accent -> Tokens.accent
            selected -> Tokens.textHigh
            else -> Tokens.textLow
        },
        animationSpec = tween(140),
        label = "row-text",
    )
    Column(
        Modifier.fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .hoverable(interaction)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            title,
            color = titleColor,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle.isNotEmpty()) {
            Text(subtitle, color = Tokens.textLow.copy(alpha = 0.6f), fontSize = 11.sp)
        }
    }
}

@Composable
private fun NavItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val background by animateColorAsState(
        targetValue = if (selected) {
            Tokens.panelHigh
        } else {
            Tokens.panelHigh.copy(alpha = if (hovered) 0.55f else 0f)
        },
        animationSpec = tween(140),
        label = "nav-bg",
    )
    val foreground by animateColorAsState(
        targetValue = if (selected) Tokens.accent else Tokens.textLow,
        animationSpec = tween(140),
        label = "nav-fg",
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) Tokens.textHigh else Tokens.textLow,
        animationSpec = tween(140),
        label = "nav-text",
    )
    Box(
        Modifier.fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .hoverable(interaction)
            .clickable(onClick = onClick),
    ) {
        if (selected) {
            Box(
                Modifier.align(Alignment.CenterStart)
                    .padding(start = 4.dp)
                    .width(3.dp)
                    .height(18.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Tokens.accent),
            )
        }
        Row(
            Modifier.fillMaxWidth()
                .padding(start = if (selected) 14.dp else 12.dp, end = 12.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                null,
                tint = foreground,
                modifier = Modifier.size(17.dp),
            )
            Spacer(Modifier.width(11.dp))
            Text(
                label,
                color = textColor,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun TopBar(c: AppController, queueOpen: Boolean, onExit: () -> Unit, onQueue: () -> Unit) {
    val query by c.searchQuery.collectAsState()
    val committed by c.committedSearch.collectAsState()
    val suggestions by c.suggestions.collectAsState()
    val focused by c.searchFieldFocused.collectAsState()
    val canBack by c.canBack.collectAsState()
    val canForward by c.canForward.collectAsState()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(query) {
        val q = query.trim()
        if (q.isBlank()) {
            c.clearSearch()
            return@LaunchedEffect
        }
        if (q == committed) return@LaunchedEffect
        delay(130)
        c.fetchSuggestions(q)
        delay(220)
        if (c.searchQuery.value.trim() != q) return@LaunchedEffect
        c.go(Screen.Search)
        c.search(q, record = false)
    }

    LaunchedEffect(c.searchFocusRequest.value) {
        if (c.searchFocusRequest.value > 0) focusRequester.requestFocus()
    }

    val showSuggestions = focused && query.isNotBlank() && query.trim() != committed && suggestions.isNotEmpty()

    Row(
        Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tip("Voltar") {
            IconButton(onClick = { c.back() }, enabled = canBack, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    "Voltar",
                    tint = if (canBack) Tokens.textHigh else Tokens.textLow.copy(alpha = 0.35f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Tip("Avançar") {
            IconButton(onClick = { c.forward() }, enabled = canForward, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    "Avançar",
                    tint = if (canForward) Tokens.textHigh else Tokens.textLow.copy(alpha = 0.35f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        Box {
            OutlinedTextField(
                value = query,
                onValueChange = { c.searchQuery.value = it },
                singleLine = true,
                placeholder = { Text("Buscar música, artista...", color = Tokens.textLow, fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Filled.Search, null, tint = Tokens.textLow, modifier = Modifier.size(18.dp)) },
                shape = RoundedCornerShape(10.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    val q = query.trim()
                    if (q.isNotEmpty()) {
                        c.go(Screen.Search)
                        c.search(q)
                    }
                }),
                modifier = Modifier.width(360.dp)
                    .focusRequester(focusRequester)
                    .trackInputFocus()
                    .onFocusChanged { c.searchFieldFocused.value = it.isFocused },
            )
            DropdownMenu(
                expanded = showSuggestions,
                onDismissRequest = { c.suggestions.value = emptyList() },
                modifier = Modifier.width(360.dp).background(Tokens.panelHigh),
            ) {
                suggestions.forEach { term ->
                    DropdownMenuItem(
                        text = {
                            Text(term, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        onClick = {
                            c.searchQuery.value = term
                            c.go(Screen.Search)
                            c.search(term)
                        },
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = { c.askCreatePlaylist() }) {
            Icon(Icons.Filled.Add, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Playlist", fontSize = 13.sp)
        }
        Tip("Importar playlist / backup") {
            IconButton(onClick = { c.openImport() }) {
                Icon(Icons.Filled.UploadFile, "Importar", tint = Tokens.textLow, modifier = Modifier.size(18.dp))
            }
        }
        ToggleIconButton(
            Icons.AutoMirrored.Filled.QueueMusic,
            "Fila",
            active = queueOpen,
            onClick = onQueue,
            size = 40.dp,
            iconSize = 18.dp,
        )
        Tip("Ajustes") {
            IconButton(onClick = { c.go(Screen.Settings) }, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Filled.Settings,
                    "Ajustes",
                    tint = Tokens.textLow,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Tip("Sair do Openfy") {
            IconButton(onClick = onExit, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Filled.PowerSettingsNew,
                    "Sair do Openfy",
                    tint = Tokens.textLow,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

// ------------------------------------------------------------------ content
@Composable
private fun Content(c: AppController, target: Screen) {
    when (target) {
        Screen.Home -> HomeScreen(c)
        Screen.Search -> SearchScreen(c)
        Screen.Explore -> ExploreScreen(c)
        Screen.Charts -> ChartsScreen(c)
        Screen.Liked -> {
            val tracks by c.liked.collectAsState()
            TrackCollection(c, "Curtidas", tracks, emptyList())
        }
        Screen.Downloads -> DownloadsScreen(c)
        Screen.Profile -> ProfileScreen(c)
        Screen.Settings -> SettingsScreen(c)
        Screen.History -> {
            val tracks by c.history.collectAsState()
            TrackCollection(c, "Histórico", tracks, emptyList())
        }
        Screen.TopPlayed -> TopPlayedScreen(c)
        Screen.Recap -> RecapScreen(c)
        is Screen.PlaylistView -> PlaylistScreen(c, target.id)
        is Screen.ArtistView -> ArtistScreen(c, target.id)
        is Screen.RemoteCollection -> RemoteCollectionScreen(c, target.id)
    }
}

@Composable
private fun HomeScreen(c: AppController) {
    val recent by c.recent.collectAsState()
    val liked by c.liked.collectAsState()
    val playlists by c.playlists.collectAsState()
    val topPlayed by c.topPlayed.collectAsState()
    val account by c.account.state.collectAsState()
    val remotePlayback = account.remotePlayback

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        item {
            Text(
                "Início",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Tokens.textHigh,
                modifier = Modifier.padding(top = 8.dp, bottom = 18.dp),
            )
        }
        if (remotePlayback != null && remotePlayback.queue.isNotEmpty()) {
            val remoteTrack = remotePlayback.queue.getOrNull(
                remotePlayback.queueIndex.coerceIn(0, remotePlayback.queue.lastIndex)
            )
            item {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 22.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Tokens.panel)
                        .clickable { c.continueOnOtherDevice() }
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.QueueMusic,
                        null,
                        tint = Tokens.accent,
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "CONTINUAR NO OUTRO APARELHO",
                            color = Tokens.accent,
                            fontSize = 11.sp,
                            letterSpacing = 1.5.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            remoteTrack?.title ?: "Fila do celular",
                            color = Tokens.textHigh,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                        Text(
                            "${remoteTrack?.artist.orEmpty()} · " +
                                if (remotePlayback.playing) "tocando agora" else "pausado",
                            color = Tokens.textLow,
                            fontSize = 12.sp,
                            maxLines = 1,
                        )
                    }
                    IconButton(onClick = { c.dismissRemotePlayback() }) {
                        Icon(Icons.Filled.Close, "Dispensar", tint = Tokens.textLow)
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        null,
                        tint = Tokens.textLow,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        if (recent.isNotEmpty() || liked.isNotEmpty()) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 22.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Tokens.heroGradient)
                        .clickable { c.startPersonalMix() }
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "SEU MIX",
                            color = Tokens.accent,
                            fontSize = 11.sp,
                            letterSpacing = 1.5.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Um mix a partir do que você curte e ouve",
                            color = Tokens.textHigh,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Recomendações locais usando suas curtidas e histórico",
                            color = Tokens.textLow,
                            fontSize = 12.sp,
                        )
                    }
                    Box(
                        Modifier.size(52.dp).clip(CircleShape).background(Tokens.brandGradient),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.PlayArrow, "Tocar mix", tint = Tokens.ink, modifier = Modifier.size(30.dp))
                    }
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
                Text(
                    "VIBES & CLIMA",
                    color = Tokens.accent,
                    fontSize = 11.sp,
                    letterSpacing = 1.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val vibes = listOf("⚡ Treino", "🎯 Foco", "🌙 Relax", "🎉 Festa", "🚗 Viagem")
                    vibes.forEach { vibe ->
                        val cleanName = vibe.substringAfter(" ")
                        Button(
                            onClick = { c.startVibeMix(cleanName) },
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Tokens.panel,
                                contentColor = Tokens.textHigh,
                            ),
                        ) {
                            Text(vibe, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 22.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Tokens.panel)
                    .clickable { c.openRecap() }
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Insights, null, tint = Tokens.accent, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "SEU RECAP",
                        color = Tokens.accent,
                        fontSize = 11.sp,
                        letterSpacing = 1.5.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Suas estatísticas mês a mês",
                        color = Tokens.textHigh,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Top faixas, artistas, sequência e descobertas em uma aba dedicada.",
                        color = Tokens.textLow,
                        fontSize = 12.sp,
                    )
                }
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Tokens.textLow, modifier = Modifier.size(20.dp))
            }
        }
        if (recent.isEmpty() && liked.isEmpty() && playlists.isEmpty()) {
            item {
                Column(
                    Modifier.fillMaxWidth()
                        .padding(top = 8.dp, bottom = 28.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Tokens.panel)
                        .padding(24.dp),
                ) {
                    Text(
                        "Sua biblioteca está vazia",
                        color = Tokens.textHigh,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Busque uma música no catálogo do YouTube Music para começar. " +
                            "Curtidas, histórico e playlists ficam neste computador.",
                        color = Tokens.textLow,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = { c.go(Screen.Search) },
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Icon(Icons.Filled.Search, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Buscar música", fontSize = 13.sp)
                    }
                }
            }
        }
        if (recent.isNotEmpty()) {
            item { SectionTitle("Ouvir de novo") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(recent, key = { "r-${it.id}" }) { track ->
                        CoverCard(
                            track.title,
                            track.artist,
                            track.thumbnail,
                            CoverCardMenu(
                                onPlayNext = { c.playNext(track) },
                                onQueue = { c.playLater(track) },
                                onRadio = { c.startTrackRadio(track) },
                            ),
                        ) {
                            // Recentes são sementes: tocar uma faixa abre a rádio dela,
                            // não enfileira o histórico inteiro.
                            c.playSearchTrack(track)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
        if (topPlayed.isNotEmpty()) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionTitle("Mais ouvidas")
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { c.go(Screen.TopPlayed) }) {
                        Text("Ver tudo", color = Tokens.textLow, fontSize = 12.sp)
                    }
                }
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(topPlayed.take(10), key = { "tp-${it.id}" }) { track ->
                        CoverCard(
                            track.title,
                            track.artist,
                            track.thumbnail,
                            CoverCardMenu(
                                onPlayNext = { c.playNext(track) },
                                onQueue = { c.playLater(track) },
                                onRadio = { c.startTrackRadio(track) },
                            ),
                        ) {
                            c.playTracks(topPlayed, topPlayed.indexOf(track), "Mais ouvidas")
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
        if (liked.isNotEmpty()) {
            item { SectionTitle("Curtidas") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(liked, key = { "l-${it.id}" }) { track ->
                        val index = liked.indexOf(track)
                        CoverCard(
                            track.title,
                            track.artist,
                            track.thumbnail,
                            CoverCardMenu(
                                onPlayNext = { c.playNext(track) },
                                onQueue = { c.playLater(track) },
                                onRadio = { c.startTrackRadio(track) },
                            ),
                        ) {
                            c.playTracks(liked, index)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
        item { SectionTitle("Suas playlists") }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(playlists, key = { "p-${it.id}" }) { playlist ->
                    PlaylistCard(playlist) { c.go(Screen.PlaylistView(playlist.id)) }
                }
                item {
                    Box(
                        Modifier.size(156.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Tokens.panel)
                            .clickable { c.askCreatePlaylist() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.Add, null, tint = Tokens.textLow)
                            Spacer(Modifier.height(6.dp))
                            Text("Nova playlist", color = Tokens.textLow, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(28.dp)) }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        color = Tokens.textHigh,
        modifier = Modifier.padding(bottom = 12.dp),
    )
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp))
            .background(Tokens.panel)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            label.uppercase(),
            color = Tokens.textLow,
            fontSize = 10.sp,
            letterSpacing = 1.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            value,
            color = Tokens.textHigh,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun formatHours(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}min" else "${minutes}min"
}

/** Alça de arrastar reutilizável (fila, playlist, blocos do perfil). */
@Composable
private fun ReorderHandle(
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 16.dp,
) {
    val start by rememberUpdatedState(onDragStart)
    val drag by rememberUpdatedState(onDrag)
    val end by rememberUpdatedState(onDragEnd)
    val cancel by rememberUpdatedState(onDragCancel)
    Box(
        modifier.size(26.dp).clip(CircleShape).pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { start() },
                onDrag = { change, amount ->
                    change.consume()
                    drag(amount.y)
                },
                onDragEnd = { end() },
                onDragCancel = { cancel() },
            )
        },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.DragHandle,
            "Arrastar para reordenar",
            tint = Tokens.textLow,
            modifier = Modifier.size(iconSize),
        )
    }
}

/** Ações rápidas dos cards da Home (sem abrir a faixa). */
private data class CoverCardMenu(
    val onPlayNext: () -> Unit,
    val onQueue: () -> Unit,
    val onRadio: () -> Unit,
)

@Composable
private fun CoverCard(
    title: String,
    subtitle: String,
    art: String,
    menu: CoverCardMenu? = null,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Column(
        Modifier.width(156.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (hovered) Tokens.panelHigh else Color.Transparent)
            .hoverable(interaction)
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        Box {
            Cover(art, 140.dp, 8.dp)
            if (menu != null) {
                var open by remember { mutableStateOf(false) }
                Box(Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                    IconButton(
                        onClick = { open = true },
                        modifier = Modifier.size(26.dp).clip(CircleShape)
                            .background(Tokens.ink.copy(alpha = if (hovered) 0.8f else 0.45f)),
                    ) {
                        Icon(
                            Icons.Filled.MoreVert,
                            "Mais ações",
                            tint = Tokens.textHigh,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                        DropdownMenuItem(
                            text = { Text("Tocar em seguida") },
                            onClick = { open = false; menu.onPlayNext() },
                        )
                        DropdownMenuItem(
                            text = { Text("Adicionar ao fim da fila") },
                            onClick = { open = false; menu.onQueue() },
                        )
                        DropdownMenuItem(
                            text = { Text("Rádio desta música") },
                            leadingIcon = { Icon(Icons.Filled.Radio, null) },
                            onClick = { open = false; menu.onRadio() },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            title,
            color = Tokens.textHigh,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            subtitle,
            color = Tokens.textLow,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PlaylistCard(playlist: Playlist, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Column(
        Modifier.width(156.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (hovered) Tokens.panelHigh else Color.Transparent)
            .hoverable(interaction)
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        CoverSource(playlist.cover, 140.dp, 8.dp)
        Spacer(Modifier.height(8.dp))
        Text(
            playlist.name,
            color = Tokens.textHigh,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text("${playlist.count} faixas", color = Tokens.textLow, fontSize = 11.sp)
    }
}

@Composable
private fun SearchScreen(c: AppController) {
    val results by c.results.collectAsState()
    val artists by c.searchArtists.collectAsState()
    val cards by c.searchCards.collectAsState()
    val mode by c.searchMode.collectAsState()
    val searching by c.searching.collectAsState()
    val error by c.searchError.collectAsState()
    val query by c.searchQuery.collectAsState()
    val likedIds by c.player.likedIds.collectAsState()
    val playlists by c.playlists.collectAsState()
    val recentSearches by c.recentSearches.collectAsState()
    val hasMore by c.searchHasMore.collectAsState()
    val loadingMore by c.searchLoadingMore.collectAsState()

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (query.isBlank()) "Buscar" else "Resultados para \"$query\"",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Tokens.textHigh,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (results.isNotEmpty()) {
                TextButton(
                    onClick = { c.playTracks(results, 0, "Busca: $query") },
                ) {
                    Icon(Icons.Filled.PlayArrow, null, tint = Tokens.textHigh, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Tocar resultados (contexto)", color = Tokens.textHigh, fontSize = 12.sp)
                }
                Spacer(Modifier.width(4.dp))
            }
            if (searching) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Tokens.accent)
                Spacer(Modifier.width(8.dp))
                Text("Procurando...", color = Tokens.textLow, fontSize = 12.sp)
            }
        }

        Row(
            Modifier.fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SearchMode.entries.forEach { item ->
                SearchChip(item.label, item == mode) { c.setSearchMode(item) }
            }
        }

        if (query.isBlank()) {
            if (recentSearches.isNotEmpty()) {
                Text(
                    "Buscas recentes",
                    color = Tokens.textHigh,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
                )
                recentSearches.take(8).forEach { term ->
                    RecentSearchRow(term) {
                        c.searchQuery.value = term
                        c.go(Screen.Search)
                    }
                }
            } else {
                Text(
                    "Busque por música, artista, álbum ou playlist na barra superior.",
                    color = Tokens.textLow,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 18.dp),
                )
            }
            return@Column
        }

        if (searching && results.isEmpty() && artists.isEmpty() && cards.isEmpty()) {
            SkeletonList()
            return@Column
        }

        error?.let {
            if (results.isEmpty() && artists.isEmpty() && cards.isEmpty()) {
                ErrorRetry(it) { c.retrySearch() }
            } else {
                Text(it, color = Tokens.textLow, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
            }
        }

        when (mode) {
            SearchMode.SONGS -> {
                LazyColumn(Modifier.weight(1f)) {
                    if (artists.isNotEmpty()) {
                        item {
                            Text(
                                "Artistas",
                                color = Tokens.textHigh,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
                            )
                        }
                        item {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(artists, key = { "a-${it.id}" }) { artist ->
                                    ArtistTile(artist) { c.go(Screen.ArtistView(artist.id)) }
                                }
                            }
                        }
                        item { Spacer(Modifier.height(16.dp)) }
                    }
                    if (results.isNotEmpty()) {
                        item {
                            TopResultCard(
                                results.first(),
                                CoverCardMenu(
                                    onPlayNext = { c.playNext(results.first()) },
                                    onQueue = { c.playLater(results.first()) },
                                    onRadio = { c.startTrackRadio(results.first()) },
                                ),
                            ) { c.playSearchTrack(results.first()) }
                        }
                    }
                    itemsIndexed(results.drop(1), key = { _, t -> t.id }) { index, track ->
                        TrackRow(
                            c = c,
                            track = track,
                            index = index + 1,
                            liked = track.id in likedIds,
                            onPlay = { c.playSearchTrack(track) },
                            onLike = { c.toggleLike(track) },
                            onPlayNext = { c.playNext(track) },
                            onQueue = { c.playLater(track) },
                            onRadio = { c.startTrackRadio(track) },
                            playlists = playlists,
                            onAddTo = { playlistId -> c.addToPlaylist(playlistId, track) },
                        )
                    }
                    if (hasMore) {
                        item { LoadMoreButton(loadingMore) { c.loadMoreSearch() } }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }

            SearchMode.ARTISTS -> {
                LazyColumn(Modifier.weight(1f)) {
                    items(artists, key = { it.id }) { artist ->
                        ArtistRow(artist) { c.go(Screen.ArtistView(artist.id)) }
                    }
                    if (hasMore) {
                        item { LoadMoreButton(loadingMore) { c.loadMoreSearch() } }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }

            SearchMode.ALBUMS, SearchMode.PLAYLISTS -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(170.dp),
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    gridItems(cards, key = { "${it.kind}:${it.id}" }) { card ->
                        MediaCardTile(card, Modifier.fillMaxWidth()) { c.openMedia(card) }
                    }
                }
                if (hasMore) {
                    LoadMoreButton(loadingMore) { c.loadMoreSearch() }
                }
            }

            SearchMode.VIDEOS -> {
                LazyColumn(Modifier.weight(1f)) {
                    itemsIndexed(results, key = { _, t -> t.id }) { index, track ->
                        TrackRow(
                            c = c,
                            track = track,
                            index = index,
                            liked = track.id in likedIds,
                            onPlay = { c.playSearchTrack(track) },
                            onLike = { c.toggleLike(track) },
                            onPlayNext = { c.playNext(track) },
                            onQueue = { c.playLater(track) },
                            onRadio = { c.startTrackRadio(track) },
                            playlists = playlists,
                            onAddTo = { playlistId -> c.addToPlaylist(playlistId, track) },
                        )
                    }
                    if (hasMore) {
                        item { LoadMoreButton(loadingMore) { c.loadMoreSearch() } }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun RecentSearchRow(term: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (hovered) Tokens.panelHigh else Color.Transparent)
            .hoverable(interaction)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.History, null, tint = Tokens.textLow, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(10.dp))
        Text(term, color = Tokens.textHigh, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TopResultCard(track: Track, menu: CoverCardMenu? = null, onPlay: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .padding(bottom = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Tokens.panel)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(track.thumbnail, 96.dp, 8.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Top result",
                color = Tokens.accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                track.title,
                color = Tokens.textHigh,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                track.artist,
                color = Tokens.textLow,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onPlay, modifier = Modifier.size(44.dp)) {
            Icon(Icons.Filled.PlayArrow, "Tocar", tint = Tokens.textHigh, modifier = Modifier.size(26.dp))
        }
        if (menu != null) {
            var open by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { open = true }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.MoreVert, "Mais ações", tint = Tokens.textLow, modifier = Modifier.size(20.dp))
                }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    DropdownMenuItem(
                        text = { Text("Tocar em seguida") },
                        onClick = { open = false; menu.onPlayNext() },
                    )
                    DropdownMenuItem(
                        text = { Text("Adicionar ao fim da fila") },
                        onClick = { open = false; menu.onQueue() },
                    )
                    DropdownMenuItem(
                        text = { Text("Rádio desta música") },
                        leadingIcon = { Icon(Icons.Filled.Radio, null) },
                        onClick = { open = false; menu.onRadio() },
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50))
            .background(if (selected) Tokens.accent else Tokens.panelHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            Icon(
                Icons.Filled.Check,
                null,
                tint = Tokens.ink,
                modifier = Modifier.size(13.dp),
            )
            Spacer(Modifier.width(5.dp))
        }
        Text(
            label,
            color = if (selected) Tokens.ink else Tokens.textLow,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun ArtistTile(artist: Artist, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Column(
        Modifier.width(128.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (hovered) Tokens.panelHigh else Color.Transparent)
            .hoverable(interaction)
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Cover(artist.thumbnail, 96.dp, 48.dp)
        Spacer(Modifier.height(8.dp))
        Text(
            artist.name,
            color = Tokens.textHigh,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (artist.subtitle.isNotBlank()) {
            Text(
                artist.subtitle,
                color = Tokens.textLow,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ArtistRow(artist: Artist, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (hovered) Tokens.panelHigh.copy(alpha = 0.6f) else Color.Transparent)
            .hoverable(interaction)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(artist.thumbnail, 54.dp, 27.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                artist.name,
                color = Tokens.textHigh,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (artist.subtitle.isNotBlank()) {
                Text(
                    artist.subtitle,
                    color = Tokens.textLow,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun MediaCardTile(card: MediaCard, modifier: Modifier = Modifier.width(156.dp), onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (hovered) Tokens.panelHigh else Color.Transparent)
            .hoverable(interaction)
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        Cover(
            card.thumbnail,
            140.dp,
            if (card.kind == MediaKind.ARTIST) 70.dp else 8.dp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            card.title,
            color = Tokens.textHigh,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (card.subtitle.isNotBlank()) {
            Text(
                card.subtitle,
                color = Tokens.textLow,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SkeletonRow() {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(6.dp)).background(Tokens.panelHigh))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Box(Modifier.width(180.dp).height(12.dp).clip(RoundedCornerShape(4.dp)).background(Tokens.panelHigh))
            Spacer(Modifier.height(6.dp))
            Box(Modifier.width(120.dp).height(10.dp).clip(RoundedCornerShape(4.dp)).background(Tokens.panelHigh))
        }
    }
}

@Composable
private fun SkeletonList(count: Int = 8) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        repeat(count) { SkeletonRow() }
    }
}

@Composable
private fun ErrorRetry(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, color = Tokens.textLow, fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = onRetry) {
            Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(16.dp), tint = Tokens.accent)
            Spacer(Modifier.width(6.dp))
            Text("Tentar novamente", color = Tokens.accent, fontSize = 13.sp)
        }
    }
}

@Composable
private fun LoadMoreButton(loadingMore: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        enabled = !loadingMore,
        modifier = Modifier.padding(vertical = 10.dp),
    ) {
        if (loadingMore) {
            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Tokens.accent)
            Spacer(Modifier.width(8.dp))
            Text("Carregando...", color = Tokens.textLow, fontSize = 13.sp)
        } else {
            Text("Carregar mais", color = Tokens.textLow, fontSize = 13.sp)
        }
    }
}

@Composable
private fun ArtistScreen(c: AppController, id: String) {
    val page by c.artistPage.collectAsState()
    val loading by c.artistLoading.collectAsState()
    val error by c.artistError.collectAsState()
    val likedIds by c.player.likedIds.collectAsState()
    val playlists by c.playlists.collectAsState()

    val loaded = page
    if (loaded == null) {
        if (loading) SkeletonList() else ErrorRetry(error ?: "Artista não encontrado.") { c.loadArtist(id) }
        return
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        item { ArtistHeader(c, loaded) }
        if (loaded.topTracks.isNotEmpty()) {
            item { SectionTitle("Populares") }
        }
        itemsIndexed(loaded.topTracks, key = { _, t -> t.id }) { index, track ->
            TrackRow(
                c = c,
                track = track,
                index = index,
                liked = track.id in likedIds,
                onPlay = { c.playTracks(loaded.topTracks, index) },
                onLike = { c.toggleLike(track) },
                onPlayNext = { c.playNext(track) },
                onQueue = { c.playLater(track) },
                onRadio = { c.startTrackRadio(track) },
                playlists = playlists,
                onAddTo = { playlistId -> c.addToPlaylist(playlistId, track) },
            )
        }
        loaded.sections.forEachIndexed { index, section ->
            item(key = "artist-title-$index") { SectionTitle(section.title) }
            item(key = "artist-cards-$index") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(section.cards, key = { "${it.kind}:${it.id}" }) { card ->
                        MediaCardTile(card) { c.openMedia(card) }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun ArtistHeader(c: AppController, page: ArtistPage) {
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (page.avatar.isNotBlank()) {
            Cover(page.avatar, 150.dp, 75.dp)
        } else {
            // Sem avatar quadrado na resposta, o banner largo fica melhor
            // como retangulo do que cortado em circulo.
            Cover(page.banner, 150.dp, 10.dp, width = 250.dp)
        }
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text("ARTISTA", color = Tokens.textLow, fontSize = 11.sp, letterSpacing = 1.sp)
            Text(
                page.name,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Tokens.textHigh,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (page.listeners.isNotBlank()) {
                Text(page.listeners, color = Tokens.textLow, fontSize = 12.sp)
            }
            if (page.description.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    page.description,
                    color = Tokens.textLow,
                    fontSize = 12.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { c.playTracks(page.topTracks, 0) },
                    enabled = page.topTracks.isNotEmpty(),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Tocar")
                }
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = { c.playShuffled(page.topTracks) },
                    enabled = page.topTracks.isNotEmpty(),
                ) {
                    Icon(Icons.Filled.Shuffle, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Aleatório")
                }
                TextButton(
                    onClick = { c.startArtistRadio() },
                    enabled = page.radioVideoId != null,
                ) {
                    Icon(Icons.Filled.Radio, null, modifier = Modifier.size(16.dp), tint = Tokens.accent)
                    Spacer(Modifier.width(6.dp))
                    Text("Rádio")
                }
            }
        }
    }
}

@Composable
private fun ExploreScreen(c: AppController) {
    val feed by c.exploreFeed.collectAsState()
    val loading by c.exploreLoading.collectAsState()
    val error by c.exploreError.collectAsState()

    val loaded = feed
    if (loaded == null) {
        if (loading) SkeletonList(10) else ErrorRetry(error ?: "Nada para explorar agora.") { c.loadExplore(force = true) }
        return
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Explorar", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Tokens.textHigh)
                    Text(
                        "Playlists públicas, paradas e lançamentos",
                        color = Tokens.textLow,
                        fontSize = 12.sp,
                    )
                }
                if (loading) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Tokens.accent)
                    Spacer(Modifier.width(10.dp))
                }
                TextButton(onClick = { c.loadExplore(force = true) }) {
                    Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(16.dp), tint = Tokens.textLow)
                    Spacer(Modifier.width(6.dp))
                    Text("Atualizar", color = Tokens.textLow)
                }
            }
        }
        error?.let {
            item {
                Text(it, color = Tokens.textLow, fontSize = 12.sp, modifier = Modifier.padding(bottom = 12.dp))
            }
        }
        if (loaded.topArtists.isNotEmpty()) {
            item { SectionTitle("Top artistas") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(loaded.topArtists, key = { "top-${it.id}" }) { artist ->
                        ArtistTile(artist) { c.go(Screen.ArtistView(artist.id)) }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
        loaded.sections.forEachIndexed { index, section ->
            item(key = "explore-title-$index") { SectionTitle(section.title) }
            item(key = "explore-cards-$index") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(section.cards, key = { "${it.kind}:${it.id}" }) { card ->
                        MediaCardTile(card) { c.openMedia(card) }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun ChartsScreen(c: AppController) {
    val tracks by c.chartsTracks.collectAsState()
    val loading by c.chartsLoading.collectAsState()
    val error by c.chartsError.collectAsState()
    val likedIds by c.player.likedIds.collectAsState()
    val playlists by c.playlists.collectAsState()

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Em alta", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Tokens.textHigh)
                Text(
                    "As músicas mais tocadas agora",
                    color = Tokens.textLow,
                    fontSize = 12.sp,
                )
            }
            if (loading) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Tokens.accent)
                Spacer(Modifier.width(10.dp))
            }
            TextButton(onClick = { c.loadCharts(force = true) }) {
                Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(16.dp), tint = Tokens.textLow)
                Spacer(Modifier.width(6.dp))
                Text("Atualizar", color = Tokens.textLow)
            }
        }

        if (tracks.isEmpty()) {
            if (loading) SkeletonList(12) else ErrorRetry(error ?: "Nada por aqui.") { c.loadCharts(force = true) }
            return@Column
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
            Button(onClick = { c.playTracks(tracks, 0) }, shape = RoundedCornerShape(8.dp)) {
                Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Tocar")
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { c.playShuffled(tracks) }) {
                Icon(Icons.Filled.Shuffle, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Aleatório")
            }
        }
        HorizontalDivider(color = Tokens.line)
        LazyColumn(Modifier.weight(1f)) {
            itemsIndexed(tracks, key = { _, t -> t.id }) { index, track ->
                TrackRow(
                    c = c,
                    track = track,
                    index = index,
                    liked = track.id in likedIds,
                    onPlay = { c.playTracks(tracks, index) },
                    onLike = { c.toggleLike(track) },
                    onPlayNext = { c.playNext(track) },
                    onQueue = { c.playLater(track) },
                    onRadio = { c.startTrackRadio(track) },
                    playlists = playlists,
                    onAddTo = { playlistId -> c.addToPlaylist(playlistId, track) },
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun RemoteCollectionScreen(c: AppController, id: String) {
    val page by c.remoteCollection.collectAsState()
    val loading by c.remoteLoading.collectAsState()
    val loadingMore by c.remoteLoadingMore.collectAsState()
    val error by c.remoteError.collectAsState()
    val likedIds by c.player.likedIds.collectAsState()
    val playlists by c.playlists.collectAsState()
    val downloadedIds by c.downloadedIds.collectAsState()
    val downloadingIds by c.downloadingIds.collectAsState()

    val loaded = page
    if (loaded == null) {
        if (loading) SkeletonList() else ErrorRetry(error ?: "Coleção não encontrada.") { c.loadRemoteCollection(id) }
        return
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Row(
            Modifier.padding(top = 10.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Cover(loaded.thumbnail, 148.dp, 10.dp)
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (loaded.kind == CollectionKind.ALBUM) "ÁLBUM" else "PLAYLIST",
                    color = Tokens.textLow,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp,
                )
                Text(
                    loaded.title,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = Tokens.textHigh,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (loaded.subtitle.isNotBlank()) {
                    Text(loaded.subtitle, color = Tokens.textLow, fontSize = 12.sp)
                }
                if (loaded.description.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        loaded.description,
                        color = Tokens.textLow,
                        fontSize = 12.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = { c.playTracks(loaded.tracks, 0) },
                        enabled = loaded.tracks.isNotEmpty(),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Tocar")
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = { c.playShuffled(loaded.tracks) },
                        enabled = loaded.tracks.isNotEmpty(),
                    ) {
                        Icon(Icons.Filled.Shuffle, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Aleatório")
                    }
                    val allDownloaded = loaded.tracks.isNotEmpty() && loaded.tracks.all { it.id in downloadedIds }
                    val anyDownloading = loaded.tracks.any { it.id in downloadingIds }
                    TextButton(
                        onClick = { c.downloadTracks(loaded.tracks) },
                        enabled = loaded.tracks.isNotEmpty() && !allDownloaded,
                    ) {
                        when {
                            anyDownloading -> CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Tokens.accent)
                            allDownloaded -> Icon(Icons.Filled.DownloadDone, null, modifier = Modifier.size(16.dp), tint = Tokens.textLow)
                            else -> Icon(Icons.Filled.Download, null, modifier = Modifier.size(16.dp), tint = Tokens.textLow)
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(if (allDownloaded) "Baixado" else "Baixar", color = Tokens.textLow, fontSize = 13.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    Text("${loaded.tracks.size} faixas", color = Tokens.textLow, fontSize = 12.sp)
                }
            }
        }
        HorizontalDivider(color = Tokens.line)
        LazyColumn(Modifier.weight(1f)) {
            itemsIndexed(loaded.tracks, key = { _, t -> t.id }) { index, track ->
                TrackRow(
                    c = c,
                    track = track,
                    index = index,
                    liked = track.id in likedIds,
                    onPlay = { c.playTracks(loaded.tracks, index) },
                    onLike = { c.toggleLike(track) },
                    onPlayNext = { c.playNext(track) },
                    onQueue = { c.playLater(track) },
                    onRadio = { c.startTrackRadio(track) },
                    playlists = playlists,
                    onAddTo = { playlistId -> c.addToPlaylist(playlistId, track) },
                )
            }
            if (loaded.continuation != null) {
                item {
                    TextButton(
                        onClick = { c.loadMoreRemote() },
                        enabled = !loadingMore,
                        modifier = Modifier.padding(vertical = 10.dp),
                    ) {
                        if (loadingMore) {
                            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Tokens.accent)
                            Spacer(Modifier.width(8.dp))
                            Text("Carregando...", color = Tokens.textLow)
                        } else {
                            Text("Carregar mais faixas", color = Tokens.textLow)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}


@Composable
private fun PlaylistScreen(c: AppController, id: String) {
    val meta by c.openPlaylist.collectAsState()
    val tracks by c.openPlaylistTracks.collectAsState()
    val likedIds by c.player.likedIds.collectAsState()
    val playlists by c.playlists.collectAsState()
    val downloadedIds by c.downloadedIds.collectAsState()
    val downloadingIds by c.downloadingIds.collectAsState()

    var ordering by remember(id) { mutableStateOf(false) }
    var dragIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    val density = LocalDensity.current
    var rowHeightPx by remember { mutableStateOf(with(density) { 56.dp.toPx() }) }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Row(Modifier.padding(top = 10.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            CoverSource(meta?.cover ?: "", 132.dp, 10.dp)
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text("PLAYLIST", color = Tokens.textLow, fontSize = 11.sp, letterSpacing = 1.sp)
                Text(
                    meta?.name ?: "Playlist",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = Tokens.textHigh,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text("${tracks.size} faixas", color = Tokens.textLow, fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = { c.playTracks(tracks, 0) },
                        enabled = tracks.isNotEmpty(),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Tocar")
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { c.playShuffled(tracks) }, enabled = tracks.isNotEmpty()) {
                        Icon(Icons.Filled.Shuffle, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Aleatório")
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = { c.startPlaylistRadio(tracks, meta?.name ?: "Playlist") },
                        enabled = tracks.isNotEmpty(),
                    ) {
                        Icon(Icons.Filled.Radio, null, modifier = Modifier.size(16.dp), tint = Tokens.accent)
                        Spacer(Modifier.width(6.dp))
                        Text("Rádio", color = Tokens.accent)
                    }
                    val allDownloaded = tracks.isNotEmpty() && tracks.all { it.id in downloadedIds }
                    val anyDownloading = tracks.any { it.id in downloadingIds }
                    TextButton(
                        onClick = { c.downloadTracks(tracks) },
                        enabled = tracks.isNotEmpty() && !allDownloaded,
                    ) {
                        when {
                            anyDownloading -> CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Tokens.accent)
                            allDownloaded -> Icon(Icons.Filled.DownloadDone, null, modifier = Modifier.size(16.dp), tint = Tokens.textLow)
                            else -> Icon(Icons.Filled.Download, null, modifier = Modifier.size(16.dp), tint = Tokens.textLow)
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(if (allDownloaded) "Baixado" else "Baixar", color = Tokens.textLow, fontSize = 13.sp)
                    }
                }
                Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    val shareMenu = remember { mutableStateOf(false) }
                    Box {
                        TextButton(
                            onClick = { shareMenu.value = true },
                            enabled = meta != null && tracks.isNotEmpty(),
                        ) {
                            Icon(
                                Icons.Filled.Share,
                                null,
                                modifier = Modifier.size(16.dp),
                                tint = Tokens.textLow,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Compartilhar", color = Tokens.textLow)
                        }
                        DropdownMenu(
                            expanded = shareMenu.value,
                            onDismissRequest = { shareMenu.value = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Copiar convite colaborativo") },
                                onClick = {
                                    shareMenu.value = false
                                    c.copyPlaylistInvite(id)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Salvar arquivo .openfyplaylist") },
                                onClick = {
                                    shareMenu.value = false
                                    meta?.let { sharePlaylist(c, id, it.name) }
                                },
                            )
                        }
                    }
                    val coverMenu = remember { mutableStateOf(false) }
                    Box {
                        TextButton(
                            onClick = { coverMenu.value = true },
                            enabled = meta != null,
                        ) {
                            Icon(
                                Icons.Filled.Image,
                                null,
                                modifier = Modifier.size(16.dp),
                                tint = Tokens.textLow,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Capa", color = Tokens.textLow)
                        }
                        DropdownMenu(
                            expanded = coverMenu.value,
                            onDismissRequest = { coverMenu.value = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Escolher imagem…") },
                                onClick = {
                                    coverMenu.value = false
                                    pickPlaylistCover(c, id)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Remover capa") },
                                onClick = {
                                    coverMenu.value = false
                                    c.clearPlaylistCover(id)
                                },
                            )
                        }
                    }
                    if (tracks.size > 1) {
                        TextButton(onClick = { ordering = !ordering }) {
                            Icon(
                                Icons.Filled.DragHandle,
                                null,
                                modifier = Modifier.size(16.dp),
                                tint = if (ordering) Tokens.accent else Tokens.textLow,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (ordering) "Concluir ordem" else "Ordenar",
                                color = if (ordering) Tokens.accent else Tokens.textLow,
                            )
                        }
                    }
                    TextButton(onClick = { meta?.let { c.askRenamePlaylist(it) } }, enabled = meta != null) {
                        Icon(Icons.Filled.Edit, null, modifier = Modifier.size(16.dp), tint = Tokens.textLow)
                        Spacer(Modifier.width(6.dp))
                        Text("Renomear", color = Tokens.textLow)
                    }
                    TextButton(onClick = { meta?.let { c.askDeletePlaylist(it) } }, enabled = meta != null) {
                        Icon(Icons.Filled.Delete, null, modifier = Modifier.size(16.dp), tint = Tokens.textLow)
                        Spacer(Modifier.width(6.dp))
                        Text("Excluir", color = Tokens.textLow)
                    }
                }
            }
        }
        HorizontalDivider(color = Tokens.line)
        if (ordering) {
            Text(
                "Arraste pelo ícone ⠿ para mudar a ordem. A nova ordem sincroniza com o celular.",
                color = Tokens.textLow,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            )
        }
        LazyColumn(Modifier.weight(1f)) {
            itemsIndexed(tracks, key = { _, t -> t.id }) { index, track ->
                val dragging = dragIndex == index
                Row(
                    Modifier.fillMaxWidth()
                        .zIndex(if (dragging) 1f else 0f)
                        .offset { IntOffset(0, if (dragging) dragOffset.roundToInt() else 0) }
                        .background(if (dragging) Tokens.panelHigh else Color.Transparent)
                        .onSizeChanged { rowHeightPx = it.height.toFloat() },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (ordering) {
                        ReorderHandle(
                            onDragStart = {
                                dragIndex = index
                                dragOffset = 0f
                            },
                            onDrag = { dragOffset += it },
                            onDragEnd = {
                                val steps = (dragOffset / rowHeightPx).roundToInt()
                                if (steps != 0) {
                                    c.moveInPlaylist(
                                        id,
                                        index,
                                        (index + steps).coerceIn(0, tracks.lastIndex),
                                    )
                                }
                                dragIndex = null
                                dragOffset = 0f
                            },
                            onDragCancel = {
                                dragIndex = null
                                dragOffset = 0f
                            },
                        )
                    }
                    Box(Modifier.weight(1f)) {
                        TrackRow(
                            c = c,
                            track = track,
                            index = index,
                            liked = track.id in likedIds,
                            onPlay = { c.playTracks(tracks, index) },
                            onLike = { c.toggleLike(track) },
                            onPlayNext = { c.playNext(track) },
                            onQueue = { c.playLater(track) },
                            onRadio = { c.startTrackRadio(track) },
                            playlists = playlists,
                            onAddTo = { playlistId -> c.addToPlaylist(playlistId, track) },
                            onRemove = { c.removeFromPlaylist(id, track.id) },
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun TrackCollection(c: AppController, title: String, tracks: List<Track>, extra: List<Track>) {
    val likedIds by c.player.likedIds.collectAsState()
    val playlists by c.playlists.collectAsState()
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Text(
            title,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Tokens.textHigh,
            modifier = Modifier.padding(top = 8.dp, bottom = 6.dp),
        )
        Text("${tracks.size} faixas", color = Tokens.textLow, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
        LazyColumn(Modifier.weight(1f)) {
            itemsIndexed(tracks, key = { _, t -> t.id }) { index, track ->
                TrackRow(
                    c = c,
                    track = track,
                    index = index,
                    liked = track.id in likedIds,
                    onPlay = { c.playTracks(tracks, index) },
                    onLike = { c.toggleLike(track) },
                    onPlayNext = { c.playNext(track) },
                    onQueue = { c.playLater(track) },
                    onRadio = { c.startTrackRadio(track) },
                    playlists = playlists,
                    onAddTo = { playlistId -> c.addToPlaylist(playlistId, track) },
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun TopPlayedScreen(c: AppController) {
    val tracks by c.topPlayed.collectAsState()
    val likedIds by c.player.likedIds.collectAsState()
    val playlists by c.playlists.collectAsState()
    LaunchedEffect(Unit) {
        if (tracks.isEmpty()) c.loadTopPlayed()
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Mais ouvidas",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Tokens.textHigh,
                )
                Text(
                    "Ranking ponderado por recência, conclusão e curtidas — sem repetir artista na sequência.",
                    color = Tokens.textLow,
                    fontSize = 12.sp,
                )
            }
            Button(
                onClick = { c.playTracks(tracks, 0, "Mais ouvidas") },
                enabled = tracks.isNotEmpty(),
                shape = RoundedCornerShape(8.dp),
            ) {
                Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Tocar", fontSize = 13.sp)
            }
            Spacer(Modifier.width(8.dp))
            TextButton(
                onClick = { c.playShuffled(tracks, "Mais ouvidas") },
                enabled = tracks.isNotEmpty(),
            ) {
                Icon(Icons.Filled.Shuffle, null, tint = Tokens.textHigh, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Aleatório", color = Tokens.textHigh, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        if (tracks.isEmpty()) {
            EmptyLibraryCard(
                title = "Ainda não há histórico suficiente",
                description = "Ouça algumas músicas para o Openfy montar seu ranking pessoal.",
                action = "Buscar música",
            ) { c.go(Screen.Search) }
        } else {
            LazyColumn(Modifier.weight(1f)) {
                itemsIndexed(tracks, key = { _, t -> t.id }) { index, track ->
                    TrackRow(
                        c = c,
                        track = track,
                        index = index,
                        liked = track.id in likedIds,
                        onPlay = { c.playTracks(tracks, index, "Mais ouvidas") },
                        onLike = { c.toggleLike(track) },
                        onPlayNext = { c.playNext(track) },
                        onQueue = { c.playLater(track) },
                        onRadio = { c.startTrackRadio(track) },
                        playlists = playlists,
                        onAddTo = { playlistId -> c.addToPlaylist(playlistId, track) },
                    )
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun RecapScreen(c: AppController) {
    val month by c.recapMonth.collectAsState()
    val months by c.recapMonths.collectAsState()
    val snapshot by c.recap.collectAsState()
    val loading by c.recapLoading.collectAsState()
    val error by c.recapError.collectAsState()
    val fromAccount by c.recapFromAccount.collectAsState()
    val likedIds by c.player.likedIds.collectAsState()
    val playlists by c.playlists.collectAsState()
    LaunchedEffect(Unit) {
        if (snapshot == null) c.loadRecap()
    }

    val label = monthLabel(month)
    val summary = snapshot?.summary
    val previous = snapshot?.previous

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Insights, null, tint = Tokens.accent, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Seu recap", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Tokens.textHigh)
                    Text(
                        if (fromAccount) "Conta · PC + celular" else "Dados deste PC",
                        color = Tokens.textLow,
                        fontSize = 12.sp,
                    )
                }
                if (loading) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Tokens.accent)
                    Spacer(Modifier.width(8.dp))
                }
                TextButton(onClick = { c.loadRecap(month, force = true) }) {
                    Text("Atualizar", color = Tokens.textLow)
                }
            }
        }

        item {
            LazyRow(
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val options = (months + month).distinct().sortedDescending()
                items(options) { option ->
                    SearchChip(monthLabel(option), option == month) { c.selectRecapMonth(option) }
                }
            }
        }

        if (error != null) {
            item {
                Text(error ?: "", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
            }
        }

        if (loading && snapshot == null) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = Tokens.accent)
                }
            }
            return@LazyColumn
        }

        if (summary == null || summary.plays == 0) {
            item {
                EmptyLibraryCard(
                    title = "Nenhuma escuta em $label",
                    description = "Toque uma música para começar a preencher este mês.",
                    action = "Buscar música",
                ) { c.go(Screen.Search) }
            }
            item { Spacer(Modifier.height(24.dp)) }
            return@LazyColumn
        }

        item {
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Tokens.heroGradient)
                    .padding(22.dp),
            ) {
                Text(
                    label.uppercase(),
                    color = Tokens.accent,
                    fontSize = 11.sp,
                    letterSpacing = 1.6.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    formatHours(summary?.playedMs?.div(1000) ?: 0),
                    color = Tokens.textHigh,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "${summary?.plays ?: 0} execuções · ${summary?.uniqueTracks ?: 0} faixas únicas",
                    color = Tokens.textLow,
                    fontSize = 13.sp,
                )
                val delta = comparison(previous, summary)
                if (delta != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        delta,
                        color = if (delta.startsWith("+")) Tokens.accent else Tokens.textLow,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatTile("Artistas", "${summary?.uniqueArtists ?: 0}", Modifier.weight(1f))
                StatTile("Conclusão", "${((summary?.completionRate ?: 0.0) * 100).roundToInt()}%", Modifier.weight(1f))
                StatTile("Puladas", "${((summary?.skipRate ?: 0.0) * 100).roundToInt()}%", Modifier.weight(1f))
                StatTile("Curtidas", "${snapshot?.liked ?: 0}", Modifier.weight(1f))
            }
        }

        item { SectionTitle("Top faixas") }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                val tracks = snapshot?.topTracks.orEmpty()
                items(tracks, key = { "recap-t-${it.track.id}" }) { entry ->
                    CoverCard(
                        entry.track.title,
                        "${entry.plays} plays · ${formatHours(entry.playedMs / 1000)}",
                        entry.track.thumbnail,
                        CoverCardMenu(
                            onPlayNext = { c.playNext(entry.track) },
                            onQueue = { c.playLater(entry.track) },
                            onRadio = { c.startTrackRadio(entry.track) },
                        ),
                    ) {
                        c.playTracks(tracks.map { it.track }, tracks.indexOf(entry), "Recap · $label")
                    }
                }
            }
        }

        item { Spacer(Modifier.height(20.dp)); SectionTitle("Top artistas") }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                val artists = snapshot?.topArtists.orEmpty()
                items(artists, key = { "recap-a-${it.name}" }) { artist ->
                    CoverCard(
                        artist.name,
                        "${artist.plays} plays · ${formatHours(artist.playedMs / 1000)}",
                        artist.thumbnail,
                    ) {}
                }
            }
        }

        item { Spacer(Modifier.height(20.dp)); SectionTitle("Atividade diária") }
        item {
            val days = snapshot?.days.orEmpty()
            val max = days.maxOfOrNull { it.plays }?.coerceAtLeast(1) ?: 1
            if (days.isEmpty()) {
                Text("Sem atividade neste mês.", color = Tokens.textLow, fontSize = 12.sp)
            } else {
                Row(
                    Modifier.fillMaxWidth().height(74.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    days.forEach { day ->
                        val height = (8 + (day.plays.toFloat() / max) * 60).dp
                        Box(
                            Modifier.weight(1f).height(height)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (day.plays > 0) Tokens.accent else Tokens.line),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "${days.size} dias com música · pico de $max execuções",
                    color = Tokens.textLow,
                    fontSize = 11.sp,
                )
            }
        }

        val firstTime = snapshot?.firstTimeTracks.orEmpty()
        if (firstTime.isNotEmpty()) {
            item { Spacer(Modifier.height(20.dp)); SectionTitle("Descobertas do mês") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(firstTime, key = { "recap-n-${it.track.id}" }) { entry ->
                        CoverCard(
                            entry.track.title,
                            entry.track.artist,
                            entry.track.thumbnail,
                        ) {
                            c.playTracks(firstTime.map { it.track }, firstTime.indexOf(entry), "Descobertas")
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun EmptyLibraryCard(
    title: String,
    description: String,
    action: String,
    onClick: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Tokens.panel)
            .padding(24.dp),
    ) {
        Text(title, color = Tokens.textHigh, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(description, color = Tokens.textLow, fontSize = 13.sp)
        Spacer(Modifier.height(14.dp))
        Button(onClick = onClick, shape = RoundedCornerShape(8.dp)) {
            Text(action, fontSize = 13.sp)
        }
    }
}

private fun monthLabel(month: YearMonth): String {
    val formatted = month.atDay(1).format(
        DateTimeFormatter.ofPattern("MMMM 'de' yyyy", Locale("pt", "BR")),
    )
    return formatted.replaceFirstChar { it.uppercase() }
}

private fun comparison(previous: com.openfy.core.RecapSummary?, current: com.openfy.core.RecapSummary?): String? {
    if (previous == null || current == null || previous.plays == 0) return null
    val diff = current.plays - previous.plays
    if (diff == 0) return "Mesmo volume de execuções do mês passado."
    val percent = (diff.toDouble() / previous.plays * 100).roundToInt()
    val sign = if (percent > 0) "+" else ""
    return "$sign$percent% em execuções vs. mês passado"
}

@Composable
private fun DownloadsScreen(c: AppController) {
    val tracks by c.downloadedTracks.collectAsState()
    val likedIds by c.player.likedIds.collectAsState()
    val playlists by c.playlists.collectAsState()
    val auto by c.autoDownload.collectAsState()
    val size by c.downloadsSize.collectAsState()
    val downloading by c.downloadingIds.collectAsState()

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Text(
            "Downloads",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Tokens.textHigh,
            modifier = Modifier.padding(top = 8.dp, bottom = 6.dp),
        )
        Text(
            "${tracks.size} faixas salvas · ${formatBytes(size)}",
            color = Tokens.textLow,
            fontSize = 12.sp,
        )
        if (downloading.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Baixando ${downloading.size} faixa(s) em segundo plano…",
                color = Tokens.accent,
                fontSize = 11.sp,
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Download automático",
                    color = Tokens.textHigh,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Baixa em segundo plano as mais ouvidas e algumas similares",
                    color = Tokens.textLow,
                    fontSize = 11.sp,
                )
            }
            Switch(checked = auto, onCheckedChange = { c.setAutoDownload(it) }, colors = openfySwitchColors())
        }
        HorizontalDivider(color = Tokens.line)
        Spacer(Modifier.height(10.dp))
        if (tracks.isEmpty()) {
            Text(
                "Nada baixado ainda. No menu (⋯) de qualquer faixa, escolha \"Baixar\" — " +
                    "elas passam a tocar mesmo sem internet.",
                color = Tokens.textLow,
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 18.dp),
            )
            return@Column
        }
        HorizontalDivider(color = Tokens.line)
        LazyColumn(Modifier.weight(1f)) {
            itemsIndexed(tracks, key = { _, t -> t.id }) { index, track ->
                TrackRow(
                    c = c,
                    track = track,
                    index = index,
                    liked = track.id in likedIds,
                    onPlay = { c.playTracks(tracks, index) },
                    onLike = { c.toggleLike(track) },
                    onPlayNext = { c.playNext(track) },
                    onQueue = { c.playLater(track) },
                    onRadio = { c.startTrackRadio(track) },
                    playlists = playlists,
                    onAddTo = { playlistId -> c.addToPlaylist(playlistId, track) },
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

// ------------------------------------------------------------------ profile
private fun profileAccent(id: String): Color = when (ColorPalette.fromId(id)) {
    ColorPalette.OPENFY -> Color(0xFF2FBFA0)
    ColorPalette.VIOLET -> Color(0xFFA78BFA)
    ColorPalette.ROSE -> Color(0xFFF472B6)
    ColorPalette.AMBER -> Color(0xFFF5A524)
    ColorPalette.ICE -> Color(0xFF7DD3FC)
}

private fun chooseProfileImage(title: String): File? {
    val chooser = javax.swing.JFileChooser().apply {
        dialogTitle = title
        fileFilter = javax.swing.filechooser.FileNameExtensionFilter(
            "Imagens", "png", "jpg", "jpeg", "webp", "gif",
        )
    }
    return if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile
    } else {
        null
    }
}

@Composable
private fun ProfileScreen(c: AppController) {
    val account by c.account.state.collectAsState()
    val liked by c.liked.collectAsState()
    val session = account.session

    if (session == null) {
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
            Text(
                "Perfil",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Tokens.textHigh,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
            Text(
                "Entre na conta para montar seu perfil e levar as mesmas faixas fixadas, " +
                    "cores e estatísticas para o celular.",
                color = Tokens.textLow,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = { c.go(Screen.Settings) }) {
                Text("Entrar ou criar conta")
            }
        }
        return
    }

    val profile = account.profile
    val extras = account.profileExtras
    val stats = account.profileStats
    val accent = profileAccent(extras.accent)
    val displayName = nameOrFallback(profile?.displayName, session.email)
    val handle = profile?.handle?.takeIf { it.isNotBlank() }?.let { "@$it" }.orEmpty()

    var editing by remember { mutableStateOf(false) }
    var name by remember(editing, profile?.displayName) {
        mutableStateOf(profile?.displayName.orEmpty())
    }
    var bio by remember(editing, extras.bio) { mutableStateOf(extras.bio) }
    var avatarFile by remember { mutableStateOf<File?>(null) }
    var coverFile by remember { mutableStateOf<File?>(null) }
    var pinMenuOpen by remember { mutableStateOf(false) }
    var pinDragIndex by remember { mutableStateOf<Int?>(null) }
    var pinDragOffset by remember { mutableStateOf(0f) }
    val profileDensity = LocalDensity.current
    var pinRowHeightPx by remember { mutableStateOf(with(profileDensity) { 52.dp.toPx() }) }
    var cardDragIndex by remember { mutableStateOf<Int?>(null) }
    var cardDragOffset by remember { mutableStateOf(0f) }
    var cardRowHeightPx by remember { mutableStateOf(with(profileDensity) { 44.dp.toPx() }) }

    LaunchedEffect(Unit) { c.account.loadProfileStats() }

    val order = ProfileStatsCards.sanitizeOrder(extras.statsOrder)
    val hidden = extras.statsHidden.toSet()
    val visibleCards = order.filter { it !in hidden }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Seu perfil", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Tokens.textHigh)
                Text(
                    "Visível só para você até o social ser liberado.",
                    color = Tokens.textLow,
                    fontSize = 12.sp,
                )
            }
            if (!editing) {
                Button(onClick = { editing = true }) {
                    Icon(Icons.Filled.Edit, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Editar perfil")
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(16.dp))) {
            ProfileCover(
                url = extras.coverUrl,
                gradient = extras.style.coverGradient,
                accent = accent,
                glow = extras.style.glow,
                motion = extras.style.motion,
            )
            Row(
                Modifier.align(Alignment.BottomStart).padding(16.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                ProfileAvatar(profile?.avatarUrl.orEmpty(), 88.dp, accent)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.padding(bottom = 4.dp)) {
                    Text(
                        if (editing) name.ifBlank { displayName } else displayName,
                        color = Tokens.textHigh,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    if (handle.isNotBlank()) {
                        Text(handle, color = Tokens.textLow, fontSize = 12.sp)
                    }
                }
            }
        }

        val shownBio = if (editing) bio else extras.bio
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                shownBio.ifBlank { "Sem bio ainda. Conte em uma linha o que você anda ouvindo." },
                color = if (shownBio.isBlank()) Tokens.textLow.copy(alpha = 0.7f) else Tokens.textHigh,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                Spacer(Modifier.width(5.dp))
                Text(
                    "Estatísticas: ${ProfileStatsVisibility.label(extras.statsVisibility)}",
                    color = Tokens.textLow,
                    fontSize = 11.sp,
                )
            }
        }

        if (account.profileStatus != null || account.error != null) {
            Text(
                account.profileStatus ?: account.error.orEmpty(),
                color = if (account.error != null) Color(0xFFFF8A80) else accent,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        if (visibleCards.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(top = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Estatísticas do mês",
                    color = Tokens.textHigh,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (account.profileStatsLoading) {
                    CircularProgressIndicator(
                        Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = accent,
                    )
                } else {
                    TextButton(onClick = { c.account.loadProfileStats(force = true) }) {
                        Text("Atualizar", color = accent, fontSize = 12.sp)
                    }
                }
            }
            visibleCards.chunked(2).forEach { row ->
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    row.forEach { id ->
                        ProfileStatCard(
                            id = id,
                            stats = stats,
                            accent = accent,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        val showArtists = ProfileStatsCards.TOP_ARTISTS !in hidden &&
            stats?.topArtists?.isNotEmpty() == true
        if (showArtists) {
            ProfileTopArtists(stats.topArtists, accent)
        }

        val showTracks = ProfileStatsCards.TOP_TRACKS !in hidden &&
            stats?.topTracks?.isNotEmpty() == true
        if (showTracks) {
            Text(
                "Top faixas",
                color = Tokens.textHigh,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 18.dp, bottom = 6.dp),
            )
            val tracks = stats.topTracks.map { it.track }
            stats.topTracks.forEachIndexed { index, row ->
                ProfileTopTrackRow(
                    position = index + 1,
                    track = row.track,
                    plays = row.plays,
                    accent = accent,
                ) { c.playTracks(tracks, index, "Top faixas do perfil") }
            }
        }

        if (extras.pins.isNotEmpty()) {
            Text(
                "Faixas fixadas",
                color = Tokens.textHigh,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 18.dp, bottom = 4.dp),
            )
            extras.pins.forEachIndexed { index, pin ->
                val dragging = pinDragIndex == index
                Box(
                    Modifier.fillMaxWidth()
                        .zIndex(if (dragging) 1f else 0f)
                        .offset { IntOffset(0, if (dragging) pinDragOffset.roundToInt() else 0) }
                        .background(if (dragging) Tokens.panelHigh else Color.Transparent)
                        .onSizeChanged { pinRowHeightPx = it.height.toFloat() },
                ) {
                    ProfilePinRow(
                        pin = pin,
                        editing = editing,
                        accent = accent,
                        onPlay = {
                            c.playTracks(extras.pins.map { it.toTrack() }, index, "Faixas fixadas")
                        },
                        dragModifier = Modifier.pointerInput(extras.pins.size, index) {
                            detectDragGestures(
                                onDragStart = {
                                    pinDragIndex = index
                                    pinDragOffset = 0f
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    pinDragOffset += amount.y
                                },
                                onDragEnd = {
                                    val steps = (pinDragOffset / pinRowHeightPx).roundToInt()
                                    var moved = 0
                                    while (moved < kotlin.math.abs(steps)) {
                                        c.moveProfilePin(pin.id, if (steps > 0) 1 else -1)
                                        moved++
                                    }
                                    pinDragIndex = null
                                    pinDragOffset = 0f
                                },
                                onDragCancel = {
                                    pinDragIndex = null
                                    pinDragOffset = 0f
                                },
                            )
                        },
                        onUp = { c.moveProfilePin(pin.id, -1) },
                        onDown = { c.moveProfilePin(pin.id, 1) },
                        onRemove = { c.removeProfilePin(pin.id) },
                    )
                }
            }
        }

        if (editing) {
            Spacer(Modifier.height(6.dp))
            SettingsSection("Identidade") {
                Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(60) },
                        label = { Text("Nome") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().trackInputFocus(),
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
                                color = Tokens.textLow,
                                fontSize = 10.sp,
                            )
                        },
                        modifier = Modifier.fillMaxWidth().trackInputFocus(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            chooseProfileImage("Escolha a foto de perfil")?.let { avatarFile = it }
                        }) {
                            Icon(Icons.Filled.Image, null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(if (avatarFile == null) "Trocar foto" else "Foto escolhida", color = accent)
                        }
                        TextButton(onClick = {
                            chooseProfileImage("Escolha a capa do perfil")?.let { coverFile = it }
                        }) {
                            Icon(Icons.Filled.Image, null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(if (coverFile == null) "Trocar capa" else "Capa escolhida", color = accent)
                        }
                    }
                    if (avatarFile != null || coverFile != null) {
                        Text(
                            "As imagens sobem quando você salvar o perfil.",
                            color = Tokens.textLow,
                            fontSize = 10.sp,
                        )
                    }
                }
            }

            SettingsSection("Cor e efeitos") {
                SettingsRow("Cor do perfil", "Destaques, gráficos e detalhes.") {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ColorPalette.entries.forEach { preset ->
                            SearchChip(preset.label, extras.accent == preset.id) {
                                c.updateProfile { it.copy(accent = preset.id) }
                            }
                        }
                    }
                }
                SettingsRow("Gradiente na capa", "Usa a cor do perfil quando não há imagem.") {
                    Switch(
                        checked = extras.style.coverGradient,
                        onCheckedChange = { value ->
                            c.updateProfile { it.copy(style = it.style.copy(coverGradient = value)) }
                        },
                        colors = openfySwitchColors(),
                    )
                }
                SettingsRow("Movimento", "Transições suaves na capa e nos cartões.") {
                    Switch(
                        checked = extras.style.motion,
                        onCheckedChange = { value ->
                            c.updateProfile { it.copy(style = it.style.copy(motion = value)) }
                        },
                        colors = openfySwitchColors(),
                    )
                }
                SettingsRow("Brilho", "Contorno iluminado nos cartões.") {
                    Switch(
                        checked = extras.style.glow,
                        onCheckedChange = { value ->
                            c.updateProfile { it.copy(style = it.style.copy(glow = value)) }
                        },
                        colors = openfySwitchColors(),
                    )
                }
            }

            SettingsSection("Quem vê suas estatísticas") {
                Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ProfileStatsVisibility.all.forEach { value ->
                            SearchChip(ProfileStatsVisibility.label(value), extras.statsVisibility == value) {
                                c.updateProfile { it.copy(statsVisibility = value) }
                            }
                        }
                    }
                    Text(
                        "Privado mantém os números só neste perfil. Seguidores/público ficam " +
                            "prontos para quando o perfil social for liberado.",
                        color = Tokens.textLow,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            SettingsSection("Cartões de estatística") {
                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    order.forEachIndexed { index, id ->
                        val dragging = cardDragIndex == index
                        Row(
                            Modifier.fillMaxWidth()
                                .zIndex(if (dragging) 1f else 0f)
                                .offset { IntOffset(0, if (dragging) cardDragOffset.roundToInt() else 0) }
                                .background(if (dragging) Tokens.panelHigh else Color.Transparent)
                                .onSizeChanged { cardRowHeightPx = it.height.toFloat() }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ReorderHandle(
                                onDragStart = {
                                    cardDragIndex = index
                                    cardDragOffset = 0f
                                },
                                onDrag = { cardDragOffset += it },
                                onDragEnd = {
                                    val steps = (cardDragOffset / cardRowHeightPx).roundToInt()
                                    var moved = 0
                                    while (moved < kotlin.math.abs(steps)) {
                                        c.moveProfileCard(id, if (steps > 0) 1 else -1)
                                        moved++
                                    }
                                    cardDragIndex = null
                                    cardDragOffset = 0f
                                },
                                onDragCancel = {
                                    cardDragIndex = null
                                    cardDragOffset = 0f
                                },
                            )
                            Text(
                                ProfileStatsCards.label(id),
                                color = Tokens.textHigh,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = { c.moveProfileCard(id, -1) },
                                enabled = index > 0,
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    Icons.Filled.KeyboardArrowUp,
                                    "Mover para cima",
                                    tint = if (index > 0) Tokens.textLow else Tokens.textLow.copy(alpha = 0.3f),
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                            IconButton(
                                onClick = { c.moveProfileCard(id, 1) },
                                enabled = index < order.lastIndex,
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    Icons.Filled.KeyboardArrowDown,
                                    "Mover para baixo",
                                    tint = if (index < order.lastIndex) {
                                        Tokens.textLow
                                    } else {
                                        Tokens.textLow.copy(alpha = 0.3f)
                                    },
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                            Switch(
                                checked = id !in hidden,
                                onCheckedChange = { c.toggleProfileCard(id, it) },
                                colors = openfySwitchColors(),
                            )
                        }
                    }
                }
            }

            SettingsSection("Faixas fixadas") {
                Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                    Box {
                        Button(
                            onClick = { pinMenuOpen = true },
                            enabled = extras.pins.size < ProfileExtras.PIN_LIMIT,
                        ) {
                            Icon(Icons.Filled.Add, null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Fixar uma curtida")
                        }
                        DropdownMenu(
                            expanded = pinMenuOpen,
                            onDismissRequest = { pinMenuOpen = false },
                        ) {
                            val available = liked.filter { track ->
                                extras.pins.none { it.id == track.id }
                            }
                            if (available.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("Nenhuma curtida disponível", fontSize = 12.sp) },
                                    onClick = { pinMenuOpen = false },
                                    enabled = false,
                                )
                            } else {
                                available.take(30).forEach { track ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(
                                                    track.title,
                                                    fontSize = 12.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                Text(
                                                    track.artist,
                                                    color = Tokens.textLow,
                                                    fontSize = 10.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        },
                                        onClick = {
                                            c.addProfilePin(track)
                                            pinMenuOpen = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        "${extras.pins.size}/${ProfileExtras.PIN_LIMIT} fixadas. " +
                            "Arraste pelo ícone ⠿ ou use as setas para mudar a ordem.",
                        color = Tokens.textLow,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    if (extras.pins.isEmpty()) {
                        Text(
                            "Nada fixado ainda — escolha entre as suas curtidas.",
                            color = Tokens.textLow,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }

            Row(
                Modifier.padding(top = 14.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = {
                        c.saveProfile(name, extras.copy(bio = bio), avatarFile, coverFile)
                        avatarFile = null
                        coverFile = null
                        editing = false
                    },
                    enabled = !account.profileBusy,
                ) {
                    if (account.profileBusy) {
                        CircularProgressIndicator(
                            Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = Tokens.ink,
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text("Salvar perfil")
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = {
                    name = profile?.displayName.orEmpty()
                    bio = extras.bio
                    avatarFile = null
                    coverFile = null
                    editing = false
                }) {
                    Text("Cancelar", color = Tokens.textLow)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

private fun nameOrFallback(name: String?, email: String): String =
    name?.takeIf { it.isNotBlank() }
        ?: email.substringBefore("@").ifBlank { "Conta Openfy" }

@Composable
private fun ProfileCover(
    url: String,
    gradient: Boolean,
    accent: Color,
    glow: Boolean,
    motion: Boolean = false,
) {
    var bitmap by remember(url) { mutableStateOf(ArtworkCache.peek(url)) }
    LaunchedEffect(url) {
        if (url.isBlank() || bitmap != null) return@LaunchedEffect
        bitmap = ArtworkCache.get(url)
    }
    val image = bitmap
    val animatedGradient = if (motion) {
        val transition = rememberInfiniteTransition(label = "profile-cover")
        val phase by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(9000, easing = LinearEasing),
                repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
            ),
            label = "cover-phase",
        )
        Brush.linearGradient(
            colors = listOf(
                accent.copy(alpha = if (glow) 0.6f else 0.4f),
                Tokens.panelHigh,
                Tokens.ink,
            ),
            start = Offset(phase * 640f, 0f),
            end = Offset(640f + phase * 640f, 960f),
        )
    } else {
        Brush.linearGradient(
            listOf(
                accent.copy(alpha = if (glow) 0.55f else 0.35f),
                Tokens.panelHigh,
                Tokens.ink,
            ),
        )
    }
    Box(
        Modifier.fillMaxSize().background(
            if (gradient || image == null) {
                animatedGradient
            } else {
                Brush.verticalGradient(listOf(Tokens.panelHigh, Tokens.ink))
            },
        ),
    ) {
        if (image != null) {
            Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            if (gradient) {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.linearGradient(
                            listOf(
                                accent.copy(alpha = if (glow) 0.42f else 0.28f),
                                Color.Transparent,
                                Tokens.ink.copy(alpha = 0.35f),
                            ),
                        ),
                    ),
                )
            }
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Tokens.ink.copy(alpha = 0.78f)),
                ),
            ),
        )
    }
}

@Composable
private fun ProfileAvatar(url: String, size: Dp, accent: Color) {
    Box(
        Modifier.size(size).clip(CircleShape)
            .background(accent.copy(alpha = 0.28f))
            .padding(3.dp),
    ) {
        Cover(url, size - 6.dp, radius = (size - 6.dp) / 2)
    }
}

@Composable
private fun ProfileStatCard(
    id: String,
    stats: ProfileStatsData?,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val label = ProfileStatsCards.label(id)
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
        modifier.clip(RoundedCornerShape(12.dp)).background(Tokens.panel)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(accent))
            Spacer(Modifier.width(6.dp))
            Text(label, color = Tokens.textLow, fontSize = 11.sp)
        }
        Text(
            value,
            color = Tokens.textHigh,
            fontSize = if (id == ProfileStatsCards.TOP_ARTISTS || id == ProfileStatsCards.TOP_TRACKS) {
                15.sp
            } else {
                22.sp
            },
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            detail,
            color = Tokens.textLow,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun formatProfileMinutes(minutes: Long): String {
    if (minutes < 60) return "$minutes min"
    val hours = minutes / 60
    val rest = minutes % 60
    return if (rest == 0L) "${hours}h" else "${hours}h ${rest}m"
}

@Composable
private fun ProfileTopArtists(artists: List<ProfileArtistStat>, accent: Color) {
    Text(
        "Top artistas",
        color = Tokens.textHigh,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 18.dp, bottom = 4.dp),
    )
    artists.forEachIndexed { index, artist ->
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${index + 1}",
                color = accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.width(22.dp),
            )
            Text(
                artist.name.ifBlank { "Artista" },
                color = Tokens.textHigh,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text("${artist.plays} plays", color = Tokens.textLow, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ProfileTopTrackRow(
    position: Int,
    track: Track,
    plays: Long,
    accent: Color,
    onPlay: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onPlay)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$position",
            color = accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(22.dp),
        )
        Cover(track.thumbnail, 38.dp, radius = 7.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                color = Tokens.textHigh,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                track.artist,
                color = Tokens.textLow,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text("$plays plays", color = Tokens.textLow, fontSize = 11.sp)
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
    dragModifier: Modifier = Modifier,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (editing) {
            Box(
                Modifier.size(26.dp).clip(CircleShape).then(dragModifier),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.DragHandle,
                    "Arrastar para reordenar",
                    tint = Tokens.textLow,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Cover(pin.thumbnail, 42.dp, radius = 8.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                pin.title,
                color = Tokens.textHigh,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                pin.artist,
                color = Tokens.textLow,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            formatTime(pin.durationSec.toDouble()),
            color = Tokens.textLow,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
        if (editing) {
            IconButton(onClick = onUp, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Filled.KeyboardArrowUp,
                    "Mover para cima",
                    tint = Tokens.textLow,
                    modifier = Modifier.size(16.dp),
                )
            }
            IconButton(onClick = onDown, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    "Mover para baixo",
                    tint = Tokens.textLow,
                    modifier = Modifier.size(16.dp),
                )
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Filled.Delete,
                    "Remover do perfil",
                    tint = Tokens.textLow,
                    modifier = Modifier.size(16.dp),
                )
            }
        } else {
            IconButton(onClick = onPlay, modifier = Modifier.size(32.dp)) {
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
private fun SettingsScreen(c: AppController) {
    val quality by c.audioQuality.collectAsState()
    val crossfade by c.player.crossfadeSeconds.collectAsState()
    val autoplayRadio by c.player.autoplayRadio.collectAsState()
    val account by c.account.state.collectAsState()
    var accountEmail by remember { mutableStateOf("") }
    var accountPassword by remember { mutableStateOf("") }
    var creatingAccount by remember { mutableStateOf(false) }
    val auto by c.autoDownload.collectAsState()
    val closeToTray by c.closeToTray.collectAsState()
    val downloadedSize by c.downloadsSize.collectAsState()
    val palette by c.palette.collectAsState()
    val playerLayout by c.playerLayout.collectAsState()
    val coverGradient by c.coverGradient.collectAsState()
    val discordEnabled by c.discordEnabled.collectAsState()
    val discordAppId by c.discordAppId.collectAsState()
    val discordPrivate by c.discordPrivate.collectAsState()
    val discordStatus by c.discord.status.collectAsState()
    var appIdDraft by remember { mutableStateOf(discordAppId) }
    var eqOpen by remember { mutableStateOf(false) }

    LaunchedEffect(discordAppId) { appIdDraft = discordAppId }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            "Ajustes",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Tokens.textHigh,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
        )
        Text("Reprodução, downloads, janela e dados", color = Tokens.textLow, fontSize = 12.sp)

        SettingsSection("Reprodução") {
            SettingsRow(
                "Qualidade do áudio",
                "Alta usa o melhor disponível; Econômica reduz o consumo de dados.",
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SearchChip("Alta", quality == "HIGH") { c.setAudioQuality("HIGH") }
                    SearchChip("Econômica", quality == "LOW") { c.setAudioQuality("LOW") }
                }
            }
            SettingsRow(
                "Transição entre faixas",
                if (crossfade == 0) {
                    "Desligada — a próxima só começa quando a atual termina."
                } else {
                    "${crossfade}s de sobreposição real: as duas faixas tocam juntas."
                },
            ) {
                Slider(
                    value = crossfade.toFloat(),
                    onValueChange = { c.setCrossfadeSeconds(it.roundToInt()) },
                    valueRange = 0f..15f,
                    steps = 14,
                    colors = openfySliderColors(),
                    modifier = Modifier.width(220.dp),
                )
            }
            SettingsRow(
                "Rádio automática",
                "Ao terminar uma playlist ou álbum, continua com uma rádio da última faixa.",
            ) {
                Switch(
                    checked = autoplayRadio,
                    onCheckedChange = { c.player.setAutoplayRadio(it) },
                    colors = openfySwitchColors(),
                )
            }
            SettingsRow("Equalizador", "5 bandas + presets") {
                TextButton(onClick = { eqOpen = true }) {
                    Text("Abrir", color = Tokens.accent)
                }
            }
        }

        SettingsSection("Aparência") {
            SettingsRow(
                "Paleta de cores",
                "Aplicada em todo o app e salva para as próximas aberturas.",
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ColorPalette.entries.forEach { preset ->
                        SearchChip(preset.label, palette == preset) { c.setPalette(preset) }
                    }
                }
            }
            SettingsRow(
                "Layout do player",
                "Central mantém a capa no meio; Lateral coloca capa e informações lado a lado.",
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PlayerLayout.entries.forEach { option ->
                        SearchChip(option.label, playerLayout == option) { c.setPlayerLayout(option) }
                    }
                }
            }
            SettingsRow(
                "Fundo pela capa",
                "Na tela cheia, o gradiente usa as cores dominantes da capa atual.",
            ) {
                Switch(
                    checked = coverGradient,
                    onCheckedChange = { c.setCoverGradient(it) },
                    colors = openfySwitchColors(),
                )
            }
        }

        SettingsSection("Conta e sincronização") {
            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                if (account.session == null) {
                    Text(
                        "Entre para sincronizar playlists, curtidas e histórico com o celular.",
                        color = Tokens.textLow,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = accountEmail,
                        onValueChange = { accountEmail = it },
                        label = { Text("E-mail") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth().trackInputFocus(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = accountPassword,
                        onValueChange = { accountPassword = it },
                        label = { Text("Senha") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth().trackInputFocus(),
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = {
                                if (creatingAccount) {
                                    c.account.signUp(accountEmail, accountPassword)
                                } else {
                                    c.account.signIn(accountEmail, accountPassword)
                                }
                            },
                            enabled = !account.busy,
                        ) {
                            if (account.busy) {
                                CircularProgressIndicator(
                                    Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = Tokens.ink,
                                )
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(if (creatingAccount) "Criar conta" else "Entrar")
                        }
                        Spacer(Modifier.width(8.dp))
                        TextButton(
                            onClick = {
                                creatingAccount = !creatingAccount
                                c.account.clearFeedback()
                            },
                        ) {
                            Text(
                                if (creatingAccount) "Já tenho conta" else "Criar conta",
                                color = Tokens.accent2,
                            )
                        }
                    }
                } else {
                    val session = account.session
                    val name = account.profile?.displayName
                        ?.takeIf { it.isNotBlank() }
                        ?: session?.email?.substringBefore("@").orEmpty().ifBlank { "Conta Openfy" }
                    Text(name, color = Tokens.textHigh, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(session?.email ?: "", color = Tokens.textLow, fontSize = 12.sp)
                    Text(
                        "Último sync: ${account.lastSync ?: "nunca"}",
                        color = Tokens.textLow,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = { c.account.syncNow() }, enabled = !account.busy) {
                            if (account.busy) {
                                CircularProgressIndicator(
                                    Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = Tokens.ink,
                                )
                                Spacer(Modifier.width(6.dp))
                            }
                            Text("Sincronizar agora")
                        }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { c.account.signOut() }, enabled = !account.busy) {
                            Text("Sair", color = Tokens.textLow)
                        }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { c.openProfile() }) {
                            Text("Abrir perfil", color = Tokens.accent2)
                        }
                    }
                }
                account.status?.let {
                    Text(it, color = Tokens.accent, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
                }
                account.error?.let {
                    Text(it, color = Color(0xFFFF8A80), fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }

        SettingsSection("Integrações") {
            SettingsRow(
                "Discord Rich Presence",
                "Mostra a faixa atual no seu perfil do Discord. Usa o app desktop aberto, sem login.",
            ) {
                Switch(
                    checked = discordEnabled,
                    onCheckedChange = { c.setDiscordEnabled(it) },
                    colors = openfySwitchColors(),
                )
            }
            Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                Text(
                    "Application ID",
                    color = Tokens.textHigh,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    "Crie um aplicativo em discord.com/developers, copie o Application ID " +
                        "e envie as imagens \"openfy\", \"play\" e \"pause\" em Rich Presence > Art Assets.",
                    color = Tokens.textLow,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = appIdDraft,
                        onValueChange = { appIdDraft = it.filter { char -> char.isDigit() }.take(32) },
                        singleLine = true,
                        placeholder = { Text("Ex.: 123456789012345678", color = Tokens.textLow, fontSize = 12.sp) },
                        modifier = Modifier.width(260.dp).trackInputFocus(),
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { c.setDiscordAppId(appIdDraft) },
                        enabled = appIdDraft != discordAppId,
                    ) {
                        Text("Salvar ID")
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(discordStatus, color = Tokens.textLow, fontSize = 11.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { c.testDiscord() }, enabled = discordEnabled) {
                        Text("Testar conexão", color = Tokens.accent)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Precisa do Discord desktop aberto.",
                        color = Tokens.textLow,
                        fontSize = 10.sp,
                    )
                }
            }
            SettingsRow(
                "Modo privado",
                "Mostra só \"Ouvindo música\" e o nome do Openfy, sem título nem artista.",
            ) {
                Switch(
                    checked = discordPrivate,
                    onCheckedChange = { c.setDiscordPrivate(it) },
                    colors = openfySwitchColors(),
                )
            }
        }

        SettingsSection("Downloads") {
            SettingsRow(
                "Download automático",
                "Mais ouvidas + similares em segundo plano",
            ) {
                Switch(checked = auto, onCheckedChange = { c.setAutoDownload(it) }, colors = openfySwitchColors())
            }
            SettingsRow("Espaço usado", formatBytes(downloadedSize)) {
                TextButton(onClick = { c.openDataFolder() }) {
                    Text("Abrir pasta", color = Tokens.accent)
                }
            }
        }

        SettingsSection("Janela") {
            SettingsRow(
                "Fechar para a bandeja",
                "O X minimiza e o áudio continua. Desligado, o X encerra.",
            ) {
                Switch(checked = closeToTray, onCheckedChange = { c.setCloseToTray(it) }, colors = openfySwitchColors())
            }
        }

        SettingsSection("Atualizações") {
            val update by c.updateStatus.collectAsState()
            val autoUpdate by c.updateAutoCheck.collectAsState()
            val install by c.updateInstall.collectAsState()
            val exitRequested by c.updateExitRequested.collectAsState()
            SettingsRow(
                "Verificar ao abrir",
                "Procura versões novas no GitHub, no máximo 1x por dia.",
            ) {
                Switch(
                    checked = autoUpdate,
                    onCheckedChange = { c.setUpdateAutoCheck(it) },
                    colors = openfySwitchColors(),
                )
            }
            SettingsRow("Versão atual", "Openfy ${BuildInfo.VERSION}") {
                TextButton(
                    onClick = { c.checkForUpdate() },
                    enabled = update !is UpdateStatus.Checking,
                ) {
                    Text(
                        if (update is UpdateStatus.Checking) "Verificando…" else "Verificar agora",
                        color = Tokens.accent,
                    )
                }
            }
            when (val status = update) {
                is UpdateStatus.UpToDate -> Text(
                    "Você está na versão mais recente.",
                    color = Tokens.textLow,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                is UpdateStatus.Available -> {
                    val downloading = install as? UpdateInstaller.Status.Downloading
                    val ready = install is UpdateInstaller.Status.Ready || exitRequested
                    SettingsRow(
                        "Openfy ${status.release.version} disponível",
                        when (val state = install) {
                            is UpdateInstaller.Status.Error -> state.message
                            else -> when {
                                status.release.installerUrl == null ->
                                    "Veja os arquivos da release no GitHub."
                                downloading != null -> "Baixando o instalador…"
                                ready -> "Instalador pronto. O Openfy fecha e abre o assistente."
                                else -> "Baixe o instalador e o app fecha para atualizar."
                            }
                        },
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(onClick = { c.openReleasePage() }) {
                                Text("Abrir release", color = Tokens.accent)
                            }
                            if (status.release.installerUrl != null && downloading == null && !ready) {
                                TextButton(onClick = { c.downloadAndInstallUpdate() }) {
                                    Text(
                                        if (install is UpdateInstaller.Status.Error) "Tentar de novo" else "Baixar e instalar",
                                        color = Tokens.accent,
                                    )
                                }
                            }
                        }
                    }
                    if (downloading != null) {
                        LinearProgressIndicator(
                            progress = { downloading.progress },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            color = Tokens.accent,
                        )
                    }
                }
                is UpdateStatus.Error -> Text(
                    status.message,
                    color = Color(0xFFFF8A80),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                else -> Unit
            }
        }

        SettingsSection("Dados") {
            SettingsRow("Backup e importação", "Playlists, curtidas e CSV do Spotify") {
                TextButton(onClick = { c.openImport() }) {
                    Text("Abrir", color = Tokens.accent)
                }
            }
            SettingsRow("Cache de capas", "Limpa as imagens em memória e disco") {
                TextButton(onClick = {
                    ArtworkCache.clearDisk()
                    c.consumeMessage()
                }) {
                    Text("Limpar", color = Tokens.accent)
                }
            }
            SettingsRow("Pasta de dados", "Banco, downloads, capas e logs") {
                TextButton(onClick = { c.openDataFolder() }) {
                    Text("Abrir", color = Tokens.accent)
                }
            }
        }

        Text(
            "Openfy ${BuildInfo.VERSION}",
            color = Tokens.textLow,
            fontSize = 11.sp,
            modifier = Modifier.padding(vertical = 24.dp),
        )
    }
    if (eqOpen) EqualizerDialog(c) { eqOpen = false }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        title,
        color = Tokens.textHigh,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 18.dp, bottom = 6.dp),
    )
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(Tokens.panel)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        content = content,
    )
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    action: @Composable RowScope.() -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Tokens.textHigh, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            if (subtitle.isNotBlank()) {
                Text(subtitle, color = Tokens.textLow, fontSize = 11.sp)
            }
        }
        action()
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> "%.1f MB".format(bytes / (1L shl 20).toDouble())
    bytes >= 1L shl 10 -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

@Composable
private fun TrackRow(
    c: AppController,
    track: Track,
    index: Int,
    liked: Boolean,
    onPlay: () -> Unit,
    onLike: () -> Unit,
    onPlayNext: () -> Unit,
    onQueue: () -> Unit,
    playlists: List<Playlist>,
    onAddTo: (String) -> Unit,
    onRadio: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var menuOpen by remember { mutableStateOf(false) }
    val downloadedIds by c.downloadedIds.collectAsState()
    val downloadingIds by c.downloadingIds.collectAsState()
    val isDownloaded = track.id in downloadedIds
    val isDownloading = track.id in downloadingIds

    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (hovered) Tokens.panelHigh.copy(alpha = 0.6f) else Color.Transparent)
            .hoverable(interaction)
            .clickable(onClick = onPlay)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${index + 1}",
            color = Tokens.textLow,
            fontSize = 12.sp,
            modifier = Modifier.width(26.dp),
        )
        Cover(track.thumbnail, 42.dp, 6.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                color = Tokens.textHigh,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                track.artist,
                color = Tokens.textLow,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // O perfil de artista nao manda duracao nas musicas populares; nesse
        // caso e melhor omitir do que exibir um traco solto.
        if (track.durationSec > 0) {
            Text(
                formatTime(track.durationSec.toDouble()),
                color = Tokens.textLow,
                fontSize = 11.sp,
            )
        }
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = onLike, modifier = Modifier.size(32.dp)) {
            Icon(
                if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                "Curtir",
                tint = if (liked) Tokens.accent else Tokens.textLow,
                modifier = Modifier.size(16.dp),
            )
        }
        Tip("Tocar em seguida") {
            IconButton(onClick = onPlayNext, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.PlaylistPlay,
                    "Tocar em seguida",
                    tint = Tokens.textLow,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Tip("Adicionar à fila") {
            IconButton(onClick = onQueue, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.QueueMusic,
                    "Adicionar à fila",
                    tint = Tokens.textLow,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Box {
            Tip("Mais ações: playlist, rádio, download…") {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.MoreVert, "Mais ações", tint = Tokens.textLow, modifier = Modifier.size(16.dp))
                }
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text("Tocar em seguida") }, onClick = {
                    menuOpen = false; onPlayNext()
                })
                DropdownMenuItem(text = { Text("Adicionar à fila") }, onClick = {
                    menuOpen = false; onQueue()
                })
                onRadio?.let {
                    DropdownMenuItem(text = { Text("Iniciar rádio") }, onClick = {
                        menuOpen = false; it()
                    })
                }
                if (isDownloaded) {
                    DropdownMenuItem(text = { Text("Remover download") }, onClick = {
                        menuOpen = false; c.removeDownload(track.id)
                    })
                } else {
                    DropdownMenuItem(
                        text = { Text(if (isDownloading) "Baixando..." else "Baixar") },
                        enabled = !isDownloading,
                        onClick = { menuOpen = false; c.downloadTrack(track) },
                    )
                }
                HorizontalDivider(color = Tokens.line)
                if (playlists.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("Criar nova playlist") },
                        onClick = { menuOpen = false; c.askCreatePlaylistFor(track) },
                    )
                } else {
                    playlists.forEach { playlist ->
                        DropdownMenuItem(
                            text = { Text("Adicionar a ${playlist.name}") },
                            onClick = { menuOpen = false; onAddTo(playlist.id) },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Criar nova playlist") },
                        onClick = { menuOpen = false; c.askCreatePlaylistFor(track) },
                    )
                }
                onRemove?.let {
                    HorizontalDivider(color = Tokens.line)
                    DropdownMenuItem(text = { Text("Remover da playlist") }, onClick = {
                        menuOpen = false; it()
                    })
                }
            }
        }
    }
}

// ------------------------------------------------------------------ queue
@Composable
private fun QueuePanel(c: AppController, queue: List<Track>, onClose: () -> Unit) {
    val radio by c.player.radioActive.collectAsState()
    val queueTitle by c.player.queueTitle.collectAsState()
    val idx by c.player.index.collectAsState()
    val upcoming = if (idx >= 0) queue.size - idx - 1 else 0
    var dragIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    val density = LocalDensity.current
    var rowHeightPx by remember { mutableStateOf(with(density) { 46.dp.toPx() }) }

    Column(
        Modifier.width(300.dp).fillMaxHeight()
            .clip(RoundedCornerShape(14.dp))
            .background(Tokens.panelGradient),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (radio) "Fila • Rádio" else "Fila",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Tokens.textHigh,
            )
            Spacer(Modifier.width(8.dp))
            Text("${queue.size}", color = Tokens.textLow, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.Close, "Fechar", tint = Tokens.textLow, modifier = Modifier.size(16.dp))
            }
        }
        if (queueTitle.isNotBlank()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    queueTitle,
                    color = Tokens.textLow,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (radio) {
                    TextButton(onClick = { c.stopRadio() }) {
                        Text("Encerrar rádio", fontSize = 10.sp, color = Tokens.textLow)
                    }
                }
            }
        }
        HorizontalDivider(color = Tokens.line)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { c.clearQueueNext() }, enabled = upcoming > 0) {
                Text("Limpar próximas", fontSize = 11.sp, color = if (upcoming > 0) Tokens.textLow else Tokens.textLow.copy(alpha = 0.4f))
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { c.askSaveQueueAsPlaylist() }, enabled = queue.isNotEmpty()) {
                Icon(
                    Icons.AutoMirrored.Filled.PlaylistAdd,
                    null,
                    tint = if (queue.isNotEmpty()) Tokens.textLow else Tokens.textLow.copy(alpha = 0.4f),
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text("Salvar", fontSize = 11.sp, color = if (queue.isNotEmpty()) Tokens.textLow else Tokens.textLow.copy(alpha = 0.4f))
            }
        }
        HorizontalDivider(color = Tokens.line)
        if (queue.isEmpty()) {
            Text(
                "Nada na fila.",
                color = Tokens.textLow,
                fontSize = 12.sp,
                modifier = Modifier.padding(16.dp),
            )
        }
        LazyColumn(Modifier.weight(1f)) {
            itemsIndexed(queue, key = { i, t -> "q-$i-${t.id}" }) { i, track ->
                val dragging = dragIndex == i
                Box(
                    Modifier.fillMaxWidth()
                        .zIndex(if (dragging) 1f else 0f)
                        .offset { IntOffset(0, if (dragging) dragOffset.roundToInt() else 0) }
                        .onSizeChanged { rowHeightPx = it.height.toFloat() },
                ) {
                    QueueRow(
                        c = c,
                        track = track,
                        index = i,
                        isCurrent = i == idx,
                        dragModifier = Modifier.pointerInput(queue.size, i) {
                            detectDragGestures(
                                onDragStart = {
                                    dragIndex = i
                                    dragOffset = 0f
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragOffset += amount.y
                                },
                                onDragEnd = {
                                    val steps = (dragOffset / rowHeightPx).roundToInt()
                                    if (steps != 0) {
                                        c.moveInQueue(i, (i + steps).coerceIn(0, queue.lastIndex))
                                    }
                                    dragIndex = null
                                    dragOffset = 0f
                                },
                                onDragCancel = {
                                    dragIndex = null
                                    dragOffset = 0f
                                },
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun QueueRow(
    c: AppController,
    track: Track,
    index: Int,
    isCurrent: Boolean,
    dragModifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var menuOpen by remember { mutableStateOf(false) }
    val playlists by c.playlists.collectAsState()
    val downloadedIds by c.downloadedIds.collectAsState()
    val downloadingIds by c.downloadingIds.collectAsState()

    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(
                when {
                    isCurrent -> Tokens.panelHigh
                    hovered -> Color(0xFF16161A)
                    else -> Color.Transparent
                }
            )
            .hoverable(interaction)
            .clickable { c.playQueueAt(index) }
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.width(3.dp).height(30.dp).clip(RoundedCornerShape(2.dp))
                .background(if (isCurrent) Tokens.accent else Color.Transparent),
        )
        Spacer(Modifier.width(7.dp))
        Cover(track.thumbnail, 34.dp, 5.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                color = if (isCurrent) Tokens.accent else Tokens.textHigh,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                track.artist,
                color = Tokens.textLow,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            Modifier.size(24.dp).clip(CircleShape).then(dragModifier),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.DragHandle,
                "Arrastar para reordenar",
                tint = Tokens.textLow,
                modifier = Modifier.size(15.dp),
            )
        }
        Box {
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(20.dp)) {
                Icon(
                    Icons.Filled.MoreVert,
                    "Mais ações",
                    tint = Tokens.textLow,
                    modifier = Modifier.size(14.dp),
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Tocar agora") },
                    onClick = { menuOpen = false; c.playQueueAt(index) },
                )
                val downloaded = track.id in downloadedIds
                val downloading = track.id in downloadingIds
                DropdownMenuItem(
                    text = { Text(if (downloaded) "Remover download" else if (downloading) "Baixando..." else "Baixar") },
                    enabled = !downloading,
                    onClick = {
                        menuOpen = false
                        if (downloaded) c.removeDownload(track.id) else c.downloadTrack(track)
                    },
                )
                HorizontalDivider(color = Tokens.line)
                if (playlists.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("Criar nova playlist") },
                        onClick = { menuOpen = false; c.askCreatePlaylistFor(track) },
                    )
                } else {
                    playlists.forEach { playlist ->
                        DropdownMenuItem(
                            text = { Text("Adicionar a ${playlist.name}") },
                            onClick = { menuOpen = false; c.addToPlaylist(playlist.id, track) },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Criar nova playlist") },
                        onClick = { menuOpen = false; c.askCreatePlaylistFor(track) },
                    )
                }
                HorizontalDivider(color = Tokens.line)
                DropdownMenuItem(
                    text = { Text("Remover da fila") },
                    onClick = { menuOpen = false; c.removeFromQueue(index) },
                )
            }
        }
    }
}

// ------------------------------------------------------------------ now playing
@Composable
private fun NowPlayingBar(c: AppController, queueOpen: Boolean, onQueue: () -> Unit) {
    val track by c.player.current.collectAsState()
    val playing by c.player.playing.collectAsState()
    val busy by c.player.busy.collectAsState()
    val position by c.player.position.collectAsState()
    val duration by c.player.duration.collectAsState()
    val volume by c.player.volume.collectAsState()
    val shuffle by c.player.shuffle.collectAsState()
    val repeat by c.player.repeat.collectAsState()
    val likedIds by c.player.likedIds.collectAsState()
    val radioActive by c.player.radioActive.collectAsState()
    val radioLoading by c.player.radioLoading.collectAsState()
    val eqOn by c.player.eqEnabled.collectAsState()

    var drag by remember { mutableStateOf<Float?>(null) }
    var eqOpen by remember { mutableStateOf(false) }
    val total = if (duration > 0) duration else 1.0
    val fraction = ((drag ?: (position / total)).toFloat()).coerceIn(0f, 1f)

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Tokens.panelGradient)
            .height(94.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.width(320.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(track?.thumbnail ?: "", 56.dp, 8.dp)
            Spacer(Modifier.width(12.dp))
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(6.dp))
                    .clickable(enabled = track != null) { c.openNowPlaying() }
                    .padding(vertical = 4.dp),
            ) {
                Text(
                    track?.title ?: "Nada tocando",
                    color = Tokens.textHigh,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when {
                        busy -> "Carregando..."
                        radioActive && radioLoading -> "Rádio • carregando mais..."
                        radioActive -> "Rádio • ${track?.artist ?: "YouTube Music"}"
                        else -> track?.artist ?: "Escolha uma faixa"
                    },
                    color = if (busy || radioActive) Tokens.accent else Tokens.textLow,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            track?.let { current ->
                ToggleIconButton(
                    if (current.id in likedIds) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    if (current.id in likedIds) "Descurtir" else "Curtir",
                    active = current.id in likedIds,
                    onClick = { c.toggleLike(current) },
                    size = 32.dp,
                    iconSize = 16.dp,
                    tooltip = if (current.id in likedIds) "Remover curtida" else "Curtir",
                    activeColor = Tokens.accent,
                )
                AddToPlaylistButton(c, current)
                DownloadButton(c, current)
            }
        }

        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ToggleIconButton(
                    Icons.Filled.Shuffle,
                    "Aleatório",
                    active = shuffle,
                    onClick = { c.toggleShuffle() },
                )
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = { c.player.previous() }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.SkipPrevious, "Anterior", tint = Tokens.textHigh, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(Tokens.brandGradient)
                        .clickable(enabled = track != null) { c.player.togglePlay() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (busy) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Tokens.ink)
                    } else {
                        Crossfade(
                            targetState = playing,
                            animationSpec = tween(120),
                            label = "play-icon",
                        ) { isPlaying ->
                            Icon(
                                if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                if (isPlaying) "Pausar" else "Tocar",
                                tint = Tokens.ink,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { c.player.next() }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.SkipNext, "Próxima", tint = Tokens.textHigh, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(4.dp))
                ToggleIconButton(
                    if (repeat == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    "Repetir",
                    active = repeat != RepeatMode.OFF,
                    onClick = { c.player.cycleRepeat() },
                    tooltip = "Repetir: off / tudo / uma",
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatTime(drag?.times(total) ?: position), color = Tokens.textLow, fontSize = 10.sp)
                Spacer(Modifier.width(8.dp))
                Slider(
                    value = fraction,
                    onValueChange = { drag = it },
                    onValueChangeFinished = {
                        drag?.let { c.player.seekToFraction(it.toDouble()) }
                        drag = null
                    },
                    colors = openfySliderColors(),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(formatTime(duration), color = Tokens.textLow, fontSize = 10.sp)
            }
        }

        Row(
            Modifier.width(300.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToggleIconButton(
                Icons.Filled.Radio,
                if (radioActive) "Encerrar rádio (ativa)" else "Iniciar rádio da música",
                active = radioActive,
                onClick = { c.toggleCurrentTrackRadio() },
                size = 30.dp,
                iconSize = 17.dp,
            )
            ToggleIconButton(
                Icons.Filled.Tune,
                "Equalizador",
                active = eqOn,
                onClick = { eqOpen = true },
                iconSize = 18.dp,
            )
            Icon(Icons.AutoMirrored.Filled.VolumeUp, null, tint = Tokens.textLow, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Slider(
                value = volume / 130f,
                onValueChange = { c.player.setVolume((it * 130).toInt()) },
                colors = openfySliderColors(),
                modifier = Modifier.width(84.dp),
            )
            Tip("Tela do player") {
                IconButton(onClick = { c.openNowPlaying() }, modifier = Modifier.size(30.dp)) {
                    Icon(
                        Icons.Filled.KeyboardArrowUp,
                        "Tela do player",
                        tint = Tokens.textLow,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            ToggleIconButton(
                Icons.AutoMirrored.Filled.QueueMusic,
                "Fila",
                active = queueOpen,
                onClick = onQueue,
                iconSize = 18.dp,
            )
        }
    }
    if (eqOpen) EqualizerDialog(c) { eqOpen = false }
}

/** Botão de adicionar à playlist com menu de playlists. */
@Composable
private fun AddToPlaylistButton(
    c: AppController,
    track: Track,
    size: Dp = 32.dp,
    iconSize: Dp = 16.dp,
    tint: Color = Tokens.textLow,
) {
    var open by remember { mutableStateOf(false) }
    val playlists by c.playlists.collectAsState()
    Box {
        Tip("Adicionar à playlist") {
            IconButton(onClick = { if (playlists.isEmpty()) c.askCreatePlaylistFor(track) else open = true }, modifier = Modifier.size(size)) {
                Icon(
                    Icons.AutoMirrored.Filled.PlaylistAdd,
                    "Adicionar à playlist",
                    tint = tint,
                    modifier = Modifier.size(iconSize),
                )
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (playlists.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("Criar nova playlist") },
                    onClick = { open = false; c.askCreatePlaylistFor(track) },
                )
            } else {
                playlists.forEach { playlist ->
                    DropdownMenuItem(
                        text = { Text(playlist.name) },
                        onClick = { open = false; c.addToPlaylist(playlist.id, track) },
                    )
                }
                HorizontalDivider(color = Tokens.line)
                DropdownMenuItem(
                    text = { Text("Criar nova playlist") },
                    onClick = { open = false; c.askCreatePlaylistFor(track) },
                )
            }
        }
    }
}

/** Botão de baixar/remover download de uma faixa. */
@Composable
private fun DownloadButton(
    c: AppController,
    track: Track,
    size: Dp = 32.dp,
    iconSize: Dp = 16.dp,
    tint: Color = Tokens.textLow,
) {
    val downloadedIds by c.downloadedIds.collectAsState()
    val downloadingIds by c.downloadingIds.collectAsState()
    val downloaded = track.id in downloadedIds
    val downloading = track.id in downloadingIds
    Tip(
        when {
            downloading -> "Baixando..."
            downloaded -> "Remover download"
            else -> "Baixar"
        },
    ) {
        IconButton(
            onClick = { if (downloaded) c.removeDownload(track.id) else c.downloadTrack(track) },
            enabled = !downloading,
            modifier = Modifier.size(size),
        ) {
            when {
                downloading -> CircularProgressIndicator(
                    Modifier.size(iconSize),
                    strokeWidth = 2.dp,
                    color = Tokens.accent,
                )
                downloaded -> Icon(
                    Icons.Filled.DownloadDone,
                    "Remover download",
                    tint = Tokens.accent,
                    modifier = Modifier.size(iconSize),
                )
                else -> Icon(
                    Icons.Filled.Download,
                    "Baixar",
                    tint = tint,
                    modifier = Modifier.size(iconSize),
                )
            }
        }
    }
}

/** Tela cheia do player, no estilo "tocando agora". */
@Composable
private fun PlayerBackdrop(url: String, enabled: Boolean) {
    val fallback = Tokens.heroGradient
    val ink = Tokens.ink
    val palette by produceState<ArtworkCache.CoverPalette?>(
        initialValue = if (enabled) ArtworkCache.peekPalette(url) else null,
        url,
        enabled,
    ) {
        value = if (enabled && url.isNotBlank()) ArtworkCache.palette(url) else null
    }
    val brush = if (!enabled || palette == null) {
        fallback
    } else {
        remember(palette, ink) {
            val p = palette!!
            Brush.verticalGradient(
                0f to lerp(p.primary, ink, 0.38f),
                0.55f to lerp(p.secondary, ink, 0.68f),
                1f to ink,
            )
        }
    }
    Box(Modifier.fillMaxSize().background(brush))
}

@Composable
private fun NowPlayingScreen(c: AppController) {
    val track by c.player.current.collectAsState()
    val playing by c.player.playing.collectAsState()
    val busy by c.player.busy.collectAsState()
    val position by c.player.position.collectAsState()
    val duration by c.player.duration.collectAsState()
    val volume by c.player.volume.collectAsState()
    val shuffle by c.player.shuffle.collectAsState()
    val repeat by c.player.repeat.collectAsState()
    val likedIds by c.player.likedIds.collectAsState()
    val radioActive by c.player.radioActive.collectAsState()
    val queued by c.queueOpen.collectAsState()
    val queueTitle by c.player.queueTitle.collectAsState()
    val lyrics by c.lyrics.collectAsState()
    val lyricsLoading by c.lyricsLoading.collectAsState()
    val sleepUntil by c.sleepUntil.collectAsState()
    val eqOn by c.player.eqEnabled.collectAsState()
    val layout by c.playerLayout.collectAsState()
    val coverGradient by c.coverGradient.collectAsState()

    var drag by remember { mutableStateOf<Float?>(null) }
    var eqOpen by remember { mutableStateOf(false) }
    var lyricsOpen by remember { mutableStateOf(false) }
    var sleepOpen by remember { mutableStateOf(false) }
    LaunchedEffect(track?.id, lyricsOpen) {
        if (lyricsOpen && track != null) c.loadLyrics(track!!)
    }
    val total = if (duration > 0) duration else 1.0
    val fraction = ((drag ?: (position / total)).toFloat()).coerceIn(0f, 1f)
    val liked = track?.id in likedIds

    BoxWithConstraints(Modifier.fillMaxSize()) {
        PlayerBackdrop(track?.thumbnail.orEmpty(), coverGradient)
        val coverSize = when {
            maxHeight > 760.dp -> 320.dp
            maxHeight > 680.dp -> 270.dp
            else -> 210.dp
        }
        val sideCover = minOf(coverSize, maxWidth * 0.34f)
        Column(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Tip("Fechar player") {
                    IconButton(onClick = { c.closeNowPlaying() }) {
                        Icon(
                            Icons.Filled.KeyboardArrowDown,
                            "Fechar player",
                            tint = Tokens.textHigh,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("TOCANDO AGORA", fontSize = 11.sp, letterSpacing = 1.5.sp, color = Tokens.textLow)
                    Text(
                        queueTitle.ifBlank {
                            track?.album?.ifBlank { "YouTube Music" } ?: "Openfy"
                        },
                        fontSize = 12.sp,
                        color = Tokens.textHigh,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.weight(1f))
                ToggleIconButton(
                    Icons.AutoMirrored.Filled.QueueMusic,
                    "Fila",
                    active = queued,
                    onClick = { c.closeNowPlaying(); c.toggleQueue() },
                    size = 40.dp,
                    iconSize = 22.dp,
                )
            }

            if (lyricsOpen) {
                LyricsPanel(
                    track = track,
                    lyrics = lyrics,
                    loading = lyricsLoading,
                    position = drag?.times(total) ?: position,
                    modifier = Modifier.weight(1f),
                )
            } else if (layout == PlayerLayout.SIDE) {
                Row(
                    Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Cover(
                        track?.thumbnail ?: "",
                        sideCover,
                        18.dp,
                    )
                    Spacer(Modifier.width(40.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            track?.title ?: "Nada tocando",
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            color = Tokens.textHigh,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            track?.artist ?: "",
                            fontSize = 15.sp,
                            color = Tokens.textLow,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            } else {
                Spacer(Modifier.weight(1f))
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Cover(track?.thumbnail ?: "", coverSize, 18.dp)
                    Spacer(Modifier.height(24.dp))
                    Text(
                        track?.title ?: "Nada tocando",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = Tokens.textHigh,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        track?.artist ?: "",
                        fontSize = 14.sp,
                        color = Tokens.textLow,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.weight(1f))
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(formatTime(drag?.times(total) ?: position), color = Tokens.textLow, fontSize = 11.sp)
                Slider(
                    value = fraction,
                    onValueChange = { drag = it },
                    onValueChangeFinished = {
                        drag?.let { c.player.seekToFraction(it.toDouble()) }
                        drag = null
                    },
                    colors = openfySliderColors(),
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                )
                Text(formatTime(duration), color = Tokens.textLow, fontSize = 11.sp)
            }

            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToggleIconButton(
                    Icons.Filled.Shuffle,
                    "Aleatório",
                    active = shuffle,
                    onClick = { c.toggleShuffle() },
                    size = 40.dp,
                    iconSize = 20.dp,
                )
                Spacer(Modifier.width(14.dp))
                IconButton(onClick = { c.player.previous() }, modifier = Modifier.size(46.dp)) {
                    Icon(Icons.Filled.SkipPrevious, "Anterior", tint = Tokens.textHigh, modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.width(16.dp))
                Box(
                    Modifier.size(64.dp).clip(CircleShape).background(Tokens.brandGradient)
                        .clickable(enabled = track != null) { c.player.togglePlay() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (busy) {
                        CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.dp, color = Tokens.ink)
                    } else {
                        Crossfade(
                            targetState = playing,
                            animationSpec = tween(120),
                            label = "play-icon-full",
                        ) { isPlaying ->
                            Icon(
                                if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                if (isPlaying) "Pausar" else "Tocar",
                                tint = Tokens.ink,
                                modifier = Modifier.size(36.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.width(16.dp))
                IconButton(onClick = { c.player.next() }, modifier = Modifier.size(46.dp)) {
                    Icon(Icons.Filled.SkipNext, "Próxima", tint = Tokens.textHigh, modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.width(14.dp))
                ToggleIconButton(
                    if (repeat == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    "Repetir",
                    active = repeat != RepeatMode.OFF,
                    onClick = { c.player.cycleRepeat() },
                    size = 40.dp,
                    iconSize = 20.dp,
                    tooltip = "Repetir: off / tudo / uma",
                )
            }

            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                track?.let { current ->
                    ToggleIconButton(
                        if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        if (liked) "Descurtir" else "Curtir",
                        active = liked,
                        onClick = { c.toggleLike(current) },
                        size = 38.dp,
                        iconSize = 22.dp,
                        tooltip = if (liked) "Remover curtida" else "Curtir",
                        activeColor = Tokens.accent,
                    )
                    AddToPlaylistButton(c, current, size = 38.dp, iconSize = 22.dp)
                    DownloadButton(c, current, size = 38.dp, iconSize = 22.dp)
                }
                ToggleIconButton(
                    Icons.Filled.Lyrics,
                    "Letras",
                    active = lyricsOpen,
                    onClick = { lyricsOpen = !lyricsOpen },
                    size = 38.dp,
                    iconSize = 22.dp,
                )
                Box {
                    ToggleIconButton(
                        Icons.Filled.Snooze,
                        "Timer",
                        active = sleepUntil != null,
                        onClick = { sleepOpen = true },
                        size = 38.dp,
                        iconSize = 22.dp,
                        tooltip = "Timer para dormir",
                    )
                    DropdownMenu(expanded = sleepOpen, onDismissRequest = { sleepOpen = false }) {
                        listOf(15, 30, 45, 60).forEach { minutes ->
                            DropdownMenuItem(
                                text = { Text("$minutes min") },
                                onClick = { sleepOpen = false; c.setSleepTimer(minutes) },
                            )
                        }
                        HorizontalDivider(color = Tokens.line)
                        DropdownMenuItem(
                            text = { Text("Desligar") },
                            onClick = { sleepOpen = false; c.setSleepTimer(null) },
                        )
                    }
                }
                ToggleIconButton(
                    Icons.Filled.Radio,
                    if (radioActive) "Encerrar rádio (ativa)" else "Iniciar rádio da música",
                    active = radioActive,
                    onClick = { c.toggleCurrentTrackRadio() },
                    size = 38.dp,
                    iconSize = 22.dp,
                )
                ToggleIconButton(
                    Icons.Filled.Tune,
                    "Equalizador",
                    active = eqOn,
                    onClick = { eqOpen = true },
                    size = 38.dp,
                    iconSize = 22.dp,
                )
                Spacer(Modifier.width(18.dp))
                Icon(Icons.AutoMirrored.Filled.VolumeUp, null, tint = Tokens.textLow, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Slider(
                    value = volume / 130f,
                    onValueChange = { c.player.setVolume((it * 130).toInt()) },
                    colors = openfySliderColors(),
                    modifier = Modifier.width(130.dp),
                )
            }
        }
    }
    if (eqOpen) EqualizerDialog(c) { eqOpen = false }
}

@Composable
private fun LyricsPanel(
    track: Track?,
    lyrics: List<LyricLine>,
    loading: Boolean,
    position: Double,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            track?.title ?: "",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Tokens.textHigh,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            track?.artist ?: "",
            fontSize = 12.sp,
            color = Tokens.textLow,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(10.dp))
        when {
            loading -> Box(
                Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Tokens.accent)
            }
            lyrics.isEmpty() -> Box(
                Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text("Letra não encontrada.", color = Tokens.textLow, fontSize = 13.sp)
            }
            else -> {
                val listState = rememberLazyListState()
                val positionMs = (position * 1000).toLong()
                val current = lyrics.indexOfLast { it.timeMs in 0..positionMs }.coerceAtLeast(0)
                LaunchedEffect(current) {
                    if (current in lyrics.indices) listState.animateScrollToItem(current)
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    contentPadding = PaddingValues(vertical = 12.dp),
                ) {
                    itemsIndexed(lyrics, key = { index, _ -> index }) { index, line ->
                        val active = index == current && line.timeMs >= 0
                        Text(
                            line.text,
                            color = if (active) {
                                Tokens.accent
                            } else {
                                Tokens.textLow.copy(alpha = if (line.timeMs >= 0) 0.9f else 0.7f)
                            },
                            fontSize = if (active) 18.sp else 15.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                                .padding(horizontal = 60.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EqualizerDialog(c: AppController, onClose: () -> Unit) {
    val enabled by c.player.eqEnabled.collectAsState()
    val gains by c.player.eqGains.collectAsState()

    AlertDialog(
        onDismissRequest = onClose,
        modifier = Modifier.width(460.dp),
        containerColor = Tokens.panelHigh,
        titleContentColor = Tokens.textHigh,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Tune, null, tint = Tokens.accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Equalizador", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Ativar", color = Tokens.textHigh, fontSize = 13.sp)
                    Spacer(Modifier.weight(1f))
                    Switch(checked = enabled, onCheckedChange = { c.setEqEnabled(it) }, colors = openfySwitchColors())
                }
                Spacer(Modifier.height(6.dp))
                Player.EQ_FREQS.forEachIndexed { index, freq ->
                    val label = if (freq >= 1000) "${freq / 1000} kHz" else "$freq Hz"
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label, color = Tokens.textLow, fontSize = 11.sp, modifier = Modifier.width(56.dp))
                        Slider(
                            value = gains.getOrElse(index) { 0 }.toFloat(),
                            onValueChange = { c.setEqGain(index, it.roundToInt()) },
                            valueRange = -12f..12f,
                            colors = openfySliderColors(),
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        )
                        Text(
                            "${if (gains.getOrElse(index) { 0 } > 0) "+" else ""}${gains.getOrElse(index) { 0 }}",
                            color = Tokens.textLow,
                            fontSize = 11.sp,
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(34.dp),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Player.EQ_PRESETS.keys.forEach { name ->
                        TextButton(onClick = { c.applyEqPreset(name) }) {
                            Text(name, color = Tokens.accent, fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) {
                Text("Fechar", color = Tokens.accent, fontSize = 13.sp)
            }
        },
    )
}
