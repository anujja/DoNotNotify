package com.donotnotify.donotnotify

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.donotnotify.donotnotify.setup.SetupState
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

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
        private val HEARTBEAT_INTERVAL_MS = TimeUnit.HOURS.toMillis(1)
    }

    private val heartbeatHandler = Handler(Looper.getMainLooper())
    private val heartbeatRunnable: Runnable = object : Runnable {
        override fun run() {
            SetupState.recordListenerConnected(this@NotificationBlockerService)
            heartbeatHandler.postDelayed(this, HEARTBEAT_INTERVAL_MS)
        }
    }

    private val recentlyBlocked = mutableMapOf<String, Long>()
    private val historyExecutor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "history-writer").apply { isDaemon = true }
    }

    private val stackPoster: StackedNotificationManager.StackPoster by lazy {
        StackedNotificationManager.AndroidStackPoster(
            context = this,
            activeProvider = {
                try {
                    (activeNotifications ?: emptyArray()).asList()
                        .filter { it.packageName == BuildConfig.APPLICATION_ID }
                        .map {
                            StackedNotificationManager.ActiveStackNote(
                                listenerKey = it.key,
                                groupKey = it.notification.group ?: ""
                            )
                        }
                } catch (e: Exception) {
                    Log.w(TAG, "activeNotifications unavailable", e)
                    emptyList()
                }
            },
            keyCanceller = { key -> cancelNotification(key) }
        )
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

        // Reentrancy guard (FIRST): our own re-posted stack notifications must never
        // re-enter rule/history/stack processing or we recurse infinitely.
        if (packageName == BuildConfig.APPLICATION_ID) return

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
                    historyExecutor.execute {
                        notificationHistoryStorage.updateAppLabelForPackage(packageName, appLabel)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save app info for $packageName", e)
            }
        } else {
            appLabel = savedAppName
        }

        Log.i(TAG, "Notification Received: App='${appLabel}', Title='${title}', Text='${text}'")

        // Authoritative guard: the listener process can survive a device-language change while
        // staying connected, so reconcile stale fail-closed prebuilt allowlists here, before
        // rules are read, regardless of whether the UI ever ran. Cheap + idempotent (no-op when
        // the locale is unchanged).
        PrebuiltRuleReconciler.reconcileIfLocaleChanged(this)

        // Pure precedence resolution (DENYLIST/allowlist-gating wins over STACK;
        // first enabled match wins; STACK never gates like allowlist).
        val rules = ruleStorage.getRules()
        val wasOngoing = (sbn.notification.flags and Notification.FLAG_ONGOING_EVENT) != 0
        val decision = RuleMatcher.planNotificationDecision(rules, packageName, title, text, wasOngoing)
        val isBlocked = decision.isBlocked
        val matchedRule: BlockerRule? = decision.matchedDenylistRule
        val matchedRuleIndices = decision.matchedRuleIndices

        if (isBlocked && matchedRule == null) {
            Log.i(TAG, "Blocking notification from $packageName because it did not match any allowlist rule.")
        }

        if (isBlocked) {
            // Cancel immediately on binder thread
            if (wasOngoing) {
                Log.w(TAG, "Attempting to block an ongoing notification. Cancellation may not be possible. Key: ${sbn.key}")
            }
            Log.i(TAG, "Blocking notification from $packageName. Matched rule: $matchedRule")
            cancelNotification(sbn.key)
        } else if (decision.shouldStack) {
            // STACK: post the replacement FIRST; only cancel the source if the
            // re-post succeeded (post-then-cancel — never lose a notification).
            val stackRule = decision.matchedStackRule!!
            val groupKey = StackedNotificationManager.groupKeyFor(packageName, stackRule)
            // Resolve the large-icon bitmap from cached storage before the lock
            // (no PackageManager call on the binder thread).
            val largeIcon = appInfoStorage.getAppIcon(packageName)
            val entry = StackedNotificationManager.Entry(
                sbnKey = sbn.key,
                title = title,
                text = text,
                timestamp = currentTime,
                contentIntent = sbn.notification.contentIntent,
                sourceVisibility = sbn.notification.visibility,
                childId = 0
            )
            val posted = StackedNotificationManager.absorbAndPost(
                stackPoster, groupKey, appLabel, entry, largeIcon
            )
            if (posted) {
                cancelNotification(sbn.key)
            } else {
                Log.w(TAG, "Stack post failed/blocked; leaving source intact: $packageName")
            }
            // Stacked notifications are NOT "blocked": they fall through to the
            // normal-history branch below (blocked count is not incremented).
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
        val notificationKey = sbn.key
        val isDuplicate = recentlyBlocked.containsKey(notificationKey) && currentTime - (recentlyBlocked[notificationKey] ?: 0) < DEBOUNCE_PERIOD_MS

        if (isDuplicate) {
            Log.i(TAG, "Ignoring duplicate for history/stats: $notificationKey")
            // Still persist hitCount updates asynchronously
            if (updatedRules != null) {
                historyExecutor.execute {
                    try {
                        ruleStorage.saveRules(updatedRules)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to save rules", e)
                    }
                }
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
                try {
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
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save notification data", e)
                }
            }
        }

        // Clean up old entries from the debounce map
        recentlyBlocked.entries.removeIf { (_, timestamp) -> currentTime - timestamp > DEBOUNCE_PERIOD_MS }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // A live device-language change: reconcile stale fail-closed prebuilt allowlists promptly.
        PrebuiltRuleReconciler.reconcileIfLocaleChanged(this)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        SetupState.recordListenerConnected(this)
        PrebuiltRuleReconciler.reconcileIfLocaleChanged(this)
        heartbeatHandler.removeCallbacks(heartbeatRunnable)
        heartbeatHandler.postDelayed(heartbeatRunnable, HEARTBEAT_INTERVAL_MS)
        Log.i(TAG, "Listener connected")
        // Restart-safety: cancel any of our own stacks that survived a process
        // restart and clear the in-memory registry (no orphans / no id reuse).
        try {
            StackedNotificationManager.reconcileOnConnect(stackPoster)
        } catch (e: Exception) {
            Log.w(TAG, "reconcileOnConnect failed", e)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        if (sbn == null || sbn.packageName != BuildConfig.APPLICATION_ID) return
        // One of our own stack notifications was dismissed — keep the registry in sync.
        try {
            StackedNotificationManager.onOurNotificationRemoved(
                stackPoster, sbn.id, getString(R.string.app_name)
            )
        } catch (e: Exception) {
            Log.w(TAG, "onOurNotificationRemoved failed", e)
        }
    }

    override fun onListenerDisconnected() {
        heartbeatHandler.removeCallbacks(heartbeatRunnable)
        super.onListenerDisconnected()
        Log.w(TAG, "Listener disconnected — requesting rebind")
        try {
            requestRebind(ComponentName(this, NotificationBlockerService::class.java))
        } catch (e: Exception) {
            Log.e(TAG, "requestRebind failed", e)
        }
    }

    override fun onDestroy() {
        heartbeatHandler.removeCallbacks(heartbeatRunnable)
        super.onDestroy()
        historyExecutor.shutdown()
        try {
            if (!historyExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                historyExecutor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            historyExecutor.shutdownNow()
        }
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
