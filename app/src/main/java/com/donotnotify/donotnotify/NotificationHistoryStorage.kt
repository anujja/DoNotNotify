package com.donotnotify.donotnotify

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

class NotificationHistoryStorage(private val context: Context) {

    private val gson = Gson()
    private val historyFile = File(context.filesDir, "notification_history.json")
    private val sharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val maxHistorySize get() = sharedPreferences.getInt("maxHistorySize", 500)

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

        // Manually find and remove the old notification, ignoring the timestamp
        val index = history.indexOfFirst {
            it.appLabel == notification.appLabel &&
            it.packageName == notification.packageName &&
            it.title == notification.title &&
            it.text == notification.text
        }
        if (index != -1) {
            history.removeAt(index)
        }

        // Add the new or updated notification to the top of the list
        history.add(0, notification)
        
        val trimmedHistory = if (history.size > maxHistorySize) history.subList(0, maxHistorySize) else history
        val json = gson.toJson(trimmedHistory)
        historyFile.writeText(json)
    }

    fun deleteNotification(notification: SimpleNotification) {
        val history = getHistory().toMutableList()
        history.remove(notification)
        val json = gson.toJson(history)
        historyFile.writeText(json)
    }

    fun clearHistory() {
        if (historyFile.exists()) {
            historyFile.delete()
        }
    }
}
