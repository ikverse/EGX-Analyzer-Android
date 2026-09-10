package com.ikverse.egxanalyzer.model

import java.time.LocalDate

/**
 * A correction the reader made to one extracted occurrence, kept beside the report rather than in
 * it.
 *
 * The model reads tickers and levels off channel screenshots and gets one wrong from time to time -
 * a transposed code, a decimal in the wrong place, a stop picked up from the card above. Until this
 * existed the only remedy was to delete the report and pay to run it again, which throws away every
 * other call in it to fix one.
 *
 * **It is an overlay and deliberately not a rewrite.** A report's stocks are not stored: they are
 * re-parsed from the model's `rawResponse` every time the report is read, so an edit written into
 * the parsed object would vanish on the next read, and an edit written into `rawResponse` would
 * destroy the one record of what the model actually said. The overlay is applied straight after the
 * parse, which buys three things at once: every screen picks the correction up with no change of
 * its own, because the cards, the table, the spreadsheet export, the scorer, the alerts and the
 * price fetching all derive from the same parsed list; the model's answer stays intact, so
 * undoing one is always available; and deleting the report takes its edits with
 * it, because they travel inside its payload.
 *
 * **What it is anchored to is the parse, not the screen.** [originalStockCode] is the code the
 * model read - not the corrected one, which is the thing being changed - and [pointIndex] is the
 * occurrence's position in that stock's *parsed* list. The screen filters those points by timing
 * and by channel before drawing them, so the index a card happens to sit at is not this one;
 * `RecommendationDataPoint.parseIndex` carries the right one onto the card.
 *
 * @property fingerprint what the occurrence held when the edit was made. A newer prompt or a
 * changed parse can return a different set of occurrences for the same response, and an index that
 * has shifted would land this edit on a different call and rewrite it silently. Checked before the
 * overlay is applied, and the edit is dropped rather than applied blind - the call then simply
 * reads as the model left it, which is recoverable, where a corrupted one is not.
 */
data class RecommendationEdit(
    val originalStockCode: String,
    val pointIndex: Int,
    val fingerprint: String,
    /* Stock-level. These describe the stock and so apply to every occurrence of it, not only the
     * one this edit is anchored to - a ticker is not a property of one reading of a card. */
    val stockCode: String? = null,
    val stockNameEnglish: String? = null,
    val stockNameArabic: String? = null,
    val notesSummary: String? = null,
    /* Occurrence-level. */
    val date: LocalDate? = null,
    val effectiveDateBasis: String? = null,
    val visibleSourceDate: String? = null,
    val dateEvidence: String? = null,
    val timingEvidence: String? = null,
    val sourceMessageId: String? = null,
    val sourceImageRef: Int? = null,
    val recommendationEvidence: String? = null,
    val recommendationType: String? = null,
    val buyPrice: Double? = null,
    val buyPriceLow: Double? = null,
    val buyPriceHigh: Double? = null,
    val target1: Double? = null,
    val target2: Double? = null,
    val stopLoss: Double? = null,
    val support: Double? = null,
    val resistance: Double? = null,
    val notesArabic: String? = null,
    /**
     * Fields emptied on purpose, which a null above cannot say.
     *
     * A null field is one this edit has nothing to say about; a field named here is one the reader
     * deleted. The two have to be told apart, because the model inventing a target 2 that is not on
     * the card is exactly as common as it misreading one, and "leave it alone" would be the only
     * thing the reader could express without this.
     */
    val cleared: Set<EditField> = emptySet(),
    val editedAt: Long = 0,
    val editedBy: String = "",
) {
    /** Where this edit files, which is the stock the model named and the occurrence's parse slot. */
    val key: String get() = "$originalStockCode#$pointIndex"

    private fun <T> pick(field: EditField, override: T?, original: T): T? = when {
        field in cleared -> null
        override != null -> override
        else -> original
    }

    /** This edit laid over the occurrence it was made about. Percentages are recomputed after. */
    fun applyTo(point: RecommendationDataPoint): RecommendationDataPoint = point.copy(
        date = pick(EditField.DATE, date, point.date),
        effectiveDateBasis = pick(
            EditField.EFFECTIVE_DATE_BASIS, effectiveDateBasis, point.effectiveDateBasis,
        ),
        visibleSourceDate = pick(
            EditField.VISIBLE_SOURCE_DATE, visibleSourceDate, point.visibleSourceDate,
        ),
        dateEvidence = pick(EditField.DATE_EVIDENCE, dateEvidence, point.dateEvidence),
        timingEvidence = pick(EditField.TIMING_EVIDENCE, timingEvidence, point.timingEvidence),
        sourceMessageId = pick(EditField.SOURCE_MESSAGE_ID, sourceMessageId, point.sourceMessageId),
        sourceImageRef = pick(EditField.SOURCE_IMAGE_REF, sourceImageRef, point.sourceImageRef),
        recommendationEvidence = pick(
            EditField.RECOMMENDATION_EVIDENCE, recommendationEvidence, point.recommendationEvidence,
        ),
        recommendationType = pick(
            EditField.RECOMMENDATION_TYPE, recommendationType, point.recommendationType,
        ),
        buyPrice = pick(EditField.BUY_PRICE, buyPrice, point.buyPrice),
        buyPriceLow = pick(EditField.BUY_PRICE_LOW, buyPriceLow, point.buyPriceLow),
        buyPriceHigh = pick(EditField.BUY_PRICE_HIGH, buyPriceHigh, point.buyPriceHigh),
        target1 = pick(EditField.TARGET_1, target1, point.target1),
        target2 = pick(EditField.TARGET_2, target2, point.target2),
        stopLoss = pick(EditField.STOP_LOSS, stopLoss, point.stopLoss),
        support = pick(EditField.SUPPORT, support, point.support),
        resistance = pick(EditField.RESISTANCE, resistance, point.resistance),
        notesArabic = pick(EditField.NOTES_ARABIC, notesArabic, point.notesArabic),
    )

    /** Whether this edit says anything at all, so an untouched sheet stores nothing. */
    val isEmpty: Boolean
        get() = cleared.isEmpty() && listOf(
            stockCode, stockNameEnglish, stockNameArabic, notesSummary, date, effectiveDateBasis,
            visibleSourceDate, dateEvidence, timingEvidence, sourceMessageId, sourceImageRef,
            recommendationEvidence, recommendationType, buyPrice, buyPriceLow, buyPriceHigh,
            target1, target2, stopLoss, support, resistance, notesArabic,
        ).all { it == null }
}

/**
 * A field an edit may empty rather than change.
 *
 * Stored by **name**, the rule `AppPreferences.callOrder` follows: an ordinal would silently
 * reinterpret every stored edit the moment this list is reordered. A name a build does not know is
 * dropped on read, which costs that one clearing rather than the whole edit.
 */
enum class EditField {
    DATE,
    EFFECTIVE_DATE_BASIS,
    VISIBLE_SOURCE_DATE,
    DATE_EVIDENCE,
    TIMING_EVIDENCE,
    SOURCE_MESSAGE_ID,
    SOURCE_IMAGE_REF,
    RECOMMENDATION_EVIDENCE,
    RECOMMENDATION_TYPE,
    BUY_PRICE,
    BUY_PRICE_LOW,
    BUY_PRICE_HIGH,
    TARGET_1,
    TARGET_2,
    STOP_LOSS,
    SUPPORT,
    RESISTANCE,
    NOTES_ARABIC,
    /* Stock-level, so that a name the catalog cannot supply can be emptied rather than left wrong. */
    STOCK_NAME_ENGLISH,
    STOCK_NAME_ARABIC,
    NOTES_SUMMARY,
}

/** What an occurrence held when an edit was made about it. See [RecommendationEdit.fingerprint]. */
fun RecommendationDataPoint.editFingerprint(): String = listOf(
    date?.toString(),
    effectiveDateBasis,
    visibleSourceDate,
    sourceMessageId,
    sourceImageRef?.toString(),
    buyPrice?.toString(),
    buyPriceLow?.toString(),
    buyPriceHigh?.toString(),
    target1?.toString(),
    target2?.toString(),
    stopLoss?.toString(),
).joinToString("#") { it ?: "-" }

/**
 * Every edit made to one report, and the one place they are laid over a parse.
 *
 * A list rather than a map so it round-trips through the payload in the order it was written, and
 * because the newest edit naming a stock-level field is the one that wins - two occurrences of one
 * stock can each carry an edit, and only one of them can be right about the ticker.
 */
object RecommendationEdits {

    /**
     * The parsed stocks with every edit that still fits laid over them.
     *
     * @param namesFor the catalog, asked again wherever a ticker changed. The names have to be
     * re-derived rather than carried: an edit that moved the code and kept the names would print
     * the right ticker with the wrong company underneath it, which is worse than either mistake on
     * its own. Passed in because naming lives in `data` and nothing in `model` may reach it.
     */
    fun apply(
        stocks: List<ConsolidatedRecommendation>,
        edits: List<RecommendationEdit>,
        namesFor: (code: String, english: String?, arabic: String?) -> Pair<String?, String?>,
    ): List<ConsolidatedRecommendation> {
        if (edits.isEmpty()) return stocks
        val byStock = edits.groupBy { it.originalStockCode }
        return stocks.map { stock ->
            val fitting = byStock[stock.originalStockCode].orEmpty().filter { it.fits(stock) }
            if (fitting.isEmpty()) {
                stock
            } else {
                val points = stock.dataPoints.map { point ->
                    val edit = fitting.lastOrNull { it.pointIndex == point.parseIndex }
                    if (edit == null) point else edit.applyTo(point).withDerivedPercentages()
                }
                stock.copy(dataPoints = points).withStockFields(fitting, namesFor)
            }
        }
    }

    /** Whether this edit still describes the occurrence it was filed against. */
    private fun RecommendationEdit.fits(stock: ConsolidatedRecommendation): Boolean {
        val point = stock.dataPoints.firstOrNull { it.parseIndex == pointIndex } ?: return false
        return point.editFingerprint() == fingerprint
    }

    private fun ConsolidatedRecommendation.withStockFields(
        edits: List<RecommendationEdit>,
        namesFor: (String, String?, String?) -> Pair<String?, String?>,
    ): ConsolidatedRecommendation {
        val code = edits.lastOrNull { it.stockCode != null }?.stockCode ?: stockCode
        val english = edits.lastOrNull { it.stockNameEnglish != null }?.stockNameEnglish
        val arabic = edits.lastOrNull { it.stockNameArabic != null }?.stockNameArabic
        val clearsEnglish = edits.any { EditField.STOCK_NAME_ENGLISH in it.cleared }
        val clearsArabic = edits.any { EditField.STOCK_NAME_ARABIC in it.cleared }
        val clearsNotes = edits.any { EditField.NOTES_SUMMARY in it.cleared }
        // Asked of the catalog under the new code, with whatever the reader typed as the offer -
        // which is exactly how the parse names a stock in the first place, so an edited stock and
        // an unedited one are named by one rule.
        val named = namesFor(code, english ?: stockNameEnglish, arabic ?: stockNameArabic)
        return copy(
            stockCode = code,
            stockNameEnglish = if (clearsEnglish) null else named.first,
            stockNameArabic = if (clearsArabic) null else named.second,
            notesSummary = when {
                clearsNotes -> null
                else -> edits.lastOrNull { it.notesSummary != null }?.notesSummary ?: notesSummary
            },
        )
    }
}

/**
 * The three percentages worked out again from whatever the levels now are.
 *
 * Stored on the occurrence rather than derived at every read because that is how the model returns
 * them, and the screen and the spreadsheet both print what is stored. An edit that moved a target
 * and left the percentage beside it would put two figures that contradict each other on one card -
 * so they are recomputed here, through [returnFrom], which is the same basis the scorer measures a
 * return on.
 */
fun RecommendationDataPoint.withDerivedPercentages(): RecommendationDataPoint = copy(
    returnTp1Pct = returnFrom(this, target1),
    returnTp2Pct = returnFrom(this, target2),
    riskPct = returnFrom(this, stopLoss),
)


/**
 * One occurrence of one stock in a report, found by the slot an edit is filed under.
 *
 * The pair rather than the point alone, because everything a correction has to reason about spans
 * both: the ticker and the names belong to the stock, the levels and the dating to the occurrence.
 */
data class CallSlot(
    val stock: ConsolidatedRecommendation,
    val point: RecommendationDataPoint,
)

/** The occurrence an edit names, as the report currently reads. Null once its slot has gone. */
fun AnalysisResult.callSlot(originalStockCode: String, pointIndex: Int): CallSlot? {
    val stock = consolidated.firstOrNull { it.originalStockCode == originalStockCode } ?: return null
    val point = stock.dataPoints.firstOrNull { it.parseIndex == pointIndex } ?: return null
    return CallSlot(stock, point)
}

/**
 * Ticker, session and channel: what every key filed against a call is built from.
 *
 * `opinionId`, `alertId` and `positionId` are all derived from some part of this, which is exactly
 * why a corrected ticker or a corrected date orphans them - and why the identity has to be read
 * *before* the correction is applied as well as after it.
 */
data class CallIdentity(
    val ticker: String,
    val openedOn: java.time.LocalDate,
    val channel: String,
    val companyEnglish: String?,
    val companyArabic: String?,
)

/**
 * How this occurrence is identified everywhere else in the app.
 *
 * Worked out exactly as `PerformanceCalculator` works it out - the normalized ticker, the session
 * the run was aimed at, and the chat behind the message the model cited - because a key built any
 * other way would match nothing that is actually stored, and would do so silently.
 *
 * Null on an occurrence with no session to belong to. Such a call cannot be scored, cannot be held
 * and cannot have been asked about, so there is nothing filed against it to go looking for.
 */
fun CallSlot.identity(result: AnalysisResult): CallIdentity? {
    val openedOn = point.callDate(result.recommendationTargetDate) ?: return null
    val ticker = Scoring.normalizeTicker(stock.stockCode)
    if (ticker.isBlank()) return null
    val channel = result.sources
        .firstOrNull { it.messageId?.toString() == point.sourceMessageId }
        ?.channelName
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?: PerformanceCalculator.UNKNOWN_CHANNEL
    return CallIdentity(
        ticker = ticker,
        openedOn = openedOn,
        channel = channel,
        companyEnglish = stock.stockNameEnglish,
        companyArabic = stock.stockNameArabic,
    )
}

/**
 * Which of a run's sources carried one message, so its stored reading can be forgotten.
 *
 * A message can arrive as several sources - a caption and the photos under it - and a misread came
 * out of the whole of it, so all of them go. Empty for an occurrence the model cited no message
 * for, which is the case where there is no reading to drop.
 */
fun AnalysisResult.sourceIdsFor(messageId: String?): Set<String> {
    if (messageId.isNullOrBlank()) return emptySet()
    return sources.filter { it.messageId?.toString() == messageId }.map { it.sourceId }.toSet()
}
