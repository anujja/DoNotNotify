package com.donotnotify.donotnotify.ui.components

import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
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
import com.donotnotify.donotnotify.StackChannels

/**
 * The rule type picker. A dropdown rather than segmented buttons: four types don't fit one row,
 * least of all in languages with long type names.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuleTypeDropdown(selected: RuleType, onSelected: (RuleType) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.padding(bottom = 4.dp)
    ) {
        TextField(
            value = selected.label(),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(stringResource(R.string.rule_type_label)) },
            leadingIcon = { Icon(selected.icon(), contentDescription = null, tint = selected.accentColor()) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            RuleType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(type.label()) },
                    leadingIcon = { Icon(type.icon(), contentDescription = null, tint = type.accentColor()) },
                    onClick = {
                        onSelected(type)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuleDialog(
    title: String,
    initialRule: BlockerRule,
    onDismiss: () -> Unit,
    onSave: (BlockerRule) -> Unit,
    onDelete: () -> Unit // Called once the user confirms the delete
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

    val suggestedName = remember(titleFilter, textFilter, initialRule.appName) {
        suggestedStackRuleName(initialRule.appName.orEmpty(), titleFilter, textFilter)
    }

    if (showAdvancedDialog) {
        AdvancedRuleConfigDialog(
            initialConfig = advancedConfig,
            initialIsEnabled = isEnabled,
            initialName = ruleName,
            suggestedName = suggestedName,
            ruleType = ruleType,
            soundChannelId = if (ruleType == RuleType.STACK &&
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
                onDelete()
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

                RuleTypeDropdown(selected = ruleType, onSelected = { ruleType = it })

                Text(
                    text = ruleType.description(),
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
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(
                        onClick = { showDeleteConfirmationDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Text(stringResource(R.string.delete))
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
                                advancedConfig = advancedConfig.withOnlyOptionsFor(ruleType),
                                name = ruleName.ifBlank { null }
                            )
                            onSave(newRule)
                        }) {
                            Text(stringResource(R.string.save))
                        }
                    }
                }
            }
        }
    }
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
        onDismiss = onDismiss,
        onSave = { newRule ->
            onUpdateRule(rule, newRule)
        },
        onDelete = { onDeleteRule(rule) }
    )
}
