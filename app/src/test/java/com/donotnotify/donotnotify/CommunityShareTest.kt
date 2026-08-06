package com.donotnotify.donotnotify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder

class CommunityShareTest {

    private fun rule(hitCount: Int = 99) = BlockerRule(
        appName = "Flipkart",
        packageName = "com.flipkart.android",
        textFilter = "(?i).*(offer|sale).*",
        textMatchType = MatchType.REGEX,
        ruleType = RuleType.DENYLIST,
        hitCount = hitCount,
        id = "device-local-id"
    )

    private fun decodedBody(url: String): String {
        val body = url.substringAfter("&body=")
        return URLDecoder.decode(body, "UTF-8")
    }

    // --- sharePayload: prefilled flow ---

    @Test
    fun `normal rule prefills the community repo new-issue page`() {
        val payload = CommunityShare.sharePayload(rule())
        assertTrue(payload.prefilled)
        assertTrue(payload.url.startsWith("${CommunityShare.REPO_URL}/issues/new?title="))
        assertTrue(payload.url.length <= CommunityShare.MAX_PREFILLED_URL_LENGTH)
    }

    @Test
    fun `title names the app`() {
        val payload = CommunityShare.sharePayload(rule())
        val title = payload.url.substringAfter("?title=").substringBefore("&body=")
        assertEquals("[Contribution] Flipkart", URLDecoder.decode(title, "UTF-8"))
    }

    @Test
    fun `url body embeds the payload json, which is an importable export envelope`() {
        val payload = CommunityShare.sharePayload(rule(), locale = "en-US")
        val body = decodedBody(payload.url)
        assertTrue(body.contains(payload.json))
        val result = RuleImport.parse(payload.json)
        assertTrue(result is ImportResult.Success)
        val imported = (result as ImportResult.Success).rules.single()
        assertEquals("com.flipkart.android", imported.packageName)
        assertEquals("(?i).*(offer|sale).*", imported.textFilter)
        assertEquals("en-US", result.locale)
    }

    @Test
    fun `device-local id and hitCount never leave the device`() {
        val payload = CommunityShare.sharePayload(rule(hitCount = 12345))
        for (content in listOf(payload.json, decodedBody(payload.url))) {
            assertFalse(content.contains("device-local-id"))
            assertFalse(content.contains("hitCount"))
            assertFalse(content.contains("12345"))
        }
    }

    @Test
    fun `blank appName falls back to packageName in title`() {
        val payload = CommunityShare.sharePayload(rule().copy(appName = " "))
        val title = URLDecoder.decode(payload.url.substringAfter("?title=").substringBefore("&body="), "UTF-8")
        assertEquals("[Contribution] com.flipkart.android", title)
    }

    @Test
    fun `non-ascii content survives the encode-decode round trip`() {
        val r = rule().copy(appName = "メルカリ", textFilter = "(?i).*(お得|割引|「セール」).*")
        val payload = CommunityShare.sharePayload(r, locale = "ja")
        assertTrue(payload.prefilled)
        val title = URLDecoder.decode(payload.url.substringAfter("?title=").substringBefore("&body="), "UTF-8")
        assertEquals("[Contribution] メルカリ", title)
        val json = decodedBody(payload.url).substringAfter("```json\n").substringBefore("```")
        val imported = (RuleImport.parse(json) as ImportResult.Success).rules.single()
        assertEquals("(?i).*(お得|割引|「セール」).*", imported.textFilter)
    }

    // --- sharePayload: oversized (manual) flow ---

    @Test
    fun `oversized rule degrades to the manual flow but still carries the json`() {
        val huge = rule().copy(
            titleFilter = "a".repeat(4000),
            titleMatchType = MatchType.CONTAINS,
            textFilter = "b".repeat(4000),
            textMatchType = MatchType.CONTAINS
        )
        val payload = CommunityShare.sharePayload(huge)
        assertFalse(payload.prefilled)
        assertEquals(CommunityShare.NEW_ISSUE_URL, payload.url)
        // The payload still carries the full rule for the clipboard path.
        val imported = (RuleImport.parse(payload.json) as ImportResult.Success).rules.single()
        assertEquals("a".repeat(4000), imported.titleFilter)
    }

    // --- isNudgeEligible ---

    private val noPrebuilt = emptySet<CommunityShare.NudgeSignature>()

    @Test
    fun `nudges exactly at the hit threshold, not below`() {
        val threshold = CommunityShare.NUDGE_HIT_THRESHOLD
        assertFalse(CommunityShare.isNudgeEligible(rule(hitCount = threshold - 1), emptySet(), noPrebuilt))
        assertTrue(CommunityShare.isNudgeEligible(rule(hitCount = threshold), emptySet(), noPrebuilt))
    }

    @Test
    fun `dismissed rules never nudge again`() {
        assertFalse(CommunityShare.isNudgeEligible(rule(), setOf("device-local-id"), noPrebuilt))
        assertTrue(CommunityShare.isNudgeEligible(rule(), setOf("some-other-id"), noPrebuilt))
    }

    @Test
    fun `never nudges while the prebuilt catalog is still unknown`() {
        assertFalse(CommunityShare.isNudgeEligible(rule(), emptySet(), prebuiltSignatures = null))
    }

    @Test
    fun `prebuilt rules never nudge`() {
        val prebuilt = rule(hitCount = 0)
        val sigs = setOf(CommunityShare.signatureOf(prebuilt))
        assertFalse(CommunityShare.isNudgeEligible(rule(), emptySet(), sigs))
    }

    @Test
    fun `same filters but different match type is a user rule and nudges`() {
        val sigs = setOf(CommunityShare.signatureOf(rule()))
        val userVariant = rule().copy(textMatchType = MatchType.CONTAINS)
        assertTrue(CommunityShare.isNudgeEligible(userVariant, emptySet(), sigs))
    }

    @Test
    fun `same filters but different rule type is a user rule and nudges`() {
        val sigs = setOf(CommunityShare.signatureOf(rule()))
        val userVariant = rule().copy(ruleType = RuleType.ALLOWLIST)
        assertTrue(CommunityShare.isNudgeEligible(userVariant, emptySet(), sigs))
    }
}
