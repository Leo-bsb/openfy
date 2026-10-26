package com.openfy.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.Dp

/** Logo (assets/logo.png) usada na sidebar e no player. */
object Branding {

    val logo: ImageBitmap? by lazy {
        runCatching {
            val stream = Branding::class.java.classLoader.getResourceAsStream("logo.png")
                ?: return@runCatching null
            val bytes = stream.use { it.readBytes() }
            org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()
        }.getOrNull()
    }

    fun awtIcon(): java.awt.Image? = runCatching {
        Branding::class.java.classLoader.getResourceAsStream("logo.png")?.use {
            javax.imageio.ImageIO.read(it)
        }
    }.getOrNull()
}

@Composable
fun Logo(size: Dp, modifier: Modifier = Modifier) {
    Branding.logo?.let { image ->
        Image(image, "Openfy", modifier.size(size))
    }
}
