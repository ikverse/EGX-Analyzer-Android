package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.AnalysisInput
import com.ikverse.egxanalyzer.model.ExcludedSource
import com.ikverse.egxanalyzer.model.RuleSlot
import com.ikverse.egxanalyzer.model.WordingRule

data class FilteredAnalysisInputs(
    val accepted: List<AnalysisInput>,
    val excluded: List<ExcludedSource>,
)

/**
 * Decides what can be settled from a source's own words before anything is sent.
 *
 * Three outcomes, not two: a phrase that drops the source, a phrase that keeps it whatever else it
 * says, and anything the words do not settle going to the model. Channels caption their cards, and
 * a caption saying the call is from the previous session is worth more than the picture - it costs
 * nothing to read and saves an image from being sent at all.
 *
 * The phrases are rows now rather than lists here, because every channel added brings wording the
 * last one did not use.
 */
object AnalysisPolicy {

    fun filter(
        inputs: List<AnalysisInput>,
        rules: RuleSet,
    ): FilteredAnalysisInputs {
        val keep = rules.localPhrases(RuleSlot.SOURCE_KEEP)
        val drop = rules.localPhrases(RuleSlot.SOURCE_DROP)
        val previous = rules.localPhrases(RuleSlot.EXCLUSION_PREVIOUS)
        val targetHit = rules.localPhrases(RuleSlot.EXCLUSION_TARGET_HIT)
        val textBySource = inputs.filterIsInstance<AnalysisInput.Text>()
            .groupBy(AnalysisInput.Text::sourceId)
            .mapValues { (_, values) -> values.joinToString("\n", transform = AnalysisInput.Text::value) }
        val excluded = mutableListOf<ExcludedSource>()
        val rejectedIds = textBySource.mapNotNull { (sourceId, value) ->
            // The source's words go through the same normalizer the rules did, or a phrase stored
            // one way would never match the same phrase typed another.
            val normalized = WordingRule.normalize(value)
            // Order is the rule: keeping wins over every reason to drop, and the reason recorded is
            // the first that applied, so a source excluded twice is still reported once.
            val reason = when {
                keep.any(normalized::contains) -> null
                drop.any(normalized::contains) -> "custom_exclude_phrase"
                previous.any(normalized::contains) -> "previous_recommendation"
                targetHit.any(normalized::contains) -> "target_already_hit"
                else -> null
            }
            reason?.let {
                excluded += ExcludedSource(sourceId, it)
                sourceId
            }
        }.toSet()
        return FilteredAnalysisInputs(
            accepted = inputs.filterNot { it.sourceId in rejectedIds },
            excluded = excluded,
        )
    }
}
