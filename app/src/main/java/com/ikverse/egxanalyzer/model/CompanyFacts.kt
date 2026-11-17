package com.ikverse.egxanalyzer.model

/**
 * A company's facts on one date, for the stock sheet: what the business earns and owes, beside
 * the price the rest of the sheet is about. Null where the source has no figure. Pounds for the
 * company's own totals; nothing here is the reader's money.
 */
data class CompanyFacts(
    val ticker: String,
    /** When they were read, epoch milliseconds. */
    val fetchedAt: Long,
    val peRatio: Double?,
    /** Net income over the last twelve months, in pounds: below zero is a loss. */
    val netIncome: Double?,
    val eps: Double?,
    val totalDebt: Double?,
    val debtToEquity: Double?,
) {
    val lossMaking: Boolean? get() = netIncome?.let { it < 0 }
    val isEmpty: Boolean get() = listOf(peRatio, netIncome, eps, totalDebt, debtToEquity).all { it == null }
}
