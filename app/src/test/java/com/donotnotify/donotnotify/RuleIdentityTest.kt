package com.donotnotify.donotnotify

import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Rule identity: the id keys a STACK rule's notification channel, so an id that is missing,
 * malformed, duplicated, or silently re-keyed loses (or merges) the user's per-rule sound
 * settings. These tests pin the properties that protect against that.
 */
class RuleIdentityTest {

    private fun rule(
        pkg: String = "com.example",
        title: String? = null,
        id: String = RuleIds.newId()
    ) = BlockerRule(packageName = pkg, titleFilter = title, id = id)

    // ---- RuleIds.normalizeIds --------------------------------------------------

    @Test
    fun `constructor mints a real id`() {
        // The Kotlin constructor default must produce a usable id — a blank default would put
        // every newly-created rule on the same notification channel.
        assertTrue(RuleIds.isValid(BlockerRule(packageName = "com.example").id))
    }

    private val listOfRules = com.google.gson.reflect.TypeToken.getParameterized(
        List::class.java, BlockerRule::class.java
    ).type

    @Test
    fun `legacy json without ids deserializes to fresh ids on every load - so it must be persisted`() {
        // The subtle one. Kotlin synthesizes a no-arg constructor because every parameter has a
        // default, and Gson uses it — so a rule with no `id` in JSON is NOT left null: it gets a
        // brand-new random UUID. That looks valid, so an object-level check sees nothing wrong,
        // yet the id differs on every load and would re-key the rule's notification channel each
        // time. Only inspecting the raw JSON catches it.
        val legacy = """[{"packageName":"com.a"},{"packageName":"com.b"}]"""

        val first: List<BlockerRule> = Gson().fromJson(legacy, listOfRules)
        val second: List<BlockerRule> = Gson().fromJson(legacy, listOfRules)

        assertTrue("Gson mints, not nulls", RuleIds.isValid(first[0].id))
        assertNotEquals("…and mints a DIFFERENT id each load", first[0].id, second[0].id)
        assertFalse("object-level check cannot see the problem", RuleIds.needsNormalizing(first))

        assertFalse("but the JSON check can", RuleIds.rulesJsonHasAllIds(legacy))
    }

    @Test
    fun `rulesJsonHasAllIds accepts a file whose rules all carry ids`() {
        val persisted = RuleExportSerializer.toJson(RuleExport(rules = emptyList()))
        assertNotNull(persisted)

        val withIds = """[{"packageName":"com.a","id":"${RuleIds.newId()}"}]"""
        assertTrue(RuleIds.rulesJsonHasAllIds(withIds))

        val blankId = """[{"packageName":"com.a","id":"  "}]"""
        assertFalse(RuleIds.rulesJsonHasAllIds(blankId))

        val nullId = """[{"packageName":"com.a","id":null}]"""
        assertFalse(RuleIds.rulesJsonHasAllIds(nullId))

        val mixed = """[{"packageName":"com.a","id":"${RuleIds.newId()}"},{"packageName":"com.b"}]"""
        assertFalse("one missing id is enough to force a re-save", RuleIds.rulesJsonHasAllIds(mixed))
    }

    @Test
    fun `explicit null id in json is repaired`() {
        val loaded: List<BlockerRule> = Gson().fromJson(
            """[{"packageName":"com.a","id":null}]""", listOfRules
        )
        val fixed = RuleIds.normalizeIds(loaded)
        assertTrue(RuleIds.isValid(fixed[0].id))
    }

    @Test
    fun `duplicate ids are repaired and the first holder keeps its id`() {
        val shared = RuleIds.newId()
        val fixed = RuleIds.normalizeIds(
            listOf(rule(pkg = "com.a", id = shared), rule(pkg = "com.b", id = shared))
        )
        assertEquals("first holder keeps the id", shared, fixed[0].id)
        assertNotEquals("duplicate is re-minted", shared, fixed[1].id)
        assertTrue(RuleIds.isValid(fixed[1].id))
    }

    @Test
    fun `malformed ids are replaced`() {
        val fixed = RuleIds.normalizeIds(listOf(rule(id = "not-a-uuid"), rule(id = "")))
        assertTrue(fixed.all { RuleIds.isValid(it.id) })
        assertEquals(2, fixed.map { it.id }.toSet().size)
    }

    @Test
    fun `already-valid distinct ids are left alone`() {
        val rules = listOf(rule(pkg = "com.a"), rule(pkg = "com.b"))
        assertFalse(RuleIds.needsNormalizing(rules))
        assertEquals(rules, RuleIds.normalizeIds(rules))
    }

    // ---- Import mints fresh ids ------------------------------------------------

    @Test
    fun `import mints fresh ids even when the file carries valid unique uuids`() {
        // The dangerous case: a crafted (or self-exported) file whose ids are well-formed and
        // unique would otherwise retain foreign identity — and one could equal a rule already
        // stored here, putting two rules on one notification channel.
        val stolen = RuleIds.newId()
        val json = """{"version":2,"rules":[{"packageName":"com.a","id":"$stolen"}]}"""

        val result = RuleImport.parse(json) as ImportResult.Success

        assertEquals(1, result.rules.size)
        assertNotEquals("imported id must not survive", stolen, result.rules[0].id)
        assertTrue(RuleIds.isValid(result.rules[0].id))
    }

    @Test
    fun `importing the same file twice yields distinct ids each time`() {
        val json = """{"version":2,"rules":[{"packageName":"com.a"},{"packageName":"com.b"}]}"""
        val first = (RuleImport.parse(json) as ImportResult.Success).rules
        val second = (RuleImport.parse(json) as ImportResult.Success).rules

        val all = (first + second).map { it.id }
        assertEquals("all four ids distinct", 4, all.toSet().size)
        assertTrue(all.all { RuleIds.isValid(it) })
    }

    // ---- Export withholds the id -----------------------------------------------

    @Test
    fun `exported json contains no id field`() {
        val json = RuleExportSerializer.toJson(
            RuleExport(locale = "en-GB", rules = listOf(rule(pkg = "com.a", title = "hi")))
        )
        assertFalse("id must not be exported — it is device-local", json.contains("\"id\""))
        assertFalse("hitCount must not be exported", json.contains("\"hitCount\""))
        assertTrue(json.contains("com.a"))

        // And a round-trip of our own export still yields a valid, fresh id.
        val reimported = (RuleImport.parse(json) as ImportResult.Success).rules
        assertEquals(1, reimported.size)
        assertTrue(RuleIds.isValid(reimported[0].id))
    }

    @Test
    fun `exported json still carries the user-authored name`() {
        val named = rule(pkg = "com.a").copy(name = "Recommended posts")
        val json = RuleExportSerializer.toJson(RuleExport(rules = listOf(named)))
        assertTrue("name is user content and should travel", json.contains("Recommended posts"))
    }

    // ---- RuleMutations: the properties that protect the channel -----------------

    @Test
    fun `hit count bump does not resurrect a deleted rule`() {
        // The listener captures rule ids on the binder thread, then persists on an executor.
        // If the user deletes the rule in that window, the bump must be a no-op — not a revival.
        val doomed = rule(pkg = "com.doomed")
        val survivor = rule(pkg = "com.survivor")
        val afterDelete = RuleMutations.applyDelete(listOf(doomed, survivor), doomed.id)

        val afterBump = RuleMutations.applyHitCounts(afterDelete, listOf(doomed.id, survivor.id))

        assertEquals(1, afterBump.size)
        assertEquals(survivor.id, afterBump[0].id)
        assertEquals(1, afterBump[0].hitCount)
    }

    @Test
    fun `hit count bump counts multiplicity and leaves other rules untouched`() {
        val a = rule(pkg = "com.a")
        val b = rule(pkg = "com.b")
        val out = RuleMutations.applyHitCounts(listOf(a, b), listOf(a.id, a.id, a.id))
        assertEquals(3, out.first { it.id == a.id }.hitCount)
        assertEquals(0, out.first { it.id == b.id }.hitCount)
    }

    @Test
    fun `update cannot re-key a rule's id`() {
        // A newRule arriving with a different valid UUID must still commit under the original id,
        // or the rule's notification channel silently changes and the user's sound is lost.
        val original = rule(pkg = "com.a", title = "before")
        val impostor = original.copy(id = RuleIds.newId(), titleFilter = "after")

        val out = RuleMutations.applyUpdate(listOf(original), original.id, impostor)

        assertNotNull(out)
        assertEquals("id is forced back onto the original", original.id, out!![0].id)
        assertEquals("but the edit itself is applied", "after", out[0].titleFilter)
    }

    @Test
    fun `update of a rule deleted meanwhile is declined rather than re-adding it`() {
        val gone = rule(pkg = "com.gone")
        assertNull(RuleMutations.applyUpdate(emptyList(), gone.id, gone))
    }

    @Test
    fun `update survives a concurrent hit count bump`() {
        // The old indexOf(oldRule) compared hitCount, so a listener writeback landing between
        // opening and saving the edit dialog made the edit silently vanish.
        val r = rule(pkg = "com.a", title = "before")
        val bumped = RuleMutations.applyHitCounts(listOf(r), listOf(r.id))

        val out = RuleMutations.applyUpdate(bumped, r.id, r.copy(titleFilter = "after"))

        assertNotNull(out)
        assertEquals("after", out!![0].titleFilter)
        assertEquals("the concurrent bump is not lost either", 1, out[0].hitCount)
    }

    @Test
    fun `add normalizes ids across the merged list so an incoming id cannot collide`() {
        val existing = rule(pkg = "com.a")
        val colliding = rule(pkg = "com.b", id = existing.id)

        val out = RuleMutations.applyAdd(listOf(existing), listOf(colliding))

        assertEquals(2, out.size)
        assertEquals("existing rule keeps its id", existing.id, out[0].id)
        assertNotEquals("the collision is re-minted", existing.id, out[1].id)
    }

    @Test
    fun `exported file has no ids so a plain gson round trip still normalizes`() {
        // Belt-and-braces: whatever route rules take back in, normalizeIds is the single authority.
        val json = RuleExportSerializer.toJson(RuleExport(rules = listOf(rule(), rule())))
        val rules = JsonParser.parseString(json).asJsonObject.getAsJsonArray("rules")
        assertEquals(2, rules.size())
        assertFalse(rules[0].asJsonObject.has("id"))
    }
}
