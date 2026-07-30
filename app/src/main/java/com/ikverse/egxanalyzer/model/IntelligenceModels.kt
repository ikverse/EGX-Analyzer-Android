package com.ikverse.egxanalyzer.model

import java.time.LocalDate

data class AnalysisSearchHit(
    val resultId: Long,
    val ticker: String,
    val companyName: String,
    val targetDate: LocalDate?,
    val sourceNames: List<String>,
)

data class ConsensusItem(
    val ticker: String,
    val companyName: String,
    val recommendationCount: Int,
    val sourceCount: Int,
    val buyCount: Int,
    val sellCount: Int,
    val holdCount: Int,
    val averageConfidence: Double?,
    val latestTargetDate: LocalDate?,
)

data class AnalysisReport(
    val title: String,
    val markdown: String,
)

/** One recommendation scored against the sessions that followed it. */
data class ScoredCall(
    val ticker: String,
    val companyEnglish: String?,
    val companyArabic: String?,
    val channel: String,
    val openedOn: LocalDate,
    val entryLow: Double?,
    val entryHigh: Double?,
    val target1: Double?,
    val target2: Double?,
    val stopLoss: Double?,
    val outcome: Outcome,
    val settledOn: LocalDate?,
    val peakHigh: Double?,
    val returnPct: Double?,
    val sessionsElapsed: Int,
)

/** A source's record over everything it has been scored on. */
data class ChannelScore(
    val channel: String,
    val calls: Int,
    val judged: Int,
    val hits: Int,
    val stopped: Int,
    val expired: Int,
    val notTradable: Int,
    val hitRate: Double?,
    val averageReturn: Double?,
    val medianSessionsToHit: Double?,
)

data class PerformanceReport(
    val windowSessions: Int,
    /** The first session with a stored price, which is where scoring can begin. */
    val scoringSince: LocalDate? = null,
    val tracked: Int = 0,
    val judged: Int = 0,
    val hits: Int = 0,
    val hitRate: Double? = null,
    val byOutcome: Map<Outcome, Int> = emptyMap(),
    val channels: List<ChannelScore> = emptyList(),
    val calls: List<ScoredCall> = emptyList(),
)
