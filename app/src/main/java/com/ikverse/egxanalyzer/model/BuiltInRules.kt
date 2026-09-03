package com.ikverse.egxanalyzer.model

/**
 * The wording the app ships knowing.
 *
 * These were a pair of lists in [AnalysisPolicy] until they became rows. They are the whole of the
 * local filter today, and moving them changed nothing about what they match - which is what the
 * equivalence test in `AnalysisPolicyTest` is for.
 *
 * An app update replaces this set wholesale. A user cannot delete one, because they are load
 * bearing, but can switch one off when a channel uses the phrase innocently.
 */
object BuiltInRules {

    private const val PREVIOUS_NOTE =
        "Stops a follow-up post being read as a new call: the card attached to it is the old one."

    private const val TARGET_HIT_NOTE =
        "Stops an already-worked call being extracted as live. When `حقق المستهدف` was missing " +
            "from this list, nine target-hit posts got through and three had rows pulled from " +
            "them - including the T+1 table reposted on 2 August, which duplicated four stocks."

    val all: List<WordingRule> = buildList {
        listOf(
            "السابق",
            "توصية سابقة",
            "توصيات سابقة",
            "التوصية السابقة",
            "التوصيات السابقة",
            "previous recommendation",
            "previous recommendations",
            "old recommendation",
            "old recommendations",
        ).forEach { add(builtIn(RuleSlot.EXCLUSION_PREVIOUS, it, PREVIOUS_NOTE)) }

        // Past tense by construction, which is what makes them safe: a live call says `يستهدف` or
        // `الهدف الأول`, never that the target was reached.
        listOf(
            "وصل الى المستهدف",
            "وصل للمستهدف",
            "تم الوصول الى المستهدف",
            "تم تحقيق المستهدف",
            "تحقق المستهدف",
            "تحقيق المستهدف",
            "حقق المستهدف",
            "حققنا نسبة ربح",
            "target reached",
            "reached target",
            "target achieved",
            "achieved target",
        ).forEach { add(builtIn(RuleSlot.EXCLUSION_TARGET_HIT, it, TARGET_HIT_NOTE)) }
    }

    /**
     * Ids are derived from the slot and the phrase rather than generated.
     *
     * A random id would differ on every device, so the same shipped rule would sync as two rows and
     * switching it off on one device would leave it on elsewhere.
     */
    fun idOf(slot: RuleSlot, phrase: String): String =
        "builtin:${slot.name.lowercase()}:${WordingRule.normalize(phrase)}"

    private fun builtIn(slot: RuleSlot, phrase: String, note: String) = WordingRule(
        id = idOf(slot, phrase),
        slot = slot,
        kind = RuleKind.EXCLUDE,
        phrase = phrase,
        // Local only: the prompt states the same idea in its own words in section 1, and saying it
        // twice in two vocabularies is how the two drift apart.
        scope = RuleScope.LOCAL,
        origin = RuleOrigin.BUILT_IN,
        note = note,
    )
}
