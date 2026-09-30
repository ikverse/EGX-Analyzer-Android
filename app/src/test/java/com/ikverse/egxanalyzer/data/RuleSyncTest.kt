package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.RuleKind
import com.ikverse.egxanalyzer.model.RuleScope
import com.ikverse.egxanalyzer.model.RuleSlot
import com.ikverse.egxanalyzer.model.WordingRule
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A table is not a set of files.
 *
 * Reports never change once written, so syncing them is a union. Rules are edited, switched off and
 * deleted, and two devices can do different things to one while both are offline - so what travels
 * is each revision, and the merge decides rather than whoever uploaded last.
 */
class RuleSyncTest {

    private fun revision(
        id: String = "rule-1",
        phrase: String = "حقق المستهدف",
        at: Long = 1_000,
        by: String = "phone",
        enabled: Boolean = true,
        deleted: Boolean = false,
    ) = SyncedRule(
        rule = WordingRule(
            id = id,
            slot = RuleSlot.EXCLUSION_TARGET_HIT,
            kind = RuleKind.EXCLUDE,
            phrase = phrase,
            scope = RuleScope.LOCAL,
            enabled = enabled,
            updatedAt = at,
            updatedBy = by,
        ),
        deleted = deleted,
    )

    @Test
    fun `a revision survives the round trip`() {
        val original = revision()

        assertEquals(original, SyncedRule.fromDocument(original.toDocument()))
    }

    @Test
    fun `the file name carries the rule and the revision`() {
        val name = revision(at = 42).fileName

        assertEquals("rule-rule-1-42.json", name)
        assertEquals("rule-1", SyncedRule.ruleIdOf(name))
    }

    /** A channel holds whatever anyone dropped in it; only this app's rule files are ours to read. */
    @Test
    fun `a file that is not a rule is ignored rather than guessed at`() {
        assertNull(SyncedRule.ruleIdOf("holiday-photo.jpg"))
        assertNull(SyncedRule.ruleIdOf("0eb9d2ec.json"))
        assertNull(SyncedRule.fromDocument("not json at all"))
        assertNull(SyncedRule.fromDocument("""{"phrase":"no id"}"""))
    }

    @Test
    fun `the latest revision of a rule is the one that counts`() {
        val merged = mergeRules(
            listOf(
                revision(phrase = "old", at = 1_000),
                revision(phrase = "new", at = 2_000),
                revision(phrase = "older", at = 500),
            ),
        )

        assertEquals(listOf("new"), merged.map { it.rule.phrase })
    }

    /** Two devices editing in the same millisecond still have to reach the same answer. */
    @Test
    fun `a tie is broken the same way on every device`() {
        val one = listOf(revision(phrase = "a", by = "phone"), revision(phrase = "b", by = "tablet"))

        assertEquals(mergeRules(one).single(), mergeRules(one.reversed()).single())
        assertEquals("b", mergeRules(one).single().rule.phrase)
    }

    /**
     * A delete is a revision, not a special case.
     *
     * It can be overtaken by a later edit, and it can never be undone by an earlier one - which is
     * what stops a device that still holds the rule putting it back.
     */
    @Test
    fun `a delete outlives every edit made before it`() {
        val merged = mergeRules(
            listOf(revision(at = 1_000), revision(at = 2_000, deleted = true)),
        )

        assertTrue(merged.single().deleted)
    }

    @Test
    fun `a later edit brings a deleted rule back`() {
        val merged = mergeRules(
            listOf(revision(at = 2_000, deleted = true), revision(at = 3_000, phrase = "again")),
        )

        assertEquals("again", merged.single().rule.phrase)
        assertTrue(!merged.single().deleted)
    }

    @Test
    fun `only what this device knows better is uploaded`() {
        val mine = listOf(
            revision(id = "a", at = 3_000),
            revision(id = "b", at = 1_000),
            revision(id = "c", at = 1_000),
        )
        val theirs = listOf(revision(id = "a", at = 2_000), revision(id = "b", at = 5_000))

        assertEquals(listOf("a", "c"), rulesToUpload(mine, theirs).map { it.rule.id })
    }

    /**
     * An older app must not quietly strip what a newer one wrote.
     *
     * Reading a rule it only half understands and uploading it back is how a field added in an
     * update disappears from every device that has not updated yet.
     */
    @Test
    fun `a field this version does not know is carried through untouched`() {
        val document = JSONObject(revision().toDocument())
            .put("channels", org.json.JSONArray(listOf(1L, 2L)))
            .put("somethingNewer", "keep me")
            .toString()

        val parsed = requireNotNull(SyncedRule.fromDocument(document))

        assertEquals(setOf(1L, 2L), parsed.rule.channels)
        assertEquals("keep me", JSONObject(parsed.toDocument()).getString("somethingNewer"))
    }
}
