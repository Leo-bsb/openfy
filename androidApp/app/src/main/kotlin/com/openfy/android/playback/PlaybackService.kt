package com.openfy.android.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.bitmapConfig
import coil3.toBitmap
import com.openfy.android.MainActivity
import com.openfy.android.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Foreground service + MediaSession para manter o áudio vivo com a tela
 * apagada ou o app em segundo plano. Os comandos voltam ao ViewModel pelo
 * PlaybackBridge; o serviço cuida da notificação, lockscreen e Bluetooth.
 */
class PlaybackService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var session: MediaSessionCompat
    private var artworkUrl = ""
    private var artwork: Bitmap? = null
    private var artworkJob: Job? = null

    /** Assinatura do que aparece na notificação; posição não força rebuild. */
    private var notificationKey = ""

    override fun onCreate() {
        super.onCreate()
        createChannel()
        session = MediaSessionCompat(this, "Openfy").apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS,
            )
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() { PlaybackBridge.play() }
                override fun onPause() { PlaybackBridge.pause() }
                override fun onSkipToNext() { PlaybackBridge.next() }
                override fun onSkipToPrevious() { PlaybackBridge.previous() }
                override fun onStop() { PlaybackBridge.stop() }
                override fun onSeekTo(pos: Long) { PlaybackBridge.seek(pos) }
            })
            isActive = true
        }
        startForeground(NOTIFICATION_ID, buildNotification(PlaybackBridge.info.value))
        scope.launch {
            PlaybackBridge.info.collect { info ->
                session.setMetadata(metadata(info))
                session.setPlaybackState(playbackState(info))
                if (info.thumbnail != artworkUrl) {
                    artworkUrl = info.thumbnail
                    artwork = null
                    artworkJob?.cancel()
                    artworkJob = scope.launch {
                        val loaded = loadArtwork(artworkUrl)
                        if (artworkUrl == info.thumbnail) {
                            artwork = loaded
                            session.setMetadata(metadata(info))
                            notificationKey = ""
                            notify(info)
                        }
                    }
                }
                notify(info)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (PlaybackBridge.onToggle == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_TOGGLE -> PlaybackBridge.toggle()
            ACTION_NEXT -> PlaybackBridge.next()
            ACTION_PREVIOUS -> PlaybackBridge.previous()
            ACTION_STOP -> {
                PlaybackBridge.stop()
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        artworkJob?.cancel()
        scope.cancel()
        runCatching { session.isActive = false }
        runCatching { session.release() }
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Reprodução",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Controles do player do Openfy"
            setShowBadge(false)
            setSound(null, null)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(info: PlaybackInfo): Notification {
        val title = info.title.ifBlank { "Openfy" }
        val text = info.artist.ifBlank { "Tocador pessoal" }
        val content = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val previous = actionIntent(ACTION_PREVIOUS, 11)
        val toggle = actionIntent(ACTION_TOGGLE, 12)
        val next = actionIntent(ACTION_NEXT, 13)
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setSubText(if (info.playing) "Tocando" else "Pausado")
            .setContentIntent(content)
            .setDeleteIntent(actionIntent(ACTION_STOP, 14))
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setOngoing(info.playing)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setColor(ACCENT)
            .addAction(android.R.drawable.ic_media_previous, "Anterior", previous)
            .addAction(
                if (info.playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (info.playing) "Pausar" else "Tocar",
                toggle,
            )
            .addAction(android.R.drawable.ic_media_next, "Próxima", next)
        artwork?.let { builder.setLargeIcon(it) }
        // A barra de progresso/arrasto da notificação vem da MediaSession; o
        // MediaStyle só precisa apontar para ela.
        builder.setStyle(
            androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(session.sessionToken)
                .setShowActionsInCompactView(0, 1, 2),
        )
        return builder.build()
    }

    private fun actionIntent(action: String, request: Int): PendingIntent =
        PendingIntent.getService(
            this,
            request,
            Intent(this, PlaybackService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun metadata(info: PlaybackInfo): MediaMetadataCompat =
        MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, info.title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, info.artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, info.thumbnail)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, info.durationMs)
            .apply {
                artwork?.let { putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, it) }
            }
            .build()

    private fun playbackState(info: PlaybackInfo): PlaybackStateCompat =
        PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or
                    PlaybackStateCompat.ACTION_SEEK_TO or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackStateCompat.ACTION_STOP,
            )
            .setState(
                if (info.playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                info.positionMs.coerceAtLeast(0L),
                1f,
            )
            .build()

    private suspend fun loadArtwork(url: String): Bitmap? {
        if (url.isBlank()) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val request = ImageRequest.Builder(this@PlaybackService)
                    .data(url)
                    .size(512, 512)
                    .bitmapConfig(Bitmap.Config.ARGB_8888)
                    .build()
                val result = SingletonImageLoader.get(this@PlaybackService).execute(request)
                    as? SuccessResult ?: return@runCatching null
                result.image.toBitmap()
            }.getOrNull()
        }
    }

    private fun notify(info: PlaybackInfo) {
        val key = "${info.trackId}|${info.playing}|${info.durationMs}|$artworkUrl|${artwork != null}"
        if (key == notificationKey) return
        notificationKey = key
        runCatching {
            NotificationManagerCompat.from(this)
                .notify(NOTIFICATION_ID, buildNotification(info))
        }
    }

    companion object {
        private const val CHANNEL_ID = "openfy_playback"
        private const val NOTIFICATION_ID = 711
        private val ACCENT = Color.parseColor("#7DD3FC")
        const val ACTION_TOGGLE = "com.openfy.android.playback.TOGGLE"
        const val ACTION_NEXT = "com.openfy.android.playback.NEXT"
        const val ACTION_PREVIOUS = "com.openfy.android.playback.PREVIOUS"
        const val ACTION_STOP = "com.openfy.android.playback.STOP"

        fun ensureStarted(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, PlaybackService::class.java),
                )
            }.onFailure {
                android.util.Log.e("Openfy", "falha ao iniciar serviço de reprodução", it)
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, PlaybackService::class.java)) }
        }
    }
}
