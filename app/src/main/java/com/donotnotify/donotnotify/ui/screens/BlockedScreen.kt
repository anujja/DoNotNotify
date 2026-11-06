package com.donotnotify.donotnotify.ui.screens

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.donotnotify.donotnotify.AppInfoStorage
import com.donotnotify.donotnotify.R
import com.donotnotify.donotnotify.SimpleNotification
import com.donotnotify.donotnotify.ui.components.EmptyState
import com.donotnotify.donotnotify.ui.components.NotificationSearchField
import com.donotnotify.donotnotify.ui.components.filterNotifications
import com.donotnotify.donotnotify.ui.components.groupNotificationsByApp
import com.donotnotify.donotnotify.ui.components.notificationGroups

@Composable
fun BlockedScreen(
    notifications: List<SimpleNotification>,
    onNotificationClick: (SimpleNotification) -> Unit,
    onDeleteNotificationClick: (SimpleNotification) -> Unit
) {
    var expandedApps by remember { mutableStateOf(setOf<String>()) }
    var searchQuery by remember { mutableStateOf("") }

    val filtered = remember(notifications, searchQuery) { filterNotifications(notifications, searchQuery) }
    val groups = remember(filtered) { groupNotificationsByApp(filtered) }
    val context = LocalContext.current
    val appInfoStorage = remember { AppInfoStorage(context) }
    val ongoingMessage = stringResource(R.string.ongoing_notification_partially_blocked)
    val deleteDescription = stringResource(R.string.delete_blocked_notification)

    LazyColumn(
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
                    icon = Icons.Outlined.NotificationsOff,
                    title = stringResource(R.string.no_blocked_notifications),
                    description = stringResource(R.string.no_blocked_notifications_desc)
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
                onDeleteNotification = onDeleteNotificationClick,
            )
        }
    }
}
