package com.donotnotify.donotnotify.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.donotnotify.donotnotify.SimpleNotification

@Composable
fun BlockedScreen(
    notifications: List<SimpleNotification>,
    totalBlockedCount: Int,
    onClearBlockedHistory: () -> Unit,
    onNotificationClick: (SimpleNotification) -> Unit,
    onDeleteNotificationClick: (SimpleNotification) -> Unit
) {
    val context = LocalContext.current
    LazyColumn(modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 16.dp)) {
        item {
            Text(
                text = "$totalBlockedCount notifications have been blocked so far.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            )
        }
        if (notifications.isEmpty()) {
            item {
                Text(
                    text = "No notifications have been blocked recently.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp)
                )
            }
        } else {
            items(notifications) { notification ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onNotificationClick(notification) }
                ) {
                    Row(
                        modifier = Modifier.padding(start = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 12.dp)) {
                            Text(
                                text = (notification.appLabel ?: notification.packageName).orEmpty(),
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
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
                        }
                        if (notification.wasOngoing) {
                            IconButton(onClick = {
                                Toast.makeText(context, "This was an ongoing notification. It may not have been fully blocked.", Toast.LENGTH_LONG).show()
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Ongoing Notification",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        IconButton(onClick = { onDeleteNotificationClick(notification) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Blocked Notification")
                        }
                    }
                }
            }
            item {
                Row(modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp), horizontalArrangement = Arrangement.Center) {
                    TextButton(onClick = onClearBlockedHistory) { Text("Clear Blocked History") }
                }
            }
        }
    }
}