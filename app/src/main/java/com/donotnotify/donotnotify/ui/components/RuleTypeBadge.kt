package com.donotnotify.donotnotify.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.donotnotify.donotnotify.R
import com.donotnotify.donotnotify.RuleType

/** The app-wide icon for a rule type (also used at larger sizes than the badge). */
fun RuleType.icon(): ImageVector = when (this) {
    RuleType.DENYLIST -> Icons.Filled.Block
    RuleType.ALLOWLIST -> Icons.Filled.CheckCircle
    RuleType.STACK -> Icons.Filled.Layers
}

/** The app-wide accent color for a rule type. */
@Composable
fun RuleType.accentColor(): Color = when (this) {
    RuleType.DENYLIST -> MaterialTheme.colorScheme.error
    RuleType.ALLOWLIST -> MaterialTheme.colorScheme.primary
    RuleType.STACK -> MaterialTheme.colorScheme.secondary
}

/** Compact icon + label badge identifying a rule type, colored by its accent. */
@Composable
fun RuleTypeBadge(ruleType: RuleType) {
    val icon = ruleType.icon()
    val color = ruleType.accentColor()
    val text = when (ruleType) {
        RuleType.DENYLIST -> stringResource(R.string.denylist)
        RuleType.ALLOWLIST -> stringResource(R.string.allowlist)
        RuleType.STACK -> stringResource(R.string.stack)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            modifier = Modifier.size(14.dp),
            tint = color
        )
        Spacer(modifier = Modifier.width(2.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

/** Rounded keyword pill tinted with the rule type's container color. */
@Composable
fun KeywordChip(keyword: String, ruleType: RuleType) {
    val backgroundColor = when (ruleType) {
        RuleType.DENYLIST -> MaterialTheme.colorScheme.errorContainer
        RuleType.ALLOWLIST -> MaterialTheme.colorScheme.primaryContainer
        RuleType.STACK -> MaterialTheme.colorScheme.secondaryContainer
    }
    val textColor = when (ruleType) {
        RuleType.DENYLIST -> MaterialTheme.colorScheme.onErrorContainer
        RuleType.ALLOWLIST -> MaterialTheme.colorScheme.onPrimaryContainer
        RuleType.STACK -> MaterialTheme.colorScheme.onSecondaryContainer
    }

    Box(
        modifier = Modifier
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = keyword,
            style = MaterialTheme.typography.labelMedium,
            color = textColor
        )
    }
}
