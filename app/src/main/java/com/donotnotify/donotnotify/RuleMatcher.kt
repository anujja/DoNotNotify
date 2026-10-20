package com.donotnotify.donotnotify

object RuleMatcher {
    private const val MAX_CACHE_SIZE = 512

    private val regexCache = object : LinkedHashMap<String, Regex>(MAX_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Regex>?): Boolean =
            size > MAX_CACHE_SIZE
    }

    @Synchronized
    private fun getCachedRegex(pattern: String): Regex =
        regexCache.getOrPut(pattern) { pattern.toRegex() }

    // Google apps wrap notification strings in invisible bidi/format chars
    // (U+2066–U+2069 isolates, U+200E/F marks, zero-width chars). They survive
    // toString() but never render, so rules must match the visible text only.
    internal fun stripInvisibleFormatting(s: String?): String? =
        s?.filterNot { it.category == CharCategory.FORMAT }

    /**
     * Where one filter matches one field: the matched range within the field's visible text
     * ([stripInvisibleFormatting]), an empty range for a blank filter (it matches anything),
     * or null for no match. [matches] is built on this, so the rule wizard's preview, which
     * highlights these ranges, always agrees with what a rule actually does.
     */
    fun findMatch(filter: String?, matchType: MatchType, value: String?): IntRange? {
        if (filter.isNullOrBlank()) return IntRange.EMPTY
        val clean = stripInvisibleFormatting(value) ?: return null
        return try {
            when (matchType) {
                MatchType.REGEX -> getCachedRegex(filter).find(clean)?.range
                // Filters are stripped too: CONTAINS filters are often pasted from
                // notification text and can carry the same invisible chars.
                MatchType.CONTAINS -> {
                    val needle = stripInvisibleFormatting(filter)!!
                    clean.indexOf(needle, ignoreCase = true).takeIf { it >= 0 }
                        ?.let { it until it + needle.length }
                }
            }
        } catch (e: Exception) {
            // In case of invalid regex or other errors, we assume no match
            null
        }
    }

    fun matches(
        rule: BlockerRule,
        packageName: String?,
        title: String?,
        text: String?,
        matchContext: MatchContext = MatchContext.now()
    ): Boolean {
        // Conditions (time window, …) gate the rule before any filter is evaluated.
        if (!RuleConditions.isActive(rule.advancedConfig, matchContext)) return false

        // If the rule has a package name, it must match.
        // Note: The caller usually filters by package name, but we check here to be safe.
        if (!rule.packageName.isNullOrEmpty() && rule.packageName != packageName) return false

        return findMatch(rule.titleFilter, rule.titleMatchType, title) != null &&
                findMatch(rule.textFilter, rule.textMatchType, text) != null
    }

    /**
     * Determines if a notification should be blocked based on the provided rules.
     * Logic:
     * 1. If there are active allowlist rules for the package, the notification MUST match at least one of them.
     * 2. If the notification matches any denylist rule, it IS blocked (even if it matched an allowlist rule).
     * A rule whose conditions don't hold right now is skipped entirely.
     */
    fun shouldBlock(
        packageName: String,
        title: String?,
        text: String?,
        rules: List<BlockerRule>,
        matchContext: MatchContext = MatchContext.now()
    ): Boolean {
        var hasAllowlistRules = false
        var matchesAllowlist = false
        var matchesDenylist = false

        for (rule in rules) {
            if (rule.packageName != packageName || !rule.isEnabled) continue
            if (!RuleConditions.isActive(rule.advancedConfig, matchContext)) continue
            when (rule.ruleType) {
                RuleType.ALLOWLIST -> {
                    hasAllowlistRules = true
                    if (!matchesAllowlist && matches(rule, packageName, title, text, matchContext)) {
                        matchesAllowlist = true
                    }
                }
                RuleType.DENYLIST -> {
                    if (!matchesDenylist && matches(rule, packageName, title, text, matchContext)) {
                        matchesDenylist = true
                    }
                }
                RuleType.STACK, RuleType.THROTTLE -> {
                    // Not blocking types: they never block outright and never gate like allowlist.
                }
            }
        }

        // Block if:
        // (It has allowlist rules AND it didn't match any) OR (It matched a denylist rule)
        return (hasAllowlistRules && !matchesAllowlist) || matchesDenylist
    }

    /**
     * Pure, Android-free block/stack precedence resolution. Single source of truth for
     * [NotificationBlockerService]'s decision, extracted so the full precedence matrix
     * is unit-testable on plain JVM (no Robolectric needed).
     *
     * Precedence: `isBlocked` is unchanged
     * (`(hasAllowlistRules && !matchesAllowlist) || matchesDenylist`).
     * `shouldStack = !isBlocked && matchesStack && !wasOngoing`.
     * STACK and THROTTLE rules never set `hasAllowlistRules`. Every matching THROTTLE rule (not
     * just the first) is returned in `throttleRules` for [ThrottleManager], unless the
     * notification is already blocked or is ongoing. A rule whose conditions (time window, phone
     * usage) don't hold is skipped entirely, so an inactive ALLOWLIST rule doesn't gate the
     * app's other notifications. First enabled match wins for both
     * DENYLIST and STACK (mirrors the existing short-circuit). Only matched rules'
     * indices are returned (for hitCount increments). [matchContext] is snapshotted once
     * so every rule is judged against the same time/device state.
     */
    fun planNotificationDecision(
        rules: List<BlockerRule>,
        packageName: String,
        title: String?,
        text: String?,
        wasOngoing: Boolean,
        matchContext: MatchContext = MatchContext.now()
    ): NotificationDecision {
        var hasAllowlistRules = false
        var matchesAllowlist = false
        var matchesDenylist = false
        var matchedDenylistRule: BlockerRule? = null
        var matchesStack = false
        var matchedStackRule: BlockerRule? = null
        val matchedThrottleRules = mutableListOf<BlockerRule>()
        val matchedRuleIndices = mutableListOf<Int>()

        for ((index, rule) in rules.withIndex()) {
            if (rule.packageName != packageName || !rule.isEnabled) continue
            if (!RuleConditions.isActive(rule.advancedConfig, matchContext)) continue
            when (rule.ruleType) {
                RuleType.ALLOWLIST -> {
                    hasAllowlistRules = true
                    if (!matchesAllowlist && matches(rule, packageName, title, text, matchContext)) {
                        matchesAllowlist = true
                        matchedRuleIndices.add(index)
                    }
                }
                RuleType.DENYLIST -> {
                    if (!matchesDenylist && matches(rule, packageName, title, text, matchContext)) {
                        matchesDenylist = true
                        matchedDenylistRule = rule
                        matchedRuleIndices.add(index)
                    }
                }
                RuleType.STACK -> {
                    if (!matchesStack && matches(rule, packageName, title, text, matchContext)) {
                        matchesStack = true
                        matchedStackRule = rule
                        matchedRuleIndices.add(index)
                    }
                }
                RuleType.THROTTLE -> {
                    if (matches(rule, packageName, title, text, matchContext)) {
                        matchedThrottleRules.add(rule)
                        matchedRuleIndices.add(index)
                    }
                }
            }
        }

        val isBlocked = (hasAllowlistRules && !matchesAllowlist) || matchesDenylist
        val shouldStack = !isBlocked && matchesStack && !wasOngoing
        return NotificationDecision(
            isBlocked = isBlocked,
            matchedDenylistRule = if (matchesDenylist) matchedDenylistRule else null,
            shouldStack = shouldStack,
            // Only meaningful when the notification will actually be stacked.
            matchedStackRule = if (shouldStack) matchedStackRule else null,
            // A blocked notification is gone anyway, and an ongoing one can't reliably be cancelled.
            throttleRules = if (!isBlocked && !wasOngoing) matchedThrottleRules else emptyList(),
            matchedRuleIndices = matchedRuleIndices
        )
    }
}

/**
 * Outcome of [RuleMatcher.planNotificationDecision]. Plain data, no Android types.
 *
 * @param matchedDenylistRule the first matched DENYLIST rule, or null.
 * @param matchedStackRule the first matched STACK rule (only meaningful when
 *   [shouldStack] is true), or null.
 * @param throttleRules every matched THROTTLE rule; empty when the notification is blocked
 *   or ongoing. A suppression by [ThrottleManager] takes precedence over stacking.
 * @param matchedRuleIndices indices (into the input list) of every rule that matched
 *   and should have its hitCount incremented.
 */
data class NotificationDecision(
    val isBlocked: Boolean,
    val matchedDenylistRule: BlockerRule?,
    val shouldStack: Boolean,
    val matchedStackRule: BlockerRule?,
    val throttleRules: List<BlockerRule> = emptyList(),
    val matchedRuleIndices: List<Int>
)
