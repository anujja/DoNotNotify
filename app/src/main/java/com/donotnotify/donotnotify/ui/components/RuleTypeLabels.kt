package com.donotnotify.donotnotify.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.donotnotify.donotnotify.AdvancedRuleConfig
import com.donotnotify.donotnotify.BlockerRule
import com.donotnotify.donotnotify.MatchType
import com.donotnotify.donotnotify.R
import com.donotnotify.donotnotify.RuleType
import com.donotnotify.donotnotify.ThrottleManager
import com.donotnotify.donotnotify.isAllApps

/** Localized, user-facing label for a [RuleType] (pickers, badges, lists). */
@Composable
fun RuleType.label(): String = when (this) {
    RuleType.DENYLIST -> stringResource(R.string.denylist)
    RuleType.ALLOWLIST -> stringResource(R.string.allowlist)
    RuleType.STACK -> stringResource(R.string.stack)
    RuleType.THROTTLE -> stringResource(R.string.throttle)
}

/** One-sentence explanation of what a [RuleType] does to matching notifications. */
@Composable
fun RuleType.description(): String = stringResource(
    when (this) {
        RuleType.DENYLIST -> R.string.rule_type_desc_denylist
        RuleType.ALLOWLIST -> R.string.rule_type_desc_allowlist
        RuleType.STACK -> R.string.rule_type_desc_stack
        RuleType.THROTTLE -> R.string.rule_type_desc_throttle
    }
)

/**
 * The app a rule is for, as its card, editor and section header show it: the app's name, else
 * its package, or "All apps" for a rule with no package.
 */
@Composable
fun BlockerRule.appLabel(): String =
    if (isAllApps) stringResource(R.string.all_apps)
    else appName?.takeIf { it.isNotBlank() } ?: packageName.orEmpty()

/** Localized, user-facing label for a [MatchType] (segmented pickers). */
@Composable
fun MatchType.label(): String = when (this) {
    MatchType.CONTAINS -> stringResource(R.string.match_type_contains)
    MatchType.REGEX -> stringResource(R.string.match_type_regex)
}

/** A length of time as "5 min" when it's whole minutes, otherwise "90 s". */
@Composable
fun formatDuration(seconds: Int): String =
    if (seconds % 60 == 0) {
        stringResource(R.string.duration_minutes_short, seconds / 60)
    } else {
        stringResource(R.string.duration_seconds_short, seconds)
    }

/** A THROTTLE rule's rate, e.g. "At most one every 20 s". */
@Composable
fun throttleSummary(config: AdvancedRuleConfig?): String =
    stringResource(R.string.throttle_summary, formatDuration(ThrottleManager.secondsFor(config)))
