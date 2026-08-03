package com.donotnotify.donotnotify

/**
 * An app the rule wizard can offer in its picker, merged from every source that
 * knows about a package (see [RuleWizardSupport.mergeKnownApps]).
 *
 * [isQueryableInstalled] means the package is visible to PackageManager (it is one
 * of the manifest `<queries>` entries and is installed), so a colorful launcher
 * icon and label can be loaded from it.
 */
data class KnownApp(
    val packageName: String,
    val appName: String?,
    val isQueryableInstalled: Boolean
)

/**
 * Pure, JVM-testable logic backing the rule-creation wizard: merging the "apps we
 * know about" list, validating filters, and duplicate detection. No Android imports —
 * the wizard screen gathers inputs and delegates here (same pattern as
 * [RuleMatcher.planNotificationDecision] and [StackedNotificationManager.planAbsorb]).
 */
object RuleWizardSupport {

    /**
     * Merges every source of known apps into one deduplicated, sorted picker list.
     *
     * Label priority (first non-blank wins): AppInfoStorage name, history appLabel,
     * PackageManager label, existing-rule appName, prebuilt-rules name. AppInfoStorage
     * wins because it stores the label resolved when the notification actually arrived;
     * PackageManager labels only exist for the few queryable packages.
     *
     * [prebuiltNames] is label-only: it never adds a package by itself, since the
     * prebuilt catalog lists apps the user may not have installed.
     */
    fun mergeKnownApps(
        appInfoRows: List<Pair<String, String?>>,
        historyRows: List<Pair<String, String?>>,
        queryableInstalled: Map<String, String?>,
        prebuiltNames: Map<String, String?>,
        ruleRows: List<Pair<String, String?>>
    ): List<KnownApp> {
        val labels = mutableMapOf<String, String?>()

        fun absorb(rows: Iterable<Pair<String, String?>>) {
            for ((pkg, name) in rows) {
                if (pkg.isBlank()) continue
                // Register the package either way; a null label upgrades to the
                // first non-blank name a later source provides.
                if (labels[pkg].isNullOrBlank()) {
                    labels[pkg] = name?.takeIf { it.isNotBlank() }
                }
            }
        }

        absorb(appInfoRows)
        absorb(historyRows)
        absorb(queryableInstalled.map { it.key to it.value })
        absorb(ruleRows)
        for ((pkg, name) in prebuiltNames) {
            if (pkg in labels && labels[pkg].isNullOrBlank()) {
                labels[pkg] = name?.takeIf { it.isNotBlank() }
            }
        }

        return labels.map { (pkg, name) ->
            KnownApp(
                packageName = pkg,
                appName = name,
                isQueryableInstalled = pkg in queryableInstalled
            )
        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.appName ?: it.packageName })
    }

    /** Result of validating the wizard's filter step. */
    sealed interface FilterCheck {
        /** Filters are usable as entered. */
        data object Ok : FilterCheck

        /** Both filters blank: the rule applies to every notification from the app. Warn, allow. */
        data object MatchesEverything : FilterCheck

        /** One or both REGEX filters fail to compile. Blocks advancing. */
        data class InvalidRegex(val titleInvalid: Boolean, val textInvalid: Boolean) : FilterCheck
    }

    fun checkFilters(
        titleFilter: String,
        titleMatchType: MatchType,
        textFilter: String,
        textMatchType: MatchType
    ): FilterCheck {
        val titleInvalid = isInvalidRegex(titleFilter, titleMatchType)
        val textInvalid = isInvalidRegex(textFilter, textMatchType)
        return when {
            titleInvalid || textInvalid -> FilterCheck.InvalidRegex(titleInvalid, textInvalid)
            titleFilter.isBlank() && textFilter.isBlank() -> FilterCheck.MatchesEverything
            else -> FilterCheck.Ok
        }
    }

    private fun isInvalidRegex(filter: String, matchType: MatchType): Boolean =
        matchType == MatchType.REGEX && filter.isNotBlank() && runCatching { filter.toRegex() }.isFailure

    /**
     * Same rule identity the prebuilt browser uses to hide already-added rules
     * (packageName + both filters), narrowed by ruleType since the wizard lets the
     * user pick it explicitly.
     */
    fun isDuplicate(
        existingRules: List<BlockerRule>,
        packageName: String,
        titleFilter: String?,
        textFilter: String?,
        ruleType: RuleType
    ): Boolean = existingRules.any {
        it.packageName == packageName &&
                it.titleFilter == titleFilter &&
                it.textFilter == textFilter &&
                it.ruleType == ruleType
    }

    /**
     * Loose sanity check for manually entered package names. Warn-only — some valid
     * packages are unusual, so the wizard never blocks on this.
     */
    fun looksLikePackageName(input: String): Boolean {
        val trimmed = input.trim()
        return trimmed.isNotBlank() &&
                trimmed.none { it.isWhitespace() } &&
                trimmed.contains('.') &&
                !trimmed.startsWith('.') &&
                !trimmed.endsWith('.')
    }
}
