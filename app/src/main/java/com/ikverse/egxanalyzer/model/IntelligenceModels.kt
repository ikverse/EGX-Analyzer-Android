package com.ikverse.egxanalyzer.model

import java.time.Instant
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
    /** Highest and lowest the stock traded since the call, across the scoring window. */
    val peakHigh: Double?,
    val troughLow: Double?,
    val returnPct: Double?,
    val sessionsElapsed: Int,
    /** The first target was banked and the stop was reached afterwards. */
    val stoppedAfterPartial: Boolean = false,
    /** False while the window is still running, so a partial hit may still become a full one. */
    val windowComplete: Boolean = false,
    /** The sessions this call was judged on, so the card can show them without another query. */
    val sessions: List<DailySession> = emptyList(),
)

/** One saved analysis with everything it recommended, scored. */
data class ScoredRun(
    val analysisId: Long,
    val completedAt: Instant,
    val model: String,
    val calls: List<ScoredCall>,
) {
    val fullHits: Int get() = calls.count { it.outcome.isFullHit }
    val partialHits: Int get() = calls.count { it.outcome == Outcome.PARTIAL_HIT }
    val stopped: Int get() = calls.count { it.outcome == Outcome.STOPPED }
    val pending: Int get() = calls.count { !it.outcome.judged }
}

/** A source's record over everything it has been scored on. */
data class ChannelScore(
    val channel: String,
    val calls: Int,
    val judged: Int,
    val fullHits: Int,
    val partialHits: Int,
    val stopped: Int,
    val expired: Int,
    val notTradable: Int,
    /** Reached the second target. */
    val fullHitRate: Double?,
    /** Reached at least the first target. */
    val anyTargetRate: Double?,
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
    /** Stocks with no stored price at all, so a refresh is the missing step. */
    val unpricedStocks: Int = 0,
    /**
     * Calls whose stock is priced but whose own sessions have not been published yet.
     *
     * A refresh cannot help these - the exchange data simply is not out - so telling the user to
     * refresh would be wrong.
     */
    val awaitingSessions: Int = 0,
    val tracked: Int = 0,
    val judged: Int = 0,
    val fullHits: Int = 0,
    val partialHits: Int = 0,
    /** Reached the second target. */
    val fullHitRate: Double? = null,
    /** Reached at least the first target. */
    val anyTargetRate: Double? = null,
    val byOutcome: Map<Outcome, Int> = emptyMap(),
    val channels: List<ChannelScore> = emptyList(),
    val runs: List<ScoredRun> = emptyList(),
)
