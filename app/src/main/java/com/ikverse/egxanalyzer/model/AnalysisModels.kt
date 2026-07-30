package com.ikverse.egxanalyzer.model

import android.net.Uri
import java.time.LocalDate
import java.time.Instant
import java.util.UUID

enum class AnalysisContentType {
    TEXT,
    IMAGES,
    AUDIO,
}

enum class AnalysisMode {
    NEXT_DAY,
    SPECIFIC_DATE,
}

/**
 * One source item sent to the selected cloud model.
 *
 * Images and voice notes remain separate media inputs. The repository is responsible for
 * converting them to the selected provider's OpenAI-compatible multimodal request format.
 */
sealed interface AnalysisInput {
    val sourceId: String

    data class Text(
        override val sourceId: String,
        val value: String,
    ) : AnalysisInput

    data class Image(
        override val sourceId: String,
        val uri: Uri,
        val mimeType: String,
    ) : AnalysisInput

    data class Voice(
        override val sourceId: String,
        val uri: Uri,
        val mimeType: String,
        val durationMilliseconds: Long?,
    ) : AnalysisInput
}

data class AnalysisRequest(
    val requestId: String = UUID.randomUUID().toString(),
    val channelIds: List<Long>,
    val contentTypes: Set<AnalysisContentType>,
    val inputs: List<AnalysisInput>,
    val mode: AnalysisMode = AnalysisMode.NEXT_DAY,
    val targetDate: LocalDate? = null,
    val provider: CloudProvider,
    val model: String,
    val sourceTraces: List<SourceTrace> = emptyList(),
    val sourceWindowStart: Instant? = null,
    val sourceWindowEnd: Instant? = null,
    val excludedSources: List<ExcludedSource> = emptyList(),
)

data class SourceTrace(
    val sourceId: String,
    val channelId: Long?,
    val channelName: String,
    val messageId: Long?,
    val timestamp: Instant,
    val contentType: AnalysisContentType,
    val preview: String,
)

data class RecommendationResult(
    val ticker: String,
    val companyName: String,
    val companyNameArabic: String? = null,
    val sourceName: String,
    val targetDate: LocalDate?,
    val timing: String?,
    val entryLow: Double?,
    val entryHigh: Double?,
    val takeProfit1: Double?,
    val takeProfit2: Double?,
    val stopLoss: Double?,
    val notesArabic: String?,
    val sourceIds: List<String> = emptyList(),
    val signal: String = "HOLD",
    val confidence: Double? = null,
    val riskLevel: String? = null,
    val timeHorizon: String? = null,
    val indicators: List<String> = emptyList(),
)

/**
 * One extracted occurrence of a stock, matching the desktop `data_points` contract.
 *
 * A stock can appear in several independent sections of one image, so every occurrence keeps
 * its own date, evidence, and levels rather than being merged during extraction.
 */
data class RecommendationDataPoint(
    val date: LocalDate?,
    val effectiveDateBasis: String?,
    val visibleSourceDate: String?,
    val dateEvidence: String?,
    val timingEvidence: String?,
    val sourceMessageId: String?,
    val sourceImageRef: Int?,
    val recommendationEvidence: String?,
    val recommendationType: String?,
    val buyPrice: Double?,
    val buyPriceLow: Double?,
    val buyPriceHigh: Double?,
    val target1: Double?,
    val returnTp1Pct: Double?,
    val target2: Double?,
    val returnTp2Pct: Double?,
    val stopLoss: Double?,
    val support: Double?,
    val resistance: Double?,
    val riskPct: Double?,
    val notesArabic: String?,
) {
    val isWatching: Boolean get() = effectiveDateBasis == "watching"
}

/** One consolidated stock from `top_consolidated_recommendations`. */
data class ConsolidatedRecommendation(
    val stockCode: String,
    val stockNameEnglish: String?,
    val stockNameArabic: String?,
    val mentionCount: Int,
    val rank: Int,
    val notesSummary: String?,
    val dataPoints: List<RecommendationDataPoint> = emptyList(),
)

data class ExcludedSource(
    val sourceId: String,
    val reason: String,
)

data class AnalysisDiagnostics(
    val sourceWindowStart: Instant? = null,
    val sourceWindowEnd: Instant? = null,
    val inputCount: Int = 0,
    val acceptedInputCount: Int = 0,
    val excludedSources: List<ExcludedSource> = emptyList(),
    val validationWarnings: List<String> = emptyList(),
    val correctionAttempted: Boolean = false,
    val durationMilliseconds: Long = 0,
)

data class AnalysisResult(
    val requestId: String,
    val recommendations: List<RecommendationResult>,
    /** Full consolidated extraction; `recommendations` is a flattened view of the same data. */
    val consolidated: List<ConsolidatedRecommendation> = emptyList(),
    /**
     * Image sources in the order they were sent, so index + 1 is the IMAGE_REF the model cites.
     * Paths point into Telegram's own storage and may disappear when it prunes its cache.
     */
    val imagePaths: List<String> = emptyList(),
    val inquiryReplyCount: Int,
    val analysisMode: AnalysisMode = AnalysisMode.NEXT_DAY,
    val recommendationTargetDate: LocalDate? = null,
    val diagnostics: AnalysisDiagnostics = AnalysisDiagnostics(),
    val sources: List<SourceTrace> = emptyList(),
    val rawResponse: String = "",
    val completedAt: Instant = Instant.now(),
)

data class SavedAnalysis(
    val id: Long,
    val result: AnalysisResult,
    val provider: CloudProvider,
    val model: String,
)

data class ChannelSelection(
    val id: Long,
    val name: String,
    val selected: Boolean = true,
    /** Telegram broadcast channel, as opposed to a group, private chat or service account. */
    val isChannel: Boolean = true,
) {
    /** The label used everywhere; identity stays with [id]. See [cleanChannelName]. */
    val displayName: String get() = cleanChannelName(name, fallback = "Untitled chat")
}
