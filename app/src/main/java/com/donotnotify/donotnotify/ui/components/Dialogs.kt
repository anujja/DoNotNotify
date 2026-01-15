package com.donotnotify.donotnotify.ui.components

import android.util.Log // Import Log
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.donotnotify.donotnotify.BlockerRule
import com.donotnotify.donotnotify.MatchType
import com.donotnotify.donotnotify.RuleType
import com.donotnotify.donotnotify.SimpleNotification

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuleDialog(
    title: String,
    initialRule: BlockerRule,
    isEditMode: Boolean,
    onDismiss: () -> Unit,
    onSave: (BlockerRule) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var titleFilter by remember { mutableStateOf(initialRule.titleFilter.orEmpty()) }
    var titleMatchType by remember { mutableStateOf(initialRule.titleMatchType) }
    var textFilter by remember { mutableStateOf(initialRule.textFilter.orEmpty()) }
    var textMatchType by remember { mutableStateOf(initialRule.textMatchType) }
    var ruleType by remember { mutableStateOf(initialRule.ruleType) }
    var isEnabled by remember { mutableStateOf(initialRule.isEnabled) }
    val scrollState = rememberScrollState()

    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(modifier = Modifier.padding(16.dp).verticalScroll(scrollState)) {
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
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = RuleType.entries.size),
                        ) {
                            Text(type.name)
                        }
                    }
                }

                Spacer(modifier = Modifier.padding(vertical = 8.dp))

                TextField(
                    value = titleFilter,
                    onValueChange = { titleFilter = it },
                    label = { Text("Title Filter (Optional)") },
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
                            Text(matchType.name)
                        }
                    }
                }

                Spacer(modifier = Modifier.padding(vertical = 8.dp))

                TextField(
                    value = textFilter,
                    onValueChange = { textFilter = it },
                    label = { Text("Text Filter (Optional)") },
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
                            Text(matchType.name)
                        }
                    }
                }

                if (isEditMode) {
                    Spacer(modifier = Modifier.padding(vertical = 16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Enabled")
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = { isEnabled = it }
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = if (isEditMode) Arrangement.SpaceBetween else Arrangement.End
                ) {
                    if (isEditMode && onDelete != null) {
                        Button(onClick = onDelete) {
                            Text("Delete")
                        }
                    }
                    Row {
                        Button(onClick = onDismiss) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            val newRule = initialRule.copy(
                                titleFilter = titleFilter.ifBlank { null },
                                titleMatchType = titleMatchType,
                                textFilter = textFilter.ifBlank { null },
                                textMatchType = textMatchType,
                                ruleType = ruleType,
                                isEnabled = isEnabled
                            )
                            onSave(newRule)
                        }) {
                            Text(if (isEditMode) "Save" else "Save Rule")
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
            ruleType = RuleType.BLACKLIST,
            isEnabled = true
        )
    }

    RuleDialog(
        title = "Add New Rule (${initialRule.appName})",
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
        title = "Edit Rule (${rule.appName})",
        initialRule = rule,
        isEditMode = true,
        onDismiss = onDismiss,
        onSave = { newRule ->
            onUpdateRule(rule, newRule)
            Log.d("RuleEvent", "Rule Updated: $newRule")
        },
        onDelete = { onDeleteRule(rule) }
    )
}
