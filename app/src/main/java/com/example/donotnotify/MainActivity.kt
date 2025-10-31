package com.example.donotnotify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.donotnotify.ui.components.AddRuleDialog
import com.example.donotnotify.ui.components.EditRuleDialog
import com.example.donotnotify.ui.components.NotificationDetailsDialog
import com.example.donotnotify.ui.screens.BlockedScreen
import com.example.donotnotify.ui.screens.EnableNotificationListenerScreen
import com.example.donotnotify.ui.screens.HistoryScreen
import com.example.donotnotify.ui.screens.RulesScreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
class MainActivity : ComponentActivity() {
    private lateinit var ruleStorage: RuleStorage
    private lateinit var notificationHistoryStorage: NotificationHistoryStorage
    private lateinit var blockedNotificationHistoryStorage: BlockedNotificationHistoryStorage
    private lateinit var statsStorage: StatsStorage
    private var isServiceEnabled by mutableStateOf(false)
    private var pastNotifications by mutableStateOf<List<SimpleNotification>>(emptyList())
    private var blockedNotifications by mutableStateOf<List<SimpleNotification>>(emptyList())
    private var rules by mutableStateOf<List<BlockerRule>>(emptyList())
    private var blockedNotificationsCount by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ruleStorage = RuleStorage(this)
        notificationHistoryStorage = NotificationHistoryStorage(this)
        blockedNotificationHistoryStorage = BlockedNotificationHistoryStorage(this)
        statsStorage = StatsStorage(this)
        isServiceEnabled = isNotificationServiceEnabled()
        setContent {
            MainScreen()
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
                onRuleClick = { rule -> ruleToEdit = rule }
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
        onRuleClick: (BlockerRule) -> Unit
    ) {
        val coroutineScope = rememberCoroutineScope()
        val tabTitles = listOf("History", "Rules", "Blocked")

        Scaffold { innerPadding ->
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
                HorizontalPager(state = pagerState) { page ->
                    when (page) {
                        0 -> HistoryScreen(pastNotifications, onNotificationClick, onClearHistory)
                        1 -> RulesScreen(rules, onRuleClick)
                        2 -> BlockedScreen(blockedNotifications, blockedNotificationsCount, onClearBlockedHistory, onBlockedNotificationClick)
                    }
                }
            }
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
