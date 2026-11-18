package com.donotnotify.donotnotify

import java.util.Calendar

/**
 * Snapshot of the device/time state that rule conditions are evaluated against. Taken once per
 * notification so every rule sees the same state, and passed in rather than read inside
 * [RuleConditions] so conditions stay pure and JVM-testable. A new condition that depends on
 * device state adds its field here.
 */
data class MatchContext(
    /** Local wall-clock time as minutes since midnight, 0..1439. */
    val minuteOfDay: Int,
    /** The local day of the week. */
    val dayOfWeek: Weekday,
    /**
     * Screen on and unlocked, or null when unknown — e.g. when the UI re-evaluates a rule
     * long after the notification arrived. Usage conditions don't exclude a rule when unknown.
     */
    val deviceInUse: Boolean? = null
) {
    companion object {
        fun now(deviceInUse: Boolean? = null): MatchContext {
            val now = Calendar.getInstance()
            return MatchContext(
                minuteOfDay = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE),
                dayOfWeek = Weekday.fromCalendarDay(now.get(Calendar.DAY_OF_WEEK)),
                deviceInUse = deviceInUse
            )
        }
    }
}

/**
 * The "only apply when…" half of [AdvancedRuleConfig]: conditions that decide whether a rule is
 * active at all (time window, days of the week, phone usage), before its title/text filters are
 * considered. A rule
 * is active only when every enabled condition holds. The UI counterpart is
 * `ui.components.RuleOptions`.
 */
object RuleConditions {

    fun isActive(config: AdvancedRuleConfig?, context: MatchContext): Boolean {
        if (config == null) return true
        if (config.isTimeLimitEnabled && !isWithinTimeWindow(config, context.minuteOfDay)) return false
        config.daysOfWeek?.takeIf { it.isNotEmpty() }?.let { if (!isOnScheduledDay(config, it, context)) return false }
        config.deviceUsage?.let { if (!matchesDeviceUsage(it, context.deviceInUse)) return false }
        return true
    }

    fun matchesDeviceUsage(usage: DeviceUsage, deviceInUse: Boolean?): Boolean = when (usage) {
        DeviceUsage.IN_USE -> deviceInUse != false
        DeviceUsage.NOT_IN_USE -> deviceInUse != true
    }

    /** Both ends are inclusive. A start after the end means the window spans midnight. */
    fun isWithinTimeWindow(config: AdvancedRuleConfig, minuteOfDay: Int): Boolean {
        val start = startMinute(config)
        val end = endMinute(config)
        return if (start <= end) {
            minuteOfDay in start..end
        } else {
            minuteOfDay >= start || minuteOfDay <= end
        }
    }

    /**
     * Whether the day [context] falls on is one of [days]. With a time window that spans midnight
     * the hours after midnight belong to the day the window started, as in Do Not Disturb
     * schedules: Friday with 22:00 – 07:00 covers Friday night through 07:00 on Saturday, and the
     * small hours of Friday belong to Thursday.
     */
    fun isOnScheduledDay(config: AdvancedRuleConfig, days: Set<Weekday>, context: MatchContext): Boolean {
        val inWindowTail = config.isTimeLimitEnabled && spansMidnight(config) &&
            context.minuteOfDay <= endMinute(config)
        val day = if (inWindowTail) context.dayOfWeek.previous else context.dayOfWeek
        return day in days
    }

    fun spansMidnight(config: AdvancedRuleConfig): Boolean = startMinute(config) > endMinute(config)

    private fun startMinute(config: AdvancedRuleConfig) = config.startTimeHour * 60 + config.startTimeMinute
    private fun endMinute(config: AdvancedRuleConfig) = config.endTimeHour * 60 + config.endTimeMinute
}
