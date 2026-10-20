package com.donotnotify.donotnotify.ui.components

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.donotnotify.donotnotify.AdvancedRuleConfig
import com.donotnotify.donotnotify.R
import com.donotnotify.donotnotify.RuleType
import com.donotnotify.donotnotify.ThrottleManager

/** Left/right inset of the dialog's header and buttons. */
private val Inset = 24.dp

/** Left/right inset of the option cards, which sit wider than the header. */
private val CardInset = 16.dp

/**
 * A rule's Advanced Configuration, opened from the rule editor and from the new-rule wizard:
 * Enable Rule, then every [RuleOption] offered for [ruleType] grouped by [RuleOptionSection],
 * then the type's own settings: the duration for THROTTLE, the channel name and sound for
 * STACK. Each group is a stack of rounded cards, as in the system Settings app. Nothing is
 * applied until Save.
 */
@Composable
fun AdvancedRuleConfigDialog(
    ruleType: RuleType,
    initialConfig: AdvancedRuleConfig,
    initialIsEnabled: Boolean,
    initialName: String,
    suggestedName: String,
    /** Non-null (a channel id) shows the "Sound & vibration" deep-link for that channel. */
    soundChannelId: String?,
    onDismiss: () -> Unit,
    onSave: (AdvancedRuleConfig, Boolean, String) -> Unit
) {
    var config by remember { mutableStateOf(initialConfig) }
    var isEnabled by remember { mutableStateOf(initialIsEnabled) }
    var ruleName by remember { mutableStateOf(initialName) }
    val options = ruleOptionsFor(ruleType)

    // Wider than the platform default: the cards need the room.
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .widthIn(max = 560.dp)
                .imePadding()
        ) {
            Column {
                DialogHeader(ruleType)

                // Only the options scroll; the header and the buttons stay put.
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = CardInset)
                ) {
                    MainSwitch(
                        title = stringResource(R.string.enable_rule),
                        checked = isEnabled,
                        onCheckedChange = { isEnabled = it }
                    )

                    RuleOptionSection.entries.forEach { section ->
                        val sectionOptions = options.filter { it.section == section }
                        if (sectionOptions.isEmpty()) return@forEach
                        SectionHeader(section.label())
                        CardGroup(sectionOptions.size) { index ->
                            val option = sectionOptions[index]
                            val on = option.isOn(config)
                            OptionRow(
                                icon = option.icon,
                                title = option.title(),
                                description = option.description(),
                                iconColors = if (on) {
                                    MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHighest to MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.toggleable(
                                    value = on,
                                    role = Role.Switch,
                                    onValueChange = { config = option.setOn(config, it) }
                                ),
                                trailing = { Switch(checked = on, onCheckedChange = null) }
                            )
                            AnimatedVisibility(visible = on) {
                                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                                    option.Editor(config) { config = it }
                                }
                            }
                        }
                    }

                    if (ruleType == RuleType.THROTTLE) {
                        SectionHeader(RuleType.THROTTLE.label())
                        CardGroup(1) {
                            OptionRow(
                                icon = RuleType.THROTTLE.icon(),
                                title = throttleSummary(config),
                                description = stringResource(R.string.throttle_help),
                                iconColors = MaterialTheme.colorScheme.tertiaryContainer to
                                    MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            ThrottleDurationEditor(
                                seconds = ThrottleManager.secondsFor(config),
                                onSecondsChange = { config = config.copy(throttleSeconds = it) }
                            )
                        }
                    }

                    if (ruleType == RuleType.STACK) {
                        SectionHeader(stringResource(R.string.stack_channel_group_name))
                        StackChannelSettings(
                            ruleName = ruleName,
                            onRuleNameChange = { ruleName = it },
                            suggestedName = suggestedName,
                            soundChannelId = soundChannelId
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }

                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = Inset, end = Inset, top = 12.dp, bottom = 20.dp)
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { onSave(config, isEnabled, ruleName) }) {
                        Text(stringResource(R.string.save))
                    }
                }
            }
        }
    }
}

/**
 * A STACK rule's suggested channel name, built from its filters. Offered as a suggestion only,
 * never prefilled: the name becomes the notification channel's name, which any notification
 * listener can read, so filter text must not be published unless the user chooses it.
 */
fun suggestedStackRuleName(appName: String, titleFilter: String, textFilter: String): String {
    val filter = titleFilter.ifBlank { textFilter }.trim()
    return when {
        filter.isBlank() -> ""
        appName.isBlank() -> filter.take(40)
        else -> "$appName — $filter".take(40)
    }
}

/** Icon, title, and the rule's type, so it's clear whose settings these are. */
@Composable
private fun DialogHeader(ruleType: RuleType) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Inset, end = Inset, top = 24.dp, bottom = 16.dp)
    ) {
        Icon(
            Icons.Outlined.Tune,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            stringResource(R.string.advanced_configuration),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        val accent = ruleType.accentColor()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.12f))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Icon(ruleType.icon(), contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(ruleType.label(), style = MaterialTheme.typography.labelLarge, color = accent)
        }
    }
}

/** The rule's on/off switch, as a bar that fills with colour when on, like Settings' main switches. */
@Composable
private fun MainSwitch(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val container by animateColorAsState(
        if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceBright,
        label = "mainSwitchContainer"
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(container)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .heightIn(min = 64.dp)
            .padding(horizontal = 20.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = if (checked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp)
    )
}

/**
 * [count] cards stacked with small gaps, rounded more at the group's outer corners, so they
 * read as one group.
 */
@Composable
private fun CardGroup(count: Int, card: @Composable ColumnScope.(index: Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(count) { index ->
            val outer = 20.dp
            val inner = 4.dp
            Surface(
                shape = RoundedCornerShape(
                    topStart = if (index == 0) outer else inner,
                    topEnd = if (index == 0) outer else inner,
                    bottomStart = if (index == count - 1) outer else inner,
                    bottomEnd = if (index == count - 1) outer else inner
                ),
                color = MaterialTheme.colorScheme.surfaceBright,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column { card(index) }
            }
        }
    }
}

/** A card's main row: an icon in a tinted circle, a title with an optional description, and [trailing]. */
@Composable
private fun OptionRow(
    icon: ImageVector,
    title: String,
    description: String?,
    iconColors: Pair<Color, Color>,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    val iconContainer by animateColorAsState(iconColors.first, label = "optionIconContainer")
    val iconContent by animateColorAsState(iconColors.second, label = "optionIconContent")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(iconContainer)
        ) {
            Icon(icon, contentDescription = null, tint = iconContent, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
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

/** How long a THROTTLE rule blocks matches after one passes, in seconds or minutes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThrottleDurationEditor(seconds: Int, onSecondsChange: (Int) -> Unit) {
    // The field keeps what's typed, even when it isn't a valid length yet; only valid lengths
    // are reported, so the rule keeps the last one meanwhile.
    var inMinutes by remember { mutableStateOf(seconds % 60 == 0) }
    var amount by remember { mutableStateOf((if (inMinutes) seconds / 60 else seconds).toString()) }

    fun secondsOf(text: String, minutes: Boolean): Long? =
        text.toLongOrNull()?.let { it * if (minutes) 60 else 1 }?.takeIf { it in 1..ThrottleManager.MAX_SECONDS }

    fun update(newAmount: String, minutes: Boolean) {
        amount = newAmount
        inMinutes = minutes
        secondsOf(newAmount, minutes)?.let { onSecondsChange(it.toInt()) }
    }

    val durationDescription = stringResource(R.string.throttle_duration)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
    ) {
        OutlinedTextField(
            value = amount,
            onValueChange = { update(it.filter(Char::isDigit).take(5), inMinutes) },
            isError = secondsOf(amount, inMinutes) == null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.Center),
            // Pill-shaped, like the unit buttons beside it.
            shape = CircleShape,
            modifier = Modifier
                .width(96.dp)
                .semantics { contentDescription = durationDescription }
        )
        Spacer(modifier = Modifier.width(12.dp))
        // As tall as the field beside it.
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .weight(1f)
                .height(OutlinedTextFieldDefaults.MinHeight)
        ) {
            listOf(false, true).forEachIndexed { index, minutes ->
                SegmentedButton(
                    selected = inMinutes == minutes,
                    onClick = { update(amount, minutes) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                    icon = {},
                    modifier = Modifier.fillMaxHeight()
                ) {
                    Text(
                        stringResource(if (minutes) R.string.throttle_unit_minutes else R.string.throttle_unit_seconds),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun StackChannelSettings(
    ruleName: String,
    onRuleNameChange: (String) -> Unit,
    suggestedName: String,
    soundChannelId: String?
) {
    val context = LocalContext.current
    CardGroup(if (soundChannelId != null) 2 else 1) { index ->
        if (index == 0) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)) {
                OutlinedTextField(
                    value = ruleName,
                    onValueChange = onRuleNameChange,
                    label = { Text(stringResource(R.string.rule_name_optional)) },
                    supportingText = { Text(stringResource(R.string.rule_name_supporting)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                // Explicit opt-in: tapping this is what publishes filter-derived text as the
                // channel name. Prefilling the field would mean a user who just taps Save
                // silently exposes their filter to any notification listener.
                if (suggestedName.isNotBlank() && ruleName != suggestedName) {
                    TextButton(
                        onClick = { onRuleNameChange(suggestedName) },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                    ) {
                        Text(stringResource(R.string.rule_name_use_suggestion, suggestedName))
                    }
                }
            }
        } else {
            // Sound/vibration are user-owned once the channel exists — Android gives the
            // app no way to change them — so link out rather than fake an in-app control.
            // Only for a saved rule: the channel must exist before it can be configured.
            OptionRow(
                icon = Icons.Filled.Notifications,
                title = stringResource(R.string.rule_sound_vibration),
                description = stringResource(R.string.rule_sound_vibration_desc),
                iconColors = MaterialTheme.colorScheme.secondaryContainer to
                    MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.clickable {
                    runCatching {
                        val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            .putExtra(Settings.EXTRA_CHANNEL_ID, soundChannelId)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    }
                },
                trailing = {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
        }
    }
}
