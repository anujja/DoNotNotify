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

    private fun matchesText(rule: BlockerRule, text: String) =
        RuleMatcher.matches(rule, rule.packageName, null, text)

    @Test
    fun `english keywords still match in every locale`() {
        val locales = listOf("raw", "raw-es", "raw-fr", "raw-ja", "raw-ko", "raw-pl", "raw-ru", "raw-zh-rCN")
        for (dir in locales) {
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
