package com.donotnotify.donotnotify

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
    fun `should block when blacklist rule matches title`() {
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
    fun `should not block when blacklist rule does not match`() {
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
    fun `should not block when whitelist rule matches`() {
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
    fun `should block when whitelist rule exists but does not match`() {
        val rule = BlockerRule(
            packageName = "com.example.app",
            titleFilter = "OTP",
            ruleType = RuleType.ALLOWLIST
        )
        val rules = listOf(rule)
        
        // Implicit block because whitelist exists but wasn't matched
        val shouldBlock = RuleMatcher.shouldBlock("com.example.app", "Promotional Content", "Text", rules)
        assertTrue(shouldBlock)
    }

    @Test
    fun `should block when both whitelist and blacklist match (Blacklist Priority)`() {
        val allowListRules = BlockerRule(
            packageName = "com.example.app",
            titleFilter = "Offer",
            ruleType = RuleType.ALLOWLIST
        )
        val denyListRule = BlockerRule(
            packageName = "com.example.app",
            textFilter = "Expired",
            ruleType = RuleType.DENYLIST
        )
        val rules = listOf(allowListRules, denyListRule)
        
        // Matches Whitelist ("Offer") AND Blacklist ("Expired")
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

}
