package com.openfy.core

import java.nio.file.Path

/**
 * Pastas graváveis do usuário. Ficam fora da pasta de instalação para que
 * upgrades (e rollbacks) do instalador nunca apaguem banco, capas ou downloads.
 */
object AppPaths {

    private fun localAppData(): Path =
        Path.of(System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"))

    /** Dados atuais: %LOCALAPPDATA%\OpenfyData */
    val home: Path by lazy { localAppData().resolve("OpenfyData") }

    /** Local antigo (instalação + dados): %LOCALAPPDATA%\Openfy */
    val legacyHome: Path by lazy { localAppData().resolve("Openfy") }
}
