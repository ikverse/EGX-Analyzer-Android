package com.ikverse.egxanalyzer.ui

import com.ikverse.egxanalyzer.model.CallSlot
import com.ikverse.egxanalyzer.model.ConsolidatedRecommendation
import com.ikverse.egxanalyzer.model.EditField
import com.ikverse.egxanalyzer.model.RecommendationDataPoint
import com.ikverse.egxanalyzer.model.RecommendationEdit
import com.ikverse.egxanalyzer.model.RecommendationEdits
import com.ikverse.egxanalyzer.model.editFingerprint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * That the edit sheet's draft says what the reader meant, measured against what the model read.
 *
 * The case this file exists for is the second save. The draft used to be built from the corrected
 * call, so its "original" was the first correction and its fingerprint was the corrected point's:
 * the edit a second save produced either left the first correction out or never fitted at all, and
 * the call quietly went back to the model's reading. Plain JVM: the draft is snapshot state and a
 * handful of strings, and needs no composition to be asked what it would save.
 */
class CallDraftTest {

    private val catalog = listOf(
        CatalogStock("ORAS", "Orascom Construction PLC", "اوراسكوم كونستراكشون"),
        CatalogStock("ORHD", "Orascom Development Egypt", "أوراسكوم للتنمية مصر"),
    )

    private val names: (String, String?, String?) -> Pair<String?, String?> = { code, en, ar ->
        catalog.firstOrNull { it.ticker == code }?.let { it.nameEnglish to it.nameArabic } ?: (en to ar)
    }

    private val read = RecommendationDataPoint(
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
        target1 = 13.20,
        returnTp1Pct = 7.7,
        target2 = 14.00,
        returnTp2Pct = 14.2,
        stopLoss = 11.80,
        support = 11.60,
        resistance = 14.35,
        riskPct = -3.7,
        notesArabic = null,
        parseIndex = 0,
    )

    private val model = ConsolidatedRecommendation(
        stockCode = "ORAS",
        stockNameEnglish = "Orascom Construction PLC",
        stockNameArabic = "اوراسكوم كونستراكشون",
        mentionCount = 1,
        rank = 1,
        notesSummary = null,
        dataPoints = listOf(read),
    )

    /** The call as the report draws it once [edits] are laid over the model's reading. */
    private fun corrected(vararg edits: RecommendationEdit): CallSlot {
        val stock = RecommendationEdits.apply(listOf(model), edits.toList(), names).single()
        return CallSlot(stock, stock.dataPoints.single())
    }

    private fun draft(standing: RecommendationEdit? = null) = CallDraft(
        model = CallSlot(model, read),
        current = standing?.let { corrected(it) } ?: CallSlot(model, read),
        standing = standing,
        catalog = catalog,
    )

    private val firstCorrection = RecommendationEdit(
        originalStockCode = "ORAS",
        pointIndex = 0,
        fingerprint = read.editFingerprint(),
        stockCode = "ORHD",
        target1 = 13.85,
    )

    @Test
    fun `a second save keeps every correction made before it`() {
        val again = draft(firstCorrection)
        again.stopLoss.text = "11.5"

        val second = again.toEdit()
        assertEquals(read.editFingerprint(), second.fingerprint)
        assertEquals("ORHD", second.stockCode)
        assertEquals(13.85, second.target1)
        assertEquals(11.5, second.stopLoss)

        // And it fits: laid over the model's reading, both corrections are there.
        val now = corrected(second)
        assertEquals("ORHD", now.stock.stockCode)
        assertEquals(13.85, now.point.target1)
        assertEquals(11.5, now.point.stopLoss)
    }

    /** "Was" means what the model read, not what the last correction said. */
    @Test
    fun `a reopened correction shows the model's reading underneath`() {
        val again = draft(firstCorrection)

        assertEquals("13.85", again.target1.text)
        assertEquals("13.2", again.target1.original)
        assertEquals("ORAS", again.originalTicker)
        assertEquals("ORHD", again.ticker)
    }

    @Test
    fun `a corrected call opened again has nothing new to save`() {
        assertFalse(draft(firstCorrection).hasChanges)
        assertFalse(draft().hasChanges)
    }

    /** Stored upper case and without `.CA`, so neither is a correction. */
    @Test
    fun `a change of case is not a correction`() {
        val typed = draft()
        typed.ticker = "oras.ca"

        assertFalse(typed.hasChanges)
        assertTrue(typed.toEdit().isEmpty)
    }

    @Test
    fun `a price typed with a decimal comma is read`() {
        val comma = draft()
        comma.target1.text = "13,9"
        val arabic = draft()
        arabic.target1.text = "١٣٫٩"

        assertEquals(13.9, comma.toEdit().target1)
        assertEquals(13.9, arabic.toEdit().target1)
        assertTrue(comma.isValid)
    }

    /** Left out of the edit instead, it would have been a silent no-op - or a removal. */
    @Test
    fun `an unreadable value blocks the save`() {
        val price = draft()
        price.target1.text = "13.x"
        val date = draft()
        date.date.text = "3 Sept"
        val separators = draft()
        separators.target1.text = "1,3,9"

        assertFalse(price.isValid)
        assertFalse(date.isValid)
        assertFalse(separators.isValid)
    }

    /** With nowhere to record it, unticking the timing used to make the edit empty. */
    @Test
    fun `unticking the timing is a clearing rather than nothing`() {
        val untimed = draft()
        untimed.basis = null

        val edit = untimed.toEdit()
        assertTrue(EditField.EFFECTIVE_DATE_BASIS in edit.cleared)
        assertFalse(edit.isEmpty)
        assertNull(corrected(edit).point.effectiveDateBasis)
    }

    @Test
    fun `names follow the ticker until the reader types one`() {
        val moved = draft()
        moved.ticker = "ORHD"
        assertEquals("Orascom Development Egypt", moved.shownEnglish)
        assertNull(moved.toEdit().stockNameEnglish)

        // Typing the catalog's own name back is not a name of the reader's.
        moved.typeEnglish("Orascom Development Egypt")
        assertNull(moved.typedEnglish)

        moved.typeEnglish("Orascom Dev")
        assertEquals("Orascom Dev", moved.toEdit().stockNameEnglish)
        assertEquals("Orascom Dev", corrected(moved.toEdit()).stock.stockNameEnglish)
    }
}
