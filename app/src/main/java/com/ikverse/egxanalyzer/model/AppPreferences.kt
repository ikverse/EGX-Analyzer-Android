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

data class AppPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val analysisLanguage: AnalysisLanguage = AnalysisLanguage.BILINGUAL,
    val responseTimeoutSeconds: Int = 180,
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
