package com.donotnotify.donotnotify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton // Import IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.donotnotify.donotnotify.ui.components.DeleteConfirmationDialog
import com.donotnotify.donotnotify.ui.components.EditRuleDialog
import com.donotnotify.donotnotify.ui.components.NotificationDetailsDialog
import com.donotnotify.donotnotify.ui.screens.BlockedScreen
import com.donotnotify.donotnotify.ui.screens.EnableNotificationListenerScreen
import com.donotnotify.donotnotify.ui.screens.HistoryScreen
import com.donotnotify.donotnotify.ui.screens.RulesScreen
import com.donotnotify.donotnotify.ui.theme.DoNotNotifyTheme
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface // Import Surface
import androidx.compose.material3.TopAppBarDefaults // Import TopAppBarDefaults

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    private lateinit var ruleStorage: RuleStorage
    private lateinit var notificationHistoryStorage: NotificationHistoryStorage
    private lateinit var blockedNotificationHistoryStorage: BlockedNotificationHistoryStorage
    private lateinit var statsStorage: StatsStorage
    private var isServiceEnabled by mutableStateOf(false)
    private var pastNotifications by mutableStateOf<List<SimpleNotification>>(emptyList())
    private var blockedNotifications by mutableStateOf<List<SimpleNotification>>(emptyList())
    private var rules by mutableStateOf<List<BlockerRule>>(emptyList())
    private var blockedNotificationsCount by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ruleStorage = RuleStorage(this)
        notificationHistoryStorage = NotificationHistoryStorage(this)
        blockedNotificationHistoryStorage = BlockedNotificationHistoryStorage(this)
        statsStorage = StatsStorage(this)
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
        blockedNotificationsCount = statsStorage.getBlockedNotificationsCount()
    }

    @Composable
    private fun MainScreen() {
        val context = LocalContext.current
        var notificationToShowAddDialog by remember { mutableStateOf<SimpleNotification?>(null) }
        var notificationToShowDetailsDialog by remember { mutableStateOf<SimpleNotification?>(null) }
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
                        blockedNotificationsCount = statsStorage.getBlockedNotificationsCount()
                    }
                }
            }
            ContextCompat.registerReceiver(context, historyUpdateReceiver, IntentFilter(NotificationBlockerService.ACTION_HISTORY_UPDATED), ContextCompat.RECEIVER_EXPORTED)
            onDispose {
                context.unregisterReceiver(historyUpdateReceiver)
            }
        }

        if (isServiceEnabled) {
            TabbedScreen(
                pagerState = pagerState,
                pastNotifications = pastNotifications,
                blockedNotifications = blockedNotifications,
                rules = rules,
                blockedNotificationsCount = blockedNotificationsCount,
                onNotificationClick = { notification -> notificationToShowAddDialog = notification },
                onBlockedNotificationClick = { notification -> notificationToShowDetailsDialog = notification },
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
                onDeleteHistoryNotificationClick = {notification -> 
                    notificationHistoryStorage.deleteNotification(notification)
                    pastNotifications = notificationHistoryStorage.getHistory()
                    Toast.makeText(context, "Notification deleted", Toast.LENGTH_SHORT).show()
                },
                isServiceEnabled = isServiceEnabled // Pass isServiceEnabled
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
        blockedNotificationsCount: Int,
        onNotificationClick: (SimpleNotification) -> Unit,
        onBlockedNotificationClick: (SimpleNotification) -> Unit,
        onClearHistory: () -> Unit,
        onClearBlockedHistory: () -> Unit,
        onRuleClick: (BlockerRule) -> Unit,
        onDeleteRuleClick: (BlockerRule) -> Unit,
        onDeleteNotificationClick: (SimpleNotification) -> Unit,
        onDeleteHistoryNotificationClick: (SimpleNotification) -> Unit,
        isServiceEnabled: Boolean // Add this parameter
    ) {
        val context = LocalContext.current // Get context inside Composable
        val coroutineScope = rememberCoroutineScope()
        val tabTitles = listOf("History", "Rules", "Blocked")

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = { Text("Do Not Notify") },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    actions = {
                        IconButton(onClick = {
                            val status = if (isServiceEnabled) "Notification Listener Service is enabled" else "Notification Listener Service is disabled"
                            Toast.makeText(context, status, Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Service Active",
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
                    TabRow(selectedTabIndex = pagerState.currentPage) {
                        tabTitles.forEachIndexed { index, title ->
                            Tab(
                                selected = pagerState.currentPage == index,
                                onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } },
                                text = { Text(title) }
                            )
                        }
                    }
                    HorizontalPager(state = pagerState) {
                        PagerScreenContent(
                            page = it,
                            pastNotifications = pastNotifications,
                            blockedNotifications = blockedNotifications,
                            rules = rules,
                            blockedNotificationsCount = blockedNotificationsCount,
                            onNotificationClick = onNotificationClick,
                            onBlockedNotificationClick = onBlockedNotificationClick,
                            onClearHistory = onClearHistory,
                            onClearBlockedHistory = onClearBlockedHistory,
                            onRuleClick = onRuleClick,
                            onDeleteRuleClick = onDeleteRuleClick,
                            onDeleteNotificationClick = onDeleteNotificationClick,
                            onDeleteHistoryNotificationClick = onDeleteHistoryNotificationClick
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
        blockedNotificationsCount: Int,
        onNotificationClick: (SimpleNotification) -> Unit,
        onBlockedNotificationClick: (SimpleNotification) -> Unit,
        onClearHistory: () -> Unit,
        onClearBlockedHistory: () -> Unit,
        onRuleClick: (BlockerRule) -> Unit,
        onDeleteRuleClick: (BlockerRule) -> Unit,
        onDeleteNotificationClick: (SimpleNotification) -> Unit,
        onDeleteHistoryNotificationClick: (SimpleNotification) -> Unit
    ) {
        when (page) {
            0 -> HistoryScreen(pastNotifications, onNotificationClick, onClearHistory, onDeleteHistoryNotificationClick)
            1 -> RulesScreen(rules, onRuleClick, onDeleteRuleClick)
            2 -> BlockedScreen(blockedNotifications, blockedNotificationsCount, onClearBlockedHistory, onBlockedNotificationClick, onDeleteNotificationClick)
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
