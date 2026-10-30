package com.openfy.android.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.openfy.android.MainActivity
import com.openfy.android.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Serviço em primeiro plano (dataSync) que mantém o processo vivo enquanto o
 * [DownloadEngine] baixa áudio. Assim o download continua ao abrir outro app e
 * mostra progresso/cancelamento na barra de notificações.
 */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var idleJob: Job? = null
    private var lastKey = ""

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForegroundWith(build(DownloadEngine.progress.value))
        scope.launch {
            DownloadEngine.progress.collect { progress ->
                idleJob?.cancel()
                notify(progress)
                if (progress.isEmpty()) {
                    idleJob = scope.launch {
                        delay(2_500)
                        if (DownloadEngine.progress.value.isEmpty() && DownloadEngine.activeCount() == 0) {
                            ServiceCompat.stopForeground(
                                this@DownloadService,
                                ServiceCompat.STOP_FOREGROUND_REMOVE,
                            )
                            stopSelf()
                        }
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            scope.launch {
                DownloadEngine.cancelAllAndWait()
                ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            return START_NOT_STICKY
        }
        if (DownloadEngine.progress.value.isEmpty() && DownloadEngine.activeCount() == 0) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        notify(DownloadEngine.progress.value)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        idleJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun startForegroundWith(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        runCatching { ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type) }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Downloads",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Progresso dos áudios baixados para ouvir offline"
            setShowBadge(false)
            setSound(null, null)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun build(progress: Map<String, Float>): Notification {
        val count = progress.size.coerceAtLeast(1)
        val single = progress.values.singleOrNull()
        val average = progress.values.filter { it >= 0f }.takeIf { it.isNotEmpty() }?.average()
        val percent = single?.let { (it * 100).toInt().coerceIn(0, 100) }
            ?: average?.let { (it * 100).toInt().coerceIn(0, 100) }
        val content = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val cancel = PendingIntent.getService(
            this,
            21,
            Intent(this, DownloadService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(
                when (count) {
                    1 -> "Baixando 1 faixa"
                    else -> "Baixando $count faixas"
                },
            )
            .setContentText(percent?.let { "$it% concluído" } ?: "Preparando o áudio…")
            .setContentIntent(content)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancelar", cancel)
        if (percent != null) {
            builder.setProgress(100, percent, false)
        } else {
            builder.setProgress(0, 0, true)
        }
        return builder.build()
    }

    private fun notify(progress: Map<String, Float>) {
        val key = progress.entries
            .sortedBy { it.key }
            .joinToString("|") { "${it.key}:${(it.value * 100).toInt()}" }
        if (key == lastKey) return
        lastKey = key
        runCatching {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, build(progress))
        }
    }

    companion object {
        private const val CHANNEL_ID = "openfy_downloads"
        private const val NOTIFICATION_ID = 712
        const val ACTION_CANCEL = "com.openfy.android.downloads.CANCEL"

        fun ensureStarted(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, DownloadService::class.java),
                )
            }.onFailure {
                android.util.Log.e("Openfy", "falha ao iniciar serviço de downloads", it)
            }
        }
    }
}
