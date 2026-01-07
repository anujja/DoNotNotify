package com.donotnotify.donotnotify.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.donotnotify.donotnotify.BlockerRule
import com.donotnotify.donotnotify.RuleStorage
import com.donotnotify.donotnotify.ui.components.AboutDialog
import com.google.gson.ExclusionStrategy
import com.google.gson.FieldAttributes
import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import java.io.BufferedReader
import java.io.InputStreamReader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val sharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    var historyDays by remember {
        mutableStateOf(sharedPreferences.getInt("historyDays", 5).toString())
    }
    var showAboutDialog by remember { mutableStateOf(false) }

    val ruleStorage = remember { RuleStorage(context) }
    val gson = remember {
        GsonBuilder()
            .setPrettyPrinting()
            .setExclusionStrategies(object : ExclusionStrategy {
            override fun shouldSkipField(f: FieldAttributes): Boolean {
                return f.name == "hitCount"
            }

            override fun shouldSkipClass(clazz: Class<*>?): Boolean {
                return false
            }
        }).create()
    }

    var showExportImportDialog by remember { mutableStateOf(false) }
    var exportImportMessage by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            try {
                val rules = ruleStorage.getRules()
                val json = gson.toJson(rules)
                context.contentResolver.openOutputStream(it)?.use { outputStream ->
                    outputStream.write(json.toByteArray())
                }
                exportImportMessage = "Rules exported successfully."
            } catch (e: Exception) {
                exportImportMessage = "Failed to export rules: ${e.message}"
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { inputStream ->
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    val json = reader.readText()
                    val type = object : TypeToken<List<BlockerRule>>() {}.type
                    try {
                        val importedRules: List<BlockerRule>? = gson.fromJson(json, type)
                        if (importedRules != null) {
                            val currentRules = ruleStorage.getRules().toMutableList()
                            val newRules = importedRules.filter { imported ->
                                currentRules.none { current ->
                                    current.packageName == imported.packageName &&
                                            current.titleFilter == imported.titleFilter &&
                                            current.titleMatchType == imported.titleMatchType &&
                                            current.textFilter == imported.textFilter &&
                                            current.textMatchType == imported.textMatchType &&
                                            current.ruleType == imported.ruleType
                                }
                            }

                            if (newRules.isNotEmpty()) {
                                currentRules.addAll(newRules)
                                ruleStorage.saveRules(currentRules)
                            }
                            exportImportMessage = "Successfully imported ${newRules.size} rules."
                        } else {
                            exportImportMessage = "Invalid rules file: Could not parse rules."
                        }
                    } catch (e: JsonSyntaxException) {
                        exportImportMessage = "Invalid rules file: Schema mismatch."
                    }
                }
            } catch (e: Exception) {
                exportImportMessage = "Failed to import rules: ${e.message}"
            }
        }
    }

    if (showAboutDialog) {
        AboutDialog {
            showAboutDialog = false
        }
    }

    if (showExportImportDialog) {
        AlertDialog(
            onDismissRequest = { showExportImportDialog = false },
            title = { Text("Export/Import Rules") },
            text = { Text("Choose an action") },
            confirmButton = {
                TextButton(onClick = {
                    showExportImportDialog = false
                    exportLauncher.launch("donotnotify_rules.json")
                }) {
                    Text("Export")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showExportImportDialog = false
                    importLauncher.launch(arrayOf("application/json"))
                }) {
                    Text("Import")
                }
            }
        )
    }

    if (exportImportMessage != null) {
        AlertDialog(
            onDismissRequest = { exportImportMessage = null },
            title = { Text("Status") },
            text = { Text(exportImportMessage!!) },
            confirmButton = {
                TextButton(onClick = { exportImportMessage = null }) {
                    Text("OK")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                    .clickable { showExportImportDialog = true }
                    .padding(16.dp),
            ) {
                Text("Export/Import Rules", style = MaterialTheme.typography.bodyLarge)
            }
            Divider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val intent =
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://donotnotify.com/"))
                        context.startActivity(intent)
                    }
                    .padding(16.dp),
            ) {
                Text("Visit Website", style = MaterialTheme.typography.bodyLarge)
            }
            Divider()
        }
    }
}
