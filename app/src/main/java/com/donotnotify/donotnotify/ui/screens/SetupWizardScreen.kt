package com.donotnotify.donotnotify.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.donotnotify.donotnotify.R
import com.donotnotify.donotnotify.setup.OemAutostart
import com.donotnotify.donotnotify.setup.SetupState
import kotlinx.coroutines.launch

private enum class WizardStep { WELCOME, LISTENER, POST_NOTIF, BATTERY, OEM, DONE }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SetupWizardScreen(
    showWelcome: Boolean,
    onFinish: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var listenerEnabled by remember { mutableStateOf(SetupState.isNotificationListenerEnabled(context)) }
    var batteryIgnored by remember { mutableStateOf(SetupState.isIgnoringBatteryOptimizations(context)) }
    var oemSeen by remember { mutableStateOf(SetupState.hasSeenOemAutostart(context)) }
    var oemFailed by remember { mutableStateOf(false) }
    var postNotifGranted by remember { mutableStateOf(SetupState.isPostNotificationsGranted(context)) }

    DisposableLifecycleResume(lifecycleOwner) {
        listenerEnabled = SetupState.isNotificationListenerEnabled(context)
        batteryIgnored = SetupState.isIgnoringBatteryOptimizations(context)
        oemSeen = SetupState.hasSeenOemAutostart(context)
        postNotifGranted = SetupState.isPostNotificationsGranted(context)
    }

    val postNotifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> postNotifGranted = granted || SetupState.isPostNotificationsGranted(context) }

    val oemApplies = remember { OemAutostart.applies() }
    // The POST_NOTIFICATIONS step is only relevant on API 33+ when not yet granted;
    // membership is fixed at first composition so the pager stays stable (a later
    // grant just auto-advances the step).
    val needsPostNotif = remember { SetupState.needsPostNotificationsStep(context) }
    val steps = remember(showWelcome, oemApplies, needsPostNotif) {
        buildList {
            if (showWelcome) add(WizardStep.WELCOME)
            add(WizardStep.LISTENER)
            if (needsPostNotif) add(WizardStep.POST_NOTIF)
            add(WizardStep.BATTERY)
            if (oemApplies) add(WizardStep.OEM)
            add(WizardStep.DONE)
        }
    }

    val pagerState = rememberPagerState(pageCount = { steps.size })
    val scope = rememberCoroutineScope()

    // The pager saves its page index across activity recreation, but the step list is
    // rebuilt from scratch and can be shorter than it was (the welcome step drops once
    // the listener is on, POST_NOTIF drops once granted). A restored index is read here
    // before the pager clamps it during layout, so clamp it ourselves.
    val currentIndex = pagerState.currentPage.coerceIn(0, steps.lastIndex)
    LaunchedEffect(steps.size) {
        if (pagerState.currentPage > steps.lastIndex) pagerState.scrollToPage(steps.lastIndex)
    }

    fun goNext() {
        if (pagerState.currentPage < steps.lastIndex) {
            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
        }
    }
    fun goBack() {
        if (pagerState.currentPage > 0) {
            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
        }
    }

    LaunchedEffect(listenerEnabled, batteryIgnored, postNotifGranted) {
        val current = steps.getOrNull(currentIndex) ?: return@LaunchedEffect
        if (current == WizardStep.LISTENER && listenerEnabled) goNext()
        else if (current == WizardStep.POST_NOTIF && postNotifGranted) goNext()
        else if (current == WizardStep.BATTERY && batteryIgnored) goNext()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(16.dp),
    ) {
        LinearProgressIndicator(
            progress = { (currentIndex + 1f) / steps.size },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.setup_step_indicator, currentIndex + 1, steps.size),
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
            when (steps.getOrNull(page)) {
                WizardStep.WELCOME -> WelcomeStep(onNext = { goNext() })
                WizardStep.LISTENER -> ListenerStep(
                    enabled = listenerEnabled,
                    onOpenSettings = {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    },
                )
                WizardStep.POST_NOTIF -> PostNotifStep(
                    granted = postNotifGranted,
                    onRequest = {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            postNotifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                )
                WizardStep.BATTERY -> BatteryStep(
                    granted = batteryIgnored,
                    onRequest = {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        try {
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        }
                    },
                )
                WizardStep.OEM -> OemStep(
                    vendor = OemAutostart.currentVendor(),
                    failed = oemFailed,
                    seen = oemSeen,
                    onOpenSettings = {
                        val launched = OemAutostart.tryLaunchAutostart(context)
                        oemFailed = !launched
                        if (launched) SetupState.markOemAutostartSeen(context)
                        oemSeen = SetupState.hasSeenOemAutostart(context)
                    },
                    onMarkDone = {
                        SetupState.markOemAutostartSeen(context)
                        oemSeen = true
                        goNext()
                    },
                )
                WizardStep.DONE, null -> DoneStep()
            }
        }

        WizardNavRow(
            isFirst = currentIndex == 0,
            isLast = currentIndex == steps.lastIndex,
            canAdvance = canAdvance(steps[currentIndex], listenerEnabled, batteryIgnored, oemSeen),
            onBack = { goBack() },
            onNext = {
                if (currentIndex == steps.lastIndex) {
                    SetupState.setLastSeenSetupVersion(context, SetupState.CURRENT_SETUP_VERSION)
                    onFinish()
                } else {
                    goNext()
                }
            },
            onSkip = {
                if (currentIndex == steps.lastIndex) return@WizardNavRow
                goNext()
            },
            showSkip = canSkip(steps[currentIndex]),
        )
    }
}

private fun canAdvance(
    step: WizardStep,
    listenerEnabled: Boolean,
    batteryIgnored: Boolean,
    oemSeen: Boolean,
): Boolean = when (step) {
    WizardStep.WELCOME -> true
    WizardStep.LISTENER -> listenerEnabled
    WizardStep.POST_NOTIF -> true
    WizardStep.BATTERY -> true
    WizardStep.OEM -> true
    WizardStep.DONE -> true
}

private fun canSkip(step: WizardStep): Boolean = when (step) {
    WizardStep.POST_NOTIF, WizardStep.BATTERY, WizardStep.OEM -> true
    else -> false
}

@Composable
private fun DisposableLifecycleResume(
    owner: androidx.lifecycle.LifecycleOwner,
    onResume: () -> Unit,
) {
    androidx.compose.runtime.DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) onResume()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}

@Composable
private fun StepCard(
    icon: ImageVector,
    title: String,
    body: String,
    statusText: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = body,
                    textAlign = TextAlign.Center,
                )
                if (statusText != null) {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = statusText,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                if (actionLabel != null && onAction != null) {
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = onAction) { Text(actionLabel) }
                }
                if (secondaryLabel != null && onSecondary != null) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onSecondary) { Text(secondaryLabel) }
                }
            }
        }
    }
}

@Composable
private fun WelcomeStep(onNext: () -> Unit) {
    StepCard(
        icon = Icons.Default.Shield,
        title = stringResource(R.string.setup_welcome_title),
        body = stringResource(R.string.setup_welcome_body),
        actionLabel = stringResource(R.string.setup_welcome_cta),
        onAction = onNext,
    )
}

@Composable
private fun ListenerStep(enabled: Boolean, onOpenSettings: () -> Unit) {
    StepCard(
        icon = if (enabled) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
        title = stringResource(R.string.setup_listener_title),
        body = stringResource(R.string.setup_listener_body),
        statusText = if (enabled) stringResource(R.string.setup_listener_granted) else null,
        actionLabel = if (enabled) null else stringResource(R.string.setup_listener_cta),
        onAction = if (enabled) null else onOpenSettings,
    )
}

@Composable
private fun PostNotifStep(granted: Boolean, onRequest: () -> Unit) {
    StepCard(
        icon = if (granted) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
        title = stringResource(R.string.setup_post_notif_title),
        body = stringResource(R.string.setup_post_notif_body),
        statusText = if (granted) stringResource(R.string.setup_post_notif_granted) else null,
        actionLabel = if (granted) null else stringResource(R.string.setup_post_notif_cta),
        onAction = if (granted) null else onRequest,
    )
}

@Composable
private fun BatteryStep(granted: Boolean, onRequest: () -> Unit) {
    StepCard(
        icon = Icons.Default.BatteryFull,
        title = stringResource(R.string.setup_battery_title),
        body = stringResource(R.string.setup_battery_body),
        statusText = if (granted) stringResource(R.string.setup_battery_granted) else null,
        actionLabel = if (granted) null else stringResource(R.string.setup_battery_cta),
        onAction = if (granted) null else onRequest,
    )
}

@Composable
private fun OemStep(
    vendor: OemAutostart.Vendor?,
    failed: Boolean,
    seen: Boolean,
    onOpenSettings: () -> Unit,
    onMarkDone: () -> Unit,
) {
    val bodyRes = when (vendor) {
        OemAutostart.Vendor.XIAOMI -> R.string.setup_oem_body_xiaomi
        OemAutostart.Vendor.HUAWEI -> R.string.setup_oem_body_huawei
        OemAutostart.Vendor.OPPO -> R.string.setup_oem_body_oppo
        OemAutostart.Vendor.ONEPLUS -> R.string.setup_oem_body_oneplus
        OemAutostart.Vendor.VIVO -> R.string.setup_oem_body_vivo
        OemAutostart.Vendor.SAMSUNG -> R.string.setup_oem_body_samsung
        OemAutostart.Vendor.ASUS -> R.string.setup_oem_body_asus
        OemAutostart.Vendor.LETV -> R.string.setup_oem_body_letv
        OemAutostart.Vendor.MEIZU -> R.string.setup_oem_body_meizu
        OemAutostart.Vendor.NOKIA -> R.string.setup_oem_body_nokia
        null -> R.string.setup_oem_body_generic
    }
    val baseBody = stringResource(bodyRes)
    val body = if (failed) baseBody + "\n\n" + stringResource(R.string.setup_oem_failed) else baseBody
    StepCard(
        icon = Icons.Default.PhoneAndroid,
        title = stringResource(R.string.setup_oem_title),
        body = body,
        statusText = if (seen) stringResource(R.string.setup_done_check) else null,
        actionLabel = stringResource(R.string.setup_oem_cta),
        onAction = onOpenSettings,
        secondaryLabel = stringResource(R.string.setup_oem_done),
        onSecondary = onMarkDone,
    )
}

@Composable
private fun DoneStep() {
    StepCard(
        icon = Icons.Default.CheckCircle,
        title = stringResource(R.string.setup_done_title),
        body = stringResource(R.string.setup_done_body),
    )
}

@Composable
private fun WizardNavRow(
    isFirst: Boolean,
    isLast: Boolean,
    canAdvance: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    showSkip: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!isFirst) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.setup_back)) }
        }
        Spacer(Modifier.weight(1f))
        if (showSkip && !isLast) {
            TextButton(onClick = onSkip) { Text(stringResource(R.string.setup_skip)) }
            Spacer(Modifier.width(8.dp))
        }
        Button(onClick = onNext, enabled = canAdvance) {
            Text(if (isLast) stringResource(R.string.setup_finish) else stringResource(R.string.setup_next))
        }
    }
}
