package com.wakeup.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Fired by AlarmManager at the configured time. All the work is a few
 * synchronous system calls, so it completes well inside the receiver's window
 * without needing goAsync() or a foreground service.
 */
class WakeupReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val result = WakeupActions.apply(context)
        val prefs = Prefs(context)

        val stamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
        prefs.lastRunSummary = "$stamp — ${result.summary()}"

        if (prefs.repeatMode == RepeatMode.ONCE) {
            prefs.enabled = false
            Scheduler.cancel(context)
            Log.i(TAG, "One-shot schedule complete; disarmed.")
        } else {
            // Search from a minute ahead so "today at this time" isn't re-armed.
            Scheduler.sync(context, from = LocalDateTime.now().plusMinutes(1))
        }

        Notifier.postRunReport(context, result)
    }

    private companion object {
        const val TAG = "WakeupReceiver"
    }
}
