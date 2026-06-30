package com.donotnotify.donotnotify

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/** Current on-disk schema version for exported rule files. */
const val RULE_EXPORT_VERSION = 2

/**
 * Envelope written by the export feature. Backward compatible: older app versions
 * exported a bare JSON array of [BlockerRule]; [RuleImport.parse] accepts both forms.
 */
data class RuleExport(
    val version: Int = RULE_EXPORT_VERSION,
    val locale: String? = null,
    val rules: List<BlockerRule> = emptyList()
)

enum class ImportError { TooLarge, Malformed, SchemaMismatch, Empty }

sealed interface ImportResult {
    /** [droppedCount] = rules silently discarded by sanitization (nulls, empties, unsafe regex). */
    data class Success(
        val rules: List<BlockerRule>,
        val locale: String?,
        val droppedCount: Int
    ) : ImportResult

    data class Error(val reason: ImportError) : ImportResult
}

/**
 * Pure, Android-independent parser + sanitizer for imported rule files. Kept free of
 * Android types so it is directly unit-testable on the JVM. The Compose import callback
 * is responsible only for SAF I/O, the document size cap, and mapping results to strings.
 */
object RuleImport {
    /**
     * Upper bound on an imported REGEX pattern's length. Imported rules may originate from
     * shared/third-party files and are re-evaluated by the listener on every notification,
     * so we bound worst-case backtracking cost; over-length patterns are dropped.
     */
    const val MAX_REGEX_LENGTH = 512

    private val gson = Gson()
    private val listType = object : TypeToken<List<BlockerRule>>() {}.type

    fun parse(json: String): ImportResult {
        val root = try {
            JsonParser.parseString(json)
        } catch (e: Exception) {
            return ImportResult.Error(ImportError.Malformed)
        }

        val rawRules: List<BlockerRule?>
        val locale: String?
        when {
            root.isJsonArray -> {
                rawRules = try {
                    gson.fromJson(root, listType) ?: emptyList()
                } catch (e: JsonSyntaxException) {
                    return ImportResult.Error(ImportError.SchemaMismatch)
                }
                locale = null
            }
            root.isJsonObject -> {
                val obj = root.asJsonObject
                val rulesEl = obj.get("rules")
                if (rulesEl == null || !rulesEl.isJsonArray) {
                    return ImportResult.Error(ImportError.SchemaMismatch)
                }
                rawRules = try {
                    gson.fromJson(rulesEl, listType) ?: emptyList()
                } catch (e: JsonSyntaxException) {
                    return ImportResult.Error(ImportError.SchemaMismatch)
                }
                // `version` is advisory: a schema designed to be additive is best-effort
                // parsed for any version rather than hard-rejected (forward compatibility).
                locale = obj.get("locale")
                    ?.takeIf { it.isJsonPrimitive }
                    ?.asString
            }
            else -> return ImportResult.Error(ImportError.SchemaMismatch)
        }

        var dropped = 0
        val sanitized = ArrayList<BlockerRule>(rawRules.size)
        for (raw in rawRules) {
            val normalized = if (raw == null) null else normalize(raw)
            if (normalized == null) dropped++ else sanitized.add(normalized)
        }

        return when {
            sanitized.isNotEmpty() -> ImportResult.Success(sanitized, locale, dropped)
            rawRules.isEmpty() -> ImportResult.Error(ImportError.Empty)
            // Had content, but every rule was invalid: surface as a (0-rule) success
            // carrying the dropped count so the UI can tell the user nothing was added.
            else -> ImportResult.Success(emptyList(), locale, dropped)
        }
    }

    /** Returns a normalized, sanitized rule, or null if it should be dropped. */
    private fun normalize(raw: BlockerRule): BlockerRule? {
        // Gson maps unknown enum constants to null even for non-null Kotlin fields; coerce
        // to defaults so the rule can never NPE inside RuleMatcher.matches().
        @Suppress("SENSELESS_COMPARISON")
        val ruleType = raw.ruleType ?: RuleType.DENYLIST
        @Suppress("SENSELESS_COMPARISON")
        val titleMatch = raw.titleMatchType ?: MatchType.CONTAINS
        @Suppress("SENSELESS_COMPARISON")
        val textMatch = raw.textMatchType ?: MatchType.CONTAINS

        val pkg = raw.packageName?.takeIf { it.isNotBlank() }
        val title = raw.titleFilter?.takeIf { it.isNotBlank() }
        val text = raw.textFilter?.takeIf { it.isNotBlank() }

        // A rule with no package and no filters matches nothing meaningful — drop it.
        if (pkg == null && title == null && text == null) return null

        // Bound/validate imported regex patterns; drop the whole rule if unsafe.
        if (titleMatch == MatchType.REGEX && title != null && !isRegexSafe(title)) return null
        if (textMatch == MatchType.REGEX && text != null && !isRegexSafe(text)) return null

        return raw.copy(
            packageName = pkg,
            titleFilter = title,
            textFilter = text,
            titleMatchType = titleMatch,
            textMatchType = textMatch,
            ruleType = ruleType
        )
    }

    private fun isRegexSafe(pattern: String): Boolean {
        if (pattern.length > MAX_REGEX_LENGTH) return false
        return try {
            Pattern.compile(pattern)
            true
        } catch (e: PatternSyntaxException) {
            false
        }
    }
}
