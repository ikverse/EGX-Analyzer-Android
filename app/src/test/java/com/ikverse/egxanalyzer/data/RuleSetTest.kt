package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.RuleKind
import com.ikverse.egxanalyzer.model.RuleOrigin
import com.ikverse.egxanalyzer.model.RuleScope
import com.ikverse.egxanalyzer.model.RuleSlot
import com.ikverse.egxanalyzer.model.WordingRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules in force are not the rows on disk.
 *
 * Shipped wording, a switched-off copy of it, and a user's own additions all have to resolve to one
 * answer before anything asks whether a source matches - and the filter and the prompt have to get
 * the same answer, or a report comes out wrong with nothing on screen to explain it.
 */
class RuleSetTest {

    private fun user(
        slot: RuleSlot = RuleSlot.SOURCE_DROP,
        kind: RuleKind = RuleKind.EXCLUDE,
        phrase: String,
        scope: RuleScope = RuleScope.LOCAL,
        enabled: Boolean = true,
        id: String = "user:$phrase",
    ) = WordingRule(id, slot, kind, phrase, scope, enabled, RuleOrigin.USER)

    @Test
    fun `a fresh install knows the wording the app ships with`() {
        val rules = RuleSet(emptyList())

        assertEquals(BuiltInRules.all.size, rules.all.size)
        assertTrue("حقق المستهدف" in rules.localPhrases(RuleSlot.EXCLUSION_TARGET_HIT))
        assertTrue("توصيه سابقه" in rules.localPhrases(RuleSlot.EXCLUSION_PREVIOUS))
    }

    /** Switching a shipped rule off has to survive a restart, which means storing that and only that. */
    @Test
    fun `a stored copy overrides a built in rule's switch but not its words`() {
        val shipped = BuiltInRules.all.first { it.slot == RuleSlot.EXCLUSION_TARGET_HIT }
        val rules = RuleSet(listOf(shipped.copy(enabled = false, phrase = "tampered", note = "gone")))

        val resolved = rules.all.single { it.id == shipped.id }
        assertFalse(resolved.enabled)
        assertEquals(shipped.phrase, resolved.phrase)
        assertEquals(shipped.note, resolved.note)
        assertTrue(shipped.normalized !in rules.localPhrases(RuleSlot.EXCLUSION_TARGET_HIT))
    }

    /**
     * An app update must be able to retire wording that turned out to misfire.
     *
     * A stored row for a rule the app no longer ships is dropped rather than resurrected, or the
     * only way to withdraw one would be to ask every user to delete it.
     */
    @Test
    fun `a built in rule the app no longer ships does not come back`() {
        val retired = WordingRule(
            id = BuiltInRules.idOf(RuleSlot.EXCLUSION_PREVIOUS, "wording since withdrawn"),
            slot = RuleSlot.EXCLUSION_PREVIOUS,
            kind = RuleKind.EXCLUDE,
            phrase = "wording since withdrawn",
            scope = RuleScope.LOCAL,
            origin = RuleOrigin.BUILT_IN,
        )

        val rules = RuleSet(listOf(retired))

        assertTrue(rules.all.none { it.phrase == "wording since withdrawn" })
    }

    @Test
    fun `a switched off rule is not matched against`() {
        val rules = RuleSet(listOf(user(phrase = "اعلان", enabled = false)))

        assertTrue(rules.localPhrases(RuleSlot.SOURCE_DROP).isEmpty())
    }

    /** Scope is the whole point: a rule decided here is not a rule the model is told about. */
    @Test
    fun `scope decides which side of the run a rule reaches`() {
        val rules = RuleSet(
            listOf(
                user(phrase = "local only", scope = RuleScope.LOCAL, id = "a"),
                user(phrase = "model only", scope = RuleScope.MODEL, id = "b"),
                user(phrase = "both", scope = RuleScope.BOTH, id = "c"),
            ),
        )

        assertEquals(listOf("local only", "both"), rules.localPhrases(RuleSlot.SOURCE_DROP))
        assertEquals(listOf("model only", "both"), rules.modelPhrases(RuleKind.EXCLUDE))
    }

    /** One phrase, however it was typed, is one rule. */
    @Test
    fun `a phrase spelled differently is still the same phrase`() {
        val rules = RuleSet(listOf(user(phrase = "حقق المستهدف")))

        val duplicate = user(phrase = "حقّق المستهدف ✅", id = "another")
        assertNotNull(rules.rejectionFor(duplicate))
    }

    @Test
    fun `the opposite of an existing rule is refused rather than resolved`() {
        val rules = RuleSet(listOf(user(phrase = "متابعة", kind = RuleKind.EXCLUDE)))

        val opposite = user(phrase = "متابعة", kind = RuleKind.INCLUDE, id = "another")
        val rejection = rules.rejectionFor(opposite)

        assertNotNull(rejection)
        assertTrue(rejection!!.message.contains("Edit that one"))
    }

    /** The same words under a different question are a different rule, and allowed. */
    @Test
    fun `the same phrase in another slot is a different rule`() {
        val rules = RuleSet(listOf(user(slot = RuleSlot.SOURCE_DROP, phrase = "متابعة")))

        assertNull(rules.rejectionFor(user(slot = RuleSlot.SOURCE_KEEP, phrase = "متابعة", id = "b")))
    }

    @Test
    fun `a phrase of nothing but decoration is refused`() {
        assertNotNull(RuleSet(emptyList()).rejectionFor(user(phrase = "  ✅ ")))
    }

    @Test
    fun `editing a rule does not collide with itself`() {
        val existing = user(phrase = "متابعة")
        val rules = RuleSet(listOf(existing))

        assertNull(rules.rejectionFor(existing.copy(scope = RuleScope.BOTH)))
    }
}
