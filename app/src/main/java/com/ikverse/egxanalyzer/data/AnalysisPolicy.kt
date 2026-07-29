package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.AnalysisInput
import com.ikverse.egxanalyzer.model.ExcludedSource

data class FilteredAnalysisInputs(
    val accepted: List<AnalysisInput>,
    val excluded: List<ExcludedSource>,
)

object AnalysisPolicy {
    private val arabicDiacritics = Regex("[\\u0610-\\u061A\\u064B-\\u065F\\u0670\\u06D6-\\u06ED\\u0640]")
    private val previousRecommendationMarkers = listOf(
        "السابق",
        "توصية سابقة",
        "توصيات سابقة",
        "التوصية السابقة",
        "التوصيات السابقة",
        "previous recommendation",
        "previous recommendations",
        "old recommendation",
        "old recommendations",
    )
    private val targetHitMarkers = listOf(
        "وصل الى المستهدف",
        "وصل للمستهدف",
        "تم الوصول الى المستهدف",
        "تم تحقيق المستهدف",
        "تحقق المستهدف",
        "تحقيق المستهدف",
        "target reached",
        "reached target",
        "target achieved",
        "achieved target",
    )

    fun filter(
        inputs: List<AnalysisInput>,
        includePhrases: String,
        excludePhrases: String,
    ): FilteredAnalysisInputs {
        val includes = phrases(includePhrases)
        val excludes = phrases(excludePhrases)
        val textBySource = inputs.filterIsInstance<AnalysisInput.Text>()
            .groupBy(AnalysisInput.Text::sourceId)
            .mapValues { (_, values) -> values.joinToString("\n", transform = AnalysisInput.Text::value) }
        val excluded = mutableListOf<ExcludedSource>()
        val rejectedIds = textBySource.mapNotNull { (sourceId, value) ->
            val normalized = normalize(value)
            val included = includes.any(normalized::contains)
            val reason = when {
                included -> null
                excludes.any(normalized::contains) -> "custom_exclude_phrase"
                previousRecommendationMarkers.any { normalize(it) in normalized } ->
                    "previous_recommendation"
                targetHitMarkers.any { normalize(it) in normalized } -> "target_already_hit"
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

    private fun phrases(value: String): List<String> =
        value.split('\n', ',', ';').map(::normalize).filter(String::isNotBlank)

    private fun normalize(value: String): String = arabicDiacritics
        .replace(value.lowercase(), "")
        .replace('أ', 'ا')
        .replace('إ', 'ا')
        .replace('آ', 'ا')
        .replace('ى', 'ي')
        .replace('ة', 'ه')
}
