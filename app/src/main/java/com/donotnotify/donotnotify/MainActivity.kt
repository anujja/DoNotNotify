package com.donotnotify.donotnotify

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.provider.Settings
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.donotnotify.donotnotify.ui.components.AddRuleDialog
import com.donotnotify.donotnotify.ui.components.AutoAddedRulesDialog
import com.donotnotify.donotnotify.ui.components.DeleteConfirmationDialog
import com.donotnotify.donotnotify.ui.components.EditRuleDialog
import com.donotnotify.donotnotify.ui.components.HistoryNotificationDetailsDialog
import com.donotnotify.donotnotify.ui.components.NotificationDetailsDialog
import com.donotnotify.donotnotify.ui.screens.BlockedScreen
import com.donotnotify.donotnotify.ui.screens.EnableNotificationListenerScreen
import com.donotnotify.donotnotify.ui.screens.HistoryScreen
import com.donotnotify.donotnotify.ui.screens.PrebuiltRulesScreen
import com.donotnotify.donotnotify.ui.screens.RulesScreen
import com.donotnotify.donotnotify.ui.screens.SettingsScreen
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
    private var isServiceEnabled by mutableStateOf(false)
    private var pastNotifications by mutableStateOf<List<SimpleNotification>>(emptyList())
    private var blockedNotifications by mutableStateOf<List<SimpleNotification>>(emptyList())
    private var rules by mutableStateOf<List<BlockerRule>>(emptyList())
    private var unmonitoredApps by mutableStateOf<Set<String>>(emptySet())
    private var showSettingsScreen by mutableStateOf(false)
    private var showPrebuiltRulesScreen by mutableStateOf(false)
    private var autoAddedApps by mutableStateOf<List<String>>(emptyList())
    private var showAutoAddedDialog by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ruleStorage = RuleStorage(this)
        notificationHistoryStorage = NotificationHistoryStorage(this)
        blockedNotificationHistoryStorage = BlockedNotificationHistoryStorage(this)
        unmonitoredAppsStorage = UnmonitoredAppsStorage(this)
        prebuiltRulesRepository = PrebuiltRulesRepository(this)
        
        val newApps = checkForNewRules()
        if (newApps.isNotEmpty()) {
            autoAddedApps = newApps
            val sharedPreferences = getSharedPreferences("settings", Context.MODE_PRIVATE)
            if (sharedPreferences.getBoolean("show_auto_add_dialog", true)) {
                showAutoAddedDialog = true
            }
        }

        isServiceEnabled = isNotificationServiceEnabled()
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
            val updatedRules = existingRules + newRules
            ruleStorage.saveRules(updatedRules)
            
            val newProcessedPackages = processedPackages + newRules.mapNotNull { it.packageName }
            with(sharedPreferences.edit()) {
                putStringSet("processed_packages", newProcessedPackages)
                apply()
            }
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
        var ruleToDelete by remember { mutableStateOf<BlockerRule?>(null) }
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

        if (showSettingsScreen) {
            BackHandler { showSettingsScreen = false }
            SettingsScreen(
                onClose = { showSettingsScreen = false }
            )
        } else if (showPrebuiltRulesScreen) {
            BackHandler { showPrebuiltRulesScreen = false }
            PrebuiltRulesScreen(
                userRules = rules,
                onClose = { showPrebuiltRulesScreen = false },
                onAddRule = { rule ->
                    val updatedRules = rules + rule
                    ruleStorage.saveRules(updatedRules)
                    rules = updatedRules
                    Toast.makeText(context, "Rule added", Toast.LENGTH_SHORT).show()
                }
            )
        } else if (isServiceEnabled) {
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
                    pastNotifications = emptyList()
                    Toast.makeText(context, "History cleared", Toast.LENGTH_SHORT).show()
                },
                onClearBlockedHistory = {
                    blockedNotificationHistoryStorage.clearHistory()
                    blockedNotifications = emptyList()
                    Toast.makeText(context, "Blocked history cleared", Toast.LENGTH_SHORT).show()
                },
                onRuleClick = { rule -> ruleToEdit = rule },
                onDeleteRuleClick = { rule -> ruleToDelete = rule },
                onDeleteNotificationClick = { notification -> notificationToDelete = notification },
                onDeleteHistoryNotificationClick = { notification ->
                    notificationHistoryStorage.deleteNotification(notification)
                    pastNotifications = notificationHistoryStorage.getHistory()
                    Toast.makeText(context, "Notification deleted", Toast.LENGTH_SHORT).show()
                },
                isServiceEnabled = isServiceEnabled, // Pass isServiceEnabled
                onSettingsClick = { showSettingsScreen = true },
                onBrowsePrebuiltRulesClick = { showPrebuiltRulesScreen = true },
                onStopMonitoring = { packageName, appName ->
                    unmonitoredAppsStorage.addApp(packageName)
                    unmonitoredApps = unmonitoredAppsStorage.getUnmonitoredApps()
                    notificationHistoryStorage.deleteNotificationsFromPackage(packageName)
                    pastNotifications = notificationHistoryStorage.getHistory()
                    Toast.makeText(context, "Stopped monitoring $appName", Toast.LENGTH_SHORT).show()
                },
                onResumeMonitoring = { packageName ->
                    unmonitoredAppsStorage.removeApp(packageName)
                    unmonitoredApps = unmonitoredAppsStorage.getUnmonitoredApps()
                    Toast.makeText(context, "Resumed monitoring $packageName", Toast.LENGTH_SHORT).show()
                }
            )
        } else {
            EnableNotificationListenerScreen(onEnableClick = { openNotificationListenerSettings() })
        }

        notificationToShowAddDialog?.let { notification ->
            AddRuleDialog(
                notification = notification,
                onDismiss = { notificationToShowAddDialog = null },
                onAddRule = { rule ->
                    val updatedRules = rules + rule
                    ruleStorage.saveRules(updatedRules)
                    rules = updatedRules
                    notificationToShowAddDialog = null
                    Toast.makeText(context, "Rule added", Toast.LENGTH_SHORT).show()
                    coroutineScope.launch { pagerState.animateScrollToPage(1) }
                }
            )
        }

        notificationToShowDetailsDialog?.let { notification ->
            NotificationDetailsDialog(
                notification = notification,
                onDismiss = { notificationToShowDetailsDialog = null }
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
                            Toast.makeText(context, "Failed to trigger action", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "Notification action is no longer available", Toast.LENGTH_SHORT).show()
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
                    val updatedRules = rules.toMutableList()
                    val index = updatedRules.indexOf(oldRule)
                    if (index != -1) {
                        updatedRules[index] = newRule
                    }
                    ruleStorage.saveRules(updatedRules)
                    rules = updatedRules
                    ruleToEdit = null
                    Toast.makeText(context, "Rule updated", Toast.LENGTH_SHORT).show()
                    coroutineScope.launch { pagerState.animateScrollToPage(1) }
                },
                onDeleteRule = {
                    val updatedRules = rules - it
                    ruleStorage.saveRules(updatedRules)
                    rules = updatedRules
                    ruleToEdit = null
                    Toast.makeText(context, "Rule deleted", Toast.LENGTH_SHORT).show()
                    coroutineScope.launch { pagerState.animateScrollToPage(1) }
                }
            )
        }

        ruleToDelete?.let { rule ->
            DeleteConfirmationDialog(
                itemName = "Rule for ${rule.appName}",
                onDismiss = { ruleToDelete = null },
                onConfirm = {
                    val updatedRules = rules - rule
                    ruleStorage.saveRules(updatedRules)
                    rules = updatedRules
                    ruleToDelete = null
                    Toast.makeText(context, "Rule deleted", Toast.LENGTH_SHORT).show()
                }
            )
        }

        notificationToDelete?.let { notification ->
            DeleteConfirmationDialog(
                itemName = "Blocked notification from ${notification.appLabel}",
                onDismiss = { notificationToDelete = null },
                onConfirm = {
                    blockedNotificationHistoryStorage.deleteNotification(notification)
                    blockedNotifications = blockedNotificationHistoryStorage.getHistory()
                    notificationToDelete = null
                    Toast.makeText(context, "Notification deleted", Toast.LENGTH_SHORT).show()
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
        onDeleteRuleClick: (BlockerRule) -> Unit,
        onDeleteNotificationClick: (SimpleNotification) -> Unit,
        onDeleteHistoryNotificationClick: (SimpleNotification) -> Unit,
        isServiceEnabled: Boolean, // Pass isServiceEnabled
        onSettingsClick: () -> Unit,
        onBrowsePrebuiltRulesClick: () -> Unit,
        onStopMonitoring: (String, String) -> Unit,
        onResumeMonitoring: (String) -> Unit
    ) {
        val context = LocalContext.current // Get context inside Composable
        val coroutineScope = rememberCoroutineScope()
        val tabTitles =
            listOf("History (${pastNotifications.size})", "Rules (${rules.count { it.isEnabled }})", "Blocked (${blockedNotifications.size})")

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = { Text("DoNotNotify") },
                    actions = {
                        IconButton(onClick = {
                            val status =
                                if (isServiceEnabled) "Notification service is enabled" else "Notification service is disabled"
                            Toast.makeText(context, status, Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Service Active",
                            )
                        }
                        IconButton(onClick = onSettingsClick) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "Settings"
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
                    TabRow(
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
                            onDeleteRuleClick = onDeleteRuleClick,
                            onDeleteNotificationClick = onDeleteNotificationClick,
                            onDeleteHistoryNotificationClick = onDeleteHistoryNotificationClick,
                            onBrowsePrebuiltRulesClick = onBrowsePrebuiltRulesClick,
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
        onDeleteRuleClick: (BlockerRule) -> Unit,
        onDeleteNotificationClick: (SimpleNotification) -> Unit,
        onDeleteHistoryNotificationClick: (SimpleNotification) -> Unit,
        onBrowsePrebuiltRulesClick: () -> Unit,
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

            1 -> RulesScreen(rules, onRuleClick, onDeleteRuleClick, onBrowsePrebuiltRulesClick)
            2 -> BlockedScreen(
                blockedNotifications,
                onClearBlockedHistory,
                onBlockedNotificationClick,
                onDeleteNotificationClick
            )
        }
    }

    private fun openNotificationListenerSettings() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        startActivity(intent)
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val enabledListeners = NotificationManagerCompat.getEnabledListenerPackages(this)
        return enabledListeners.contains(packageName)
    }
}

fun Color.luminance(): Float {
    return (this.red * 0.2126f + this.green * 0.7152f + this.blue * 0.0722f)
}
