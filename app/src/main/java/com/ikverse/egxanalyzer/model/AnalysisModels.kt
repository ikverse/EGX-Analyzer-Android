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
)
