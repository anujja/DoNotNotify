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
            val titleMatch = when (rule.titleMatchType) {
                MatchType.REGEX -> rule.titleFilter.isNullOrBlank() || (title?.matches(getCachedRegex(rule.titleFilter)) ?: false)
                MatchType.CONTAINS -> rule.titleFilter.isNullOrBlank() || (title?.contains(rule.titleFilter, ignoreCase = true) ?: false)
            }

            val textMatch = when (rule.textMatchType) {
                MatchType.REGEX -> rule.textFilter.isNullOrBlank() || (text?.matches(getCachedRegex(rule.textFilter)) ?: false)
                MatchType.CONTAINS -> rule.textFilter.isNullOrBlank() || (text?.contains(rule.textFilter, ignoreCase = true) ?: false)
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
            }
        }

        // Block if:
        // (It has allowlist rules AND it didn't match any) OR (It matched a denylist rule)
        return (hasAllowlistRules && !matchesAllowlist) || matchesDenylist
    }
}
