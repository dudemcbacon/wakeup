package com.wakeup.app

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import java.time.LocalDateTime

/** Everything the screen renders, read fresh from prefs plus live system state. */
data class WakeupUiState(
    val hour: Int,
    val minute: Int,
    val repeatMode: RepeatMode,
    val enabled: Boolean,
    val nextRun: LocalDateTime?,
    val dndGranted: Boolean,
    val exactAlarmsGranted: Boolean,
    val notificationsGranted: Boolean,
    val lastRunSummary: String?,
)

private fun readUiState(context: Context): WakeupUiState {
    val prefs = Prefs(context)
    return WakeupUiState(
        hour = prefs.hour,
        minute = prefs.minute,
        repeatMode = prefs.repeatMode,
        enabled = prefs.enabled,
        nextRun = if (prefs.enabled) {
            Scheduler.nextTrigger(prefs.hour, prefs.minute, prefs.repeatMode)
        } else {
            null
        },
        dndGranted = WakeupActions.hasDndAccess(context),
        exactAlarmsGranted = Scheduler.canScheduleExact(context),
        notificationsGranted = Notifier.hasNotificationPermission(context),
        lastRunSummary = prefs.lastRunSummary,
    )
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            WakeupTheme {
                val context = LocalContext.current
                var state by remember { mutableStateOf(readUiState(context)) }
                val refresh = { state = readUiState(context) }

                // Permission state can change while the user is away in Settings.
                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh() }

                val notificationLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { refresh() }

                WakeupScreen(
                    state = state,
                    onTimeSelected = { hour, minute ->
                        Prefs(context).apply {
                            this.hour = hour
                            this.minute = minute
                        }
                        Scheduler.sync(context)
                        refresh()
                    },
                    onRepeatModeSelected = { mode ->
                        Prefs(context).repeatMode = mode
                        Scheduler.sync(context)
                        refresh()
                    },
                    onEnabledChange = { enabled ->
                        Prefs(context).enabled = enabled
                        Scheduler.sync(context)
                        refresh()
                    },
                    onGrantDnd = {
                        openSettings(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                    },
                    onGrantExactAlarms = {
                        openSettings(
                            Intent(
                                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                Uri.fromParts("package", packageName, null),
                            ),
                        )
                    },
                    onGrantNotifications = {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    },
                    onRunNow = {
                        val result = WakeupActions.apply(context)
                        Prefs(context).lastRunSummary = "Manual test — ${result.summary()}"
                        Toast.makeText(
                            context,
                            if (result.succeeded) "Applied" else "Ran with problems",
                            Toast.LENGTH_SHORT,
                        ).show()
                        refresh()
                    },
                )
            }
        }
    }

    private fun openSettings(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.settings_unavailable, Toast.LENGTH_LONG).show()
        }
    }
}
