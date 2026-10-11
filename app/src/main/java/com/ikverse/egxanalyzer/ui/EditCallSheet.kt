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
import androidx.compose.material.icons.outlined.Undo
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
import com.ikverse.egxanalyzer.model.CallSlot
import com.ikverse.egxanalyzer.model.modelSlot
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
 *
 * [stock] and [point] are the call as it reads now, corrections and all. The draft is built from
 * what the model read with this call's standing correction laid over it - see [CallDraft] - so a
 * second correction restates the first rather than replacing it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditCallSheet(
    stock: ConsolidatedRecommendation,
    point: RecommendationDataPoint,
    editor: CallEditor,
    onDismiss: () -> Unit,
) {
    val catalog = remember { editor.catalog() }
    val draft = rememberCallDraft(stock, point, editor, catalog)
    val held = remember(stock, point) { editor.tradeOn(stock, point) }
    val clash = remember(draft.normalizedTicker, stock, point) {
        editor.clashFor(stock, point, draft.normalizedTicker)
    }
    // Ticked only when the trade looks like it was taken on this call. A position is one stock on
    // one session, so a trade bought on another channel's call for the stock the model named turns
    // up here too - and moving that one would be moving a trade the correction is not about.
    var correctTrade by remember(held) {
        mutableStateOf(held != null && held.takenFrom(editor.channelOf(point)))
    }

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
                            draft.anchor,
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
                    point = point,
                    draft = draft,
                    editor = editor,
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
                // The one thing the card's own ⋮ menu used to offer that this sheet didn't: with
                // that menu gone from the card, undoing a correction is reached from here instead,
                // where the correction itself is made. Absent when there is nothing on this call to
                // undo - guarded on this call's own edit, not the report's, so fixing one call does
                // not offer to throw away a correction made on a different one.
                if (editor.editFor(stock, point) != null) {
                    TextButton(
                        onClick = {
                            editor.undo(stock, point)
                            onDismiss()
                        },
                    ) {
                        Icon(
                            Icons.Outlined.Undo,
                            contentDescription = null,
                            modifier = Modifier.size(IconSize.Inline),
                        )
                        Spacer(Modifier.width(Space.xs))
                        Text("Undo this call's edits")
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
                ActionPill(
                    label = "Save",
                    icon = Icons.Outlined.Check,
                    // Refused while a field holds something that cannot be read. Saved anyway, the
                    // field would simply be left out - and if it was the only change, the edit
                    // would come out empty, which is how a correction is removed.
                    enabled = draft.hasChanges && draft.isValid,
                    onClick = {
                        editor.save(draft.toEdit(), correctTrade)
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
 *
 * **Built from what the model read, with the standing correction laid over it** - [model] for the
 * "was", [current] for what each box opens holding. An edit is the whole correction of its call
 * and saving replaces it, so a draft that measured itself against the *corrected* figures produced
 * an edit that left out every earlier correction it had not been asked to repeat, and carried a
 * fingerprint of the corrected point that never matched the model's. Either way the earlier
 * correction was lost on the second save, with "updated" on the status line.
 *
 * @param standing the correction already on file for this call, for the parts of it that are not
 * measured against the model: names the reader typed, and the stock's notes.
 */
internal class CallDraft(
    model: CallSlot,
    current: CallSlot,
    private val standing: RecommendationEdit?,
    private val catalog: List<CatalogStock>,
) {
    /** The code the model filed this call under, which is what its edit is filed under. */
    val anchor: String = model.stock.anchorOf(model.point)
    private val pointIndex = model.point.parseIndex
    private val fingerprint = model.point.editFingerprint()
    private val modelStock = model.stock

    val fields = linkedMapOf<EditField, DraftField>()

    /** What the model read the ticker as, which is what "was" means under the box. */
    val originalTicker: String = model.stock.stockCode
    var ticker by mutableStateOf(current.stock.stockCode)

    /** The ticker as it will be stored, so a change of case or a `.CA` is not a correction. */
    val normalizedTicker: String get() = storedTicker(ticker)

    /**
     * A name the reader typed, or null to follow the catalog under whatever the ticker now reads.
     *
     * Kept apart from the boxes the rest of the draft uses because a name has no fixed original to
     * be measured against: what it should say moves with the ticker. Empty means emptied on purpose.
     */
    var typedEnglish by mutableStateOf(
        standing.typedName(EditField.STOCK_NAME_ENGLISH) { it.stockNameEnglish },
    )
    var typedArabic by mutableStateOf(
        standing.typedName(EditField.STOCK_NAME_ARABIC) { it.stockNameArabic },
    )

    val shownEnglish: String get() = typedEnglish ?: catalogNames().first
    val shownArabic: String get() = typedArabic ?: catalogNames().second

    /** What the names would be with nothing typed: the model's reading, or the catalog's. */
    fun catalogNames(): Pair<String, String> {
        val code = normalizedTicker
        if (code == originalTicker) {
            return modelStock.stockNameEnglish.orEmpty() to modelStock.stockNameArabic.orEmpty()
        }
        val listed = catalog.firstOrNull { it.ticker.equals(code, ignoreCase = true) }
        return listed?.nameEnglish.orEmpty() to listed?.nameArabic.orEmpty()
    }

    /** Typing the catalog's own name back is the same as not having typed one. */
    fun typeEnglish(value: String) {
        typedEnglish = value.takeIf { it.trim() != catalogNames().first.trim() }
    }

    fun typeArabic(value: String) {
        typedArabic = value.takeIf { it.trim() != catalogNames().second.trim() }
    }

    private val was = model.point
    private val now = current.point
    val entryLow = number(
        EditField.BUY_PRICE_LOW, was.buyPriceLow ?: was.buyPrice, now.buyPriceLow ?: now.buyPrice,
    )
    val entryHigh = number(
        EditField.BUY_PRICE_HIGH, was.buyPriceHigh ?: was.buyPrice, now.buyPriceHigh ?: now.buyPrice,
    )
    val target1 = number(EditField.TARGET_1, was.target1, now.target1)
    val target2 = number(EditField.TARGET_2, was.target2, now.target2)
    val stopLoss = number(EditField.STOP_LOSS, was.stopLoss, now.stopLoss)
    val support = number(EditField.SUPPORT, was.support, now.support)
    val resistance = number(EditField.RESISTANCE, was.resistance, now.resistance)
    val visibleSourceDate =
        field(EditField.VISIBLE_SOURCE_DATE, was.visibleSourceDate, now.visibleSourceDate)
    val date = field(EditField.DATE, was.date?.toString(), now.date?.toString()) {
        typedDate(it) != null
    }
    val recommendationType =
        field(EditField.RECOMMENDATION_TYPE, was.recommendationType, now.recommendationType)
    val notesArabic = field(EditField.NOTES_ARABIC, was.notesArabic, now.notesArabic)
    val dateEvidence = field(EditField.DATE_EVIDENCE, was.dateEvidence, now.dateEvidence)
    val timingEvidence = field(EditField.TIMING_EVIDENCE, was.timingEvidence, now.timingEvidence)
    val recommendationEvidence = field(
        EditField.RECOMMENDATION_EVIDENCE, was.recommendationEvidence, now.recommendationEvidence,
    )
    val sourceMessageId =
        field(EditField.SOURCE_MESSAGE_ID, was.sourceMessageId, now.sourceMessageId)
    val sourceImageRef = field(
        EditField.SOURCE_IMAGE_REF, was.sourceImageRef?.toString(), now.sourceImageRef?.toString(),
    ) { typedWhole(it) != null }

    /**
     * The stock's notes, which belong to whichever entry the call sits in rather than to the call.
     *
     * Measured against what the entry says now rather than against the model, and carried over
     * from the standing correction when left alone: the model's notes were about the stock it read,
     * which after a moved ticker is not the one this box describes.
     */
    val notesSummary = DraftField(current.stock.notesSummary.orEmpty())

    val originalBasis = was.effectiveDateBasis
    var basis by mutableStateOf(now.effectiveDateBasis)

    private fun field(
        which: EditField,
        original: String?,
        initial: String?,
        valid: (String) -> Boolean = { true },
    ): DraftField =
        DraftField(original.orEmpty(), initial.orEmpty(), valid).also { fields[which] = it }

    private fun number(which: EditField, original: Double?, initial: Double?): DraftField =
        field(which, price(original), price(initial)) { typedDecimal(it) != null }

    /**
     * Whether saving would change anything, measured against what is on file rather than against
     * the model - a call corrected yesterday and opened again today has nothing new to save.
     */
    val hasChanges: Boolean get() = toEdit() != opening

    /** Whether every box holds something that can be read. See the Save button. */
    val isValid: Boolean get() = normalizedTicker.isNotBlank() && fields.values.all { it.isValid }

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
     * The correction this draft amounts to, measured against what the model read.
     *
     * A field the reader emptied becomes a clearing rather than a null, because the two mean
     * different things - see `RecommendationEdit.cleared`. A field that reads as the model left it
     * contributes neither, which is what keeps an edit small and what makes putting one field back
     * the same operation as never having changed it. A field corrected on an earlier save still
     * differs from the model, so it is restated here without the reader having to repeat it.
     */
    fun toEdit(): RecommendationEdit {
        val cleared = fields.filterValues { it.changed && it.text.isBlank() }.keys.toMutableSet()
        if (EditField.BUY_PRICE_LOW in cleared && EditField.BUY_PRICE_HIGH in cleared) {
            // The band reads `buyPriceLow ?: buyPrice`, so a single price the model returned would
            // survive both halves being emptied and the deletion would look as if it had failed.
            cleared += EditField.BUY_PRICE
        }
        // Unticking the timing chip is a change like any other. With nowhere to record it, it was
        // the one way to make Save produce an empty edit - which removes the correction on file.
        if (basis == null && originalBasis != null) cleared += EditField.EFFECTIVE_DATE_BASIS
        if (typedEnglish?.isBlank() == true) cleared += EditField.STOCK_NAME_ENGLISH
        if (typedArabic?.isBlank() == true) cleared += EditField.STOCK_NAME_ARABIC
        val notes = if (notesSummary.changed) {
            if (notesSummary.text.isBlank()) cleared += EditField.NOTES_SUMMARY
            notesSummary.text.trim().takeIf(String::isNotBlank)
        } else {
            if (standing?.cleared?.contains(EditField.NOTES_SUMMARY) == true) {
                cleared += EditField.NOTES_SUMMARY
            }
            standing?.notesSummary
        }

        fun text(which: EditField): String? =
            fields[which]?.takeIf { it.changed && it.text.isNotBlank() }?.text?.trim()

        fun number(which: EditField): Double? = text(which)?.let(::typedDecimal)

        return RecommendationEdit(
            originalStockCode = anchor,
            pointIndex = pointIndex,
            fingerprint = fingerprint,
            stockCode = normalizedTicker.takeIf { it.isNotBlank() && it != originalTicker },
            stockNameEnglish = typedEnglish?.trim()?.takeIf(String::isNotBlank),
            stockNameArabic = typedArabic?.trim()?.takeIf(String::isNotBlank),
            notesSummary = notes,
            date = text(EditField.DATE)?.let(::typedDate),
            recommendationType = text(EditField.RECOMMENDATION_TYPE),
            effectiveDateBasis = basis?.takeIf { it != originalBasis },
            visibleSourceDate = text(EditField.VISIBLE_SOURCE_DATE),
            dateEvidence = text(EditField.DATE_EVIDENCE),
            timingEvidence = text(EditField.TIMING_EVIDENCE),
            sourceMessageId = text(EditField.SOURCE_MESSAGE_ID),
            sourceImageRef = text(EditField.SOURCE_IMAGE_REF)?.let(::typedWhole),
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

    /** The edit this draft opened as, which [hasChanges] is measured against. Declared last. */
    private val opening: RecommendationEdit = toEdit()
}

/** A stock-level name the standing correction typed or emptied, or null when it did neither. */
private fun RecommendationEdit?.typedName(
    field: EditField,
    value: (RecommendationEdit) -> String?,
): String? = when {
    this == null -> null
    field in cleared -> ""
    else -> value(this)
}

/** One field of the draft: what it says now, and what the model read. */
internal class DraftField(
    val original: String,
    initial: String = original,
    private val valid: (String) -> Boolean = { true },
) {
    var text by mutableStateOf(initial)
    val changed: Boolean get() = text.trim() != original.trim()
    val number: Double? get() = typedDecimal(text)

    /** Empty is always readable - it means the field was emptied. */
    val isValid: Boolean get() = text.isBlank() || valid(text.trim())

    fun reset() {
        text = original
    }
}

/** A ticker as it is stored: upper case, without the `.CA` the feeds put on it. */
internal fun storedTicker(typed: String): String = typed.trim().uppercase().removeSuffix(".CA")

/**
 * Arabic-Indic digits as the ASCII ones, so a phone set to Arabic can type a price.
 *
 * Both sets: the Arabic one and the extended one Persian keyboards produce, which look the same
 * from four of the ten digits and are not the same characters.
 */
internal fun asciiDigits(text: String): String = buildString {
    text.trim().forEach { c ->
        append(
            when (c) {
                in '٠'..'٩' -> '0' + (c - '٠')
                in '۰'..'۹' -> '0' + (c - '۰')
                else -> c
            },
        )
    }
}

/**
 * A price as typed, or null when it cannot be read as one.
 *
 * A single comma or Arabic decimal separator with no point beside it is the decimal point: that
 * is what a keyboard set to Arabic, or to most of Europe, puts there. It used to be read as nothing
 * at all, and the field quietly dropped out of the correction. Two of them, or one beside a point,
 * is a thousands separator or a typo, and is refused rather than guessed at.
 */
internal fun typedDecimal(text: String): Double? {
    val digits = asciiDigits(text)
    val separators = digits.count { it == ',' || it == '٫' }
    val plain = if (separators == 1 && '.' !in digits) {
        digits.replace(',', '.').replace('٫', '.')
    } else {
        digits
    }
    return plain.toDoubleOrNull()?.takeIf(Double::isFinite)
}

internal fun typedWhole(text: String): Int? = asciiDigits(text).toIntOrNull()

internal fun typedDate(text: String): LocalDate? =
    runCatching { LocalDate.parse(asciiDigits(text)) }.getOrNull()

@Composable
private fun rememberCallDraft(
    stock: ConsolidatedRecommendation,
    point: RecommendationDataPoint,
    editor: CallEditor,
    catalog: List<CatalogStock>,
): CallDraft = remember(stock.anchorOf(point), point.parseIndex) {
    CallDraft(
        model = editor.modelSlotFor(stock, point),
        current = CallSlot(stock, point),
        standing = editor.editFor(stock, point),
        catalog = catalog,
    )
}

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
    val known = remember(draft.normalizedTicker, catalog) {
        catalog.any { it.ticker.equals(draft.normalizedTicker, ignoreCase = true) }
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
        if (draft.normalizedTicker != draft.originalTicker) {
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
                // The names follow the pick rather than being left for the reader, which is the
                // whole point of picking from the catalog rather than typing - so anything typed
                // over them before is let go. They stay editable underneath.
                draft.typedArabic = null
                draft.typedEnglish = null
                searching = false
            }
        }
        NameField(
            label = "Arabic name",
            shown = draft.shownArabic,
            typed = draft.typedArabic != null,
            automatic = draft.catalogNames().second,
            onType = draft::typeArabic,
            onReset = { draft.typedArabic = null },
        )
        NameField(
            label = "English name",
            shown = draft.shownEnglish,
            typed = draft.typedEnglish != null,
            automatic = draft.catalogNames().first,
            onType = draft::typeEnglish,
            onReset = { draft.typedEnglish = null },
        )
    }
}

/**
 * A company name, which follows the ticker until the reader types over it.
 *
 * Typed, it stands: the reader typed it because the catalog had the wrong name or none. The line
 * under it says what the catalog would have said, and puts it back.
 */
@Composable
private fun NameField(
    label: String,
    shown: String,
    typed: Boolean,
    automatic: String,
    onType: (String) -> Unit,
    onReset: () -> Unit,
) {
    Column {
        OutlinedTextField(
            value = shown,
            onValueChange = onType,
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (typed) {
            WasLine(if (automatic.isBlank()) "catalog has none" else "catalog: $automatic", onReset)
        }
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
        DraftTextField(draft.date, "Target date (yyyy-mm-dd)", unreadable = "Not a yyyy-mm-dd date")
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
        AnimatedVisibility(open, enter = ExpandIn, exit = CollapseOut) {
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
    point: RecommendationDataPoint,
    draft: CallDraft,
    editor: CallEditor,
    held: HeldTrade?,
    correctTrade: Boolean,
    onCorrectTrade: (Boolean) -> Unit,
) {
    val target = draft.normalizedTicker
    val moved = target != stock.stockCode
    // A different message is a different channel, which is part of what an opinion is filed under.
    val rehomed = draft.sourceMessageId.text.trim() != point.sourceMessageId.orEmpty()
    if (!draft.hasChanges) return
    val session = editor.sessionOf(point)
    // Counted over the whole report: the stock handed in may be a card's filtered copy.
    val staying = editor.callsUnder(stock.stockCode) - 1
    val joining = editor.callsUnder(target)
    val blocked = moved && held != null && editor.holds(target, session)
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
                Consequence("This call moves from ${stock.stockCode} to $target.")
                if (joining > 0) {
                    Consequence(
                        "It joins the ${calls(joining)} already under $target in this report.",
                    )
                }
                if (staying > 0) {
                    Consequence(
                        "The other ${calls(staying)} under ${stock.stockCode} stay where they are.",
                        tone = PriceRole.muted,
                    )
                }
            }
            Consequence("The call is scored again from scratch on Insights.")
            if (moved || rehomed) {
                val why = if (moved) {
                    "it was about the other company."
                } else {
                    "it was filed under the other channel."
                }
                Consequence("The AI opinion on this call is deleted - $why", tone = PriceRole.stop)
            }
            if (moved) {
                Consequence("Prices for the corrected ticker are fetched.", tone = PriceRole.muted)
            }
            // Said before the press because the app will not do it: a position is one stock on one
            // session, and moving this trade there would write over the one already held.
            if (blocked) {
                Caution(
                    "You already hold $target for this session, so the trade on this call stays " +
                        "on ${stock.stockCode}.",
                )
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
private fun DraftTextField(
    field: DraftField,
    label: String,
    modifier: Modifier = Modifier,
    unreadable: String = "Can't be read",
) {
    Column(modifier) {
        OutlinedTextField(
            value = field.text,
            onValueChange = { field.text = it },
            label = { Text(label) },
            singleLine = true,
            isError = !field.isValid,
            supportingText = if (field.isValid) null else { { Text(unreadable) } },
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
            isError = !field.isValid,
            supportingText = if (field.isValid) null else { { Text("Not a number") } },
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
    /** The channel whose call the trade was recorded on. Null on trades older than the field. */
    val channel: String? = null,
) {
    fun describe(): String = "bought ${formatPrice(entryPrice)} on ${shortDate(entryDate)}" +
        (channel?.let { " · from $it" } ?: "")

    /**
     * Whether this looks like the trade taken on a call from [callChannel].
     *
     * A position is one stock on one session, with no channel in its identity, so a trade bought on
     * a different channel's call for the stock the model named is found here too. True when either
     * side cannot say, which is how it was before the channel was recorded.
     */
    fun takenFrom(callChannel: String?): Boolean =
        channel == null || callChannel == null || channel.trim().equals(callChannel.trim(), true)
}

private fun calls(count: Int): String = if (count == 1) "1 call" else "$count calls"


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
     * Matched by identity alone. The fingerprint that decides whether an edit still fits is
     * already checked once, in [RecommendationEdits.apply] against the *raw* parse, before a
     * fitting edit's values are folded into [stock]/[point] at all - so by the time this is
     * called, `point` already carries the corrected figures, and re-checking its fingerprint here
     * would be comparing it against the value it was corrected away from. That always disagrees,
     * which is what silently hid the Edited chip and the per-call undo on every real correction.
     */
    fun editFor(
        stock: ConsolidatedRecommendation,
        point: RecommendationDataPoint,
    ): RecommendationEdit? = saved.result.edits.firstOrNull {
        it.originalStockCode == stock.anchorOf(point) && it.pointIndex == point.parseIndex
    }

    /**
     * This occurrence as the model read it, which every correction is measured against.
     *
     * Falls back on the occurrence as it reads now only when the report holds no reading of its
     * own - a run not yet read back from the store, which cannot have been corrected.
     */
    fun modelSlotFor(stock: ConsolidatedRecommendation, point: RecommendationDataPoint): CallSlot =
        saved.result.modelSlot(stock.anchorOf(point), point.parseIndex) ?: CallSlot(stock, point)

    /** Whether anything in this report has been corrected, which is what the undo is offered on. */
    val hasEdits: Boolean get() = saved.result.edits.isNotEmpty()

    /** The trade recorded on this call, so the sheet can name what it is offering to move. */
    fun tradeOn(
        stock: ConsolidatedRecommendation,
        point: RecommendationDataPoint,
    ): HeldTrade? = appState.heldFor(stock.stockCode, sessionOf(point))?.let {
        HeldTrade(
            it.position.ticker,
            it.position.entryPrice,
            it.position.entryDate,
            it.position.channel,
        )
    }

    /** Whether a trade is already filed under [ticker] for [session]. */
    fun holds(ticker: String, session: LocalDate?): Boolean =
        appState.heldFor(ticker, session) != null

    /** The channel this occurrence was read from, by the message the model cited. */
    fun channelOf(point: RecommendationDataPoint): String? = saved.result.sources
        .firstOrNull { it.messageId?.toString() == point.sourceMessageId }
        ?.channelName

    /** How many occurrences in the whole report sit under [code]. */
    fun callsUnder(code: String): Int = saved.result.consolidated
        .filter { it.stockCode == code }
        .sumOf { it.dataPoints.size }

    /**
     * The one consequence the app will not act on for the reader, said out loud instead.
     *
     * `PerformanceCalculator` keeps one call per stock per channel and drops the rest, so
     * correcting a code to one already in this report from the same chat quietly costs a call on
     * the Insights tab. It is not prevented, because both readings may genuinely be right and
     * merging them would invent a call neither channel made - but it is never allowed to happen
     * without being named first. Only this occurrence's channel counts, because only this
     * occurrence moves.
     */
    fun clashFor(
        stock: ConsolidatedRecommendation,
        point: RecommendationDataPoint,
        wanted: String,
    ): String? {
        if (wanted.isBlank() || wanted == stock.stockCode) return null
        val mine = channelOf(point) ?: return null
        val shared = saved.result.consolidated
            .filter { it.stockCode == wanted }
            .any { other -> other.dataPoints.any { channelOf(it) == mine } }
        if (!shared) return null
        return "$wanted is already in this report from the same channel. " +
            "Insights keeps one call per stock per channel, so one of the two stops being scored."
    }

    fun save(edit: RecommendationEdit, correctTrade: Boolean) =
        appState.editRecommendation(saved, edit, correctTrade)

    /** Puts this one call back to what the model read, wherever its own undo is reached from. */
    fun undo(stock: ConsolidatedRecommendation, point: RecommendationDataPoint) =
        appState.undoRecommendationEdit(saved, stock.anchorOf(point), point.parseIndex)

    /** Puts every call in the report back at once. Offered only from the report's own menu. */
    fun undoAll() = appState.clearRecommendationEdits(saved)
}
