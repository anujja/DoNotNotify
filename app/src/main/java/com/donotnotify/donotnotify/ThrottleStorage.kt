package com.donotnotify.donotnotify

import android.content.Context

/**
 * Last-pass timestamps for THROTTLE rules, one entry per window key (`rule:<id>`).
 * SharedPreferences keeps the state tiny and persistent: a service restart must not
 * re-open the floodgates in the middle of a burst. Entries are a single long each — pruning
 * when rules are deleted is deliberately left out (worst case is a few stale bytes).
 */
class ThrottleStorage(context: Context) {

    private val prefs = context.getSharedPreferences("throttle", Context.MODE_PRIVATE)

    private fun key(windowKey: String) = "last_pass_$windowKey"

    /** Epoch millis of the last pass for [windowKey], or null when none was recorded. */
    fun lastPassAt(windowKey: String): Long? {
        val value = prefs.getLong(key(windowKey), -1L)
        return if (value >= 0) value else null
    }

    fun recordPass(windowKey: String, now: Long) {
        prefs.edit().putLong(key(windowKey), now).apply()
    }
}
