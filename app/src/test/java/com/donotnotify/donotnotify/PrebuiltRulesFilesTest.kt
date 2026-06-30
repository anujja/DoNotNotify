package com.donotnotify.donotnotify

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.regex.Pattern

/**
 * Integrity gate for the shipped prebuilt rule assets. Reads each locale file straight from
 * disk (no Android runtime needed) and asserts it stays structurally consistent with the
 * English default — so a bad translation is caught in CI, not in the field.
 */
class PrebuiltRulesFilesTest {

    private val gson = Gson()
    private val listType = object : TypeToken<List<BlockerRule>>() {}.type

    private val localeDirs = listOf(
        "raw", "raw-es", "raw-fr", "raw-ja", "raw-ko", "raw-pl", "raw-ru", "raw-zh-rCN"
    )

    private fun resDir(): File =
        listOf(File("src/main/res"), File("app/src/main/res"))
            .firstOrNull { it.isDirectory }
            ?: error("res dir not found; cwd=${File(".").absolutePath}")

    private fun load(dir: String): List<BlockerRule> {
        val f = File(resDir(), "$dir/prebuilt_rules.json")
        assertTrue("missing ${f.path}", f.isFile)
        return gson.fromJson(f.readText(Charsets.UTF_8), listType)
    }

    private fun packagesOf(rules: List<BlockerRule>) = rules.mapNotNull { it.packageName }.toSet()

    @Test
    fun `every locale file parses and is non-empty`() {
        for (dir in localeDirs) {
            val rules = load(dir)
            assertTrue("$dir should have rules", rules.isNotEmpty())
        }
    }

    @Test
    fun `package set matches english default in every locale`() {
        val englishPackages = packagesOf(load("raw"))
        for (dir in localeDirs) {
            assertEquals("package set mismatch in $dir", englishPackages, packagesOf(load(dir)))
        }
    }

    @Test
    fun `every rule has at least one filter`() {
        for (dir in localeDirs) {
            for (rule in load(dir)) {
                assertTrue(
                    "$dir: rule for ${rule.packageName} has no filter",
                    rule.titleFilter != null || rule.textFilter != null
                )
            }
        }
    }

    @Test
    fun `every regex filter compiles`() {
        for (dir in localeDirs) {
            for (rule in load(dir)) {
                if (rule.titleMatchType == MatchType.REGEX && rule.titleFilter != null) {
                    Pattern.compile(rule.titleFilter)
                }
                if (rule.textMatchType == MatchType.REGEX && rule.textFilter != null) {
                    Pattern.compile(rule.textFilter)
                }
            }
        }
    }
}
