package com.donotnotify.donotnotify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    /** The share URL's query parameters, decoded. Values are percent-encoded, so `&`/`=` split safely. */
    private fun params(url: String): Map<String, String> =
        url.substringAfter('?').split('&').associate { pair ->
            val key = pair.substringBefore('=')
            val value = pair.substringAfter('=', missingDelimiterValue = "")
            URLDecoder.decode(key, "UTF-8") to URLDecoder.decode(value, "UTF-8")
        }

    // --- sharePayload: prefilled flow ---

    @Test
    fun `normal rule prefills the community repo's share form`() {
        val payload = CommunityShare.sharePayload(rule())
        assertTrue(payload.prefilled)
        assertTrue(payload.url.startsWith("${CommunityShare.REPO_URL}/issues/new?template=contribution.yml&"))
        assertTrue(payload.url.length <= CommunityShare.MAX_PREFILLED_URL_LENGTH)
    }

    @Test
    fun `no free-form body is sent, since issue forms ignore it`() {
        val payload = CommunityShare.sharePayload(rule())
        assertEquals(
            setOf("template", "title", CommunityShare.FIELD_APP, CommunityShare.FIELD_RULE_JSON),
            params(payload.url).keys
        )
    }

    @Test
    fun `title names the app`() {
        val payload = CommunityShare.sharePayload(rule())
        assertEquals("[Contribution] Flipkart", params(payload.url)["title"])
    }

    @Test
    fun `app field names the app and its package`() {
        val payload = CommunityShare.sharePayload(rule())
        assertEquals("Flipkart (`com.flipkart.android`)", params(payload.url)[CommunityShare.FIELD_APP])
    }

    @Test
    fun `rule-json field carries the payload json verbatim, which is an importable export envelope`() {
        val payload = CommunityShare.sharePayload(rule(), locale = "en-US")
        assertEquals(payload.json, params(payload.url)[CommunityShare.FIELD_RULE_JSON])
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
        for (content in listOf(payload.json) + params(payload.url).values) {
            assertFalse(content.contains("device-local-id"))
            assertFalse(content.contains("hitCount"))
            assertFalse(content.contains("12345"))
        }
    }

    @Test
    fun `blank appName falls back to packageName in title`() {
        val payload = CommunityShare.sharePayload(rule().copy(appName = " "))
        assertEquals("[Contribution] com.flipkart.android", params(payload.url)["title"])
    }

    @Test
    fun `non-ascii content survives the encode-decode round trip`() {
        val r = rule().copy(appName = "メルカリ", textFilter = "(?i).*(お得|割引|「セール」).*")
        val payload = CommunityShare.sharePayload(r, locale = "ja")
        assertTrue(payload.prefilled)
        assertEquals("[Contribution] メルカリ", params(payload.url)["title"])
        val json = params(payload.url).getValue(CommunityShare.FIELD_RULE_JSON)
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
        assertTrue(payload.url.length <= CommunityShare.MAX_PREFILLED_URL_LENGTH)
        // The form still opens with title and app filled in; only the JSON is left to paste.
        assertTrue(payload.url.startsWith(CommunityShare.CONTRIBUTION_ISSUE_URL))
        val params = params(payload.url)
        assertEquals("[Contribution] Flipkart", params["title"])
        assertEquals("Flipkart (`com.flipkart.android`)", params[CommunityShare.FIELD_APP])
        assertNull(params[CommunityShare.FIELD_RULE_JSON])
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

    // --- all-apps rules ---

    @Test
    fun `all-apps rule is titled and described as All apps, with no package`() {
        val payload = CommunityShare.sharePayload(rule().copy(appName = null, packageName = null))
        assertTrue(payload.prefilled)
        val params = params(payload.url)
        assertEquals("[Contribution] All apps", params["title"])
        assertEquals("All apps", params[CommunityShare.FIELD_APP])
    }
}
