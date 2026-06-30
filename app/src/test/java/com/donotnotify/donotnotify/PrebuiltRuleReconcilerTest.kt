package com.donotnotify.donotnotify

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure decision logic for disabling stale auto-installed prebuilt ALLOWLIST rules. */
class PrebuiltRuleReconcilerTest {

    private val MYGATE = "com.mygate.user"
    private val AMAZON = "com.amazon.mShop.android.shopping"

    private val englishMygate = BlockerRule(
        packageName = MYGATE,
        textFilter = ".*(checked|approv|declined|invite).*",
        textMatchType = MatchType.REGEX,
        ruleType = RuleType.ALLOWLIST
    )
    private val localizedMygate = englishMygate.copy(textFilter = ".*(確認|承認|拒否).*")
    private val amazonDeny = BlockerRule(
        packageName = AMAZON,
        textFilter = "(?i).*(offer|sale).*",
        textMatchType = MatchType.REGEX,
        ruleType = RuleType.DENYLIST
    )

    private val processed = setOf(MYGATE, AMAZON)
    private val english = listOf(englishMygate, amazonDeny)

    @Test
    fun `stale english allowlist is disabled when current locale differs`() {
        val current = listOf(localizedMygate, amazonDeny)
        assertTrue(
            PrebuiltRuleReconciler.isStaleAutoInstalledAllowlist(englishMygate, processed, english, current)
        )
    }

    @Test
    fun `allowlist that still matches current locale is not disabled`() {
        // Mygate keeps English tokens in every locale, so it is never stale.
        val current = listOf(englishMygate, amazonDeny)
        assertFalse(
            PrebuiltRuleReconciler.isStaleAutoInstalledAllowlist(englishMygate, processed, english, current)
        )
    }

    @Test
    fun `user-authored allowlist for same package is left alone`() {
        val userRule = englishMygate.copy(textFilter = ".*(my custom token).*")
        val current = listOf(localizedMygate, amazonDeny)
        assertFalse(
            PrebuiltRuleReconciler.isStaleAutoInstalledAllowlist(userRule, processed, english, current)
        )
    }

    @Test
    fun `denylist rule is never disabled`() {
        val current = listOf(localizedMygate, amazonDeny)
        assertFalse(
            PrebuiltRuleReconciler.isStaleAutoInstalledAllowlist(amazonDeny, processed, english, current)
        )
    }

    @Test
    fun `rule whose package was not auto-processed is left alone`() {
        val current = listOf(localizedMygate, amazonDeny)
        assertFalse(
            PrebuiltRuleReconciler.isStaleAutoInstalledAllowlist(englishMygate, emptySet(), english, current)
        )
    }

    @Test
    fun `already-disabled rule is ignored`() {
        val current = listOf(localizedMygate, amazonDeny)
        assertFalse(
            PrebuiltRuleReconciler.isStaleAutoInstalledAllowlist(
                englishMygate.copy(isEnabled = false), processed, english, current
            )
        )
    }
}
