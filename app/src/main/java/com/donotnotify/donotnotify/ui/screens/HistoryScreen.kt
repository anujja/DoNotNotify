package com.donotnotify.donotnotify.ui.screens

import android.graphics.Bitmap
import android.text.format.DateUtils
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.donotnotify.donotnotify.AppInfoStorage
import com.donotnotify.donotnotify.SimpleNotification
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredNotifications = remember(notifications, searchQuery) {
        if (searchQuery.isBlank()) {
            notifications
        } else {
            val query = searchQuery.lowercase()
            notifications.filter { notification ->
                val appName = (notification.appLabel ?: notification.packageName.orEmpty()).lowercase()
                val title = notification.title.orEmpty().lowercase()
                val text = notification.text.orEmpty().lowercase()
                appName.contains(query) || title.contains(query) || text.contains(query)
            }
        }
    }

    val groupedNotifications = remember(filteredNotifications) {
        filteredNotifications
            .groupBy { it.appLabel ?: it.packageName.orEmpty() }
            .entries
            .sortedByDescending { (_, notifs) -> notifs.maxOf { it.timestamp } }
    }
    val context = LocalContext.current
    val appInfoStorage = remember { AppInfoStorage(context) }
    val packageManager = context.packageManager
    val listState = rememberLazyListState()

    val unmonitoredAppsHeaderIndex = remember(filteredNotifications, expandedApps) {
        var count = 1 // Search bar
        if (filteredNotifications.isEmpty()) {
            count += 1 // Empty message
        } else {
            groupedNotifications.forEach { (appName, notifs) ->
                count += 1 // Header
                if (expandedApps.contains(appName)) {
                    count += notifs.size
                    count += 1 // Stop monitoring
                }
            }
            count += 1 // Clear History
        }
        count
    }

    LaunchedEffect(isUnmonitoredAppsExpanded) {
        if (isUnmonitoredAppsExpanded) {
            listState.animateScrollToItem(unmonitoredAppsHeaderIndex)
        }
    }

    if (showClearHistoryDialog) {
        Dialog(onDismissRequest = { showClearHistoryDialog = false }) {
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Clear History?",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "Are you sure you want to clear all notification history?",
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(onClick = { showClearHistoryDialog = false }, modifier = Modifier.padding(end = 8.dp)) {
                            Text("Cancel")
                        }
                        Button(onClick = {
                            onClearHistory()
                            showClearHistoryDialog = false
                        }) {
                            Text("Clear")
                        }
                    }
                }
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
    ) {
        item(contentType = "search") {
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                placeholder = { Text("Search notifications...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search"
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear search"
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                )
            )
        }

        if (notifications.isEmpty()) {
            item(contentType = "emptyMessage") {
                Text(
                    text = "Waiting to receive new notifications...",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 32.dp)
                )
            }
        } else if (filteredNotifications.isEmpty()) {
            item(contentType = "emptyMessage") {
                Text(
                    text = "No notifications match your search",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 32.dp)
                )
            }
        } else {
            groupedNotifications.forEach { (appName, notifs) ->
                item(key = "header_$appName", contentType = "appHeader") {
                    val packageName = notifs.firstOrNull()?.packageName
                    val appIcon by produceState<Bitmap?>(initialValue = null, key1 = packageName) {
                        if (packageName != null) {
                            value = withContext(Dispatchers.IO) {
                                appInfoStorage.getAppIcon(packageName)
                            }
                        }
                    }

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
                        if (appIcon != null) {
                            Image(
                                bitmap = appIcon!!.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.size(32.dp).padding(end = 8.dp),
                                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
                            )
                        }
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
                    items(notifs, key = { it.id ?: it.timestamp }, contentType = { "notification" }) { notification ->
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
                                if (notification.wasOngoing) {
                                    IconButton(onClick = {
                                        Toast.makeText(context, "This was an ongoing notification. It may not be possible to block it.", Toast.LENGTH_LONG).show()
                                    }) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = "Ongoing Notification",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                                IconButton(onClick = { onDeleteNotification(notification) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                                }
                            }
                        }
                    }
                    item(key = "stopMonitoring_$appName", contentType = "stopMonitoring") {
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

            item(contentType = "clearHistory") {
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.Center) {
                    Button(onClick = { showClearHistoryDialog = true }) { Text("Clear History") }
                }
            }
        }
        
        if (unmonitoredApps.isNotEmpty()) {
            item(contentType = "unmonitoredHeader") {
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
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
                items(unmonitoredApps.toList(), key = { it }, contentType = { "unmonitoredApp" }) { packageName ->
                    val appLabel = remember(packageName) {
                        try {
                            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
                        } catch (e: Exception) {
                            packageName
                        }
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
