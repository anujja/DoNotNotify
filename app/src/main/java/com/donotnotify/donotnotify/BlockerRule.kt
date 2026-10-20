package com.donotnotify.donotnotify

import android.os.Parcelable
import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import java.util.UUID
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
    ALLOWLIST,

    @SerializedName(value = "STACK", alternate = ["GROUP", "STACKED"])
    STACK,

    /** Let one matching notification through, then block matches for `ThrottleManager.secondsFor`. */
    THROTTLE
}

/**
 * Whether the user is using the phone. Serialized by name — never rename the constants.
 * In use = screen on and unlocked; anything else (screen off, lock screen, always-on display)
 * is not in use.
 */
@Keep
enum class DeviceUsage {
    IN_USE,
    NOT_IN_USE
}

/**
 * Per-rule conditions and actions (see `RuleConditions` and `ui.components.RuleOptions`).
 * Options added after the time window are one nullable field each, null meaning off, so rules
 * stored or exported before an option existed load with it off.
 */
@Keep
@Parcelize
data class AdvancedRuleConfig(
    val isTimeLimitEnabled: Boolean = false,
    val startTimeHour: Int = 9,
    val startTimeMinute: Int = 0,
    val endTimeHour: Int = 17,
    val endTimeMinute: Int = 0,
    /** Only apply the rule while the phone is (or isn't) in use. */
    val deviceUsage: DeviceUsage? = null,
    /** THROTTLE rules: how long matches are blocked after one passes; null means the default. */
    val throttleSeconds: Int? = null
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
    val advancedConfig: AdvancedRuleConfig? = null,
    /**
     * Stable, device-local identity. Rules created through the Kotlin constructor get a
     * UUID from this default; rules loaded by Gson get **null** (Gson allocates via Unsafe
     * and never runs the constructor), so every load path must pass through
     * [RuleIds.normalizeIds]. Never exported — see the export ExclusionStrategy.
     */
    val id: String = UUID.randomUUID().toString(),
    /** Optional user-supplied label. Used as the notification channel name for STACK rules. */
    val name: String? = null
) : Parcelable