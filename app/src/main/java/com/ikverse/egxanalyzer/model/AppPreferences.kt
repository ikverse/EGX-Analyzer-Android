package com.ikverse.egxanalyzer.model


enum class ThemeMode(val displayName: String) {
    SYSTEM("Follow system"),
    LIGHT("Light"),
    DARK("Dark"),
}

enum class AnalysisLanguage(val displayName: String, val promptInstruction: String) {
    ARABIC("Arabic", "Write recommendation notes in Arabic."),
    BILINGUAL("Arabic + English", "Write recommendation notes in both Arabic and English."),
    ENGLISH("English", "Write recommendation notes in English."),
}

/**
 * How long the model may think about one chunk before the connection is hung up.
 *
 * The call does not stream, so the server sends nothing at all until it has finished the whole
 * chunk - which makes this the model's thinking time, not a gap between bytes. Eight images
 * regularly take longer than the old five-minute ceiling allowed, and hitting it used to throw the
 * whole run away.
 */
object ResponseTimeout {
    const val MIN = 30
    const val DEFAULT = 300
    const val MAX = 900
}

data class AppPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val analysisLanguage: AnalysisLanguage = AnalysisLanguage.BILINGUAL,
    val responseTimeoutSeconds: Int = ResponseTimeout.DEFAULT,
    val defaultContentTypes: Set<AnalysisContentType> = AnalysisContentType.entries.toSet(),
    val customSystemPrompt: String = "",
    val includePhrases: String = "",
    val excludePhrases: String = "",
    val correctionRetries: Int = 1,
    val catalogEnrichmentEnabled: Boolean = true,
    /**
     * Trading sessions a **trade** is offered when it is recorded, which the user may type over.
     *
     * The user's own deadline and nothing else. It used to judge the channels as well, and the two
     * jobs pulled in opposite directions: a reader who wants to be out inside a week would set five
     * sessions, and every call a source made was then filed as having reached nothing unless it got
     * there inside five - which answers "how long do this source's calls take?" by refusing to look.
     * Scoring runs to [Scoring.JUDGING_HORIZON_SESSIONS] now and is not a setting at all.
     *
     * Stored and synced under its old name, `scoringWindowSessions`, because renaming a persisted
     * key silently resets the value on every device that has one.
     */
    val defaultTradeWindowSessions: Int = Scoring.DEFAULT_WINDOW_SESSIONS,
    /**
     * A daily notification when a trade has run past its deadline with no sale recorded.
     *
     * On by default: the whole point is to reach someone who is not opening the app, and a warning
     * nobody switched on warns nobody. It is the one thing here that speaks unprompted, so it is
     * also the one thing with a switch of its own.
     */
    val overdueRemindersEnabled: Boolean = true,
    /**
     * A notification when the market changes what a trade is - a target reached, the stop taken,
     * the deadline passed.
     *
     * On by default, and a separate switch from [overdueRemindersEnabled] rather than a second
     * meaning for it. That one asks the user for a decision the app cannot make; this one reports
     * something that has already happened, and someone can reasonably want either without the
     * other. Costs nothing on its own: the statuses are re-derived on every recompute whether or
     * not anyone is told, so this only decides whether the phone says so.
     */
    val tradeAlertsEnabled: Boolean = true,
    /**
     * Whether the phone says a stock has traded into the buy zone of a call not yet taken.
     *
     * Its own switch beside [tradeAlertsEnabled] and its own notification channel, because the two
     * are different questions: one reports what happened to money already committed, the other an
     * opportunity nothing has been committed to. Somebody who wants the first and not the second
     * has to be able to have exactly that.
     *
     * Default **off**. Every other notification in the app reports something that happened to a
     * thing the user chose - a trade they took, a deadline they set. This one arrives unprompted
     * about a call they only read, and a feature that starts buzzing about stocks on its own is one
     * that gets the whole app silenced.
     */
    val callAlertsEnabled: Boolean = false,
    /**
     * Whether a launch quietly asks GitHub whether a newer build exists.
     *
     * On by default and silent unless there is something new: the app is sideloaded, so a release
     * nobody is told about reaches nobody. It is a read of one public URL - no analysis, no
     * Telegram, and nothing is downloaded or installed without being asked.
     */
    val updateChecksEnabled: Boolean = true,
    /**
     * The order the Portfolio's trades are read in, chosen on that tab and kept.
     *
     * Stored where the date filter beside it is not, and the difference is what each one does to the
     * screen: an order hides nothing, so finding it still set weeks later costs the user a moment's
     * thought. A date filter still set weeks later shows one session and nothing else, which is how
     * someone concludes their trades have gone missing.
     */
    val portfolioOrder: PortfolioOrder = PortfolioOrder.URGENT,
    /**
     * The order the calls inside a session card on Insights are read in.
     *
     * Kept for the same reason [portfolioOrder] is, and safe to keep for a stronger one: every
     * option orders the identical set of calls, so an order found still set weeks later cannot have
     * hidden anything. [CallOrder.TICKER] is the default because it is the record's own order.
     */
    val callOrder: CallOrder = CallOrder.TICKER,
)

data class PromptSnapshot(
    val systemPrompt: String,
    val includePhrases: String,
    val excludePhrases: String,
    val savedAtEpochMilliseconds: Long,
)
