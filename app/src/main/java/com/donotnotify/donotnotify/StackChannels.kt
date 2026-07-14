package com.donotnotify.donotnotify

/**
 * One notification channel per STACK rule, so the user can set sound/vibration per rule in
 * Android's settings. Stacking otherwise flattens every re-posted notification onto a single
 * channel, discarding whatever alerting the source app's own channel had.
 *
 * Android only lets the *user* change a channel's importance/sound/vibration once it exists —
 * the app can only choose the values at creation. So this file's job is limited to: minting the
 * right channels, naming them intelligibly, seeding them sensibly on migration, and deleting
 * them when their rule goes away. Everything after that belongs to the user.
 *
 * The decisions are a pure function ([planChannelSync]) over an Android-free [ChannelHost] seam,
 * matching the `planAbsorb`/`StackPoster` split already used by [StackedNotificationManager], so
 * the invariants below are unit-testable on the JVM.
 */
object StackChannels {

    /** Every per-rule channel id starts with this. Nothing else may be deleted by a sync. */
    const val STACK_CHANNEL_PREFIX = "stack_rule_"

    /** The single shared channel used before per-rule channels existed. Deleted on migration. */
    const val LEGACY_CHANNEL_ID = "stacked_notifications"

    /** Groups the per-rule channels together in system settings. */
    const val CHANNEL_GROUP_ID = "stack_rules"

    /** Mirrors NotificationManager.IMPORTANCE_* without linking Android into the pure layer. */
    const val IMPORTANCE_NONE = 0
    const val IMPORTANCE_DEFAULT = 3

    fun channelIdFor(rule: BlockerRule): String = "$STACK_CHANNEL_PREFIX${rule.id}"

    fun isStackChannelId(id: String): Boolean = id.startsWith(STACK_CHANNEL_PREFIX)

    /**
     * The user-owned settings of an existing channel. Copied off the legacy channel before it is
     * deleted, and used to seed the per-rule channels, so a user who had deliberately silenced
     * stacking is not suddenly re-alerted by a fresh batch of IMPORTANCE_DEFAULT channels.
     *
     * Plain types only — no Android imports — so the planner stays JVM-testable.
     */
    data class ChannelSnapshot(
        val importance: Int,
        val soundUri: String?,
        val audioUsage: Int?,
        val audioContentType: Int?,
        val audioFlags: Int?,
        val vibrationEnabled: Boolean,
        val vibrationPattern: LongArray?,
        val lightsEnabled: Boolean,
        val lightColor: Int,
        val showBadge: Boolean,
        val lockscreenVisibility: Int?,
        val bypassDnd: Boolean
    ) {
        // LongArray breaks data-class equality; spell it out so tests can compare snapshots.
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ChannelSnapshot) return false
            return importance == other.importance &&
                soundUri == other.soundUri &&
                audioUsage == other.audioUsage &&
                audioContentType == other.audioContentType &&
                audioFlags == other.audioFlags &&
                vibrationEnabled == other.vibrationEnabled &&
                (vibrationPattern?.toList() == other.vibrationPattern?.toList()) &&
                lightsEnabled == other.lightsEnabled &&
                lightColor == other.lightColor &&
                showBadge == other.showBadge &&
                lockscreenVisibility == other.lockscreenVisibility &&
                bypassDnd == other.bypassDnd
        }

        override fun hashCode(): Int {
            var result = importance
            result = 31 * result + (soundUri?.hashCode() ?: 0)
            result = 31 * result + (vibrationPattern?.toList()?.hashCode() ?: 0)
            result = 31 * result + vibrationEnabled.hashCode()
            return result
        }

        companion object {
            /** What a channel looks like on a fresh install, with no legacy channel to inherit from. */
            fun default(): ChannelSnapshot = ChannelSnapshot(
                importance = IMPORTANCE_DEFAULT,
                soundUri = null,
                audioUsage = null,
                audioContentType = null,
                audioFlags = null,
                vibrationEnabled = true,
                vibrationPattern = null,
                lightsEnabled = false,
                lightColor = 0,
                showBadge = true,
                lockscreenVisibility = null,
                bypassDnd = false
            )
        }
    }

    /** Everything needed to create a channel. Importance/sound/etc. are immutable afterwards. */
    data class ChannelSpec(
        val id: String,
        val name: String,
        val groupId: String,
        val settings: ChannelSnapshot
    )

    data class ChannelSyncPlan(
        val create: List<ChannelSpec>,
        val delete: List<String>
    )

    /**
     * Localizable name fragments, passed in so the planner stays free of Android resources.
     * - [appRuleFormat]: two args — app label, short id. e.g. `"%1$s — rule %2$s"`
     * - [unnamedFormat]: one arg — short id. e.g. `"Stack rule %1$s"`
     */
    data class ChannelNaming(
        val appRuleFormat: String,
        val unnamedFormat: String
    )

    /**
     * Decides the whole channel lifecycle for a set of rules.
     *
     * Invariants:
     * - **Deletion is strictly scoped.** Only orphaned `stack_rule_*` channels and the single
     *   legacy channel may be deleted. Unrelated channels (notably the app's own `health`
     *   channel, and anything a future version adds) are never touched.
     * - A **disabled** rule keeps its channel: deleting it would tombstone the channel, and
     *   Android resurrects a deleted channel's old settings if the id ever comes back — so a
     *   disable/re-enable cycle would silently restore stale settings. Only *deletion* of the
     *   rule (or its conversion away from STACK) removes the channel.
     * - New channels are seeded from [legacySeed] when present, so migrating users keep the
     *   alerting behaviour they had chosen for the shared channel.
     */
    fun planChannelSync(
        existingIds: Set<String>,
        rules: List<BlockerRule>,
        legacySeed: ChannelSnapshot?,
        naming: ChannelNaming
    ): ChannelSyncPlan {
        // Note: isEnabled is deliberately NOT filtered on — see invariant above.
        val stackRules = rules.filter { it.ruleType == RuleType.STACK }
        val wanted = stackRules.associateBy { channelIdFor(it) }

        val seed = legacySeed ?: ChannelSnapshot.default()
        val create = wanted
            .filterKeys { it !in existingIds }
            .map { (id, rule) ->
                ChannelSpec(
                    id = id,
                    name = channelNameFor(rule, naming),
                    groupId = CHANNEL_GROUP_ID,
                    settings = seed
                )
            }
            .sortedBy { it.id }

        val orphans = existingIds.filter { isStackChannelId(it) && it !in wanted }
        val legacy = if (LEGACY_CHANNEL_ID in existingIds) listOf(LEGACY_CHANNEL_ID) else emptyList()

        return ChannelSyncPlan(create = create, delete = (orphans + legacy).sorted())
    }

    /**
     * The channel's user-visible name.
     *
     * Filter text is **never** used here. Channel names are readable by any notification
     * listener/assistant via `getNotificationChannels(pkg, user)`, and a filter is user-authored
     * content those readers cannot otherwise see — in a notification-filtering app it can be
     * genuinely private. The rule editor offers a filter-derived suggestion, but only an explicit
     * user action writes it into [BlockerRule.name]; nothing is published implicitly.
     */
    fun channelNameFor(rule: BlockerRule, naming: ChannelNaming): String {
        val shortId = shortId(rule.id)

        sanitize(rule.name)?.let { return truncate(it) }

        val label = sanitize(rule.appName) ?: sanitize(rule.packageName)
        return if (label != null) {
            // Truncate the *label*, never the disambiguating suffix — two rules on one app must
            // stay tellable apart in system settings.
            val suffix = String.format(naming.appRuleFormat, "", shortId)
            val room = (MAX_NAME_LENGTH - suffix.length).coerceAtLeast(MIN_LABEL_LENGTH)
            String.format(naming.appRuleFormat, truncate(label, room), shortId)
        } else {
            truncate(String.format(naming.unnamedFormat, shortId))
        }
    }

    private const val MAX_NAME_LENGTH = 40
    private const val MIN_LABEL_LENGTH = 8
    private const val SHORT_ID_LENGTH = 8

    /** Four hex chars is not a uniqueness guarantee; eight makes a same-app collision negligible. */
    private fun shortId(id: String?): String =
        (id ?: "").filter { it.isLetterOrDigit() }.take(SHORT_ID_LENGTH).ifEmpty { "00000000" }

    /**
     * Strips Unicode control (Cc) and format (Cf) characters and collapses whitespace.
     *
     * `appName` is untrusted: it comes from the source notification's extras, so it can carry
     * newlines, or bidi overrides/isolates (U+202A–U+202E, U+2066–U+2069) that visually reorder
     * text — which would let a notification spoof a channel name into appearing to belong to a
     * different app in the user's system settings.
     *
     * @return the cleaned string, or null if nothing usable remains.
     */
    fun sanitize(raw: String?): String? {
        if (raw == null) return null
        val cleaned = raw
            .filterNot { ch ->
                val type = Character.getType(ch).toByte()
                type == Character.CONTROL || type == Character.FORMAT
            }
            .replace(Regex("\\s+"), " ")
            .trim()
        return cleaned.ifEmpty { null }
    }

    private fun truncate(s: String, max: Int = MAX_NAME_LENGTH): String =
        if (s.length <= max) s else s.take((max - 1).coerceAtLeast(1)).trimEnd() + "…"

    /** Android-facing side-effect seam; faked in tests. */
    interface ChannelHost {
        fun existingChannelIds(): Set<String>
        fun snapshot(channelId: String): ChannelSnapshot?
        fun createGroup(groupId: String, name: String)
        fun create(spec: ChannelSpec)
        fun delete(channelId: String)
    }

    /**
     * Applies [planChannelSync] against [host].
     *
     * Takes **no rule list**: it reads the committed rules itself. A caller-supplied list would be
     * racy — mutation A hands us its list, mutation B commits a new rule, then A's sync runs
     * against the stale list and deletes B's brand-new channel. Reading current state under a
     * single monitor makes sync convergent instead: a late invocation simply re-plans from the
     * latest committed rules.
     */
    @Synchronized
    fun sync(
        host: ChannelHost,
        rulesProvider: () -> List<BlockerRule>,
        groupName: String,
        naming: ChannelNaming
    ) {
        val rules = rulesProvider()
        val existing = host.existingChannelIds()

        // Snapshot the legacy channel *before* planning, since the plan deletes it.
        val legacySeed = if (LEGACY_CHANNEL_ID in existing) host.snapshot(LEGACY_CHANNEL_ID) else null

        val plan = planChannelSync(existing, rules, legacySeed, naming)
        if (plan.create.isEmpty() && plan.delete.isEmpty()) return

        if (plan.create.isNotEmpty()) host.createGroup(CHANNEL_GROUP_ID, groupName)
        plan.create.forEach(host::create)
        plan.delete.forEach(host::delete)
    }

    /** Idempotent single-rule create — defence in depth, so a missed sync can't swallow a stack. */
    @Synchronized
    fun ensure(
        host: ChannelHost,
        rule: BlockerRule,
        groupName: String,
        naming: ChannelNaming
    ) {
        if (rule.ruleType != RuleType.STACK) return
        val id = channelIdFor(rule)
        val existing = host.existingChannelIds()
        if (id in existing) return

        val legacySeed = if (LEGACY_CHANNEL_ID in existing) host.snapshot(LEGACY_CHANNEL_ID) else null
        host.createGroup(CHANNEL_GROUP_ID, groupName)
        host.create(
            ChannelSpec(
                id = id,
                name = channelNameFor(rule, naming),
                groupId = CHANNEL_GROUP_ID,
                settings = legacySeed ?: ChannelSnapshot.default()
            )
        )
    }
}
