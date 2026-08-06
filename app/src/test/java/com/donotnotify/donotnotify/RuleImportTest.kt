package com.donotnotify.donotnotify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleImportTest {

    private fun successOf(json: String): ImportResult.Success {
        val r = RuleImport.parse(json)
        assertTrue("expected Success but was $r", r is ImportResult.Success)
        return r as ImportResult.Success
    }

    private fun errorOf(json: String): ImportError {
        val r = RuleImport.parse(json)
        assertTrue("expected Error but was $r", r is ImportResult.Error)
        return (r as ImportResult.Error).reason
    }

    @Test
    fun `legacy bare array imports`() {
        val s = successOf("""[{"packageName":"com.x","textFilter":"foo","ruleType":"DENYLIST"}]""")
        assertEquals(1, s.rules.size)
        assertEquals(null, s.locale)
        assertEquals(0, s.droppedCount)
    }

    @Test
    fun `versioned envelope imports with locale`() {
        val s = successOf("""{"version":2,"locale":"ja","rules":[{"packageName":"com.x","textFilter":"foo"}]}""")
        assertEquals(1, s.rules.size)
        assertEquals("ja", s.locale)
    }

    @Test
    fun `envelope missing rules is schema mismatch`() {
        assertEquals(ImportError.SchemaMismatch, errorOf("""{"version":2}"""))
    }

    @Test
    fun `envelope with non-array rules is schema mismatch`() {
        assertEquals(ImportError.SchemaMismatch, errorOf("""{"rules":{}}"""))
    }

    @Test
    fun `null entries in rules array are dropped`() {
        val s = successOf("""{"rules":[null,{"packageName":"com.x","textFilter":"foo"}]}""")
        assertEquals(1, s.rules.size)
        assertEquals(1, s.droppedCount)
    }

    @Test
    fun `rule with no package and no filters is dropped`() {
        val s = successOf("""[{"packageName":null,"titleFilter":null,"textFilter":null}]""")
        assertTrue(s.rules.isEmpty())
        assertEquals(1, s.droppedCount)
    }

    @Test
    fun `unknown enum is normalized to default`() {
        val s = successOf("""[{"packageName":"com.x","textFilter":"foo","ruleType":"BOGUS"}]""")
        assertEquals(1, s.rules.size)
        assertEquals(RuleType.DENYLIST, s.rules[0].ruleType)
        assertEquals(MatchType.CONTAINS, s.rules[0].textMatchType)
    }

    @Test
    fun `unknown future version is best-effort parsed`() {
        val s = successOf("""{"version":99,"rules":[{"packageName":"com.x","textFilter":"foo"}]}""")
        assertEquals(1, s.rules.size)
    }

    @Test
    fun `over-length regex rule is dropped`() {
        val longPattern = "a".repeat(RuleImport.MAX_REGEX_LENGTH + 50)
        val s = successOf("""[{"packageName":"com.x","textFilter":"$longPattern","textMatchType":"REGEX"}]""")
        assertTrue(s.rules.isEmpty())
        assertEquals(1, s.droppedCount)
    }

    @Test
    fun `valid regex under cap is kept`() {
        val s = successOf("""[{"packageName":"com.x","textFilter":"(?i).*(offer|sale).*","textMatchType":"REGEX"}]""")
        assertEquals(1, s.rules.size)
    }

    @Test
    fun `invalid regex is dropped`() {
        val s = successOf("""[{"packageName":"com.x","textFilter":"(unclosed","textMatchType":"REGEX"}]""")
        assertTrue(s.rules.isEmpty())
        assertEquals(1, s.droppedCount)
    }

    @Test
    fun `malformed json is reported`() {
        assertEquals(ImportError.Malformed, errorOf("not json {"))
    }

    @Test
    fun `empty array is reported as empty`() {
        assertEquals(ImportError.Empty, errorOf("""[]"""))
    }

    @Test
    fun `non-array non-object root is schema mismatch`() {
        assertEquals(ImportError.SchemaMismatch, errorOf("""42"""))
    }

    @Test
    fun `legacy minified envelope from pre-6_2 release builds is accepted`() {
        // Release builds up to 6.1 lacked @Keep on RuleExport, so R8 renamed the envelope
        // fields: locale -> "a", rules -> "b". Files exported by those builds must import.
        val s = successOf(
            """{"a":"en-US","b":[{"packageName":"com.x","textFilter":"spam","textMatchType":"CONTAINS"}]}"""
        )
        assertEquals(1, s.rules.size)
        assertEquals("en-US", s.locale)
        assertEquals("com.x", s.rules[0].packageName)
    }
}
