package com.ikverse.egxanalyzer.data

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/**
 * Keeps an analysis alive while the user is elsewhere.
 *
 * The work itself runs on the application scope; this exists so Android treats the process as busy
 * rather than reclaiming it the moment the app leaves the foreground. It holds no state and does no
 * work - starting it is a promise that something is running, and stopping it withdraws the promise.
 */
class AnalysisService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val sources = intent?.getIntExtra(EXTRA_SOURCES, 0) ?: 0
        val model = intent?.getStringExtra(EXTRA_MODEL).orEmpty()
        val notification = AnalysisNotifier(this).running(sources, model)
        // The typed overload is required from Android 10 on and does not exist before it.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                AnalysisNotifier.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(AnalysisNotifier.NOTIFICATION_ID, notification)
        }
        // Not restarted if Android kills the process: the analysis would be gone with it, and
        // silently starting a paid request again is the last thing the user wants.
        return START_NOT_STICKY
    }

    companion object {
        private const val EXTRA_SOURCES = "sources"
        private const val EXTRA_MODEL = "model"

        fun start(context: Context, sources: Int, model: String) {
            val intent = Intent(context, AnalysisService::class.java)
                .putExtra(EXTRA_SOURCES, sources)
                .putExtra(EXTRA_MODEL, model)
            ContextCompat_startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AnalysisService::class.java))
        }

        private fun ContextCompat_startForegroundService(context: Context, intent: Intent) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
