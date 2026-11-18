package com.donotnotify.donotnotify

import androidx.annotation.Keep

/**
 * A day of the week, for the "Days of the week" rule condition ([AdvancedRuleConfig.daysOfWeek]).
 * Serialized by name into stored rules and exports, so never rename the constants. Our own enum
 * because `java.time.DayOfWeek` needs API 26 and the app's minimum is 24.
 */
@Keep
enum class Weekday {
    MONDAY,
    TUESDAY,
    WEDNESDAY,
    THURSDAY,
    FRIDAY,
    SATURDAY,
    SUNDAY;

    val previous: Weekday get() = entries[(ordinal + 6) % 7]
    val next: Weekday get() = entries[(ordinal + 1) % 7]

    /** This day as a `java.util.Calendar.DAY_OF_WEEK` value (Sunday = 1 … Saturday = 7). */
    val calendarDay: Int get() = (ordinal + 1) % 7 + 1

    companion object {
        /** What the condition starts with when switched on, like the time window's 09:00 – 17:00. */
        val WORKDAYS: Set<Weekday> = setOf(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY)

        /** From a `java.util.Calendar.DAY_OF_WEEK` value (Sunday = 1 … Saturday = 7). */
        fun fromCalendarDay(day: Int): Weekday = entries[(day + 5) % 7]

        /** The seven days in display order, starting with [first], the locale's first day of the week. */
        fun weekStartingOn(first: Weekday): List<Weekday> = List(7) { entries[(first.ordinal + it) % 7] }

        /**
         * [selected] as runs of consecutive days, for a summary like "Mon – Fri". The runs come in
         * [week] order, and a run may wrap past the end of the week, so Saturday and Sunday stay one
         * run in a week that starts on Sunday. Every day selected is one run in [week] order.
         */
        fun runs(selected: Set<Weekday>, week: List<Weekday>): List<List<Weekday>> {
            if (selected.isEmpty()) return emptyList()
            if (selected.containsAll(entries)) return listOf(week)
            return week.filter { it in selected && it.previous !in selected }.map { start ->
                generateSequence(start) { it.next }.takeWhile { it in selected }.toList()
            }
        }
    }
}
