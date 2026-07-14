package com.donotnotify.donotnotify

import com.donotnotify.donotnotify.StackChannels.ChannelSnapshot
import com.donotnotify.donotnotify.StackChannels.ChannelSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fake [StackChannels.ChannelHost] — no Android, mirroring the FakeStackPoster pattern.
 */
private class FakeChannelHost(
    existing: Map<String, ChannelSnapshot> = emptyMap(),
    names: Map<String, String> = emptyMap()
) : StackChannels.ChannelHost {
    val channels = existing.toMutableMap()
    val channelNames = names.toMutableMap()
    val created = mutableListOf<ChannelSpec>()
    val renamed = mutableListOf<Pair<String, String>>()
    val deleted = mutableListOf<String>()
    val groups = mutableListOf<String>()

    init {
        // Any pre-existing channel without an explicit name gets a placeholder.
        channels.keys.forEach { channelNames.putIfAbsent(it, "old-$it") }
    }

    override fun existingChannels(): Map<String, String> = channelNames.toMap()
    override fun snapshot(channelId: String): ChannelSnapshot? = channels[channelId]
    override fun createGroup(groupId: String, name: String) { groups.add(groupId) }
    override fun create(spec: ChannelSpec) {
        created.add(spec)
        channels[spec.id] = spec.settings
        channelNames[spec.id] = spec.name
    }
    override fun rename(channelId: String, name: String) {
        renamed.add(channelId to name)
        channelNames[channelId] = name
    }
    override fun delete(channelId: String) {
        deleted.add(channelId)
        channels.remove(channelId)
        channelNames.remove(channelId)
    }
}

class StackChannelsTest {

    private val naming = StackChannels.ChannelNaming(
        appRuleFormat = "%1\$s — rule %2\$s",
        unnamedFormat = "Stack rule %1\$s"
    )

    private fun stackRule(
        app: String = "LinkedIn",
        pkg: String = "com.linkedin",
        title: String? = null,
        name: String? = null
    ) = BlockerRule(
        appName = app,
        packageName = pkg,
        titleFilter = title,
        ruleType = RuleType.STACK,
        name = name
    )

    // ---- deletion scope --------------------------------------------------------

    @Test
    fun `sync never deletes channels it does not own`() {
        // The invariant that protects the app's own `health` channel — and anything a future
        // version adds. A sync may only ever touch stack_rule_* and the single legacy channel.
        val kept = stackRule(title = "keep")
        val orphan = "${StackChannels.STACK_CHANNEL_PREFIX}dead-rule-id"

        val plan = StackChannels.planChannelSync(
            existing = mapOf(
                "health" to "Service health",
                "some_other_app_channel" to "Other",
                StackChannels.LEGACY_CHANNEL_ID to "Stacked notifications",
                orphan to "Dead rule",
                StackChannels.channelIdFor(kept) to StackChannels.channelNameFor(kept, naming)
            ),
            rules = listOf(kept),
            legacySeed = null,
            naming = naming
        )

        assertEquals(
            listOf(StackChannels.LEGACY_CHANNEL_ID, orphan).sorted(),
            plan.delete.sorted()
        )
        assertFalse("health channel must survive", plan.delete.contains("health"))
        assertFalse(plan.delete.contains("some_other_app_channel"))
        assertTrue("live rule's channel is untouched", plan.create.isEmpty())
    }

    @Test
    fun `a disabled rule keeps its channel`() {
        // Deleting it would tombstone the channel; Android restores a deleted channel's old
        // settings if the id ever returns, so a disable/re-enable cycle would silently resurrect
        // stale settings. Only real deletion (or conversion away from STACK) removes a channel.
        val disabled = stackRule(title = "x").copy(isEnabled = false)
        val plan = StackChannels.planChannelSync(
            existing = mapOf(
                StackChannels.channelIdFor(disabled) to StackChannels.channelNameFor(disabled, naming)
            ),
            rules = listOf(disabled),
            legacySeed = null,
            naming = naming
        )
        assertTrue(plan.delete.isEmpty())
    }

    @Test
    fun `converting a rule away from STACK orphans its channel`() {
        val wasStack = stackRule(title = "x")
        val nowDeny = wasStack.copy(ruleType = RuleType.DENYLIST)
        val plan = StackChannels.planChannelSync(
            existing = mapOf(StackChannels.channelIdFor(wasStack) to "LinkedIn — rule x"),
            rules = listOf(nowDeny),
            legacySeed = null,
            naming = naming
        )
        assertEquals(listOf(StackChannels.channelIdFor(wasStack)), plan.delete)
    }

    @Test
    fun `non-stack rules never get channels`() {
        val plan = StackChannels.planChannelSync(
            existing = emptyMap(),
            rules = listOf(
                BlockerRule(packageName = "p", ruleType = RuleType.DENYLIST),
                BlockerRule(packageName = "p", ruleType = RuleType.ALLOWLIST)
            ),
            legacySeed = null,
            naming = naming
        )
        assertTrue(plan.create.isEmpty())
    }

    // ---- migration seeding -----------------------------------------------------

    @Test
    fun `new channels inherit the silenced legacy channel rather than re-alerting the user`() {
        // A user who muted the shared "Stacked notifications" channel must not be blasted by a
        // fresh batch of IMPORTANCE_DEFAULT channels on upgrade.
        val silenced = ChannelSnapshot.default().copy(
            importance = StackChannels.IMPORTANCE_NONE,
            vibrationEnabled = false,
            soundUri = null
        )
        val rule = stackRule(title = "x")

        val plan = StackChannels.planChannelSync(
            existing = mapOf(StackChannels.LEGACY_CHANNEL_ID to "n"),
            rules = listOf(rule),
            legacySeed = silenced,
            naming = naming
        )

        assertEquals(1, plan.create.size)
        assertEquals(StackChannels.IMPORTANCE_NONE, plan.create[0].settings.importance)
        assertFalse(plan.create[0].settings.vibrationEnabled)
        assertEquals(listOf(StackChannels.LEGACY_CHANNEL_ID), plan.delete)
    }

    @Test
    fun `sound and vibration pattern are carried across from the legacy channel`() {
        val custom = ChannelSnapshot.default().copy(
            importance = StackChannels.IMPORTANCE_DEFAULT,
            soundUri = "content://media/alarm/7",
            audioUsage = 5,
            vibrationEnabled = true,
            vibrationPattern = longArrayOf(0, 250, 100, 250)
        )
        val plan = StackChannels.planChannelSync(
            existing = mapOf(StackChannels.LEGACY_CHANNEL_ID to "n"),
            rules = listOf(stackRule(title = "x")),
            legacySeed = custom,
            naming = naming
        )
        val seeded = plan.create.single().settings
        assertEquals("content://media/alarm/7", seeded.soundUri)
        assertEquals(5, seeded.audioUsage)
        assertEquals(listOf(0L, 250L, 100L, 250L), seeded.vibrationPattern?.toList())
    }

    @Test
    fun `a fresh install with no legacy channel gets sensible defaults`() {
        val plan = StackChannels.planChannelSync(
            existing = emptyMap(),
            rules = listOf(stackRule(title = "x")),
            legacySeed = null,
            naming = naming
        )
        assertEquals(StackChannels.IMPORTANCE_DEFAULT, plan.create.single().settings.importance)
        assertTrue(plan.delete.isEmpty())
    }

    @Test
    fun `sync snapshots the legacy channel before deleting it`() {
        val host = FakeChannelHost(
            existing = mapOf(
                StackChannels.LEGACY_CHANNEL_ID to
                    ChannelSnapshot.default().copy(importance = StackChannels.IMPORTANCE_NONE)
            )
        )
        val rule = stackRule(title = "x")

        StackChannels.sync(host, { listOf(rule) }, "Stacked notifications", naming)

        assertEquals(
            "seeded from the legacy channel, not from defaults",
            StackChannels.IMPORTANCE_NONE,
            host.created.single().settings.importance
        )
        assertEquals(listOf(StackChannels.LEGACY_CHANNEL_ID), host.deleted)
        assertNull(host.channels[StackChannels.LEGACY_CHANNEL_ID])
    }

    @Test
    fun `sync is idempotent`() {
        val host = FakeChannelHost()
        val rules = listOf(stackRule(title = "a"), stackRule(title = "b"))

        StackChannels.sync(host, { rules }, "Stacked", naming)
        val afterFirst = host.created.size
        StackChannels.sync(host, { rules }, "Stacked", naming)

        assertEquals(2, afterFirst)
        assertEquals("second sync creates nothing new", afterFirst, host.created.size)
        assertTrue(host.deleted.isEmpty())
    }

    @Test
    fun `sync re-reads rules so a stale caller cannot delete a newer rule's channel`() {
        // syncChannels takes no rule list precisely to avoid this: if it accepted one, a sync
        // triggered by an earlier mutation could run after a later mutation committed and delete
        // the newly-created channel as an "orphan".
        val host = FakeChannelHost()
        val early = stackRule(title = "early")
        val late = stackRule(title = "late")

        var committed = listOf(early)
        StackChannels.sync(host, { committed }, "Stacked", naming)

        // A later mutation commits a second rule; the (stale) trigger fires afterwards.
        committed = listOf(early, late)
        StackChannels.sync(host, { committed }, "Stacked", naming)

        assertTrue("the newer rule's channel must survive", host.deleted.isEmpty())
        assertEquals(
            setOf(StackChannels.channelIdFor(early), StackChannels.channelIdFor(late)),
            host.created.map { it.id }.toSet()
        )
    }

    @Test
    fun `ensure creates a missing channel and is a no-op when it exists`() {
        val host = FakeChannelHost()
        val rule = stackRule(title = "x")

        StackChannels.ensure(host, rule, "Stacked", naming)
        StackChannels.ensure(host, rule, "Stacked", naming)

        assertEquals(1, host.created.size)
        assertEquals(StackChannels.channelIdFor(rule), host.created[0].id)
    }

    @Test
    fun `ensure ignores non-stack rules`() {
        val host = FakeChannelHost()
        StackChannels.ensure(
            host,
            BlockerRule(packageName = "p", ruleType = RuleType.DENYLIST),
            "Stacked",
            naming
        )
        assertTrue(host.created.isEmpty())
    }

    // ---- renaming an existing channel ------------------------------------------

    @Test
    fun `naming a rule after its channel exists renames the channel`() {
        // Caught on-device: the channel is created with the auto-derived name, and the user then
        // names the rule. Without this, system settings would keep showing the old name forever.
        // A channel's name IS mutable (unlike importance/sound), so keep it current.
        val unnamed = stackRule(title = "recommended")
        val host = FakeChannelHost(
            existing = mapOf(StackChannels.channelIdFor(unnamed) to ChannelSnapshot.default()),
            names = mapOf(StackChannels.channelIdFor(unnamed) to
                StackChannels.channelNameFor(unnamed, naming))
        )

        val named = unnamed.copy(name = "Recommended posts")
        StackChannels.sync(host, { listOf(named) }, "Stacked", naming)

        assertEquals(
            listOf(StackChannels.channelIdFor(named) to "Recommended posts"),
            host.renamed
        )
        assertTrue("rename must not recreate the channel", host.created.isEmpty())
        assertTrue("nor delete it — that would reset the user's sound", host.deleted.isEmpty())
    }

    @Test
    fun `a rule whose name is unchanged is not renamed`() {
        val rule = stackRule(title = "x", name = "My rule")
        val host = FakeChannelHost(
            existing = mapOf(StackChannels.channelIdFor(rule) to ChannelSnapshot.default()),
            names = mapOf(StackChannels.channelIdFor(rule) to "My rule")
        )
        StackChannels.sync(host, { listOf(rule) }, "Stacked", naming)
        assertTrue(host.renamed.isEmpty())
    }

    // ---- naming ----------------------------------------------------------------

    @Test
    fun `filter text is never used as a channel name`() {
        // Channel names are readable by any notification listener/assistant. A filter is private
        // user content ("oncologist", an ex's name) that such an app cannot otherwise see.
        val rule = stackRule(title = "my therapist")
        val name = StackChannels.channelNameFor(rule, naming)
        assertFalse(name.contains("therapist"))
        assertTrue(name.startsWith("LinkedIn"))
    }

    @Test
    fun `an explicit user-supplied name is used verbatim`() {
        val rule = stackRule(title = "recommended", name = "Recommended posts")
        assertEquals("Recommended posts", StackChannels.channelNameFor(rule, naming))
    }

    @Test
    fun `two rules on one app get distinguishable names`() {
        val a = stackRule(title = "recommended")
        val b = stackRule(title = "commented")
        assertNotEquals(
            StackChannels.channelNameFor(a, naming),
            StackChannels.channelNameFor(b, naming)
        )
    }

    @Test
    fun `bidi overrides and control chars are stripped from an untrusted app label`() {
        // appName comes from the source notification's extras. U+202E (RTL override) can visually
        // reverse text, letting a notification spoof a channel into looking like another app's.
        val spoof = stackRule(app = "Bank‮moc.live‬\nEvil")
        val name = StackChannels.channelNameFor(spoof, naming)

        assertFalse(name.contains('‮'))
        assertFalse(name.contains('‬'))
        assertFalse(name.contains('\n'))
    }

    @Test
    fun `isolate characters are stripped too`() {
        val rule = stackRule(app = "A⁦B⁧C⁨D⁩E")
        val name = StackChannels.channelNameFor(rule, naming)
        listOf('⁦', '⁧', '⁨', '⁩').forEach {
            assertFalse("stripped $it", name.contains(it))
        }
    }

    @Test
    fun `an overlong name is truncated but keeps its disambiguating suffix`() {
        val rule = stackRule(app = "A".repeat(500))
        val a = StackChannels.channelNameFor(rule, naming)
        val b = StackChannels.channelNameFor(stackRule(app = "A".repeat(500)), naming)

        assertTrue("bounded", a.length <= 40)
        assertNotEquals("suffix survives truncation, so the two stay distinguishable", a, b)
    }

    @Test
    fun `a label that sanitizes to nothing falls back to the package name`() {
        val rule = stackRule(app = "‮⁦\n\t ", pkg = "com.linkedin")
        val name = StackChannels.channelNameFor(rule, naming)
        assertTrue("falls back to package: $name", name.contains("com.linkedin"))
    }

    @Test
    fun `a rule with no label at all still gets a usable name`() {
        val rule = BlockerRule(ruleType = RuleType.STACK, titleFilter = "x")
        val name = StackChannels.channelNameFor(rule, naming)
        assertTrue(name.isNotBlank())
        assertTrue(name.startsWith("Stack rule "))
    }

    @Test
    fun `sanitize returns null when nothing usable remains`() {
        assertNull(StackChannels.sanitize("‮​ \n\t"))
        assertNull(StackChannels.sanitize(""))
        assertNull(StackChannels.sanitize(null))
        assertEquals("hello world", StackChannels.sanitize("  hello \n world  "))
    }

    // ---- channel ids -----------------------------------------------------------

    @Test
    fun `channel id is derived from the stable rule id and survives an edit`() {
        // The point of the whole identity change: editing a rule's filters must NOT re-key its
        // channel, or the user's sound settings evaporate on every edit.
        val before = stackRule(title = "recommended")
        val afterEdit = before.copy(titleFilter = "suggested", textFilter = "new")

        assertEquals(
            StackChannels.channelIdFor(before),
            StackChannels.channelIdFor(afterEdit)
        )
        assertTrue(StackChannels.isStackChannelId(StackChannels.channelIdFor(before)))
        assertFalse(StackChannels.isStackChannelId("health"))
    }

    private fun assertNotEquals(a: Any?, b: Any?) = org.junit.Assert.assertNotEquals(a, b)
    private fun assertNotEquals(msg: String, a: Any?, b: Any?) =
        org.junit.Assert.assertNotEquals(msg, a, b)
}
