package com.example.donotnotify

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

class BlockedNotificationHistoryStorage(private val context: Context) {

    private val gson = Gson()
    private val historyFile = File(context.filesDir, "blocked_notification_history.json")
    private val maxHistorySize = 100 // Let's keep the history to a reasonable size

    fun getHistory(): List<SimpleNotification> {
        if (!historyFile.exists()) {
            return emptyList()
        }
        val json = historyFile.readText()
        val type = object : TypeToken<List<SimpleNotification>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    fun saveNotification(notification: SimpleNotification) {
        val history = getHistory().toMutableList()
        history.add(0, notification) // Add to the top of the list
        val trimmedHistory = if (history.size > maxHistorySize) history.subList(0, maxHistorySize) else history
        val json = gson.toJson(trimmedHistory)
        historyFile.writeText(json)
    }

    fun clearHistory() {
        if (historyFile.exists()) {
            historyFile.delete()
        }
    }
}
