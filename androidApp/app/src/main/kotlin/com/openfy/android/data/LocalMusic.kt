package com.openfy.android.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.openfy.core.Track

/**
 * Musicas que ja estao no aparelho (MediaStore). Nada e copiado nem enviado:
 * a faixa toca direto do `content://` do sistema. O id fica `local:<uri>` para
 * o player reconhecer o caminho sem consultar o banco.
 */
object LocalMusic {

    const val PREFIX = "local:"

    fun isLocal(id: String): Boolean = id.startsWith(PREFIX)

    fun uriFor(id: String): Uri = Uri.parse(id.removePrefix(PREFIX))

    fun scan(context: Context, limit: Int = 3000): List<Track> {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val order = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        val tracks = ArrayList<Track>()
        runCatching {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                order,
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                while (cursor.moveToNext() && tracks.size < limit) {
                    val id = cursor.getLong(idIndex)
                    val uri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id,
                    )
                    tracks += Track(
                        id = PREFIX + uri,
                        title = cursor.getString(titleIndex).orEmpty().ifBlank { "Faixa" },
                        artist = cursor.getString(artistIndex).orEmpty()
                            .takeUnless { it == MediaStore.UNKNOWN_STRING }
                            .orEmpty(),
                        album = cursor.getString(albumIndex).orEmpty(),
                        durationSec = (cursor.getLong(durationIndex) / 1000L).toInt(),
                    )
                }
            }
        }
        return tracks
    }
}
