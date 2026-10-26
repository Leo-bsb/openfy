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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openfy.core.AppController
import com.openfy.core.Artist
import com.openfy.core.ArtistPage
import com.openfy.core.BuildInfo
import com.openfy.core.CollectionKind
import com.openfy.core.MediaCard
import com.openfy.core.MediaKind
import com.openfy.core.Playlist
import com.openfy.core.PlaylistPrompt
import com.openfy.core.RepeatMode
import com.openfy.core.Screen
import com.openfy.core.SearchMode
import com.openfy.core.Track
import com.openfy.core.formatTime
import com.openfy.data.LyricLine
import com.openfy.player.Player
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

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

/** Rotulo ao passar o mouse; usado para explicar icones (ex.: DJ/BPM). */
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
        tooltipPlacement = TooltipPlacement.CursorPoint(offset = DpOffset(0.dp, 16.dp)),
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
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Tip(tooltip) {
        Box(
            Modifier.size(size)
                .clip(CircleShape)
                .background(
                    when {
                        active -> Tokens.accent
                        hovered && enabled -> Tokens.panelHigh
                        else -> Color.Transparent
                    }
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
                    active -> Tokens.ink
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

    OpenfyTheme {
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
                    modifier = Modifier.width(320.dp),
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
    var url by remember { mutableStateOf("") }

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
                Text("Importar / backup", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
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
                        modifier = Modifier.weight(1f),
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
        c.search(q)
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
        Screen.Settings -> SettingsScreen(c)
        Screen.History -> {
            val tracks by c.history.collectAsState()
            TrackCollection(c, "Histórico", tracks, emptyList())
        }
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
    val stats by c.stats.collectAsState()

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
        stats?.takeIf { it.totalPlays > 0 }?.let { s ->
            item {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 22.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    StatTile("Execuções", "${s.totalPlays}", Modifier.weight(1f))
                    StatTile("Horas ouvidas", formatHours(s.totalSeconds), Modifier.weight(1f))
                    StatTile(
                        "Artista top",
                        s.topArtists.firstOrNull()?.first ?: "—",
                        Modifier.weight(1f),
                    )
                }
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
                        val index = recent.indexOf(track)
                        CoverCard(track.title, track.artist, track.thumbnail) {
                            c.playTracks(recent, index)
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
                        CoverCard(track.title, track.artist, track.thumbnail) {
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

@Composable
private fun CoverCard(title: String, subtitle: String, art: String, onClick: () -> Unit) {
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
        Cover(art, 140.dp, 8.dp)
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
                            TopResultCard(results.first()) { c.playSearchTrack(results.first()) }
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
private fun TopResultCard(track: Track, onPlay: () -> Unit) {
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
                    TextButton(
                        onClick = { meta?.let { sharePlaylist(c, id, it.name) } },
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
                    onRemove = { c.removeFromPlaylist(id, track.id) },
                )
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

@Composable
private fun SettingsScreen(c: AppController) {
    val quality by c.audioQuality.collectAsState()
    val crossfade by c.player.crossfadeSeconds.collectAsState()
    val transition by c.player.transitionStyle.collectAsState()
    val dj by c.player.djMode.collectAsState()
    val auto by c.autoDownload.collectAsState()
    val closeToTray by c.closeToTray.collectAsState()
    val downloadedSize by c.downloadsSize.collectAsState()
    var eqOpen by remember { mutableStateOf(false) }

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
                    onValueChange = { c.player.setCrossfade(it.roundToInt()) },
                    valueRange = 0f..12f,
                    steps = 11,
                    colors = openfySliderColors(),
                    modifier = Modifier.width(220.dp),
                )
            }
            SettingsRow(
                "Estilo da transição",
                if (transition == Player.TRANSITION_BLEND) {
                    "DJ blend: corta os graves da faixa que sai e deixa os graves/vocal da próxima aparecerem. É uma aproximação de mashup, não separa vocais."
                } else {
                    "Crossfade equal-power: as duas faixas ficam audíveis no meio, sem buraco de volume."
                },
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SearchChip("Crossfade", transition == Player.TRANSITION_CROSSFADE) {
                        c.player.setTransitionStyle(Player.TRANSITION_CROSSFADE)
                    }
                    SearchChip("DJ blend", transition == Player.TRANSITION_BLEND) {
                        c.player.setTransitionStyle(Player.TRANSITION_BLEND)
                    }
                }
            }
            SettingsRow(
                "Modo DJ (BPM)",
                "Reordena as próximas faixas por andamento (metadata do Deezer).",
            ) {
                Switch(
                    checked = dj,
                    onCheckedChange = { c.player.setDjMode(it) },
                    colors = openfySwitchColors(),
                )
            }
            SettingsRow("Equalizador", "5 bandas + presets") {
                TextButton(onClick = { eqOpen = true }) {
                    Text("Abrir", color = Tokens.accent)
                }
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
        Box {
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Add, "Adicionar", tint = Tokens.textLow, modifier = Modifier.size(16.dp))
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
                playlists.forEach { playlist ->
                    DropdownMenuItem(
                        text = { Text("Adicionar a ${playlist.name}") },
                        onClick = { menuOpen = false; onAddTo(playlist.id) },
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
    val idx by c.player.index.collectAsState()
    val upcoming = if (idx >= 0) queue.size - idx - 1 else 0

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
            itemsIndexed(queue, key = { i, t -> "$i-${t.id}" }) { i, track ->
                QueueRow(c, track, i, i == idx, i > 0, i < queue.lastIndex)
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
    canUp: Boolean,
    canDown: Boolean,
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
        IconButton(onClick = { c.moveInQueue(index, index - 1) }, enabled = canUp, modifier = Modifier.size(20.dp)) {
            Icon(
                Icons.Filled.KeyboardArrowUp,
                "Mover para cima",
                tint = if (canUp) Tokens.textLow else Tokens.textLow.copy(alpha = 0.3f),
                modifier = Modifier.size(14.dp),
            )
        }
        IconButton(onClick = { c.moveInQueue(index, index + 1) }, enabled = canDown, modifier = Modifier.size(20.dp)) {
            Icon(
                Icons.Filled.KeyboardArrowDown,
                "Mover para baixo",
                tint = if (canDown) Tokens.textLow else Tokens.textLow.copy(alpha = 0.3f),
                modifier = Modifier.size(14.dp),
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
                        text = { Text("Nova playlist") },
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
                        text = { Text("Nova playlist") },
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
    val dj by c.player.djMode.collectAsState()
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
                ToggleIconButton(
                    Icons.Filled.AutoAwesome,
                    "Modo DJ (BPM)",
                    active = dj,
                    onClick = { c.player.toggleDj() },
                    tooltip = "Modo DJ — próximas faixas por BPM",
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
            if (radioActive) {
                IconButton(onClick = { c.stopRadio() }, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Filled.Radio, "Encerrar rádio", tint = Tokens.accent, modifier = Modifier.size(17.dp))
                }
            }
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
            IconButton(onClick = { open = true }, modifier = Modifier.size(size)) {
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
                    text = { Text("Nova playlist") },
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
                    text = { Text("Nova playlist") },
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
    val lyrics by c.lyrics.collectAsState()
    val lyricsLoading by c.lyricsLoading.collectAsState()
    val sleepUntil by c.sleepUntil.collectAsState()
    val dj by c.player.djMode.collectAsState()
    val eqOn by c.player.eqEnabled.collectAsState()

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

    BoxWithConstraints(Modifier.fillMaxSize().background(Tokens.heroGradient)) {
        val coverSize = when {
            maxHeight > 760.dp -> 320.dp
            maxHeight > 680.dp -> 270.dp
            else -> 210.dp
        }
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
                        track?.album?.ifBlank { "YouTube Music" } ?: "Openfy",
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
                ToggleIconButton(
                    Icons.Filled.AutoAwesome,
                    "Modo DJ (BPM)",
                    active = dj,
                    onClick = { c.player.toggleDj() },
                    size = 40.dp,
                    iconSize = 20.dp,
                    tooltip = "Modo DJ — próximas faixas por BPM",
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
                if (radioActive) {
                    IconButton(onClick = { c.stopRadio() }, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Filled.Radio, "Encerrar rádio", tint = Tokens.accent, modifier = Modifier.size(22.dp))
                    }
                }
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
                    Switch(checked = enabled, onCheckedChange = { c.player.setEqEnabled(it) }, colors = openfySwitchColors())
                }
                Spacer(Modifier.height(6.dp))
                Player.EQ_FREQS.forEachIndexed { index, freq ->
                    val label = if (freq >= 1000) "${freq / 1000} kHz" else "$freq Hz"
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label, color = Tokens.textLow, fontSize = 11.sp, modifier = Modifier.width(56.dp))
                        Slider(
                            value = gains.getOrElse(index) { 0 }.toFloat(),
                            onValueChange = { c.player.setEqGain(index, it.roundToInt()) },
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
                        TextButton(onClick = { c.player.applyPreset(name) }) {
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
