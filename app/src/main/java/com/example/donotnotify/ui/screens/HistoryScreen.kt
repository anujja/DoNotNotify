package com.example.donotnotify.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.donotnotify.SimpleNotification

@Composable
fun HistoryScreen(
    notifications: List<SimpleNotification>,
    onNotificationClick: (SimpleNotification) -> Unit,
    onClearHistory: () -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        if (notifications.isEmpty()) {
            item {
                Text(
                    text = "Waiting to receive new notifications...",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                )
            }
        } else {
            item {
                Text(
                    text = "Here is a history of notifications you have received. Tap on one to create a rule to block similar notifications in the future.",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                )
            }
            item {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text("App", modifier = Modifier.weight(0.25f), fontWeight = FontWeight.Bold)
                    Text("Title", modifier = Modifier.weight(0.3f), fontWeight = FontWeight.Bold)
                    Text("Text", modifier = Modifier.weight(0.45f), fontWeight = FontWeight.Bold)
                }
                HorizontalDivider()
            }
            items(notifications) { notification ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onNotificationClick(notification) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text((notification.appLabel ?: notification.packageName).orEmpty(), modifier = Modifier.weight(0.25f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(notification.title.orEmpty(), modifier = Modifier.weight(0.3f).padding(horizontal = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(notification.text.orEmpty(), modifier = Modifier.weight(0.45f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                HorizontalDivider()
            }
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.Center) {
                    TextButton(onClick = onClearHistory) { Text("Clear History") }
                }
            }
        }
    }
}
