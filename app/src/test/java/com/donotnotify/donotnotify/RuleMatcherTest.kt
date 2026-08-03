package com.donotnotify.donotnotify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleMatcherTest {

    @Test
    fun `should not block when no rules exist`() {
        val rules = emptyList<BlockerRule>()
        val shouldBlock = RuleMatcher.shouldBlock("com.example.app", "Title", "Text", rules)
        assertFalse(shouldBlock)
    }

    @Test
    fun `should block when denylist rule matches title`() {
        val rule = BlockerRule(
            packageName = "com.example.app",
            titleFilter = "Promo",
            ruleType = RuleType.DENYLIST
        )
        val rules = listOf(rule)

        val shouldBlock = RuleMatcher.shouldBlock("com.example.app", "This is a Promo", "Text", rules)
        assertTrue(shouldBlock)
    }

    @Test
    fun `should not block when denylist rule does not match`() {
        val rule = BlockerRule(
            packageName = "com.example.app",
            titleFilter = "Promo",
            ruleType = RuleType.DENYLIST
        )
        val rules = listOf(rule)

        val shouldBlock = RuleMatcher.shouldBlock("com.example.app", "Important Update", "Text", rules)
        assertFalse(shouldBlock)
    }

    @Test
    fun `should not block when allowlist rule matches`() {
        val rule = BlockerRule(
            packageName = "com.example.app",
            titleFilter = "OTP",
            ruleType = RuleType.ALLOWLIST
        )
        val rules = listOf(rule)

        val shouldBlock = RuleMatcher.shouldBlock("com.example.app", "Your OTP is 1234", "Text", rules)
        assertFalse(shouldBlock)
    }

    @Test
    fun `should block when allowlist rule exists but does not match`() {
        val rule = BlockerRule(
            packageName = "com.example.app",
            titleFilter = "OTP",
            ruleType = RuleType.ALLOWLIST
        )
        val rules = listOf(rule)

        // Implicit block because allowlist exists but wasn't matched
        val shouldBlock = RuleMatcher.shouldBlock("com.example.app", "Promotional Content", "Text", rules)
        assertTrue(shouldBlock)
    }

    @Test
    fun `should block when both allowlist and denylist match (Denylist Priority)`() {
        val allowlistRule = BlockerRule(
            packageName = "com.example.app",
            titleFilter = "Offer",
            ruleType = RuleType.ALLOWLIST
        )
        val denylistRule = BlockerRule(
            packageName = "com.example.app",
            textFilter = "Expired",
            ruleType = RuleType.DENYLIST
        )
        val rules = listOf(allowlistRule, denylistRule)

        // Matches Allowlist ("Offer") AND Denylist ("Expired")
        val shouldBlock = RuleMatcher.shouldBlock("com.example.app", "Special Offer", "This offer has Expired", rules)
        assertTrue(shouldBlock)
    }

    @Test
    fun `should handle regex matching`() {
        val rule = BlockerRule(
            packageName = "com.example.app",
            titleFilter = "^[0-9]+$", // Regex for only numbers
            titleMatchType = MatchType.REGEX,
            ruleType = RuleType.DENYLIST
        )
        val rules = listOf(rule)

        assertTrue(RuleMatcher.shouldBlock("com.example.app", "123456", "Text", rules))
        assertFalse(RuleMatcher.shouldBlock("com.example.app", "123abc456", "Text", rules))
    }

    @Test
    fun `should ignore disabled rules`() {
        val rule = BlockerRule(
            packageName = "com.example.app",
            titleFilter = "Promo",
            ruleType = RuleType.DENYLIST,
            isEnabled = false
        )
        val rules = listOf(rule)

        assertFalse(RuleMatcher.shouldBlock("com.example.app", "Promo Code", "Text", rules))
    }

    @Test
    fun `stack rule alone never blocks`() {
        val rule = BlockerRule(
            packageName = "com.example.app",
            titleFilter = "News",
            ruleType = RuleType.STACK
        )
        assertFalse(RuleMatcher.shouldBlock("com.example.app", "News flash", "Text", listOf(rule)))
    }

    @Test
    fun `stack rule does not gate like an allowlist`() {
        // Only a STACK rule present; a non-matching notification must NOT be blocked.
        val rule = BlockerRule(
            packageName = "com.example.app",
            titleFilter = "News",
            ruleType = RuleType.STACK
        )
        assertFalse(RuleMatcher.shouldBlock("com.example.app", "Unrelated", "Text", listOf(rule)))
    }

    @Test
    fun `stack and denylist with identical filters match identically`() {
        val stack = BlockerRule(packageName = "p", titleFilter = "Promo", ruleType = RuleType.STACK)
        val deny = BlockerRule(packageName = "p", titleFilter = "Promo", ruleType = RuleType.DENYLIST)
        assertTrue(RuleMatcher.matches(stack, "p", "Big Promo", "x"))
        assertEquals(
            RuleMatcher.matches(deny, "p", "Big Promo", "x"),
            RuleMatcher.matches(stack, "p", "Big Promo", "x")
        )
        assertEquals(
            RuleMatcher.matches(deny, "p", "Nope", "x"),
            RuleMatcher.matches(stack, "p", "Nope", "x")
        )
    }

    @Test
    fun `mygate test with regex`() {
        val rule = BlockerRule(
            packageName = "com.mygate.app",
            textFilter = ".*(checked|approval).*",
            textMatchType = MatchType.REGEX,
            ruleType = RuleType.ALLOWLIST,
        )
        val rules = listOf(rule)

        assertFalse(RuleMatcher.shouldBlock("com.mygate.app",
            "Delivery - Tower 20",
            "XYZ has checked in to your society",
            rules))
    }

    @Test
    fun `regex matches single-line title with anchored pattern`() {
        val rule = BlockerRule(
            packageName = "com.example.app",
            titleFilter = "^-.*",
            titleMatchType = MatchType.REGEX,
            ruleType = RuleType.DENYLIST
        )
        assertTrue(RuleMatcher.shouldBlock("com.example.app", "-taskname", "Text", listOf(rule)))
    }

    @Test
    fun `regex matches multiline title with anchored pattern`() {
        val rule = BlockerRule(
            packageName = "com.example.app",
            titleFilter = "^-.*",
            titleMatchType = MatchType.REGEX,
            ruleType = RuleType.DENYLIST
        )
        assertTrue(RuleMatcher.shouldBlock("com.example.app", "-taskname\nToday, 5pm", "Text", listOf(rule)))
    }

    @Test
    fun `regex does not match when hyphen is not at start of multiline title`() {
        val rule = BlockerRule(
            packageName = "com.example.app",
            titleFilter = "^-.*",
            titleMatchType = MatchType.REGEX,
            ruleType = RuleType.DENYLIST
        )
        assertFalse(RuleMatcher.shouldBlock("com.example.app", "Header\n-taskname", "Text", listOf(rule)))
    }

    @Test
    fun `regex matches title substring with unanchored pattern`() {
        val rule = BlockerRule(
            packageName = "com.example.app",
            titleFilter = "Promo",
            titleMatchType = MatchType.REGEX,
            ruleType = RuleType.DENYLIST
        )
        val rules = listOf(rule)
        assertTrue(RuleMatcher.shouldBlock("com.example.app", "Big Promo Deal", "Text", rules))
        assertFalse(RuleMatcher.shouldBlock("com.example.app", "Sale today", "Text", rules))
    }

    @Test
    fun `regex matches text substring with unanchored pattern`() {
        val rule = BlockerRule(
            packageName = "com.example.app",
            textFilter = "checked",
            textMatchType = MatchType.REGEX,
            ruleType = RuleType.DENYLIST
        )
        assertTrue(RuleMatcher.shouldBlock("com.example.app", "Title", "XYZ has checked in", listOf(rule)))
    }

    @Test
    fun `regex matches start-only anchored pattern`() {
        val rule = BlockerRule(
            packageName = "com.example.app",
            titleFilter = "^Promo",
            titleMatchType = MatchType.REGEX,
            ruleType = RuleType.DENYLIST
        )
        val rules = listOf(rule)
        assertTrue(RuleMatcher.shouldBlock("com.example.app", "Promo Deal", "Text", rules))
        assertFalse(RuleMatcher.shouldBlock("com.example.app", "Sale Promo", "Text", rules))
    }

    @Test
    fun `regex matches end-only anchored pattern`() {
        val rule = BlockerRule(
            packageName = "com.example.app",
            titleFilter = "[0-9]+$",
            titleMatchType = MatchType.REGEX,
            ruleType = RuleType.DENYLIST
        )
        val rules = listOf(rule)
        assertTrue(RuleMatcher.shouldBlock("com.example.app", "OTP 1234", "Text", rules))
        assertFalse(RuleMatcher.shouldBlock("com.example.app", "1234 OTP", "Text", rules))
    }

    @Test
    fun `regex end-anchor matches field with trailing newline`() {
        val ruleLoose = BlockerRule(
            packageName = "com.example.app",
            titleFilter = "^foo$",
            titleMatchType = MatchType.REGEX,
            ruleType = RuleType.DENYLIST
        )
        val ruleStrict = BlockerRule(
            packageName = "com.example.app",
            titleFilter = "\\Afoo\\z",
            titleMatchType = MatchType.REGEX,
            ruleType = RuleType.DENYLIST
        )
        // ^foo$ matches before the trailing \n (Java $ semantics)
        assertTrue(RuleMatcher.shouldBlock("com.example.app", "foo\n", "Text", listOf(ruleLoose)))
        // \A...\z is strict whole-input: does not match trailing \n
        assertFalse(RuleMatcher.shouldBlock("com.example.app", "foo\n", "Text", listOf(ruleStrict)))
    }

    @Test
    fun `regex start-anchor matches title wrapped in bidi isolate chars`() {
        // Issue #19: Google Calendar wraps titles in invisible U+2068/U+2069
        // (FIRST STRONG ISOLATE / POP DIRECTIONAL ISOLATE), so "^-" never
        // anchored at the visible hyphen.
        val rule = BlockerRule(
            packageName = "com.example.app",
            titleFilter = "^-.*",
            titleMatchType = MatchType.REGEX,
            ruleType = RuleType.DENYLIST
        )
        assertTrue(RuleMatcher.shouldBlock("com.example.app", "\u2068-example\u2069", null, listOf(rule)))
    }

    @Test
    fun `regex start-anchor matches title prefixed with left-to-right mark`() {
        val rule = BlockerRule(
            packageName = "com.example.app",
            titleFilter = "^-.*",
            titleMatchType = MatchType.REGEX,
            ruleType = RuleType.DENYLIST
        )
        assertTrue(RuleMatcher.shouldBlock("com.example.app", "\u200E-example", null, listOf(rule)))
    }

    @Test
    fun `regex end-anchor matches title with trailing bidi isolate char`() {
        val rule = BlockerRule(
            packageName = "com.example.app",
            titleFilter = "[0-9]+$",
            titleMatchType = MatchType.REGEX,
            ruleType = RuleType.DENYLIST
        )
        assertTrue(RuleMatcher.shouldBlock("com.example.app", "\u2068OTP 1234\u2069", null, listOf(rule)))
    }

    @Test
    fun `contains filter with pasted bidi chars matches clean title`() {
        val rule = BlockerRule(
            packageName = "com.example.app",
            titleFilter = "\u2068Promo",
            titleMatchType = MatchType.CONTAINS,
            ruleType = RuleType.DENYLIST
        )
        assertTrue(RuleMatcher.shouldBlock("com.example.app", "big promo deal", null, listOf(rule)))
    }

    @Test
    fun `regex stack rule matches with regex pattern via planNotificationDecision`() {
        val rule = BlockerRule(
            packageName = "com.example.app",
            titleFilter = "^-.*",
            titleMatchType = MatchType.REGEX,
            ruleType = RuleType.STACK
        )
        val decision = RuleMatcher.planNotificationDecision(
            rules = listOf(rule),
            packageName = "com.example.app",
            title = "-taskname\nToday",
            text = "Text",
            wasOngoing = false
        )
        assertTrue(decision.shouldStack)
        assertEquals(listOf(0), decision.matchedRuleIndices)
    }

}
