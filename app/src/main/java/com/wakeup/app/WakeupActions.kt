package com.wakeup.app

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.util.Log
import kotlin.math.roundToInt

/** What actually happened during one run, so the UI and log can report honestly. */
data class WakeupResult(
    val dndTurnedOff: Boolean,
    val ringerSetToNormal: Boolean,
    val volumesApplied: List<String>,
    val problems: List<String>,
) {
    val succeeded: Boolean get() = problems.isEmpty()

    /** One-line summary for the UI and the confirmation notification. */
    fun summary(): String = if (succeeded) {
        "DND off, sound on, volumes at ${WakeupActions.VOLUME_PERCENT}% " +
            "(${volumesApplied.joinToString(", ")})"
    } else {
        problems.joinToString(" ")
    }
}

/**
 * The single action this app exists to perform: leave Do Not Disturb, make the
 * phone audible again, and raise every volume slider to a fixed percentage.
 */
object WakeupActions {

    const val VOLUME_PERCENT = 75

    private const val TAG = "WakeupActions"

    /**
     * Ring and notification are deliberately both listed. On most devices the
     * notification stream is aliased to the ring stream and the second write is a
     * no-op, but on devices that separate them we want both raised.
     */
    private val STREAMS = listOf(
        AudioManager.STREAM_RING to "ring",
        AudioManager.STREAM_NOTIFICATION to "notification",
        AudioManager.STREAM_ALARM to "alarm",
        AudioManager.STREAM_MUSIC to "media",
    )

    /**
     * True once the user has granted Do Not Disturb access in system settings.
     * Without it the OS rejects [NotificationManager.setInterruptionFilter] and
     * silently ignores volume writes while DND is on.
     */
    fun hasDndAccess(context: Context): Boolean =
        notificationManager(context).isNotificationPolicyAccessGranted

    fun isDndCurrentlyOn(context: Context): Boolean =
        notificationManager(context).currentInterruptionFilter !=
            NotificationManager.INTERRUPTION_FILTER_ALL

    /** Applies the whole wake-up state. Safe to call from a BroadcastReceiver. */
    fun apply(context: Context): WakeupResult {
        val nm = notificationManager(context)
        val am = context.getSystemService(AudioManager::class.java)
        val problems = mutableListOf<String>()

        // 1. Do Not Disturb off, FIRST. While DND is active the system ignores
        //    ringer-mode and volume changes from apps, so doing this last would
        //    make steps 2 and 3 quietly do nothing.
        var dndOff = false
        if (!nm.isNotificationPolicyAccessGranted) {
            problems += "No Do Not Disturb access — grant it in the app."
        } else {
            try {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                dndOff = true
            } catch (e: SecurityException) {
                problems += "Could not turn off Do Not Disturb (${e.message})."
            }
        }

        // 2. Leave silent/vibrate so notifications are audible again.
        var ringerNormal = false
        try {
            if (am.ringerMode != AudioManager.RINGER_MODE_NORMAL) {
                am.ringerMode = AudioManager.RINGER_MODE_NORMAL
            }
            ringerNormal = am.ringerMode == AudioManager.RINGER_MODE_NORMAL
            if (!ringerNormal) {
                problems += "Ringer stayed in ${describeRinger(am.ringerMode)} mode."
            }
        } catch (e: SecurityException) {
            problems += "Could not leave silent mode (${e.message})."
        }

        // 3. Every volume slider to VOLUME_PERCENT.
        val applied = mutableListOf<String>()
        if (am.isVolumeFixed) {
            problems += "This device uses a fixed volume; levels can't be changed."
        } else {
            for ((stream, label) in STREAMS) {
                try {
                    val target = targetVolume(am, stream)
                    am.setStreamVolume(stream, target, 0)
                    applied += "$label ${am.getStreamVolume(stream)}/${am.getStreamMaxVolume(stream)}"
                } catch (e: SecurityException) {
                    problems += "Could not set $label volume (${e.message})."
                } catch (e: IllegalArgumentException) {
                    problems += "Device rejected the $label volume (${e.message})."
                }
            }
        }

        val result = WakeupResult(dndOff, ringerNormal, applied, problems)
        Log.i(TAG, "Wake-up run: ${result.summary()}")
        return result
    }

    /**
     * [VOLUME_PERCENT] of the stream's usable range. Some streams have a non-zero
     * minimum, so the percentage is taken across min..max rather than 0..max.
     */
    private fun targetVolume(am: AudioManager, stream: Int): Int {
        val min = am.getStreamMinVolume(stream)
        val max = am.getStreamMaxVolume(stream)
        val target = min + (max - min) * (VOLUME_PERCENT / 100.0)
        return target.roundToInt().coerceIn(min, max)
    }

    private fun describeRinger(mode: Int): String = when (mode) {
        AudioManager.RINGER_MODE_SILENT -> "silent"
        AudioManager.RINGER_MODE_VIBRATE -> "vibrate"
        AudioManager.RINGER_MODE_NORMAL -> "normal"
        else -> "unknown"
    }

    private fun notificationManager(context: Context): NotificationManager =
        context.getSystemService(NotificationManager::class.java)
}
