package com.donotnotify.donotnotify

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi

/**
 * The Android half of [StackChannels]. All API-26 gating lives here, so the pure planner never
 * has to think about it.
 *
 * Below API 26 there are no channels at all: [StackChannels.sync] is a no-op and the poster
 * reports IMPORTANCE_DEFAULT, so stacking behaves exactly as it did before this feature.
 */
object StackChannelsAndroid {

    private const val TAG = "StackChannels"

    @RequiresApi(Build.VERSION_CODES.O)
    private class Host(private val nm: NotificationManager) : StackChannels.ChannelHost {

        override fun existingChannels(): Map<String, String> =
            nm.notificationChannels.associate { it.id to (it.name?.toString() ?: "") }

        override fun rename(channelId: String, name: String) {
            // Re-issuing createNotificationChannel for an existing id updates the mutable fields
            // (name/description) and leaves the user-owned ones — importance, sound, vibration —
            // exactly as they set them. Mutate the live channel so nothing else is disturbed.
            val existing = nm.getNotificationChannel(channelId) ?: return
            existing.name = name
            nm.createNotificationChannel(existing)
        }

        override fun snapshot(channelId: String): StackChannels.ChannelSnapshot? {
            val c = nm.getNotificationChannel(channelId) ?: return null
            return StackChannels.ChannelSnapshot(
                importance = c.importance,
                soundUri = c.sound?.toString(),
                audioUsage = c.audioAttributes?.usage,
                audioContentType = c.audioAttributes?.contentType,
                audioFlags = c.audioAttributes?.flags,
                vibrationEnabled = c.shouldVibrate(),
                vibrationPattern = c.vibrationPattern,
                lightsEnabled = c.shouldShowLights(),
                lightColor = c.lightColor,
                showBadge = c.canShowBadge(),
                lockscreenVisibility = c.lockscreenVisibility,
                bypassDnd = c.canBypassDnd()
            )
        }

        override fun createGroup(groupId: String, name: String) {
            nm.createNotificationChannelGroup(NotificationChannelGroup(groupId, name))
        }

        override fun create(spec: StackChannels.ChannelSpec) {
            val s = spec.settings
            val channel = NotificationChannel(spec.id, spec.name, s.importance).apply {
                group = spec.groupId

                // Sound must be set with its AudioAttributes together, or the platform falls back
                // to defaults and the seeded sound is silently lost.
                val sound = s.soundUri?.let { runCatching { Uri.parse(it) }.getOrNull() }
                if (sound != null) {
                    val attrs = AudioAttributes.Builder()
                        .setUsage(s.audioUsage ?: AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(s.audioContentType ?: AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .apply { s.audioFlags?.let { setFlags(it) } }
                        .build()
                    setSound(sound, attrs)
                }

                enableVibration(s.vibrationEnabled)
                s.vibrationPattern?.let { if (it.isNotEmpty()) vibrationPattern = it }
                enableLights(s.lightsEnabled)
                if (s.lightsEnabled) lightColor = s.lightColor
                setShowBadge(s.showBadge)
                s.lockscreenVisibility?.let { lockscreenVisibility = it }
                // Only honoured if the user has granted DND access; harmless otherwise.
                if (s.bypassDnd) setBypassDnd(true)
            }
            nm.createNotificationChannel(channel)
        }

        override fun delete(channelId: String) {
            // Guard the invariant at the boundary too: nothing outside the stack channel space
            // may ever be deleted, whatever a caller believes.
            if (!StackChannels.isStackChannelId(channelId) && channelId != StackChannels.LEGACY_CHANNEL_ID) {
                Log.w(TAG, "Refusing to delete non-stack channel: $channelId")
                return
            }
            nm.deleteNotificationChannel(channelId)
        }
    }

    private fun hostOf(context: Context): StackChannels.ChannelHost? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        val nm = context.getSystemService(NotificationManager::class.java) ?: return null
        return Host(nm)
    }

    private fun namingOf(context: Context) = StackChannels.ChannelNaming(
        appRuleFormat = context.getString(R.string.stack_channel_name_app_rule),
        unnamedFormat = context.getString(R.string.stack_channel_name_unnamed)
    )

    /**
     * Reconcile every per-rule channel against the committed rules. Safe to call often; it reads
     * the rules itself rather than trusting a caller's (possibly stale) list.
     */
    fun sync(context: Context) {
        val host = hostOf(context) ?: return
        val storage = RuleStorage(context.applicationContext)
        try {
            StackChannels.sync(
                host = host,
                rulesProvider = { storage.getRules() },
                groupName = context.getString(R.string.stack_channel_group_name),
                naming = namingOf(context)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Channel sync failed", e)
        }
    }

    /** Lazily create one rule's channel, so a missed [sync] can never silently swallow a stack. */
    fun ensure(context: Context, rule: BlockerRule) {
        val host = hostOf(context) ?: return
        try {
            StackChannels.ensure(
                host = host,
                rule = rule,
                groupName = context.getString(R.string.stack_channel_group_name),
                naming = namingOf(context)
            )
        } catch (e: Exception) {
            Log.e(TAG, "ensureChannel failed for ${rule.packageName}", e)
        }
    }

    /** The channel id a STACK rule posts on — used by the poster and the settings deep-link. */
    fun channelIdFor(rule: BlockerRule): String = StackChannels.channelIdFor(rule)
}
