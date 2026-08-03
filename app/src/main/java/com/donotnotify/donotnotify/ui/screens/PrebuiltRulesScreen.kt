package com.donotnotify.donotnotify.ui.screens

import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.donotnotify.donotnotify.BlockerRule
import com.donotnotify.donotnotify.PrebuiltRulesRepository
import com.donotnotify.donotnotify.R
import com.donotnotify.donotnotify.RuleType
import com.donotnotify.donotnotify.ui.components.KeywordChip
import com.donotnotify.donotnotify.ui.components.RuleTypeBadge
import com.donotnotify.donotnotify.ui.components.icon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val repository = PrebuiltRulesRepository(context)
        prebuiltRules = repository.getPrebuiltRules()
    }

    val installedAppPackages = remember {
        packageManager.getInstalledPackages(PackageManager.MATCH_ALL)
            .map { it.packageName }
            .toSet()
    }

    val filteredRules = remember(prebuiltRules, userRules, searchQuery) {
        prebuiltRules
            .filter { rule ->
                rule.packageName in installedAppPackages &&
                        userRules.none {
                            it.packageName == rule.packageName &&
                                    it.titleFilter == rule.titleFilter &&
                                    it.textFilter == rule.textFilter
                        }
            }
            .filter { rule ->
                if (searchQuery.isBlank()) true
                else {
                    val query = searchQuery.lowercase()
                    (rule.appName?.lowercase()?.contains(query) == true) ||
                            (rule.packageName?.lowercase()?.contains(query) == true)
                }
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.prebuilt_rules)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            contentPadding = WindowInsets.navigationBars.asPaddingValues()
        ) {
            item(key = "search") {
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    placeholder = { Text(stringResource(R.string.search_apps)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = stringResource(R.string.search)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = stringResource(R.string.clear_search)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
                    )
                )
            }

            item(key = "description") {
                Text(
                    text = stringResource(R.string.prebuilt_rules_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            if (filteredRules.isEmpty()) {
                item(key = "empty") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (searchQuery.isNotEmpty()) {
                                stringResource(R.string.no_rules_match_search)
                            } else {
                                stringResource(R.string.no_new_prebuilt_rules)
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) {
                                stringResource(R.string.try_different_search)
                            } else {
                                stringResource(R.string.all_rules_added)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                itemsIndexed(
                    filteredRules,
                    key = { index, it -> "prebuilt_${index}_${it.packageName}_${it.titleFilter}_${it.textFilter}" }
                ) { _, rule ->
                    PrebuiltRuleCard(
                        rule = rule,
                        packageManager = packageManager,
                        onAddRule = onAddRule
                    )
                }
            }

            item(key = "bottomSpacer") {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PrebuiltRuleCard(
    rule: BlockerRule,
    packageManager: PackageManager,
    onAddRule: (BlockerRule) -> Unit
) {
    val appIcon by produceState<Drawable?>(initialValue = null, key1 = rule.packageName) {
        value = withContext(Dispatchers.IO) {
            try {
                rule.packageName?.let { packageManager.getApplicationIcon(it) }
            } catch (e: Exception) {
                null
            }
        }
    }

    val keywords = remember(rule) {
        extractAllKeywords(rule)
    }

    val actionText = when (rule.ruleType) {
        RuleType.DENYLIST -> stringResource(R.string.blocks_notifications_containing)
        RuleType.ALLOWLIST -> stringResource(R.string.allows_only_notifications_containing)
        RuleType.STACK -> stringResource(R.string.stacks_notifications_containing)
    }

    val unknownApp = stringResource(R.string.unknown_app)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // App Icon
                if (appIcon != null) {
                    Image(
                        bitmap = appIcon!!.toBitmap(48, 48).asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(48.dp)
                    )
                } else {
                    Icon(
                        imageVector = rule.ruleType.icon(),
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // App name and rule type
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = rule.appName ?: rule.packageName ?: unknownApp,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    RuleTypeBadge(ruleType = rule.ruleType)
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Add Button
                FilledTonalButton(
                    onClick = { onAddRule(rule) }
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.add))
                }
            }

            // Keywords section
            if (keywords.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = actionText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    keywords.forEach { keyword ->
                        KeywordChip(keyword = keyword, ruleType = rule.ruleType)
                    }
                }
            }
        }
    }
}

private fun extractAllKeywords(rule: BlockerRule): List<String> {
    val keywords = mutableListOf<String>()

    rule.textFilter?.let { filter ->
        keywords.addAll(extractKeywordsFromPattern(filter))
    }

    rule.titleFilter?.let { filter ->
        keywords.addAll(extractKeywordsFromPattern(filter))
    }

    return keywords.distinct()
}

private fun extractKeywordsFromPattern(pattern: String): List<String> {
    // Extract readable keywords from regex patterns like "(?i).*(offer|discount|sale).*"
    val keywordRegex = Regex("""\(([^)]+)\)""")
    val matches = keywordRegex.findAll(pattern)

    val keywords = mutableListOf<String>()
    for (match in matches) {
        val group = match.groupValues[1]
        // Split by | to get individual keywords
        val parts = group.split("|")
            .map { it.trim() }
            // Keep non-blank tokens that aren't inline regex flags. No min length: CJK
            // keywords are often 1–2 characters (e.g. お得, 割引) and must still render.
            .filter { it.isNotBlank() && !it.startsWith("?") }
        keywords.addAll(parts)
    }

    // If no keywords found from regex groups, check if it's a simple contains pattern
    if (keywords.isEmpty() && !pattern.contains("(") && pattern.isNotBlank()) {
        // Clean up regex artifacts and return the pattern itself
        val cleaned = pattern
            .replace("(?i)", "")
            .replace(".*", "")
            .replace("\\s+", " ")
            .trim()
        if (cleaned.isNotEmpty()) {
            keywords.add(cleaned)
        }
    }

    return keywords
}
