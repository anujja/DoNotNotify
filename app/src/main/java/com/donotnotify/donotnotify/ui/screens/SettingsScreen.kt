package com.donotnotify.donotnotify.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.donotnotify.donotnotify.ui.components.AboutDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onClose: () -> Unit,
    onOpenUnmonitoredApps: () -> Unit
) {
    val context = LocalContext.current
    val sharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    var historyDays by remember {
        mutableStateOf(sharedPreferences.getInt("historyDays", 5).toString())
    }
    var showAboutDialog by remember { mutableStateOf(false) }

    if (showAboutDialog) {
        AboutDialog {
            showAboutDialog = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "History Retention (Days):",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                TextField(
                    value = historyDays,
                    onValueChange = { newText ->
                        historyDays = newText
                        newText.toIntOrNull()?.let { newDays ->
                            with(sharedPreferences.edit()) {
                                putInt("historyDays", newDays)
                                apply()
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(0.5f)
                )
            }
            Divider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenUnmonitoredApps() }
                    .padding(16.dp),
            ) {
                Text("Unmonitored Apps", style = MaterialTheme.typography.bodyLarge)
            }
            Divider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAboutDialog = true }
                    .padding(16.dp),
            ) {
                Text("About", style = MaterialTheme.typography.bodyLarge)
            }
            Divider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val intent =
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://donotnotify.com/help.html"))
                        context.startActivity(intent)
                    }
                    .padding(16.dp),
            ) {
                Text("Help", style = MaterialTheme.typography.bodyLarge)
            }
            Divider()
        }
    }
}
