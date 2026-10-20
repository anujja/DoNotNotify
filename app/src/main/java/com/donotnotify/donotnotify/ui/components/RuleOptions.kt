package com.donotnotify.donotnotify.ui.components

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccessAlarms
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.donotnotify.donotnotify.AdvancedRuleConfig
import com.donotnotify.donotnotify.BlockerRule
import com.donotnotify.donotnotify.DeviceUsage
import com.donotnotify.donotnotify.R
import com.donotnotify.donotnotify.RuleType
import java.util.Locale

/** Which group of the rule editor's options section an option is listed under. */
enum class RuleOptionSection {
    /** "Only apply when…" — gates whether the rule is active. Evaluated in `RuleConditions`. */
    CONDITION,
    /** "When matched…" — extra behaviour once the rule has matched. */
    ACTION
}

/**
 * One configurable rule option (a condition or an action) stored in [AdvancedRuleConfig].
 *
 * Every option renders the same way in [AdvancedRuleConfigDialog] — icon, title, description,
 * switch, and its editor while switched on — and the same descriptor supplies the rule card's
 * icon, so adding an option means adding one object to [RULE_OPTIONS]. Neither screen needs to change.
 */
interface RuleOption {
    val section: RuleOptionSection
    val icon: ImageVector

    /** Rule types the option is offered for. It is switched off on save for any other type. */
    fun appliesTo(ruleType: RuleType): Boolean
    fun isOn(config: AdvancedRuleConfig): Boolean

    /** Switching off must keep the option's settings where the model allows, so re-enabling restores them. */
    fun setOn(config: AdvancedRuleConfig, on: Boolean): AdvancedRuleConfig

    @Composable
    fun title(): String

    /** What the option does, in one short line under its title. */
    @Composable
    fun description(): String

    /** One line describing the current settings, e.g. "09:00 – 17:00". */
    @Composable
    fun summary(config: AdvancedRuleConfig): String

    @Composable
    fun Editor(config: AdvancedRuleConfig, onChange: (AdvancedRuleConfig) -> Unit)
}

/** Every rule option, in display order. */
val RULE_OPTIONS: List<RuleOption> = listOf(
    TimeWindowOption,
    DeviceUsageOption
)

fun ruleOptionsFor(ruleType: RuleType): List<RuleOption> =
    RULE_OPTIONS.filter { it.appliesTo(ruleType) }

/** Options that are switched on and take effect for this rule. */
fun BlockerRule.activeRuleOptions(): List<RuleOption> {
    val config = advancedConfig ?: return emptyList()
    return ruleOptionsFor(ruleType).filter { it.isOn(config) }
}

/**
 * Switches off options [ruleType] doesn't offer, and drops a throttle length from a rule that
 * isn't THROTTLE, so a type change can't leave a hidden setting behind.
 */
fun AdvancedRuleConfig.withOnlyOptionsFor(ruleType: RuleType): AdvancedRuleConfig =
    RULE_OPTIONS.filterNot { it.appliesTo(ruleType) }
        .fold(this) { config, option -> option.setOn(config, false) }
        .let { if (ruleType == RuleType.THROTTLE) it else it.copy(throttleSeconds = null) }

object TimeWindowOption : RuleOption {
    override val section = RuleOptionSection.CONDITION
    override val icon = Icons.Filled.AccessAlarms

    override fun appliesTo(ruleType: RuleType) = true
    override fun isOn(config: AdvancedRuleConfig) = config.isTimeLimitEnabled
    override fun setOn(config: AdvancedRuleConfig, on: Boolean) = config.copy(isTimeLimitEnabled = on)

    @Composable
    override fun title() = stringResource(R.string.rule_condition_time)

    @Composable
    override fun description() = stringResource(R.string.rule_condition_time_desc)

    @Composable
    override fun summary(config: AdvancedRuleConfig) = stringResource(
        R.string.time_range,
        formatClockTime(config.startTimeHour, config.startTimeMinute),
        formatClockTime(config.endTimeHour, config.endTimeMinute)
    )

    @Composable
    override fun Editor(config: AdvancedRuleConfig, onChange: (AdvancedRuleConfig) -> Unit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TimeTile(
                label = stringResource(R.string.rule_time_start),
                hour = config.startTimeHour,
                minute = config.startTimeMinute,
                onTimeSelected = { h, m -> onChange(config.copy(startTimeHour = h, startTimeMinute = m)) },
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .size(20.dp)
            )
            TimeTile(
                label = stringResource(R.string.rule_time_end),
                hour = config.endTimeHour,
                minute = config.endTimeMinute,
                onTimeSelected = { h, m -> onChange(config.copy(endTimeHour = h, endTimeMinute = m)) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

object DeviceUsageOption : RuleOption {
    override val section = RuleOptionSection.CONDITION
    override val icon = Icons.Filled.Smartphone

    override fun appliesTo(ruleType: RuleType) = true
    override fun isOn(config: AdvancedRuleConfig) = config.deviceUsage != null
    override fun setOn(config: AdvancedRuleConfig, on: Boolean) =
        config.copy(deviceUsage = if (on) config.deviceUsage ?: DeviceUsage.IN_USE else null)

    @Composable
    override fun title() = stringResource(R.string.rule_condition_usage)

    @Composable
    override fun description() = stringResource(R.string.rule_condition_usage_desc)

    @Composable
    override fun summary(config: AdvancedRuleConfig) = when (config.deviceUsage) {
        DeviceUsage.IN_USE -> stringResource(R.string.device_usage_summary_in_use)
        DeviceUsage.NOT_IN_USE -> stringResource(R.string.device_usage_summary_not_in_use)
        null -> ""
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Editor(config: AdvancedRuleConfig, onChange: (AdvancedRuleConfig) -> Unit) {
        val selected = config.deviceUsage ?: return
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            DeviceUsage.entries.forEachIndexed { index, usage ->
                SegmentedButton(
                    selected = selected == usage,
                    onClick = { onChange(config.copy(deviceUsage = usage)) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = DeviceUsage.entries.size),
                    // Matches the rule-type picker, which marks the selection by fill alone.
                    icon = {}
                ) {
                    Text(usage.label(), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Text(
            stringResource(R.string.device_usage_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, start = 4.dp)
        )
    }
}

/** The switched-on options as one line, e.g. "22:00 – 07:00 · Phone in use"; empty when none are on. */
@Composable
fun ruleOptionsSummary(ruleType: RuleType, config: AdvancedRuleConfig): String =
    ruleOptionsFor(ruleType).filter { it.isOn(config) }.map { it.summary(config) }.joinToString(" · ")

/** The rule card's icons for [rule]'s active options; emits nothing when none are on. */
@Composable
fun RuleOptionIcons(rule: BlockerRule, modifier: Modifier = Modifier) {
    val config = rule.advancedConfig ?: return
    val active = rule.activeRuleOptions()
    if (active.isEmpty()) return
    Row(
        modifier = modifier.padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        active.forEach { option ->
            Icon(
                imageVector = option.icon,
                contentDescription = "${option.title()}: ${option.summary(config)}"
            )
        }
    }
}

@Composable
internal fun RuleOptionSection.label(): String = when (this) {
    RuleOptionSection.CONDITION -> stringResource(R.string.rule_options_conditions)
    RuleOptionSection.ACTION -> stringResource(R.string.rule_options_actions)
}

@Composable
private fun DeviceUsage.label(): String = when (this) {
    DeviceUsage.IN_USE -> stringResource(R.string.device_usage_in_use)
    DeviceUsage.NOT_IN_USE -> stringResource(R.string.device_usage_not_in_use)
}

private fun formatClockTime(hour: Int, minute: Int): String =
    String.format(Locale.getDefault(), "%02d:%02d", hour, minute)

/** A labelled time, e.g. "Start 09:00"; tapping it opens the system time picker. */
@Composable
private fun TimeTile(
    label: String,
    hour: Int,
    minute: Int,
    onTimeSelected: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Surface(
        onClick = {
            TimePickerDialog(
                context,
                { _, h, m -> onTimeSelected(h, m) },
                hour,
                minute,
                true // 24 hour view
            ).show()
        },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(formatClockTime(hour, minute), style = MaterialTheme.typography.headlineSmall)
        }
    }
}
