package com.donotnotify.donotnotify

import java.util.Calendar
import java.util.regex.PatternSyntaxException

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
    private fun stripInvisibleFormatting(s: String?): String? =
        s?.filterNot { it.category == CharCategory.FORMAT }

    fun matches(
        rule: BlockerRule,
        packageName: String?,
        title: String?,
        text: String?
    ): Boolean {
        // Check if the rule is active based on time settings
        if (rule.advancedConfig?.isTimeLimitEnabled == true) {
            val config = rule.advancedConfig
            val now = Calendar.getInstance()
            val currentHour = now.get(Calendar.HOUR_OF_DAY)
            val currentMinute = now.get(Calendar.MINUTE)
            val currentTotalMinutes = currentHour * 60 + currentMinute

            val startTotalMinutes = config.startTimeHour * 60 + config.startTimeMinute
            val endTotalMinutes = config.endTimeHour * 60 + config.endTimeMinute

            val isActive = if (startTotalMinutes <= endTotalMinutes) {
                currentTotalMinutes in startTotalMinutes..endTotalMinutes
            } else {
                // Spans midnight
                currentTotalMinutes >= startTotalMinutes || currentTotalMinutes <= endTotalMinutes
            }

            if (!isActive) return false
        }

        // If the rule has a package name, it must match.
        // Note: The caller usually filters by package name, but we check here to be safe.
        if (!rule.packageName.isNullOrEmpty() && rule.packageName != packageName) return false

        try {
            val cleanTitle = stripInvisibleFormatting(title)
            val cleanText = stripInvisibleFormatting(text)

            val titleMatch = when (rule.titleMatchType) {
                MatchType.REGEX -> rule.titleFilter.isNullOrBlank() || (cleanTitle != null && getCachedRegex(rule.titleFilter).containsMatchIn(cleanTitle))
                // Filters are stripped too: CONTAINS filters are often pasted from
                // notification text and can carry the same invisible chars.
                MatchType.CONTAINS -> rule.titleFilter.isNullOrBlank() || (cleanTitle?.contains(stripInvisibleFormatting(rule.titleFilter)!!, ignoreCase = true) ?: false)
            }

            val textMatch = when (rule.textMatchType) {
                MatchType.REGEX -> rule.textFilter.isNullOrBlank() || (cleanText != null && getCachedRegex(rule.textFilter).containsMatchIn(cleanText))
                MatchType.CONTAINS -> rule.textFilter.isNullOrBlank() || (cleanText?.contains(stripInvisibleFormatting(rule.textFilter)!!, ignoreCase = true) ?: false)
            }

            return titleMatch && textMatch
        } catch (e: Exception) {
            // In case of invalid regex or other errors, we assume no match
            return false
        }
    }

    /**
     * Determines if a notification should be blocked based on the provided rules.
     * Logic:
     * 1. If there are allowlist rules for the package, the notification MUST match at least one of them.
     * 2. If the notification matches any denylist rule, it IS blocked (even if it matched an allowlist rule).
     */
    fun shouldBlock(
        packageName: String,
        title: String?,
        text: String?,
        rules: List<BlockerRule>
    ): Boolean {
        var hasAllowlistRules = false
        var matchesAllowlist = false
        var matchesDenylist = false

        for (rule in rules) {
            if (rule.packageName != packageName || !rule.isEnabled) continue
            when (rule.ruleType) {
                RuleType.ALLOWLIST -> {
                    hasAllowlistRules = true
                    if (!matchesAllowlist && matches(rule, packageName, title, text)) {
                        matchesAllowlist = true
                    }
                }
                RuleType.DENYLIST -> {
                    if (!matchesDenylist && matches(rule, packageName, title, text)) {
                        matchesDenylist = true
                    }
                }
                RuleType.STACK -> {
                    // Not a blocking type — STACK never blocks and never gates like allowlist.
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
     * STACK rules never set `hasAllowlistRules`. First enabled match wins for both
     * DENYLIST and STACK (mirrors the existing short-circuit). Only matched rules'
     * indices are returned (for hitCount increments).
     */
    fun planNotificationDecision(
        rules: List<BlockerRule>,
        packageName: String,
        title: String?,
        text: String?,
        wasOngoing: Boolean
    ): NotificationDecision {
        var hasAllowlistRules = false
        var matchesAllowlist = false
        var matchesDenylist = false
        var matchedDenylistRule: BlockerRule? = null
        var matchesStack = false
        var matchedStackRule: BlockerRule? = null
        val matchedRuleIndices = mutableListOf<Int>()

        for ((index, rule) in rules.withIndex()) {
            if (rule.packageName != packageName || !rule.isEnabled) continue
            when (rule.ruleType) {
                RuleType.ALLOWLIST -> {
                    hasAllowlistRules = true
                    if (!matchesAllowlist && matches(rule, packageName, title, text)) {
                        matchesAllowlist = true
                        matchedRuleIndices.add(index)
                    }
                }
                RuleType.DENYLIST -> {
                    if (!matchesDenylist && matches(rule, packageName, title, text)) {
                        matchesDenylist = true
                        matchedDenylistRule = rule
                        matchedRuleIndices.add(index)
                    }
                }
                RuleType.STACK -> {
                    if (!matchesStack && matches(rule, packageName, title, text)) {
                        matchesStack = true
                        matchedStackRule = rule
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
 * @param matchedRuleIndices indices (into the input list) of every rule that matched
 *   and should have its hitCount incremented.
 */
data class NotificationDecision(
    val isBlocked: Boolean,
    val matchedDenylistRule: BlockerRule?,
    val shouldStack: Boolean,
    val matchedStackRule: BlockerRule?,
    val matchedRuleIndices: List<Int>
)
