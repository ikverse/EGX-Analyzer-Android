package com.ikverse.egxanalyzer.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.ikverse.egxanalyzer.model.ScheduleClock
import com.ikverse.egxanalyzer.model.ScheduledJob
import java.time.Instant

/**
 * Books the one alarm that wakes the app for whatever is next.
 *
 * One alarm for every schedule rather than one each: only the nearest fire matters, and the run
 * that answers it books the one after. A pending intent per job would be state to keep in step
 * with the table for no gain.
 *
 * WorkManager is not the timekeeper here, deliberately. Its delays are a floor and not a promise -
 * in Doze an hourly window becomes whenever the system next feels like it - so 18:00 would mean
 * some time that evening. OverdueWorker can live with that because it asks a question whose
 * answer changes once a day; a schedule the user set to a minute cannot. The alarm is the clock,
 * and WorkManager runs the work once the alarm has woken the phone.
 */
class JobScheduler(private val context: Context) {

    private val alarms: AlarmManager = context.getSystemService(AlarmManager::class.java)

    /**
     * Whether the system will honour an exact alarm.
     *
     * From Android 14 this is off until the user grants it, and the app asks rather than declaring
     * USE_EXACT_ALARM, which is meant for alarm clocks and calendars. Without it the fallback is
     * inexact and can drift by the better part of an hour, which is survivable precisely because
     * every job carries a grace window.
     */
    fun canScheduleExact(): Boolean = alarms.canScheduleExactAlarms()

    /**
     * Points the alarm at the earliest fire any enabled job has left, or takes it down.
     *
     * Called after anything that could move that moment: a job saved, deleted or switched, a run
     * served, a reboot, the exact-alarm permission changing. Booking is cheap and idempotent, so
     * the safe thing on every one of those is to book again.
     */
    fun rebook(jobs: List<ScheduledJob>, enabled: Boolean) {
        val pending = fireIntent()
        val at = if (enabled) ScheduleClock.earliestFire(jobs, Instant.now()) else null
        if (at == null) {
            alarms.cancel(pending)
            return
        }
        // RTC and not elapsed-realtime: a schedule is a wall-clock promise, so it has to follow the
        // clock across a reboot or a time-zone change rather than count seconds from a boot.
        if (canScheduleExact()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), pending)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), pending)
        }
    }

    private fun fireIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, ScheduleReceiver::class.java).setAction(ScheduleReceiver.ACTION_FIRE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
