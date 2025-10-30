package com.example.donotnotify

import android.content.Intent
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class NotificationBlockerService : NotificationListenerService() {

    private val TAG = "NotificationBlockerService"
    private lateinit var ruleStorage: RuleStorage
    private lateinit var notificationHistoryStorage: NotificationHistoryStorage
    private lateinit var blockedNotificationHistoryStorage: BlockedNotificationHistoryStorage

    companion object {
        const val ACTION_HISTORY_UPDATED = "com.example.donotnotify.HISTORY_UPDATED"
    }

    override fun onCreate() {
        super.onCreate()
        ruleStorage = RuleStorage(this)
        notificationHistoryStorage = NotificationHistoryStorage(this)
        blockedNotificationHistoryStorage = BlockedNotificationHistoryStorage(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val packageName = sbn.packageName
        val notification = sbn.notification
        val title = notification.extras.getCharSequence("android.title")?.toString()
        val text = notification.extras.getCharSequence("android.text")?.toString()

        val appLabel = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName // Fallback to package name
        }

        val simpleNotification = SimpleNotification(appLabel, packageName, title, text)
        var isBlocked = false

        // Check against blocking rules
        val rules = ruleStorage.getRules()
        for (rule in rules) {
            if (packageName == rule.appName) {
                var shouldBlock = false

                if (rule.titleRegex != null && title != null) {
                    if (title.matches(rule.titleRegex.toRegex())) {
                        shouldBlock = true
                    }
                }

                if (!shouldBlock && rule.textRegex != null && text != null) {
                    if (text.matches(rule.textRegex.toRegex())) {
                        shouldBlock = true
                    }
                }

                if (shouldBlock) {
                    isBlocked = true
                    Log.i(TAG, "Blocking notification from $packageName based on rule: $rule")
                    cancelNotification(sbn.key)
                    break // Stop checking other rules for this notification
                }
            }
        }

        if (isBlocked) {
            blockedNotificationHistoryStorage.saveNotification(simpleNotification)
        } else {
            notificationHistoryStorage.saveNotification(simpleNotification)
        }

        // Send a broadcast to signal that the history has been updated
        sendBroadcast(Intent(ACTION_HISTORY_UPDATED))

        Log.i(TAG, "Notification received from $packageName")
        Log.i(TAG, "Title: $title")
        Log.i(TAG, "Text: $text")
    }
}
