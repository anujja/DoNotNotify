package com.donotnotify.donotnotify.ui.screens

import android.text.format.DateUtils
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.donotnotify.donotnotify.SimpleNotification

@Composable
fun HistoryScreen(
    notifications: List<SimpleNotification>,
    unmonitoredApps: Set<String>,
    onNotificationClick: (SimpleNotification) -> Unit,
    onClearHistory: () -> Unit,
    onDeleteNotification: (SimpleNotification) -> Unit,
    onStopMonitoring: (String, String) -> Unit,
    onResumeMonitoring: (String) -> Unit
) {
    var expandedApps by remember { mutableStateOf(setOf<String>()) }
    var isUnmonitoredAppsExpanded by remember { mutableStateOf(false) }
    val groupedNotifications = remember(notifications) {
        notifications
            .groupBy { it.appLabel ?: it.packageName.orEmpty() }
            .entries
            .sortedByDescending { (_, notifs) -> notifs.maxOf { it.timestamp } }
    }
    val context = LocalContext.current
    val packageManager = context.packageManager
    val listState = rememberLazyListState()

    val unmonitoredAppsHeaderIndex = remember(notifications, expandedApps) {
        if (notifications.isEmpty()) {
            1
        } else {
            var count = 1 // Intro text
            groupedNotifications.forEach { (appName, notifs) ->
                count += 1 // Header
                if (expandedApps.contains(appName)) {
                    count += notifs.size
                    count += 1 // Stop monitoring
                }
            }
            count + 1 // Clear History
        }
    }

    LaunchedEffect(isUnmonitoredAppsExpanded) {
        if (isUnmonitoredAppsExpanded) {
            listState.animateScrollToItem(unmonitoredAppsHeaderIndex)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
    ) {
        if (notifications.isEmpty()) {
            item {
                Text(
                    text = "Waiting to receive new notifications...",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 32.dp)
                )
            }
        } else {
            groupedNotifications.forEach { (appName, notifs) ->
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (!expandedApps.contains(appName)) {
                                    Log.d("HistoryScreen", "Expanded app: $appName, package: ${notifs.firstOrNull()?.packageName}")
                                }
                                expandedApps = if (expandedApps.contains(appName)) {
                                    expandedApps - appName
                                } else {
                                    expandedApps + appName
                                }
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$appName (${notifs.size})",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = {
                            if (!expandedApps.contains(appName)) {
                                Log.d("HistoryScreen", "Expanded app: $appName, package: ${notifs.firstOrNull()?.packageName}")
                            }
                            expandedApps = if (expandedApps.contains(appName)) {
                                expandedApps - appName
                            } else {
                                expandedApps + appName
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = if (expandedApps.contains(appName)) "Collapse" else "Expand"
                            )
                        }
                    }
                }

                if (expandedApps.contains(appName)) {
                    items(notifs) { notification ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp, horizontal = 8.dp)
                                .clickable { onNotificationClick(notification) }
                        ) {
                            Row(
                                modifier = Modifier.padding(start = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(vertical = 12.dp)) {
                                    Text(
                                        text = "Title: ${notification.title.orEmpty()}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Text: ${notification.text.orEmpty()}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = DateUtils.getRelativeTimeSpanString(notification.timestamp).toString(),
                                        style = MaterialTheme.typography.bodySmall,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, end = 16.dp)
                                    )
                                }
                                IconButton(onClick = { onDeleteNotification(notification) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                                }
                            }
                        }
                    }
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            TextButton(onClick = {
                                val packageName = notifs.firstOrNull()?.packageName
                                if (packageName != null) {
                                    onStopMonitoring(packageName, appName)
                                }
                            }) {
                                Text("Stop monitoring $appName")
                            }
                        }
                    }
                }
            }

            item {
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.Center) {
                    TextButton(onClick = onClearHistory) { Text("Clear History") }
                }
            }
        }
        
        if (unmonitoredApps.isNotEmpty()) {
            item {
                Divider(modifier = Modifier.padding(vertical = 16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isUnmonitoredAppsExpanded = !isUnmonitoredAppsExpanded }
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Unmonitored Apps (${unmonitoredApps.size})",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { isUnmonitoredAppsExpanded = !isUnmonitoredAppsExpanded }) {
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = if (isUnmonitoredAppsExpanded) "Collapse" else "Expand"
                        )
                    }
                }
            }
            if (isUnmonitoredAppsExpanded) {
                items(unmonitoredApps.toList()) { packageName ->
                    val appLabel = try {
                        packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
                    } catch (e: Exception) {
                        packageName
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = appLabel,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        TextButton(onClick = { onResumeMonitoring(packageName) }) {
                            Text("Resume")
                        }
                    }
                }
            }
        }
    }
}
