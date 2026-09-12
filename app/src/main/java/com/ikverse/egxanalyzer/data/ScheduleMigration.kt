package com.ikverse.egxanalyzer.data

/**
 * What the rows of the retired job table mean under what replaced it.
 *
 * The table let a user build any number of jobs out of a kind of work and a choice of trigger.
 * What is left is a checkbox that keeps prices fresh while the market trades, so this reads what
 * was there and answers with the nearest true thing, once, before the table is dropped.
 *
 * Carrying an intent across is not the same as making a new one. A phone that was asking for
 * prices through the session goes on asking; a phone that had switched that off does not have it
 * switched on for it. An old repeating analysis row is dropped along with the table rather than
 * carried anywhere - the feature it belonged to is gone.
 *
 * Pure, so the decision can be checked without a database: it is handed rows and returns whether
 * the price refresh should be turned on.
 */
object ScheduleMigration {

    /**
     * Whether a price refresh was switched on among the old rows, whatever shape its trigger had.
     *
     * Every trigger kind - after the close, hourly, through the session - was a way of asking the
     * same question, and the checkbox is now the answer to all of them.
     */
    fun marketRefreshWasOn(rows: List<LegacyScheduleRow>): Boolean =
        rows.any { it.workKind == PRICE_REFRESH && it.enabled }

    private const val PRICE_REFRESH = "PRICE_REFRESH"
}
