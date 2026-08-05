package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.RuleKind
import com.ikverse.egxanalyzer.model.RuleScope
import com.ikverse.egxanalyzer.model.RuleSlot
import com.ikverse.egxanalyzer.model.WordingRule
import java.security.MessageDigest

/** A prompt as it will be sent, and the identity that lets a run say which one it used. */
data class ComposedPrompt(
    val id: String,
    val text: String,
    val schemaVersion: Int?,
    /** Which rules were folded in, so a version can be explained without diffing 400 lines. */
    val ruleIds: List<String>,
)

/**
 * Builds the prompt that is actually sent, from the shipped one plus the rules in force.
 *
 * Always from the shipped one. Deriving each version from the last is how a prompt accumulates
 * instructions nobody added and keeps ones that were deleted - and it is why the old "custom system
 * prompt" box, which replaced the whole file, meant never receiving another prompt improvement.
 *
 * Wording lands at the section that decides the thing it is about, not at the end. A phrase that
 * identifies a T+1 card is useless three sections below the one about T+1 cards, and worse than
 * useless where it competes with the rules already covering that ground.
 */
object PromptComposer {

    private val anchor = Regex("""^[ \t]*<!--\s*EGX_RULES:\s*([a-z0-9.\-]+)\s*-->[ \t]*\r?\n?""", RegexOption.MULTILINE)

    fun compose(defaultPrompt: String, rules: RuleSet, useDefaultOnly: Boolean = false): ComposedPrompt {
        val active = if (useDefaultOnly) emptyList() else rules.promptRules()
        val bySlot = active.groupBy(WordingRule::slot)
        val text = anchor.replace(defaultPrompt) { match ->
            val slot = RuleSlot.entries.firstOrNull { it.anchor == match.groupValues[1] }
            // An anchor whose slot has nothing to say disappears entirely, which is what keeps a
            // removed rule from leaving a heading behind it.
            slot?.let { render(it, bySlot[it].orEmpty()) }.orEmpty()
        }
        return ComposedPrompt(
            id = identify(defaultPrompt, active),
            text = text,
            schemaVersion = schemaOf(defaultPrompt),
            ruleIds = active.map(WordingRule::id),
        )
    }

    private fun render(slot: RuleSlot, rules: List<WordingRule>): String {
        if (rules.isEmpty()) return ""
        fun line(kind: RuleKind, lead: String): String? = rules
            .filter { it.kind == kind }
            .map(WordingRule::phrase)
            .distinct()
            .takeIf(List<String>::isNotEmpty)
            ?.joinToString(", ") { "`$it`" }
            ?.let { "$lead $it." }

        return buildString {
            appendLine()
            appendLine("Managed wording, added by the operator. It extends the wording above and never")
            appendLine("overrides the exclusions, date eligibility, source isolation, or destination")
            appendLine("separation stated anywhere in this prompt.")
            line(RuleKind.INCLUDE, slot.includeLead)?.let { appendLine(it) }
            line(RuleKind.EXCLUDE, slot.excludeLead)?.let { appendLine(it) }
        }
    }

    /**
     * A version's identity is what produced it, hashed.
     *
     * A counter would need two devices to agree who reached seven first. A hash does not: the same
     * shipped prompt plus the same rules gives the same id everywhere, which is the whole of what
     * "reproducible across devices" needs.
     */
    private fun identify(defaultPrompt: String, rules: List<WordingRule>): String {
        val canonical = buildString {
            append(sha256(defaultPrompt))
            rules.sortedBy(WordingRule::id).forEach { rule ->
                appendLine()
                append(rule.id).append('|')
                append(rule.slot.name).append('|')
                append(rule.kind.name).append('|')
                append(rule.scope.name).append('|')
                append(rule.normalized)
            }
        }
        return sha256(canonical).take(16)
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }

    fun schemaOf(prompt: String): Int? =
        SCHEMA.find(prompt.take(512))?.groupValues?.get(1)?.toIntOrNull()

    private val SCHEMA = Regex("""<!--\s*EGX_PROMPT_SCHEMA:\s*(\d+)\s*-->""")
}

/** The rules that reach the model: enabled, not local-only, and with something left after trimming. */
internal fun RuleSet.promptRules(): List<WordingRule> = all
    .filter { it.enabled && it.scope != RuleScope.LOCAL && it.normalized.isNotBlank() }
