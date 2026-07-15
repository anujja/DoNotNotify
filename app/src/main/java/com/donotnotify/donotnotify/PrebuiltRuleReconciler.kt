package com.donotnotify.donotnotify

import android.content.Context
import android.content.res.Configuration
import android.util.Log
import java.util.Locale

/**
 * Keeps auto-installed prebuilt rules safe across a device-language change.
 *
 * A prebuilt ALLOWLIST rule is **fail-closed**: it blocks every notification for its package
 * that does not match it (see [RuleMatcher.shouldBlock]). If such a rule was auto-installed in
 * one language and the device language later changes, its keywords can stop matching the now
 * differently-worded notifications — silently blocking legitimate ones. (DENYLIST/STACK rules
 * are fail-open and need no action.)
 *
 * This reconciler runs on the notification-evaluation path (and at process start), so the fix
 * applies even when the UI is never opened. It is cheap and idempotent: when the locale is
 * unchanged it is a single SharedPreferences read + string compare.
 */
object PrebuiltRuleReconciler {
    private const val TAG = "PrebuiltRuleReconciler"
    private const val PREFS = "settings"
    private const val KEY_LOCALE = "prebuilt_rules_locale"
    private const val KEY_PROCESSED = "processed_packages"

    fun currentLocaleTag(context: Context): String =
        context.resources.configuration.locales[0].toLanguageTag()

    /** Records the current locale as the one prebuilt rules were installed under. */
    fun stampCurrentLocale(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LOCALE, currentLocaleTag(context))
            .apply()
    }

    /**
     * If the resource locale differs from the stamp (or the stamp is missing — the legacy
     * upgrade case), disable any stale auto-installed prebuilt ALLOWLIST rules, then update
     * the stamp. Safe to call from multiple entry points.
     */
    @Synchronized
    fun reconcileIfLocaleChanged(context: Context) {
        val appContext = context.applicationContext ?: context
        val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val currentTag = currentLocaleTag(appContext)
        if (prefs.getString(KEY_LOCALE, null) == currentTag) return // fast path: unchanged

        try {
            val processed = prefs.getStringSet(KEY_PROCESSED, emptySet()) ?: emptySet()
            if (processed.isNotEmpty()) {
                disableStaleAllowlists(appContext, processed)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Reconciliation failed", e)
        } finally {
            prefs.edit().putString(KEY_LOCALE, currentTag).apply()
        }
    }

    private fun disableStaleAllowlists(context: Context, processed: Set<String>) {
        val englishPrebuilt = loadPrebuiltForLocale(context, Locale.ENGLISH)
        val currentPrebuilt = PrebuiltRulesRepository(context).getPrebuiltRules()

        val storage = RuleStorage(context)
        val staleIds = storage.getRules()
            .filter { isStaleAutoInstalledAllowlist(it, processed, englishPrebuilt, currentPrebuilt) }
            .map { it.id }
            .toSet()
        if (staleIds.isNotEmpty()) {
            storage.setEnabledByIds(staleIds, enabled = false)
            Log.i(TAG, "Disabled stale prebuilt ALLOWLIST rule(s) after locale change")
        }
    }

    /**
     * Pure staleness decision (no Android types → JVM-testable). True only for a rule that is
     * genuinely the auto-installed prebuilt ALLOWLIST (matches a canonical English prebuilt
     * signature) AND is now stale (differs from every current-locale prebuilt rule for its
     * package). User-authored allowlists won't match the English signature and are left alone.
     */
    internal fun isStaleAutoInstalledAllowlist(
        rule: BlockerRule,
        processed: Set<String>,
        englishPrebuilt: List<BlockerRule>,
        currentPrebuilt: List<BlockerRule>
    ): Boolean {
        if (rule.ruleType != RuleType.ALLOWLIST || !rule.isEnabled) return false
        val pkg = rule.packageName ?: return false
        if (pkg !in processed) return false
        val sig = signatureOf(rule)
        val englishAllowlistSigs = englishPrebuilt
            .filter { it.ruleType == RuleType.ALLOWLIST }
            .mapTo(HashSet()) { signatureOf(it) }
        if (sig !in englishAllowlistSigs) return false
        // Already the correct localized rule for this package? Then it isn't stale.
        val currentSigsForPkg = currentPrebuilt
            .filter { it.packageName == pkg }
            .map { signatureOf(it) }
        return sig !in currentSigsForPkg
    }

    private fun loadPrebuiltForLocale(context: Context, locale: Locale): List<BlockerRule> {
        val config = Configuration(context.resources.configuration).apply { setLocale(locale) }
        val localizedContext = context.createConfigurationContext(config)
        return PrebuiltRulesRepository(localizedContext).getPrebuiltRules()
    }

    private data class Signature(
        val packageName: String?,
        val titleFilter: String?,
        val titleMatchType: MatchType,
        val textFilter: String?,
        val textMatchType: MatchType
    )

    private fun signatureOf(r: BlockerRule) = Signature(
        r.packageName, r.titleFilter, r.titleMatchType, r.textFilter, r.textMatchType
    )
}
