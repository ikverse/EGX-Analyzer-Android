package com.ikverse.egxanalyzer.model

/**
 * A phrase the app recognises, and what recognising it does.
 *
 * Channels word the same idea differently - one says `حقق المستهدف`, the next says `تم تحقيق
 * المستهدف` - so adding a channel means adding wording, and until now that meant editing Kotlin.
 * These rows are that wording, moved somewhere it can be read and changed.
 */
data class WordingRule(
    val id: String,
    /** Which decision the phrase feeds. A phrase means nothing without the question it answers. */
    val slot: RuleSlot,
    val kind: RuleKind,
    /** As typed, so it can be read back and corrected. Matching uses [normalized]. */
    val phrase: String,
    val scope: RuleScope,
    val enabled: Boolean = true,
    val origin: RuleOrigin = RuleOrigin.USER,
    /**
     * The chats this applies to, empty meaning all of them.
     *
     * Carried from the start though nothing sets it yet: a column added later would have to be
     * back-filled on every device and through the sync, which is the expensive way to arrive here.
     */
    val channels: Set<Long> = emptySet(),
    /** Why the rule exists. The built-in rows carry the incident that put them there. */
    val note: String? = null,
    val updatedAt: Long = 0L,
    val updatedBy: String = "",
) {
    /** What matching and duplicate detection actually compare. */
    val normalized: String get() = normalize(phrase)

    /** Two rules are the same rule when they answer the same question with the same words. */
    val identity: Triple<RuleSlot, RuleKind, String> get() = Triple(slot, kind, normalized)

    companion object {
        private val diacritics = Regex("[\\u0610-\\u061A\\u064B-\\u065F\\u0670\\u06D6-\\u06ED\\u0640]")

        /**
         * Emoji, variation selectors and joiners, which these captions end and interrupt phrases
         * with. Replaced with a space rather than removed, so a phrase split by one still reads as
         * two words and a phrase followed by one is not glued to whatever comes next.
         */
        private val symbols = Regex("[\\p{So}\\p{Sk}\\p{Cf}\\uFE0E\\uFE0F]")
        private val whitespace = Regex("\\s+")

        /**
         * Strips what a channel varies without meaning to.
         *
         * Diacritics and tatweel are decoration, and the alef, ya and ta-marbuta spellings differ
         * between typists writing the same word: `حقّق المستهدف` and `حقق المستهدف` are one phrase.
         * The source text is put through this same function before matching, which is the only
         * reason a stored phrase matches a typed one at all.
         */
        fun normalize(value: String): String = diacritics
            .replace(value.lowercase(), "")
            .replace('أ', 'ا')
            .replace('إ', 'ا')
            .replace('آ', 'ا')
            .replace('ى', 'ي')
            .replace('ة', 'ه')
            .let { symbols.replace(it, " ") }
            .let { whitespace.replace(it, " ") }
            .trim()
    }
}

/**
 * Which question a phrase answers.
 *
 * Kept small on purpose: every slot here is one the app already decides today. Slots that feed the
 * model prompt arrive with the prompt composition that generates it.
 */
enum class RuleSlot(
    val title: String,
    val explanation: String,
    /**
     * The marker in the shipped prompt this slot's wording is written into.
     *
     * Wording belongs at the section that decides the thing it is about. A phrase identifying a
     * T+1 card is useless three sections below the one about T+1 cards.
     */
    val anchor: String,
    val includeLead: String,
    val excludeLead: String,
) {
    SOURCE_KEEP(
        "Always keep",
        "A source containing this is analysed whatever else it says. Checked first, so it " +
            "overrides every rule below.",
        anchor = "source.keep",
        includeLead = "Treat a source as eligible when it carries any of:",
        excludeLead = "Do not treat a source as eligible on the strength of:",
    ),
    SOURCE_DROP(
        "Always drop",
        "A source containing this is dropped before anything is sent.",
        anchor = "source.drop",
        includeLead = "Exclude an entire source item carrying any of:",
        excludeLead = "Do not exclude a source merely for carrying:",
    ),
    EXCLUSION_PREVIOUS(
        "Follow-up on an earlier call",
        "Wording that says the post is revisiting a call already made, which makes its card an " +
            "old one reposted rather than a new recommendation.",
        anchor = "exclusion.previous",
        includeLead = "Also read as a follow-up on an earlier call:",
        excludeLead = "Do not read as a follow-up merely on the strength of:",
    ),
    EXCLUSION_TARGET_HIT(
        "Target already reached",
        "Wording that announces the call already worked. Same consequence: the card is history, " +
            "not a call you can still take.",
        anchor = "exclusion.target-hit",
        includeLead = "Also read as announcing a target already reached:",
        excludeLead = "Do not read as a reached target merely on the strength of:",
    ),
    DATE_EXPLICIT(
        "Names its own session",
        "Wording that states which session the call is for, which is what makes a card eligible " +
            "rather than undated.",
        anchor = "date.explicit",
        includeLead = "Also read as naming the session explicitly:",
        excludeLead = "Do not read as naming a session:",
    ),
    DESTINATION_MAIN(
        "A live recommendation",
        "Wording that marks a card as a call to take now, as opposed to a watch, a T+1 trade or " +
            "an answer to a client.",
        anchor = "destination.main",
        includeLead = "Also classify as a main recommendation when the source prints:",
        excludeLead = "Do not classify as a main recommendation on the strength of:",
    ),
    DESTINATION_WATCHING(
        "On the watch list",
        "Wording that marks a stock as watched rather than called, so it is recorded without " +
            "being scored as a live entry.",
        anchor = "destination.watching",
        includeLead = "Also classify as Watching when the source prints:",
        excludeLead = "Do not classify as Watching on the strength of:",
    ),
    DESTINATION_T_PLUS_1(
        "A T+1 trade",
        "Wording that marks a card as a trade between today's close and tomorrow's open.",
        anchor = "destination.t-plus-1",
        includeLead = "Also classify as T+1 when the source prints:",
        excludeLead = "Do not classify as T+1 on the strength of:",
    ),
    DESTINATION_INQUIRY(
        "An answer to a client",
        "Wording that marks a post as a reply to someone who asked, rather than a call the " +
            "channel is making.",
        anchor = "destination.inquiry",
        includeLead = "Also classify as a client inquiry when the source prints:",
        excludeLead = "Do not classify as a client inquiry on the strength of:",
    ),
}

enum class RuleKind(val title: String) {
    INCLUDE("Include"),
    EXCLUDE("Exclude"),
}

/** Where a rule is applied, which is the thing the old free-text fields could never say. */
enum class RuleScope(val title: String, val explanation: String) {
    LOCAL("On this device", "Decided here from the source's own words. Costs nothing and sends nothing."),
    MODEL("In the prompt", "Added to what the model is told. Only affects sources that reach it."),
    BOTH("Both", "Decided here, and told to the model for the sources that get through."),
}

enum class RuleOrigin {
    /** Shipped with the app. Can be switched off, cannot be deleted, replaced by app updates. */
    BUILT_IN,
    USER,
}
