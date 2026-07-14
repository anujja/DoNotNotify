package com.donotnotify.donotnotify

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileOutputStream

/**
 * Rule persistence.
 *
 * Every mutation is **id-keyed** and performed under [lock] against freshly-read state.
 * That matters because two writers race: the listener bumps hit counts from a background
 * executor while the UI edits/deletes/imports from Compose state. A whole-list
 * read-modify-write loses writes in both directions — a hit-count bump can resurrect a rule
 * the UI just deleted, and a UI save can erase an import. The listener and the UI share one
 * process (no `android:process` in the manifest), so this lock is a genuine mutual-exclusion
 * point.
 */
class RuleStorage(private val context: Context) {

    companion object {
        private const val TAG = "RuleStorage"
        @Volatile
        private var cachedRules: List<BlockerRule>? = null
        private val lock = Any()
    }

    private val gson = Gson()
    private val rulesFile = File(context.filesDir, "rules.json")
    private val atomicFile = AtomicFile(rulesFile)

    fun getRules(): List<BlockerRule> {
        cachedRules?.let { return it }
        synchronized(lock) {
            return loadLocked()
        }
    }

    /** Caller must hold [lock]. */
    private fun loadLocked(): List<BlockerRule> {
        cachedRules?.let { return it }
        if (!rulesFile.exists()) {
            return emptyList<BlockerRule>().also { cachedRules = it }
        }
        val json: String
        val parsed = try {
            json = atomicFile.readFully().toString(Charsets.UTF_8)
            val type = object : TypeToken<List<BlockerRule>>() {}.type
            gson.fromJson<List<BlockerRule>>(json, type) ?: emptyList()
        } catch (e: JsonSyntaxException) {
            preserveCorruptFile(e)
            return emptyList<BlockerRule>().also { cachedRules = it }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading rules", e)
            return emptyList<BlockerRule>().also { cachedRules = it }
        }

        // Ids missing *from the JSON* must be persisted immediately. Gson deserializes such a rule
        // through Kotlin's synthesized no-arg constructor, which mints a fresh random UUID — so the
        // id looks valid but would differ on every load, silently re-keying the rule's notification
        // channel each time. Checking the raw JSON is the only way to see this.
        val missingFromDisk = !RuleIds.rulesJsonHasAllIds(json)
        return if (missingFromDisk || RuleIds.needsNormalizing(parsed)) {
            val normalized = RuleIds.normalizeIds(parsed)
            writeLocked(normalized)
            normalized
        } else {
            parsed.also { cachedRules = it }
        }
    }

    /**
     * A corrupt file is *preserved*, not deleted. The previous behaviour discarded every rule
     * the user had, unrecoverably, on a single bad parse. The timestamp stops a later failure
     * from overwriting the first (most likely still-recoverable) copy.
     */
    private fun preserveCorruptFile(e: Exception) {
        val backup = File(context.filesDir, "rules.json.corrupt.${System.currentTimeMillis()}")
        val moved = try {
            rulesFile.renameTo(backup)
        } catch (io: Exception) {
            false
        }
        Log.e(
            TAG,
            "Corrupted rules file; ${if (moved) "preserved as ${backup.name}" else "could not preserve"}",
            e
        )
    }

    /**
     * Atomically replace the rules file, then publish to the cache. The cache is updated only
     * after a durable write, so it can never run ahead of disk.
     * Caller must hold [lock].
     */
    private fun writeLocked(rules: List<BlockerRule>) {
        var stream: FileOutputStream? = null
        try {
            stream = atomicFile.startWrite()
            stream.write(gson.toJson(rules).toByteArray(Charsets.UTF_8))
            atomicFile.finishWrite(stream)
        } catch (e: Exception) {
            stream?.let { atomicFile.failWrite(it) }
            Log.e(TAG, "Failed to write rules; cache left untouched", e)
            throw e
        }
        cachedRules = rules
    }

    /**
     * Whole-list overwrite. Prefer the id-keyed mutations below — this clobbers whatever a
     * concurrent writer committed, and exists only for callers that genuinely own the list.
     */
    fun saveRules(rules: List<BlockerRule>) {
        synchronized(lock) { writeLocked(RuleIds.normalizeIds(rules)) }
    }

    /**
     * Apply a pure mutation from [RuleMutations] against freshly-read state and commit it.
     * Nothing outside this class may write, so every mutation is serialized against every
     * other and against the listener's hit-count path.
     *
     * @return the committed list, or null if [mutate] declined (e.g. the rule no longer exists).
     */
    private fun mutate(mutate: (List<BlockerRule>) -> List<BlockerRule>?): List<BlockerRule>? {
        synchronized(lock) {
            val current = loadLocked()
            val updated = mutate(current) ?: return null
            if (updated != current) writeLocked(updated)
            return updated
        }
    }

    /**
     * Hot path: called by the listener for every matched notification. Bumps only the named
     * rules, against current state, so it cannot resurrect a rule deleted in the meantime.
     * Deliberately does **not** trigger channel sync — nothing structural changes here.
     */
    fun incrementHitCounts(ruleIds: List<String>) {
        if (ruleIds.isEmpty()) return
        mutate { RuleMutations.applyHitCounts(it, ruleIds) }
    }

    /**
     * Replace the rule identified by [id], forcing the committed rule back onto [id] — see
     * [RuleMutations.applyUpdate].
     * @return the committed list, or null if no rule with [id] exists.
     */
    fun updateRuleById(id: String, newRule: BlockerRule): List<BlockerRule>? =
        mutate { RuleMutations.applyUpdate(it, id, newRule) }

    fun deleteRuleById(id: String): List<BlockerRule> =
        mutate { RuleMutations.applyDelete(it, id) }!!

    fun addRules(rules: List<BlockerRule>): List<BlockerRule> =
        mutate { RuleMutations.applyAdd(it, rules) }!!

    fun setEnabledByIds(ruleIds: Set<String>, enabled: Boolean): List<BlockerRule> =
        mutate { RuleMutations.applySetEnabled(it, ruleIds, enabled) }!!

    fun setAllEnabled(enabled: Boolean): List<BlockerRule> =
        mutate { RuleMutations.applySetAllEnabled(it, enabled) }!!

    fun resetHitCounts() {
        mutate { RuleMutations.applyResetHitCounts(it) }
    }

    fun invalidateCache() {
        synchronized(lock) {
            cachedRules = null
        }
    }
}
