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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.automirrored.outlined.Rule
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.text.font.FontFamily
import com.donotnotify.donotnotify.BlockerRule
import com.donotnotify.donotnotify.CommunityShare
import com.donotnotify.donotnotify.ExternalLinks
import com.donotnotify.donotnotify.PrebuiltRuleReconciler
import com.donotnotify.donotnotify.PrebuiltRulesRepository
import com.donotnotify.donotnotify.R
import com.donotnotify.donotnotify.RuleType
import com.donotnotify.donotnotify.StackChannels
import com.donotnotify.donotnotify.StackedNotificationManager
import com.donotnotify.donotnotify.ui.components.EmptyState
import com.donotnotify.donotnotify.ui.components.label

@Composable
fun RulesScreen(
    rules: List<BlockerRule>,
    onRuleClick: (BlockerRule) -> Unit,
    onCreateRuleClick: () -> Unit,
    onBrowsePrebuiltRulesClick: () -> Unit,
    onToggleAllRules: (Boolean) -> Unit
) {
    // Group rules by packageName; apps with multiple rules get a section header
    val grouped = rules.groupBy { it.packageName ?: it.appName ?: "" }

    val context = LocalContext.current
    val nudgePrefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    var nudgeDismissed by remember {
        mutableStateOf(
            nudgePrefs.getStringSet(CommunityShare.PREF_NUDGE_DISMISSED, emptySet()).orEmpty().toSet()
        )
    }
    fun dismissNudge(ruleId: String) {
        // Prune ids of since-deleted rules so the persisted set stays bounded by the rule list.
        val liveIds = rules.mapTo(HashSet()) { it.id }
        nudgeDismissed = (nudgeDismissed intersect liveIds) + ruleId
        nudgePrefs.edit().putStringSet(CommunityShare.PREF_NUDGE_DISMISSED, nudgeDismissed).apply()
    }
    // Prebuilt rules are already community-shared; their cards never nudge. Null until loaded.
    // The repository degrades load failures to an empty list; the catalog is never genuinely
    // empty, so treat empty as "unknown" and keep every nudge suppressed rather than nudging
    // prebuilt rules we failed to recognize.
    var prebuiltSignatures by remember { mutableStateOf<Set<CommunityShare.NudgeSignature>?>(null) }
    LaunchedEffect(Unit) {
        prebuiltSignatures = PrebuiltRulesRepository(context).getPrebuiltRules()
            .takeIf { it.isNotEmpty() }
            ?.map { CommunityShare.signatureOf(it) }
            ?.toSet()
    }
    // Share flow: nudge → in-app preview of the exact payload → browser only on confirm.
    var shareDialogRule by remember { mutableStateOf<BlockerRule?>(null) }
    shareDialogRule?.let { rule ->
        CommunitySharePreviewDialog(
            rule = rule,
            onConfirmed = {
                dismissNudge(rule.id)
                shareDialogRule = null
            },
            onDismiss = { shareDialogRule = null }
        )
    }

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
                    item(key = "rule_${appRules[0].id}") {
                        RuleCard(
                            rule = appRules[0],
                            showAppName = true,
                            showCommunityNudge = CommunityShare.isNudgeEligible(appRules[0], nudgeDismissed, prebuiltSignatures),
                            onShareToCommunity = { shareDialogRule = appRules[0] },
                            onDismissNudge = { dismissNudge(appRules[0].id) },
                            onClick = { onRuleClick(appRules[0]) }
                        )
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
                        // Key on the stable id: an index-based key shifts on delete/reorder and
                        // would strand each row's remembered channel-warning state on a sibling.
                        key = { _, rule -> "rule_${rule.id}" }
                    ) { _, rule ->
                        RuleCard(
                            rule = rule,
                            showAppName = false,
                            modifier = Modifier.padding(start = 8.dp),
                            showCommunityNudge = CommunityShare.isNudgeEligible(rule, nudgeDismissed, prebuiltSignatures),
                            onShareToCommunity = { shareDialogRule = rule },
                            onDismissNudge = { dismissNudge(rule.id) },
                            onClick = { onRuleClick(rule) }
                        )
                    }
                }
            }
        }
        item {
            Button(
                onClick = onCreateRuleClick,
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

/** Cap on the JSON characters rendered in the preview — display only, copy/share always carry it all. */
private const val SHARE_PREVIEW_MAX_CHARS = 4000

/**
 * In-app review step before anything reaches the network: shows the JSON that will be
 * embedded in the prefilled GitHub issue. The browser is opened only from here, and the
 * nudge is marked handled only when the handoff succeeds. Rules too large to prefill
 * (payload.prefilled == false) switch to a copy-and-paste flow: the confirm button first
 * puts the JSON on the clipboard, then opens the plain new-issue page.
 */
@Composable
private fun CommunitySharePreviewDialog(
    rule: BlockerRule,
    onConfirmed: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val payload = remember(rule.id) {
        CommunityShare.sharePayload(rule, PrebuiltRuleReconciler.currentLocaleTag(context))
    }
    fun copyJson() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("DoNotNotify rule", payload.json))
        // Android 13+ shows its own clipboard confirmation overlay.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(context, R.string.toast_copied_to_clipboard, Toast.LENGTH_SHORT).show()
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.community_share_preview_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(
                        if (payload.prefilled) R.string.community_share_preview_desc
                        else R.string.community_share_manual_desc
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = if (payload.json.length > SHARE_PREVIEW_MAX_CHARS) {
                        payload.json.take(SHARE_PREVIEW_MAX_CHARS) + "…"
                    } else {
                        payload.json
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .heightIn(max = 200.dp)
                        .verticalScroll(rememberScrollState())
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (!payload.prefilled) copyJson()
                if (ExternalLinks.open(context, payload.url)) {
                    onConfirmed()
                }
            }) {
                Text(
                    stringResource(
                        if (payload.prefilled) R.string.community_share_open
                        else R.string.community_share_copy_open
                    )
                )
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { copyJson() }) { Text(stringResource(R.string.copy_json)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        }
    )
}

@Composable
private fun RuleCard(
    rule: BlockerRule,
    showAppName: Boolean,
    modifier: Modifier = Modifier,
    showCommunityNudge: Boolean = false,
    onShareToCommunity: () -> Unit = {},
    onDismissNudge: () -> Unit = {},
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
                    // Re-query when the user returns from system settings — otherwise a warning
                    // they just fixed (or newly caused) would be stale until the screen is rebuilt.
                    val lifecycleOwner = LocalLifecycleOwner.current
                    var refreshToken by remember { mutableIntStateOf(0) }
                    DisposableEffect(lifecycleOwner) {
                        val observer = LifecycleEventObserver { _, event ->
                            if (event == Lifecycle.Event.ON_RESUME) refreshToken++
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                    }
                    val postBlock = remember(rule.id, refreshToken) {
                        StackedNotificationManager.canPost(context, rule)
                    }
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
                                                        StackChannels.channelIdFor(rule)
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
        if (showCommunityNudge) {
            HorizontalDivider()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 16.dp, end = 4.dp)
            ) {
                Text(
                    text = stringResource(R.string.community_nudge_text, rule.hitCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onShareToCommunity) {
                    Text(stringResource(R.string.community_nudge_share))
                }
                IconButton(onClick = onDismissNudge) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.close)
                    )
                }
            }
        }
    }
}
