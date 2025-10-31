package com.example.donotnotify

import android.content.Intent
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import java.util.regex.PatternSyntaxException

class NotificationBlockerService : NotificationListenerService() {

    private val TAG = "NotificationBlockerService"
    private lateinit var ruleStorage: RuleStorage
    private lateinit var notificationHistoryStorage: NotificationHistoryStorage
    private lateinit var blockedNotificationHistoryStorage: BlockedNotificationHistoryStorage
    private lateinit var statsStorage: StatsStorage

    companion object {
        const val ACTION_HISTORY_UPDATED = "com.example.donotnotify.HISTORY_UPDATED"
        private const val DEBOUNCE_PERIOD_MS = 5000L
    }

    private val recentlyBlocked = mutableMapOf<String, Long>()

    override fun onCreate() {
        super.onCreate()
        ruleStorage = RuleStorage(this)
        notificationHistoryStorage = NotificationHistoryStorage(this)
        blockedNotificationHistoryStorage = BlockedNotificationHistoryStorage(this)
        statsStorage = StatsStorage(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val packageName = sbn.packageName
        val notification = sbn.notification
        val title = notification.extras.getCharSequence("android.title")?.toString()
        val text = notification.extras.getCharSequence("android.text")?.toString()
        val notificationKey = "$packageName:$title:$text"
        val currentTime = System.currentTimeMillis()

        // Debounce logic
        if (recentlyBlocked.containsKey(notificationKey) && currentTime - (recentlyBlocked[notificationKey] ?: 0) < DEBOUNCE_PERIOD_MS) {
            Log.i(TAG, "Ignoring duplicate notification: $notificationKey")
            return
        }
        recentlyBlocked.entries.removeIf { (_, timestamp) -> currentTime - timestamp > DEBOUNCE_PERIOD_MS }

        val appLabel = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }

        Log.i(TAG, "Notification Received: App='${appLabel}', Title='${title}', Text='${text}'")

        val simpleNotification = SimpleNotification(appLabel, packageName, title, text, currentTime)
        var isBlocked = false

        val rules = ruleStorage.getRules()
        for (rule in rules) {
            try {
                val appMatch = rule.appName.isNullOrBlank() || rule.appName == packageName
                val titleMatch = rule.titleRegex.isNullOrBlank() || (title?.matches(rule.titleRegex.toRegex()) ?: false)
                val textMatch = rule.textRegex.isNullOrBlank() || (text?.matches(rule.textRegex.toRegex()) ?: false)

                if (appMatch && titleMatch && textMatch) {
                    isBlocked = true
                    Log.i(TAG, "Blocking notification from $packageName based on rule: $rule")
                    cancelNotification(sbn.key)
                    recentlyBlocked[notificationKey] = currentTime
                    statsStorage.incrementBlockedNotificationsCount()
                    break 
                }
            } catch (e: PatternSyntaxException) {
                Log.e(TAG, "Invalid regex in rule: $rule", e)
            }
        }

        if (isBlocked) {
            blockedNotificationHistoryStorage.saveNotification(simpleNotification)
        } else {
            notificationHistoryStorage.saveNotification(simpleNotification)
        }

        sendBroadcast(Intent(ACTION_HISTORY_UPDATED))
    }
}
