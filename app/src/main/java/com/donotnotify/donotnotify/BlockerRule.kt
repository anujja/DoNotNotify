package com.donotnotify.donotnotify

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

enum class MatchType {
    REGEX,
    CONTAINS
}

@Parcelize
data class BlockerRule(
    val appName: String? = null,
    val packageName: String? = null, // Added packageName
    val titleFilter: String? = null,
    val titleMatchType: MatchType = MatchType.REGEX,
    val textFilter: String? = null,
    val textMatchType: MatchType = MatchType.REGEX,
    val blockedCount: Int = 0
) : Parcelable
