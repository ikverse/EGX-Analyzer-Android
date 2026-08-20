package com.ikverse.egxanalyzer.data

import android.content.Context
import android.content.pm.ServiceInfo
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ikverse.egxanalyzer.EgxApplication
import com.ikverse.egxanalyzer.model.ScheduleClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant

/**
 * Runs whatever the schedules owe, once the alarm has woken the phone.
 *
 * Goes through the app's own AppState rather than reaching for the repositories directly, which is
 * the opposite of what OverdueWorker does and is deliberate. That worker answers a question out of
 * the database and touches nothing else, so borrowing the whole app would have dragged a Telegram
 * session up for an arithmetic comparison. A scheduled job does the same work a button on screen
 * does - and a second implementation of a price refresh is a second set of rules about what gets
 * fetched, what gets re-scored afterwards and what the record then says. One of the two would
 * eventually be wrong, and it would be this one, because nobody is watching it.
 *
 * The cost is honest: waking the process brings the catalog, a stale-price check and a sync
 * catch-up with it. None of them is paid and none of them reaches a cloud provider.
 */
class ScheduledJobWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result = runCatching {
        val application = applicationContext as? EgxApplication
            ?: return@runCatching Result.success()
        if (paidWorkIsDue()) goForeground()
        // AppState is Compose state driven from the main thread; the run itself suspends onto IO
        // inside the repositories, exactly as it does when a screen starts it.
        withContext(Dispatchers.Main) { application.appState.runDueScheduledJobs() }
        Result.success()
        // Never retried. Each job has already written down what happened to it, a retry ten
        // minutes later would be answering a fire that has passed, and the schedule's own next
        // run is the only retry that makes sense.
    }.getOrDefault(Result.success())

    /**
     * Whether anything owed right now is going to send a paid request.
     *
     * Asked before the run rather than during it, because what it decides - going foreground - has
     * to be settled before the long part starts. A free refresh finishes well inside WorkManager's
     * ordinary window and has no business putting a notification on the phone.
     */
    private fun paidWorkIsDue(): Boolean {
        val settings = SettingsRepository(
            applicationContext,
            AndroidKeystoreCredentialStore(applicationContext),
        )
        if (!settings.schedulesEnabled() || !settings.paidSchedulesEnabled()) return false
        val now = Instant.now()
        return LocalDataStore(applicationContext).scheduledJobs().any { job ->
            job.work.spendsCredits && ScheduleClock.unservedFire(job, now) != null
        }
    }

    /**
     * Puts this worker in the foreground for the length of a paid run.
     *
     * Two problems, one answer. WorkManager stops ordinary work after about ten minutes, and an
     * analysis of a busy morning can outlast that - the response timeout alone reaches fifteen.
     * And from Android 12 an app in the background may not start a foreground service at all, so
     * the service the app has always used to hold itself open would be refused precisely when it
     * is needed most. Going foreground here fixes the ceiling and makes that later start legal,
     * because an app already running one is allowed to start another.
     *
     * The same notification id the analysis itself uses, so the reader sees one notification that
     * fills in with real numbers rather than two describing the same run. A refusal is survivable
     * and deliberately swallowed: the run still goes, it simply gets the ordinary window.
     */
    private suspend fun goForeground() {
        runCatching {
            setForeground(
                ForegroundInfo(
                    AnalysisNotifier.NOTIFICATION_ID,
                    AnalysisNotifier(applicationContext).starting(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                ),
            )
        }
    }

    companion object {
        private const val NAME = "scheduled-jobs"

        /**
         * Asks for the owed jobs to be run.
         *
         * KEEP, so an alarm landing on top of a boot does not queue the same sweep twice. It would
         * be harmless - a fire already served is skipped by the runner - but the second one would
         * wake the app for nothing.
         */
        fun sweep(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                NAME,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<ScheduledJobWorker>()
                    // Everything schedulable so far reaches the network. Waiting for one beats
                    // recording a failure against a phone that was simply out of signal.
                    .setConstraints(
                        Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                    )
                    .build(),
            )
        }
    }
}
