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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.donotnotify.donotnotify.SimpleNotification

@Composable
fun BlockedScreen(
    notifications: List<SimpleNotification>,
    onClearBlockedHistory: () -> Unit,
    onNotificationClick: (SimpleNotification) -> Unit,
    onDeleteNotificationClick: (SimpleNotification) -> Unit
) {
    val context = LocalContext.current
    var showClearBlockedHistoryDialog by remember { mutableStateOf(false) }

    if (showClearBlockedHistoryDialog) {
        Dialog(onDismissRequest = { showClearBlockedHistoryDialog = false }) {
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Clear Blocked History?",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "Are you sure you want to clear all blocked notification history?",
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(onClick = { showClearBlockedHistoryDialog = false }, modifier = Modifier.padding(end = 8.dp)) {
                            Text("Cancel")
                        }
                        Button(onClick = {
                            onClearBlockedHistory()
                            showClearBlockedHistoryDialog = false
                        }) {
                            Text("Clear")
                        }
                    }
                }
            }
        }
    }

    LazyColumn(modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 16.dp)) {
        if (notifications.isEmpty()) {
            item {
                Text(
                    text = "No notifications have been blocked recently.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp, start = 16.dp, end = 16.dp, bottom = 16.dp)
                )
            }
        } else {
            items(notifications, key = { it.id ?: "${it.packageName}_${it.timestamp}_${it.title}_${it.text}" }) { notification ->
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
                    Button(onClick = { showClearBlockedHistoryDialog = true }) { Text("Clear Blocked History") }
                }
            }
        }
    }
}
