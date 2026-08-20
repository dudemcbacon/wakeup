package com.wakeup.app

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun WakeupScreen(
    state: WakeupUiState,
    onTimeSelected: (hour: Int, minute: Int) -> Unit,
    onRepeatModeSelected: (RepeatMode) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onGrantDnd: () -> Unit,
    onGrantExactAlarms: () -> Unit,
    onGrantNotifications: () -> Unit,
    onRunNow: () -> Unit,
) {
    var showTimePicker by remember { mutableStateOf(false) }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.app_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionLabel(stringResource(R.string.label_time), topSpacing = 32.dp)
            Text(
                text = formatTime(LocalTime.of(state.hour, state.minute)),
                style = MaterialTheme.typography.displaySmall,
            )
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(onClick = { showTimePicker = true }) {
                Text(stringResource(R.string.change_time))
            }

            SectionLabel(stringResource(R.string.label_repeat), topSpacing = 32.dp)
            // Explicit order: the enum's declaration order is not the display order.
            REPEAT_DISPLAY_ORDER.forEach { mode ->
                RepeatOption(
                    label = stringResource(mode.labelRes()),
                    selected = state.repeatMode == mode,
                    onSelect = { onRepeatModeSelected(mode) },
                )
            }

            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.schedule_enabled),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = state.enabled, onCheckedChange = onEnabledChange)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = state.nextRun
                    ?.let { stringResource(R.string.next_run, formatDateTime(it)) }
                    ?: stringResource(R.string.schedule_off),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider(Modifier.padding(vertical = 32.dp))

            SectionLabel(stringResource(R.string.label_permissions), topSpacing = 0.dp)
            PermissionRow(
                statusText = stringResource(
                    if (state.dndGranted) R.string.dnd_granted else R.string.dnd_missing,
                ),
                granted = state.dndGranted,
                buttonText = stringResource(R.string.grant_dnd),
                onGrant = onGrantDnd,
                required = true,
            )
            PermissionRow(
                statusText = stringResource(
                    if (state.exactAlarmsGranted) R.string.alarm_granted else R.string.alarm_missing,
                ),
                granted = state.exactAlarmsGranted,
                buttonText = stringResource(R.string.grant_alarm),
                onGrant = onGrantExactAlarms,
                required = true,
            )
            PermissionRow(
                statusText = stringResource(
                    if (state.notificationsGranted) {
                        R.string.notifications_granted
                    } else {
                        R.string.notifications_missing
                    },
                ),
                granted = state.notificationsGranted,
                buttonText = stringResource(R.string.grant_notifications),
                onGrant = onGrantNotifications,
                required = false,
            )

            HorizontalDivider(Modifier.padding(vertical = 32.dp))

            OutlinedButton(onClick = onRunNow, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.run_now))
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = state.lastRunSummary
                    ?.let { stringResource(R.string.last_run, it) }
                    ?: stringResource(R.string.last_run_never),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showTimePicker) {
        WakeupTimePickerDialog(
            initialHour = state.hour,
            initialMinute = state.minute,
            onDismiss = { showTimePicker = false },
            onConfirm = { hour, minute ->
                showTimePicker = false
                onTimeSelected(hour, minute)
            },
        )
    }
}

private val REPEAT_DISPLAY_ORDER =
    listOf(RepeatMode.DAILY, RepeatMode.WEEKDAYS, RepeatMode.ONCE)

@Composable
private fun SectionLabel(text: String, topSpacing: Dp) {
    if (topSpacing > 0.dp) Spacer(Modifier.height(topSpacing))
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun RepeatOption(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // null onClick: the whole row handles selection, so the button itself
        // must not be separately focusable.
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun PermissionRow(
    statusText: String,
    granted: Boolean,
    buttonText: String,
    onGrant: () -> Unit,
    required: Boolean,
) {
    Spacer(Modifier.height(12.dp))
    Text(
        text = statusText,
        style = MaterialTheme.typography.bodyMedium,
        color = when {
            granted -> MaterialTheme.colorScheme.onSurfaceVariant
            required -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
    if (!granted) {
        Spacer(Modifier.height(4.dp))
        OutlinedButton(onClick = onGrant) { Text(buttonText) }
    }
}

/**
 * A time picker in a dialog. Material 3 ships the picker but leaves the dialog
 * to the caller, and [BasicAlertDialog] is used rather than AlertDialog because
 * the clock dial is wider than AlertDialog's constraints allow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WakeupTimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit,
) {
    val context = LocalContext.current
    val pickerState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = DateFormat.is24HourFormat(context),
    )

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                // No fillMaxWidth here: letting the Column wrap its content keeps
                // the dialog at the time picker's natural width instead of
                // stretching it to the screen edges.
                Text(
                    text = stringResource(R.string.pick_time_title),
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.height(20.dp))
                TimePicker(state = pickerState)
                Spacer(Modifier.height(8.dp))
                // align(End) rather than fillMaxWidth(): filling would stretch the
                // enclosing Column to the screen width and the dialog with it.
                Row(modifier = Modifier.align(Alignment.End)) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(android.R.string.cancel))
                    }
                    TextButton(onClick = { onConfirm(pickerState.hour, pickerState.minute) }) {
                        Text(stringResource(android.R.string.ok))
                    }
                }
            }
        }
    }
}

private fun RepeatMode.labelRes(): Int = when (this) {
    RepeatMode.ONCE -> R.string.repeat_once
    RepeatMode.DAILY -> R.string.repeat_daily
    RepeatMode.WEEKDAYS -> R.string.repeat_weekdays
}

@Composable
private fun formatTime(time: LocalTime): String {
    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    return time.format(DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm a"))
}

@Composable
private fun formatDateTime(dateTime: LocalDateTime): String {
    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    val pattern = if (is24Hour) "EEE d MMM 'at' HH:mm" else "EEE d MMM 'at' h:mm a"
    return dateTime.format(DateTimeFormatter.ofPattern(pattern))
}
