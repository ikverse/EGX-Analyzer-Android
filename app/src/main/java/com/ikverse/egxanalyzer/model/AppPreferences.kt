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
     * Whether the phone says a trade is closing on its stop or on target 2.
     *
     * The only alert here that arrives while something can still be decided. Every other one
     * reports a settlement - a stop announced is a stop already taken - so this is the one with any
     * use beyond the record.
     *
     * Default **off**, for the reason [callAlertsEnabled] is: it is inherently the noisiest thing
     * the app can say, because a price near a level goes on being near it, and a warning that
     * arrives twice a week about a trade nothing has happened to is how the whole app gets muted.
     * Somebody who wants it asks for it.
     */
    val approachAlertsEnabled: Boolean = false,
    /**
     * How near a level counts as closing on it, in percent of the current price.
     *
     * A setting rather than a constant because it is the one number here whose right answer depends
     * on the reader: someone trading tight stops on a liquid large cap and someone holding a thin
     * mid cap through a 4% day mean different things by "close". [ApproachAlerts] holds the bounds
     * and the default.
     */
    val approachThresholdPercent: Int = ApproachAlerts.DEFAULT_THRESHOLD_PERCENT,
    /**
     * Whether the phone says, once after the close, what the whole session did.
     *
     * The gap it fills is the session where nothing of the reader's own moved: every other
     * notification here is about one trade or one call, so an afternoon when three of their
     * sources' calls reached targets and they held none of them passed in complete silence.
     *
     * Default **off**. A daily line is the most easily resented notification an app can have - it
     * arrives on a rhythm rather than on an event, whether or not anything happened - so it is
     * switched on by somebody who wants it, exactly like [callAlertsEnabled].
     */
    val sessionDigestEnabled: Boolean = false,
    /**
     * Whether the phone says the price feed has gone quiet about stocks the record names.
     *
     * Default **on**, and that is the difference from the three above: those report the market,
     * this reports the app being unable to read it. A frozen feed is invisible - the series answers
     * every request while its newest session stays put - so every rate on the page quietly rests on
     * fewer calls than the reader believes, with nothing anywhere saying so. It has happened here
     * once already, on the ISIN symbol migration, and nothing noticed at the time.
     *
     * It cannot become chatty: it speaks once when a spell begins and re-arms only when the feed
     * comes back.
     */
    val feedAlertsEnabled: Boolean = true,
    /**
     * Whether the phone says a scheduled analysis was due and did not happen.
     *
     * Default **on**, for the reason above: it reports the app failing to keep a promise the reader
     * made it make. The reported symptom of a broken schedule is silence - nothing fires and
     * nothing says so - and the two system permissions that stop one working are named only on a
     * screen somebody has to think to open.
     *
     * It never announces a *skip*, so it cannot become a daily line about paid runs being switched
     * off, which is the standing state of that switch.
     */
    val scheduleAlertsEnabled: Boolean = true,
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
