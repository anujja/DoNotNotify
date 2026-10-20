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

    // --- previewFilters ---

    private fun n(title: String?, text: String?, at: Long, pkg: String = "com.a") =
        SimpleNotification(appLabel = "A", packageName = pkg, title = title, text = text, timestamp = at)

    private val history = listOf(
        n("Sale ends tonight", "50% off everything", at = 3),
        n("Your order shipped", "Arriving Friday", at = 2),
        n("Sale ends tonight", "50% off everything", at = 1),
        n("Flash sale", "Today only", at = 4, pkg = "com.other"),
    )

    @Test
    fun `preview counts the app's distinct recent notifications and finds the latest match`() {
        val p = RuleWizardSupport.previewFilters(history, "com.a", "sale", MatchType.CONTAINS, "", MatchType.CONTAINS)
        assertEquals(2, p.recentCount)
        assertEquals(1, p.matchCount)
        assertEquals(3L, p.latestMatch?.timestamp)
        assertEquals(RuleWizardSupport.PreviewField("Sale ends tonight", 0..3), p.latestMatch?.title)
        assertTrue(p.latestMatch!!.text.match.isEmpty())
    }

    @Test
    fun `preview with blank filters matches everything, and nothing when a filter misses`() {
        val all = RuleWizardSupport.previewFilters(history, "com.a", " ", MatchType.CONTAINS, "", MatchType.REGEX)
        assertEquals(2, all.matchCount)
        assertEquals(3L, all.latestMatch?.timestamp)

        val none = RuleWizardSupport.previewFilters(history, "com.a", "", MatchType.CONTAINS, "^Today", MatchType.REGEX)
        assertEquals(2, none.recentCount)
        assertEquals(0, none.matchCount)
        assertNull(none.latestMatch)
    }

    @Test
    fun `preview trims filters like the saved rule and shows text on one line`() {
        val h = listOf(n("Delivery", "Out for\ndelivery today", at = 1))
        val p = RuleWizardSupport.previewFilters(h, "com.a", "", MatchType.CONTAINS, "  delivery  ", MatchType.CONTAINS)
        assertEquals(RuleWizardSupport.PreviewField("Out for delivery today", 8..15), p.latestMatch?.text)
    }

    @Test
    fun `preview of an invalid regex matches nothing`() {
        val p = RuleWizardSupport.previewFilters(history, "com.a", "(unclosed", MatchType.REGEX, "", MatchType.CONTAINS)
        assertEquals(0, p.matchCount)
    }

    // --- snippetAround / exampleField ---

    @Test
    fun `snippet keeps an early match as is`() {
        val field = RuleWizardSupport.PreviewField("Your code is 123456", 13..18)
        assertEquals(field, RuleWizardSupport.snippetAround(field))
    }

    @Test
    fun `snippet cuts at a word start so a late match ends the line`() {
        val text = "Your verification code for signing in is 123456"
        val shown = RuleWizardSupport.snippetAround(RuleWizardSupport.PreviewField(text, 41..46))
        assertEquals("…code for signing in is 123456", shown.text)
        assertEquals("123456", shown.text.substring(shown.match.first, shown.match.last + 1))
    }

    @Test
    fun `snippet of a match longer than the line starts at the match`() {
        val text = "Note: " + "a".repeat(40)
        val shown = RuleWizardSupport.snippetAround(RuleWizardSupport.PreviewField(text, 6..45))
        assertEquals("…" + "a".repeat(40), shown.text)
        assertEquals(1..40, shown.match)
    }

    @Test
    fun `snippet of a long match from the start is not cut`() {
        val field = RuleWizardSupport.PreviewField("Flash sale: 50% off all sneakers, today only", 0..43)
        assertEquals(field, RuleWizardSupport.snippetAround(field))
    }

    @Test
    fun `example field highlights a filter it contains, else shows the filter`() {
        val example = "Your order has been delivered"
        assertEquals(
            RuleWizardSupport.PreviewField(example, 5..9),
            RuleWizardSupport.exampleField(example, "ORDER", MatchType.CONTAINS)
        )
        assertEquals(
            RuleWizardSupport.PreviewField("…promo…", 1..5),
            RuleWizardSupport.exampleField(example, " promo ", MatchType.CONTAINS)
        )
        assertEquals(
            RuleWizardSupport.PreviewField("\\d{6}", 0..4),
            RuleWizardSupport.exampleField(example, "\\d{6}", MatchType.REGEX)
        )
        assertTrue(RuleWizardSupport.exampleField(example, "", MatchType.REGEX).match.isEmpty())
    }
}
