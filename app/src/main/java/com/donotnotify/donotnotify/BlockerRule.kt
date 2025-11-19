package com.donotnotify.donotnotify

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

enum class MatchType {
    REGEX,
    CONTAINS
}

enum class RuleType {
    BLACKLIST,
    WHITELIST
}

@Parcelize
data class BlockerRule(
    val appName: String? = null,
    val packageName: String? = null, // Added packageName
    val titleFilter: String? = null,
    val titleMatchType: MatchType = MatchType.REGEX,
    val textFilter: String? = null,
    val textMatchType: MatchType = MatchType.REGEX,
    val blockedCount: Int = 0,
    val ruleType: RuleType = RuleType.BLACKLIST
) : Parcelable
