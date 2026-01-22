package com.donotnotify.donotnotify

import java.util.Calendar
import java.util.regex.PatternSyntaxException

object RuleMatcher {
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
                MatchType.REGEX -> rule.titleFilter.isNullOrBlank() || (title?.matches(rule.titleFilter.toRegex()) ?: false)
                MatchType.CONTAINS -> rule.titleFilter.isNullOrBlank() || (title?.contains(rule.titleFilter, ignoreCase = true) ?: false)
            }

            val textMatch = when (rule.textMatchType) {
                MatchType.REGEX -> rule.textFilter.isNullOrBlank() || (text?.matches(rule.textFilter.toRegex()) ?: false)
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
     * 1. If there are whitelist rules for the package, the notification MUST match at least one of them.
     * 2. If the notification matches any blacklist rule, it IS blocked (even if it matched a whitelist rule).
     */
    fun shouldBlock(
        packageName: String,
        title: String?,
        text: String?,
        rules: List<BlockerRule>
    ): Boolean {
        val rulesForPackage = rules.filter { it.packageName == packageName && it.isEnabled }
        val whitelistRules = rulesForPackage.filter { it.ruleType == RuleType.WHITELIST }
        val blacklistRules = rulesForPackage.filter { it.ruleType == RuleType.BLACKLIST }

        val hasWhitelistRules = whitelistRules.isNotEmpty()

        var matchesWhitelist = false
        for (rule in whitelistRules) {
            if (matches(rule, packageName, title, text)) {
                matchesWhitelist = true
                break
            }
        }

        var matchesBlacklist = false
        for (rule in blacklistRules) {
            if (matches(rule, packageName, title, text)) {
                matchesBlacklist = true
                break
            }
        }

        // Block if:
        // (It has whitelist rules AND it didn't match any) OR (It matched a blacklist rule)
        return (hasWhitelistRules && !matchesWhitelist) || matchesBlacklist
    }
}
