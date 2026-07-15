package com.donotnotify.donotnotify.ui.components

import android.app.TimePickerDialog
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.donotnotify.donotnotify.AdvancedRuleConfig
import com.donotnotify.donotnotify.BlockerRule
import com.donotnotify.donotnotify.MatchType
import com.donotnotify.donotnotify.R
import com.donotnotify.donotnotify.RuleType
import com.donotnotify.donotnotify.SimpleNotification
import com.donotnotify.donotnotify.StackChannels
import java.util.Locale

@Composable
fun TimeSelector(
    label: String,
    hour: Int,
    minute: Int,
    onTimeSelected: (Int, Int) -> Unit
) {
    val context = LocalContext.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Text(text = label, modifier = Modifier.weight(1f))
        TextButton(onClick = {
            TimePickerDialog(
                context,
                { _, h, m -> onTimeSelected(h, m) },
                hour,
                minute,
                true // 24 hour view
            ).show()
        }) {
            Text(text = String.format(Locale.getDefault(), "%02d:%02d", hour, minute))
        }
    }
}

@Composable
fun AdvancedRuleConfigDialog(
    initialConfig: AdvancedRuleConfig,
    initialIsEnabled: Boolean,
    initialName: String,
    suggestedName: String,
    /** True for STACK rules: shows the channel-name field. */
    showRuleName: Boolean,
    /** Non-null (a channel id) shows the "Sound & vibration" deep-link for that channel. */
    soundChannelId: String?,
    onDismiss: () -> Unit,
    onSave: (AdvancedRuleConfig, Boolean, String) -> Unit
) {
    var config by remember { mutableStateOf(initialConfig) }
    var isEnabled by remember { mutableStateOf(initialIsEnabled) }
    var ruleName by remember { mutableStateOf(initialName) }
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 20.dp)
                    .imePadding()
                    .verticalScroll(scrollState)
            ) {
                Text(
                    stringResource(R.string.advanced_configuration),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                ToggleRow(
                    checked = isEnabled,
                    label = stringResource(R.string.enable_rule),
                    onToggle = { isEnabled = it }
                )
                ToggleRow(
                    checked = config.isTimeLimitEnabled,
                    label = stringResource(R.string.enable_time_limit),
                    onToggle = { config = config.copy(isTimeLimitEnabled = it) }
                )

                if (config.isTimeLimitEnabled) {
                    // Indent the pickers so they read as children of the time-limit toggle.
                    Column(modifier = Modifier.padding(start = 40.dp, top = 4.dp)) {
                        TimeSelector(
                            label = stringResource(R.string.start_time),
                            hour = config.startTimeHour,
                            minute = config.startTimeMinute,
                            onTimeSelected = { h, m ->
                                config = config.copy(startTimeHour = h, startTimeMinute = m)
                            }
                        )
                        TimeSelector(
                            label = stringResource(R.string.end_time),
                            hour = config.endTimeHour,
                            minute = config.endTimeMinute,
                            onTimeSelected = { h, m ->
                                config = config.copy(endTimeHour = h, endTimeMinute = m)
                            }
                        )
                    }
                }

                if (showRuleName) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                    TextField(
                        value = ruleName,
                        onValueChange = { ruleName = it },
                        label = { Text(stringResource(R.string.rule_name_optional)) },
                        supportingText = {
                            Text(stringResource(R.string.rule_name_supporting))
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    // Explicit opt-in: tapping this is what publishes filter-derived text as the
                    // channel name. Prefilling the field would mean a user who just taps Save
                    // silently exposes their filter to any notification listener.
                    if (suggestedName.isNotBlank() && ruleName != suggestedName) {
                        TextButton(
                            onClick = { ruleName = suggestedName },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                        ) {
                            Text(stringResource(R.string.rule_name_use_suggestion, suggestedName))
                        }
                    }

                    // Sound/vibration are user-owned once the channel exists — Android gives the
                    // app no way to change them — so link out rather than fake an in-app control.
                    // Only for a saved rule: the channel must exist before it can be configured.
                    if (soundChannelId != null) {
                        OutlinedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                                .clickable {
                                    runCatching {
                                        val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                            .putExtra(Settings.EXTRA_CHANNEL_ID, soundChannelId)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(intent)
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.Notifications,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        stringResource(R.string.rule_sound_vibration),
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        stringResource(R.string.rule_sound_vibration_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { onSave(config, isEnabled, ruleName) }) {
                        Text(stringResource(R.string.save))
                    }
                }
            }
        }
    }
}

/** Full-width, tappable checkbox row — larger target and consistent rhythm than a bare Checkbox. */
@Composable
private fun ToggleRow(
    checked: Boolean,
    label: String,
    onToggle: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!checked) }
            .padding(vertical = 4.dp)
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Spacer(modifier = Modifier.width(12.dp))
        Text(label, modifier = Modifier.weight(1f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuleDialog(
    title: String,
    initialRule: BlockerRule,
    isEditMode: Boolean,
    onDismiss: () -> Unit,
    onSave: (BlockerRule) -> Unit,
    onDelete: (() -> Unit)? = null // This lambda now triggers the confirmation dialog
) {
    var titleFilter by remember { mutableStateOf(initialRule.titleFilter.orEmpty()) }
    var titleMatchType by remember { mutableStateOf(initialRule.titleMatchType) }
    var textFilter by remember { mutableStateOf(initialRule.textFilter.orEmpty()) }
    var textMatchType by remember { mutableStateOf(initialRule.textMatchType) }
    var ruleType by remember { mutableStateOf(initialRule.ruleType) }
    var isEnabled by remember { mutableStateOf(initialRule.isEnabled) }
    var advancedConfig by remember { mutableStateOf(initialRule.advancedConfig ?: AdvancedRuleConfig()) }
    var ruleName by remember { mutableStateOf(initialRule.name.orEmpty()) }
    var showAdvancedDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmationDialog by remember { mutableStateOf(false) } // State for delete confirmation
    val scrollState = rememberScrollState()

    // A name derived from the rule's filters, offered as a *suggestion* only. It is never
    // prefilled: the name becomes the notification channel's name, which any notification
    // listener can read — so filter text must not be published unless the user chooses it.
    val suggestedName = remember(titleFilter, textFilter, initialRule.appName) {
        val filter = titleFilter.ifBlank { textFilter }.trim()
        val app = initialRule.appName.orEmpty()
        when {
            filter.isBlank() -> ""
            app.isBlank() -> filter.take(40)
            else -> "$app — $filter".take(40)
        }
    }

    if (showAdvancedDialog) {
        AdvancedRuleConfigDialog(
            initialConfig = advancedConfig,
            initialIsEnabled = isEnabled,
            initialName = ruleName,
            suggestedName = suggestedName,
            showRuleName = ruleType == RuleType.STACK,
            // The channel exists only after the rule is saved, so the deep-link is edit-mode only.
            soundChannelId = if (isEditMode && ruleType == RuleType.STACK &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            ) StackChannels.channelIdFor(initialRule) else null,
            onDismiss = { showAdvancedDialog = false },
            onSave = { config, enabled, name ->
                advancedConfig = config
                isEnabled = enabled
                ruleName = name
                showAdvancedDialog = false
            }
        )
    }

    if (showDeleteConfirmationDialog) {
        DeleteConfirmationDialog(
            itemName = stringResource(R.string.rule_for, initialRule.appName.orEmpty()),
            onDismiss = { showDeleteConfirmationDialog = false },
            onConfirm = {
                onDelete?.invoke() // Call the actual delete lambda passed from EditRuleDialog
                showDeleteConfirmationDialog = false
                onDismiss() // Dismiss the EditRuleDialog after deletion
            }
        )
    }

    val view = LocalView.current
    Dialog(
        onDismissRequest = onDismiss,
        // Don't let the back gesture or an edge/outside touch auto-dismiss the
        // dialog. We handle back ourselves: a visible keyboard is hidden first,
        // so edits aren't lost; a second back (keyboard down) dismisses.
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        BackHandler {
            val imeVisible = ViewCompat.getRootWindowInsets(view)
                ?.isVisible(WindowInsetsCompat.Type.ime()) == true
            if (imeVisible) {
                ViewCompat.getWindowInsetsController(view)
                    ?.hide(WindowInsetsCompat.Type.ime())
            } else {
                onDismiss()
            }
        }
        Card {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .imePadding()
                    .verticalScroll(scrollState)
            ) {
                Text(
                    title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    RuleType.entries.forEachIndexed { index, type ->
                        SegmentedButton(
                            selected = ruleType == type,
                            onClick = { ruleType = type },
                            modifier = Modifier.weight(1f),
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = RuleType.entries.size),
                        ) {
                            Text(
                                text = type.label(),
                                maxLines = 1,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }

                Text(
                    text = stringResource(
                        when (ruleType) {
                            RuleType.DENYLIST -> R.string.rule_type_desc_denylist
                            RuleType.ALLOWLIST -> R.string.rule_type_desc_allowlist
                            RuleType.STACK -> R.string.rule_type_desc_stack
                        }
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                )

                Spacer(modifier = Modifier.padding(vertical = 4.dp))

                TextField(
                    value = titleFilter,
                    onValueChange = { titleFilter = it },
                    label = { Text(stringResource(R.string.title_filter_optional)) },
                    modifier = Modifier.fillMaxWidth()
                )
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    MatchType.entries.forEachIndexed { index, matchType ->
                        SegmentedButton(
                            selected = titleMatchType == matchType,
                            onClick = { titleMatchType = matchType },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = MatchType.entries.size),
                        ) {
                            Text(matchType.label())
                        }
                    }
                }

                Spacer(modifier = Modifier.padding(vertical = 8.dp))

                TextField(
                    value = textFilter,
                    onValueChange = { textFilter = it },
                    label = { Text(stringResource(R.string.text_filter_optional)) },
                    modifier = Modifier.fillMaxWidth()
                )
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    MatchType.entries.forEachIndexed { index, matchType ->
                        SegmentedButton(
                            selected = textMatchType == matchType,
                            onClick = { textMatchType = matchType },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = MatchType.entries.size),
                        ) {
                            Text(matchType.label())
                        }
                    }
                }

                Spacer(modifier = Modifier.padding(vertical = 16.dp))

                Button(
                    onClick = { showAdvancedDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.advanced_configuration))
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = if (isEditMode) Arrangement.SpaceBetween else Arrangement.End
                ) {
                    if (isEditMode && onDelete != null) {
                        Button(onClick = { showDeleteConfirmationDialog = true }) { // Changed to show confirmation dialog
                            Text(stringResource(R.string.delete))
                        }
                    }
                    Row {
                        Button(onClick = onDismiss) {
                            Text(stringResource(R.string.cancel))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            val newRule = initialRule.copy(
                                titleFilter = titleFilter.ifBlank { null },
                                titleMatchType = titleMatchType,
                                textFilter = textFilter.ifBlank { null },
                                textMatchType = textMatchType,
                                ruleType = ruleType,
                                isEnabled = isEnabled,
                                advancedConfig = advancedConfig,
                                name = ruleName.ifBlank { null }
                            )
                            onSave(newRule)
                        }) {
                            Text(if (isEditMode) stringResource(R.string.save) else stringResource(R.string.save_rule))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRuleDialog(
    notification: SimpleNotification,
    onDismiss: () -> Unit,
    onAddRule: (BlockerRule) -> Unit
) {
    val initialRule = remember(notification) {
        BlockerRule(
            appName = notification.appLabel.orEmpty(),
            packageName = notification.packageName.orEmpty(),
            titleFilter = notification.title,
            titleMatchType = MatchType.CONTAINS,
            textFilter = notification.text,
            textMatchType = MatchType.CONTAINS,
            ruleType = RuleType.DENYLIST,
            isEnabled = true
        )
    }

    RuleDialog(
        title = stringResource(R.string.add_rule_title, initialRule.appName.orEmpty()),
        initialRule = initialRule,
        isEditMode = false,
        onDismiss = onDismiss,
        onSave = { newRule ->
            onAddRule(newRule)
            Log.d("RuleEvent", "Rule Created: $newRule")
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditRuleDialog(
    rule: BlockerRule,
    onDismiss: () -> Unit,
    onUpdateRule: (BlockerRule, BlockerRule) -> Unit,
    onDeleteRule: (BlockerRule) -> Unit
) {
    RuleDialog(
        title = stringResource(R.string.edit_rule_title, rule.appName.orEmpty()),
        initialRule = rule,
        isEditMode = true,
        onDismiss = onDismiss,
        onSave = { newRule ->
            onUpdateRule(rule, newRule)
            Log.d("RuleEvent", "Rule Updated: $newRule")
        },
        onDelete = { onDeleteRule(rule) } // This onDelete now sets the state to show the confirmation dialog in RuleDialog
    )
}
