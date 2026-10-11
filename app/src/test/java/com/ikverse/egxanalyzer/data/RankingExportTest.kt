package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.ChannelScore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The ranking as a sheet: a header, a row per source, and a rank only where the page gives one.
 */
class RankingExportTest {

    private fun score(channel: String, judged: Int) = ChannelScore(
        channel = channel,
        calls = judged + 1,
        judged = judged,
        fullHits = 1,
        partialHits = 1,
        stopped = 1,
        expired = 0,
        notTradable = 1,
        fullHitRate = 20.0,
        anyTargetRate = 40.0,
        averageReturn = 2.5,
        medianSessionsToHit = 3.0,
    )

    @Test
    fun `one row per source under a frozen header, ranked only above the floor`() {
        val sheet = rankingSheet(listOf(score("Strong", 8), score("Thin", 2)), LocalDate.of(2026, 10, 11))

        assertEquals("EGX sources 2026-10-11", sheet.name)
        assertEquals(1, sheet.freezeRows)
        assertEquals(3, sheet.rows.size)
        assertEquals(Cell.Text("Rank", CellStyle(bold = true)), sheet.rows[0][0])
        assertEquals(Cell.Number(1.0), sheet.rows[1][0])
        assertEquals(Cell.Text("Strong"), sheet.rows[1][1])
        assertTrue(sheet.rows[2][0] is Cell.Blank)
        assertEquals(Cell.Text("Thin"), sheet.rows[2][1])
    }

    @Test
    fun `an absent figure is a blank cell, not a dash`() {
        val sheet = rankingSheet(listOf(score("Strong", 8)))
        // Column 15 is "Median sessions to stop", which the score above never set.
        assertTrue(sheet.rows[1][15] is Cell.Blank)
    }

    @Test
    fun `the file is named for the exchange's day`() {
        assertEquals("EGX-sources-2026-10-11.xlsx", rankingFileName(LocalDate.of(2026, 10, 11)))
    }
}
