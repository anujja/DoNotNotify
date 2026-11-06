package com.donotnotify.donotnotify.ui.components

import android.graphics.Bitmap
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.donotnotify.donotnotify.AppInfoStorage
import com.donotnotify.donotnotify.R
import com.donotnotify.donotnotify.SimpleNotification
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Notifications from one app, shown under a collapsible header. */
data class NotificationGroup(
    val appName: String,
    val packageName: String?,
    val notifications: List<SimpleNotification>,
)

/** Case-insensitive match on app name, title or text; a blank query keeps everything. */
fun filterNotifications(notifications: List<SimpleNotification>, query: String): List<SimpleNotification> {
    if (query.isBlank()) return notifications
    val q = query.trim().lowercase()
    return notifications.filter { n ->
        (n.appLabel ?: n.packageName.orEmpty()).lowercase().contains(q) ||
            n.title.orEmpty().lowercase().contains(q) ||
            n.text.orEmpty().lowercase().contains(q)
    }
}

/** Groups by app label (package name when unlabelled), most recently active app first. */
fun groupNotificationsByApp(notifications: List<SimpleNotification>): List<NotificationGroup> =
    notifications
        .groupBy { it.appLabel ?: it.packageName.orEmpty() }
        .map { (appName, items) -> NotificationGroup(appName, items.firstOrNull()?.packageName, items) }
        .sortedByDescending { group -> group.notifications.maxOf { it.timestamp } }

/** Number of lazy-list items [notificationGroups] emits, for callers that place items after them. */
fun groupedItemCount(groups: List<NotificationGroup>, expandedApps: Set<String>, hasGroupAction: Boolean): Int =
    groups.sumOf { group ->
        if (group.appName in expandedApps) {
            1 + group.notifications.size + (if (hasGroupAction) 1 else 0)
        } else {
            1
        }
    }

@Composable
fun NotificationSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        placeholder = { Text(stringResource(R.string.search_notifications)) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = stringResource(R.string.search)
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = stringResource(R.string.clear_search)
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

/**
 * Emits one collapsible header per app and, when expanded, an optional [groupAction] row
 * followed by a card per notification. The History and Blocked tabs share this so they look
 * and behave the same. Strings are passed in because this runs in [LazyListScope], not in
 * a composable.
 */
fun LazyListScope.notificationGroups(
    groups: List<NotificationGroup>,
    expandedApps: Set<String>,
    onToggleApp: (String) -> Unit,
    appInfoStorage: AppInfoStorage,
    ongoingMessage: String,
    deleteContentDescription: String,
    onNotificationClick: (SimpleNotification) -> Unit,
    onDeleteNotification: (SimpleNotification) -> Unit,
    groupAction: (@Composable (NotificationGroup) -> Unit)? = null,
) {
    groups.forEach { group ->
        val expanded = group.appName in expandedApps
        item(key = "header_${group.appName}", contentType = "appHeader") {
            AppGroupHeader(group, expanded, appInfoStorage) { onToggleApp(group.appName) }
        }
        if (!expanded) return@forEach
        if (groupAction != null) {
            item(key = "action_${group.appName}", contentType = "groupAction") { groupAction(group) }
        }
        itemsIndexed(
            group.notifications,
            key = { index, n -> "${group.appName}_${index}_${n.id ?: n.timestamp}" },
            contentType = { _, _ -> "notification" }
        ) { _, notification ->
            NotificationCard(
                notification = notification,
                ongoingMessage = ongoingMessage,
                deleteContentDescription = deleteContentDescription,
                onClick = { onNotificationClick(notification) },
                onDelete = { onDeleteNotification(notification) },
            )
        }
    }
}

@Composable
fun AppGroupHeader(
    group: NotificationGroup,
    expanded: Boolean,
    appInfoStorage: AppInfoStorage,
    onToggle: () -> Unit,
) {
    val appIcon by produceState<Bitmap?>(initialValue = null, key1 = group.packageName) {
        val packageName = group.packageName ?: return@produceState
        value = withContext(Dispatchers.IO) { appInfoStorage.getAppIcon(packageName) }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        appIcon?.let { icon ->
            Image(
                bitmap = icon.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .size(32.dp)
                    .padding(end = 8.dp),
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
            )
        }
        Text(
            text = "${group.appName} (${group.notifications.size})",
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onToggle) {
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = stringResource(if (expanded) R.string.collapse else R.string.expand)
            )
        }
    }
}

/** Title, text and relative time; the app is implied by the group header above. */
@Composable
fun NotificationCard(
    notification: SimpleNotification,
    ongoingMessage: String,
    deleteContentDescription: String,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 8.dp)
            .clickable(onClick = onClick)
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
                val title = notification.title.orEmpty()
                val text = notification.text.orEmpty()
                if (title.isNotBlank()) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (text.isNotBlank()) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = DateUtils.getRelativeTimeSpanString(notification.timestamp).toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, end = 16.dp)
                )
            }
            if (notification.wasOngoing) {
                IconButton(onClick = {
                    Toast.makeText(context, ongoingMessage, Toast.LENGTH_LONG).show()
                }) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = stringResource(R.string.ongoing_notification),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = deleteContentDescription)
            }
        }
    }
}
