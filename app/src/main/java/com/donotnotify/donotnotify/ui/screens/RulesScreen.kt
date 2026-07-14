package com.donotnotify.donotnotify.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.AccessAlarms
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.automirrored.outlined.Rule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.donotnotify.donotnotify.BlockerRule
import com.donotnotify.donotnotify.R
import com.donotnotify.donotnotify.RuleType
import com.donotnotify.donotnotify.StackedNotificationManager
import com.donotnotify.donotnotify.ui.components.EmptyState
import com.donotnotify.donotnotify.ui.components.label

@Composable
fun RulesScreen(
    rules: List<BlockerRule>,
    onRuleClick: (BlockerRule) -> Unit,
    onDeleteRuleClick: (BlockerRule) -> Unit, // This lambda is no longer directly used for UI, but kept for consistency if needed elsewhere.
    onBrowsePrebuiltRulesClick: () -> Unit,
    onToggleAllRules: (Boolean) -> Unit
) {
    var showAddRuleDialog by remember { mutableStateOf(false) }

    if (showAddRuleDialog) {
        AlertDialog(
            onDismissRequest = { showAddRuleDialog = false },
            title = { Text(stringResource(R.string.how_to_add_rule)) },
            text = { Text(stringResource(R.string.how_to_add_rule_desc)) },
            confirmButton = {
                TextButton(onClick = { showAddRuleDialog = false }) {
                    Text(stringResource(R.string.got_it))
                }
            }
        )
    }

    // Group rules by packageName; apps with multiple rules get a section header
    val grouped = rules.groupBy { it.packageName ?: it.appName ?: "" }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = WindowInsets.navigationBars.asPaddingValues()
    ) {
        if (rules.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.AutoMirrored.Outlined.Rule,
                    title = stringResource(R.string.no_rules_created),
                    description = stringResource(R.string.no_rules_created_desc),
                    actionLabel = stringResource(R.string.browse_prebuilt_rules),
                    onAction = onBrowsePrebuiltRulesClick
                )
            }
        } else {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.rules_master_switch_label),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(
                        checked = rules.all { it.isEnabled },
                        onCheckedChange = onToggleAllRules
                    )
                }
                HorizontalDivider()
                Text(
                    text = stringResource(R.string.rules_auto_block_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                )
            }
            grouped.forEach { (_, appRules) ->
                if (appRules.size == 1) {
                    // Single rule — show as flat card (unchanged)
                    item(key = "rule_${appRules[0].packageName}_${appRules[0].ruleType}_0") {
                        RuleCard(rule = appRules[0], showAppName = true, onClick = { onRuleClick(appRules[0]) })
                    }
                } else {
                    // Multiple rules — show a section header then indented rule cards
                    val appName = appRules[0].appName.orEmpty()
                    item(key = "header_${appRules[0].packageName}") {
                        Text(
                            text = "$appName (${appRules.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp, bottom = 4.dp)
                        )
                        HorizontalDivider()
                    }
                    itemsIndexed(
                        appRules,
                        key = { index, rule -> "rule_${rule.packageName}_${rule.ruleType}_$index" }
                    ) { _, rule ->
                        RuleCard(
                            rule = rule,
                            showAppName = false,
                            modifier = Modifier.padding(start = 8.dp),
                            onClick = { onRuleClick(rule) }
                        )
                    }
                }
            }
        }
        item {
            Button(
                onClick = { showAddRuleDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                Text(stringResource(R.string.add_new_rule))
            }
            Button(
                onClick = onBrowsePrebuiltRulesClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 16.dp)
            ) {
                Text(stringResource(R.string.browse_prebuilt_rules))
            }
        }
    }
}

@Composable
private fun RuleCard(
    rule: BlockerRule,
    showAppName: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .alpha(if (rule.isEnabled) 1f else 0.5f)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 12.dp)
            ) {
                if (showAppName) {
                    Text(
                        text = rule.appName.orEmpty(),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textDecoration = if (rule.isEnabled) null else TextDecoration.LineThrough
                    )
                }
                val notApplicable = stringResource(R.string.not_applicable)
                val titleFilterText = if (rule.titleFilter.isNullOrBlank()) notApplicable else rule.titleFilter.orEmpty()
                Text(
                    text = stringResource(R.string.notification_title_prefix, titleFilterText),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val textFilterText = if (rule.textFilter.isNullOrBlank()) notApplicable else rule.textFilter.orEmpty()
                Text(
                    text = stringResource(R.string.notification_text_prefix, textFilterText),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (rule.ruleType == RuleType.STACK) {
                    val context = LocalContext.current
                    val postBlock = StackedNotificationManager.canPost(context)
                    if (postBlock != StackedNotificationManager.PostBlock.OK) {
                        val warning = when (postBlock) {
                            StackedNotificationManager.PostBlock.CHANNEL_DISABLED ->
                                stringResource(R.string.stack_warning_channel_disabled)
                            else ->
                                stringResource(R.string.stack_warning_notifications_disabled)
                        }
                        Text(
                            text = warning,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .clickable {
                                    runCatching {
                                        val intent = when {
                                            postBlock == StackedNotificationManager.PostBlock.CHANNEL_DISABLED &&
                                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ->
                                                Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                                                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                                    .putExtra(
                                                        Settings.EXTRA_CHANNEL_ID,
                                                        StackedNotificationManager.CHANNEL_ID
                                                    )
                                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ->
                                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                            else ->
                                                Intent(
                                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                                    android.net.Uri.fromParts("package", context.packageName, null)
                                                )
                                        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(intent)
                                    }
                                }
                        )
                    }
                }
            }
            if (rule.advancedConfig?.isTimeLimitEnabled == true) {
                Icon(
                    imageVector = Icons.Filled.AccessAlarms,
                    contentDescription = stringResource(R.string.time_limited_rule),
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp)
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                val icon = when (rule.ruleType) {
                    RuleType.DENYLIST -> Icons.Filled.Block
                    RuleType.ALLOWLIST -> Icons.Filled.CheckCircle
                    RuleType.STACK -> Icons.Filled.Layers
                }
                Icon(
                    imageVector = icon,
                    contentDescription = rule.ruleType.label()
                )
                if (rule.hitCount > 0) {
                    Text(
                        text = stringResource(R.string.hits_count, rule.hitCount),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Text(
                        text = stringResource(R.string.no_hits),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
