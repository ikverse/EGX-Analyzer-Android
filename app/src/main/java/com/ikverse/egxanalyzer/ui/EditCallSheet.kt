package com.ikverse.egxanalyzer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.ikverse.egxanalyzer.ui.theme.extraColors
import com.ikverse.egxanalyzer.model.callDate
import com.ikverse.egxanalyzer.model.SavedAnalysis
import com.ikverse.egxanalyzer.model.ConsolidatedRecommendation
import com.ikverse.egxanalyzer.model.EditField
import com.ikverse.egxanalyzer.model.RecommendationDataPoint
import com.ikverse.egxanalyzer.model.RecommendationEdit
import com.ikverse.egxanalyzer.model.editFingerprint
import com.ikverse.egxanalyzer.model.timing
import com.ikverse.egxanalyzer.model.DERIVED_ALPHA
import java.time.LocalDate

/**
 * Correcting one extracted occurrence, on the screen it was read off.
 *
 * The model reads a card from a screenshot and gets a ticker or a level wrong from time to time,
 * and the only remedy used to be deleting the report and paying to run it again - which throws away
 * every other call in it to fix one. What this sheet writes is a `RecommendationEdit`, an overlay
 * on the report rather than a rewrite of the model's answer, so the correction is undoable and the
 * record of what the model actually said survives it.
 *
 * **The consequences are stated before the press, not after it.** Correcting a ticker deletes the
 * AI opinion on that call and re-keys the trade taken on it, and neither of those is a thing to
 * discover afterwards - so the block above the buttons names them, and the trade is named by the
 * price and the day it was bought on. That block is also where the one warning lives that the app
 * cannot act on for the reader: correcting a stock to a code already in this report from the same
 * channel leaves the scorer with two calls where it keeps one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditCallSheet(
    stock: ConsolidatedRecommendation,
    point: RecommendationDataPoint,
    editor: CallEditor,
    onDismiss: () -> Unit,
) {
    val draft = rememberCallDraft(stock, point)
    val catalog = remember { editor.catalog() }
    val held = remember(stock, point) { editor.tradeOn(stock, point) }
    val clash = remember(draft.ticker, stock) { editor.clashFor(stock, draft.ticker) }
    var correctTrade by remember(held) { mutableStateOf(held != null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .sheetDragSlop()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Space.l),
                verticalArrangement = Arrangement.spacedBy(Space.l),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    Text("Edit call", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        listOfNotNull(
                            stock.originalStockCode,
                            timing(point),
                            editor.sessionOf(point)?.let({ shortDate(it) }),
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = PriceRole.muted,
                    )
                }

                StockSection(draft, catalog, clash)
                LevelsSection(draft)
                TimingSection(draft)
                EvidenceSection(draft)
                ConsequenceSection(
                    stock = stock,
                    draft = draft,
                    held = held,
                    correctTrade = correctTrade,
                    onCorrectTrade = { correctTrade = it },
                )
            }

            HorizontalDivider()
            FlowRow(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.l, vertical = Space.m),
                horizontalArrangement = Arrangement.spacedBy(Space.s, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(Space.xs),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                ActionPill(
                    label = "Save",
                    icon = Icons.Outlined.Check,
                    enabled = draft.hasChanges,
                    onClick = {
                        editor.save(stock, point, draft.toEdit(stock, point), correctTrade)
                        onDismiss()
                    },
                )
            }
        }
    }
}

/**
 * Every field of one occurrence while it is being corrected, held as the text that is on screen.
 *
 * Text rather than numbers, deliberately: a price field mid-edit is legitimately "12." for a
 * keystroke, and a draft that stored a `Double?` would empty the box under the reader's finger.
 * Parsing happens once, at [toEdit].
 *
 * Every field remembers what the model read beside what it now says, which is what lets the sheet
 * mark a changed field, show the original under it, and put it back one field at a time.
 */
internal class CallDraft(stock: ConsolidatedRecommendation, point: RecommendationDataPoint) {
    val fields = linkedMapOf<EditField, DraftField>()

    var ticker by mutableStateOf(stock.stockCode)
    val originalTicker = stock.stockCode

    val nameEnglish = field(EditField.STOCK_NAME_ENGLISH, stock.stockNameEnglish)
    val nameArabic = field(EditField.STOCK_NAME_ARABIC, stock.stockNameArabic)
    val entryLow = field(EditField.BUY_PRICE_LOW, price(point.buyPriceLow ?: point.buyPrice))
    val entryHigh = field(EditField.BUY_PRICE_HIGH, price(point.buyPriceHigh ?: point.buyPrice))
    val target1 = field(EditField.TARGET_1, price(point.target1))
    val target2 = field(EditField.TARGET_2, price(point.target2))
    val stopLoss = field(EditField.STOP_LOSS, price(point.stopLoss))
    val support = field(EditField.SUPPORT, price(point.support))
    val resistance = field(EditField.RESISTANCE, price(point.resistance))
    val visibleSourceDate = field(EditField.VISIBLE_SOURCE_DATE, point.visibleSourceDate)
    val date = field(EditField.DATE, point.date?.toString())
    val recommendationType = field(EditField.RECOMMENDATION_TYPE, point.recommendationType)
    val notesSummary = field(EditField.NOTES_SUMMARY, stock.notesSummary)
    val notesArabic = field(EditField.NOTES_ARABIC, point.notesArabic)
    val dateEvidence = field(EditField.DATE_EVIDENCE, point.dateEvidence)
    val timingEvidence = field(EditField.TIMING_EVIDENCE, point.timingEvidence)
    val recommendationEvidence =
        field(EditField.RECOMMENDATION_EVIDENCE, point.recommendationEvidence)
    val sourceMessageId = field(EditField.SOURCE_MESSAGE_ID, point.sourceMessageId)
    val sourceImageRef = field(EditField.SOURCE_IMAGE_REF, point.sourceImageRef?.toString())

    var basis by mutableStateOf(point.effectiveDateBasis)
    val originalBasis = point.effectiveDateBasis

    private fun field(which: EditField, original: String?): DraftField =
        DraftField(original.orEmpty()).also { fields[which] = it }

    val hasChanges: Boolean
        get() = ticker.trim() != originalTicker ||
            basis != originalBasis ||
            fields.values.any { it.changed }

    /** The middle of whatever the entry band now says, for the percentages under the targets. */
    private val entryMid: Double?
        get() {
            val low = entryLow.number
            val high = entryHigh.number
            return when {
                low != null && high != null -> (low + high) / 2
                else -> low ?: high
            }
        }

    /** What a target is worth from the band as it now reads, recomputed on every keystroke. */
    fun returnOn(field: DraftField): Double? {
        val target = field.number ?: return null
        val entry = entryMid ?: return null
        if (entry == 0.0) return null
        return (target - entry) / entry * 100
    }

    /**
     * The correction this draft amounts to, with everything untouched left out.
     *
     * A field the reader emptied becomes a clearing rather than a null, because the two mean
     * different things - see `RecommendationEdit.cleared`. A field they never touched contributes
     * neither, which is what keeps an edit small and what makes putting one field back the same
     * operation as never having changed it.
     */
    fun toEdit(
        stock: ConsolidatedRecommendation,
        point: RecommendationDataPoint,
    ): RecommendationEdit {
        val cleared = fields.filterValues { it.changed && it.text.isBlank() }.keys.toMutableSet()
        if (EditField.BUY_PRICE_LOW in cleared && EditField.BUY_PRICE_HIGH in cleared) {
            // The band reads `buyPriceLow ?: buyPrice`, so a single price the model returned would
            // survive both halves being emptied and the deletion would look as if it had failed.
            cleared += EditField.BUY_PRICE
        }
        fun text(which: EditField): String? =
            fields[which]?.takeIf { it.changed && it.text.isNotBlank() }?.text?.trim()

        fun number(which: EditField): Double? = text(which)?.toDoubleOrNull()

        return RecommendationEdit(
            originalStockCode = stock.originalStockCode,
            pointIndex = point.parseIndex,
            fingerprint = point.editFingerprint(),
            stockCode = ticker.trim().uppercase().removeSuffix(".CA")
                .takeIf { it.isNotBlank() && it != stock.stockCode },
            stockNameEnglish = text(EditField.STOCK_NAME_ENGLISH),
            stockNameArabic = text(EditField.STOCK_NAME_ARABIC),
            notesSummary = text(EditField.NOTES_SUMMARY),
            date = text(EditField.DATE)?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            recommendationType = text(EditField.RECOMMENDATION_TYPE),
            effectiveDateBasis = basis?.takeIf { it != point.effectiveDateBasis },
            visibleSourceDate = text(EditField.VISIBLE_SOURCE_DATE),
            dateEvidence = text(EditField.DATE_EVIDENCE),
            timingEvidence = text(EditField.TIMING_EVIDENCE),
            sourceMessageId = text(EditField.SOURCE_MESSAGE_ID),
            sourceImageRef = text(EditField.SOURCE_IMAGE_REF)?.toIntOrNull(),
            recommendationEvidence = text(EditField.RECOMMENDATION_EVIDENCE),
            buyPriceLow = number(EditField.BUY_PRICE_LOW),
            buyPriceHigh = number(EditField.BUY_PRICE_HIGH),
            target1 = number(EditField.TARGET_1),
            target2 = number(EditField.TARGET_2),
            stopLoss = number(EditField.STOP_LOSS),
            support = number(EditField.SUPPORT),
            resistance = number(EditField.RESISTANCE),
            notesArabic = text(EditField.NOTES_ARABIC),
            cleared = cleared.toSet(),
        )
    }

    private fun price(value: Double?): String? = value?.let { formatPrice(it) }
}

/** One field of the draft: what it says now, and what the model read. */
internal class DraftField(val original: String) {
    var text by mutableStateOf(original)
    val changed: Boolean get() = text.trim() != original.trim()
    val number: Double? get() = text.trim().toDoubleOrNull()

    fun reset() {
        text = original
    }
}

@Composable
private fun rememberCallDraft(
    stock: ConsolidatedRecommendation,
    point: RecommendationDataPoint,
): CallDraft = remember(stock.originalStockCode, point.parseIndex) { CallDraft(stock, point) }

@Composable
private fun StockSection(draft: CallDraft, catalog: List<CatalogStock>, clash: String?) {
    var searching by remember { mutableStateOf(false) }
    val matches = remember(draft.ticker, catalog, searching) {
        if (!searching) {
            emptyList()
        } else {
            val wanted = StockSearch.query(draft.ticker)
            catalog
                .filter { StockSearch.matches(wanted, it.ticker, it.nameEnglish, it.nameArabic) }
                .take(CATALOG_SUGGESTIONS)
        }
    }
    val known = remember(draft.ticker, catalog) {
        catalog.any { it.ticker.equals(draft.ticker.trim(), ignoreCase = true) }
    }

    SheetSection("Stock") {
        OutlinedTextField(
            value = draft.ticker,
            onValueChange = {
                draft.ticker = it
                searching = true
            },
            label = { Text("Ticker") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (draft.ticker.trim() != draft.originalTicker) {
            WasLine("was ${draft.originalTicker}") { draft.ticker = draft.originalTicker }
        }
        // Free text is allowed - a listing this build's catalog has never heard of is a real thing,
        // and refusing it would make a new listing uncorrectable - but it is said out loud, because
        // an unknown code is also exactly what a typo looks like and it will not price.
        if (draft.ticker.isNotBlank() && !known) {
            Caution("Not in the EGX catalog. Prices may not be found for it.")
        }
        clash?.let { Caution(it) }
        matches.forEach { match ->
            CatalogRow(match) {
                draft.ticker = match.ticker
                // Filled in rather than left for the reader, which is the whole point of picking
                // from the catalog rather than typing - and they stay editable underneath.
                draft.nameArabic.text = match.nameArabic.orEmpty()
                draft.nameEnglish.text = match.nameEnglish.orEmpty()
                searching = false
            }
        }
        DraftTextField(draft.nameArabic, "Arabic name")
        DraftTextField(draft.nameEnglish, "English name")
    }
}

@Composable
private fun CatalogRow(stock: CatalogStock, onPick: () -> Unit) {
    Surface(
        onClick = onPick,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = Space.m, vertical = Space.s),
            horizontalArrangement = Arrangement.spacedBy(Space.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stock.ticker,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(TickerColumnWidth),
            )
            Column(Modifier.weight(1f)) {
                stock.nameArabic?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                stock.nameEnglish?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        color = PriceRole.muted,
                    )
                }
            }
        }
    }
}

@Composable
private fun LevelsSection(draft: CallDraft) {
    SheetSection("Levels") {
        DraftPair(
            { DraftNumberField(draft.entryLow, "Entry low", it) },
            { DraftNumberField(draft.entryHigh, "Entry high", it) },
        )
        DraftPair(
            {
                DraftNumberField(draft.target1, "Target 1", it, PriceRole.target) {
                    draft.returnOn(draft.target1)
                }
            },
            {
                DraftNumberField(draft.target2, "Target 2", it, PriceRole.target) {
                    draft.returnOn(draft.target2)
                }
            },
        )
        DraftPair(
            {
                DraftNumberField(draft.stopLoss, "Stop loss", it, PriceRole.stop) {
                    draft.returnOn(draft.stopLoss)
                }
            },
            { DraftNumberField(draft.support, "Support", it, PriceRole.market) },
        )
        DraftPair(
            { DraftNumberField(draft.resistance, "Resistance", it, PriceRole.market) },
            { Spacer(it) },
        )
    }
}

@Composable
private fun TimingSection(draft: CallDraft) {
    SheetSection("Timing") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            TIMING_BASES.forEach { (value, label) ->
                FilterChip(
                    selected = draft.basis == value,
                    onClick = { draft.basis = if (draft.basis == value) null else value },
                    label = { Text(label) },
                )
            }
        }
        DraftTextField(draft.visibleSourceDate, "Source date, as printed")
        // The occurrence's own target date. It usually decides nothing - a call is dated by the
        // session the run was aimed at, and this is only read where that is absent - which is
        // exactly why it is here rather than in the section above: it is a value of the card, not
        // a control over when the call is judged. The session a whole report is for is not editable
        // at all, because moving it moves every call in the report at once.
        DraftTextField(draft.date, "Target date (yyyy-mm-dd)")
    }
}

@Composable
private fun EvidenceSection(draft: CallDraft) {
    var open by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
        DisclosureButton(
            if (open) "Hide notes and evidence" else "Notes and evidence",
            expanded = open,
        ) { open = !open }
        AnimatedVisibility(open) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
                DraftTextField(draft.recommendationType, "Recommendation type")
                DraftTextField(draft.notesArabic, "Notes")
                DraftTextField(draft.notesSummary, "Stock notes summary")
                DraftTextField(draft.dateEvidence, "Date evidence")
                DraftTextField(draft.timingEvidence, "Timing evidence")
                DraftTextField(draft.recommendationEvidence, "Recommendation evidence")
                DraftPair(
                    { DraftTextField(draft.sourceMessageId, "Message id", it) },
                    { DraftNumberField(draft.sourceImageRef, "Image", it) },
                )
            }
        }
    }
}

/**
 * What the press is about to do, spelled out in the reader's own terms.
 *
 * Every line here is something that happens away from this sheet - on another tab, to a stored
 * answer, to a trade - and the reader has no other way of finding out about it beforehand.
 */
@Composable
private fun ConsequenceSection(
    stock: ConsolidatedRecommendation,
    draft: CallDraft,
    held: HeldTrade?,
    correctTrade: Boolean,
    onCorrectTrade: (Boolean) -> Unit,
) {
    val moved = draft.ticker.trim().uppercase().removeSuffix(".CA") != stock.stockCode
    if (!draft.hasChanges) return
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(Space.m),
            verticalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            SectionLabel("What saving changes")
            if (moved) {
                Consequence("${stock.stockCode} → ${draft.ticker.trim().uppercase()} everywhere this call appears")
            }
            Consequence("The call is scored again from scratch on Insights.")
            if (moved) {
                Consequence(
                    "The AI opinion on this call is deleted - it was about the other company.",
                    tone = PriceRole.stop,
                )
                Consequence("Prices for the corrected ticker are fetched.", tone = PriceRole.muted)
            }
            held?.let { trade ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onCorrectTrade(!correctTrade) }
                        .padding(top = Space.xs),
                    horizontalArrangement = Arrangement.spacedBy(Space.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = correctTrade, onCheckedChange = onCorrectTrade)
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Also correct the trade taken on it",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            trade.describe(),
                            style = MaterialTheme.typography.labelMedium,
                            color = PriceRole.muted,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Consequence(text: String, tone: Color? = null) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = tone ?: MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun Caution(text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Space.s),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            Icons.Outlined.Warning,
            contentDescription = null,
            tint = extraColors.expired,
            modifier = Modifier.size(IconSize.Inline),
        )
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = extraColors.expired,
        )
    }
}

@Composable
private fun SheetSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
        SectionLabel(title)
        content()
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = PriceRole.muted,
        fontWeight = FontWeight.Bold,
    )
}

/** Two slots of equal width, the shape the card's own level grid already reads in. */
@Composable
private fun DraftPair(
    left: @Composable (Modifier) -> Unit,
    right: @Composable (Modifier) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
        left(Modifier.weight(1f))
        right(Modifier.weight(1f))
    }
}

@Composable
private fun DraftTextField(field: DraftField, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        OutlinedTextField(
            value = field.text,
            onValueChange = { field.text = it },
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (field.changed) {
            WasLine(if (field.original.isBlank()) "was empty" else "was ${field.original}") {
                field.reset()
            }
        }
    }
}

@Composable
private fun DraftNumberField(
    field: DraftField,
    label: String,
    modifier: Modifier = Modifier,
    tone: Color? = null,
    percent: (() -> Double?)? = null,
) {
    Column(modifier) {
        OutlinedTextField(
            value = field.text,
            onValueChange = { field.text = it },
            label = { Text(label) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        // Recomputed as the reader types, through the scorer's own basis, so a figure and the
        // percentage beside it can never end up describing different levels.
        percent?.invoke()?.let {
            Text(
                signedPercent(it),
                style = MaterialTheme.typography.labelMedium,
                color = (tone ?: PriceRole.muted).copy(alpha = DERIVED_ALPHA),
                modifier = Modifier.padding(start = Space.m),
            )
        }
        if (field.changed) {
            WasLine(if (field.original.isBlank()) "was empty" else "was ${field.original}") {
                field.reset()
            }
        }
    }
}

/**
 * What the model read, under the field that has replaced it.
 *
 * The muted grey a derived figure is already drawn in, because it is the same kind of statement -
 * this number did not come from where the numbers beside it came from. Pressing it puts the field
 * back, which is what makes trying a correction free.
 */
@Composable
private fun WasLine(text: String, onReset: () -> Unit) {
    Row(
        Modifier
            .clickable(onClick = onReset)
            .padding(start = Space.m, top = Space.xs),
        horizontalArrangement = Arrangement.spacedBy(Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = PriceRole.muted)
        Text(
            "undo",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** The chip a corrected call carries, and the way back to what the model read. */
@Composable
internal fun EditedChip(onClick: () -> Unit) {
    // The same 20dp ring the timing pill beside it wears. It shared that slot as a 32dp AssistChip,
    // so a corrected call carried two chips a third taller than the button they sat against, and
    // stacked they were the tallest thing on the card. Tertiary rather than primary: the pill under
    // it may already be the app's own T+1, and two primary rings in one column read as one control
    // split in half.
    val tone = MaterialTheme.colorScheme.tertiary
    OutlinePill(
        "Edited",
        outline = tone,
        textColor = MaterialTheme.colorScheme.onTertiaryContainer,
        onClick = onClick,
    )
}

private fun signedPercent(value: Double): String =
    if (value >= 0) "+%.1f%%".format(value) else "%.1f%%".format(value)


/**
 * The three ways a card dates itself, in the words the app already uses for them.
 *
 * The stored value on the left and the label on the right, because the value is the model's
 * contract and the label is the reader's - `timing()` maps one onto the other everywhere else.
 */
private val TIMING_BASES = listOf(
    "watching" to "Watching",
    "t_plus_1" to "T+1",
    "explicit_date" to "Explicit date",
)

/** Enough of the catalog to choose from without the list becoming the sheet. */
private const val CATALOG_SUGGESTIONS = 6

private val TickerColumnWidth = 56.dp

/** A trade recorded on the call being corrected, named by what the reader actually did. */
internal data class HeldTrade(
    val ticker: String,
    val entryPrice: Double,
    val entryDate: LocalDate,
) {
    fun describe(): String = "bought ${formatPrice(entryPrice)} on ${shortDate(entryDate)}"
}


/**
 * What a card needs to correct the call it happens to be showing.
 *
 * One object rather than six lambdas, exactly as [TradeBook] is and for the same reason: a
 * recommendation card already takes a handful of lookups, and every screen that draws one would
 * otherwise thread the same save, catalog and trade calls through by hand - three chances to date a
 * correction differently from the way the report dates its calls.
 */
internal class CallEditor(
    private val appState: AppState,
    private val saved: SavedAnalysis,
) {
    /** The session a call belongs to, worked out exactly as the scorer works it out. */
    fun sessionOf(point: RecommendationDataPoint): LocalDate? =
        point.callDate(saved.result.recommendationTargetDate)

    fun catalog(): List<CatalogStock> = appState.stockCatalog()

    /**
     * The correction standing against this occurrence, if one is actually in force.
     *
     * The fingerprint is checked here as well as where the overlay is applied, and for the same
     * reason: an edit whose occurrence has changed underneath it is not applied, so a chip drawn
     * off the stored list alone would mark a card as corrected while showing the model's own
     * figures - which is the one claim this screen cannot afford to get wrong.
     */
    fun editFor(
        stock: ConsolidatedRecommendation,
        point: RecommendationDataPoint,
    ): RecommendationEdit? = saved.result.edits.firstOrNull {
        it.originalStockCode == stock.originalStockCode &&
            it.pointIndex == point.parseIndex &&
            it.fingerprint == point.editFingerprint()
    }

    /** Whether anything in this report has been corrected, which is what the undo is offered on. */
    val hasEdits: Boolean get() = saved.result.edits.isNotEmpty()

    /** The trade recorded on this call, so the sheet can name what it is offering to move. */
    fun tradeOn(
        stock: ConsolidatedRecommendation,
        point: RecommendationDataPoint,
    ): HeldTrade? = appState.heldFor(stock.stockCode, sessionOf(point))?.let {
        HeldTrade(it.position.ticker, it.position.entryPrice, it.position.entryDate)
    }

    /**
     * The one consequence the app will not act on for the reader, said out loud instead.
     *
     * `PerformanceCalculator` keeps one call per stock per channel and drops the rest, so
     * correcting a code to one already in this report from the same chat quietly costs a call on
     * the Insights tab. It is not prevented, because both readings may genuinely be right and
     * merging them would invent a call neither channel made - but it is never allowed to happen
     * without being named first.
     */
    fun clashFor(stock: ConsolidatedRecommendation, typed: String): String? {
        val wanted = typed.trim().uppercase().removeSuffix(".CA")
        if (wanted.isBlank() || wanted == stock.stockCode) return null
        val mine = channelsOf(stock)
        val shared = saved.result.consolidated
            .filter { it.stockCode == wanted && it.originalStockCode != stock.originalStockCode }
            .any { channelsOf(it).any { channel -> channel in mine } }
        if (!shared) return null
        return "$wanted is already in this report from the same channel. " +
            "Insights keeps one call per stock per channel, so one of the two stops being scored."
    }

    fun save(
        stock: ConsolidatedRecommendation,
        point: RecommendationDataPoint,
        edit: RecommendationEdit,
        correctTrade: Boolean,
    ) = appState.editRecommendation(saved, edit, correctTrade)

    fun undoAll() = appState.clearRecommendationEdits(saved)

    private fun channelsOf(stock: ConsolidatedRecommendation): Set<String> =
        stock.dataPoints.mapNotNull { point ->
            saved.result.sources
                .firstOrNull { it.messageId?.toString() == point.sourceMessageId }
                ?.channelName
        }.toSet()
}
