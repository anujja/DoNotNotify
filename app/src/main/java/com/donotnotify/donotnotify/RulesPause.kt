package com.donotnotify.donotnotify

import android.content.Context

/**
 * The Rules tab's master switch. Pausing leaves every rule's own enabled flag untouched and
 * makes the listener behave as if there were no rules at all, so resuming restores exactly the
 * set that was active before. Notifications are still recorded in History while paused.
 */
object RulesPause {
    private const val PREFS = "settings"
    private const val KEY_PAUSED = "rules_paused"

    fun isPaused(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PAUSED, false)

    fun setPaused(context: Context, paused: Boolean) {
        prefs(context).edit().putBoolean(KEY_PAUSED, paused).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
