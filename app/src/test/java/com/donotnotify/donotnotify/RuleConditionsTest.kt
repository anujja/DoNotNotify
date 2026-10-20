package com.donotnotify.donotnotify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleConditionsTest {

    private fun at(hour: Int, minute: Int = 0) = MatchContext(minuteOfDay = hour * 60 + minute)

    private fun window(startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) =
        AdvancedRuleConfig(
            isTimeLimitEnabled = true,
            startTimeHour = startHour,
            startTimeMinute = startMinute,
            endTimeHour = endHour,
            endTimeMinute = endMinute
        )

    @Test
    fun `no config means always active`() {
        assertTrue(RuleConditions.isActive(null, at(3)))
    }

    @Test
    fun `disabled time limit is ignored`() {
        val config = window(9, 0, 17, 0).copy(isTimeLimitEnabled = false)
        assertTrue(RuleConditions.isActive(config, at(3)))
    }

    @Test
    fun `daytime window includes both ends`() {
        val config = window(9, 0, 17, 0)
        assertFalse(RuleConditions.isActive(config, at(8, 59)))
        assertTrue(RuleConditions.isActive(config, at(9, 0)))
        assertTrue(RuleConditions.isActive(config, at(12, 30)))
        assertTrue(RuleConditions.isActive(config, at(17, 0)))
        assertFalse(RuleConditions.isActive(config, at(17, 1)))
    }

    @Test
    fun `window spanning midnight`() {
        val config = window(22, 0, 7, 0)
        assertTrue(RuleConditions.isActive(config, at(23, 30)))
        assertTrue(RuleConditions.isActive(config, at(0, 0)))
        assertTrue(RuleConditions.isActive(config, at(7, 0)))
        assertFalse(RuleConditions.isActive(config, at(7, 1)))
        assertFalse(RuleConditions.isActive(config, at(21, 59)))
    }

    @Test
    fun `in-use condition follows device state`() {
        val config = AdvancedRuleConfig(deviceUsage = DeviceUsage.IN_USE)
        assertTrue(RuleConditions.isActive(config, MatchContext(0, deviceInUse = true)))
        assertFalse(RuleConditions.isActive(config, MatchContext(0, deviceInUse = false)))
    }

    @Test
    fun `not-in-use condition follows device state`() {
        val config = AdvancedRuleConfig(deviceUsage = DeviceUsage.NOT_IN_USE)
        assertFalse(RuleConditions.isActive(config, MatchContext(0, deviceInUse = true)))
        assertTrue(RuleConditions.isActive(config, MatchContext(0, deviceInUse = false)))
    }

    @Test
    fun `usage condition does not exclude a rule when device state is unknown`() {
        for (usage in DeviceUsage.entries) {
            val config = AdvancedRuleConfig(deviceUsage = usage)
            assertTrue(usage.name, RuleConditions.isActive(config, MatchContext(0, deviceInUse = null)))
        }
    }

    @Test
    fun `no usage condition ignores device state`() {
        assertTrue(RuleConditions.isActive(AdvancedRuleConfig(), MatchContext(0, deviceInUse = true)))
        assertTrue(RuleConditions.isActive(AdvancedRuleConfig(), MatchContext(0, deviceInUse = false)))
    }

    @Test
    fun `time window and usage must both hold`() {
        val config = window(9, 0, 17, 0).copy(deviceUsage = DeviceUsage.NOT_IN_USE)
        assertTrue(RuleConditions.isActive(config, at(10).copy(deviceInUse = false)))
        assertFalse(RuleConditions.isActive(config, at(10).copy(deviceInUse = true)))
        assertFalse(RuleConditions.isActive(config, at(20).copy(deviceInUse = false)))
    }

    @Test
    fun `matches rejects a rule outside its time window before checking filters`() {
        val rule = BlockerRule(
            packageName = "p",
            titleFilter = "Promo",
            advancedConfig = window(9, 0, 17, 0)
        )
        assertTrue(RuleMatcher.matches(rule, "p", "Promo", null, at(10)))
        assertFalse(RuleMatcher.matches(rule, "p", "Promo", null, at(20)))
    }

    @Test
    fun `decision uses the supplied context for every rule`() {
        val timedDeny = BlockerRule(
            packageName = "p",
            titleFilter = "Promo",
            ruleType = RuleType.DENYLIST,
            advancedConfig = window(9, 0, 17, 0)
        )
        val inside = RuleMatcher.planNotificationDecision(
            listOf(timedDeny), "p", "Promo", null, wasOngoing = false, matchContext = at(10)
        )
        val outside = RuleMatcher.planNotificationDecision(
            listOf(timedDeny), "p", "Promo", null, wasOngoing = false, matchContext = at(20)
        )
        assertTrue(inside.isBlocked)
        assertEquals(listOf(0), inside.matchedRuleIndices)
        assertFalse(outside.isBlocked)
        assertEquals(emptyList<Int>(), outside.matchedRuleIndices)
    }
}
