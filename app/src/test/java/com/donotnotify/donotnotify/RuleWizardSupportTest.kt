package com.donotnotify.donotnotify

import com.donotnotify.donotnotify.RuleWizardSupport.FilterCheck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleWizardSupportTest {

    // --- mergeKnownApps ---

    @Test
    fun `merge dedupes packages across sources`() {
        val result = RuleWizardSupport.mergeKnownApps(
            appInfoRows = listOf("com.a" to "App A"),
            historyRows = listOf("com.a" to "App A History", "com.b" to "App B"),
            queryableInstalled = mapOf("com.a" to "App A PM", "com.c" to "App C"),
            prebuiltNames = emptyMap(),
            ruleRows = listOf("com.b" to "App B Rule")
        )
        assertEquals(listOf("com.a", "com.b", "com.c"), result.map { it.packageName }.sorted())
    }

    @Test
    fun `prebuilt names fill labels but never add packages`() {
        val result = RuleWizardSupport.mergeKnownApps(
            appInfoRows = listOf("com.known" to null),
            historyRows = emptyList(),
            queryableInstalled = emptyMap(),
            prebuiltNames = mapOf("com.known" to "Known App", "com.notinstalled" to "Ghost App"),
            ruleRows = emptyList()
        )
        assertEquals(listOf("com.known"), result.map { it.packageName })
        assertEquals("Known App", result.single().appName)
    }

    @Test
    fun `label priority prefers appInfo over later sources`() {
        val result = RuleWizardSupport.mergeKnownApps(
            appInfoRows = listOf("com.a" to "From AppInfo"),
            historyRows = listOf("com.a" to "From History"),
            queryableInstalled = mapOf("com.a" to "From PM"),
            prebuiltNames = mapOf("com.a" to "From Prebuilt"),
            ruleRows = listOf("com.a" to "From Rule")
        )
        assertEquals("From AppInfo", result.single().appName)
    }

    @Test
    fun `blank label upgrades to first non-blank from a later source`() {
        val result = RuleWizardSupport.mergeKnownApps(
            appInfoRows = listOf("com.a" to null),
            historyRows = listOf("com.a" to ""),
            queryableInstalled = mapOf("com.a" to "From PM"),
            prebuiltNames = emptyMap(),
            ruleRows = emptyList()
        )
        assertEquals("From PM", result.single().appName)
    }

    @Test
    fun `label stays null when no source knows a name`() {
        val result = RuleWizardSupport.mergeKnownApps(
            appInfoRows = emptyList(),
            historyRows = listOf("com.a" to null),
            queryableInstalled = emptyMap(),
            prebuiltNames = emptyMap(),
            ruleRows = emptyList()
        )
        assertNull(result.single().appName)
    }

    @Test
    fun `queryable installed flag set only for PM-visible packages`() {
        val result = RuleWizardSupport.mergeKnownApps(
            appInfoRows = listOf("com.a" to "A", "com.b" to "B"),
            historyRows = emptyList(),
            queryableInstalled = mapOf("com.b" to "B"),
            prebuiltNames = emptyMap(),
            ruleRows = emptyList()
        )
        assertFalse(result.first { it.packageName == "com.a" }.isQueryableInstalled)
        assertTrue(result.first { it.packageName == "com.b" }.isQueryableInstalled)
    }

    @Test
    fun `sorted case-insensitively by name with package fallback`() {
        val result = RuleWizardSupport.mergeKnownApps(
            appInfoRows = listOf(
                "com.zebra" to "zebra",
                "com.apple" to "Apple",
                "com.banana" to null
            ),
            historyRows = emptyList(),
            queryableInstalled = emptyMap(),
            prebuiltNames = emptyMap(),
            ruleRows = emptyList()
        )
        // "Apple" < "com.banana" (package fallback) < "zebra", case-insensitive
        assertEquals(listOf("com.apple", "com.banana", "com.zebra"), result.map { it.packageName })
    }

    @Test
    fun `blank package names are dropped`() {
        val result = RuleWizardSupport.mergeKnownApps(
            appInfoRows = listOf("" to "Nameless"),
            historyRows = emptyList(),
            queryableInstalled = emptyMap(),
            prebuiltNames = emptyMap(),
            ruleRows = emptyList()
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `all sources empty yields empty list`() {
        val result = RuleWizardSupport.mergeKnownApps(
            emptyList(), emptyList(), emptyMap(), emptyMap(), emptyList()
        )
        assertTrue(result.isEmpty())
    }

    // --- checkFilters ---

    @Test
    fun `valid contains filters are ok`() {
        val check = RuleWizardSupport.checkFilters("Promo", MatchType.CONTAINS, "", MatchType.CONTAINS)
        assertEquals(FilterCheck.Ok, check)
    }

    @Test
    fun `valid regex filters are ok`() {
        val check = RuleWizardSupport.checkFilters("(?i)promo|sale", MatchType.REGEX, "", MatchType.CONTAINS)
        assertEquals(FilterCheck.Ok, check)
    }

    @Test
    fun `both blank filters match everything`() {
        val check = RuleWizardSupport.checkFilters("", MatchType.CONTAINS, "  ", MatchType.REGEX)
        assertEquals(FilterCheck.MatchesEverything, check)
    }

    @Test
    fun `invalid title regex flags title field only`() {
        val check = RuleWizardSupport.checkFilters("(unclosed", MatchType.REGEX, "ok", MatchType.CONTAINS)
        assertEquals(FilterCheck.InvalidRegex(titleInvalid = true, textInvalid = false), check)
    }

    @Test
    fun `invalid text regex flags text field only`() {
        val check = RuleWizardSupport.checkFilters("ok", MatchType.CONTAINS, "[unclosed", MatchType.REGEX)
        assertEquals(FilterCheck.InvalidRegex(titleInvalid = false, textInvalid = true), check)
    }

    @Test
    fun `both invalid regexes flag both fields`() {
        val check = RuleWizardSupport.checkFilters("(a", MatchType.REGEX, "[b", MatchType.REGEX)
        assertEquals(FilterCheck.InvalidRegex(titleInvalid = true, textInvalid = true), check)
    }

    @Test
    fun `invalid pattern under contains match is not an error`() {
        val check = RuleWizardSupport.checkFilters("(unclosed", MatchType.CONTAINS, "", MatchType.CONTAINS)
        assertEquals(FilterCheck.Ok, check)
    }

    @Test
    fun `blank regex field is not an error`() {
        val check = RuleWizardSupport.checkFilters("", MatchType.REGEX, "ok", MatchType.CONTAINS)
        assertEquals(FilterCheck.Ok, check)
    }

    // --- isDuplicate ---

    private val existing = listOf(
        BlockerRule(
            packageName = "com.a",
            titleFilter = "Promo",
            textFilter = null,
            ruleType = RuleType.DENYLIST
        )
    )

    @Test
    fun `exact same rule is a duplicate`() {
        assertTrue(RuleWizardSupport.isDuplicate(existing, "com.a", "Promo", null, RuleType.DENYLIST))
    }

    @Test
    fun `different filter is not a duplicate`() {
        assertFalse(RuleWizardSupport.isDuplicate(existing, "com.a", "Sale", null, RuleType.DENYLIST))
    }

    @Test
    fun `different package is not a duplicate`() {
        assertFalse(RuleWizardSupport.isDuplicate(existing, "com.b", "Promo", null, RuleType.DENYLIST))
    }

    @Test
    fun `same filters but different rule type is not a duplicate`() {
        assertFalse(RuleWizardSupport.isDuplicate(existing, "com.a", "Promo", null, RuleType.STACK))
    }

    // --- looksLikePackageName ---

    @Test
    fun `typical package name looks valid`() {
        assertTrue(RuleWizardSupport.looksLikePackageName("com.example.app"))
        assertTrue(RuleWizardSupport.looksLikePackageName("  com.example.app  "))
    }

    @Test
    fun `inputs without dots or with whitespace look invalid`() {
        assertFalse(RuleWizardSupport.looksLikePackageName(""))
        assertFalse(RuleWizardSupport.looksLikePackageName("myapp"))
        assertFalse(RuleWizardSupport.looksLikePackageName("com.example app"))
        assertFalse(RuleWizardSupport.looksLikePackageName(".com.example"))
        assertFalse(RuleWizardSupport.looksLikePackageName("com.example."))
    }
}
