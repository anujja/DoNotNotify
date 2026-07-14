package com.donotnotify.donotnotify.ui.components

import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.donotnotify.donotnotify.R

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val packageInfo = try {
        context.packageManager.getPackageInfo(context.packageName, 0)
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    val appName = context.getString(context.applicationInfo.labelRes)
    val appVersion = packageInfo?.versionName ?: stringResource(R.string.not_applicable)
    val developerEmail = "aj@donotnotify.com"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.about)) },
        text = {
            Column {
                Text(stringResource(R.string.about_app, appName))
                Text(stringResource(R.string.about_version, appVersion))
                Text(stringResource(R.string.about_developer, developerEmail))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}
