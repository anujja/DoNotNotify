package com.donotnotify.donotnotify.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.donotnotify.donotnotify.ImportError
import com.donotnotify.donotnotify.ImportResult
import com.donotnotify.donotnotify.R
import com.donotnotify.donotnotify.RuleExport
import com.donotnotify.donotnotify.RuleExportSerializer
import com.donotnotify.donotnotify.RuleImport
import com.donotnotify.donotnotify.RuleStorage
import com.donotnotify.donotnotify.StackChannelsAndroid
import com.donotnotify.donotnotify.ui.components.AboutDialog
import java.io.ByteArrayOutputStream

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

    var showExportImportDialog by remember { mutableStateOf(false) }
    var exportImportMessage by remember { mutableStateOf<String?>(null) }
    var showResetHitsDialog by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            try {
                val localeTag = context.resources.configuration.locales[0].toLanguageTag()
                val export = RuleExport(locale = localeTag, rules = ruleStorage.getRules())
                val json = RuleExportSerializer.toJson(export)
                context.contentResolver.openOutputStream(it)?.use { outputStream ->
                    outputStream.write(json.toByteArray())
                }
                exportImportMessage = context.getString(R.string.rules_exported_successfully)
            } catch (e: Exception) {
                exportImportMessage = context.getString(R.string.failed_to_export_rules, e.message ?: "")
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            try {
                val json = readCappedText(context, it, MAX_IMPORT_BYTES)
                if (json == null) {
                    exportImportMessage = context.getString(R.string.rules_file_too_large)
                    return@let
                }
                when (val result = RuleImport.parse(json)) {
                    is ImportResult.Error -> {
                        exportImportMessage = when (result.reason) {
                            ImportError.TooLarge -> context.getString(R.string.rules_file_too_large)
                            ImportError.Malformed -> context.getString(R.string.invalid_rules_file)
                            ImportError.SchemaMismatch -> context.getString(R.string.invalid_rules_file_schema)
                            ImportError.Empty -> context.getString(R.string.import_no_rules)
                        }
                    }
                    is ImportResult.Success -> {
                        val currentRules = ruleStorage.getRules()
                        // Dedup by rule *signature* (not identity): imported ids are always freshly
                        // minted, so they can never match an existing rule.
                        val newRules = result.rules.filter { imported ->
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
                            ruleStorage.addRules(newRules)
                            // Imported STACK rules need channels now — otherwise they'd have none
                            // until the next app start, and the settings deep-link would point at
                            // a channel that doesn't exist.
                            StackChannelsAndroid.sync(context)
                        }
                        exportImportMessage = if (result.droppedCount > 0) {
                            context.getString(
                                R.string.imported_rules_some_skipped,
                                newRules.size,
                                result.droppedCount
                            )
                        } else {
                            context.getString(R.string.successfully_imported_rules, newRules.size)
                        }
                    }
                }
            } catch (e: Exception) {
                exportImportMessage = context.getString(R.string.failed_to_import_rules, e.message ?: "")
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
            title = { Text(stringResource(R.string.export_import_rules)) },
            text = { Text(stringResource(R.string.choose_action)) },
            confirmButton = {
                TextButton(onClick = {
                    showExportImportDialog = false
                    exportLauncher.launch("donotnotify_rules.json")
                }) {
                    Text(stringResource(R.string.export))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showExportImportDialog = false
                    importLauncher.launch(arrayOf("application/json"))
                }) {
                    Text(stringResource(R.string.import_rules))
                }
            }
        )
    }

    if (showResetHitsDialog) {
        AlertDialog(
            onDismissRequest = { showResetHitsDialog = false },
            title = { Text(stringResource(R.string.reset_hit_counters_title)) },
            text = { Text(stringResource(R.string.reset_hit_counters_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    ruleStorage.resetHitCounts()
                    showResetHitsDialog = false
                    Toast.makeText(
                        context,
                        context.getString(R.string.toast_hit_counters_reset),
                        Toast.LENGTH_SHORT
                    ).show()
                }) {
                    Text(stringResource(R.string.reset))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetHitsDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (exportImportMessage != null) {
        AlertDialog(
            onDismissRequest = { exportImportMessage = null },
            title = { Text(stringResource(R.string.status)) },
            text = { Text(exportImportMessage!!) },
            confirmButton = {
                TextButton(onClick = { exportImportMessage = null }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
        ) {
            SettingsSection(title = stringResource(R.string.settings_section_general)) {
                SettingsRow(
                    icon = Icons.Filled.History,
                    title = stringResource(R.string.history_retention),
                    subtitle = stringResource(R.string.history_retention_desc),
                    trailing = {
                        OutlinedTextField(
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
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            suffix = { Text(stringResource(R.string.days_unit)) },
                            modifier = Modifier.width(112.dp)
                        )
                    }
                )
            }

            SettingsSection(title = stringResource(R.string.settings_section_rules)) {
                SettingsRow(
                    icon = Icons.Filled.ImportExport,
                    title = stringResource(R.string.export_import_rules),
                    subtitle = stringResource(R.string.export_import_rules_desc),
                    onClick = { showExportImportDialog = true },
                    trailing = { NavChevron() }
                )
                RowDivider()
                SettingsRow(
                    icon = Icons.Filled.RestartAlt,
                    title = stringResource(R.string.reset_hit_counters),
                    subtitle = stringResource(R.string.reset_hit_counters_desc),
                    onClick = { showResetHitsDialog = true },
                    trailing = { NavChevron() }
                )
            }

            SettingsSection(title = stringResource(R.string.settings_section_about)) {
                SettingsRow(
                    icon = Icons.Filled.Favorite,
                    title = stringResource(R.string.support_this_app),
                    subtitle = stringResource(R.string.support_this_app_desc),
                    onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://buymeacoffee.com/jainanuj"))
                        )
                    },
                    trailing = { NavChevron() }
                )
                RowDivider()
                SettingsRow(
                    icon = Icons.Filled.BugReport,
                    title = stringResource(R.string.report_an_issue),
                    subtitle = stringResource(R.string.report_an_issue_desc),
                    onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/anujja/DoNotNotify/issues"))
                        )
                    },
                    trailing = { NavChevron() }
                )
                RowDivider()
                SettingsRow(
                    icon = Icons.Filled.Info,
                    title = stringResource(R.string.about),
                    subtitle = stringResource(R.string.about_desc),
                    onClick = { showAboutDialog = true },
                    trailing = { NavChevron() }
                )
            }

            val packageInfo = try {
                context.packageManager.getPackageInfo(context.packageName, 0)
            } catch (e: Exception) {
                null
            }
            val versionName = packageInfo?.versionName ?: stringResource(R.string.unknown)

            Text(
                text = stringResource(R.string.app_version, versionName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 28.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 28.dp, end = 16.dp, top = 20.dp, bottom = 8.dp)
    )
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(12.dp))
            trailing()
        }
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 72.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

@Composable
private fun NavChevron() {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** Cap on the size of an imported rules document (defends against OOM from a corrupt file). */
private const val MAX_IMPORT_BYTES = 5 * 1024 * 1024

/**
 * Reads the document as UTF-8 text, but never buffers more than [maxBytes]. Returns null if the
 * document exceeds the cap (so the caller can report "file too large" instead of risking OOM).
 */
private fun readCappedText(context: Context, uri: Uri, maxBytes: Int): String? {
    context.contentResolver.openInputStream(uri)?.use { input ->
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            total += read
            if (total > maxBytes) return null
            out.write(buffer, 0, read)
        }
        return out.toString(Charsets.UTF_8.name())
    }
    return null
}
