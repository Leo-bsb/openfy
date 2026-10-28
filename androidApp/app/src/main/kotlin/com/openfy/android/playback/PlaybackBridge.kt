package com.openfy.android.playback

import com.openfy.core.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Estado compartilhado entre o ViewModel e o serviço de reprodução. */
data class PlaybackInfo(
    val trackId: String = "",
    val title: String = "",
    val artist: String = "",
    val thumbnail: String = "",
    val playing: Boolean = false,
    val durationMs: Long = 0L,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
)

object PlaybackBridge {

    private val _info = MutableStateFlow(PlaybackInfo())
    val info: StateFlow<PlaybackInfo> = _info.asStateFlow()

    var onToggle: (() -> Unit)? = null
    var onPlay: (() -> Unit)? = null
    var onPause: (() -> Unit)? = null
    var onNext: (() -> Unit)? = null
    var onPrevious: (() -> Unit)? = null
    var onStop: (() -> Unit)? = null

    fun update(
        track: Track?,
        playing: Boolean,
        durationMs: Long,
        hasPrevious: Boolean,
        hasNext: Boolean,
    ) {
        val next = PlaybackInfo(
            trackId = track?.id.orEmpty(),
            title = track?.title.orEmpty(),
            artist = track?.artist.orEmpty(),
            thumbnail = track?.thumbnail.orEmpty(),
            playing = playing,
            durationMs = durationMs,
            hasPrevious = hasPrevious,
            hasNext = hasNext,
        )
        if (_info.value != next) _info.value = next
    }

    fun clear() {
        onToggle = null
        onPlay = null
        onPause = null
        onNext = null
        onPrevious = null
        onStop = null
        _info.value = PlaybackInfo()
    }

    fun toggle() = onToggle?.invoke()
    fun play() = onPlay?.invoke()
    fun pause() = onPause?.invoke()
    fun next() = onNext?.invoke()
    fun previous() = onPrevious?.invoke()
    fun stop() = onStop?.invoke()
}
