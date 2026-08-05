package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.RuleKind
import com.ikverse.egxanalyzer.model.RuleOrigin
import com.ikverse.egxanalyzer.model.RuleScope
import com.ikverse.egxanalyzer.model.RuleSlot
import com.ikverse.egxanalyzer.model.WordingRule

/** Why a rule could not be added, in the words the person adding it needs. */
data class RuleRejection(val message: String)

/**
 * The rules in force, and the questions asked of them.
 *
 * A list plus the shipped set is not the same thing as the rules in force: a switched-off row, a
 * user row that shadows a built-in one, and a phrase that normalizes to nothing all have to be
 * resolved before anything asks whether a source matches. That resolution lives here so the filter
 * and the prompt cannot disagree about it.
 */
class RuleSet(stored: List<WordingRule>) {

    /**
     * Shipped rows first, then stored ones over the top by id.
     *
     * A stored row wins so switching off a built-in survives a restart; a shipped row that no
     * longer exists disappears even if a device still has it stored, so an app update can retire
     * wording that turned out to misfire.
     */
    val all: List<WordingRule> = run {
        val storedById = stored.associateBy(WordingRule::id)
        val builtIn = BuiltInRules.all.map { storedById[it.id]?.copy(
            phrase = it.phrase,
            slot = it.slot,
            kind = it.kind,
            note = it.note,
            origin = it.origin,
        ) ?: it }
        val shippedIds = BuiltInRules.all.map(WordingRule::id).toSet()
        // A stored row claiming to be built-in that the app no longer ships is dropped, not kept:
        // withdrawing wording that misfires has to work without asking every user to delete it.
        builtIn + stored.filter { it.id !in shippedIds && it.origin != RuleOrigin.BUILT_IN }
    }

    private val active = all.filter { it.enabled && it.normalized.isNotBlank() }

    fun of(slot: RuleSlot): List<WordingRule> = all.filter { it.slot == slot }

    /** The phrases a local decision compares against, for one slot. */
    fun localPhrases(slot: RuleSlot): List<String> = active
        .filter { it.slot == slot && it.scope != RuleScope.MODEL }
        .map(WordingRule::normalized)

    /** The phrases the model is told about, by kind, whatever slot they came from. */
    fun modelPhrases(kind: RuleKind): List<String> = active
        .filter { it.kind == kind && it.scope != RuleScope.LOCAL }
        .map(WordingRule::phrase)
        .distinct()

    /**
     * Whether a rule can join the set.
     *
     * Two rows that say the same thing are noise; two that say opposite things are a bug nobody
     * can see, because which one wins depends on the order they happen to be read in. Both are
     * refused at the door and named, rather than resolved silently.
     */
    fun rejectionFor(candidate: WordingRule): RuleRejection? {
        if (candidate.normalized.isBlank()) {
            return RuleRejection("Enter a phrase.")
        }
        val clash = all.firstOrNull {
            it.id != candidate.id &&
                it.slot == candidate.slot &&
                it.normalized == candidate.normalized
        } ?: return null
        return if (clash.kind == candidate.kind) {
            RuleRejection("\"${clash.phrase}\" already sits under ${candidate.slot.title}.")
        } else {
            RuleRejection(
                "\"${clash.phrase}\" is already an ${clash.kind.title.lowercase()} rule under " +
                    "${candidate.slot.title}. Edit that one instead of adding its opposite.",
            )
        }
    }
}
