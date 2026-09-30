package com.ikverse.egxanalyzer.model

import java.time.LocalDate

/**
 * What the kept five-minute archive holds, for the one line on screen that reports it.
 *
 * In `model` rather than beside the store it is read from, for the reason [PriceHealthReport] is:
 * the `ui` package imports nothing from `data`, so a figure a screen draws has to be expressible in
 * a type the screen is allowed to name.
 *
 * [bars] is a `Long` and the others are not, deliberately. The row count passes a million inside a
 * year on a phone that leaves this switched on, which is comfortably inside an `Int` and close
 * enough to it to be worth never having to think about again; the stock count is bounded by the
 * exchange.
 */
data class PriceSeriesSummary(
    val bars: Long,
    val stocks: Int,
    val from: LocalDate?,
    val through: LocalDate?,
) {
    /** Nothing copied yet, which is what a phone that has never switched this on reports. */
    val empty: Boolean get() = bars == 0L

    companion object {
        val EMPTY = PriceSeriesSummary(0L, 0, null, null)
    }
}
