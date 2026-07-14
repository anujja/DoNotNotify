package com.donotnotify.donotnotify

import android.app.Notification
import android.graphics.Bitmap
import com.donotnotify.donotnotify.StackedNotificationManager.AbsorbPlan
import com.donotnotify.donotnotify.StackedNotificationManager.ActiveStackNote
import com.donotnotify.donotnotify.StackedNotificationManager.Entry
import com.donotnotify.donotnotify.StackedNotificationManager.PostBlock
import com.donotnotify.donotnotify.StackedNotificationManager.RegistrySnapshot
import com.donotnotify.donotnotify.StackedNotificationManager.StackPoster
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

private const val TEST_CHANNEL = "stack_rule_test"

private class FakeStackPoster(
    var enabled: Boolean = true,
    /** Default importance for any channel not named in [channelImportances]. 3 = IMPORTANCE_DEFAULT. */
    var importance: Int = 3,
    /** Per-channel overrides — channels are per-rule now, so importance is not app-wide. */
    val channelImportances: MutableMap<String, Int> = mutableMapOf(),
    val active: MutableList<ActiveStackNote> = mutableListOf(),
    var throwOnChild: Boolean = false,
    var throwOnSummary: Boolean = false
) : StackPoster {
    val children = mutableListOf<AbsorbPlan>()
    val summaries = mutableListOf<AbsorbPlan>()
    val cancelled = mutableListOf<Int>()
    val cancelledKeys = mutableListOf<String>()
    /** Channel each post actually landed on, so tests can assert routing. */
    val childChannels = mutableListOf<String>()
    val summaryChannels = mutableListOf<String>()

    override fun areEnabled() = enabled
    override fun channelImportance(channelId: String) = channelImportances[channelId] ?: importance
    override fun activeStackNotifications() = active.toList()
    override fun postChild(
        plan: AbsorbPlan,
        groupKey: String,
        channelId: String,
        appLabel: String,
        entry: Entry,
        largeIcon: Bitmap?
    ) {
        if (throwOnChild) throw RuntimeException("child boom")
        children.add(plan)
        childChannels.add(channelId)
    }
    override fun postSummary(plan: AbsorbPlan, groupKey: String, channelId: String, appLabel: String) {
        if (throwOnSummary) throw RuntimeException("summary boom")
        summaries.add(plan)
        summaryChannels.add(channelId)
    }
    override fun cancel(id: Int) { cancelled.add(id) }
    override fun cancelByKey(key: String) { cancelledKeys.add(key) }
}

class StackedNotificationManagerTest {

    private fun entry(key: String, ts: Long = 1L, vis: Int = Notification.VISIBILITY_PUBLIC) =
        Entry(key, "T-$key", "B-$key", ts, null, vis, 0)

    @Before
    fun reset() {
        // Clear the singleton registry (no active notes → just clears the maps).
        StackedNotificationManager.reconcileOnConnect(FakeStackPoster())
    }

    // ---- groupKeyFor ----------------------------------------------------------

    @Test
    fun `identical signatures yield identical group key`() {
        val r1 = BlockerRule(packageName = "p", titleFilter = "x", ruleType = RuleType.STACK)
        val r2 = BlockerRule(packageName = "p", titleFilter = "x", ruleType = RuleType.STACK)
        assertEquals(
            StackedNotificationManager.groupKeyFor("p", r1),
            StackedNotificationManager.groupKeyFor("p", r2)
        )
    }

    @Test
    fun `null vs empty title filter produce different keys`() {
        val rNull = BlockerRule(packageName = "p", titleFilter = null, ruleType = RuleType.STACK)
        val rEmpty = BlockerRule(packageName = "p", titleFilter = "", ruleType = RuleType.STACK)
        assertNotEquals(
            StackedNotificationManager.groupKeyFor("p", rNull),
            StackedNotificationManager.groupKeyFor("p", rEmpty)
        )
    }

    @Test
    fun `time window and ruleType differences change the key`() {
        val base = BlockerRule(packageName = "p", titleFilter = "x", ruleType = RuleType.STACK)
        val timed = base.copy(advancedConfig = AdvancedRuleConfig(isTimeLimitEnabled = true))
        val deny = base.copy(ruleType = RuleType.DENYLIST)
        val k = StackedNotificationManager.groupKeyFor("p", base)
        assertNotEquals(k, StackedNotificationManager.groupKeyFor("p", timed))
        assertNotEquals(k, StackedNotificationManager.groupKeyFor("p", deny))
    }

    // ---- planAbsorb (pure) ----------------------------------------------------

    private fun snapshot(
        existing: List<Entry> = emptyList(),
        summaryId: Int? = null,
        cumulative: Int = 0
    ) = RegistrySnapshot(existing, summaryId, cumulative, 0, summaryId == null, emptyList(), null)

    @Test
    fun `new entry is not an update and increments cumulative`() {
        val ids = AtomicInteger(1)
        val plan = StackedNotificationManager.planAbsorb(
            snapshot(), entry("a"), 1L, { ids.getAndIncrement() }
        )
        assertFalse(plan.isUpdate)
        assertEquals(1, plan.cumulativeCount)
        assertEquals(1, plan.committedEntries.size)
    }

    @Test
    fun `same sbnKey reuses child id and keeps cumulative`() {
        val existing = entry("a").copy(childId = 77)
        val ids = AtomicInteger(1)
        val plan = StackedNotificationManager.planAbsorb(
            snapshot(listOf(existing), summaryId = 5, cumulative = 1),
            entry("a", ts = 2L), 2L, { ids.getAndIncrement() }
        )
        assertTrue(plan.isUpdate)
        assertTrue(plan.pingSuppressed)
        assertEquals(77, plan.childId)
        assertEquals(1, plan.cumulativeCount)
    }

    @Test
    fun `per-stack cap evicts oldest children`() {
        val cap = StackedNotificationManager.MAX_CHILDREN_PER_STACK
        val existing = (1..cap).map { entry("k$it", ts = it.toLong()).copy(childId = it) }
        val ids = AtomicInteger(9000)
        val plan = StackedNotificationManager.planAbsorb(
            snapshot(existing, summaryId = 1, cumulative = cap),
            entry("new", ts = 999L), 999L, { ids.getAndIncrement() }
        )
        assertEquals(cap, plan.committedEntries.size)
        assertEquals(listOf(1), plan.evictChildIds) // oldest (childId 1) evicted
        assertEquals(cap + 1, plan.cumulativeCount)
    }

    @Test
    fun `unknown visibility is conservatively private and redacted`() {
        val ids = AtomicInteger(1)
        val plan = StackedNotificationManager.planAbsorb(
            snapshot(), entry("a", vis = 99 /* unknown */), 1L, { ids.getAndIncrement() }
        )
        assertEquals(Notification.VISIBILITY_PRIVATE, plan.effectiveVisibility)
        assertTrue(plan.redactPublic)
    }

    @Test
    fun `public visibility is not redacted`() {
        val ids = AtomicInteger(1)
        val plan = StackedNotificationManager.planAbsorb(
            snapshot(), entry("a", vis = Notification.VISIBILITY_PUBLIC), 1L, { ids.getAndIncrement() }
        )
        assertFalse(plan.redactPublic)
    }

    // ---- absorbAndPost (transactional) ---------------------------------------

    private fun groupKey() = StackedNotificationManager.groupKeyFor(
        "p", BlockerRule(packageName = "p", titleFilter = "x", ruleType = RuleType.STACK)
    )

    @Test
    fun `postBlockVia reports typed reasons`() {
        assertEquals(PostBlock.NOTIFICATIONS_DISABLED,
            StackedNotificationManager.postBlockVia(FakeStackPoster(enabled = false), TEST_CHANNEL))
        assertEquals(PostBlock.CHANNEL_DISABLED,
            StackedNotificationManager.postBlockVia(FakeStackPoster(importance = 0), TEST_CHANNEL))
        assertEquals(PostBlock.OK,
            StackedNotificationManager.postBlockVia(FakeStackPoster(), TEST_CHANNEL))
    }

    @Test
    fun `disabled poster posts nothing and returns false`() {
        val poster = FakeStackPoster(enabled = false)
        val ok = StackedNotificationManager.absorbAndPost(poster, groupKey(), TEST_CHANNEL, "App", entry("a"), null)
        assertFalse(ok)
        assertTrue(poster.children.isEmpty())
        assertTrue(poster.summaries.isEmpty())
    }

    @Test
    fun `successful absorb posts child and summary`() {
        val poster = FakeStackPoster()
        val ok = StackedNotificationManager.absorbAndPost(poster, groupKey(), TEST_CHANNEL, "App", entry("a"), null)
        assertTrue(ok)
        assertEquals(1, poster.children.size)
        assertEquals(1, poster.summaries.size)
        assertEquals(1, poster.children[0].cumulativeCount)
        assertFalse(poster.children[0].isUpdate)
    }

    @Test
    fun `same key re-absorb is an update with suppressed ping and stable count`() {
        val poster = FakeStackPoster()
        val gk = groupKey()
        StackedNotificationManager.absorbAndPost(poster, gk, TEST_CHANNEL, "App", entry("a", ts = 1L), null)
        StackedNotificationManager.absorbAndPost(poster, gk, TEST_CHANNEL, "App", entry("a", ts = 2L), null)
        val second = poster.children.last()
        assertTrue(second.isUpdate)
        assertTrue(second.pingSuppressed)
        assertEquals(1, second.cumulativeCount)
    }

    @Test
    fun `rollback on summary failure for a new child cancels the child and leaves registry clean`() {
        val gk = groupKey()
        val failing = FakeStackPoster(throwOnSummary = true)
        val ok = StackedNotificationManager.absorbAndPost(failing, gk, TEST_CHANNEL, "App", entry("a"), null)
        assertFalse(ok)
        assertEquals(1, failing.children.size)
        // The freshly posted child (and the would-be-new summary) were cancelled.
        assertTrue(failing.cancelled.contains(failing.children[0].childId))
        // Registry was not committed: a fresh absorb is again a brand-new entry.
        val good = FakeStackPoster()
        StackedNotificationManager.absorbAndPost(good, gk, TEST_CHANNEL, "App", entry("a"), null)
        assertFalse(good.children[0].isUpdate)
        assertEquals(1, good.children[0].cumulativeCount)
    }

    @Test
    fun `rollback on update failure does not cancel the reused child`() {
        val gk = groupKey()
        val good = FakeStackPoster()
        StackedNotificationManager.absorbAndPost(good, gk, TEST_CHANNEL, "App", entry("a", ts = 1L), null)
        val childId = good.children[0].childId

        val failing = FakeStackPoster(throwOnSummary = true)
        val ok = StackedNotificationManager.absorbAndPost(failing, gk, TEST_CHANNEL, "App", entry("a", ts = 2L), null)
        assertFalse(ok)
        // The reused (still-valid) child id must NOT be cancelled.
        assertFalse(failing.cancelled.contains(childId))
    }

    @Test
    fun `reconcileOnConnect cancels our active stacks by key and clears the registry`() {
        val gk = groupKey()
        val poster = FakeStackPoster()
        StackedNotificationManager.absorbAndPost(poster, gk, TEST_CHANNEL, "App", entry("a"), null)

        val restart = FakeStackPoster(
            active = mutableListOf(
                ActiveStackNote("dnn|0|child", gk),
                ActiveStackNote("other|1|x", "com.foo")
            )
        )
        StackedNotificationManager.reconcileOnConnect(restart)
        assertEquals(listOf("dnn|0|child"), restart.cancelledKeys) // only our dnn_stack key

        // Registry cleared: a subsequent absorb starts a brand-new stack.
        val after = FakeStackPoster()
        StackedNotificationManager.absorbAndPost(after, gk, TEST_CHANNEL, "App", entry("a"), null)
        assertFalse(after.children[0].isUpdate)
        assertEquals(1, after.children[0].cumulativeCount)
    }

    // ---- per-rule channels ----------------------------------------------------

    private fun stackRule(title: String) =
        BlockerRule(packageName = "p", titleFilter = title, ruleType = RuleType.STACK)

    @Test
    fun `silencing one rule's channel does not silence another rule's`() {
        // The whole point of the feature: LinkedIn "recommended posts" can be silent while
        // LinkedIn "comments on your post" still alerts. With the old shared channel, disabling
        // one meant disabling stacking entirely.
        val quiet = stackRule("recommended")
        val loud = stackRule("commented")
        val quietChannel = StackChannels.channelIdFor(quiet)
        val loudChannel = StackChannels.channelIdFor(loud)

        val poster = FakeStackPoster(
            channelImportances = mutableMapOf(quietChannel to 0 /* IMPORTANCE_NONE */)
        )

        assertEquals(
            PostBlock.CHANNEL_DISABLED,
            StackedNotificationManager.postBlockVia(poster, quietChannel)
        )
        assertEquals(
            PostBlock.OK,
            StackedNotificationManager.postBlockVia(poster, loudChannel)
        )

        // And the blocked one really does refuse to post, while the other goes through.
        assertFalse(
            StackedNotificationManager.absorbAndPost(
                poster, StackedNotificationManager.groupKeyFor("p", quiet), quietChannel,
                "App", entry("q"), null
            )
        )
        assertTrue(
            StackedNotificationManager.absorbAndPost(
                poster, StackedNotificationManager.groupKeyFor("p", loud), loudChannel,
                "App", entry("l"), null
            )
        )
        assertEquals(listOf(loudChannel), poster.childChannels)
    }

    @Test
    fun `child and summary post on the same rule channel`() {
        // GROUP_ALERT_CHILDREN means the child's channel is what actually rings, and grouping
        // only behaves if the summary shares it.
        val rule = stackRule("x")
        val channel = StackChannels.channelIdFor(rule)
        val poster = FakeStackPoster()

        StackedNotificationManager.absorbAndPost(
            poster, StackedNotificationManager.groupKeyFor("p", rule), channel, "App", entry("a"), null
        )

        assertEquals(listOf(channel), poster.childChannels)
        assertEquals(listOf(channel), poster.summaryChannels)
    }

    @Test
    fun `cancelStackForRule cancels the summary and every child, then forgets the group`() {
        val rule = stackRule("x")
        val gk = StackedNotificationManager.groupKeyFor("p", rule)
        val channel = StackChannels.channelIdFor(rule)
        val poster = FakeStackPoster()

        StackedNotificationManager.absorbAndPost(poster, gk, channel, "App", entry("a"), null)
        StackedNotificationManager.absorbAndPost(poster, gk, channel, "App", entry("b"), null)
        val childIds = poster.children.map { it.childId }.distinct()
        val summaryId = poster.summaries.last().summaryId

        StackedNotificationManager.cancelStackForRule(poster, "p", rule)

        assertTrue("summary cancelled", poster.cancelled.contains(summaryId))
        assertTrue("every child cancelled", poster.cancelled.containsAll(childIds))

        // Group forgotten: the next absorb starts fresh rather than resuming the old stack.
        val after = FakeStackPoster()
        StackedNotificationManager.absorbAndPost(after, gk, channel, "App", entry("c"), null)
        assertEquals(1, after.children[0].cumulativeCount)
    }
}
