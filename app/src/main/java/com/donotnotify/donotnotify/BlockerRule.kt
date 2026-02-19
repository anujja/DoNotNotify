package com.donotnotify.donotnotify

import android.os.Parcelable
import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize
@Keep
enum class MatchType {
    CONTAINS,
    REGEX
}

@Keep
enum class RuleType {
    @SerializedName(value = "DENYLIST", alternate = ["BLACKLIST"])
    DENYLIST,
    @SerializedName(value = "ALLOWLIST", alternate = ["WHITELIST"])
    ALLOWLIST
}

@Keep
@Parcelize
data class AdvancedRuleConfig(
    val isTimeLimitEnabled: Boolean = false,
    val startTimeHour: Int = 9,
    val startTimeMinute: Int = 0,
    val endTimeHour: Int = 17,
    val endTimeMinute: Int = 0
) : Parcelable

@Keep
@Parcelize
data class BlockerRule(
    val appName: String? = null,
    val packageName: String? = null,
    val titleFilter: String? = null,
    val titleMatchType: MatchType = MatchType.CONTAINS,
    val textFilter: String? = null,
    val textMatchType: MatchType = MatchType.CONTAINS,
    val hitCount: Int = 0,
    val ruleType: RuleType = RuleType.DENYLIST,
    val isEnabled: Boolean = true,
    val advancedConfig: AdvancedRuleConfig? = null
) : Parcelable