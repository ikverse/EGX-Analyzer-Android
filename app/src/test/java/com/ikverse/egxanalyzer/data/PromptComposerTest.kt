package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.RuleKind
import com.ikverse.egxanalyzer.model.RuleOrigin
import com.ikverse.egxanalyzer.model.RuleScope
import com.ikverse.egxanalyzer.model.RuleSlot
import com.ikverse.egxanalyzer.model.WordingRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every version is built from the prompt the app ships, never from the version before it.
 *
 * That is the whole design. Appending to the last generated prompt is how one collects instructions
 * nobody added and keeps ones that were deleted, and it is what made the old whole-file override
 * freeze anyone who used it out of every prompt improvement.
 */
class PromptComposerTest {

    private val shipped = """
        <!-- EGX_PROMPT_SCHEMA: 7 -->
        # Prompt

        ## 1. Exclusions
        Existing wording.
        <!-- EGX_RULES: exclusion.target-hit -->

        ## 3. Destinations
        <!-- EGX_RULES: destination.watching -->
        End.
    """.trimIndent()

    private fun rule(
        slot: RuleSlot,
        phrase: String,
        kind: RuleKind = RuleKind.INCLUDE,
        scope: RuleScope = RuleScope.MODEL,
        enabled: Boolean = true,
    ) = WordingRule("id:$slot:$phrase", slot, kind, phrase, scope, enabled, RuleOrigin.USER)

    private fun compose(vararg rules: WordingRule, defaultOnly: Boolean = false) =
        PromptComposer.compose(shipped, RuleSet(rules.toList()), defaultOnly)

    @Test
    fun `an anchor with nothing to say leaves no trace`() {
        val composed = compose()

        assertFalse(composed.text.contains("EGX_RULES"))
        assertFalse(composed.text.contains("Managed wording"))
        assertTrue(composed.text.contains("Existing wording."))
    }

    /** Wording lands at the section that decides the thing it is about, not at the end. */
    @Test
    fun `wording is written into its own section`() {
        val composed = compose(rule(RuleSlot.DESTINATION_WATCHING, "تحت المراقبة"))

        val watchingAt = composed.text.indexOf("تحت المراقبة")
        assertTrue(watchingAt > composed.text.indexOf("## 3. Destinations"))
        assertTrue(watchingAt < composed.text.indexOf("End."))
    }

    @Test
    fun `include and exclude wording read as different instructions`() {
        val composed = compose(
            rule(RuleSlot.EXCLUSION_TARGET_HIT, "حقق الهدف"),
            rule(RuleSlot.EXCLUSION_TARGET_HIT, "قريب من الهدف", kind = RuleKind.EXCLUDE),
        )

        assertTrue(composed.text.contains(RuleSlot.EXCLUSION_TARGET_HIT.includeLead))
        assertTrue(composed.text.contains(RuleSlot.EXCLUSION_TARGET_HIT.excludeLead))
    }

    /**
     * The point of composing from the shipped text every time.
     *
     * Removing a rule has to remove its wording, not leave it behind under a heading nobody can
     * find their way back to.
     */
    @Test
    fun `a removed rule leaves nothing behind`() {
        val added = compose(rule(RuleSlot.DESTINATION_WATCHING, "تحت المراقبة"))
        val removed = compose()

        assertTrue(added.text.contains("تحت المراقبة"))
        assertFalse(removed.text.contains("تحت المراقبة"))
        assertEquals(compose().text, removed.text)
    }

    @Test
    fun `a rule that stays on this device never reaches the prompt`() {
        val composed = compose(rule(RuleSlot.DESTINATION_WATCHING, "محلي", scope = RuleScope.LOCAL))

        assertFalse(composed.text.contains("محلي"))
        assertTrue(composed.ruleIds.isEmpty())
    }

    @Test
    fun `a switched off rule never reaches the prompt`() {
        val composed = compose(rule(RuleSlot.DESTINATION_WATCHING, "مطفأ", enabled = false))

        assertFalse(composed.text.contains("مطفأ"))
    }

    @Test
    fun `the default-only switch sends the shipped prompt untouched`() {
        val composed = compose(rule(RuleSlot.DESTINATION_WATCHING, "تحت المراقبة"), defaultOnly = true)

        assertEquals(compose().text, composed.text)
        assertTrue(composed.ruleIds.isEmpty())
    }

    /**
     * Two devices with the same configuration must agree on the version without talking.
     *
     * A counter would need them to agree who reached seven first; a hash of what produced the
     * prompt needs nothing at all.
     */
    @Test
    fun `the same configuration is the same version whoever composed it`() {
        val here = compose(rule(RuleSlot.DESTINATION_WATCHING, "تحت المراقبة"))
        val there = compose(rule(RuleSlot.DESTINATION_WATCHING, "تحت المراقبة"))

        assertEquals(here.id, there.id)
    }

    @Test
    fun `the order rules were added in does not change the version`() {
        val one = rule(RuleSlot.DESTINATION_WATCHING, "أ")
        val two = rule(RuleSlot.EXCLUSION_TARGET_HIT, "ب")

        assertEquals(compose(one, two).id, compose(two, one).id)
    }

    @Test
    fun `changing anything that reaches the model changes the version`() {
        val base = compose(rule(RuleSlot.DESTINATION_WATCHING, "أ"))

        assertNotEquals(base.id, compose().id)
        assertNotEquals(base.id, compose(rule(RuleSlot.DESTINATION_WATCHING, "ب")).id)
        assertNotEquals(
            base.id,
            compose(rule(RuleSlot.DESTINATION_WATCHING, "أ", kind = RuleKind.EXCLUDE)).id,
        )
    }

    /** A new shipped prompt is a new baseline, even with the rules untouched. */
    @Test
    fun `an updated shipped prompt is a new version by itself`() {
        val before = PromptComposer.compose(shipped, RuleSet(emptyList()))
        val after = PromptComposer.compose(shipped + "\nOne more rule.", RuleSet(emptyList()))

        assertNotEquals(before.id, after.id)
    }

    /**
     * The guarantee that matters most.
     *
     * With nothing configured, what is sent must be the shipped file exactly - not the shipped file
     * plus the residue of markers that had nothing to put in them. Nine blank lines crept in that
     * way once, and a prompt that drifts when you have changed nothing is a prompt nobody can
     * reason about.
     */
    @Test
    fun `nothing configured leaves no residue where the markers were`() {
        val bare = """
            <!-- EGX_PROMPT_SCHEMA: 7 -->
            # Prompt

            ## 1. Exclusions
            Existing wording.

            ## 3. Destinations
            End.
        """.trimIndent()

        assertEquals(bare, compose().text)
        assertEquals(bare, compose(rule(RuleSlot.DESTINATION_WATCHING, "x"), defaultOnly = true).text)
    }

    /**
     * The authoring rule that keeps the guarantee true of the real file.
     *
     * A marker written after a blank line leaves that blank line behind when it is removed, and the
     * prompt drifts from the shipped one by a line per marker without anyone touching a rule. Nine
     * of them crept in exactly that way.
     */
    @Test
    fun `no marker in the shipped prompt sits after a blank line`() {
        val asset = java.io.File("src/main/assets/consolidated_recommendation.md").readText()
        val offenders = Regex("""
[ 	]*?
[ 	]*<!--\s*EGX_RULES:\s*([a-z0-9.\-]+)""")
            .findAll(asset)
            .map { it.groupValues[1] }
            .toList()

        assertEquals(emptyList<String>(), offenders)
    }

    @Test
    fun `every marker in the shipped prompt names a slot the app knows`() {
        val asset = java.io.File("src/main/assets/consolidated_recommendation.md").readText()
        val named = Regex("""<!--\s*EGX_RULES:\s*([a-z0-9.\-]+)\s*-->""")
            .findAll(asset)
            .map { it.groupValues[1] }
            .toSet()

        assertEquals(emptySet<String>(), named - RuleSlot.entries.map(RuleSlot::anchor).toSet())
        assertTrue(named.isNotEmpty())
    }

    @Test
    fun `the composed prompt reports the schema it was built from`() {
        assertEquals(7, compose().schemaVersion)
    }
}
