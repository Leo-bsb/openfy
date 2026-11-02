package com.openfy.core

import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

/**
 * Reduz avatar/capa antes de subir para o Storage. O plano gratuito do
 * Supabase tem 1 GB de arquivos; uma foto de celular de 8 MB gasta o espaco
 * de ~32 capas boas. Tudo que e grande fica no aparelho, na nuvem vai so o
 * necessario para sincronizar.
 */
object ImageTools {

    const val MAX_PROFILE_BYTES = 350_000
    const val MAX_COVER_BYTES = 450_000

    fun compressForUpload(
        source: File,
        maxDim: Int = 1024,
        maxBytes: Int = MAX_COVER_BYTES,
    ): File {
        val original = runCatching { ImageIO.read(source) }.getOrNull() ?: return source
        val scale = minOf(1.0, maxDim.toDouble() / maxOf(original.width, original.height))
        val width = (original.width * scale).toInt().coerceAtLeast(1)
        val height = (original.height * scale).toInt().coerceAtLeast(1)
        val canvas = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = canvas.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g.color = Color(0x0B1312)
            g.fillRect(0, 0, width, height)
            g.drawImage(original, 0, 0, width, height, null)
        } finally {
            g.dispose()
        }

        val target = File.createTempFile("openfy-upload-", ".jpg", source.parentFile ?: null)
        var quality = 0.88f
        while (quality >= 0.45f) {
            val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
            try {
                ImageIO.createImageOutputStream(target).use { out ->
                    writer.output = out
                    val params = writer.defaultWriteParam.apply {
                        compressionMode = ImageWriteParam.MODE_EXPLICIT
                        compressionQuality = quality
                    }
                    writer.write(null, IIOImage(canvas, null, null), params)
                }
            } finally {
                writer.dispose()
            }
            if (target.length() <= maxBytes) break
            quality -= 0.08f
        }
        Log.write(
            "imagem: ${source.name} ${source.length() / 1024}KB -> " +
                "${target.length() / 1024}KB (${width}x$height)",
        )
        target.deleteOnExit()
        return target
    }

    fun bytes(file: File): ByteArray = Files.readAllBytes(file.toPath())
}
