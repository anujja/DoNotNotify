package com.donotnotify.donotnotify

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class BackupRulesTest {

    private fun backupPolicies(): List<Element> {
        val resDir = listOf(File("src/main/res"), File("app/src/main/res"))
            .first { it.isDirectory }
        val parser = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        val legacy = parser.parse(File(resDir, "xml/backup_rules.xml")).documentElement
        val modern = parser.parse(File(resDir, "xml/data_extraction_rules.xml"))
        return listOf(
            legacy,
            modern.getElementsByTagName("cloud-backup").item(0) as Element,
            modern.getElementsByTagName("device-transfer").item(0) as Element
        )
    }

    // Android includes all eligible data unless at least one include rule is present;
    // exclusions take precedence. Check effective coverage rather than exact XML text.
    private fun Element.includes(domain: String, path: String): Boolean {
        fun Element.matches(): Boolean {
            val rulePath = getAttribute("path").trimEnd('/')
            return getAttribute("domain") == domain &&
                (rulePath == "." || path == rulePath || path.startsWith("$rulePath/"))
        }

        val includes = getElementsByTagName("include")
        val excludes = getElementsByTagName("exclude")
        val included = includes.length == 0 || (0 until includes.length).any {
            (includes.item(it) as Element).matches()
        }
        val excluded = (0 until excludes.length).any {
            (excludes.item(it) as Element).matches()
        }
        return included && !excluded
    }

    @Test
    fun `notification histories and temporary copies stay out of every backup transport`() {
        val historyFiles = listOf(
            "notification_history.json",
            "notification_history.json.tmp",
            "blocked_notification_history.json"
        )
        for (policy in backupPolicies()) {
            for (path in historyFiles) {
                assertFalse("${policy.tagName} must not back up $path", policy.includes("file", path))
            }
        }
    }

    @Test
    fun `rules and user preferences remain eligible for backup`() {
        for (policy in backupPolicies()) {
            assertTrue("${policy.tagName} must retain rules", policy.includes("file", "rules.json"))
            for (path in listOf("settings.xml", "unmonitored_apps_prefs.xml", "stats.xml")) {
                assertTrue("${policy.tagName} must retain $path", policy.includes("sharedpref", path))
            }
        }
    }

    @Test
    fun `unlisted files and cached databases are not backed up`() {
        for (policy in backupPolicies()) {
            assertFalse(policy.includes("file", "future_notification_history.json"))
            assertFalse(policy.includes("file", "rules.json.corrupt.123"))
            assertFalse(policy.includes("database", "app_info.db"))
        }
    }
}
