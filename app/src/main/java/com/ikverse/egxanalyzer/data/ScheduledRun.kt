package com.ikverse.egxanalyzer.data

import android.content.Context
import com.ikverse.egxanalyzer.model.ScheduleClock
import java.time.Instant

/**
 * Which of the two ways a wake is answered, and the one question that decides it.
 *
 * There are two kinds of work the clock can owe and they want opposite things. A price refresh is
 * seconds of public feed and belongs in WorkManager, which will wait for a network and retry on a
 * phone that had none. A paid analysis is minutes of provider requests and cannot be there at all:
 * WorkManager stops ordinary work after ten minutes, and an analysis of a busy morning routinely
 * outlasts that - the run is killed mid-request, having already paid for every chunk it sent.
 *
 * So a wake that owes an analysis is answered by [ScheduledRunService] instead, which has no such
 * ceiling. Everything else stays on [ScheduledJobWorker].
 *
 * The decision lives here rather than in either caller because three of them ask it - the alarm,
 * the launch, and the worker deciding how much of the app to bring up - and three copies of a
 * question about spending money is two too many.
 */
object ScheduledRun {

    /**
     * Whether what is owed right now is going to send a paid request.
     *
     * Deliberately cheap: shared preferences and arithmetic, no keystore read, no database and no
     * network. It is asked from inside a broadcast receiver where the app holds a temporary
     * allowlist that is measured in seconds, and a slow answer here is the difference between a
     * foreground service that starts and one the system refuses.
     */
    fun paidAnalysisOwed(context: Context): Boolean {
        val settings = SettingsRepository(context, AndroidKeystoreCredentialStore(context))
        if (!settings.paidSchedulesEnabled()) return false
        val now = Instant.now()
        return settings.analysisSchedules().any { ScheduleClock.unservedFire(it, now) != null }
    }

    /**
     * Asks for whatever is owed to be run, by whichever route can actually finish it.
     *
     * Callers must already be somewhere a foreground service may be started: inside the window an
     * exact alarm grants its receiver, or with the app on screen. A refused start falls back to the
     * worker, which is the old behaviour, ceiling and all - worse than a service and much better
     * than not running at all.
     */
    fun request(context: Context) {
        if (paidAnalysisOwed(context) && ScheduledRunService.start(context)) return
        ScheduledJobWorker.sweep(context)
    }
}
