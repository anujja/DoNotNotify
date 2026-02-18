package com.donotnotify.donotnotify

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import java.io.File

class NotificationHistoryStorage(private val context: Context) {

    companion object {
        private const val TAG = "NotificationHistoryStorage"
    }

    private val gson = Gson()
    private val historyFile = File(context.filesDir, "notification_history.json")
    private val sharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val historyDays get() = sharedPreferences.getInt("historyDays", 5)

    fun getHistory(): List<SimpleNotification> {
        if (!historyFile.exists()) {
            return emptyList()
        }
        return try {
            val json = historyFile.readText()
            val type = object : TypeToken<List<SimpleNotification>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: JsonSyntaxException) {
            Log.e(TAG, "Corrupted notification history file, deleting", e)
            historyFile.delete()
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error reading notification history", e)
            emptyList()
        }
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
        
        // Prune old notifications
        val cutoff = System.currentTimeMillis() - (historyDays * 24 * 60 * 60 * 1000L)
        val filteredHistory = history.filter { it.timestamp >= cutoff }

        val json = gson.toJson(filteredHistory)
        historyFile.writeText(json)
    }

    fun deleteNotification(notification: SimpleNotification) {
        val history = getHistory().toMutableList()
        history.remove(notification)
        val json = gson.toJson(history)
        historyFile.writeText(json)
    }

    fun deleteNotificationsFromPackage(packageName: String) {
        val history = getHistory().toMutableList()
        history.removeAll { it.packageName == packageName }
        val json = gson.toJson(history)
        historyFile.writeText(json)
    }

    fun updateAppLabelForPackage(packageName: String, newAppLabel: String) {
        val history = getHistory()
        val updatedHistory = history.map {
            if (it.packageName == packageName) {
                it.copy(appLabel = newAppLabel)
            } else {
                it
            }
        }
        val json = gson.toJson(updatedHistory)
        historyFile.writeText(json)
    }

    fun clearHistory() {
        if (historyFile.exists()) {
            historyFile.delete()
        }
    }
}
