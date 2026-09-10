package com.ikverse.egxanalyzer.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * That a correction lands on the call it was made about, and on no other.
 *
 * Most of this file is about the corrections that must **not** apply. An edit is anchored to a slot
 * in a parse, and the parse can move: a newer prompt reads a card differently, an occurrence that
 * used to be dropped starts surviving, a stock comes back with one reading where it had three. An
 * edit that went on applying through any of that would rewrite a call nobody looked at, silently,
 * on a screen whose whole claim is that every figure is traceable - which is a far worse outcome
 * than the correction quietly reverting, because reverting is visible and recoverable.
 */
class RecommendationEditTest {

    private val catalog: (String, String?, String?) -> Pair<String?, String?> = { code, en, ar ->
        when (code) {
            "ORHD" -> "Orascom Development Egypt S.A.E." to "أوراسكوم للتنمية مصر"
            "ORAS" -> "Orascom Construction PLC" to "اوراسكوم كونستراكشون بي ال سي"
            else -> en to ar
        }
    }

    private fun point(
        slot: Int,
        target1: Double? = 13.20,
        target2: Double? = 14.00,
    ) = RecommendationDataPoint(
        date = LocalDate.parse("2026-09-03"),
        effectiveDateBasis = "t_plus_1",
        visibleSourceDate = "2026-09-02",
        dateEvidence = null,
        timingEvidence = null,
        sourceMessageId = "5501",
        sourceImageRef = 2,
        recommendationEvidence = null,
        recommendationType = "buy",
        buyPrice = null,
        buyPriceLow = 12.10,
        buyPriceHigh = 12.40,
        target1 = target1,
        returnTp1Pct = 7.7,
        target2 = target2,
        returnTp2Pct = 14.2,
        stopLoss = 11.80,
        support = 11.60,
        resistance = 14.35,
        riskPct = -3.7,
        notesArabic = null,
        parseIndex = slot,
    )

    private fun stock(vararg points: RecommendationDataPoint) = ConsolidatedRecommendation(
        stockCode = "ORAS",
        stockNameEnglish = "Orascom Construction PLC",
        stockNameArabic = "اوراسكوم كونستراكشون بي ال سي",
        mentionCount = 1,
        rank = 1,
        notesSummary = null,
        dataPoints = points.toList(),
    )

    private fun edit(
        on: RecommendationDataPoint,
        stockCode: String? = null,
        target1: Double? = null,
        cleared: Set<EditField> = emptySet(),
    ) = RecommendationEdit(
        originalStockCode = "ORAS",
        pointIndex = on.parseIndex,
        fingerprint = on.editFingerprint(),
        stockCode = stockCode,
        target1 = target1,
        cleared = cleared,
    )

    @Test
    fun `a corrected ticker moves the stock and its names come from the catalog`() {
        val only = point(0)
        val corrected = RecommendationEdits
            .apply(listOf(stock(only)), listOf(edit(only, stockCode = "ORHD")), catalog)
            .single()

        assertEquals("ORHD", corrected.stockCode)
        assertEquals("Orascom Development Egypt S.A.E.", corrected.stockNameEnglish)
        assertEquals("أوراسكوم للتنمية مصر", corrected.stockNameArabic)
    }

    /**
     * The one that would print the right ticker over the wrong company.
     *
     * Carrying the names through unchanged is the obvious implementation and is worse than either
     * mistake on its own: the reader is looking at a card that agrees with itself and is wrong.
     */
    @Test
    fun `the old names never survive a corrected ticker`() {
        val only = point(0)
        val corrected = RecommendationEdits
            .apply(listOf(stock(only)), listOf(edit(only, stockCode = "ORHD")), catalog)
            .single()

        assertEquals(false, corrected.stockNameEnglish == "Orascom Construction PLC")
    }

    /** The stock is not a property of one reading of a card, so every occurrence of it moves. */
    @Test
    fun `a ticker corrected on one occurrence moves the whole stock`() {
        val first = point(0)
        val second = point(1, target1 = 13.90)
        val corrected = RecommendationEdits
            .apply(listOf(stock(first, second)), listOf(edit(first, stockCode = "ORHD")), catalog)
            .single()

        assertEquals("ORHD", corrected.stockCode)
        assertEquals(2, corrected.dataPoints.size)
    }

    /** The levels are, so a correction to one must leave the occurrence beside it alone. */
    @Test
    fun `a level corrected on one occurrence leaves the other untouched`() {
        val first = point(0)
        val second = point(1)
        val corrected = RecommendationEdits
            .apply(listOf(stock(first, second)), listOf(edit(first, target1 = 13.85)), catalog)
            .single()

        assertEquals(13.85, corrected.dataPoints[0].target1)
        assertEquals(13.20, corrected.dataPoints[1].target1)
    }

    /**
     * A figure and the percentage beside it can never describe different levels.
     *
     * The model returns both, and the card and the spreadsheet both print what is stored - so an
     * edit that moved a target and left its percentage would put two numbers on one card that
     * contradict each other.
     */
    @Test
    fun `a corrected target brings its percentage with it`() {
        val only = point(0)
        val corrected = RecommendationEdits
            .apply(listOf(stock(only)), listOf(edit(only, target1 = 13.85)), catalog)
            .single()
            .dataPoints
            .single()

        assertEquals(13.85, corrected.target1)
        // Entry midpoint 12.25, the same basis the scorer measures a return from.
        assertEquals(13.06, corrected.returnTp1Pct!!, 0.01)
    }

    /**
     * Emptying a field and saying nothing about it are different acts.
     *
     * The model inventing a target that is not on the card is as common as it misreading one, and
     * without this the reader could only ever change a figure, never delete it.
     */
    @Test
    fun `a cleared level is emptied rather than left alone`() {
        val only = point(0)
        val corrected = RecommendationEdits.apply(
            listOf(stock(only)),
            listOf(edit(only, cleared = setOf(EditField.TARGET_2))),
            catalog,
        ).single().dataPoints.single()

        assertNull(corrected.target2)
        assertNull(corrected.returnTp2Pct)
        assertEquals(13.20, corrected.target1)
    }

    /**
     * The guard against an edit landing on somebody else's call.
     *
     * A newer prompt can return a different reading of the same response, and the slot an edit was
     * filed against may now hold another occurrence entirely. Dropped rather than applied: the call
     * reads as the model left it, which is where the reader started and is visible on the card.
     */
    @Test
    fun `an edit whose occurrence has changed underneath it is dropped`() {
        val original = point(0)
        val stale = edit(original, stockCode = "ORHD", target1 = 13.85)
        val reparsed = point(0, target1 = 12.90)

        val corrected = RecommendationEdits
            .apply(listOf(stock(reparsed)), listOf(stale), catalog)
            .single()

        assertEquals("ORAS", corrected.stockCode)
        assertEquals(12.90, corrected.dataPoints.single().target1)
    }

    /** The slot itself can vanish, which is the same failure arriving from the other direction. */
    @Test
    fun `an edit for an occurrence that is no longer there is dropped`() {
        val gone = point(3)
        val corrected = RecommendationEdits
            .apply(listOf(stock(point(0))), listOf(edit(gone, stockCode = "ORHD")), catalog)
            .single()

        assertEquals("ORAS", corrected.stockCode)
    }

    /** Filed under what the model read, or a corrected report could never be corrected again. */
    @Test
    fun `a second correction of an already corrected stock still finds it`() {
        val only = point(0)
        val once = RecommendationEdits
            .apply(listOf(stock(only)), listOf(edit(only, stockCode = "ORHD")), catalog)
            .single()

        assertEquals("ORAS", once.originalStockCode)

        val twice = RecommendationEdits
            .apply(listOf(once), listOf(edit(only, stockCode = "ORAS")), catalog)
            .single()

        assertEquals("ORAS", twice.stockCode)
    }

    @Test
    fun `an edit that says nothing is empty and is never stored`() {
        val only = point(0)

        assertEquals(true, edit(only).isEmpty)
        assertEquals(false, edit(only, target1 = 1.0).isEmpty)
        assertEquals(false, edit(only, cleared = setOf(EditField.SUPPORT)).isEmpty)
    }

    /** The occurrence's own fingerprint has to move when any judged figure does. */
    @Test
    fun `the fingerprint changes with the values it stands for`() {
        assertEquals(point(0).editFingerprint(), point(0).editFingerprint())
        assertNotNull(point(0).editFingerprint())
        assertEquals(false, point(0).editFingerprint() == point(0, target1 = 9.0).editFingerprint())
    }

    /** Nothing to apply must cost nothing, because this runs on every read of every report. */
    @Test
    fun `a report with no corrections is handed back untouched`() {
        val stocks = listOf(stock(point(0)))

        assertEquals(stocks, RecommendationEdits.apply(stocks, emptyList(), catalog))
    }

    // The identity every stored answer about a call is filed under.

    private fun report(stock: ConsolidatedRecommendation) = AnalysisResult(
        requestId = "run-1",
        recommendations = emptyList(),
        consolidated = listOf(stock),
        inquiryReplyCount = 0,
        recommendationTargetDate = LocalDate.parse("2026-09-03"),
        sources = listOf(
            SourceTrace(
                sourceId = "chat-1:5501",
                channelId = 1,
                channelName = "Ashum Masr",
                messageId = 5501,
                timestamp = java.time.Instant.parse("2026-09-02T12:00:00Z"),
                contentType = AnalysisContentType.IMAGES,
                preview = "",
            ),
            SourceTrace(
                sourceId = "chat-1:5501:photo",
                channelId = 1,
                channelName = "Ashum Masr",
                messageId = 5501,
                timestamp = java.time.Instant.parse("2026-09-02T12:00:00Z"),
                contentType = AnalysisContentType.IMAGES,
                preview = "",
            ),
        ),
    )

    @Test
    fun `a call is identified the way the scorer identifies it`() {
        val only = point(0)
        val identity = report(stock(only)).callSlot("ORAS", 0)!!.identity(report(stock(only)))!!

        assertEquals("ORAS", identity.ticker)
        // The session the run was aimed at, not the date printed on the card.
        assertEquals(LocalDate.parse("2026-09-03"), identity.openedOn)
        assertEquals("Ashum Masr", identity.channel)
    }

    /**
     * The channel is part of a call's identity, so a key built without it matches nothing.
     *
     * `opinionId` and `alertId` both carry it, and an occurrence citing a message no source trace
     * covers has to fall back on the same word the scorer falls back on or the two file the same
     * call in two places.
     */
    @Test
    fun `an occurrence with no traceable channel falls back on the scorer's own word`() {
        val orphan = point(0).copy(sourceMessageId = "9999")
        val identity = report(stock(orphan)).callSlot("ORAS", 0)!!.identity(report(stock(orphan)))!!

        assertEquals(PerformanceCalculator.UNKNOWN_CHANNEL, identity.channel)
    }

    /** Nothing is filed against an undated call, so there is nothing for a correction to follow. */
    @Test
    fun `an undated occurrence has no identity to chase`() {
        val undated = point(0).copy(date = null, visibleSourceDate = null)
        val result = report(stock(undated)).copy(recommendationTargetDate = null)

        assertNull(result.callSlot("ORAS", 0)!!.identity(result))
    }

    /**
     * A message can arrive as several sources - a caption and the photos under it.
     *
     * The misread came out of the whole message, so forgetting one of its readings and leaving the
     * rest would have the next run adopt the same mistake from the source beside it.
     */
    @Test
    fun `every source carrying one message is forgotten together`() {
        val result = report(stock(point(0)))

        assertEquals(setOf("chat-1:5501", "chat-1:5501:photo"), result.sourceIdsFor("5501"))
        assertEquals(emptySet<String>(), result.sourceIdsFor("9999"))
        assertEquals(emptySet<String>(), result.sourceIdsFor(null))
    }
}
