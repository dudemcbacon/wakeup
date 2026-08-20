package com.wakeup.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Alarms do not survive a reboot, an app update, or a clock/timezone change, so
 * the schedule is rebuilt from saved settings whenever one of those happens.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> {
                val armedFor = Scheduler.sync(context)
                Log.i(TAG, "Re-synced after ${intent.action}; next run = $armedFor")
            }
        }
    }

    private companion object {
        const val TAG = "BootReceiver"
    }
}
