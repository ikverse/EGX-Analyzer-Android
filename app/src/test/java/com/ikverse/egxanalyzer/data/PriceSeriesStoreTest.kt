package com.ikverse.egxanalyzer.data

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.Instant
import java.time.LocalDate

/**
 * The archive read back, which is the half of this feature that decisions alone cannot cover.
 *
 * Every rule in `SeriesHarvestTest` is worthless if what comes out of the table is not what went
 * in, and an archive that reads back empty looks exactly like a phone that never turned it on -
 * the same reason `BackupRoundTripTest` exists beside the pure decisions in `BackupRestore`.
 */
@RunWith(RobolectricTestRunner::class)
class PriceSeriesStoreTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    private fun store(name: String) = PriceSeriesStore(context, name)

    private fun bar(
        ticker: String = "AMOC",
        at: String = "2026-09-06T08:00:00Z",
        close: Double? = 12.5,
        volume: Double? = 1_000.0,
    ) = PriceBar(
        ticker = ticker,
        at = Instant.parse(at),
        open = 12.0,
        high = 12.8,
        low = 11.9,
        close = close,
        volume = volume,
    )

    private val through = LocalDate.of(2026, 9, 6)

    @Test
    fun `bars come back as they went in`() {
        val store = store("round-trip.db")
        store.saveBars("AMOC", listOf(bar()), through)

        val read = mutableListOf<PriceBar>()
        store.forEachBar(read::add)

        assertEquals(1, read.size)
        assertEquals(bar(), read.single())
    }

    @Test
    fun `a bar the feed had nothing for keeps its nulls`() {
        // Empty rather than zero, the rule the spreadsheet export follows: a low of zero sits under
        // every stop loss ever printed, and anyone charting this would take it as a real price.
        val store = store("nulls.db")
        store.saveBars("AMOC", listOf(bar(close = null, volume = null)), through)

        val read = mutableListOf<PriceBar>()
        store.forEachBar(read::add)

        assertNull(read.single().close)
        assertNull(read.single().volume)
    }

    @Test
    fun `re-copying a session replaces its bars rather than doubling them`() {
        // A harvest may legitimately re-cover a day it already holds - the feed answers in whole
        // days - and a bar is the same bar whenever it was fetched.
        val store = store("replace.db")
        store.saveBars("AMOC", listOf(bar(close = 12.5)), through)
        store.saveBars("AMOC", listOf(bar(close = 13.0)), through)

        val read = mutableListOf<PriceBar>()
        store.forEachBar(read::add)

        assertEquals(1, read.size)
        assertEquals(13.0, read.single().close!!, 1e-9)
    }

    @Test
    fun `the mark advances even where the session yielded no bars`() {
        // The whole reason harvest_marks exists rather than reading max(bar_at): a session the feed
        // has nothing for would otherwise be asked about on every fire forever.
        val store = store("empty-mark.db")
        store.saveBars("AMOC", emptyList(), through)

        assertEquals(through, store.harvestedThrough()["AMOC"])
        assertTrue(store.summary().empty)
    }

    @Test
    fun `the summary counts bars, stocks and the span they cover`() {
        val store = store("summary.db")
        store.saveBars(
            "AMOC",
            listOf(
                bar(at = "2026-09-06T08:00:00Z"),
                bar(at = "2026-09-06T08:05:00Z"),
            ),
            through,
        )
        store.saveBars("COMI", listOf(bar(ticker = "COMI", at = "2026-09-03T08:00:00Z")), through)

        val summary = store.summary()

        assertEquals(3L, summary.bars)
        assertEquals(2, summary.stocks)
        assertEquals(LocalDate.of(2026, 9, 3), summary.from)
        assertEquals(LocalDate.of(2026, 9, 6), summary.through)
    }

    @Test
    fun `a fresh store holds nothing and says so`() {
        val summary = store("fresh.db").summary()

        assertTrue(summary.empty)
        assertEquals(0L, summary.bars)
        assertNull(summary.from)
    }

    @Test
    fun `two stocks stay separate under one key`() {
        // The primary key is (ticker, bar_at), so two stocks printing a bar at the same instant are
        // two rows. Keyed on the instant alone they would overwrite each other silently.
        val store = store("two-stocks.db")
        val at = "2026-09-06T08:00:00Z"
        store.saveBars("AMOC", listOf(bar(ticker = "AMOC", at = at)), through)
        store.saveBars("COMI", listOf(bar(ticker = "COMI", at = at)), through)

        val read = mutableListOf<PriceBar>()
        store.forEachBar(read::add)

        assertEquals(2, read.size)
        assertEquals(listOf("AMOC", "COMI"), read.map(PriceBar::ticker))
    }
}
