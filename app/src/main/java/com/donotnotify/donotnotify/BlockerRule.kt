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
    val throttleSeconds: Int? = null,
    /**
     * Only apply the rule on these days; null or empty means every day. With a time window that
     * spans midnight, a day covers the window that starts on it (`RuleConditions.isOnScheduledDay`).
     */
    val daysOfWeek: Set<Weekday>? = null
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

/**
 * A rule with no package applies to every app's notifications ("All apps" in the rule wizard).
 * Such a rule always has at least one filter: the wizard, the editor and the importer refuse one
 * without, since it would act on every notification on the phone. It is never an ALLOWLIST
 * ([RuleType.isAllowedForAllApps]).
 */
val BlockerRule.isAllApps: Boolean
    get() = packageName.isNullOrBlank()

/**
 * Whether a rule for all apps may be of this type. An ALLOWLIST for all apps would block every
 * notification on the phone that doesn't match it, so the wizard and the editor don't offer it,
 * the importer drops one, and [RuleMatcher] ignores one that reaches storage some other way.
 */
val RuleType.isAllowedForAllApps: Boolean
    get() = this != RuleType.ALLOWLIST

/** Whether the rule covers notifications from [packageName]: its own app, or every app. */
fun BlockerRule.appliesTo(packageName: String?): Boolean =
    isAllApps || this.packageName == packageName