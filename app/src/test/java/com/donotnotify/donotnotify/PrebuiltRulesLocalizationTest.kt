package com.donotnotify.donotnotify

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Behavioural fixtures: proves the shipped localized prebuilt rules actually match the kind of
 * notifications they target (and don't over-match benign ones), via the real [RuleMatcher].
 * Guards against a translation silently breaking filtering.
 */
class PrebuiltRulesLocalizationTest {

    private val gson = Gson()
    private val listType = object : TypeToken<List<BlockerRule>>() {}.type

    private fun resDir(): File =
        listOf(File("src/main/res"), File("app/src/main/res"))
            .firstOrNull { it.isDirectory }
            ?: error("res dir not found; cwd=${File(".").absolutePath}")

    private fun rules(dir: String): List<BlockerRule> =
        gson.fromJson(File(resDir(), "$dir/prebuilt_rules.json").readText(Charsets.UTF_8), listType)

    private fun rule(dir: String, pkg: String): BlockerRule =
        rules(dir).first { it.packageName == pkg }

    private val AMAZON = "com.amazon.mShop.android.shopping"
    private val GPAY = "com.google.android.apps.nbu.paisa.user"
    private val LINKEDIN = "com.linkedin.android"
    private val TINDER = "com.tinder"
    private val REDDIT = "com.reddit.frontpage"

    private val LOCALES = listOf("raw", "raw-es", "raw-fr", "raw-ja", "raw-ko", "raw-pl", "raw-ru", "raw-zh-rCN")

    /** A message notification in each locale, using the phrase for "new message" these rules once blocked. */
    private val localizedNewMessage = mapOf(
        "raw-es" to "Tienes un nuevo mensaje de Juan",
        "raw-fr" to "Vous avez un nouveau message de Jean",
        "raw-ja" to "山田さんから新着メッセージがあります",
        "raw-ko" to "김민수님의 새 메시지",
        "raw-pl" to "Jan: nowa wiadomość",
        "raw-ru" to "У вас новое сообщение от Ивана",
        "raw-zh-rCN" to "张三给你发了新消息"
    )

    private fun matchesText(rule: BlockerRule, text: String) =
        RuleMatcher.matches(rule, rule.packageName, null, text)

    @Test
    fun `engagement rules never block messages or replies from people`() {
        for (dir in LOCALES) {
            val linkedin = rule(dir, LINKEDIN)
            val tinder = rule(dir, TINDER)
            val reddit = rule(dir, REDDIT)
            val messages = listOf("New message from Jane Doe", "Jane Doe sent you a message") +
                    listOfNotNull(localizedNewMessage[dir])
            for (text in messages) {
                assertFalse("$dir: LinkedIn blocked '$text'", matchesText(linkedin, text))
                assertFalse("$dir: Tinder blocked '$text'", matchesText(tinder, text))
            }
            assertFalse(
                "$dir: LinkedIn blocked a person's recommendation",
                matchesText(linkedin, "Jane Doe recommended you for Senior Engineer")
            )
            for (text in listOf(
                "u/someone replied to your comment",
                "u/someone mentioned you in r/android",
                "Mod notice: your post was removed"
            )) {
                assertFalse("$dir: Reddit blocked '$text'", matchesText(reddit, text))
            }
        }
    }

    @Test
    fun `engagement rules still block the bait in every locale`() {
        for (dir in LOCALES) {
            assertTrue("$dir: LinkedIn", matchesText(rule(dir, LINKEDIN), "Jobs recommended for you"))
            assertTrue("$dir: LinkedIn", matchesText(rule(dir, LINKEDIN), "Someone viewed your profile"))
            assertTrue("$dir: Tinder", matchesText(rule(dir, TINDER), "You have a new match!"))
            assertTrue("$dir: Reddit", matchesText(rule(dir, REDDIT), "Trending in r/worldnews"))
        }
    }

    @Test
    fun `english keywords still match in every locale`() {
        for (dir in LOCALES) {
            val amazon = rule(dir, AMAZON)
            assertTrue("$dir: english 'offer' should still match", matchesText(amazon, "Special offer just for you"))
            assertFalse("$dir: benign text should not match", matchesText(amazon, "Your package was delivered"))
        }
    }

    @Test
    fun `localized keywords match in their locale`() {
        assertTrue(matchesText(rule("raw-ja", AMAZON), "本日のタイムセール開催中"))      // セール
        assertTrue(matchesText(rule("raw-fr", AMAZON), "Profitez de notre offre du jour")) // offre
        assertTrue(matchesText(rule("raw-es", AMAZON), "Oferta especial de hoy"))          // oferta
        assertTrue(matchesText(rule("raw-zh-rCN", AMAZON), "今日特价促销"))                 // 特价 / 促销
        assertTrue(matchesText(rule("raw-ru", AMAZON), "Большая скидка сегодня"))          // скидка
    }

    @Test
    fun `gpay english literal still matches in default`() {
        val gpay = rule("raw", GPAY)
        assertTrue(RuleMatcher.matches(gpay, GPAY, "You earned a reward!", "Tap to reveal"))
        assertFalse(RuleMatcher.matches(gpay, GPAY, "Payment received", "₹500 from John"))
    }

    @Test
    fun `gpay localized rule matches localized wording`() {
        val gpayJa = rule("raw-ja", GPAY)
        assertTrue(RuleMatcher.matches(gpayJa, GPAY, "特典が当たりました", "タップして確認してください"))
    }
}
