package com.wakeup.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Posts a short confirmation after each run, so a missed or partly-blocked run
 * is visible instead of silent.
 */
object Notifier {

    private const val CHANNEL_ID = "wakeup_runs"
    private const val NOTIFICATION_ID = 1

    fun postRunReport(context: Context, result: WakeupResult) {
        if (!hasNotificationPermission(context)) return

        ensureChannel(context)

        val title = if (result.succeeded) {
            "Phone audio restored"
        } else {
            "Wake-up run had problems"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(result.summary())
            .setStyle(NotificationCompat.BigTextStyle().bigText(result.summary()))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification)
    }

    fun hasNotificationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Wake-up runs",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Confirms that the scheduled audio change ran."
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }
}
