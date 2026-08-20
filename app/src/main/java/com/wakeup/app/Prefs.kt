package com.wakeup.app

import android.content.Context
import androidx.core.content.edit

/** How often the schedule fires. */
enum class RepeatMode {
    /** Fire once at the next occurrence, then disable the schedule. */
    ONCE,

    /** Fire every day. */
    DAILY,

    /** Fire Monday to Friday, skipping the weekend. */
    WEEKDAYS,
    ;

    companion object {
        fun fromName(name: String?): RepeatMode =
            entries.firstOrNull { it.name == name } ?: DAILY
    }
}

/** The handful of settings this app needs, backed by SharedPreferences. */
class Prefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    /** Hour of day, 0–23. */
    var hour: Int
        get() = sp.getInt(KEY_HOUR, DEFAULT_HOUR)
        set(value) = sp.edit { putInt(KEY_HOUR, value.coerceIn(0, 23)) }

    /** Minute of hour, 0–59. */
    var minute: Int
        get() = sp.getInt(KEY_MINUTE, DEFAULT_MINUTE)
        set(value) = sp.edit { putInt(KEY_MINUTE, value.coerceIn(0, 59)) }

    var repeatMode: RepeatMode
        get() = RepeatMode.fromName(sp.getString(KEY_REPEAT, null))
        set(value) = sp.edit { putString(KEY_REPEAT, value.name) }

    /** Whether the alarm is currently armed. */
    var enabled: Boolean
        get() = sp.getBoolean(KEY_ENABLED, false)
        set(value) = sp.edit { putBoolean(KEY_ENABLED, value) }

    /** Human-readable outcome of the most recent run, shown on the main screen. */
    var lastRunSummary: String?
        get() = sp.getString(KEY_LAST_RUN, null)
        set(value) = sp.edit { putString(KEY_LAST_RUN, value) }

    private companion object {
        const val FILE_NAME = "wakeup_prefs"
        const val KEY_HOUR = "hour"
        const val KEY_MINUTE = "minute"
        const val KEY_REPEAT = "repeat_mode"
        const val KEY_ENABLED = "enabled"
        const val KEY_LAST_RUN = "last_run_summary"
        const val DEFAULT_HOUR = 7
        const val DEFAULT_MINUTE = 0
    }
}
