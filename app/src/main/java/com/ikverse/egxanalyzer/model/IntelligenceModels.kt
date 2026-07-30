package com.ikverse.egxanalyzer.model

import java.time.LocalDate

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
    /**
     * The earliest call actually scored, not the earliest stored price.
     *
     * Reporting the price history's start claimed a month of coverage that no saved call came
     * anywhere near, which read as though the figures rested on far more than they did.
     */
    val scoringSince: LocalDate? = null,
    /** Calls naming a stock with no stored price at all, so a refresh is the missing step. */
    val unpricedStocks: Int = 0,
    val tracked: Int = 0,
    val judged: Int = 0,
    val hits: Int = 0,
    val hitRate: Double? = null,
    val byOutcome: Map<Outcome, Int> = emptyMap(),
    val channels: List<ChannelScore> = emptyList(),
    val calls: List<ScoredCall> = emptyList(),
)
