package com.donotnotify.donotnotify.ui.components

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.donotnotify.donotnotify.BlockerRule
import com.donotnotify.donotnotify.SimpleNotification

@Composable
fun AddRuleDialog(
    notification: SimpleNotification,
    onDismiss: () -> Unit,
    onAddRule: (BlockerRule) -> Unit
) {
    val context = LocalContext.current
    var appName by remember { mutableStateOf(notification.packageName.orEmpty()) }
    var titleRegex by remember { mutableStateOf(notification.title.orEmpty()) }
    var textRegex by remember { mutableStateOf(notification.text.orEmpty()) }
    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Add New Rule", fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.padding(bottom = 16.dp))
                TextField(
                    value = appName,
                    onValueChange = { appName = it },
                    label = { Text("App Name (Package Name)") },
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            Toast.makeText(context, "App name (package name) cannot be changed", Toast.LENGTH_SHORT).show()
                        }
                )
                TextField(value = titleRegex, onValueChange = { titleRegex = it }, label = { Text("Title Regex (Optional)") }, modifier = Modifier.fillMaxWidth())
                TextField(value = textRegex, onValueChange = { textRegex = it }, label = { Text("Text Regex (Optional)") }, modifier = Modifier.fillMaxWidth())
                Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.End) {
                    Button(onClick = onDismiss, modifier = Modifier.padding(end = 8.dp)) {
                        Text("Cancel")
                    }
                    Button(onClick = {
                        val newRule = BlockerRule(appName, titleRegex.ifBlank { null }, textRegex.ifBlank { null })
                        onAddRule(newRule)
                    }) {
                        Text("Save Rule")
                    }
                }
            }
        }
    }
}

@Composable
fun EditRuleDialog(
    rule: BlockerRule,
    onDismiss: () -> Unit,
    onUpdateRule: (BlockerRule, BlockerRule) -> Unit,
    onDeleteRule: (BlockerRule) -> Unit
) {
    var appName by remember { mutableStateOf(rule.appName.orEmpty()) }
    var titleRegex by remember { mutableStateOf(rule.titleRegex.orEmpty()) }
    var textRegex by remember { mutableStateOf(rule.textRegex.orEmpty()) }
    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Edit Rule", fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.padding(bottom = 16.dp))
                TextField(value = appName, onValueChange = { appName = it }, label = { Text("App Name") }, modifier = Modifier.fillMaxWidth())
                TextField(value = titleRegex, onValueChange = { titleRegex = it }, label = { Text("Title Regex (Optional)") }, modifier = Modifier.fillMaxWidth())
                TextField(value = textRegex, onValueChange = { textRegex = it }, label = { Text("Text Regex (Optional)") }, modifier = Modifier.fillMaxWidth())
                Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Button(onClick = { onDeleteRule(rule) }) {
                        Text("Delete")
                    }
                    Row {
                        Button(onClick = onDismiss) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            val newRule = BlockerRule(appName, titleRegex.ifBlank { null }, textRegex.ifBlank { null })
                            onUpdateRule(rule, newRule)
                        }) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}
