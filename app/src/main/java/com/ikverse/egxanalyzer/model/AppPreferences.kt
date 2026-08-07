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
)

data class PromptSnapshot(
    val systemPrompt: String,
    val includePhrases: String,
    val excludePhrases: String,
    val savedAtEpochMilliseconds: Long,
)
