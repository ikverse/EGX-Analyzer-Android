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
     * Trading sessions a recommendation stays open before it counts as expired rather than missed.
     * Adjustable because it depends on how long the user actually holds a position.
     */
    val scoringWindowSessions: Int = Scoring.DEFAULT_WINDOW_SESSIONS,
    /**
     * A daily notification when a trade has run past its deadline with no sale recorded.
     *
     * On by default: the whole point is to reach someone who is not opening the app, and a warning
     * nobody switched on warns nobody. It is the one thing here that speaks unprompted, so it is
     * also the one thing with a switch of its own.
     */
    val overdueRemindersEnabled: Boolean = true,
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
)

data class PromptSnapshot(
    val systemPrompt: String,
    val includePhrases: String,
    val excludePhrases: String,
    val savedAtEpochMilliseconds: Long,
)
