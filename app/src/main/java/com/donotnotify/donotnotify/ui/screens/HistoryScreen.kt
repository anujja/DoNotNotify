package com.donotnotify.donotnotify.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.donotnotify.donotnotify.AppInfoStorage
import com.donotnotify.donotnotify.R
import com.donotnotify.donotnotify.SimpleNotification
import com.donotnotify.donotnotify.ui.components.ConfirmationDialog
import com.donotnotify.donotnotify.ui.components.EmptyState
import com.donotnotify.donotnotify.ui.components.NotificationSearchField
import com.donotnotify.donotnotify.ui.components.filterNotifications
import com.donotnotify.donotnotify.ui.components.groupNotificationsByApp
import com.donotnotify.donotnotify.ui.components.groupedItemCount
import com.donotnotify.donotnotify.ui.components.notificationGroups

@Composable
fun HistoryScreen(
    notifications: List<SimpleNotification>,
    unmonitoredApps: Set<String>,
    onNotificationClick: (SimpleNotification) -> Unit,
    onDeleteNotification: (SimpleNotification) -> Unit,
    onStopMonitoring: (String, String) -> Unit,
    onResumeMonitoring: (String) -> Unit
) {
    var expandedApps by remember { mutableStateOf(setOf<String>()) }
    var isUnmonitoredAppsExpanded by remember { mutableStateOf(false) }
    var showStopMonitoringDialog by remember { mutableStateOf<Pair<String, String>?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val filtered = remember(notifications, searchQuery) { filterNotifications(notifications, searchQuery) }
    val groups = remember(filtered) { groupNotificationsByApp(filtered) }
    val context = LocalContext.current
    val appInfoStorage = remember { AppInfoStorage(context) }
    val packageManager = context.packageManager
    val listState = rememberLazyListState()
    val ongoingMessage = stringResource(R.string.ongoing_notification_cannot_block)
    val deleteDescription = stringResource(R.string.delete)

    // Index of the "Unmonitored apps" header: the search field, then either the empty state or
    // the app groups.
    val unmonitoredHeaderIndex = remember(groups, expandedApps, filtered) {
        1 + if (filtered.isEmpty()) 1 else groupedItemCount(groups, expandedApps, hasGroupAction = true)
    }
    LaunchedEffect(isUnmonitoredAppsExpanded) {
        if (isUnmonitoredAppsExpanded) {
            listState.animateScrollToItem(unmonitoredHeaderIndex)
        }
    }

    showStopMonitoringDialog?.let { (packageName, appName) ->
        ConfirmationDialog(
            title = stringResource(R.string.stop_monitoring_title),
            text = stringResource(R.string.stop_monitoring_confirm, appName),
            confirmLabel = stringResource(R.string.stop),
            onConfirm = {
                onStopMonitoring(packageName, appName)
                showStopMonitoringDialog = null
            },
            onDismiss = { showStopMonitoringDialog = null }
        )
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = WindowInsets.navigationBars.asPaddingValues()
    ) {
        item(contentType = "search") {
            NotificationSearchField(query = searchQuery, onQueryChange = { searchQuery = it })
        }

        if (notifications.isEmpty()) {
            item(contentType = "emptyMessage") {
                EmptyState(
                    icon = Icons.Outlined.Inbox,
                    title = stringResource(R.string.no_notifications_yet),
                    description = stringResource(R.string.no_notifications_yet_desc)
                )
            }
        } else if (filtered.isEmpty()) {
            item(contentType = "emptyMessage") {
                EmptyState(
                    icon = Icons.Outlined.SearchOff,
                    title = stringResource(R.string.no_results_found),
                    description = stringResource(R.string.no_results_found_desc)
                )
            }
        } else {
            notificationGroups(
                groups = groups,
                expandedApps = expandedApps,
                onToggleApp = { app ->
                    expandedApps = if (app in expandedApps) expandedApps - app else expandedApps + app
                },
                appInfoStorage = appInfoStorage,
                ongoingMessage = ongoingMessage,
                deleteContentDescription = deleteDescription,
                onNotificationClick = onNotificationClick,
                onDeleteNotification = onDeleteNotification,
                groupAction = { group ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        TextButton(onClick = {
                            group.packageName?.let { showStopMonitoringDialog = it to group.appName }
                        }) {
                            Text(stringResource(R.string.stop_monitoring_app, group.appName))
                        }
                    }
                }
            )
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
                        text = stringResource(R.string.unmonitored_apps, unmonitoredApps.size),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { isUnmonitoredAppsExpanded = !isUnmonitoredAppsExpanded }) {
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = if (isUnmonitoredAppsExpanded) stringResource(R.string.collapse) else stringResource(R.string.expand)
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
                            Text(stringResource(R.string.resume))
                        }
                    }
                }
            }
        }
    }
}
