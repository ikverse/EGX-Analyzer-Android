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
