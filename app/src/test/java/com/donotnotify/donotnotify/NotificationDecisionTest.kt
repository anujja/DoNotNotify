package com.donotnotify.donotnotify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Precedence matrix for the pure [RuleMatcher.planNotificationDecision]. */
class NotificationDecisionTest {

    private fun deny(t: String) =
        BlockerRule(packageName = "p", titleFilter = t, ruleType = RuleType.DENYLIST)

    private fun allow(t: String) =
        BlockerRule(packageName = "p", titleFilter = t, ruleType = RuleType.ALLOWLIST)

    private fun stack(t: String) =
        BlockerRule(packageName = "p", titleFilter = t, ruleType = RuleType.STACK)

    private fun throttle(t: String?) =
        BlockerRule(packageName = "p", titleFilter = t, ruleType = RuleType.THROTTLE)

    // ---- conditions vs allowlist gating ----------------------------------------

    private val daytime = AdvancedRuleConfig(isTimeLimitEnabled = true, startTimeHour = 9, endTimeHour = 17)
    private val noon = MatchContext(minuteOfDay = 12 * 60)
    private val night = MatchContext(minuteOfDay = 22 * 60)

    @Test
    fun `allowlist outside its time window does not gate the app`() {
        val rules = listOf(allow("John").copy(advancedConfig = daytime))
        val d = RuleMatcher.planNotificationDecision(rules, "p", "Promo", null, false, night)
        assertFalse(d.isBlocked)
        assertTrue(d.matchedRuleIndices.isEmpty())
    }

    @Test
    fun `allowlist inside its time window still gates the app`() {
        val rules = listOf(allow("John").copy(advancedConfig = daytime))
        assertTrue(RuleMatcher.planNotificationDecision(rules, "p", "Promo", null, false, noon).isBlocked)
        assertFalse(RuleMatcher.planNotificationDecision(rules, "p", "John", null, false, noon).isBlocked)
    }

    @Test
    fun `allowlist limited to phone not in use gates only while not in use`() {
        val rules = listOf(allow("John").copy(advancedConfig = AdvancedRuleConfig(deviceUsage = DeviceUsage.NOT_IN_USE)))
        val inUse = MatchContext(minuteOfDay = 0, deviceInUse = true)
        val idle = MatchContext(minuteOfDay = 0, deviceInUse = false)
        assertFalse(RuleMatcher.planNotificationDecision(rules, "p", "Promo", null, false, inUse).isBlocked)
        assertFalse(RuleMatcher.planNotificationDecision(rules, "p", "John", null, false, inUse).isBlocked)
        assertTrue(RuleMatcher.planNotificationDecision(rules, "p", "Promo", null, false, idle).isBlocked)
        assertFalse(RuleMatcher.planNotificationDecision(rules, "p", "John", null, false, idle).isBlocked)
    }

    @Test
    fun `only active allowlist rules let notifications through`() {
        // Inactive "Promo" allowlist neither gates nor allows; the active "John" one still gates.
        val rules = listOf(allow("Promo").copy(advancedConfig = daytime), allow("John"))
        val d = RuleMatcher.planNotificationDecision(rules, "p", "Promo", null, false, night)
        assertTrue(d.isBlocked)
        assertTrue(d.matchedRuleIndices.isEmpty())
    }

    @Test
    fun `shouldBlock agrees on inactive allowlist rules`() {
        val rules = listOf(allow("John").copy(advancedConfig = daytime))
        assertFalse(RuleMatcher.shouldBlock("p", "Promo", null, rules, night))
        assertTrue(RuleMatcher.shouldBlock("p", "Promo", null, rules, noon))
    }

    @Test
    fun `stack only, matching, not ongoing - stacks, not blocked`() {
        val d = RuleMatcher.planNotificationDecision(listOf(stack("News")), "p", "News x", null, false)
        assertFalse(d.isBlocked)
        assertTrue(d.shouldStack)
        assertEquals(listOf(0), d.matchedRuleIndices)
    }

    @Test
    fun `stack only, non-matching - neither blocked nor stacked`() {
        val d = RuleMatcher.planNotificationDecision(listOf(stack("News")), "p", "Other", null, false)
        assertFalse(d.isBlocked)
        assertFalse(d.shouldStack)
        assertTrue(d.matchedRuleIndices.isEmpty())
    }

    @Test
    fun `denylist wins over stack`() {
        val rules = listOf(stack("Promo"), deny("Promo"))
        val d = RuleMatcher.planNotificationDecision(rules, "p", "Promo!", null, false)
        assertTrue(d.isBlocked)
        assertFalse(d.shouldStack)
        assertNull(d.matchedStackRule)
    }

    @Test
    fun `allowlist gating wins over stack`() {
        val rules = listOf(allow("OTP"), stack("Promo"))
        val d = RuleMatcher.planNotificationDecision(rules, "p", "Promo!", null, false)
        assertTrue(d.isBlocked) // allowlist present but unmatched
        assertFalse(d.shouldStack)
    }

    @Test
    fun `first enabled stack match wins`() {
        val first = stack("A")
        val second = stack("A")
        val d = RuleMatcher.planNotificationDecision(listOf(first, second), "p", "A here", null, false)
        assertTrue(d.shouldStack)
        assertSame(first, d.matchedStackRule)
        assertEquals(listOf(0), d.matchedRuleIndices)
    }

    @Test
    fun `ongoing notification is excluded from stacking`() {
        val d = RuleMatcher.planNotificationDecision(listOf(stack("News")), "p", "News x", null, true)
        assertFalse(d.shouldStack)
    }

    @Test
    fun `stack never gates - other package unaffected`() {
        val d = RuleMatcher.planNotificationDecision(listOf(stack("News")), "other", "News", null, false)
        assertFalse(d.isBlocked)
        assertFalse(d.shouldStack)
    }

    @Test
    fun `disabled stack rule ignored`() {
        val rule = stack("News").copy(isEnabled = false)
        val d = RuleMatcher.planNotificationDecision(listOf(rule), "p", "News", null, false)
        assertFalse(d.shouldStack)
    }

    // ---- THROTTLE ---------------------------------------------------------------

    @Test
    fun `throttle rule neither blocks nor gates the app`() {
        val rules = listOf(throttle("Speed trap"))
        val matched = RuleMatcher.planNotificationDecision(rules, "p", "Speed trap 900m", null, false)
        assertFalse(matched.isBlocked)
        assertEquals(listOf(rules[0]), matched.throttleRules)
        assertEquals(listOf(0), matched.matchedRuleIndices)
        val other = RuleMatcher.planNotificationDecision(rules, "p", "Traffic jam", null, false)
        assertFalse(other.isBlocked)
        assertTrue(other.throttleRules.isEmpty())
    }

    @Test
    fun `every matching throttle rule is returned, not just the first`() {
        val rules = listOf(throttle("Speed"), throttle(null), throttle("Jam"))
        val d = RuleMatcher.planNotificationDecision(rules, "p", "Speed trap", null, false)
        assertEquals(listOf(rules[0], rules[1]), d.throttleRules)
        assertEquals(listOf(0, 1), d.matchedRuleIndices)
    }

    @Test
    fun `blocked or ongoing notifications are never throttled`() {
        val t = throttle("Promo")
        assertTrue(RuleMatcher.planNotificationDecision(listOf(deny("Promo"), t), "p", "Promo", null, false).throttleRules.isEmpty())
        assertTrue(RuleMatcher.planNotificationDecision(listOf(allow("John"), t), "p", "Promo", null, false).throttleRules.isEmpty())
        assertTrue(RuleMatcher.planNotificationDecision(listOf(t), "p", "Promo", null, wasOngoing = true).throttleRules.isEmpty())
        // Let through by the allowlist, so the throttle applies.
        assertEquals(listOf(t), RuleMatcher.planNotificationDecision(listOf(allow("Promo"), t), "p", "Promo", null, false).throttleRules)
    }

    @Test
    fun `throttle and stack can both apply`() {
        val t = throttle("News")
        val s = stack("News")
        val d = RuleMatcher.planNotificationDecision(listOf(t, s), "p", "News", null, false)
        assertTrue(d.shouldStack)
        assertSame(s, d.matchedStackRule)
        assertEquals(listOf(t), d.throttleRules)
    }

    @Test
    fun `throttle rule whose conditions don't hold is skipped`() {
        val t = throttle("News").copy(advancedConfig = daytime)
        assertTrue(RuleMatcher.planNotificationDecision(listOf(t), "p", "News", null, false, night).throttleRules.isEmpty())
        assertEquals(listOf(t), RuleMatcher.planNotificationDecision(listOf(t), "p", "News", null, false, noon).throttleRules)
    }

    @Test
    fun `shouldBlock ignores throttle rules`() {
        assertFalse(RuleMatcher.shouldBlock("p", "News", null, listOf(throttle("News"))))
        assertFalse(RuleMatcher.shouldBlock("p", "Other", null, listOf(throttle("News"))))
    }
}
