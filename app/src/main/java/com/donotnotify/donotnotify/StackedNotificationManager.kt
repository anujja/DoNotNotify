package com.donotnotify.donotnotify

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Re-posts STACK-matched notifications under our own package as a single native
 * notification group (summary + children), reducing clutter without dropping
 * notifications.
 *
 * The mechanism is cancel-and-repost (a [android.service.notification.NotificationListenerService]
 * cannot group another app's notification in place). To avoid ever losing a
 * notification, [absorbAndPost] is strictly ordered
 * precondition → plan → post → commit → post-commit cleanup: nothing destructive
 * (the source cancel, evictions) happens until the replacement is durably posted.
 *
 * v1 limitations (documented):
 * - MessagingStyle / RemoteInput / custom RemoteViews richness is lost on repost.
 * - Late silent cancellations by the source app are NOT synced.
 * - A stack that survives a process restart is cleared (not rebuilt) on the next
 *   [reconcileOnConnect].
 */
object StackedNotificationManager {

    private const val TAG = "StackedNotifManager"

    // Channels are per-rule now — see StackChannels.channelIdFor(). The old shared
    // "stacked_notifications" channel lives on only as StackChannels.LEGACY_CHANNEL_ID, which
    // migration snapshots (to seed the new channels) and then deletes.
    const val GROUP_KEY_PREFIX = "dnn_stack:"

    const val MAX_CHILDREN_PER_STACK = 20
    const val MAX_STACKS = 50
    const val STACK_TTL_MS = 24L * 60L * 60L * 1000L

    /** Max InboxStyle lines shown in the summary. */
    private const val SUMMARY_MAX_LINES = 7

    private val groups = ConcurrentHashMap<String, MutableList<Entry>>()
    private val summaryIds = ConcurrentHashMap<String, Int>()
    private val lastTouched = ConcurrentHashMap<String, Long>()

    /** Channel each group posts on. Remembered because a summary rebuild after a child is
     *  dismissed only has the group key to work from — the rule is long out of scope. */
    private val channelIds = ConcurrentHashMap<String, String>()

    /** Distinct sbnKeys ever seen per group — drives the summary count independent
     *  of evicted children. Cleared in every group-deletion path. */
    private val cumulativeCounts = ConcurrentHashMap<String, Int>()

    // Offset clear of HealthCheckWorker's id.
    private val idCounter = AtomicInteger(100_000)
    private fun nextId(): Int = idCounter.getAndIncrement()

    data class Entry(
        val sbnKey: String,
        val title: String?,
        val text: String?,
        val timestamp: Long,
        val contentIntent: PendingIntent?,
        val sourceVisibility: Int,
        val childId: Int
    )

    enum class PostBlock { OK, NOTIFICATIONS_DISABLED, CHANNEL_DISABLED }

    /** Records of our currently-posted stack notifications, addressable by the
     *  listener key (the only stable handle after a process restart). */
    data class ActiveStackNote(val listenerKey: String, val groupKey: String)

    /**
     * Side-effect seam. The real impl wraps [NotificationManagerCompat] plus the
     * service's `getActiveNotifications()` / `cancelNotification(key)`. An in-memory
     * fake (addressable by both int id and listener key) makes the whole flow
     * unit-testable on plain JVM.
     */
    interface StackPoster {
        fun areEnabled(): Boolean
        /** Importance of [channelId], or [NotificationManager.IMPORTANCE_DEFAULT] when channels
         *  don't apply (API < 26). Per-rule now: each STACK rule has its own channel, so a rule
         *  the user silenced must not silence the others. */
        fun channelImportance(channelId: String): Int
        fun activeStackNotifications(): List<ActiveStackNote>
        fun postChild(
            plan: AbsorbPlan,
            groupKey: String,
            channelId: String,
            appLabel: String,
            entry: Entry,
            largeIcon: Bitmap?
        )
        fun postSummary(plan: AbsorbPlan, groupKey: String, channelId: String, appLabel: String)
        fun cancel(id: Int)
        fun cancelByKey(key: String)
    }

    /** Immutable plan produced by the pure [planAbsorb]. No Android types. */
    data class AbsorbPlan(
        val childId: Int,
        val isUpdate: Boolean,
        val pingSuppressed: Boolean,
        val newSummary: Boolean,
        val summaryId: Int,
        val committedEntries: List<Entry>,
        val cumulativeCount: Int,
        val evictChildIds: List<Int>,
        val evictGroups: List<EvictGroup>,
        val effectiveVisibility: Int,
        val redactPublic: Boolean,
        val summaryVisibility: Int,
        val summaryRedactPublic: Boolean,
        val summaryLines: List<String>,
        val overflowCount: Int
    )

    data class EvictGroup(val groupKey: String, val summaryId: Int?, val childIds: List<Int>)

    /** Snapshot of registry state for one absorb call — keeps [planAbsorb] pure. */
    data class RegistrySnapshot(
        val existingEntries: List<Entry>,
        val existingSummaryId: Int?,
        val existingCumulative: Int,
        val totalGroups: Int,
        val isNewGroup: Boolean,
        val ttlExpiredGroups: List<EvictGroup>,
        val maxStacksEviction: EvictGroup?
    )

    // ---- canPost --------------------------------------------------------------

    /** UI convenience: typed post-capability for a [Context] (used by the Rules
     *  screen warning). The transactional path uses [postBlockVia] instead so it
     *  stays JVM-testable through the poster seam. */
    fun canPost(context: Context, rule: BlockerRule): PostBlock {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return PostBlock.NOTIFICATIONS_DISABLED
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java)
            val channel = nm?.getNotificationChannel(StackChannels.channelIdFor(rule))
                ?: return PostBlock.CHANNEL_DISABLED
            if (channel.importance == NotificationManager.IMPORTANCE_NONE) {
                return PostBlock.CHANNEL_DISABLED
            }
        }
        return PostBlock.OK
    }

    /** Typed post-capability derived purely from the poster seam (no Android types). */
    fun postBlockVia(poster: StackPoster, channelId: String): PostBlock = when {
        !poster.areEnabled() -> PostBlock.NOTIFICATIONS_DISABLED
        poster.channelImportance(channelId) == NotificationManager.IMPORTANCE_NONE ->
            PostBlock.CHANNEL_DISABLED
        else -> PostBlock.OK
    }

    // ---- group key ------------------------------------------------------------

    private const val NULL_SENTINEL = "␀NULL␀"

    /**
     * One stack per *distinct full rule signature*. [BlockerRule] has no stable id,
     * so the key is a canonical, injective serialization (length-prefixed fields +
     * a distinct null sentinel, so no two distinct signatures can collide via
     * delimiter ambiguity) hashed with SHA-256 → base64url.
     */
    fun groupKeyFor(packageName: String, rule: BlockerRule): String {
        val cfg = rule.advancedConfig
        val fields = listOf(
            rule.packageName,
            rule.titleFilter,
            rule.titleMatchType.name,
            rule.textFilter,
            rule.textMatchType.name,
            rule.ruleType.name,
            cfg?.isTimeLimitEnabled?.toString(),
            cfg?.startTimeHour?.toString(),
            cfg?.startTimeMinute?.toString(),
            cfg?.endTimeHour?.toString(),
            cfg?.endTimeMinute?.toString()
        )
        val canon = buildString {
            for (f in fields) {
                val s = f ?: NULL_SENTINEL
                append(s.length).append(':').append(s).append(' ')
            }
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canon.toByteArray(Charsets.UTF_8))
        // Hex (not android.util.Base64): dependency-free, works on all min-SDK levels.
        val hex = buildString(digest.size * 2) {
            for (b in digest) {
                append("0123456789abcdef"[(b.toInt() ushr 4) and 0xF])
                append("0123456789abcdef"[b.toInt() and 0xF])
            }
        }
        return "$GROUP_KEY_PREFIX$packageName:$hex"
    }

    // ---- visibility helpers ---------------------------------------------------

    /** Normalize an unknown/out-of-range visibility to the conservative PRIVATE. */
    private fun normalizeVisibility(v: Int): Int = when (v) {
        Notification.VISIBILITY_PUBLIC,
        Notification.VISIBILITY_PRIVATE,
        Notification.VISIBILITY_SECRET -> v
        else -> Notification.VISIBILITY_PRIVATE
    }

    private fun shouldRedact(v: Int): Boolean = v != Notification.VISIBILITY_PUBLIC

    // ---- pure planning --------------------------------------------------------

    /**
     * Pure: computes every decision against [snapshot] without touching Android or
     * mutating the registry.
     */
    fun planAbsorb(
        snapshot: RegistrySnapshot,
        entry: Entry,
        now: Long,
        idAllocator: () -> Int,
        maxChildren: Int = MAX_CHILDREN_PER_STACK
    ): AbsorbPlan {
        val existing = snapshot.existingEntries
        val sameKeyIndex = existing.indexOfFirst { it.sbnKey == entry.sbnKey }
        val isUpdate = sameKeyIndex >= 0

        val childId = if (isUpdate) existing[sameKeyIndex].childId else idAllocator()
        val placed = entry.copy(childId = childId)

        // Build the post-update list.
        val working = existing.toMutableList()
        if (isUpdate) working[sameKeyIndex] = placed else working.add(placed)

        // Per-stack cap: drop oldest, cancel their child ids post-commit.
        val evictChildIds = mutableListOf<Int>()
        while (working.size > maxChildren) {
            val removed = working.removeAt(0)
            evictChildIds.add(removed.childId)
        }

        val cumulative = if (isUpdate) {
            snapshot.existingCumulative
        } else {
            snapshot.existingCumulative + 1
        }

        // Summary content: latest lines first.
        val latest = working.sortedByDescending { it.timestamp }
        val lineEntries = latest.take(SUMMARY_MAX_LINES)
        val summaryLines = lineEntries.map { e ->
            listOfNotNull(e.title?.takeIf { it.isNotBlank() }, e.text?.takeIf { it.isNotBlank() })
                .joinToString(": ")
        }
        val overflowCount = (cumulative - summaryLines.size).coerceAtLeast(0)

        val childVis = normalizeVisibility(entry.sourceVisibility)
        val summaryVis = working
            .map { normalizeVisibility(it.sourceVisibility) }
            .minOrNull() ?: childVis

        val newSummary = snapshot.existingSummaryId == null
        val summaryId = snapshot.existingSummaryId ?: idAllocator()

        val evictGroups = buildList {
            addAll(snapshot.ttlExpiredGroups)
            snapshot.maxStacksEviction?.let { if (snapshot.isNewGroup) add(it) }
        }

        return AbsorbPlan(
            childId = childId,
            isUpdate = isUpdate,
            pingSuppressed = isUpdate,
            newSummary = newSummary,
            summaryId = summaryId,
            committedEntries = working,
            cumulativeCount = cumulative,
            evictChildIds = evictChildIds,
            evictGroups = evictGroups,
            effectiveVisibility = childVis,
            redactPublic = shouldRedact(childVis),
            summaryVisibility = summaryVis,
            summaryRedactPublic = shouldRedact(summaryVis),
            summaryLines = summaryLines,
            overflowCount = overflowCount
        )
    }

    private fun snapshotFor(groupKey: String, now: Long): RegistrySnapshot {
        val existing = groups[groupKey]?.toList() ?: emptyList()
        val isNewGroup = !groups.containsKey(groupKey)

        val ttlExpired = lastTouched.entries
            .filter { it.key != groupKey && now - it.value > STACK_TTL_MS }
            .map { evictGroupFor(it.key) }

        val maxStacksEviction: EvictGroup? =
            if (isNewGroup && groups.size >= MAX_STACKS) {
                lastTouched.entries
                    .filter { it.key != groupKey }
                    .minByOrNull { it.value }
                    ?.let { evictGroupFor(it.key) }
            } else null

        return RegistrySnapshot(
            existingEntries = existing,
            existingSummaryId = summaryIds[groupKey],
            existingCumulative = cumulativeCounts[groupKey] ?: 0,
            totalGroups = groups.size,
            isNewGroup = isNewGroup,
            ttlExpiredGroups = ttlExpired,
            maxStacksEviction = maxStacksEviction
        )
    }

    private fun evictGroupFor(groupKey: String): EvictGroup =
        EvictGroup(
            groupKey = groupKey,
            summaryId = summaryIds[groupKey],
            childIds = groups[groupKey]?.map { it.childId } ?: emptyList()
        )

    private fun forgetGroup(groupKey: String) {
        groups.remove(groupKey)
        summaryIds.remove(groupKey)
        lastTouched.remove(groupKey)
        cumulativeCounts.remove(groupKey)
        channelIds.remove(groupKey)
    }

    // ---- absorb (transactional) ----------------------------------------------

    /**
     * Returns true only after the child **and** summary are confirmed posted and the
     * registry is consistently committed. The caller must NOT cancel the source
     * notification unless this returns true.
     */
    @Synchronized
    fun absorbAndPost(
        poster: StackPoster,
        groupKey: String,
        channelId: String,
        appLabel: String,
        entry: Entry,
        largeIcon: Bitmap?
    ): Boolean {
        // 1. Precondition (via the poster seam — keeps this JVM-testable). Per-rule now: one
        //    rule's silenced channel must not block a different rule's stack.
        val block = postBlockVia(poster, channelId)
        if (block != PostBlock.OK) {
            Log.w(TAG, "Skip stack ($block) — source left intact: $groupKey")
            return false
        }

        // 2. Plan only (no mutation, no cancels).
        val now = entry.timestamp
        val snapshot = snapshotFor(groupKey, now)
        val plan = planAbsorb(snapshot, entry, now, ::nextId)

        // 3. Post (non-destructive: a same-key update re-notifies the same id, which
        //    atomically replaces the prior notification).
        var childPosted = false
        try {
            poster.postChild(
                plan, groupKey, channelId, appLabel, entry.copy(childId = plan.childId), largeIcon
            )
            childPosted = true
            poster.postSummary(plan, groupKey, channelId, appLabel)
        } catch (e: Exception) {
            Log.e(TAG, "absorbAndPost post failed — rolling back", e)
            // 4. Failure → rollback. Never cancel a reused-update child (that would
            //    delete a still-valid prior notification). No registry mutation or
            //    eviction has run yet, so nothing else to undo.
            if (childPosted && !plan.isUpdate) poster.cancel(plan.childId)
            if (plan.newSummary) poster.cancel(plan.summaryId)
            return false
        }

        // 5. Commit.
        groups[groupKey] = plan.committedEntries.toMutableList()
        summaryIds[groupKey] = plan.summaryId
        lastTouched[groupKey] = now
        cumulativeCounts[groupKey] = plan.cumulativeCount
        channelIds[groupKey] = channelId

        // 6. Post-commit cleanup.
        for (id in plan.evictChildIds) safeCancel(poster, id)
        for (g in plan.evictGroups) {
            g.summaryId?.let { safeCancel(poster, it) }
            g.childIds.forEach { safeCancel(poster, it) }
            forgetGroup(g.groupKey)
        }
        return true
    }

    private fun safeCancel(poster: StackPoster, id: Int) {
        try {
            poster.cancel(id)
        } catch (e: Exception) {
            Log.w(TAG, "cancel($id) failed during cleanup", e)
        }
    }

    /**
     * Tears down the live stack belonging to [rule]: cancels its summary and every child, then
     * drops the registry entry.
     *
     * Called when a STACK rule is deleted or converted to another type, *before* its notification
     * channel is deleted. Deleting a channel cancels its notifications anyway, but doing it in
     * this order leaves the in-memory registry consistent rather than holding entries that point
     * at a channel which no longer exists.
     *
     * [rule] must be the rule as it was *before* the edit — the group key is derived from rule
     * content, so a post-edit rule would hash to a different (empty) group.
     */
    @Synchronized
    fun cancelStackForRule(poster: StackPoster, packageName: String, rule: BlockerRule) {
        val groupKey = groupKeyFor(packageName, rule)
        summaryIds[groupKey]?.let { safeCancel(poster, it) }
        groups[groupKey]?.forEach { safeCancel(poster, it.childId) }
        forgetGroup(groupKey)
    }

    /**
     * Context-based variant for the UI process, which has no [StackPoster] (that lives in the
     * listener service). The UI and the listener share one process, so the registry read here is
     * the same one the service writes.
     *
     * Deleting the rule's channel would cancel its notifications anyway, but only on API 26+ —
     * and it would leave the registry holding entries for a stack that no longer exists.
     */
    @Synchronized
    fun cancelStackForRule(context: Context, packageName: String, rule: BlockerRule) {
        val groupKey = groupKeyFor(packageName, rule)
        val nmc = NotificationManagerCompat.from(context)
        val ids = buildList {
            summaryIds[groupKey]?.let { add(it) }
            groups[groupKey]?.forEach { add(it.childId) }
        }
        for (id in ids) {
            try {
                nmc.cancel(id)
            } catch (e: Exception) {
                Log.w(TAG, "cancel($id) failed while tearing down a deleted rule's stack", e)
            }
        }
        forgetGroup(groupKey)
    }

    // ---- removal & reconnect --------------------------------------------------

    /**
     * Called when one of OUR notifications is dismissed. If a summary was removed →
     * clear the whole group. If a child was removed → drop that entry and rebuild
     * (or clear the group if now empty).
     */
    @Synchronized
    fun onOurNotificationRemoved(poster: StackPoster, removedId: Int, appLabel: String) {
        // Summary removed?
        val summaryGroup = summaryIds.entries.firstOrNull { it.value == removedId }?.key
        if (summaryGroup != null) {
            groups[summaryGroup]?.forEach { safeCancel(poster, it.childId) }
            forgetGroup(summaryGroup)
            return
        }
        // Child removed?
        val childGroup = groups.entries
            .firstOrNull { (_, list) -> list.any { it.childId == removedId } }?.key ?: return
        val list = groups[childGroup] ?: return
        list.removeAll { it.childId == removedId }
        if (list.isEmpty()) {
            summaryIds[childGroup]?.let { safeCancel(poster, it) }
            forgetGroup(childGroup)
            return
        }
        // Rebuild the summary from the remaining entries.
        val snapshot = RegistrySnapshot(
            existingEntries = list.toList().dropLast(1),
            existingSummaryId = summaryIds[childGroup],
            existingCumulative = (cumulativeCounts[childGroup] ?: list.size),
            totalGroups = groups.size,
            isNewGroup = false,
            ttlExpiredGroups = emptyList(),
            maxStacksEviction = null
        )
        val plan = planAbsorb(snapshot, list.last(), System.currentTimeMillis(), ::nextId)
        val channelId = channelIds[childGroup]
        if (channelId == null) {
            // Registry lost the channel (e.g. process restart mid-stack): drop the group rather
            // than guess a channel and post onto the wrong one.
            summaryIds[childGroup]?.let { safeCancel(poster, it) }
            forgetGroup(childGroup)
            return
        }
        try {
            poster.postSummary(plan, childGroup, channelId, appLabel)
        } catch (e: Exception) {
            Log.w(TAG, "rebuild summary after child removal failed", e)
        }
    }

    /**
     * After a process restart the in-memory registry ids are gone, so cancellation
     * must be by listener key. Cancels every still-posted `dnn_stack:` notification
     * we own and clears the registry — guarantees no orphaned/uncontrollable stacks
     * and no child-id reuse against a live notification.
     */
    @Synchronized
    fun reconcileOnConnect(poster: StackPoster) {
        try {
            for (note in poster.activeStackNotifications()) {
                if (note.groupKey.startsWith(GROUP_KEY_PREFIX)) {
                    try {
                        poster.cancelByKey(note.listenerKey)
                    } catch (e: Exception) {
                        Log.w(TAG, "cancelByKey failed during reconcile", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "reconcileOnConnect enumeration failed", e)
        }
        groups.clear()
        summaryIds.clear()
        lastTouched.clear()
        cumulativeCounts.clear()
        channelIds.clear()
    }

    // ---- real Android poster --------------------------------------------------

    /**
     * @param activeProvider supplies our currently-posted notifications (the service
     *   reads `getActiveNotifications()`); [keyCanceller] cancels by listener key
     *   (the service calls `cancelNotification(key)`).
     */
    class AndroidStackPoster(
        private val context: Context,
        private val activeProvider: () -> List<ActiveStackNote>,
        private val keyCanceller: (String) -> Unit
    ) : StackPoster {

        private val nmc = NotificationManagerCompat.from(context)

        override fun areEnabled(): Boolean = nmc.areNotificationsEnabled()

        override fun channelImportance(channelId: String): Int {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                return NotificationManager.IMPORTANCE_DEFAULT
            }
            val nm = context.getSystemService(NotificationManager::class.java)
            return nm?.getNotificationChannel(channelId)?.importance
                ?: NotificationManager.IMPORTANCE_NONE
        }

        override fun activeStackNotifications(): List<ActiveStackNote> = activeProvider()

        override fun postChild(
            plan: AbsorbPlan,
            groupKey: String,
            channelId: String,
            appLabel: String,
            entry: Entry,
            largeIcon: Bitmap?
        ) {
            val title = entry.title?.takeIf { it.isNotBlank() } ?: appLabel
            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_stat_stack)
                .setContentTitle(title)
                .setContentText(entry.text)
                .setGroup(groupKey)
                .setAutoCancel(true)
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
                .setOnlyAlertOnce(plan.isUpdate)
                .setVisibility(plan.effectiveVisibility)
                .setContentIntent(entry.contentIntent ?: sourceLaunchIntent(groupKey, plan.childId))
            largeIcon?.let { builder.setLargeIcon(it) }
            if (plan.redactPublic) {
                builder.setPublicVersion(
                    redactedPublic(groupKey, channelId, appLabel, plan.cumulativeCount)
                )
            }
            notifyChecked(plan.childId, builder.build())
        }

        override fun postSummary(
            plan: AbsorbPlan,
            groupKey: String,
            channelId: String,
            appLabel: String
        ) {
            val countText = context.resources.getQuantityStringOrFallback(plan.cumulativeCount)
            val inbox = NotificationCompat.InboxStyle().setSummaryText(countText)
            plan.summaryLines.forEach { inbox.addLine(it) }
            if (plan.overflowCount > 0) {
                inbox.addLine(context.getString(R.string.stack_overflow_more, plan.overflowCount))
            }
            // Summary and children share the rule's channel: GROUP_ALERT_CHILDREN means the
            // *child* is what actually alerts, and grouping only behaves if both sit on one channel.
            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_stat_stack)
                .setContentTitle(appLabel)
                .setContentText(countText)
                .setStyle(inbox)
                .setGroup(groupKey)
                .setGroupSummary(true)
                .setOnlyAlertOnce(true)
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
                .setVisibility(plan.summaryVisibility)
                .setContentIntent(sourceLaunchIntent(groupKey, plan.summaryId))
            if (plan.summaryRedactPublic) {
                builder.setPublicVersion(
                    redactedPublic(groupKey, channelId, appLabel, plan.cumulativeCount)
                )
            }
            notifyChecked(plan.summaryId, builder.build())
        }

        /** Lock-screen-safe public version: shows the app name + count, never the
         *  source notification's title/body. */
        private fun redactedPublic(
            groupKey: String,
            channelId: String,
            appLabel: String,
            count: Int
        ): Notification =
            NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_stat_stack)
                .setContentTitle(appLabel)
                .setContentText(
                    context.resources.getQuantityStringOrFallback(count)
                )
                .setGroup(groupKey)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .build()

        private fun sourceLaunchIntent(groupKey: String, requestCode: Int): PendingIntent {
            val sourcePackage = groupKey.removePrefix(GROUP_KEY_PREFIX).substringBefore(':')
            val intent = context.packageManager
                .getLaunchIntentForPackage(sourcePackage)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                ?: return appLaunchIntent(requestCode)
            return PendingIntent.getActivity(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        private fun appLaunchIntent(requestCode: Int): PendingIntent {
            val launch = context.packageManager
                .getLaunchIntentForPackage(context.packageName)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                ?: Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return PendingIntent.getActivity(
                context,
                requestCode,
                launch,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        private fun notifyChecked(id: Int, n: Notification) {
            if (!nmc.areNotificationsEnabled()) {
                throw IllegalStateException("notifications disabled at post time")
            }
            nmc.notify(id, n)
        }

        override fun cancel(id: Int) = nmc.cancel(id)

        override fun cancelByKey(key: String) = keyCanceller(key)
    }
}

/** Plurals are overkill here; small helper keeps call sites tidy. */
private fun android.content.res.Resources.getQuantityStringOrFallback(count: Int): String =
    getString(R.string.stack_summary_count, count)
