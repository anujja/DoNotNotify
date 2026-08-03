package com.donotnotify.donotnotify

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton // Import IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface // Import Surface
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.donotnotify.donotnotify.ui.components.AddRuleDialog
import com.donotnotify.donotnotify.ui.components.AutoAddedRulesDialog
import com.donotnotify.donotnotify.ui.components.DeleteConfirmationDialog
import com.donotnotify.donotnotify.ui.components.EditRuleDialog
import com.donotnotify.donotnotify.ui.components.HistoryNotificationDetailsDialog
import com.donotnotify.donotnotify.health.HealthCheckWorker
import com.donotnotify.donotnotify.ui.components.NotificationDetailsDialog
import com.donotnotify.donotnotify.setup.SetupState
import com.donotnotify.donotnotify.ui.screens.BlockedScreen
import com.donotnotify.donotnotify.ui.screens.HistoryScreen
import com.donotnotify.donotnotify.ui.screens.PrebuiltRulesScreen
import com.donotnotify.donotnotify.ui.screens.RulesScreen
import com.donotnotify.donotnotify.ui.screens.RuleWizardScreen
import com.donotnotify.donotnotify.ui.screens.SettingsScreen
import com.donotnotify.donotnotify.ui.screens.SetupWizardScreen
import com.donotnotify.donotnotify.ui.theme.DoNotNotifyTheme
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    private lateinit var ruleStorage: RuleStorage
    private lateinit var notificationHistoryStorage: NotificationHistoryStorage
    private lateinit var blockedNotificationHistoryStorage: BlockedNotificationHistoryStorage
    private lateinit var unmonitoredAppsStorage: UnmonitoredAppsStorage
    private lateinit var prebuiltRulesRepository: PrebuiltRulesRepository
    private lateinit var appInfoStorage: AppInfoStorage
    private var isServiceEnabled by mutableStateOf(false)
    private var pastNotifications by mutableStateOf<List<SimpleNotification>>(emptyList())
    private var blockedNotifications by mutableStateOf<List<SimpleNotification>>(emptyList())
    private var rules by mutableStateOf<List<BlockerRule>>(emptyList())
    private var unmonitoredApps by mutableStateOf<Set<String>>(emptySet())
    private var showSettingsScreen by mutableStateOf(false)
    private var showPrebuiltRulesScreen by mutableStateOf(false)
    private var showRuleWizard by mutableStateOf(false)
    private var autoAddedApps by mutableStateOf<List<String>>(emptyList())
    private var showAutoAddedDialog by mutableStateOf(false)
    private var showSetupWizard by mutableStateOf(false)
    private var wizardShowsWelcome by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ruleStorage = RuleStorage(this)
        notificationHistoryStorage = NotificationHistoryStorage(this)
        blockedNotificationHistoryStorage = BlockedNotificationHistoryStorage(this)
        unmonitoredAppsStorage = UnmonitoredAppsStorage(this)
        prebuiltRulesRepository = PrebuiltRulesRepository(this)
        appInfoStorage = AppInfoStorage(this)
        
        val newApps = checkForNewRules()
        if (newApps.isNotEmpty()) {
            autoAddedApps = newApps
            val sharedPreferences = getSharedPreferences("settings", Context.MODE_PRIVATE)
            if (sharedPreferences.getBoolean("show_auto_add_dialog", true)) {
                showAutoAddedDialog = true
            }
        }

        isServiceEnabled = isNotificationServiceEnabled()
        wizardShowsWelcome = !isServiceEnabled && SetupState.lastSeenSetupVersion(this) == 0
        showSetupWizard = SetupState.shouldShowSetupWizard(this) ||
            intent?.getBooleanExtra(HealthCheckWorker.EXTRA_OPEN_WIZARD, false) == true
        setContent {
            DoNotNotifyTheme {
                val systemUiController = rememberSystemUiController()
                val useDarkIcons = MaterialTheme.colorScheme.background.luminance() > 0.5f
                SideEffect {
                    systemUiController.setSystemBarsColor(
                        color = Color.Transparent,
                        darkIcons = useDarkIcons
                    )
                }
                MainScreen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isServiceEnabled = isNotificationServiceEnabled()
        if (!isServiceEnabled) {
            showSetupWizard = true
        }
        pastNotifications = notificationHistoryStorage.getHistory()
        blockedNotifications = blockedNotificationHistoryStorage.getHistory()
        rules = ruleStorage.getRules()
        unmonitoredApps = unmonitoredAppsStorage.getUnmonitoredApps()
    }

    private fun checkForNewRules(): List<String> {
        val sharedPreferences = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val processedPackages = sharedPreferences.getStringSet("processed_packages", emptySet()) ?: emptySet()
        val installedPackages = packageManager.getInstalledPackages(0).map { it.packageName }.toSet()
        val prebuiltRules = prebuiltRulesRepository.getPrebuiltRules()
        val existingRules = ruleStorage.getRules()

        val newRules = prebuiltRules.filter { rule ->
            val packageName = rule.packageName ?: return@filter false
            packageName in installedPackages &&
                    packageName !in processedPackages &&
                    existingRules.none { it.packageName == packageName }
        }

        if (newRules.isNotEmpty()) {
            ruleStorage.addRules(newRules)

            val newProcessedPackages = processedPackages + newRules.mapNotNull { it.packageName }
            with(sharedPreferences.edit()) {
                putStringSet("processed_packages", newProcessedPackages)
                apply()
            }
            // Record the locale these rules were installed under, so a later language change
            // can be detected and stale fail-closed allowlists reconciled.
            PrebuiltRuleReconciler.stampCurrentLocale(this)
            return newRules.mapNotNull { it.appName }
        }
        return emptyList()
    }

    @Composable
    private fun MainScreen() {
        val context = LocalContext.current
        var notificationToShowAddDialog by remember { mutableStateOf<SimpleNotification?>(null) }
        var notificationToShowDetailsDialog by remember { mutableStateOf<SimpleNotification?>(null) }
        var notificationToShowHistoryDetailsDialog by remember { mutableStateOf<SimpleNotification?>(null) }
        var ruleToEdit by remember { mutableStateOf<BlockerRule?>(null) }
        var notificationToDelete by remember { mutableStateOf<SimpleNotification?>(null) }
        val pagerState = rememberPagerState(pageCount = { 3 })
        val coroutineScope = rememberCoroutineScope()

        DisposableEffect(Unit) {
            val historyUpdateReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == NotificationBlockerService.ACTION_HISTORY_UPDATED) {
                        pastNotifications = notificationHistoryStorage.getHistory()
                        blockedNotifications = blockedNotificationHistoryStorage.getHistory()
                    }
                }
            }
            ContextCompat.registerReceiver(
                context,
                historyUpdateReceiver,
                IntentFilter(NotificationBlockerService.ACTION_HISTORY_UPDATED),
                ContextCompat.RECEIVER_EXPORTED
            )
            onDispose {
                context.unregisterReceiver(historyUpdateReceiver)
            }
        }

        if (showAutoAddedDialog && pagerState.currentPage == 1) {
            AutoAddedRulesDialog(
                addedApps = autoAddedApps,
                onDismiss = { showAutoAddedDialog = false },
                onDoNotShowAgain = {
                    showAutoAddedDialog = false
                    val sharedPreferences = getSharedPreferences("settings", Context.MODE_PRIVATE)
                    with(sharedPreferences.edit()) {
                        putBoolean("show_auto_add_dialog", false)
                        apply()
                    }
                }
            )
        }

        if (showSetupWizard) {
            SetupWizardScreen(
                showWelcome = wizardShowsWelcome,
                onFinish = {
                    showSetupWizard = false
                    isServiceEnabled = isNotificationServiceEnabled()
                },
            )
        } else if (showSettingsScreen) {
            BackHandler {
                showSettingsScreen = false
                rules = ruleStorage.getRules()
            }
            SettingsScreen(
                onClose = {
                    showSettingsScreen = false
                    rules = ruleStorage.getRules()
                }
            )
        } else if (showPrebuiltRulesScreen) {
            BackHandler { showPrebuiltRulesScreen = false }
            PrebuiltRulesScreen(
                userRules = rules,
                onClose = { showPrebuiltRulesScreen = false },
                onAddRule = { rule ->
                    rules = ruleStorage.addRules(listOf(rule))
                    StackChannelsAndroid.sync(context)
                    Toast.makeText(context, context.getString(R.string.toast_rule_added), Toast.LENGTH_SHORT).show()
                }
            )
        } else if (showRuleWizard) {
            // No BackHandler here: the wizard owns back (keyboard first, then
            // step-back, then close).
            RuleWizardScreen(
                existingRules = rules,
                pastNotifications = pastNotifications,
                blockedNotifications = blockedNotifications,
                onClose = { showRuleWizard = false },
                onCreateRule = { rule ->
                    rules = ruleStorage.addRules(listOf(rule))
                    StackChannelsAndroid.sync(context)
                    showRuleWizard = false
                    Toast.makeText(context, context.getString(R.string.toast_rule_added), Toast.LENGTH_SHORT).show()
                    coroutineScope.launch { pagerState.animateScrollToPage(1) }
                }
            )
        } else {
            TabbedScreen(
                pagerState = pagerState,
                pastNotifications = pastNotifications,
                blockedNotifications = blockedNotifications,
                rules = rules,
                unmonitoredApps = unmonitoredApps,
                onNotificationClick = { notification -> notificationToShowHistoryDetailsDialog = notification },
                onBlockedNotificationClick = { notification ->
                    notificationToShowDetailsDialog = notification
                },
                onClearHistory = {
                    notificationHistoryStorage.clearHistory()
                    appInfoStorage.clearAllAppInfo()
                    pastNotifications = emptyList()
                    Toast.makeText(context, context.getString(R.string.toast_history_cleared), Toast.LENGTH_SHORT).show()
                },
                onClearBlockedHistory = {
                    blockedNotificationHistoryStorage.clearHistory()
                    blockedNotifications = emptyList()
                    Toast.makeText(context, context.getString(R.string.toast_blocked_history_cleared), Toast.LENGTH_SHORT).show()
                },
                onRuleClick = { rule -> ruleToEdit = rule },
                onCreateRuleClick = { showRuleWizard = true },
                onDeleteNotificationClick = { notification -> notificationToDelete = notification },
                onDeleteHistoryNotificationClick = { notification ->
                    notificationHistoryStorage.deleteNotification(notification)
                    pastNotifications = notificationHistoryStorage.getHistory()
                    Toast.makeText(context, context.getString(R.string.toast_notification_deleted), Toast.LENGTH_SHORT).show()
                },
                isServiceEnabled = isServiceEnabled, // Pass isServiceEnabled
                onSettingsClick = { showSettingsScreen = true },
                onBrowsePrebuiltRulesClick = { showPrebuiltRulesScreen = true },
                onToggleAllRules = { enabled ->
                    rules = ruleStorage.setAllEnabled(enabled)
                },
                onStopMonitoring = { packageName, appName ->
                    unmonitoredAppsStorage.addApp(packageName)
                    unmonitoredApps = unmonitoredAppsStorage.getUnmonitoredApps()
                    notificationHistoryStorage.deleteNotificationsFromPackage(packageName)
                    pastNotifications = notificationHistoryStorage.getHistory()
                    appInfoStorage.deleteAppInfo(packageName)
                    Toast.makeText(context, context.getString(R.string.toast_stopped_monitoring, appName), Toast.LENGTH_SHORT).show()
                },
                onResumeMonitoring = { packageName ->
                    unmonitoredAppsStorage.removeApp(packageName)
                    unmonitoredApps = unmonitoredAppsStorage.getUnmonitoredApps()
                    Toast.makeText(context, context.getString(R.string.toast_resumed_monitoring, packageName), Toast.LENGTH_SHORT).show()
                }
            )
        }

        notificationToShowAddDialog?.let { notification ->
            AddRuleDialog(
                notification = notification,
                onDismiss = { notificationToShowAddDialog = null },
                onAddRule = { rule ->
                    rules = ruleStorage.addRules(listOf(rule))
                    StackChannelsAndroid.sync(context)
                    notificationToShowAddDialog = null
                    Toast.makeText(context, context.getString(R.string.toast_rule_added), Toast.LENGTH_SHORT).show()
                    coroutineScope.launch { pagerState.animateScrollToPage(1) }
                }
            )
        }

        notificationToShowDetailsDialog?.let { notification ->
            val actualBlockingRule = remember(notification, rules) {
                val rulesForPackage = rules.filter { it.packageName == notification.packageName && it.isEnabled }
                // STACK rules are intentionally excluded: they do not block, so a
                // stack-only match correctly leaves the blocking-rule resolution null.
                val allowlistRules = rulesForPackage.filter { it.ruleType == RuleType.ALLOWLIST }
                val denylistRules = rulesForPackage.filter { it.ruleType == RuleType.DENYLIST }

                var result: BlockerRule? = null

                // Check if blocked by a denylist rule
                for (rule in denylistRules) {
                    if (RuleMatcher.matches(rule, notification.packageName, notification.title, notification.text)) {
                        result = rule
                        break
                    }
                }

                // If not blocked by a denylist rule, and there are allowlist rules, show the first allowlist rule.
                if (result == null && allowlistRules.isNotEmpty()) {
                    result = allowlistRules.firstOrNull()
                }
                result
            }

            NotificationDetailsDialog(
                notification = notification,
                onDismiss = { notificationToShowDetailsDialog = null },
                onViewRule = actualBlockingRule?.let { rule ->
                    {
                        notificationToShowDetailsDialog = null
                        ruleToEdit = rule
                    }
                }
            )
        }
        
        notificationToShowHistoryDetailsDialog?.let { notification ->
            val isActionAvailable = if (notification.id != null) {
                NotificationActionRepository.getAction(notification.id) != null
            } else {
                false
            }
            HistoryNotificationDetailsDialog(
                notification = notification,
                isActionAvailable = isActionAvailable,
                onDismiss = { notificationToShowHistoryDetailsDialog = null },
                onTriggerAction = {
                    val intent = if (notification.id != null) NotificationActionRepository.getAction(notification.id) else null
                    if (intent != null) {
                        try {
                            val options = android.app.ActivityOptions.makeBasic()
                            if (android.os.Build.VERSION.SDK_INT >= 34) {
                                options.setPendingIntentBackgroundActivityStartMode(android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS)
                            }

                            val actionIntent = Intent()
                            actionIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                            intent.send(context, 0, actionIntent, null, null, null, options.toBundle())
                        } catch (e: Exception) {
                            Toast.makeText(context, context.getString(R.string.toast_failed_to_trigger), Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, context.getString(R.string.toast_action_unavailable), Toast.LENGTH_SHORT).show()
                    }
                    notificationToShowHistoryDetailsDialog = null
                },
                onCreateRule = {
                    notificationToShowHistoryDetailsDialog = null
                    notificationToShowAddDialog = notification
                }
            )
        }

        ruleToEdit?.let { rule ->
            EditRuleDialog(
                rule = rule,
                onDismiss = { ruleToEdit = null },
                onUpdateRule = { oldRule, newRule ->
                    // Id-keyed: storage re-reads current state and forces the committed rule back
                    // onto oldRule.id. The old indexOf() compared hitCount, so a listener writeback
                    // racing this dialog returned -1 and the edit was silently dropped.
                    ruleStorage.updateRuleById(oldRule.id, newRule)?.let { rules = it }
                    // The stack's group key is derived from rule *content*, so an edit that changes
                    // any matched field strands the old stack under the pre-edit key. Tear it down
                    // — but only when the key actually moved, so a cosmetic edit (e.g. renaming the
                    // rule) doesn't needlessly dismiss the user's live stack.
                    val pkg = oldRule.packageName
                    if (oldRule.ruleType == RuleType.STACK && pkg != null) {
                        val oldKey = StackedNotificationManager.groupKeyFor(pkg, oldRule)
                        val newKey = StackedNotificationManager.groupKeyFor(pkg, newRule)
                        if (oldKey != newKey || newRule.ruleType != RuleType.STACK) {
                            StackedNotificationManager.cancelStackForRule(context, pkg, oldRule)
                        }
                    }
                    StackChannelsAndroid.sync(context)
                    ruleToEdit = null
                    Toast.makeText(context, context.getString(R.string.toast_rule_updated), Toast.LENGTH_SHORT).show()
                    coroutineScope.launch { pagerState.animateScrollToPage(1) }
                },
                onDeleteRule = { deleted ->
                    rules = ruleStorage.deleteRuleById(deleted.id)
                    if (deleted.ruleType == RuleType.STACK) {
                        deleted.packageName?.let {
                            StackedNotificationManager.cancelStackForRule(context, it, deleted)
                        }
                    }
                    StackChannelsAndroid.sync(context)
                    ruleToEdit = null
                    Toast.makeText(context, context.getString(R.string.toast_rule_deleted), Toast.LENGTH_SHORT).show()
                    coroutineScope.launch { pagerState.animateScrollToPage(1) }
                }
            )
        }

        notificationToDelete?.let { notification ->
            DeleteConfirmationDialog(
                itemName = context.getString(R.string.blocked_notification_from, notification.appLabel.orEmpty()),
                onDismiss = { notificationToDelete = null },
                onConfirm = {
                    blockedNotificationHistoryStorage.deleteNotification(notification)
                    blockedNotifications = blockedNotificationHistoryStorage.getHistory()
                    notificationToDelete = null
                    Toast.makeText(context, context.getString(R.string.toast_notification_deleted), Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    @Composable
    private fun TabbedScreen(
        pagerState: PagerState,
        pastNotifications: List<SimpleNotification>,
        blockedNotifications: List<SimpleNotification>,
        rules: List<BlockerRule>,
        unmonitoredApps: Set<String>,
        onNotificationClick: (SimpleNotification) -> Unit,
        onBlockedNotificationClick: (SimpleNotification) -> Unit,
        onClearHistory: () -> Unit,
        onClearBlockedHistory: () -> Unit,
        onRuleClick: (BlockerRule) -> Unit,
        onCreateRuleClick: () -> Unit,
        onDeleteNotificationClick: (SimpleNotification) -> Unit,
        onDeleteHistoryNotificationClick: (SimpleNotification) -> Unit,
        isServiceEnabled: Boolean, // Pass isServiceEnabled
        onSettingsClick: () -> Unit,
        onBrowsePrebuiltRulesClick: () -> Unit,
        onToggleAllRules: (Boolean) -> Unit,
        onStopMonitoring: (String, String) -> Unit,
        onResumeMonitoring: (String) -> Unit
    ) {
        val context = LocalContext.current // Get context inside Composable
        val coroutineScope = rememberCoroutineScope()
        val tabTitles = listOf(
            stringResource(R.string.tab_history, pastNotifications.size),
            stringResource(R.string.tab_rules, rules.count { it.isEnabled }),
            stringResource(R.string.tab_blocked, blockedNotifications.size)
        )

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.app_name)) },
                    actions = {
                        IconButton(onClick = {
                            val status = if (isServiceEnabled)
                                context.getString(R.string.toast_service_enabled)
                            else
                                context.getString(R.string.toast_service_disabled)
                            Toast.makeText(context, status, Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = stringResource(R.string.service_active),
                            )
                        }
                        IconButton(onClick = onSettingsClick) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = stringResource(R.string.settings)
                            )
                        }
                    }
                )
            }
        ) { innerPadding ->
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(modifier = Modifier.padding(innerPadding)) {
                    PrimaryTabRow(
                        selectedTabIndex = pagerState.currentPage,
                    ) {
                        tabTitles.forEachIndexed { index, title ->
                            Tab(
                                selected = pagerState.currentPage == index,
                                onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } },
                                text = { Text(title) },
                                selectedContentColor = MaterialTheme.colorScheme.onSurface,
                                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f) // Adjust alpha for unselected tab text
                            )
                        }
                    }
                    HorizontalPager(state = pagerState) {
                        PagerScreenContent(
                            page = it,
                            pastNotifications = pastNotifications,
                            blockedNotifications = blockedNotifications,
                            rules = rules,
                            unmonitoredApps = unmonitoredApps,
                            onNotificationClick = onNotificationClick,
                            onBlockedNotificationClick = onBlockedNotificationClick,
                            onClearHistory = onClearHistory,
                            onClearBlockedHistory = onClearBlockedHistory,
                            onRuleClick = onRuleClick,
                            onCreateRuleClick = onCreateRuleClick,
                            onDeleteNotificationClick = onDeleteNotificationClick,
                            onDeleteHistoryNotificationClick = onDeleteHistoryNotificationClick,
                            onBrowsePrebuiltRulesClick = onBrowsePrebuiltRulesClick,
                            onToggleAllRules = onToggleAllRules,
                            onStopMonitoring = onStopMonitoring,
                            onResumeMonitoring = onResumeMonitoring
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun PagerScreenContent(
        page: Int,
        pastNotifications: List<SimpleNotification>,
        blockedNotifications: List<SimpleNotification>,
        rules: List<BlockerRule>,
        unmonitoredApps: Set<String>,
        onNotificationClick: (SimpleNotification) -> Unit,
        onBlockedNotificationClick: (SimpleNotification) -> Unit,
        onClearHistory: () -> Unit,
        onClearBlockedHistory: () -> Unit,
        onRuleClick: (BlockerRule) -> Unit,
        onCreateRuleClick: () -> Unit,
        onDeleteNotificationClick: (SimpleNotification) -> Unit,
        onDeleteHistoryNotificationClick: (SimpleNotification) -> Unit,
        onBrowsePrebuiltRulesClick: () -> Unit,
        onToggleAllRules: (Boolean) -> Unit,
        onStopMonitoring: (String, String) -> Unit,
        onResumeMonitoring: (String) -> Unit
    ) {
        when (page) {
            0 -> HistoryScreen(
                pastNotifications,
                unmonitoredApps,
                onNotificationClick,
                onClearHistory,
                onDeleteHistoryNotificationClick,
                onStopMonitoring,
                onResumeMonitoring
            )

            1 -> RulesScreen(rules, onRuleClick, onCreateRuleClick, onBrowsePrebuiltRulesClick, onToggleAllRules)
            2 -> BlockedScreen(
                blockedNotifications,
                onClearBlockedHistory,
                onBlockedNotificationClick,
                onDeleteNotificationClick
            )
        }
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val enabledListeners = NotificationManagerCompat.getEnabledListenerPackages(this)
        return enabledListeners.contains(packageName)
    }
}

fun Color.luminance(): Float {
    return (this.red * 0.2126f + this.green * 0.7152f + this.blue * 0.0722f)
}