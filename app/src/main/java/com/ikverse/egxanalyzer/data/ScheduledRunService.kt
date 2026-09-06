package com.ikverse.egxanalyzer.data

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import com.ikverse.egxanalyzer.EgxApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Runs a paid scheduled analysis for as long as it actually takes.
 *
 * The one thing this has that [ScheduledJobWorker] does not is time. WorkManager stops ordinary
 * work after ten minutes; nothing in this app caps a run, because the response timeout is per
 * request and a busy morning is a dozen of them. A foreground service has no such window, so the
 * run either finishes or fails on its own terms rather than being cut off at an arbitrary line.
 *
 * **Started from the alarm, not from here.** From Android 12 an app in the background may not start
 * a foreground service, and this app is in the background at 07:00 by definition. The exemption it
 * uses is the temporary allowlist an exact alarm grants its receiver, which lasts seconds - so
 * [ScheduleReceiver] starts this before it does anything else, and [start] reports whether the
 * system took it. That is also why the exact-alarm permission is load-bearing for a paid schedule
 * rather than merely a matter of punctuality: an inexact alarm grants no allowlist.
 */
class ScheduledRunService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var work: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Foreground first and unconditionally. The system gives a service started this way a few
        // seconds to say what it is doing, and a process that spends them building app state
        // before calling startForeground is killed for it.
        startForeground()
        // A second alarm landing while a run is going must not start a second run on top of it.
        // The one already going is answering the same clock.
        if (work?.isActive == true) return START_NOT_STICKY
        holding = true
        acquireWakeLock()
        work = scope.launch {
            try {
                val application = applicationContext as? EgxApplication ?: return@launch
                // Compose state is driven from the main thread; the run itself suspends onto IO
                // inside the repositories, exactly as it does when a screen starts it.
                withContext(Dispatchers.Main) { application.appState.runDueScheduledJobs() }
            } catch (error: Throwable) {
                // Swallowed for the reason the worker swallows its own: whatever ran has already
                // written down what happened to it, and there is nobody here to tell.
            } finally {
                finish()
            }
        }
        return START_NOT_STICKY
    }

    /**
     * The system taking its six hours of data-sync back, from Android 15.
     *
     * Irrelevant to a ten-minute analysis and overridden anyway, because a service that ignores
     * this callback is killed as though it had hung rather than stopped. Both signatures: the
     * platform calls the one-argument form on Android 14 and the two-argument one after it.
     */
    override fun onTimeout(startId: Int) = finish()

    override fun onTimeout(startId: Int, fgsType: Int) = finish()

    override fun onDestroy() {
        // Cancelling the run cancels the analysis inside it, which JobRunner records as a run that
        // was stopped before it finished rather than leaving the fire owed and paid for twice.
        work?.cancel()
        scope.cancel()
        releaseWakeLock()
        holding = false
        super.onDestroy()
    }

    private fun startForeground() {
        val notification = AnalysisNotifier(this).starting()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                AnalysisNotifier.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(AnalysisNotifier.NOTIFICATION_ID, notification)
        }
    }

    /**
     * Keeps the CPU awake for the length of the run.
     *
     * A foreground service keeps the *process* from being reclaimed; it does not keep the phone
     * from sleeping. WorkManager held one of these for the ten minutes it allowed, so leaving it
     * out here would trade a run that reliably died at ten minutes for one that stalls with the
     * screen off - which is the worse bug, because it looks intermittent.
     *
     * The timeout is a safety net and not the plan: released in [finish] on every path, and if a
     * path is ever missed the system takes it back rather than draining the battery until reboot.
     */
    private fun acquireWakeLock() {
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
            .apply { acquire(TimeUnit.MINUTES.toMillis(WAKE_LOCK_MINUTES)) }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    private fun finish() {
        releaseWakeLock()
        holding = false
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    companion object {
        private const val WAKE_LOCK_TAG = "egxanalyzer:scheduled-run"

        /**
         * Longer than any run this app has taken and far shorter than the day it could waste.
         *
         * A run that is still going after an hour is not going to finish; the lock coming off is
         * how the phone stops paying for it.
         */
        private const val WAKE_LOCK_MINUTES = 60L

        /**
         * Whether this service is holding the process open right now.
         *
         * Read by the app when a run asks for the foreground service that ordinarily keeps it
         * alive: while this is true that service would be a second holder of one notification,
         * and the first one to stop would take the other's notification down with it.
         */
        @Volatile
        var holding: Boolean = false
            private set

        /**
         * Starts the service, and says whether the system allowed it.
         *
         * False is a real answer and not an error. From Android 12 the start is refused outside a
         * window the caller has to be inside, and the caller's fallback - the worker, ceiling and
         * all - is better than an exception thrown out of a broadcast receiver.
         */
        fun start(context: Context): Boolean = runCatching {
            context.startForegroundService(Intent(context, ScheduledRunService::class.java))
            true
        }.getOrElse { false }
    }
}
