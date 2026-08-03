package com.donotnotify.donotnotify.ui.screens

import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.donotnotify.donotnotify.AppInfoStorage
import com.donotnotify.donotnotify.BlockerRule
import com.donotnotify.donotnotify.KnownApp
import com.donotnotify.donotnotify.MatchType
import com.donotnotify.donotnotify.PrebuiltRulesRepository
import com.donotnotify.donotnotify.R
import com.donotnotify.donotnotify.RuleType
import com.donotnotify.donotnotify.RuleWizardSupport
import com.donotnotify.donotnotify.SimpleNotification
import com.donotnotify.donotnotify.ui.components.EmptyState
import com.donotnotify.donotnotify.ui.components.KeywordChip
import com.donotnotify.donotnotify.ui.components.RuleTypeBadge
import com.donotnotify.donotnotify.ui.components.accentColor
import com.donotnotify.donotnotify.ui.components.icon
import com.donotnotify.donotnotify.ui.components.label
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.regex.PatternSyntaxException

private enum class RuleWizardStep { APP, TYPE, FILTERS, SUMMARY }

/**
 * Full-screen 4-step flow for creating a rule from scratch: pick an app, pick a
 * rule type, set text filters, review and create. Launched from the Rules tab's
 * "Add a New Rule" button; the caller persists the emitted rule.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleWizardScreen(
    existingRules: List<BlockerRule>,
    pastNotifications: List<SimpleNotification>,
    blockedNotifications: List<SimpleNotification>,
    onClose: () -> Unit,
    onCreateRule: (BlockerRule) -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val appInfoStorage = remember { AppInfoStorage(context) }
    val scope = rememberCoroutineScope()

    var selectedPackage by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedAppName by rememberSaveable { mutableStateOf<String?>(null) }
    var manualEntry by rememberSaveable { mutableStateOf(false) }
    var manualPackage by rememberSaveable { mutableStateOf("") }
    var ruleType by rememberSaveable { mutableStateOf(RuleType.DENYLIST) }
    var titleFilter by rememberSaveable { mutableStateOf("") }
    var titleMatchType by rememberSaveable { mutableStateOf(MatchType.CONTAINS) }
    var textFilter by rememberSaveable { mutableStateOf("") }
    var textMatchType by rememberSaveable { mutableStateOf(MatchType.CONTAINS) }

    val effectivePackage = if (manualEntry) manualPackage.trim() else selectedPackage.orEmpty()
    val effectiveAppName =
        if (manualEntry) manualPackage.trim() else (selectedAppName ?: selectedPackage.orEmpty())

    val knownApps by produceState<List<KnownApp>?>(initialValue = null) {
        value = withContext(Dispatchers.IO) {
            loadKnownApps(context, existingRules, pastNotifications, blockedNotifications)
        }
    }

    val steps = RuleWizardStep.entries
    val pagerState = rememberPagerState(pageCount = { steps.size })

    fun hideIme() {
        ViewCompat.getWindowInsetsController(view)?.hide(WindowInsetsCompat.Type.ime())
    }

    fun goNext() {
        hideIme()
        if (pagerState.currentPage < steps.lastIndex) {
            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
        }
    }

    fun goBack() {
        hideIme()
        if (pagerState.currentPage > 0) {
            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
        }
    }

    // A visible keyboard is hidden first, so edits aren't lost; the next back
    // goes one step back, and back on the first step leaves the wizard.
    BackHandler {
        val imeVisible = ViewCompat.getRootWindowInsets(view)
            ?.isVisible(WindowInsetsCompat.Type.ime()) == true
        when {
            imeVisible -> hideIme()
            pagerState.currentPage > 0 -> goBack()
            else -> onClose()
        }
    }

    val filterCheck = RuleWizardSupport.checkFilters(titleFilter, titleMatchType, textFilter, textMatchType)
    val canAdvance = when (steps[pagerState.currentPage]) {
        RuleWizardStep.APP -> effectivePackage.isNotBlank()
        RuleWizardStep.TYPE -> true
        RuleWizardStep.FILTERS -> filterCheck !is RuleWizardSupport.FilterCheck.InvalidRegex
        RuleWizardStep.SUMMARY -> true
    }

    // Without an explicit Surface the window background shows through behind the
    // pager, which reads as white-on-white / gray-on-white in dark mode.
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(16.dp),
        ) {
            LinearProgressIndicator(
                progress = { (pagerState.currentPage + 1f) / steps.size },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.setup_step_indicator, pagerState.currentPage + 1, steps.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                userScrollEnabled = false,
            ) { page ->
                when (steps[page]) {
                    RuleWizardStep.APP -> AppStep(
                        knownApps = knownApps,
                        appInfoStorage = appInfoStorage,
                        selectedPackage = selectedPackage,
                        manualEntry = manualEntry,
                        manualPackage = manualPackage,
                        onAppSelected = { app ->
                            selectedPackage = app.packageName
                            selectedAppName = app.appName
                            manualEntry = false
                        },
                        onManualEntryChange = { expanded -> manualEntry = expanded },
                        onManualPackageChange = { manualPackage = it },
                    )
                    RuleWizardStep.TYPE -> TypeStep(
                        selected = ruleType,
                        onSelected = { ruleType = it },
                    )
                    RuleWizardStep.FILTERS -> FiltersStep(
                        appName = effectiveAppName,
                        titleFilter = titleFilter,
                        onTitleFilterChange = { titleFilter = it },
                        titleMatchType = titleMatchType,
                        onTitleMatchTypeChange = { titleMatchType = it },
                        textFilter = textFilter,
                        onTextFilterChange = { textFilter = it },
                        textMatchType = textMatchType,
                        onTextMatchTypeChange = { textMatchType = it },
                        filterCheck = filterCheck,
                    )
                    RuleWizardStep.SUMMARY -> SummaryStep(
                        appInfoStorage = appInfoStorage,
                        knownApps = knownApps,
                        packageName = effectivePackage,
                        appName = effectiveAppName,
                        ruleType = ruleType,
                        titleFilter = titleFilter,
                        titleMatchType = titleMatchType,
                        textFilter = textFilter,
                        textMatchType = textMatchType,
                        isDuplicate = RuleWizardSupport.isDuplicate(
                            existingRules,
                            effectivePackage,
                            titleFilter.trim().ifBlank { null },
                            textFilter.trim().ifBlank { null },
                            ruleType
                        ),
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (pagerState.currentPage > 0) {
                    TextButton(onClick = { goBack() }) { Text(stringResource(R.string.setup_back)) }
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = {
                        if (pagerState.currentPage == steps.lastIndex) {
                            onCreateRule(
                                BlockerRule(
                                    appName = effectiveAppName.ifBlank { effectivePackage },
                                    packageName = effectivePackage,
                                    titleFilter = titleFilter.trim().ifBlank { null },
                                    titleMatchType = titleMatchType,
                                    textFilter = textFilter.trim().ifBlank { null },
                                    textMatchType = textMatchType,
                                    ruleType = ruleType,
                                )
                            )
                        } else {
                            goNext()
                        }
                    },
                    enabled = canAdvance,
                ) {
                    Text(
                        if (pagerState.currentPage == steps.lastIndex) stringResource(R.string.rule_wizard_create)
                        else stringResource(R.string.setup_next)
                    )
                }
            }
        }
    }
}

/** Title + explanation header shown at the top of each step. */
@Composable
private fun StepHeader(title: String, body: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = body,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
}

// ---------------------------------------------------------------------------
// Step 1 — pick an app
// ---------------------------------------------------------------------------

@Composable
private fun AppStep(
    knownApps: List<KnownApp>?,
    appInfoStorage: AppInfoStorage,
    selectedPackage: String?,
    manualEntry: Boolean,
    manualPackage: String,
    onAppSelected: (KnownApp) -> Unit,
    onManualEntryChange: (Boolean) -> Unit,
    onManualPackageChange: (String) -> Unit,
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        StepHeader(
            title = stringResource(R.string.rule_wizard_step_app_title),
            body = stringResource(R.string.rule_wizard_step_app_body),
        )

        if (knownApps == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        if (knownApps.isNotEmpty()) {
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
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
            Spacer(Modifier.height(12.dp))
        }

        val filteredApps = remember(knownApps, searchQuery) {
            if (searchQuery.isBlank()) knownApps
            else {
                val query = searchQuery.lowercase()
                knownApps.filter {
                    it.appName?.lowercase()?.contains(query) == true ||
                            it.packageName.lowercase().contains(query)
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (knownApps.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.Outlined.Apps,
                        title = stringResource(R.string.rule_wizard_no_known_apps_title),
                        description = stringResource(R.string.rule_wizard_no_known_apps_desc),
                    )
                }
            }

            items(filteredApps, key = { it.packageName }) { app ->
                AppRow(
                    app = app,
                    appInfoStorage = appInfoStorage,
                    selected = !manualEntry && app.packageName == selectedPackage,
                    onClick = { onAppSelected(app) },
                )
            }

            item(key = "manual") {
                ManualEntryCard(
                    expanded = manualEntry,
                    packageInput = manualPackage,
                    onExpandedChange = onManualEntryChange,
                    onPackageInputChange = onManualPackageChange,
                )
            }
        }
    }
}

@Composable
private fun AppRow(
    app: KnownApp,
    appInfoStorage: AppInfoStorage,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = MaterialTheme.shapes.medium
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape)
                else Modifier
            ),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(
                packageName = app.packageName,
                isQueryableInstalled = app.isQueryableInstalled,
                fallbackLetterSource = app.appName ?: app.packageName,
                appInfoStorage = appInfoStorage,
                size = 40.dp,
                preferLauncher = false,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appName ?: app.packageName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (app.appName != null) {
                    Text(
                        text = app.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (selected) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = stringResource(R.string.rule_wizard_selected),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ManualEntryCard(
    expanded: Boolean,
    packageInput: String,
    onExpandedChange: (Boolean) -> Unit,
    onPackageInputChange: (String) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    // The card sits at the bottom of the list, so its expansion happens below the
    // fold and the tap looks like a no-op. Focus the field (raising the IME) and,
    // once the expand animation and keyboard have settled, scroll it into view.
    LaunchedEffect(expanded) {
        if (expanded) {
            delay(100)
            focusRequester.requestFocus()
            delay(350)
            bringIntoViewRequester.bringIntoView()
        } else {
            keyboard?.hide()
        }
    }

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onExpandedChange(!expanded) }
                .padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = stringResource(R.string.rule_wizard_app_not_listed),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.rule_wizard_enter_package),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = packageInput,
                        onValueChange = onPackageInputChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .bringIntoViewRequester(bringIntoViewRequester),
                        label = { Text(stringResource(R.string.rule_wizard_package_label)) },
                        placeholder = { Text(stringResource(R.string.rule_wizard_package_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                        supportingText = {
                            val warn = packageInput.isNotBlank() &&
                                    !RuleWizardSupport.looksLikePackageName(packageInput)
                            Text(
                                text = if (warn) stringResource(R.string.rule_wizard_package_warning)
                                else stringResource(R.string.rule_wizard_package_help),
                                color = if (warn) MaterialTheme.colorScheme.tertiary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Step 2 — pick a rule type
// ---------------------------------------------------------------------------

@Composable
private fun TypeStep(
    selected: RuleType,
    onSelected: (RuleType) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        StepHeader(
            title = stringResource(R.string.rule_wizard_step_type_title),
            body = stringResource(R.string.rule_wizard_step_type_body),
        )

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            RuleType.entries.forEach { type ->
                RuleTypeCard(
                    type = type,
                    selected = type == selected,
                    onClick = { onSelected(type) },
                )
            }
        }
    }
}

@Composable
private fun RuleTypeCard(
    type: RuleType,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = MaterialTheme.shapes.medium
    val containerColor = when (type) {
        RuleType.DENYLIST -> MaterialTheme.colorScheme.errorContainer
        RuleType.ALLOWLIST -> MaterialTheme.colorScheme.primaryContainer
        RuleType.STACK -> MaterialTheme.colorScheme.secondaryContainer
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape)
                else Modifier
            ),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(color = containerColor, shape = CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = type.icon(),
                    contentDescription = null,
                    tint = type.accentColor(),
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = type.label(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = type.description(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            RadioButton(selected = selected, onClick = onClick)
        }
    }
}

@Composable
private fun RuleType.description(): String = stringResource(
    when (this) {
        RuleType.DENYLIST -> R.string.rule_type_desc_denylist
        RuleType.ALLOWLIST -> R.string.rule_type_desc_allowlist
        RuleType.STACK -> R.string.rule_type_desc_stack
    }
)

// ---------------------------------------------------------------------------
// Step 3 — text filters
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FiltersStep(
    appName: String,
    titleFilter: String,
    onTitleFilterChange: (String) -> Unit,
    titleMatchType: MatchType,
    onTitleMatchTypeChange: (MatchType) -> Unit,
    textFilter: String,
    onTextFilterChange: (String) -> Unit,
    textMatchType: MatchType,
    onTextMatchTypeChange: (MatchType) -> Unit,
    filterCheck: RuleWizardSupport.FilterCheck,
) {
    val invalid = filterCheck as? RuleWizardSupport.FilterCheck.InvalidRegex

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding(),
    ) {
        StepHeader(
            title = stringResource(R.string.rule_wizard_step_filters_title),
            body = stringResource(R.string.rule_wizard_step_filters_body),
        )

        ExampleNotificationCard(appName = appName)
        Spacer(Modifier.height(20.dp))

        FilterField(
            label = stringResource(R.string.title_filter_optional),
            value = titleFilter,
            onValueChange = onTitleFilterChange,
            matchType = titleMatchType,
            onMatchTypeChange = onTitleMatchTypeChange,
            showRegexError = invalid?.titleInvalid == true,
        )

        Spacer(Modifier.height(20.dp))

        FilterField(
            label = stringResource(R.string.text_filter_optional),
            value = textFilter,
            onValueChange = onTextFilterChange,
            matchType = textMatchType,
            onMatchTypeChange = onTextMatchTypeChange,
            showRegexError = invalid?.textInvalid == true,
        )

        Spacer(Modifier.height(16.dp))

        AnimatedVisibility(visible = filterCheck is RuleWizardSupport.FilterCheck.MatchesEverything) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                ),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.rule_wizard_matches_all_warning, appName),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
        }
    }
}

/**
 * A mock notification annotated with Title/Text tags, so the user can see exactly
 * which part of a notification each filter applies to.
 */
@Composable
private fun ExampleNotificationCard(appName: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = appName.trim().take(1).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "$appName · ${stringResource(R.string.rule_wizard_example_now)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.rule_wizard_example_title),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                FieldTag(stringResource(R.string.rule_wizard_summary_title_filter))
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.rule_wizard_example_text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                FieldTag(stringResource(R.string.rule_wizard_summary_text_filter))
            }
        }
    }
}

/** Small label pill marking a region of the example notification. */
@Composable
private fun FieldTag(text: String) {
    Box(
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(6.dp),
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    matchType: MatchType,
    onMatchTypeChange: (MatchType) -> Unit,
    showRegexError: Boolean,
) {
    val regexErrorDetail = remember(value, matchType) {
        if (matchType == MatchType.REGEX && value.isNotBlank()) {
            (runCatching { value.toRegex() }.exceptionOrNull() as? PatternSyntaxException)
                ?.let { it.description ?: it.message }
        } else null
    }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        isError = showRegexError,
        supportingText = if (showRegexError && regexErrorDetail != null) {
            {
                Text(
                    text = stringResource(R.string.rule_wizard_invalid_regex, regexErrorDetail),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        } else null,
    )
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        MatchType.entries.forEachIndexed { index, type ->
            SegmentedButton(
                selected = matchType == type,
                onClick = { onMatchTypeChange(type) },
                modifier = Modifier.weight(1f),
                shape = SegmentedButtonDefaults.itemShape(index = index, count = MatchType.entries.size),
            ) {
                Text(type.label())
            }
        }
    }
    // "Contains" is self-explanatory; only regex warrants a hint.
    if (matchType == MatchType.REGEX) {
        Text(
            text = stringResource(R.string.rule_wizard_regex_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, start = 4.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// Step 4 — summary
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SummaryStep(
    appInfoStorage: AppInfoStorage,
    knownApps: List<KnownApp>?,
    packageName: String,
    appName: String,
    ruleType: RuleType,
    titleFilter: String,
    titleMatchType: MatchType,
    textFilter: String,
    textMatchType: MatchType,
    isDuplicate: Boolean,
) {
    val isQueryableInstalled =
        knownApps?.firstOrNull { it.packageName == packageName }?.isQueryableInstalled == true

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = stringResource(R.string.rule_wizard_step_summary_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(
                        packageName = packageName,
                        isQueryableInstalled = isQueryableInstalled,
                        fallbackLetterSource = appName.ifBlank { packageName },
                        appInfoStorage = appInfoStorage,
                        size = 48.dp,
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            text = appName.ifBlank { packageName },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (appName.isNotBlank() && appName != packageName) {
                            Text(
                                text = packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))

                RuleTypeBadge(ruleType = ruleType)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = ruleType.description(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(16.dp))

                FilterSummaryRow(
                    label = stringResource(R.string.rule_wizard_summary_title_filter),
                    filter = titleFilter.trim(),
                    matchType = titleMatchType,
                    ruleType = ruleType,
                )
                Spacer(Modifier.height(12.dp))
                FilterSummaryRow(
                    label = stringResource(R.string.rule_wizard_summary_text_filter),
                    filter = textFilter.trim(),
                    matchType = textMatchType,
                    ruleType = ruleType,
                )

                if (isDuplicate) {
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = MaterialTheme.shapes.small,
                            )
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.rule_wizard_duplicate_warning),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.rule_wizard_edit_later_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSummaryRow(
    label: String,
    filter: String,
    matchType: MatchType,
    ruleType: RuleType,
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        if (filter.isBlank()) {
            Text(
                text = stringResource(R.string.rule_wizard_summary_any),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                KeywordChip(
                    keyword = stringResource(
                        when (matchType) {
                            MatchType.CONTAINS -> R.string.rule_wizard_summary_contains
                            MatchType.REGEX -> R.string.rule_wizard_summary_regex
                        },
                        filter
                    ),
                    ruleType = ruleType,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Shared bits
// ---------------------------------------------------------------------------

private sealed interface AppIconResult {
    /** Colorful launcher icon from PackageManager. */
    data class Launcher(val bitmap: ImageBitmap) : AppIconResult

    /** Monochrome notification small icon cached by AppInfoStorage; tint before drawing. */
    data class Monochrome(val bitmap: ImageBitmap) : AppIconResult

    data object None : AppIconResult
}

/**
 * Icon for a package. With [preferLauncher] the colorful launcher icon is used
 * when the package is PackageManager-visible; otherwise (and as fallback) the
 * tinted monochrome notification icon cached in [AppInfoStorage], else a letter
 * avatar so no row ever renders icon-less.
 *
 * List rows pass `preferLauncher = false`: only a few packages have launcher
 * icons, and mixing colorful icons with monochrome ones makes the list look
 * inconsistent. The summary shows a single app, so it keeps the best available.
 */
@Composable
private fun AppIcon(
    packageName: String,
    isQueryableInstalled: Boolean,
    fallbackLetterSource: String,
    appInfoStorage: AppInfoStorage,
    size: Dp,
    preferLauncher: Boolean = true,
) {
    val context = LocalContext.current
    val icon by produceState<AppIconResult>(initialValue = AppIconResult.None, key1 = packageName) {
        value = withContext(Dispatchers.IO) {
            val launcher = if (preferLauncher && isQueryableInstalled) {
                try {
                    context.packageManager.getApplicationIcon(packageName)
                        .toBitmap(96, 96).asImageBitmap()
                } catch (e: Exception) {
                    null
                }
            } else null
            when {
                launcher != null -> AppIconResult.Launcher(launcher)
                else -> appInfoStorage.getAppIcon(packageName)
                    ?.let { AppIconResult.Monochrome(it.asImageBitmap()) }
                    ?: AppIconResult.None
            }
        }
    }

    when (val result = icon) {
        is AppIconResult.Launcher -> Image(
            bitmap = result.bitmap,
            contentDescription = null,
            modifier = Modifier.size(size),
        )
        // Notification small icons are stark full-bleed silhouettes; seating them
        // inset in a circle makes them read as avatars next to launcher icons.
        is AppIconResult.Monochrome -> Box(
            modifier = Modifier
                .size(size)
                .background(color = MaterialTheme.colorScheme.surface, shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = result.bitmap,
                contentDescription = null,
                modifier = Modifier.size(size * 0.55f),
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant),
            )
        }
        // Same circle treatment as the monochrome branch so letter avatars and
        // glyph avatars read as one uniform set.
        AppIconResult.None -> Box(
            modifier = Modifier
                .size(size)
                .background(color = MaterialTheme.colorScheme.surface, shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = fallbackLetterSource.trim().take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Assembles the picker's "apps we know about" inputs and delegates to the pure
 * [RuleWizardSupport.mergeKnownApps]. Runs on Dispatchers.IO.
 *
 * PackageManager visibility is probed per candidate package (rather than only the
 * manifest `<queries>` set) so system apps that are visible by default also get
 * their real label and launcher icon.
 */
private fun loadKnownApps(
    context: Context,
    existingRules: List<BlockerRule>,
    pastNotifications: List<SimpleNotification>,
    blockedNotifications: List<SimpleNotification>,
): List<KnownApp> {
    val pm = context.packageManager

    val appInfoRows = AppInfoStorage(context).getAllApps()
    val historyRows = (pastNotifications + blockedNotifications)
        .mapNotNull { n -> n.packageName?.let { it to n.appLabel } }
    val ruleRows = existingRules.mapNotNull { r -> r.packageName?.let { it to r.appName } }
    val prebuiltNames = PrebuiltRulesRepository(context).getPrebuiltRules()
        .mapNotNull { r -> r.packageName?.let { it to r.appName } }
        .toMap()

    val installedPackages = try {
        pm.getInstalledPackages(PackageManager.MATCH_ALL).map { it.packageName }.toSet()
    } catch (e: Exception) {
        emptySet()
    }

    val candidates = buildSet {
        appInfoRows.forEach { add(it.first) }
        historyRows.forEach { add(it.first) }
        ruleRows.forEach { add(it.first) }
        addAll(prebuiltNames.keys.intersect(installedPackages))
    }

    val queryableInstalled = candidates.mapNotNull { pkg ->
        try {
            pkg to pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) {
            null
        }
    }.toMap()

    return RuleWizardSupport.mergeKnownApps(
        appInfoRows = appInfoRows,
        historyRows = historyRows,
        queryableInstalled = queryableInstalled,
        prebuiltNames = prebuiltNames,
        ruleRows = ruleRows,
    )
}
