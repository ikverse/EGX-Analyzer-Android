package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.Dividend
import com.ikverse.egxanalyzer.model.DividendBook
import com.ikverse.egxanalyzer.model.DividendCheck
import com.ikverse.egxanalyzer.model.DividendNews
import com.ikverse.egxanalyzer.model.DividendNewsKind
import com.ikverse.egxanalyzer.model.DividendRecord
import com.ikverse.egxanalyzer.model.PaidDividend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The dividend parser and rules, ported with their cases from Signal Lab's `EgxDividendsTest`.
 * The fixture is Arab Finance's dividend page as recorded on 14 November 2026.
 */
class DividendsTest {
    private fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d)

    private val page = requireNotNull(javaClass.getResource("/dividends/arabfinance_dividends.html")).readText()
    private val items = ArabFinanceParser.parse(page)

    private fun first(code: String, kind: DividendNewsKind? = null) =
        items.first { it.code == code && (kind == null || it.kind == kind) }

    private val today = day(2026, 11, 14)
    private val paid = mapOf(
        "MHOT" to listOf(PaidDividend(day(2025, 9, 29), 0.75), PaidDividend(day(2026, 10, 13), 1.2)),
        "ISPH" to listOf(PaidDividend(day(2026, 10, 12), 0.130004)),
        "FERC" to listOf(PaidDividend(day(2026, 11, 10), 1.25)),
        "EDFM" to listOf(PaidDividend(day(2026, 11, 1), 23.0)),
    )
    private val book = DividendBook.merge(emptyList(), items, paid, today)
    private fun of(code: String) = book.single { it.code == code }

    @Test
    fun readsEveryItemOnTheRecordedPage() {
        assertEquals(20, items.size)
        assertEquals(
            listOf("PRDC", "ADCI", "MPCI", "FERC", "ISMQ", "CSAG", "EDFM", "CEFM", "FERC", "EDFM", "EFIH", "ISPH",
                "MFSC", "MICH", "WCDF", "MHOT", "CICH", "WCDF", "MHOT", "ISPH"),
            items.map { it.code },
        )
    }

    @Test
    fun aNoticeGivesTheAmountAndThePaymentDay() {
        val p = items.first()
        assertEquals(DividendNewsKind.NOTICE, p.kind)
        assertEquals(0.1, p.amount!!, 1e-12)
        assertEquals(day(2026, 11, 30), p.paymentDay)
        assertEquals(day(2026, 11, 12), p.published)
        assertTrue(p.url.startsWith("https://www.arabfinance.com/ar/news/newdetails/"))
        assertEquals(day(2026, 10, 14), first("ISPH", DividendNewsKind.NOTICE).paymentDay)
    }

    @Test
    fun theWordingVariesAndIsStillRead() {
        assertEquals(day(2026, 11, 19), first("ISMQ").paymentDay)
        assertEquals(0.35, first("ISMQ").amount!!, 1e-12)
        assertEquals(day(2026, 11, 2), first("CEFM").paymentDay)
        assertEquals(day(2026, 9, 24), first("CICH").paymentDay)
    }

    @Test
    fun anAssemblyGivesItsDay() {
        val csag = first("CSAG")
        assertEquals(DividendNewsKind.ASSEMBLY, csag.kind)
        assertEquals(day(2026, 10, 28), csag.assemblyDay)
        assertNull(csag.amount)
        assertEquals(1.2, first("MHOT", DividendNewsKind.ASSEMBLY).amount!!, 1e-12)
    }

    @Test
    fun aDateCutShortIsNotGuessed() {
        val cut = items.filter { it.code == "FERC" }.single { it.paymentDay == null }
        assertEquals(DividendNewsKind.OTHER, cut.kind)
        assertNull(first("MICH").paymentDay)
    }

    @Test
    fun arabicDigitsPiastresAndForeignLinks() {
        val html = """
            <ul><li><a class="x" href='/ar/news/newdetails/abc'>شركة <b>ألفا</b> توزع كوبونا بقيمة ٣٥ قرشا للسهم</a>
            <div>أعلنت شركة ألفا (ALFA) توزيع الكوبون اعتبارًا من ٠٥/١٢/٢٠٢٦ للسهم</div>
            <small>آخر تحديث منذ 2026/11/13 10:00:00 ص</small></li>
            <li><a href="https://evil.example/news/newdetails/x">(BETA) بواقع 1 جنيه اعتبارا من 01/12/2026</a><p>x</p></li></ul>
        """.trimIndent()
        val parsed = ArabFinanceParser.parse(html)
        val one = parsed.first { it.code == "ALFA" }
        assertEquals(0.35, one.amount!!, 1e-12)
        assertEquals(day(2026, 12, 5), one.paymentDay)
        // A link anywhere but Arab Finance is never kept for the browser.
        assertEquals("", parsed.first { it.code == "BETA" }.url)
        assertTrue(ArabFinanceParser.parse("<html><body>maintenance</body></html>").isEmpty())
    }

    @Test
    fun eachDividendAppearsOnce() {
        assertEquals(15, book.size)
        assertEquals(day(2026, 10, 15), of("MHOT").paymentDay)
        assertEquals(day(2026, 11, 12), of("FERC").paymentDay)
    }

    @Test
    fun theLastDayToBuyIsThreeSessionsBeforePayment() {
        val ismq = of("ISMQ")
        assertEquals(day(2026, 11, 16), ismq.lastCumDay)
        assertTrue(ismq.lastCumEstimated)
        assertEquals(DividendCheck.WAITING, ismq.check)
    }

    @Test
    fun theFeedConfirmsOrQuestionsEachOne() {
        val mhot = of("MHOT")
        assertEquals(DividendCheck.CONFIRMED, mhot.check)
        assertEquals(day(2026, 10, 12), mhot.lastCumDay)
        assertFalse(mhot.lastCumEstimated)
        assertEquals(DividendCheck.CONFIRMED, of("ISPH").check)
        assertEquals(DividendCheck.AMOUNT_DIFFERS, of("EDFM").check)
        assertEquals(DividendCheck.NOT_ON_FEED, of("WCDF").check)
    }

    @Test
    fun upcomingSoonestFirst() {
        assertEquals(listOf("ISMQ", "ADCI", "MPCI", "PRDC"), DividendBook.upcoming(book, today).map { it.code })
    }

    @Test
    fun mergingTheSameNewsAgainChangesNothing() {
        assertEquals(book, DividendBook.merge(book, items, paid, today))
    }

    @Test
    fun aCorrectedPaymentDayReplacesTheOldOne() {
        val first = DividendNews("ABCD", DividendNewsKind.NOTICE, 1.0, day(2026, 11, 22), null, day(2026, 11, 10), "a", "u1")
        val fixed = first.copy(paymentDay = day(2026, 11, 24), published = day(2026, 11, 11), url = "u2")
        val out = DividendBook.merge(emptyList(), listOf(fixed, first), emptyMap(), today).single()
        assertEquals(day(2026, 11, 24), out.paymentDay)
        assertEquals("u2", out.url)
    }

    @Test
    fun shiftSkipsFridayAndSaturday() {
        // Thursday 19 Nov 2026 forward one session is Sunday 22 Nov.
        assertEquals(day(2026, 11, 22), DividendBook.shift(day(2026, 11, 19), 1))
        assertEquals(day(2026, 11, 19), DividendBook.shift(day(2026, 11, 22), -1))
    }

    @Test
    fun theCardPillFindsAnExDayInsideALife() {
        val record = DividendRecord(book, paid)
        // MHOT went ex on 13 Oct 2026: a call opened 1 Oct and settled 20 Oct carries it.
        assertEquals(day(2026, 10, 13), DividendBook.inside(record, "MHOT", day(2026, 10, 1), day(2026, 10, 20))!!.exDay)
        // Bought on the ex-day itself, there was no dividend in it.
        assertNull(DividendBook.inside(record, "MHOT", day(2026, 10, 13), day(2026, 10, 20)))
        // Still open: the next announced ex-day counts, and is marked estimated.
        val ismq = DividendBook.inside(record, "ISMQ", day(2026, 11, 1), null)!!
        assertEquals(day(2026, 11, 17), ismq.exDay)
        assertTrue(ismq.estimated)
    }

    @Test
    fun theStoreRoundTrips() {
        val record = DividendRecord(book, paid, newsAt = 1234L)
        assertEquals(record, decodeDividends(encodeDividends(record)))
        assertEquals(DividendRecord(), runCatching { decodeDividends("not json") }.getOrDefault(DividendRecord()))
    }

    @Test
    fun theChartAnswerCarriesItsDividends() {
        val payload = """{"chart":{"result":[{"events":{"dividends":{
            "1760313600":{"amount":1.2,"date":1760313600},
            "1727568000":{"amount":0.75,"date":1727568000}}}}]}}"""
        assertEquals(
            listOf(PaidDividend(day(2024, 9, 29), 0.75), PaidDividend(day(2025, 10, 13), 1.2)),
            parseDividends(payload),
        )
        assertTrue(parseDividends("""{"chart":{"result":[{}]}}""").isEmpty())
    }

    @Test
    fun aFeedDividendFoldsInOnce() {
        val a = DividendBook.withPaid(emptyMap(), "X", listOf(PaidDividend(day(2026, 1, 5), 1.0)))
        val b = DividendBook.withPaid(a, "X", listOf(PaidDividend(day(2026, 1, 5), 1.0), PaidDividend(day(2025, 1, 5), 0.5)))
        assertEquals(listOf(day(2025, 1, 5), day(2026, 1, 5)), b.getValue("X").map { it.exDay })
        assertEquals(emptyList<Dividend>(), DividendBook.upcoming(emptyList(), today))
    }
}
