package com.ikverse.egxanalyzer.model

import com.ikverse.egxanalyzer.data.ComposedPrompt
import com.ikverse.egxanalyzer.data.RuleSet

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
    /** The chats behind [channelIds], carried so the saved analysis can name its coverage. */
    val selectedChannels: List<AnalysedChannel> = emptyList(),
    /** What the model reports having deliberately left out, and why. */
    val modelExclusions: List<ModelExclusion> = emptyList(),
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
    /** The wording rules in force for this run, so the request can say what it was told to look for. */
    val rules: RuleSet = RuleSet(emptyList()),
    /**
     * The prompt this run will actually send, generated before the run rather than during it.
     *
     * Passed rather than looked up so a run cannot be judged by a prompt that changed while it was
     * in flight, and so the version it used is a fact about the request.
     */
    val prompt: ComposedPrompt? = null,
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

    /** Named on the card itself as a T+1 trade, between today's close and tomorrow's open. */
    val isTPlusOne: Boolean get() = effectiveDateBasis == "t_plus_1"
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

/**
 * Something the model saw and rejected, in its own words.
 *
 * Exclusion used to be pure absence, which cannot be checked: a source dropped for good reason and
 * one never examined looked identical. Stated outright, an over-eager gate becomes as visible as a
 * leaky one.
 */
data class ModelExclusion(
    val stockCode: String?,
    val sourceMessageId: String?,
    val visibleSourceDate: String?,
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
    /** Model calls a run took: one per source chunk, one to consolidate, plus any retries. */
    val requestCount: Int = 0,
    val imagesSent: Int = 0,
    val unaccountedImages: List<UnaccountedImage> = emptyList(),
    /**
     * The prompt version this run was judged by, and the rules folded into it.
     *
     * Recorded rather than re-derived: editing a rule tomorrow must not change what a report from
     * today is understood to have meant.
     */
    val promptId: String? = null,
    val promptSchemaVersion: Int? = null,
    val promptRuleIds: List<String> = emptyList(),
)

/**
 * An image the model was given and never mentioned - neither recommended nor excluded.
 *
 * Silence used to be indistinguishable from a considered rejection. It cost a card captioned
 * `اهم الاسهم غدا`, headed with the target session, that simply never appeared in a report and left
 * nothing behind to say so.
 */
data class UnaccountedImage(
    val reference: Int,
    val sourceId: String?,
    val caption: String?,
)

/** A chat a run was pointed at, whether or not it turned out to have anything to say. */
data class AnalysedChannel(val id: Long, val name: String)

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
    /**
     * The chats this run covered.
     *
     * Not the same as the chats that produced sources: one selected chat may simply have posted
     * nothing in the window. Without this, a later run looks like it never examined that chat, and
     * an earlier run's calls for it survive a rerun that had already decided against them.
     */
    val selectedChannels: List<AnalysedChannel> = emptyList(),
    /** What the model reports having deliberately left out, and why. */
    val modelExclusions: List<ModelExclusion> = emptyList(),
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
    /** What Telegram calls this chat, shown so a group is not mistaken for a channel. */
    val kind: ChatKind = ChatKind.CHANNEL,
) {
    val isChannel: Boolean get() = kind == ChatKind.CHANNEL

    /** The label used everywhere; identity stays with [id]. See [cleanChannelName]. */
    val displayName: String get() = cleanChannelName(name, fallback = "Untitled chat")
}
