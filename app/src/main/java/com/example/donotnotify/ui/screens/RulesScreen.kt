package com.example.donotnotify.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.donotnotify.BlockerRule

@Composable
fun RulesScreen(
    rules: List<BlockerRule>,
    onRuleClick: (BlockerRule) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        if (rules.isEmpty()) {
            item {
                Text(
                    text = "No rules have been created yet to block notifications. To create a rule, please switch to the \"History\" tab and select a notification that you would like to block.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 32.dp)
                )
            }
        } else {
            item {
                Text(
                    text = "Any notifications matching the rules given below will be blocked automatically.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                )
            }
            item {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text("App Name", modifier = Modifier.weight(0.3f), fontWeight = FontWeight.Bold)
                    Text("Title Regex", modifier = Modifier.weight(0.35f), fontWeight = FontWeight.Bold)
                    Text("Text Regex", modifier = Modifier.weight(0.35f), fontWeight = FontWeight.Bold)
                }
                HorizontalDivider()
            }
            items(rules) { rule ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onRuleClick(rule) }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(rule.appName.orEmpty(), modifier = Modifier.weight(0.3f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(rule.titleRegex.orEmpty(), modifier = Modifier.weight(0.35f).padding(horizontal = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(rule.textRegex.orEmpty(), modifier = Modifier.weight(0.35f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                HorizontalDivider()
            }
        }
    }
}
