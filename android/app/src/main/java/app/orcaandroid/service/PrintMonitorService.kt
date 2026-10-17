package app.orcaandroid.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.orcaandroid.OrcaApp
import app.orcaandroid.R
import app.orcaandroid.container
import app.orcaandroid.net.PrintHost
import app.orcaandroid.net.PrinterStatus
import app.orcaandroid.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Follows a print on the connected printer and shows its progress as a notification until the job
 * ends. Polls the printer host every few seconds; also offers pause/resume/cancel.
 */
class PrintMonitorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private var host: PrintHost? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val printer = intent?.getStringExtra(EXTRA_PRINTER)
        when (intent?.action) {
            ACTION_PAUSE, ACTION_RESUME, ACTION_CANCEL -> {
                val action = when (intent.action) {
                    ACTION_PAUSE -> PrintHost.JobAction.PAUSE
                    ACTION_RESUME -> PrintHost.JobAction.RESUME
                    else -> PrintHost.JobAction.CANCEL
                }
                scope.launch { withContext(Dispatchers.IO) { runCatching { host?.control(action) } } }
                return START_NOT_STICKY
            }
        }
        val connection = printer?.let { application.container.settings.connection(it) }
        if (connection == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(printer, PrinterStatus(PrinterStatus.State.PRINTING)),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0)
        job?.cancel()
        host?.close()
        val h = PrintHost.create(connection).also { host = it }
        job = scope.launch {
            var idlePolls = 0
            var failures = 0
            while (true) {
                val status = withContext(Dispatchers.IO) { runCatching { h.status() }.getOrNull() }
                if (status == null) {
                    if (++failures >= 6) break
                } else {
                    failures = 0
                    application.container.printerStatus.value = status
                    getSystemService(android.app.NotificationManager::class.java).notify(NOTIFICATION_ID, notification(printer, status))
                    // A just-started job may still report idle for a moment.
                    if (!status.isActive && ++idlePolls >= 3) break
                    if (status.isActive) idlePolls = 0
                }
                delay(POLL_MS)
            }
            finish(printer)
        }
        return START_STICKY
    }

    private fun finish(printer: String) {
        val last = application.container.printerStatus.value
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        if (last?.state == PrinterStatus.State.FINISHED || last?.state == PrinterStatus.State.ERROR) {
            val text = getString(if (last.state == PrinterStatus.State.FINISHED) R.string.print_finished else R.string.print_failed)
            getSystemService(android.app.NotificationManager::class.java).notify(DONE_ID,
                NotificationCompat.Builder(this, OrcaApp.CHANNEL_PRINT).setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(printer).setContentText(text).setAutoCancel(true).build())
        }
        stopSelf()
    }

    private fun notification(printer: String, s: PrinterStatus): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        fun action(a: String, code: Int) = PendingIntent.getService(this, code,
            Intent(this, PrintMonitorService::class.java).setAction(a), PendingIntent.FLAG_IMMUTABLE)
        val percent = ((s.progress ?: 0f) * 100).toInt()
        val remaining = s.remainingSeconds?.let { " · " + getString(R.string.remaining_fmt, formatDuration(it.toDouble())) }.orEmpty()
        return NotificationCompat.Builder(this, OrcaApp.CHANNEL_PRINT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(s.file ?: printer)
            .setContentText("$percent %$remaining")
            .setProgress(100, percent, s.progress == null)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .apply {
                if (s.state == PrinterStatus.State.PAUSED) addAction(0, getString(R.string.resume), action(ACTION_RESUME, 2))
                else addAction(0, getString(R.string.pause), action(ACTION_PAUSE, 3))
                addAction(0, getString(R.string.cancel_print), action(ACTION_CANCEL, 4))
            }
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        host?.close()
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 2
        private const val DONE_ID = 3
        private const val POLL_MS = 15_000L
        private const val EXTRA_PRINTER = "printer"
        private const val ACTION_PAUSE = "pause"
        private const val ACTION_RESUME = "resume"
        private const val ACTION_CANCEL = "cancel"

        fun start(context: Context, printer: String) = ContextCompat.startForegroundService(context,
            Intent(context, PrintMonitorService::class.java).putExtra(EXTRA_PRINTER, printer))

        fun formatDuration(seconds: Double): String {
            val s = seconds.toLong()
            val h = s / 3600
            val m = (s % 3600) / 60
            return if (h > 0) "${h} h ${m} min" else "${m} min"
        }
    }
}
