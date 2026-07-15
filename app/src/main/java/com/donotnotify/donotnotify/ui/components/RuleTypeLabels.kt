package com.donotnotify.donotnotify.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.donotnotify.donotnotify.MatchType
import com.donotnotify.donotnotify.R
import com.donotnotify.donotnotify.RuleType

/** Localized, user-facing label for a [RuleType] (segmented pickers, badges, lists). */
@Composable
fun RuleType.label(): String = when (this) {
    RuleType.DENYLIST -> stringResource(R.string.denylist)
    RuleType.ALLOWLIST -> stringResource(R.string.allowlist)
    RuleType.STACK -> stringResource(R.string.stack)
}

/** Localized, user-facing label for a [MatchType] (segmented pickers). */
@Composable
fun MatchType.label(): String = when (this) {
    MatchType.CONTAINS -> stringResource(R.string.match_type_contains)
    MatchType.REGEX -> stringResource(R.string.match_type_regex)
}
