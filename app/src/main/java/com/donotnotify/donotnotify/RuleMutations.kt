package com.donotnotify.donotnotify

/**
 * Pure rule-list mutations (no Android types → JVM-testable), in the same spirit as
 * [RuleMatcher.planNotificationDecision] and `StackedNotificationManager.planAbsorb`.
 *
 * [RuleStorage] is the thin shell that takes the lock, re-reads current state, applies one of
 * these against it, and writes back. Keeping the merge semantics pure is what makes the
 * important properties testable: a hit-count bump must never resurrect a deleted rule, and an
 * update must never re-key a rule's id (which would silently orphan its notification channel).
 *
 * Every function takes the **current** list — never a caller's stale snapshot.
 */
object RuleMutations {

    /**
     * Bump `hitCount` for the named ids only. Ids not present in [current] are ignored rather
     * than re-added: the rule was deleted while the notification was in flight, and resurrecting
     * it would undo the user's delete. Repeated ids bump by their multiplicity.
     */
    fun applyHitCounts(current: List<BlockerRule>, ruleIds: List<String>): List<BlockerRule> {
        if (ruleIds.isEmpty()) return current
        val bump = ruleIds.groupingBy { it }.eachCount()
        return current.map { rule ->
            val n = bump[rule.id] ?: 0
            if (n > 0) rule.copy(hitCount = rule.hitCount + n) else rule
        }
    }

    /**
     * Replace the rule identified by [id].
     *
     * Two fields are taken from *current* state rather than from [newRule], because they are not
     * the editor's to set and [newRule] is a snapshot taken when the dialog opened:
     * - `id` — a caller supplying a different (even well-formed) id would silently re-key the
     *   rule's notification channel and discard the user's sound settings.
     * - `hitCount` — owned by the listener. Committing the editor's stale copy would swallow every
     *   hit counted while the dialog was open.
     *
     * Returns null if no rule with [id] exists — e.g. it was deleted while the dialog was open.
     */
    fun applyUpdate(current: List<BlockerRule>, id: String, newRule: BlockerRule): List<BlockerRule>? {
        val index = current.indexOfFirst { it.id == id }
        if (index == -1) return null
        val live = current[index]
        return current.toMutableList().apply {
            this[index] = newRule.copy(id = live.id, hitCount = live.hitCount)
        }
    }

    fun applyDelete(current: List<BlockerRule>, id: String): List<BlockerRule> =
        current.filterNot { it.id == id }

    /**
     * Append [additions], normalizing ids across the **merged** list so an incoming id can never
     * collide with one already stored.
     */
    fun applyAdd(current: List<BlockerRule>, additions: List<BlockerRule>): List<BlockerRule> =
        RuleIds.normalizeIds(current + additions)

    fun applySetEnabled(current: List<BlockerRule>, ruleIds: Set<String>, enabled: Boolean): List<BlockerRule> =
        if (ruleIds.isEmpty()) current
        else current.map { if (it.id in ruleIds) it.copy(isEnabled = enabled) else it }

    fun applySetAllEnabled(current: List<BlockerRule>, enabled: Boolean): List<BlockerRule> =
        current.map { it.copy(isEnabled = enabled) }

    fun applyResetHitCounts(current: List<BlockerRule>): List<BlockerRule> =
        current.map { it.copy(hitCount = 0) }
}
