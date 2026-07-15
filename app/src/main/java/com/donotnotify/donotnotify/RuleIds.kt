package com.donotnotify.donotnotify

import com.google.gson.JsonParser
import java.util.UUID

/**
 * Single authority for [BlockerRule.id]. Ids are stable, device-local UUIDs; the per-rule
 * notification channel of a STACK rule is derived from its id, so an id that changes,
 * collides, or is missing silently re-keys or merges the user's channels (losing their
 * sound/vibration settings).
 *
 * Every path that produces rules must pass through [normalizeIds], because Gson allocates
 * via Unsafe and does **not** run the Kotlin constructor — so `id` is null on every rule
 * loaded from disk or an import file, despite its non-null declared type.
 */
object RuleIds {

    /** True if [id] is a well-formed UUID. Null/blank/garbage are all rejected. */
    fun isValid(id: String?): Boolean {
        if (id.isNullOrBlank()) return false
        return try {
            UUID.fromString(id).toString() == id.lowercase()
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    /**
     * Returns [rules] with every id guaranteed valid and unique within the list: null, blank,
     * malformed and *duplicate* ids are replaced with fresh UUIDs. Duplicate detection is
     * inherently cross-rule, which is why this operates on the whole list rather than per-rule.
     *
     * Order is preserved, and the first holder of a duplicated id keeps it.
     */
    fun normalizeIds(rules: List<BlockerRule>): List<BlockerRule> {
        val seen = HashSet<String>(rules.size)
        return rules.map { rule ->
            // Gson bypasses the constructor default, so this non-null field can be null at runtime.
            @Suppress("SENSELESS_COMPARISON")
            val current: String? = rule.id
            if (isValid(current) && seen.add(current!!)) {
                rule
            } else {
                val fresh = newId()
                seen.add(fresh)
                rule.copy(id = fresh)
            }
        }
    }

    /**
     * True if every rule in a stored rules-array [json] carries a non-blank `id`.
     *
     * This exists because inspecting the *deserialized* objects is not enough. Kotlin synthesizes
     * a no-arg constructor when every parameter has a default, and Gson uses it — so a rule whose
     * JSON has no `id` is deserialized with a **freshly minted random UUID**, which looks perfectly
     * valid. Without this check, such a rule would be re-minted on every load and never persisted,
     * so its notification channel id would change on each read and the user's per-rule sound
     * settings would evaporate. Callers must persist ids as soon as any are missing from disk.
     */
    fun rulesJsonHasAllIds(json: String): Boolean = try {
        val root = JsonParser.parseString(json)
        root.isJsonArray && root.asJsonArray.all { el ->
            el.isJsonObject &&
                el.asJsonObject.get("id")
                    ?.takeIf { it.isJsonPrimitive }
                    ?.asString
                    ?.isNotBlank() == true
        }
    } catch (e: Exception) {
        false
    }

    /** True if [normalizeIds] would change [rules] — lets callers avoid a pointless re-save. */
    fun needsNormalizing(rules: List<BlockerRule>): Boolean {
        val seen = HashSet<String>(rules.size)
        return rules.any { rule ->
            @Suppress("SENSELESS_COMPARISON")
            val current: String? = rule.id
            !isValid(current) || !seen.add(current!!)
        }
    }

    fun newId(): String = UUID.randomUUID().toString()
}
