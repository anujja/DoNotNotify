package com.donotnotify.donotnotify

import java.net.URLEncoder

/**
 * Links into the community rules repository (github.com/anujja/DoNotNotify-rules).
 *
 * Contribution stays zero-network: everything here builds a URL that is handed to the
 * browser via ACTION_VIEW. Because a prefilled-issue URL reaches GitHub (and browser
 * history) the moment the browser opens, callers MUST show the user the payload first —
 * the share flow presents an in-app preview dialog of [contributionJson] and only opens
 * the browser on explicit confirmation. Nothing is published until the user additionally
 * submits the issue on GitHub.
 *
 * Pure JVM (no Android types) so URL construction and nudge eligibility are
 * unit-testable, like [RuleImport].
 */
object CommunityShare {
    const val REPO_URL = "https://github.com/anujja/DoNotNotify-rules"
    const val RULE_REQUEST_URL = "$REPO_URL/issues/new?template=rule-request.yml"
    const val NEW_ISSUE_URL = "$REPO_URL/issues/new"

    /**
     * The rules repo's "Share a rule" issue form (`.github/ISSUE_TEMPLATE/contribution.yml`).
     * The form labels the issue `contribution` (a label in the URL would be ignored for
     * contributors without triage access), and GitHub prefills its fields from query
     * parameters named by field id: [FIELD_APP], [FIELD_RULE_JSON]. The form's "What it
     * blocks" / "What it keeps" fields are left for the user to write.
     */
    const val CONTRIBUTION_ISSUE_URL = "$NEW_ISSUE_URL?template=contribution.yml"
    const val FIELD_APP = "app"
    const val FIELD_RULE_JSON = "rule-json"

    /**
     * A user-authored rule with at least this many hits has proven itself; that is the
     * moment we invite the user to share it.
     */
    const val NUDGE_HIT_THRESHOLD = 25

    /** SharedPreferences (file "settings") string-set of rule ids whose nudge was dismissed. */
    const val PREF_NUDGE_DISMISSED = "community_nudge_dismissed"

    /**
     * GitHub returns 414 for new-issue URLs beyond ~8k chars; past this margin
     * [contributionIssueUrl] degrades to the plain new-issue page (the preview dialog's
     * "Copy JSON" affordance carries the payload instead).
     */
    const val MAX_PREFILLED_URL_LENGTH = 7000

    /** How an all-apps rule names its app in the (English) issue. */
    const val ALL_APPS_LABEL = "All apps"

    /**
     * Behavior-defining identity used to recognize prebuilt-catalog rules (which are
     * already community-shared and must never nudge). All fields that change what a rule
     * *does* participate, so a user rule that reuses a catalog rule's filters with a
     * different match or rule type still counts as user-authored.
     */
    data class NudgeSignature(
        val packageName: String?,
        val titleFilter: String?,
        val titleMatchType: MatchType,
        val textFilter: String?,
        val textMatchType: MatchType,
        val ruleType: RuleType
    )

    fun signatureOf(rule: BlockerRule) = NudgeSignature(
        rule.packageName,
        rule.titleFilter,
        rule.titleMatchType,
        rule.textFilter,
        rule.textMatchType,
        rule.ruleType
    )

    /**
     * Whether a rule card should show the "share with the community" banner.
     *
     * Invariant: the banner is persistent-until-acted-upon — it stays across visits until
     * the user shares or explicitly dismisses (recorded per rule id in
     * [PREF_NUDGE_DISMISSED]), deliberately not impression-counted.
     *
     * [prebuiltSignatures] is null while the prebuilt catalog is still loading; nothing
     * nudges until it is known (fail quiet, never nudge a possibly-prebuilt rule).
     */
    fun isNudgeEligible(
        rule: BlockerRule,
        dismissedIds: Set<String>,
        prebuiltSignatures: Set<NudgeSignature>?
    ): Boolean {
        if (prebuiltSignatures == null) return false
        return rule.hitCount >= NUDGE_HIT_THRESHOLD &&
                rule.id !in dismissedIds &&
                signatureOf(rule) !in prebuiltSignatures
    }

    /**
     * The rule as a standard export envelope ([RuleExportSerializer] — `id` and
     * `hitCount` never leave the device). Shown verbatim in the preview dialog and
     * embedded in the prefilled issue body.
     */
    fun contributionJson(rule: BlockerRule, locale: String? = null): String =
        RuleExportSerializer.toJson(RuleExport(locale = locale, rules = listOf(rule)))

    /**
     * Everything the share dialog needs. [prefilled] tells the UI which flow it is:
     * true — [url] opens the share form with title, app and [json] filled in, and opening
     * it is the entire hand-off; false — the rule was too large for a URL, [url] opens the
     * form with only title and app filled in, and the UI must put [json] on the clipboard
     * so the user can paste it into the form's Rule JSON field.
     */
    data class SharePayload(val url: String, val prefilled: Boolean, val json: String)

    fun sharePayload(rule: BlockerRule, locale: String? = null): SharePayload {
        val json = contributionJson(rule, locale)
        // The issue is written in English, like the rest of the form.
        val appLabel = when {
            rule.isAllApps -> ALL_APPS_LABEL
            else -> rule.appName?.takeIf { it.isNotBlank() } ?: rule.packageName.orEmpty()
        }
        val app = if (rule.isAllApps) appLabel else "$appLabel (`${rule.packageName.orEmpty()}`)"
        val formUrl = "$CONTRIBUTION_ISSUE_URL&title=${encode("[Contribution] $appLabel")}" +
                "&$FIELD_APP=${encode(app)}"
        // An app label can't realistically blow the cap, but never hand GitHub a URL it 414s.
        val manualUrl = if (formUrl.length <= MAX_PREFILLED_URL_LENGTH) formUrl else CONTRIBUTION_ISSUE_URL
        // Percent-encoding never shrinks its input, so an over-cap raw JSON can skip
        // encoding entirely — the encoding work below is bounded by the cap.
        if (formUrl.length + json.length > MAX_PREFILLED_URL_LENGTH) {
            return SharePayload(manualUrl, prefilled = false, json = json)
        }
        val url = "$formUrl&$FIELD_RULE_JSON=${encode(json)}"
        return if (url.length > MAX_PREFILLED_URL_LENGTH) {
            SharePayload(manualUrl, prefilled = false, json = json)
        } else {
            SharePayload(url, prefilled = true, json = json)
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
