package com.donotnotify.donotnotify

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import java.io.File

class BlockedNotificationHistoryStorage(private val context: Context) {

    companion object {
        private const val TAG = "BlockedNotificationHistoryStorage"
    }

    private val gson = Gson()
    private val historyFile = File(context.filesDir, "blocked_notification_history.json")
    private val maxHistorySize = 100 // Let's keep the history to a reasonable size

    fun getHistory(): List<SimpleNotification> {
        if (!historyFile.exists()) {
            return emptyList()
        }
        return try {
            val json = historyFile.readText()
            val type = object : TypeToken<List<SimpleNotification>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: JsonSyntaxException) {
            Log.e(TAG, "Corrupted blocked notification history file, deleting", e)
            historyFile.delete()
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error reading blocked notification history", e)
            emptyList()
        }
    }

    // Returns true if a new notification was added, false if it was a duplicate
    fun saveNotification(notification: SimpleNotification): Boolean {
        val history = getHistory().toMutableList()

        // Manually find and remove the old notification, ignoring the timestamp
        val index = history.indexOfFirst {
            it.appLabel == notification.appLabel &&
            it.packageName == notification.packageName &&
            it.title == notification.title &&
            it.text == notification.text
        }

        val isNew = index == -1
        if (!isNew) {
            history.removeAt(index)
        }

        // Add the new or updated notification to the top of the list
        history.add(0, notification)
        
        val trimmedHistory = if (history.size > maxHistorySize) history.subList(0, maxHistorySize) else history
        val json = gson.toJson(trimmedHistory)
        historyFile.writeText(json)
        
        return isNew
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
