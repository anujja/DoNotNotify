package com.donotnotify.donotnotify

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Test

class WeekdayTest {

    private val mondayFirst = Weekday.weekStartingOn(Weekday.MONDAY)
    private val sundayFirst = Weekday.weekStartingOn(Weekday.SUNDAY)

    @Test
    fun `calendar days map both ways`() {
        assertEquals(Weekday.SUNDAY, Weekday.fromCalendarDay(Calendar.SUNDAY))
        assertEquals(Weekday.MONDAY, Weekday.fromCalendarDay(Calendar.MONDAY))
        assertEquals(Weekday.SATURDAY, Weekday.fromCalendarDay(Calendar.SATURDAY))
        for (day in Weekday.entries) assertEquals(day, Weekday.fromCalendarDay(day.calendarDay))
    }

    @Test
    fun `previous and next wrap around the week`() {
        assertEquals(Weekday.SUNDAY, Weekday.MONDAY.previous)
        assertEquals(Weekday.MONDAY, Weekday.SUNDAY.next)
        assertEquals(Weekday.THURSDAY, Weekday.FRIDAY.previous)
    }

    @Test
    fun `week starts on the given day`() {
        assertEquals(Weekday.entries, mondayFirst)
        assertEquals(
            listOf(Weekday.SUNDAY, Weekday.MONDAY, Weekday.TUESDAY, Weekday.WEDNESDAY, Weekday.THURSDAY, Weekday.FRIDAY, Weekday.SATURDAY),
            sundayFirst
        )
    }

    @Test
    fun `runs group consecutive days in week order`() {
        assertEquals(listOf(Weekday.WORKDAYS.toList()), Weekday.runs(Weekday.WORKDAYS, sundayFirst))
        assertEquals(
            listOf(listOf(Weekday.MONDAY), listOf(Weekday.WEDNESDAY), listOf(Weekday.FRIDAY)),
            Weekday.runs(setOf(Weekday.FRIDAY, Weekday.MONDAY, Weekday.WEDNESDAY), mondayFirst)
        )
        assertEquals(emptyList<List<Weekday>>(), Weekday.runs(emptySet(), mondayFirst))
    }

    @Test
    fun `a run wraps past the end of the week`() {
        val weekend = setOf(Weekday.SATURDAY, Weekday.SUNDAY)
        assertEquals(listOf(listOf(Weekday.SATURDAY, Weekday.SUNDAY)), Weekday.runs(weekend, mondayFirst))
        assertEquals(listOf(listOf(Weekday.SATURDAY, Weekday.SUNDAY)), Weekday.runs(weekend, sundayFirst))
        assertEquals(
            listOf(listOf(Weekday.FRIDAY, Weekday.SATURDAY, Weekday.SUNDAY, Weekday.MONDAY)),
            Weekday.runs(setOf(Weekday.MONDAY, Weekday.FRIDAY, Weekday.SATURDAY, Weekday.SUNDAY), mondayFirst)
        )
    }

    @Test
    fun `every day is one run in week order`() {
        assertEquals(listOf(sundayFirst), Weekday.runs(Weekday.entries.toSet(), sundayFirst))
    }
}
