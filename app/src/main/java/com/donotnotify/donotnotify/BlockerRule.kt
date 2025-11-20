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
    val packageName: String? = null,
    val titleFilter: String? = null,
    val titleMatchType: MatchType = MatchType.CONTAINS,
    val textFilter: String? = null,
    val textMatchType: MatchType = MatchType.CONTAINS,
    val hitCount: Int = 0,
    val ruleType: RuleType = RuleType.BLACKLIST,
    val isEnabled: Boolean = true
) : Parcelable