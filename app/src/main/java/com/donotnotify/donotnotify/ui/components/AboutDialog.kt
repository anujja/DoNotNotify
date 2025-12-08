package com.donotnotify.donotnotify.ui.components

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val packageInfo = try {
        context.packageManager.getPackageInfo(context.packageName, 0)
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    val appName = context.getString(context.applicationInfo.labelRes)
    val appVersion = packageInfo?.versionName ?: "N/A"
    val developerEmail = "aj@donotnotify.com"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("About") },
        text = {
            Column {
                Text("App: $appName")
                Text("Version: $appVersion")
                Text("Developer: $developerEmail")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
