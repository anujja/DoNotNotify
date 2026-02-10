package com.donotnotify.donotnotify

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

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
    private val historyExecutor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "history-writer").apply { isDaemon = true }
    }

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

                if (savedAppName == packageName) {
                    notificationHistoryStorage.updateAppLabelForPackage(packageName, appLabel)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save app info for $packageName", e)
            }
        } else {
            appLabel = savedAppName
        }

        Log.i(TAG, "Notification Received: App='${appLabel}', Title='${title}', Text='${text}'")

        // Single-loop rule evaluation — eliminates intermediate list allocations
        val rules = ruleStorage.getRules()
        var hasWhitelistRules = false
        var matchesWhitelist = false
        var matchesBlacklist = false
        var matchedBlacklistRule: BlockerRule? = null
        val matchedRuleIndices = mutableListOf<Int>()

        for ((index, rule) in rules.withIndex()) {
            if (rule.packageName != packageName || !rule.isEnabled) continue
            when (rule.ruleType) {
                RuleType.WHITELIST -> {
                    hasWhitelistRules = true
                    if (!matchesWhitelist && RuleMatcher.matches(rule, packageName, title, text)) {
                        matchesWhitelist = true
                        matchedRuleIndices.add(index)
                    }
                }
                RuleType.BLACKLIST -> {
                    if (!matchesBlacklist && RuleMatcher.matches(rule, packageName, title, text)) {
                        matchesBlacklist = true
                        matchedBlacklistRule = rule
                        matchedRuleIndices.add(index)
                    }
                }
            }
        }

        val isBlocked = (hasWhitelistRules && !matchesWhitelist) || matchesBlacklist
        val matchedRule: BlockerRule? = if (matchesBlacklist) matchedBlacklistRule else null

        if (isBlocked && !matchesBlacklist) {
            Log.i(TAG, "Blocking notification from $packageName because it did not match any whitelist rule.")
        }

        // Cancel immediately on binder thread
        val wasOngoing = (sbn.notification.flags and Notification.FLAG_ONGOING_EVENT) != 0
        if (isBlocked) {
            if (wasOngoing) {
                Log.w(TAG, "Attempting to block an ongoing notification. Cancellation may not be possible. Key: ${sbn.key}")
            }
            Log.i(TAG, "Blocking notification from $packageName. Matched rule: $matchedRule")
            cancelNotification(sbn.key)
        }

        // Prepare hitCount updates (deferred toMutableList only when needed)
        val updatedRules = if (matchedRuleIndices.isNotEmpty()) {
            val mutableRules = rules.toMutableList()
            for (idx in matchedRuleIndices) {
                val r = mutableRules[idx]
                mutableRules[idx] = r.copy(hitCount = r.hitCount + 1)
            }
            mutableRules as List<BlockerRule>
        } else null

        // Debounce check on binder thread
        val notificationKey = "$packageName:$title:$text"
        val isDuplicate = recentlyBlocked.containsKey(notificationKey) && currentTime - (recentlyBlocked[notificationKey] ?: 0) < DEBOUNCE_PERIOD_MS

        if (isDuplicate) {
            Log.i(TAG, "Ignoring duplicate for history/stats: $notificationKey")
            // Still persist hitCount updates asynchronously
            if (updatedRules != null) {
                historyExecutor.execute { ruleStorage.saveRules(updatedRules) }
            }
        } else {
            val simpleNotification = SimpleNotification(appLabel, packageName, title, text, currentTime, wasOngoing = wasOngoing)

            sbn.notification.contentIntent?.let { intent ->
                simpleNotification.id?.let { id ->
                    NotificationActionRepository.saveAction(id, intent)
                }
            }

            if (isBlocked) {
                recentlyBlocked[notificationKey] = currentTime
            }

            // Move all I/O to background executor
            historyExecutor.execute {
                updatedRules?.let { ruleStorage.saveRules(it) }
                if (isBlocked) {
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
        }

        // Clean up old entries from the debounce map
        recentlyBlocked.entries.removeIf { (_, timestamp) -> currentTime - timestamp > DEBOUNCE_PERIOD_MS }
    }

    override fun onDestroy() {
        super.onDestroy()
        historyExecutor.shutdown()
    }

    fun resolveAppName(context: Context, sbn: StatusBarNotification): CharSequence {
        val extras = sbn.notification.extras

        // 1. System-resolved app label (best)
        extras.getCharSequence("android.substituteAppName")?.let { return it }

        // 2. Same-profile PackageManager fallback
        val pkg = sbn.opPkg
        return try {
            val ai = context.packageManager.getApplicationInfo(pkg, 0)
            context.packageManager.getApplicationLabel(ai)
        } catch (_: Exception) {
            // 3. Honest last resort
            pkg
        }
    }

}
