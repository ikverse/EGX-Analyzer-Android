package com.ikverse.egxanalyzer.data

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Everything that can mean the alarm needs looking at again.
 *
 * The alarm firing is only one of them. An alarm does not survive a reboot or an app update, and
 * from Android 14 the permission behind an exact one can be taken away while the app is closed -
 * each of which leaves a phone with work it will never do and no sign that anything is wrong. All
 * four arrive here and are answered the same way, because the answer to all four is the same: book
 * the next alarm, then run whatever is owed.
 *
 * Re-booking happens here rather than in the worker so that it needs nothing: no network, no cloud
 * credential, no Telegram. A phone that boots into a tunnel still comes out of it with its alarm
 * set.
 */
class ScheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in handled) return
        val application = context.applicationContext
        // A broadcast has about ten seconds and this touches storage, so it is finished off the
        // main thread and the system is told to keep the process alive until it is done.
        val finish = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val settings = SettingsRepository(
                    application,
                    AndroidKeystoreCredentialStore(application),
                )
                val schedules = settings.analysisSchedules()
                val marketRefresh = settings.marketRefreshEnabled()
                // The same question the daily check is booked on: either notification about a
                // trade needs the sweep at the close, so either one wants the alarm.
                val preferences = settings.loadPreferences()
                val closeSweep = preferences.overdueRemindersEnabled ||
                    preferences.tradeAlertsEnabled
                JobScheduler(application).rebook(schedules, marketRefresh, closeSweep)
                // Swept while anything is on, and cancelled only when everything is off - the same
                // shape as the daily check, and for the same reason. Reading only the analysis
                // side here would take the price refresh down with it silently.
                if (schedules.any { it.enabled } || marketRefresh || closeSweep) {
                    ScheduledJobWorker.sweep(application)
                }
            } finally {
                finish.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.ikverse.egxanalyzer.RUN_SCHEDULE"

        private val handled = setOf(
            ACTION_FIRE,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )
    }
}
