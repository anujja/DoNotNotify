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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
class MainActivity : ComponentActivity() {
    private lateinit var ruleStorage: RuleStorage
    private lateinit var notificationHistoryStorage: NotificationHistoryStorage
    private lateinit var blockedNotificationHistoryStorage: BlockedNotificationHistoryStorage
    private var isServiceEnabled by mutableStateOf(false)
    private var pastNotifications by mutableStateOf<List<SimpleNotification>>(emptyList())
    private var blockedNotifications by mutableStateOf<List<SimpleNotification>>(emptyList())
    private var rules by mutableStateOf<List<BlockerRule>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ruleStorage = RuleStorage(this)
        notificationHistoryStorage = NotificationHistoryStorage(this)
        blockedNotificationHistoryStorage = BlockedNotificationHistoryStorage(this)
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
    }

    @Composable
    private fun MainScreen() {
        val context = LocalContext.current
        var notificationToShowAddDialog by remember { mutableStateOf<SimpleNotification?>(null) }
        var ruleToEdit by remember { mutableStateOf<BlockerRule?>(null) }
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
                onNotificationClick = { notification -> notificationToShowAddDialog = notification },
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
            EnableNotificationListenerScreen()
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
        onNotificationClick: (SimpleNotification) -> Unit,
        onClearHistory: () -> Unit,
        onClearBlockedHistory: () -> Unit,
        onRuleClick: (BlockerRule) -> Unit
    ) {
        val coroutineScope = rememberCoroutineScope()
        val tabTitles = listOf("History", "Rules", "Blocked")
        val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }

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
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                        when (page) {
                            0 -> { // History
                                if (pastNotifications.isEmpty()) {
                                    item {
                                        Text(
                                            text = "Waiting to receive new notifications...",
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                                        )
                                    }
                                } else {
                                    item {
                                        Text(
                                            text = "Here is a history of notifications you have received. Tap on one to create a rule to block similar notifications in the future.",
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                                        )
                                    }
                                    item {
                                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                            Text("App", modifier = Modifier.weight(0.25f), fontWeight = FontWeight.Bold)
                                            Text("Title", modifier = Modifier.weight(0.3f), fontWeight = FontWeight.Bold)
                                            Text("Text", modifier = Modifier.weight(0.45f), fontWeight = FontWeight.Bold)
                                        }
                                        HorizontalDivider()
                                    }
                                    items(pastNotifications) { notification ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().clickable { onNotificationClick(notification) }.padding(vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text((notification.appLabel ?: notification.packageName).orEmpty(), modifier = Modifier.weight(0.25f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(notification.title.orEmpty(), modifier = Modifier.weight(0.3f).padding(horizontal = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(notification.text.orEmpty(), modifier = Modifier.weight(0.45f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        }
                                        HorizontalDivider()
                                    }
                                    item {
                                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.Center) {
                                            TextButton(onClick = onClearHistory) { Text("Clear History") }
                                        }
                                    }
                                }
                            }
                            1 -> { // Rules
                                if (rules.isEmpty()) {
                                    item {
                                        Text(
                                            text = "No rules have been created yet to block notifications. To create a rule, please switch to the \"History\" tab and select a notification that you would like to block.",
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                                        )
                                    }
                                } else {
                                    item {
                                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                            Text("App Name", modifier = Modifier.weight(0.3f), fontWeight = FontWeight.Bold)
                                            Text("Title Regex", modifier = Modifier.weight(0.35f), fontWeight = FontWeight.Bold)
                                            Text("Text Regex", modifier = Modifier.weight(0.35f), fontWeight = FontWeight.Bold)
                                        }
                                        HorizontalDivider()
                                    }
                                    items(rules) { rule ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().clickable { onRuleClick(rule) }.padding(vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(rule.appName.orEmpty(), modifier = Modifier.weight(0.3f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(rule.titleRegex.orEmpty(), modifier = Modifier.weight(0.35f).padding(horizontal = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(rule.textRegex.orEmpty(), modifier = Modifier.weight(0.35f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        HorizontalDivider()
                                    }
                                }
                            }
                            2 -> { // Blocked
                                if (blockedNotifications.isEmpty()) {
                                    item {
                                        Text(
                                            text = "No notifications have been blocked so far.",
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                                        )
                                    }
                                } else {
                                    item {
                                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                            Text("App", modifier = Modifier.weight(0.25f), fontWeight = FontWeight.Bold)
                                            Text("Title", modifier = Modifier.weight(0.3f), fontWeight = FontWeight.Bold)
                                            Text("Time", modifier = Modifier.weight(0.45f), fontWeight = FontWeight.Bold)
                                        }
                                        HorizontalDivider()
                                    }
                                    items(blockedNotifications) { notification ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text((notification.appLabel ?: notification.packageName).orEmpty(), modifier = Modifier.weight(0.25f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(notification.title.orEmpty(), modifier = Modifier.weight(0.3f).padding(horizontal = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(dateFormat.format(Date(notification.timestamp)), modifier = Modifier.weight(0.45f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        }
                                        HorizontalDivider()
                                    }
                                    item {
                                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.Center) {
                                            TextButton(onClick = onClearBlockedHistory) { Text("Clear Blocked History") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun AddRuleDialog(
        notification: SimpleNotification,
        onDismiss: () -> Unit,
        onAddRule: (BlockerRule) -> Unit
    ) {
        var appName by remember { mutableStateOf(notification.packageName.orEmpty()) }
        var titleRegex by remember { mutableStateOf(notification.title.orEmpty()) }
        var textRegex by remember { mutableStateOf(notification.text.orEmpty()) }

        Dialog(onDismissRequest = onDismiss) {
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Add New Rule", fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.padding(bottom = 16.dp))
                    TextField(value = appName, onValueChange = { appName = it }, label = { Text("App Name") }, modifier = Modifier.fillMaxWidth())
                    TextField(value = titleRegex, onValueChange = { titleRegex = it }, label = { Text("Title Regex (Optional)") }, modifier = Modifier.fillMaxWidth())
                    TextField(value = textRegex, onValueChange = { textRegex = it }, label = { Text("Text Regex (Optional)") }, modifier = Modifier.fillMaxWidth())
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.End) {
                        Button(onClick = onDismiss, modifier = Modifier.padding(end = 8.dp)) {
                            Text("Cancel")
                        }
                        Button(onClick = {
                            val newRule = BlockerRule(appName, titleRegex.ifBlank { null }, textRegex.ifBlank { null })
                            onAddRule(newRule)
                        }) {
                            Text("Save Rule")
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun EditRuleDialog(
        rule: BlockerRule,
        onDismiss: () -> Unit,
        onUpdateRule: (BlockerRule, BlockerRule) -> Unit,
        onDeleteRule: (BlockerRule) -> Unit
    ) {
        var appName by remember { mutableStateOf(rule.appName.orEmpty()) }
        var titleRegex by remember { mutableStateOf(rule.titleRegex.orEmpty()) }
        var textRegex by remember { mutableStateOf(rule.textRegex.orEmpty()) }

        Dialog(onDismissRequest = onDismiss) {
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Edit Rule", fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.padding(bottom = 16.dp))
                    TextField(value = appName, onValueChange = { appName = it }, label = { Text("App Name") }, modifier = Modifier.fillMaxWidth())
                    TextField(value = titleRegex, onValueChange = { titleRegex = it }, label = { Text("Title Regex (Optional)") }, modifier = Modifier.fillMaxWidth())
                    TextField(value = textRegex, onValueChange = { textRegex = it }, label = { Text("Text Regex (Optional)") }, modifier = Modifier.fillMaxWidth())
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Button(onClick = { onDeleteRule(rule) }) {
                            Text("Delete")
                        }
                        Row {
                            Button(onClick = onDismiss) {
                                Text("Cancel")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(onClick = {
                                val newRule = BlockerRule(appName, titleRegex.ifBlank { null }, textRegex.ifBlank { null })
                                onUpdateRule(rule, newRule)
                            }) {
                                Text("Save")
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun EnableNotificationListenerScreen() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsOff,
                        contentDescription = "Notifications Off",
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Permission Required",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "This app needs access to your notifications to block them. Please enable the notification listener service in your device settings.",
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { openNotificationListenerSettings() }) {
                        Text("Enable Notification Listener")
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
