package com.donotnotify.donotnotify

import androidx.annotation.Keep
import com.google.gson.ExclusionStrategy
import com.google.gson.FieldAttributes
import com.google.gson.Gson
import com.google.gson.GsonBuilder
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
 *
 * @Keep is load-bearing: without it R8 renames these fields in release builds
 * (`locale` → `a`, `rules` → `b`), producing exports no other build can import.
 * Release builds up to 6.1 shipped with that defect; [RuleImport.parse] contains a
 * compatibility branch for files they wrote.
 */
@Keep
data class RuleExport(
    val version: Int = RULE_EXPORT_VERSION,
    val locale: String? = null,
    val rules: List<BlockerRule> = emptyList()
)

/**
 * Serializer for exported rule files. Two fields are deliberately withheld:
 * - `hitCount` — local statistics, meaningless to a recipient.
 * - `id` — device-local identity. It keys *this* device's notification channels; exporting it
 *   would let an import carry foreign identity and collide with an existing rule's channel.
 *   [RuleImport.parse] mints a fresh id for every imported rule regardless.
 */
object RuleExportSerializer {
    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .setExclusionStrategies(object : ExclusionStrategy {
            override fun shouldSkipField(f: FieldAttributes): Boolean =
                f.name == "hitCount" || f.name == "id"

            override fun shouldSkipClass(clazz: Class<*>?): Boolean = false
        })
        .create()

    fun toJson(export: RuleExport): String = gson.toJson(export)
}

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
                // Release builds up to 6.1 lacked @Keep on RuleExport, so their exports carry
                // R8-renamed fields: locale → "a", rules → "b". Accept those files forever —
                // users hold them as backups. (BlockerRule itself was always @Keep, so the
                // rules inside such files are well-formed.)
                val rulesEl = obj.get("rules")
                    ?: obj.get("b")?.takeIf { it.isJsonArray }
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
                locale = (obj.get("locale") ?: obj.get("a"))
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

        // Imported identity is never meaningful: import *appends*, it does not restore. Mint a
        // fresh id for every rule rather than merely repairing invalid ones — a file carrying
        // well-formed UUIDs would otherwise keep externally-chosen identity and could collide
        // with a rule already stored, putting two rules on one notification channel.
        for (i in sanitized.indices) {
            sanitized[i] = sanitized[i].copy(id = RuleIds.newId())
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
