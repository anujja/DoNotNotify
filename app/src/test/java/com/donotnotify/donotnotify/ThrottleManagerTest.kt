package com.donotnotify.donotnotify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure planning for [ThrottleManager]. Which rules match is covered in [NotificationDecisionTest]. */
class ThrottleManagerTest {

    private fun throttle(seconds: Int? = null, textFilter: String? = null) = BlockerRule(
        packageName = "p",
        textFilter = textFilter,
        ruleType = RuleType.THROTTLE,
        advancedConfig = AdvancedRuleConfig(throttleSeconds = seconds)
    )

    // ---- secondsFor / windowsFor --------------------------------------------------

    @Test
    fun `unset or invalid length uses the default, long ones are capped`() {
        assertEquals(ThrottleManager.DEFAULT_SECONDS, ThrottleManager.secondsFor(null))
        assertEquals(ThrottleManager.DEFAULT_SECONDS, ThrottleManager.secondsFor(AdvancedRuleConfig()))
        assertEquals(ThrottleManager.DEFAULT_SECONDS, ThrottleManager.secondsFor(AdvancedRuleConfig(throttleSeconds = 0)))
        assertEquals(ThrottleManager.DEFAULT_SECONDS, ThrottleManager.secondsFor(AdvancedRuleConfig(throttleSeconds = -5)))
        assertEquals(20, ThrottleManager.secondsFor(AdvancedRuleConfig(throttleSeconds = 20)))
        assertEquals(
            ThrottleManager.MAX_SECONDS,
            ThrottleManager.secondsFor(AdvancedRuleConfig(throttleSeconds = Int.MAX_VALUE))
        )
    }

    @Test
    fun `each matched rule gets its own window`() {
        val a = throttle(seconds = 30)
        val b = throttle()
        assertEquals(
            listOf(
                ThrottleManager.ThrottleWindow("rule:${a.id}", 30),
                ThrottleManager.ThrottleWindow("rule:${b.id}", ThrottleManager.DEFAULT_SECONDS)
            ),
            ThrottleManager.windowsFor(listOf(a, b))
        )
        assertTrue(ThrottleManager.windowsFor(emptyList()).isEmpty())
    }

    // ---- shouldSuppress ---------------------------------------------------------

    @Test
    fun `no previous pass - never suppressed`() {
        assertFalse(ThrottleManager.shouldSuppress(null, 60, 1_000_000L))
    }

    @Test
    fun `inside window - suppressed`() {
        assertTrue(ThrottleManager.shouldSuppress(1_000L, 60, 30_000L))
    }

    @Test
    fun `window edge passes`() {
        // Exactly 60s after the pass the window has run out.
        assertFalse(ThrottleManager.shouldSuppress(1_000L, 60, 61_000L))
    }

    @Test
    fun `outside window - passes`() {
        assertFalse(ThrottleManager.shouldSuppress(1_000L, 60, 70_000L))
    }

    @Test
    fun `clock set back - passes`() {
        assertFalse(ThrottleManager.shouldSuppress(100_000L, 60, 50_000L))
    }

    // ---- evaluate ---------------------------------------------------------------

    @Test
    fun `a pass restarts every window`() {
        val windows = listOf(
            ThrottleManager.ThrottleWindow("rule:a", 60),
            ThrottleManager.ThrottleWindow("rule:b", 30)
        )
        val outcome = ThrottleManager.evaluate(windows, { null }, 1_000L)
        assertFalse(outcome.suppress)
        assertEquals(listOf("rule:a", "rule:b"), outcome.windowsToRecord)
    }

    @Test
    fun `any running window suppresses, and a suppression restarts nothing`() {
        val windows = listOf(
            ThrottleManager.ThrottleWindow("rule:a", 60),
            ThrottleManager.ThrottleWindow("rule:b", 30)
        )
        val lastPass = mapOf("rule:b" to 10_000L)
        val outcome = ThrottleManager.evaluate(windows, lastPass::get, 20_000L)
        assertTrue(outcome.suppress)
        assertTrue(outcome.windowsToRecord.isEmpty())
    }

    @Test
    fun `burst - first passes, repeats blocked until the window ends`() {
        val rule = throttle(seconds = 20)
        val store = mutableMapOf<String, Long>()
        fun post(at: Long): Boolean {
            val outcome = ThrottleManager.evaluate(ThrottleManager.windowsFor(listOf(rule)), store::get, at)
            outcome.windowsToRecord.forEach { store[it] = at }
            return !outcome.suppress
        }
        assertTrue(post(0L))
        assertFalse(post(5_000L))
        assertFalse(post(19_999L))
        assertTrue(post(20_000L))
        assertFalse(post(25_000L))
    }
}
