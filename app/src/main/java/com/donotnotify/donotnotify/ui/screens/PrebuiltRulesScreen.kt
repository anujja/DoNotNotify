package com.donotnotify.donotnotify.ui.screens

import android.content.pm.PackageManager
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.donotnotify.donotnotify.BlockerRule
import com.donotnotify.donotnotify.PrebuiltRulesRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrebuiltRulesScreen(
    userRules: List<BlockerRule>,
    onClose: () -> Unit,
    onAddRule: (BlockerRule) -> Unit
) {
    val context = LocalContext.current
    val packageManager = context.packageManager
    var prebuiltRules by remember { mutableStateOf<List<BlockerRule>>(emptyList()) }

    LaunchedEffect(Unit) {
        val repository = PrebuiltRulesRepository(context)
        prebuiltRules = repository.getPrebuiltRules()
    }

    val installedAppPackages = packageManager.getInstalledPackages(0)
        .map { it.packageName }

    Log.i("PrebuiltRulesScreen", "Installed App Packages: $installedAppPackages")

    val filteredRules = prebuiltRules.filter { rule ->
        rule.packageName in installedAppPackages && userRules.none { it.packageName == rule.packageName && it.titleFilter == rule.titleFilter && it.textFilter == rule.textFilter }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pre-built Rules") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            if (filteredRules.isEmpty()) {
                item {
                    Text("No new pre-built rules available for your installed apps.", modifier = Modifier.padding(16.dp))
                }
            } else {
                items(filteredRules) { rule ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(rule.appName ?: rule.packageName ?: "", fontWeight = FontWeight.Bold)
                                if (!rule.titleFilter.isNullOrEmpty()) {
                                    Text("Title: ${rule.titleFilter}")
                                }
                                if (!rule.textFilter.isNullOrEmpty()) {
                                    Text("Text: ${rule.textFilter}")
                                }
                            }
                            IconButton(onClick = { onAddRule(rule) }) {
                                Icon(Icons.Default.Add, contentDescription = "Add Rule")
                            }
                        }
                    }
                }
            }
        }
    }
}