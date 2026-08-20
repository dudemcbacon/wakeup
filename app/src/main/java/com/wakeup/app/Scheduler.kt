package com.wakeup.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId

/** Arms, re-arms, and cancels the single alarm this app owns. */
object Scheduler {

    private const val TAG = "Scheduler"
    private const val ALARM_REQUEST_CODE = 1001
    private const val SHOW_INTENT_REQUEST_CODE = 1002

    fun canScheduleExact(context: Context): Boolean =
        alarmManager(context).canScheduleExactAlarms()

    /**
     * Brings the system alarm in line with the saved settings: arms the next run
     * when enabled, cancels when not.
     *
     * @param from the moment to search forward from. The receiver passes a time
     *   slightly in the future so that re-arming a daily alarm at its own trigger
     *   instant lands on tomorrow rather than firing again immediately.
     * @return epoch millis of the armed run, or null if nothing is armed.
     */
    fun sync(context: Context, from: LocalDateTime = LocalDateTime.now()): Long? {
        val prefs = Prefs(context)
        if (!prefs.enabled) {
            cancel(context)
            return null
        }

        val next = nextTrigger(prefs.hour, prefs.minute, prefs.repeatMode, from)
        val triggerAtMillis = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val am = alarmManager(context)
        val operation = alarmOperation(context)

        if (am.canScheduleExactAlarms()) {
            // setAlarmClock is the right primitive for a user-visible wake-up
            // event: it is exempt from Doze deferral and surfaces the system
            // alarm icon. It needs SCHEDULE_EXACT_ALARM.
            am.setAlarmClock(
                AlarmManager.AlarmClockInfo(triggerAtMillis, showIntent(context)),
                operation,
            )
            Log.i(TAG, "Exact alarm armed for $next")
        } else {
            // Permission not granted: still schedule, but the OS may run it late.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
            Log.w(TAG, "Exact alarms not permitted; armed inexact alarm for $next")
        }
        return triggerAtMillis
    }

    fun cancel(context: Context) {
        alarmManager(context).cancel(alarmOperation(context))
        Log.i(TAG, "Alarm cancelled")
    }

    /**
     * The next date-time matching [hour]:[minute] under [mode], strictly after
     * [from].
     *
     * Uses local wall-clock time, so a daylight-saving shift keeps the alarm at
     * the same displayed time rather than drifting by an hour.
     */
    fun nextTrigger(
        hour: Int,
        minute: Int,
        mode: RepeatMode,
        from: LocalDateTime = LocalDateTime.now(),
    ): LocalDateTime {
        var candidate = from
            .withHour(hour)
            .withMinute(minute)
            .withSecond(0)
            .withNano(0)

        if (!candidate.isAfter(from)) {
            candidate = candidate.plusDays(1)
        }
        if (mode == RepeatMode.WEEKDAYS) {
            while (candidate.dayOfWeek == DayOfWeek.SATURDAY ||
                candidate.dayOfWeek == DayOfWeek.SUNDAY
            ) {
                candidate = candidate.plusDays(1)
            }
        }
        return candidate
    }

    private fun alarmManager(context: Context): AlarmManager =
        context.getSystemService(AlarmManager::class.java)

    private fun alarmOperation(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            Intent(context, WakeupReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** Where the status-bar alarm icon takes the user when tapped. */
    private fun showIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            SHOW_INTENT_REQUEST_CODE,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
