package com.ikverse.egxanalyzer.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.ikverse.egxanalyzer.model.AnalysisSchedule
import com.ikverse.egxanalyzer.model.MarketRefresh
import com.ikverse.egxanalyzer.model.ScheduleClock
import java.time.Instant

/**
 * Books the one alarm that wakes the app for whatever is next.
 *
 * One alarm for both the things this phone does on its own - keeping prices fresh through a
 * session, and the analysis before the open - rather than one each: only the nearest fire matters,
 * and the run that answers it books the one after. A pending intent per feature would be state to
 * keep in step for no gain.
 *
 * WorkManager is not the timekeeper here, deliberately. Its delays are a floor and not a promise -
 * in Doze a fifteen-minute period becomes whenever the system next feels like it - so 11:15 would
 * mean some time that afternoon. OverdueWorker can live with that because it asks a question whose
 * answer changes once a day; a refresh that is meant to keep up with a moving market cannot. The
 * alarm is the clock, and WorkManager runs the work once the alarm has woken the phone.
 */
class JobScheduler(private val context: Context) {

    private val alarms: AlarmManager = context.getSystemService(AlarmManager::class.java)

    /**
     * Whether the system will honour an exact alarm.
     *
     * From Android 14 this is off until the user grants it, and the app asks rather than declaring
     * USE_EXACT_ALARM, which is meant for alarm clocks and calendars. Without it the fallback is
     * inexact and can drift by the better part of an hour, which is survivable for the analysis
     * because it carries a grace window, and is most of what a fifteen-minute refresh has to
     * offer - which is why the screen says so rather than leaving the user to wonder.
     */
    fun canScheduleExact(): Boolean = alarms.canScheduleExactAlarms()

    /**
     * Points the alarm at the earliest fire either feature has left, or takes it down.
     *
     * Called after anything that could move that moment: either switch flipped, the schedule's
     * time changed, a run served, a reboot, the exact-alarm permission changing, and every launch.
     * Booking is cheap and idempotent, so the safe thing on all of those is to book again.
     */
    fun rebook(schedule: AnalysisSchedule, marketRefresh: Boolean) {
        val pending = fireIntent()
        val now = Instant.now()
        val at = listOfNotNull(
            if (schedule.enabled) ScheduleClock.nextFire(schedule.at, now) else null,
            if (marketRefresh) MarketRefresh.nextFire(now) else null,
        ).minOrNull()
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
