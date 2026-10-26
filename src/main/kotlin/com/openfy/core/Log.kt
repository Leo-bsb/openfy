package com.openfy.core

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * Log de inicializacao em %LOCALAPPDATA%\OpenfyData\openfy.log.
 * Existe porque o app empacotado nao tem console: sem isso, falha de mpv
 * ou de extracao vira uma janela silenciosa.
 */
object Log {

    private val file: Path by lazy {
        val dir = AppPaths.home
        runCatching { Files.createDirectories(dir) }
        dir.resolve("openfy.log")
    }

    @Synchronized
    fun write(message: String) {
        runCatching {
            val line = "[%s] %s%n".format(
                java.time.LocalTime.now().withNano(0).toString(),
                message,
            )
            Files.writeString(
                file,
                line,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND,
            )
        }
        println(message)
    }

    @Synchronized
    fun rotate() {
        runCatching {
            val previous = file.resolveSibling(file.fileName.toString() + ".1")
            Files.deleteIfExists(previous)
            if (Files.exists(file)) {
                Files.move(
                    file,
                    previous,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                )
            }
        }
    }

    @Synchronized
    fun clear() {
        runCatching { Files.deleteIfExists(file) }
    }
}
