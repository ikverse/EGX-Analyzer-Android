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

    /** Another stock in the same report, filed by the model under its own code. */
    private fun other(code: String, rank: Int, vararg points: RecommendationDataPoint) =
        ConsolidatedRecommendation(
            stockCode = code,
            stockNameEnglish = null,
            stockNameArabic = null,
            mentionCount = 1,
            rank = rank,
            notesSummary = "$code notes",
            dataPoints = points.toList(),
        )

    private fun edit(
        on: RecommendationDataPoint,
        stockCode: String? = null,
        target1: Double? = null,
        cleared: Set<EditField> = emptySet(),
        stockNameEnglish: String? = null,
    ) = RecommendationEdit(
        originalStockCode = "ORAS",
        pointIndex = on.parseIndex,
        fingerprint = on.editFingerprint(),
        stockCode = stockCode,
        stockNameEnglish = stockNameEnglish,
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

    /**
     * A misread card is filed among the genuine cards of whatever it was misread as, so correcting
     * it must move it alone. Renaming the entry took the genuine cards with it.
     */
    @Test
    fun `a ticker corrected on one occurrence moves only that occurrence`() {
        val first = point(0)
        val second = point(1, target1 = 13.90)
        val corrected = RecommendationEdits
            .apply(listOf(stock(first, second)), listOf(edit(first, stockCode = "ORHD")), catalog)

        assertEquals(listOf("ORAS", "ORHD"), corrected.map { it.stockCode })
        assertEquals(listOf(1), corrected[0].dataPoints.map { it.parseIndex })
        assertEquals(listOf(0), corrected[1].dataPoints.map { it.parseIndex })
        assertEquals("Orascom Development Egypt S.A.E.", corrected[1].stockNameEnglish)
    }

    /** The bug as reported: a corrected call has to join the calls already under that code. */
    @Test
    fun `a corrected ticker joins the stock already in the report`() {
        val misread = point(0)
        val genuine = point(0, target1 = 15.00).copy(sourceMessageId = "7700")
        val report = AnalysisResult(
            requestId = "run-1",
            recommendations = emptyList(),
            inquiryReplyCount = 0,
            consolidated = RecommendationEdits.apply(
                listOf(stock(misread), other("ORHD", rank = 2, genuine)),
                listOf(edit(misread, stockCode = "ORHD")),
                catalog,
            ),
        )

        val merged = report.consolidated.single()
        assertEquals("ORHD", merged.stockCode)
        // The model's own entry leads, so its notes and its occurrence come first.
        assertEquals(listOf(15.00, 13.20), merged.dataPoints.map { it.target1 })
        assertEquals("ORHD notes", merged.notesSummary)
        assertEquals(1, merged.rank)
        // Each occurrence is still found by the slot its edit is filed under.
        assertEquals(13.20, report.callSlot("ORAS", 0)!!.point.target1)
        assertEquals(15.00, report.callSlot("ORHD", 0)!!.point.target1)
        assertEquals("ORAS", merged.anchorOf(merged.dataPoints[1]))
    }

    /** Corrected wholesale to a code nobody else used, a stock keeps its place in the report. */
    @Test
    fun `a stock corrected wholesale keeps its place`() {
        val only = point(0)
        val corrected = RecommendationEdits.apply(
            listOf(stock(only), other("COMI", rank = 2, point(0))),
            listOf(edit(only, stockCode = "ORHD")),
            catalog,
        )

        assertEquals(listOf("ORHD", "COMI"), corrected.map { it.stockCode })
    }

    /** The reader typed the name because the catalog had it wrong; the catalog must not win. */
    @Test
    fun `a name the reader typed stands over the catalog's`() {
        val only = point(0)
        val corrected = RecommendationEdits.apply(
            listOf(stock(only)),
            listOf(edit(only, stockCode = "ORHD", stockNameEnglish = "Orascom Dev")),
            catalog,
        ).single()

        assertEquals("Orascom Dev", corrected.stockNameEnglish)
        assertEquals("أوراسكوم للتنمية مصر", corrected.stockNameArabic)
    }

    /** A new listing has no catalog entry, so what the reader typed is the only name it has. */
    @Test
    fun `a typed name survives a ticker the catalog does not know`() {
        val only = point(0)
        val typed = RecommendationEdits.apply(
            listOf(stock(only)),
            listOf(edit(only, stockCode = "NEWCO", stockNameEnglish = "New Company")),
            catalog,
        ).single()
        val untyped = RecommendationEdits.apply(
            listOf(stock(only)),
            listOf(edit(only, stockCode = "NEWCO")),
            catalog,
        ).single()

        assertEquals("New Company", typed.stockNameEnglish)
        // Not the company the model named: that is the one name certain to be wrong.
        assertNull(untyped.stockNameEnglish)
    }

    /** The flat list renames a code only when every occurrence of it has left. */
    @Test
    fun `only a code every occurrence left is renamed`() {
        val first = point(0)
        val second = point(1, target1 = 13.90)
        val split = RecommendationEdits
            .apply(listOf(stock(first, second)), listOf(edit(first, stockCode = "ORHD")), catalog)
        val whole = RecommendationEdits
            .apply(listOf(stock(first)), listOf(edit(first, stockCode = "ORHD")), catalog)

        assertEquals(emptyMap<String, String>(), RecommendationEdits.renamedCodes(split))
        assertEquals(mapOf("ORAS" to "ORHD"), RecommendationEdits.renamedCodes(whole))
    }

    /** An edit that no longer fits moves nothing in the report, and so renames nothing either. */
    @Test
    fun `an edit that no longer fits renames nothing`() {
        val stale = edit(point(0), stockCode = "ORHD")
        val corrected = RecommendationEdits
            .apply(listOf(stock(point(0, target1 = 12.90))), listOf(stale), catalog)

        assertEquals(emptyMap<String, String>(), RecommendationEdits.renamedCodes(corrected))
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
    // Undo, and the trade a correction carries.

    /**
     * Undo puts back what its save replaced. On a call's first correction that is nothing - the
     * case where Undo used to find no edit on its stale copy and do nothing at all.
     */
    @Test
    fun `undoing a first correction removes it`() {
        val only = point(0)
        val undo = EditUndo.of(report(stock(only)), edit(only, target1 = 13.85), carriedTrade = false)

        assertNull(undo.previous)
        assertEquals(true, undo.restore.isEmpty)
        assertEquals("ORAS#0", undo.restore.key)
    }

    /** On a second correction it is the first one, not nothing: Undo used to remove both. */
    @Test
    fun `undoing a second correction puts the first one back`() {
        val only = point(0)
        val first = edit(only, target1 = 13.85)
        val before = report(stock(only)).copy(edits = listOf(first))
        val undo = EditUndo.of(before, edit(only, target1 = 13.95), carriedTrade = true)

        assertEquals(first, undo.restore)
        assertEquals(true, undo.carriedTrade)
    }

    private fun identity(ticker: String) = CallIdentity(
        ticker = ticker,
        openedOn = LocalDate.parse("2026-09-03"),
        channel = "Ashum Masr",
        companyEnglish = null,
        companyArabic = null,
    )

    @Test
    fun `a trade under the corrected call is carried to the new one`() {
        val follow = tradeFollow(setOf("ORAS@2026-09-03"), identity("ORAS"), identity("ORHD"))

        assertEquals(TradeFollow.Carry("ORAS@2026-09-03", "ORHD@2026-09-03"), follow)
    }

    /** Saving there would write over a trade the reader already holds on the corrected stock. */
    @Test
    fun `a trade is never carried over one already held`() {
        val held = setOf("ORAS@2026-09-03", "ORHD@2026-09-03")
        val follow = tradeFollow(held, identity("ORAS"), identity("ORHD"))

        assertEquals(TradeFollow.Blocked("ORAS@2026-09-03", "ORHD@2026-09-03"), follow)
    }

    @Test
    fun `no trade under the call means nothing to carry`() {
        assertEquals(TradeFollow.Nothing, tradeFollow(emptySet(), identity("ORAS"), identity("ORHD")))
    }

    @Test
    fun `every source carrying one message is forgotten together`() {
        val result = report(stock(point(0)))

        assertEquals(setOf("chat-1:5501", "chat-1:5501:photo"), result.sourceIdsFor("5501"))
        assertEquals(emptySet<String>(), result.sourceIdsFor("9999"))
        assertEquals(emptySet<String>(), result.sourceIdsFor(null))
    }
}
