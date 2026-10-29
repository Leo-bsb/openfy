package com.openfy

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.openfy.core.AppController
import com.openfy.core.AppPaths
import com.openfy.core.Log
import com.openfy.data.Db
import com.openfy.data.Downloader
import com.openfy.data.Media
import com.openfy.data.StreamServer
import com.openfy.data.YouTubeEngine
import com.openfy.platform.MediaKeys
import com.openfy.platform.Tray
import com.openfy.platform.applyDarkTitleBar
import com.openfy.player.Mpv
import com.openfy.player.Player
import com.openfy.ui.Branding
import com.openfy.ui.OpenfyApp
import com.openfy.ui.openfyKeyHandler
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.awt.Dimension
import java.awt.Frame
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

fun main() {
    // Dados em pasta separada da instalacao: um upgrade/rollback do MSI nao
    // pode apagar o banco, capas ou downloads.
    val home = AppPaths.home
    val legacyHome = AppPaths.legacyHome
    Files.createDirectories(home)
    migrateLegacyData(legacyHome, home)

    // Instancia unica: se ja houver uma aberta, foca a janela existente.
    val lockChannel = runCatching {
        FileChannel.open(
            home.resolve("openfy.lock"),
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
        )
    }.getOrNull()
    val lock = runCatching { lockChannel?.tryLock() }.getOrNull()
    if (lockChannel != null && lock == null) {
        runCatching {
            val hwnd = com.sun.jna.platform.win32.User32.INSTANCE.FindWindow(null, "Openfy")
            if (hwnd != null) {
                com.sun.jna.platform.win32.User32.INSTANCE.ShowWindow(hwnd, 9)
                com.sun.jna.platform.win32.User32.INSTANCE.SetForegroundWindow(hwnd)
            }
        }
        Log.write("outra instancia ja esta aberta; focando a janela existente")
        return
    }

    Log.rotate()
    Log.write("iniciando openfy; home=$home")
    Thread.setDefaultUncaughtExceptionHandler { thread, error ->
        Log.write("CRASH ${thread.name}: ${error.stackTraceToString()}")
    }

    val resources = System.getProperty("compose.application.resources.dir")
    Log.write("resources.dir=$resources user.dir=${System.getProperty("user.dir")}")

    val mpvPath = Mpv.locate() ?: Path.of("D:\\scripts\\openfy\\.tools\\mpv\\mpv.exe")
    Log.write("mpv encontrado em $mpvPath")
    if (!Files.isRegularFile(mpvPath)) {
        Log.write("ERRO: mpv.exe nao encontrado; coloque mpv.exe em resources-dist/windows/ ou no PATH")
        return
    }

    val db = Db(home.resolve("openfy.db"))
    Log.write("banco aberto")
    val youtube = YouTubeEngine()
    val downloader = Downloader(youtube, home.resolve("downloads"))
    val stream = StreamServer { id ->
        db.downloadPath(id)?.let { path ->
            val local = Path.of(path)
            if (Files.isRegularFile(local)) Media.Local(local) else null
        } ?: youtube.media(id)
    }
    stream.start()
    Log.write("servidor de stream na porta ${stream.port}")

    val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, error ->
            Log.write("CRASH coroutine: ${error.stackTraceToString()}")
        },
    )
    val mpv = Mpv(mpvPath, home.resolve("mpv.log"))
    val mpvB = Mpv(mpvPath, home.resolve("mpv-b.log"), "-b")
    val player = Player(mpv, mpvB, stream, db, scope)
    val controller = AppController(db, youtube, player, downloader, scope)
    player.start()
    Log.write("mpv iniciado: processo=${mpv.processId()}")
    player.restore()
    controller.restoreLastScreen()
    // Hooks de diagnostico (nao usados no fluxo normal).
    if (System.getenv("OPENFY_OPEN_PLAYER") == "1") controller.openNowPlaying()
    System.getenv("OPENFY_AUTOPLAY")?.takeIf { it.isNotBlank() }?.let { id ->
        controller.playTracks(listOf(com.openfy.core.Track(id, "Teste", "Openfy")))
    }

    val stopping = AtomicBoolean(false)
    val quit = AtomicBoolean(false)
    fun shutdown() {
        if (!stopping.compareAndSet(false, true)) return
        Log.write("encerrando")
        runCatching { player.shutdown() }
        runCatching { controller.shutdown() }
        runCatching { stream.stop() }
        runCatching { scope.cancel() }
        runCatching { youtube.close() }
        runCatching { db.backupTo(home.resolve("openfy-backup.db")) }
        runCatching { db.close() }
        runCatching { lock?.release() }
        runCatching { lockChannel?.close() }
    }

    Runtime.getRuntime().addShutdownHook(Thread { shutdown() })

    fun readBounds(): IntArray? {
        val raw = db.get("window.bounds") ?: return null
        val parts = raw.split(';').mapNotNull { it.toIntOrNull() }
        return if (parts.size == 4) parts.toIntArray() else null
    }

    application {
        val screen = java.awt.Toolkit.getDefaultToolkit().screenSize
        val defaultWidth = minOf(1220, screen.width - 80).coerceAtLeast(980)
        val defaultHeight = minOf(790, screen.height - 160).coerceAtLeast(620)
        val saved = readBounds()
        val state = rememberWindowState(
            width = (saved?.get(2) ?: defaultWidth).dp,
            height = (saved?.get(3) ?: defaultHeight).dp,
            position = if (saved != null) {
                WindowPosition(saved[0].coerceAtLeast(0).dp, saved[1].coerceAtLeast(0).dp)
            } else {
                WindowPosition(Alignment.Center)
            },
        )

        var windowRef: ComposeWindow? = null
        val trayActive = remember { mutableStateOf(false) }

        fun saveBounds() {
            windowRef?.let { w ->
                runCatching { db.put("window.bounds", "${w.x};${w.y};${w.width};${w.height}") }
            }
        }
        fun showWindow() {
            windowRef?.let { w ->
                w.isVisible = true
                w.state = Frame.NORMAL
                w.toFront()
            }
        }
        fun hideWindow() {
            windowRef?.isVisible = false
        }

        val mediaKeys = remember {
            MediaKeys(
                onPlayPause = { player.togglePlay() },
                onNext = { player.next() },
                onPrevious = { player.previous() },
            )
        }
        val tray = remember {
            Tray(
                onPlayPause = { player.togglePlay() },
                onNext = { player.next() },
                onPrevious = { player.previous() },
                onShow = { showWindow() },
                onExit = {
                    quit.set(true)
                    saveBounds()
                    exitApplication()
                },
            )
        }

        DisposableEffect(Unit) {
            trayActive.value = tray.install()
            mediaKeys.start()
            onDispose {
                tray.dispose()
                mediaKeys.stop()
            }
        }

        val currentTrack by player.current.collectAsState()
        LaunchedEffect(currentTrack?.id) {
            tray.setTooltip(currentTrack?.let { "${it.title} — ${it.artist}" } ?: "Openfy")
        }

        Window(
            onCloseRequest = {
                val closeToTray = runCatching {
                    db.get("window.closeToTray")?.toBooleanStrict()
                }.getOrNull() ?: true
                if (trayActive.value && !quit.get() && closeToTray) {
                    // Fechar minimiza para a bandeja; o audio continua.
                    hideWindow()
                    tray.notify(
                        "Openfy",
                        "Continua tocando na bandeja. Clique no ícone para voltar.",
                    )
                } else {
                    saveBounds()
                    exitApplication()
                }
            },
            title = "Openfy",
            state = state,
            icon = Branding.logo?.let { BitmapPainter(it) },
            onPreviewKeyEvent = { openfyKeyHandler(controller)(it) },
        ) {
            windowRef = window
            LaunchedEffect(Unit) {
                window.minimumSize = Dimension(980, 620)
                applyDarkTitleBar("Openfy")
            }
            OpenfyApp(
                controller,
                onExit = {
                    quit.set(true)
                    saveBounds()
                    exitApplication()
                },
            )
        }
    }

    // A aplicacao nao termina sozinha enquanto o dispatcher HTTP do Ktor
    // estiver vivo; sem isso o processo ficava aberto depois de fechar a janela.
    shutdown()
    exitProcess(0)
}

/**
 * Uma vez so: traz os dados da pasta antiga (que tambem era a pasta de
 * instalacao) para a pasta de dados separada. Nunca sobrescreve dados novos.
 */
private fun migrateLegacyData(legacyHome: Path, home: Path) {
    if (!Files.isDirectory(legacyHome)) return
    if (Files.exists(home.resolve("openfy.db"))) return
    val migrated = mutableListOf<String>()
    runCatching {
        for (name in listOf("openfy.db", "openfy.db-wal", "openfy.db-shm")) {
            val source = legacyHome.resolve(name)
            if (!Files.isRegularFile(source)) continue
            runCatching {
                Files.copy(source, home.resolve(name), StandardCopyOption.REPLACE_EXISTING)
                migrated += name
            }.onFailure { Log.write("migracao: falha ao copiar $name: ${it.message}") }
        }
        for (name in listOf("artwork", "downloads", "covers")) {
            val source = legacyHome.resolve(name)
            val target = home.resolve(name)
            if (!Files.isDirectory(source) || Files.exists(target)) continue
            runCatching {
                Files.move(source, target)
                migrated += "$name/"
            }.onFailure {
                // Pasta em uso (antena/arquivo aberto): copia em vez de mover.
                runCatching {
                    copyDirectory(source, target)
                    migrated += "$name/ (copiado)"
                }.onFailure { copyError -> Log.write("migracao: falha em $name: ${copyError.message}") }
            }
        }
        if (migrated.isNotEmpty()) {
            Log.write("dados migrados de $legacyHome: ${migrated.joinToString()}")
        }
    }.onFailure { Log.write("migracao: erro inesperado: ${it.stackTraceToString()}") }
}

private fun copyDirectory(source: Path, target: Path) {
    Files.walk(source).use { paths ->
        paths.forEach { path ->
            val destination = target.resolve(source.relativize(path).toString())
            if (Files.isDirectory(path)) {
                Files.createDirectories(destination)
            } else {
                Files.createDirectories(destination.parent)
                Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }
}
