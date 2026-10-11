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
 * `RecommendationDataPoint.parseIndex` carries the right one onto the card, and
 * `ConsolidatedRecommendation.anchorOf` the right code, since a corrected occurrence can sit in
 * another stock's entry.
 *
 * **An edit is the whole correction of its call, measured against what the model read.** Saving
 * again replaces it, so a second save has to restate the first: the sheet builds its draft from
 * [AnalysisResult.modelReading] with this edit laid over it, which is what makes that automatic.
 *
 * @property fingerprint what the occurrence held **as the model read it** when the edit was made.
 * A newer prompt or a changed parse can return a different set of occurrences for the same response, and an index that
 * has shifted would land this edit on a different call and rewrite it silently. Checked before the
 * overlay is applied, and the edit is dropped rather than applied blind - the call then simply
 * reads as the model left it, which is recoverable, where a corrupted one is not.
 */
data class RecommendationEdit(
    val originalStockCode: String,
    val pointIndex: Int,
    val fingerprint: String,
    /*
     * Which stock this occurrence belongs to. Moves this occurrence alone, into the entry already
     * holding that code if the report has one.
     *
     * It used to rename the model's whole entry, on the argument that a ticker is not a property of
     * one reading of a card. That holds for a typo and fails for the misread that actually happens:
     * a card is read as another real listing and filed among that listing's genuine cards, and
     * fixing it renamed the genuine ones with it. And a code moved without joining the entry that
     * already held it left the report showing the same stock twice.
     */
    val stockCode: String? = null,
    /* Stock-level. These describe whichever entry the occurrence ends up in. A name typed here
     * stands over the catalog's: the reader typed it because the catalog was wrong or silent. */
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
    /**
     * Whether the trade taken on this call was carried along with the correction.
     *
     * Recorded so that undoing it - a week later, from the card, or for the whole report at once -
     * moves the trade back only if this edit is what moved it. Without it the only safe guess was
     * "always", which took a trade the reader had kept where it was on purpose and moved it anyway.
     * Says nothing about the call itself, so it does not make an edit non-empty.
     */
    val movedTrade: Boolean = false,
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
 * because the newest edit naming a stock-level field is the one that wins - two occurrences that
 * end up in one entry can each carry a name for it, and only one of them can be what it is called.
 *
 * **Corrected tickers are merged here, not on any screen.** An occurrence whose ticker was
 * corrected joins the entry that already holds the corrected code, so the report reads one stock
 * as one block wherever it is drawn - the table, the cards, the spreadsheet, the counts on the
 * report card - and nothing downstream has to know a correction happened.
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
        val ownCodes = stocks.map { it.stockCode }.toSet()
        // One bucket per code, in the order the report already ranks them. A code the model filed
        // an entry under keeps that entry's place even when an occurrence moving into it is met
        // first; a code nobody filed takes the place of the occurrence that moved there, so a
        // stock corrected wholesale stays where it was in the report rather than dropping to the
        // foot of it.
        val buckets = LinkedHashMap<String, MutableList<Placed>>()
        val waiting = HashMap<String, MutableList<Placed>>()
        stocks.forEach { stock ->
            if (stock.stockCode !in buckets) {
                buckets[stock.stockCode] = waiting.remove(stock.stockCode) ?: mutableListOf()
            }
            val fitting = byStock[stock.originalStockCode].orEmpty().filter { it.fits(stock) }
            stock.dataPoints.forEach { point ->
                val edit = fitting.lastOrNull { it.pointIndex == point.parseIndex }
                val laid = if (edit == null) point else edit.applyTo(point).withDerivedPercentages()
                val placed = Placed(stock, laid, edit)
                when (val code = edit?.stockCode ?: stock.stockCode) {
                    in buckets -> buckets.getValue(code) += placed
                    in ownCodes -> waiting.getOrPut(code) { mutableListOf() } += placed
                    else -> buckets.getOrPut(code) { mutableListOf() } += placed
                }
            }
        }
        return buckets.mapNotNull { (code, members) -> entry(code, members, edits, namesFor) }
    }

    /**
     * Which code each of the model's codes now reads as, for the codes every occurrence has left.
     *
     * For the flat `recommendations` list, which is keyed by code and has no slot to tell its rows
     * apart by. Worked out from the corrected report rather than from the edits, so an edit that no
     * longer fits renames nothing - it used to rename the flat list while the report beside it
     * still read as the model left it. A code only some of whose occurrences moved is left alone:
     * the rest of its rows are still that stock.
     */
    fun renamedCodes(stocks: List<ConsolidatedRecommendation>): Map<String, String> = stocks
        .flatMap { stock -> stock.dataPoints.map { stock.anchorOf(it) to stock.stockCode } }
        .groupBy({ it.first }, { it.second })
        .mapNotNull { (from, now) ->
            now.distinct().singleOrNull()?.takeIf { it != from }?.let { from to it }
        }
        .toMap()

    /** One occurrence on its way to the entry it now belongs in. */
    private class Placed(
        val from: ConsolidatedRecommendation,
        val point: RecommendationDataPoint,
        val edit: RecommendationEdit?,
    )

    /** Whether this edit still describes the occurrence it was filed against. */
    private fun RecommendationEdit.fits(stock: ConsolidatedRecommendation): Boolean {
        val point = stock.dataPoints.firstOrNull { it.parseIndex == pointIndex } ?: return false
        return point.editFingerprint() == fingerprint
    }

    /**
     * Every occurrence now filed under [code], as one entry. Null when every one of them moved out.
     *
     * The entry the model filed under this code leads, so its rank, notes and order stand and the
     * occurrences moved into it follow its own. An occurrence that came from another entry carries
     * that entry's code on itself, because the entry it sits in can no longer say it.
     */
    private fun entry(
        code: String,
        members: List<Placed>,
        edits: List<RecommendationEdit>,
        namesFor: (String, String?, String?) -> Pair<String?, String?>,
    ): ConsolidatedRecommendation? {
        if (members.isEmpty()) return null
        val ordered = members.sortedBy { if (it.from.stockCode == code) 0 else 1 }
        val lead = ordered.first().from
        val points = ordered.map { placed ->
            if (placed.from === lead) {
                placed.point
            } else {
                placed.point.copy(originalStockCode = placed.from.anchorOf(placed.point))
            }
        }
        val touched = ordered.mapNotNull(Placed::edit)
        // In the order they were made, so the newest edit naming a field is the one that says it.
        val mine = edits.filter { edit -> touched.any { it === edit } }
        val moved = code != lead.stockCode || ordered.any { it.from !== lead }
        if (!moved && mine.none { it.saysAboutStock }) return lead.copy(dataPoints = points)

        // Offered to the catalog only from an entry the model itself filed under this code. An
        // occurrence moved here from another code brings that company's names, which are the one
        // thing certain to be wrong - printed under the right ticker, they make a card that agrees
        // with itself and is wrong.
        val own = ordered.firstOrNull { it.from.stockCode == code }?.from
        val named = namesFor(code, own?.stockNameEnglish, own?.stockNameArabic)
        val english = mine.lastOrNull { it.names(EditField.STOCK_NAME_ENGLISH, it.stockNameEnglish) }
        val arabic = mine.lastOrNull { it.names(EditField.STOCK_NAME_ARABIC, it.stockNameArabic) }
        val notes = mine.lastOrNull { it.names(EditField.NOTES_SUMMARY, it.notesSummary) }
        return lead.copy(
            stockCode = code,
            stockNameEnglish = english.typed(
                EditField.STOCK_NAME_ENGLISH, english?.stockNameEnglish, named.first,
            ),
            stockNameArabic = arabic.typed(
                EditField.STOCK_NAME_ARABIC, arabic?.stockNameArabic, named.second,
            ),
            notesSummary = notes.typed(
                EditField.NOTES_SUMMARY,
                notes?.notesSummary,
                ordered.firstNotNullOfOrNull { it.from.notesSummary },
            ),
            // Not shown anywhere; kept honest for whatever reads it next. Each occurrence moved in
            // is one more mention of this stock.
            mentionCount = lead.mentionCount + ordered.count { it.from !== lead },
            rank = ordered.minOf { it.from.rank },
            dataPoints = points,
        )
    }

    /** Whether this edit says anything about a stock-level field: a value, or that it is empty. */
    private fun RecommendationEdit.names(field: EditField, value: String?): Boolean =
        value != null || field in cleared

    private val RecommendationEdit.saysAboutStock: Boolean
        get() = names(EditField.STOCK_NAME_ENGLISH, stockNameEnglish) ||
            names(EditField.STOCK_NAME_ARABIC, stockNameArabic) ||
            names(EditField.NOTES_SUMMARY, notesSummary)

    /** What the reader typed for a stock-level field, or [otherwise] when they typed nothing. */
    private fun RecommendationEdit?.typed(field: EditField, value: String?, otherwise: String?) =
        when {
            this == null -> otherwise
            field in cleared -> null
            else -> value
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

/**
 * The occurrence an edit names, as the report currently reads. Null once its slot has gone.
 *
 * Searched across every entry rather than in the one whose own code matches: a corrected ticker
 * carries an occurrence into another stock's entry, and from then on the entry it was read into
 * no longer holds it. Every lookup of a call by its slot comes through here for that reason.
 */
fun AnalysisResult.callSlot(originalStockCode: String, pointIndex: Int): CallSlot? =
    consolidated.slotOf(originalStockCode, pointIndex)

/**
 * The same occurrence as the model read it, before any correction - what a second correction
 * starts from, and what its fingerprint is checked against.
 */
fun AnalysisResult.modelSlot(originalStockCode: String, pointIndex: Int): CallSlot? =
    modelReading.ifEmpty { consolidated }.slotOf(originalStockCode, pointIndex)

private fun List<ConsolidatedRecommendation>.slotOf(code: String, index: Int): CallSlot? {
    for (stock in this) {
        val point = stock.dataPoints
            .firstOrNull { it.parseIndex == index && stock.anchorOf(it) == code }
        if (point != null) return CallSlot(stock, point)
    }
    return null
}

/**
 * What one save replaced, so that its Undo puts back exactly that.
 *
 * Taken from the report as it stood **before** the save. Undo used to work it out afterwards from a
 * copy captured at the same moment, asked that copy whether an edit existed, found none on a
 * call's first correction, and quietly did nothing; on a second correction it found the first and
 * removed both. The edit to put back is the one the save replaced - nothing on a first
 * correction, the earlier correction on a later one.
 */
data class EditUndo(
    val originalStockCode: String,
    val pointIndex: Int,
    val previous: RecommendationEdit?,
    /** Whether the save being undone carried the trade, which is whether Undo carries it back. */
    val carriedTrade: Boolean,
) {
    /** What to write back: the edit this save replaced, or a removal when there was none. */
    val restore: RecommendationEdit
        get() = previous ?: RecommendationEdit(originalStockCode, pointIndex, fingerprint = "")

    companion object {
        fun of(before: AnalysisResult, edit: RecommendationEdit, carriedTrade: Boolean) = EditUndo(
            originalStockCode = edit.originalStockCode,
            pointIndex = edit.pointIndex,
            previous = before.edits.firstOrNull { it.key == edit.key },
            carriedTrade = carriedTrade,
        )
    }
}

/**
 * What happens to the trade taken on a corrected call.
 *
 * Decided apart from the moving itself so it can be checked, because the case that matters loses
 * money silently: a position is one stock on one session, and saving the moved trade under an id
 * that already holds one overwrote that trade with this one.
 */
sealed interface TradeFollow {
    /** No trade was filed under the call being corrected. */
    data object Nothing : TradeFollow

    /** Re-filed from [from] to [to], or its levels brought up to date when the two are the same. */
    data class Carry(val from: String, val to: String) : TradeFollow

    /** Left where it is, because [to] already holds a trade on the corrected call's stock. */
    data class Blocked(val from: String, val to: String) : TradeFollow
}

fun tradeFollow(heldIds: Set<String>, was: CallIdentity, now: CallIdentity?): TradeFollow {
    val from = positionId(was.ticker, was.openedOn)
    if (from !in heldIds) return TradeFollow.Nothing
    val to = now?.let { positionId(it.ticker, it.openedOn) } ?: from
    return if (to != from && to in heldIds) TradeFollow.Blocked(from, to) else TradeFollow.Carry(from, to)
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
