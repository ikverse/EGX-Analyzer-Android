package com.ikverse.egxanalyzer.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ikverse.egxanalyzer.model.PositionView
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * The once-a-day look at whether anything has run past its deadline.
 *
 * Deliberately the only thing in this app that runs while it is closed, and deliberately the
 * cheapest possible version of it: no network, no analysis, no Telegram. How overdue a trade is
 * comes from a date already on disk and today's date, so this is a database read and an arithmetic
 * comparison. An analysis costs the owner cloud credits and must never be started from here.
 *
 * It reads the store directly rather than going through `AppState`, which exists to serve a screen
 * and drags a Telegram session up with it. Nothing on screen needs to change for this to be right.
 */
class OverdueWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result = runCatching {
        val settings = SettingsRepository(
            applicationContext,
            AndroidKeystoreCredentialStore(applicationContext),
        )
        if (!settings.loadPreferences().overdueRemindersEnabled) return@runCatching Result.success()

        val store = LocalDataStore(applicationContext)
        val portfolio = PortfolioCalculator.build(
            positions = store.positions(),
            sessionsFor = store::sessionsFrom,
            latestQuoteFor = store::latestQuote,
            // The exchange's calendar, matching what the Portfolio tab counts with, so a
            // notification never disagrees with the screen it is pointing at.
            today = LocalDate.now(ZoneId.of(EGX_ZONE)),
        )

        val overdue = portfolio.positions.filter(PositionView::overdue)
        if (overdue.isNotEmpty()) {
            OverdueNotifier(applicationContext).overdue(
                count = overdue.size,
                longestDays = overdue.maxOf(PositionView::overdueDays),
            )
        }
        Result.success()
        // Retrying would mean asking again in a few minutes about something that changes once a
        // day. Tomorrow's run is the retry.
    }.getOrDefault(Result.success())

    companion object {
        private const val NAME = "overdue-check"

        /** Cairo, because a deadline belongs to the exchange rather than to wherever the phone is. */
        private const val EGX_ZONE = "Africa/Cairo"

        /**
         * Books the daily check, replacing whatever was booked before.
         *
         * `UPDATE` rather than `KEEP`, so changing the schedule in a later version actually takes
         * effect instead of leaving every existing install on the old one forever.
         */
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<OverdueWorker>(1, TimeUnit.DAYS)
                    .setConstraints(
                        // No network needed, so the only thing worth waiting for is a phone that
                        // is not actively being used.
                        Constraints.Builder().setRequiresBatteryNotLow(true).build(),
                    )
                    .build(),
            )
        }

        /** Stops the daily check, for a user who has turned the reminder off. */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}
