package com.example.donotnotify

import android.content.Context

class StatsStorage(private val context: Context) {

    private val prefs = context.getSharedPreferences("stats", Context.MODE_PRIVATE)
    private val blockedCountKey = "blocked_count"

    fun getBlockedNotificationsCount(): Int {
        return prefs.getInt(blockedCountKey, 0)
    }

    fun incrementBlockedNotificationsCount() {
        val currentCount = getBlockedNotificationsCount()
        prefs.edit().putInt(blockedCountKey, currentCount + 1).apply()
    }
}
