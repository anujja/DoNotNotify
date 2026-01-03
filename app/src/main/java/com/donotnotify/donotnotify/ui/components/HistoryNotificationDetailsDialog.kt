package com.donotnotify.donotnotify.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.donotnotify.donotnotify.SimpleNotification
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryNotificationDetailsDialog(
    notification: SimpleNotification,
    isActionAvailable: Boolean,
    onDismiss: () -> Unit,
    onTriggerAction: () -> Unit,
    onCreateRule: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Notification Details",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                DetailRow("App:", notification.appLabel ?: notification.packageName.orEmpty())
                DetailRow("Title:", notification.title.orEmpty())
                DetailRow("Text:", notification.text.orEmpty())
                DetailRow("Time:", dateFormat.format(Date(notification.timestamp)))
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(
                        onClick = onTriggerAction,
                        enabled = isActionAvailable,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        Text("Open")
                    }
                    Button(
                        onClick = onCreateRule,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp)
                    ) {
                        Text("Create Rule")
                    }
                }
                
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Text("Close")
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DetailRow(label: String, value: String) {
    val context = LocalContext.current
    Row(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(0.25f))
        Text(
            text = value,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {},
                    onLongClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText(label, value)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                )
        )
    }
}
