package com.ikverse.egxanalyzer.data

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ikverse.egxanalyzer.EgxApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Today's backup to Google Drive, run once the phone has a connection.
 *
 * Queued on resume by `LiveDriveBackup.backUpIfDue`, so a phone opened on the metro uploads when it
 * next reaches a network rather than failing and waiting for tomorrow. Retried with backoff a few
 * times; past that the next day's resume queues it again, and the Settings line naming the newest
 * copy is what shows a backup that has stopped arriving.
 */
class DriveBackupWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val application = applicationContext as? EgxApplication ?: return Result.success()
        // As the scheduled worker does: a process woken for this alone brings up no Telegram
        // session. Read only when the state is first built, so it changes nothing in an open app.
        application.startedForSchedule = true
        return try {
            withContext(Dispatchers.Main) { application.appState.backUpToDrive() }
            Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val NAME = "drive-backup"
        private const val MAX_ATTEMPTS = 4

        /** KEEP, so resuming the app twice before the upload runs queues it once. */
        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<DriveBackupWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
