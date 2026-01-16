package com.donotnotify.donotnotify

import android.app.Notification
import android.content.Context
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
    private lateinit var statsStorage: StatsStorage
    private lateinit var unmonitoredAppsStorage: UnmonitoredAppsStorage
    private lateinit var appInfoStorage: AppInfoStorage

    companion object {
        const val ACTION_HISTORY_UPDATED = "com.donotnotify.donotnotify.HISTORY_UPDATED"
        private const val DEBOUNCE_PERIOD_MS = 5000L
    }

    private val recentlyBlocked = mutableMapOf<String, Long>()

    override fun onCreate() {
        super.onCreate()
        ruleStorage = RuleStorage(this)
        notificationHistoryStorage = NotificationHistoryStorage(this)
        blockedNotificationHistoryStorage = BlockedNotificationHistoryStorage(this)
        statsStorage = StatsStorage(this)
        unmonitoredAppsStorage = UnmonitoredAppsStorage(this)
        appInfoStorage = AppInfoStorage(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val packageName = sbn.packageName
        val notification = sbn.notification
        val title = notification.extras.getCharSequence("android.title")?.toString()
        val text = notification.extras.getCharSequence("android.text")?.toString()
        val currentTime = System.currentTimeMillis()

        if (title.isNullOrBlank() && text.isNullOrBlank()) {
            Log.i(TAG, "Ignoring notification with no title and text from ${sbn.packageName}")
            return
        }

        var appLabel = resolveAppName(this, sbn).toString()
        val savedAppName = appInfoStorage.isAppInfoSaved(packageName)

        // Save App Info if not exists
        if (savedAppName == null || savedAppName == packageName) {
            try {
                // Extract app name from notification extras or fallback to package name
                val appName = appLabel

                // Extract app icon from notification
                val iconDrawable = notification.smallIcon?.loadDrawable(this)

                if (iconDrawable != null) {
                    appInfoStorage.saveAppInfo(packageName, appName, iconDrawable)
                } else {
                    Log.w(TAG, "Could not load icon for $packageName")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save app info for $packageName", e)
            }
        } else {
             if (savedAppName != packageName) {
                  appLabel = savedAppName
             }
        }

        Log.i(TAG, "Notification Received: App='${appLabel}', Title='${title}', Text='${text}'")

        val allRules = ruleStorage.getRules().toMutableList()
        val rulesForPackage = allRules.filter { it.packageName == packageName && it.isEnabled }

        val whitelistRules = rulesForPackage.filter { it.ruleType == RuleType.WHITELIST }
        val blacklistRules = rulesForPackage.filter { it.ruleType == RuleType.BLACKLIST }

        val hasWhitelistRules = whitelistRules.isNotEmpty()

        var rulesModified = false
        var matchesWhitelist = false
        for (rule in whitelistRules) {
            if (RuleMatcher.matches(rule, packageName, title, text)) {
                matchesWhitelist = true
                val ruleIndex = allRules.indexOf(rule)
                if (ruleIndex != -1) {
                    val updatedRule = rule.copy(hitCount = rule.hitCount + 1)
                    allRules[ruleIndex] = updatedRule
                    rulesModified = true
                }
                break
            }
        }

        var matchesBlacklist = false
        var matchedBlacklistRule: BlockerRule? = null
        for (rule in blacklistRules) {
            if (RuleMatcher.matches(rule, packageName, title, text)) {
                matchesBlacklist = true
                matchedBlacklistRule = rule
                val ruleIndex = allRules.indexOf(rule)
                if (ruleIndex != -1) {
                    val updatedRule = rule.copy(hitCount = rule.hitCount + 1)
                    allRules[ruleIndex] = updatedRule
                    rulesModified = true
                }
                break
            }
        }

        if (rulesModified) {
            ruleStorage.saveRules(allRules)
        }

        val isBlocked = (hasWhitelistRules && !matchesWhitelist) || matchesBlacklist
        val matchedRule: BlockerRule? = if (matchesBlacklist) matchedBlacklistRule else null

        if (isBlocked) {
            if (!matchesBlacklist) {
                Log.i(TAG, "Blocking notification from $packageName because it did not match any whitelist rule.")
            }
        }

        // 2. If it's a blockable notification, cancel it immediately
        val wasOngoing = (sbn.notification.flags and Notification.FLAG_ONGOING_EVENT) != 0
        if (isBlocked) {
            if (wasOngoing) {
                Log.w(TAG, "Attempting to block an ongoing notification. Cancellation may not be possible. Key: ${sbn.key}")
            }
            Log.i(TAG, "Blocking notification from $packageName. Matched rule: $matchedRule")
            cancelNotification(sbn.key)
        }

        // 3. Now, handle history and stats, using debounce logic ONLY for recording
        val notificationKey = "$packageName:$title:$text"
        val isDuplicate = recentlyBlocked.containsKey(notificationKey) && currentTime - (recentlyBlocked[notificationKey] ?: 0) < DEBOUNCE_PERIOD_MS

        if (isDuplicate) {
            Log.i(TAG, "Ignoring duplicate for history/stats: $notificationKey")
        } else {
            val simpleNotification = SimpleNotification(appLabel, packageName, title, text, currentTime, wasOngoing = wasOngoing)
            
            sbn.notification.contentIntent?.let { intent ->
                simpleNotification.id?.let { id ->
                    NotificationActionRepository.saveAction(id, intent)
                }
            }

            if (isBlocked) {
                recentlyBlocked[notificationKey] = currentTime
                val isNew = blockedNotificationHistoryStorage.saveNotification(simpleNotification)
                if (isNew) {
                    statsStorage.incrementBlockedNotificationsCount()
                }
            } else {
                if (!unmonitoredAppsStorage.isAppUnmonitored(packageName)) {
                    notificationHistoryStorage.saveNotification(simpleNotification)
                }
            }
            sendBroadcast(Intent(ACTION_HISTORY_UPDATED))
        }

        // Clean up old entries from the debounce map
        recentlyBlocked.entries.removeIf { (_, timestamp) -> currentTime - timestamp > DEBOUNCE_PERIOD_MS }
    }

    fun resolveAppName(context: Context, sbn: StatusBarNotification): CharSequence {
        val extras = sbn.notification.extras

        // 1. System-resolved app label (best)
        extras.getCharSequence("android.appInfo")?.let { return it }
        extras.getCharSequence("android.substituteAppName")?.let { return it }

        // 2. Same-profile PackageManager fallback
        val pkg = sbn.opPkg ?: sbn.packageName
        return try {
            val ai = context.packageManager.getApplicationInfo(pkg, 0)
            context.packageManager.getApplicationLabel(ai)
        } catch (_: Exception) {
            // 3. Honest last resort
            pkg
        }
    }

}
