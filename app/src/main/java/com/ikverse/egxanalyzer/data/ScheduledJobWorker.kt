package com.ikverse.egxanalyzer.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ikverse.egxanalyzer.EgxApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
        // AppState is Compose state driven from the main thread; the run itself suspends onto IO
        // inside the repositories, exactly as it does when a screen starts it.
        withContext(Dispatchers.Main) { application.appState.runDueScheduledJobs() }
        Result.success()
        // Never retried. Each job has already written down what happened to it, a retry ten
        // minutes later would be answering a fire that has passed, and the schedule's own next
        // run is the only retry that makes sense.
    }.getOrDefault(Result.success())

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
