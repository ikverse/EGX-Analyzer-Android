package com.ikverse.egxanalyzer.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.LocalContentColor
import com.ikverse.egxanalyzer.ui.theme.palette
import com.ikverse.egxanalyzer.ui.kit.LocalFoldPosture
import com.ikverse.egxanalyzer.ui.kit.TileTone
import com.ikverse.egxanalyzer.ui.kit.tileSignals
import com.ikverse.egxanalyzer.model.plainWord
import com.ikverse.egxanalyzer.ui.kit.Tile
import com.ikverse.egxanalyzer.ui.kit.SegmentedControl
import com.ikverse.egxanalyzer.ui.kit.KitStepper
import com.ikverse.egxanalyzer.ui.kit.KitTextField
import com.ikverse.egxanalyzer.ui.kit.KitSheet
import com.ikverse.egxanalyzer.ui.kit.KitDialog
import com.ikverse.egxanalyzer.ui.kit.ButtonSize
import com.ikverse.egxanalyzer.ui.kit.ButtonKind
import com.ikverse.egxanalyzer.ui.kit.KitButton
import com.ikverse.egxanalyzer.ui.kit.Kit
import com.ikverse.egxanalyzer.ui.kit.KitTag
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddShoppingCart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.ikverse.egxanalyzer.model.ConsolidatedRecommendation
import com.ikverse.egxanalyzer.model.Outcome
import com.ikverse.egxanalyzer.model.PositionStatus
import com.ikverse.egxanalyzer.model.PositionView
import com.ikverse.egxanalyzer.model.FULL_SPLIT_PCT
import com.ikverse.egxanalyzer.model.RecommendationDataPoint
import com.ikverse.egxanalyzer.model.Sale
import com.ikverse.egxanalyzer.model.Scoring
import com.ikverse.egxanalyzer.model.callDate
import com.ikverse.egxanalyzer.model.offeredTradeWindow
import com.ikverse.egxanalyzer.ui.theme.extraColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * What a card needs to record a trade against the call it happens to be showing.
 *
 * Passed as one object rather than as six lambdas: a recommendation card already takes a handful of
 * lookups, and every screen that shows one would otherwise have to thread the same purchase, sale
 * and holding calls through by hand - three chances to date a trade differently from the way the
 * scorer dates its call.
 */
internal class TradeBook(
    private val appState: AppState,
    /** The session the run was for, which dates every call inside it. */
    private val targetDate: java.time.LocalDate?,
) {
    /** The session a call belongs to, worked out exactly as the scorer works it out. */
    fun dateOf(point: RecommendationDataPoint): LocalDate? = point.callDate(targetDate)

    fun heldFor(stock: ConsolidatedRecommendation, point: RecommendationDataPoint): PositionView? =
        appState.heldFor(stock.stockCode, dateOf(point))

    /**
     * What a new trade's window is offered as, which becomes this trade's own deadline.
     *
     * The setting for an ordinary call; a T+1 call names its own and the setting does not apply to
     * it. Read at the moment the dialog opens rather than captured earlier: a user who has just
     * changed the setting means the new one. This is the only window anybody sets - what the
     * channel that made the call is judged over is fixed, and is no business of this trade's.
     */
    fun windowFor(point: RecommendationDataPoint): Int =
        point.offeredTradeWindow(appState.appPreferences.defaultTradeWindowSessions).sessions

    fun buy(
        stock: ConsolidatedRecommendation,
        point: RecommendationDataPoint,
        channel: String?,
        price: Double,
        date: LocalDate,
        windowSessions: Int,
    ) {
        // A call with no date at all cannot be scored and so cannot be held; the button is not
        // offered for one, and this is the belt to that brace.
        val on = dateOf(point) ?: return
        appState.recordPurchase(
            ticker = stock.stockCode,
            companyEnglish = stock.stockNameEnglish,
            companyArabic = stock.stockNameArabic,
            channel = channel,
            recommendationDate = on,
            entryPrice = price,
            entryDate = date,
            entryLow = point.buyPriceLow ?: point.buyPrice,
            entryHigh = point.buyPriceHigh ?: point.buyPrice,
            target1 = point.target1,
            target2 = point.target2,
            stopLoss = point.stopLoss,
            windowSessions = windowSessions,
            // What the dialog showed them, so accepting a T+1 call's two sessions is not recorded
            // as a deadline they set by hand.
            offeredWindow = windowFor(point),
            // Copied off the card while the card is still in hand. The report behind it can be
            // deleted or re-run, and neither may take back what this trade was taken on.
            isTPlusOne = point.isTPlusOne,
        )
    }

    fun sell(view: PositionView, sale: Sale) = appState.recordSale(view.position, sale)
}

/**
 * The middle of the buy band, which is what a fill is usually nearest.
 *
 * The same basis the scorer measures a call's return from, so the price the dialog offers is the
 * one the recommendation was judged on - the user overwrites it with what they actually paid.
 */
internal fun RecommendationDataPoint.entryMidpoint(): Double? {
    val low = buyPriceLow ?: buyPrice
    val high = buyPriceHigh ?: buyPrice
    return when {
        low != null && high != null -> (low + high) / 2
        else -> low ?: high
    }
}

/**
 * Recording a trade, wherever a stock is shown.
 *
 * One control rather than one per screen: the card in a report, the sheet behind a table row and the
 * Portfolio itself all ask the same two questions - what did you pay, and what did you sell for -
 * and an app that asked them three slightly different ways would be storing three slightly
 * different answers.
 */
@Composable
internal fun TradeAction(
    /** The position already recorded for this call, if there is one. */
    held: PositionView?,
    /** Prefills the entry dialog: the call's own entry, which is what most fills are near. */
    suggestedEntry: Double?,
    /** Offered as the new trade's window, and overwritable in the dialog. */
    defaultWindow: Int,
    /**
     * This call carries its own deadline rather than taking the scoring setting.
     *
     * Only changes what the dialog says about the number it offers. The field stays editable: a
     * T+1 call the user means to hold longer is theirs to hold, and a window they cannot argue
     * with would be the app overruling them about their own trade.
     */
    tPlusOne: Boolean = false,
    /** Prefills the sale dialog with the latest close, which is the likeliest sale price. */
    suggestedExit: Double? = null,
    /**
     * Whether this surface may close a position as well as open one.
     *
     * False on the Results call card, which is read to decide what to buy rather than to manage
     * what is held: the chips still say where a trade stands, but the way out of it is on the tab
     * the trade lives on. Everywhere a single position is the subject leaves this alone.
     */
    canSell: Boolean = true,
    onBuy: (price: Double, date: LocalDate, windowSessions: Int) -> Unit,
    onSell: (Sale) -> Unit,
    modifier: Modifier = Modifier,
) {
    var buying by remember { mutableStateOf(false) }

    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (held == null) {
            KitButton("I bought it", onClick = { buying = true }, size = ButtonSize.SMALL, icon = Icons.Outlined.AddShoppingCart)
        } else {
            PositionStatusChip(held)
            // The same warning the Portfolio card carries, on the card the trade was taken from:
            // a deadline that has passed with nothing recorded is worth seeing wherever the stock
            // is being looked at, not only on the tab the user has to remember to open.
            if (held.overdue) OverdueChip(held.overdueDays)
            if (held.priceScaleChanged) PriceScaleChip()
            if (canSell && held.awaitingSale) {
                // The estimate before the live price: they are the same while the trade is open,
                // and once the deadline has closed it the estimate is the better guess at what the
                // user actually got out at.
                SellButton(held, suggestedExit ?: held.exitPrice ?: held.currentPrice, onSell)
            }
        }
    }

    if (buying) {
        PurchaseDialog(
            suggestedEntry = suggestedEntry,
            defaultWindow = defaultWindow,
            tPlusOne = tPlusOne,
            onDismiss = { buying = false },
            onBuy = { price, date, window ->
                buying = false
                onBuy(price, date, window)
            },
        )
    }
}

/**
 * Recording a purchase: the price paid, the day, and the trade's own window. [TradeAction]'s button
 * and the Results hold menu's I bought this both open it, so the two say the same thing.
 */
@Composable
internal fun PurchaseDialog(
    suggestedEntry: Double?,
    defaultWindow: Int,
    tPlusOne: Boolean,
    onDismiss: () -> Unit,
    onBuy: (price: Double, date: LocalDate, windowSessions: Int) -> Unit,
) {
        TradeDialog(
            title = "Record the purchase",
            explanation = "The price you actually paid, which is what every figure for this " +
                "position is measured from. The deadline still runs from the session the call was " +
                "made for - buying late does not buy extra time - but how many sessions it runs " +
                "for is yours to set.",
            priceLabel = "Entry price",
            dateLabel = "Entry date",
            confirmLabel = "Save",
            initialPrice = suggestedEntry,
            initialWindow = defaultWindow,
            windowHelp = if (tPlusOne) {
                "This call is T+1: the session it was made for, and the next one. Change it to " +
                    "give this trade longer."
            } else {
                "Your default, from Settings. Change it to give this trade its own deadline - " +
                    "it decides when this trade expires and nothing else."
            },
            onDismiss = onDismiss,
            onConfirm = { price, date, window -> onBuy(price, date, window ?: defaultWindow) },
        )
}

/**
 * How far past its deadline a trade has run with nothing recorded about how it ended.
 *
 * The loss colour rather than a neutral one, and next to the status rather than buried in the
 * figures: it is the one thing on a card that is asking the user to do something. Tappable since
 * 2026-09-24, like every other note pill on this card.
 */
@Composable
internal fun OverdueChip(days: Long) {
    var showing by remember { mutableStateOf(false) }
    OutlinePill(
        "Overdue $days ${days.dayWord()}",
        outline = extraColors.loss,
        textColor = extraColors.onLossContainer,
        onClick = { showing = true },
    )
    if (showing) {
        InfoSheet(
            infoNote(
                "Overdue",
                "This trade's deadline passed $days ${days.dayWord()} ago with nothing recorded " +
                    "about how it ended, because it was kept open past it rather than closed " +
                    "there. Record the sale once you know what it was sold for, or hand it back " +
                    "to its deadline from the card's own menu.",
            ),
        ) { showing = false }
    }
}

/**
 * Says that the stock split under this trade, so nothing here is being valued.
 *
 * Neutral rather than a warning colour, and deliberately the same neutral the unjudged outcomes wear
 * in Insights: nothing has gone wrong with the trade, and nothing about it is known either. The
 * wording is [Outcome.PRICE_BREAK]'s own label rather than a second phrasing of it, so a card and the
 * report it came from can never end up describing this two different ways. Tappable since
 * 2026-09-24, like every other note pill on this card.
 */
@Composable
internal fun PriceScaleChip() {
    var showing by remember { mutableStateOf(false) }
    OutlinePill(
        Outcome.PRICE_BREAK.label,
        outline = MaterialTheme.colorScheme.outline,
        textColor = MaterialTheme.colorScheme.onSurfaceVariant,
        onClick = { showing = true },
    )
    if (showing) {
        InfoSheet(
            infoNote(
                Outcome.PRICE_BREAK.label,
                "The share price changed scale while this trade was running - a split or a bonus " +
                    "issue - so the levels it was taken on and the prices since are in different " +
                    "money. Nothing here is valued against them until that is sorted out, and " +
                    "nothing about the trade itself is in question.",
            ),
        ) { showing = false }
    }
}

/**
 * Lets a trade outlive its deadline.
 *
 * One direction only, and offered only to a trade not already carrying it. A card that is being kept
 * open says so in a pill, and a button repeating the pill was taking the place beside Sold - the one
 * action that actually ends a position. Undoing it moved to the card's own menu, where it stays
 * reachable without competing for that space.
 */
@Composable
internal fun KeepOpenButton(
    onKeepOpen: (keep: Boolean, note: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var asking by remember { mutableStateOf(false) }
    KitButton(
        "Keep it open",
        onClick = { asking = true },
        modifier = modifier,
        kind = ButtonKind.SECONDARY,
        size = ButtonSize.SMALL,
    )

    if (asking) {
        KeepOpenDialog(
            onDismiss = { asking = false },
            onConfirm = { note ->
                asking = false
                onKeepOpen(true, note)
            },
        )
    }
}

/**
 * Asks why, and takes no for an answer.
 *
 * The note is optional because a trade held on a hunch is still held, and a dialog that refused to
 * proceed without a reason would only teach the user to type a full stop. It earns its place months
 * later, when the reason is the one thing they cannot reconstruct.
 */
@Composable
internal fun KeepOpenDialog(onDismiss: () -> Unit, onConfirm: (String?) -> Unit) {
    var note by remember { mutableStateOf("") }
    KitDialog(
        title = "Keep this trade open?",
        text = "It stops closing on its own. The deadline, target 1 and the stop all stop ending " +
            "it, and it stays open until you record a sale with Sold - or until it reaches target " +
            "2, which finishes any trade. What the call itself did is still tracked and still shown.",
        confirmLabel = "Keep open",
        onConfirm = { onConfirm(note.trim().takeIf(String::isNotBlank)) },
        dismissLabel = "Cancel",
        onDismiss = onDismiss,
        posture = LocalFoldPosture.current,
        tone = TileTone.TILE3,
        glyph = "‖",
    ) {
        KitTextField(note, { note = it }, label = "Why (optional)", modifier = Modifier.padding(top = Space.m).fillMaxWidth())
        Text(
            "Shown on the card, so the reason outlives the decision.",
            style = MaterialTheme.typography.labelMedium,
            color = palette.muted,
            modifier = Modifier.padding(top = Space.xs),
        )
    }
}

/**
 * Closes a position at a price the user names.
 *
 * Offered for as long as no sale has been recorded, including after the recommendation's window has
 * run out: the deadline decides when the app stops watching, not when the user stopped holding.
 */
@Composable
internal fun SellButton(
    /** The trade being closed, for the levels its own call printed. */
    held: PositionView,
    suggestedExit: Double?,
    onSell: (Sale) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The Record sale action in the shade no longer opens this one: since 5.1 the Portfolio hosts
    // its sale dialog once, on PageState, so a fold cannot close it. See TradeDialogHost.
    var selling by remember { mutableStateOf(false) }
    KitButton("I sold it", onClick = { selling = true }, modifier = modifier, size = ButtonSize.SMALL)
    if (selling) {
        SellDialog(
            held = held,
            suggestedExit = suggestedExit,
            onDismiss = { selling = false },
            onConfirm = { sale ->
                selling = false
                onSell(sale)
            },
        )
    }
}

/**
 * The two prices a holding usually goes out at, and the share that went at each.
 *
 * A call names two targets and the ordinary way out of one is half at the first and the rest at the
 * second, which is two prices on two days and one position. The dialog offers exactly that: the
 * call's own targets prefilled, split evenly, both editable. Typing 100 into the split collapses it
 * back to the single price and single day a sale used to be, which is why there is no second dialog
 * and no switch to choose between them.
 *
 * Separate from [TradeDialog] rather than a third mode of it. That one asks a price and a day and
 * optionally a window, and every field it has means the same thing in both of its uses; folding
 * five fields and a conditional half into it would leave one dialog whose questions depend on which
 * button opened it.
 *
 * @param existing the sale already recorded, when this is correcting one rather than making one.
 *   Seeds every field from what was actually typed instead of the call's own targets, and the
 *   title and button say "Edit" rather than "Record" - the only two things that differ between
 *   selling a running trade and fixing a sale already on the books, which is otherwise the same
 *   two prices and two dates either way.
 */
@Composable
internal fun SellDialog(
    held: PositionView,
    suggestedExit: Double?,
    existing: Sale? = null,
    onDismiss: () -> Unit,
    onConfirm: (Sale) -> Unit,
) {
    val position = held.position
    // The call's own targets, which is where a holder following it gets out - unless a sale is
    // already on the books, which is what a correction opens on instead. Today's estimate stands
    // in for a fresh sale where the call never printed a target, so a call without targets still
    // sells.
    var price1 by remember {
        mutableStateOf(
            (existing?.price1 ?: position.target1 ?: suggestedExit)?.let(::formatPrice).orEmpty(),
        )
    }
    var price2 by remember {
        mutableStateOf(
            (existing?.price2 ?: position.target2 ?: suggestedExit)?.let(::formatPrice).orEmpty(),
        )
    }
    var split by remember {
        mutableStateOf(formatPrice(existing?.splitPct ?: HALF_SPLIT_PCT))
    }
    // The mode is its own choice, not worked out from the number. Worked out from it, emptying the
    // field to type a new split made the split "not two parts" for one keystroke, and the field the
    // reader was typing into left the screen with the keyboard still up.
    var twoParts by remember { mutableStateOf((existing?.splitPct ?: HALF_SPLIT_PCT) < FULL_SPLIT_PCT) }
    var date1 by remember { mutableStateOf((existing?.date1 ?: LocalDate.now()).toString()) }
    var date2 by remember { mutableStateOf((existing?.date2 ?: LocalDate.now()).toString()) }

    val parsedPrice1 = price1.toPriceOrNull()
    val parsedPrice2 = price2.toPriceOrNull()
    val parsedSplit = remember(split) { split.toSplitOrNull() }
    val parsedDate1 = remember(date1) { runCatching { LocalDate.parse(date1.trim()) }.getOrNull() }
    val parsedDate2 = remember(date2) { runCatching { LocalDate.parse(date2.trim()) }.getOrNull() }
    // The whole lot at one price is the sale this dialog collapses to, and it asks two fewer
    // questions rather than asking them and ignoring the answers.
    val inTwoParts = twoParts
    // Rejected rather than silently reordered. The two parts are typed together, so a second date
    // before the first is a typo, and a dialog that quietly swapped them would store a day the
    // user never gave.
    val datesInOrder = !inTwoParts || parsedDate1 == null || parsedDate2 == null ||
        !parsedDate2.isBefore(parsedDate1)
    val sale = when {
        parsedPrice1 == null || parsedDate1 == null -> null
        !inTwoParts -> Sale(price1 = parsedPrice1, date1 = parsedDate1)
        parsedSplit == null -> null
        // A split of the whole holding is one price, however the mode was reached.
        parsedSplit >= FULL_SPLIT_PCT -> Sale(price1 = parsedPrice1, date1 = parsedDate1)
        parsedPrice2 == null || parsedDate2 == null || !datesInOrder -> null
        else -> Sale(parsedPrice1, parsedDate1, parsedPrice2, parsedDate2, parsedSplit)
    }

    val p = palette
    // **A kit sheet since 5.0**, as on the canvas: one price or two parts, the split drawn as a bar,
    // each part's price and day, and what the position closes at before it is saved. A tap outside
    // or a swipe does not close it, so two typed prices cannot be lost by accident.
    KitSheet(
        onDismiss = onDismiss,
        posture = LocalFoldPosture.current,
        title = if (existing != null) "Edit the sale" else "Record the sale",
        dismissOnOutside = false,
    ) {
        SheetTitle(
            if (existing != null) "Edit the sale" else "Record the sale",
            "${position.ticker} · bought ${formatPrice(position.entryPrice)} on ${shortDate(position.entryDate)}",
        )
        SegmentedControl(
            options = listOf(false, true),
            selected = inTwoParts,
            onSelect = { chosen ->
                twoParts = chosen
                // A fresh half only where there is no usable split already, so switching back and
                // forth does not throw away a figure the reader typed.
                if (chosen && (parsedSplit == null || parsedSplit >= FULL_SPLIT_PCT)) split = formatPrice(HALF_SPLIT_PCT)
            },
            label = { if (it) "In two parts" else "At one price" },
            modifier = Modifier.fillMaxWidth(),
        )
        if (inTwoParts) {
            Tile(modifier = Modifier.fillMaxWidth()) {
                Text("How the holding was split", style = MaterialTheme.typography.labelMedium, color = p.muted)
                // The bar follows a figure it can draw; the field under it stays put either way.
                if (parsedSplit != null) Row(Modifier.padding(top = Space.s).fillMaxWidth().height(44.dp).clip(Kit.PanelShape), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Box(Modifier.weight(parsedSplit.toFloat()).fillMaxHeight().background(p.highlight), contentAlignment = Alignment.Center) {
                        Text("${formatPrice(parsedSplit)}%", style = MaterialTheme.typography.titleSmall.copy(fontFamily = TabularFigures), color = p.onTile)
                    }
                    Box(Modifier.weight((FULL_SPLIT_PCT - parsedSplit).toFloat().coerceAtLeast(0.01f)).fillMaxHeight().background(p.hero), contentAlignment = Alignment.Center) {
                        Text("${formatPrice(FULL_SPLIT_PCT - parsedSplit)}%", style = MaterialTheme.typography.titleSmall.copy(fontFamily = TabularFigures), color = p.onHero)
                    }
                }
                KitTextField(
                    split, { split = it },
                    label = "Sold at the target 1 price (%)",
                    figure = true,
                    error = if (parsedSplit == null) "Between 1 and 100." else null,
                    modifier = Modifier.padding(top = Space.m).fillMaxWidth(),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Kit.TileGap)) {
            Tile(modifier = Modifier.weight(1f), tone = if (inTwoParts) TileTone.HIGHLIGHT else TileTone.CARD) {
                if (inTwoParts) Text("First part", style = MaterialTheme.typography.titleSmall)
                KitTextField(
                    price1, { price1 = it },
                    label = if (inTwoParts) "Target 1 price" else "Selling price",
                    figure = true,
                    error = if (price1.isNotBlank() && parsedPrice1 == null) "Not a price" else null,
                    modifier = Modifier.padding(top = Space.s).fillMaxWidth(),
                )
                DateField(
                    label = if (inTwoParts) "Date" else "Selling date",
                    value = date1,
                    onValueChange = { date1 = it },
                    isError = parsedDate1 == null,
                    modifier = Modifier.padding(top = Space.s).fillMaxWidth(),
                )
            }
            if (inTwoParts) {
                Tile(modifier = Modifier.weight(1f), tone = TileTone.HERO) {
                    Text("Second part", style = MaterialTheme.typography.titleSmall)
                    KitTextField(
                        price2, { price2 = it },
                        label = "Target 2 price",
                        figure = true,
                        error = if (price2.isNotBlank() && parsedPrice2 == null) "Not a price" else null,
                        modifier = Modifier.padding(top = Space.s).fillMaxWidth(),
                    )
                    DateField(
                        label = "Date",
                        value = date2,
                        onValueChange = { date2 = it },
                        isError = parsedDate2 == null || !datesInOrder,
                        modifier = Modifier.padding(top = Space.s).fillMaxWidth(),
                    )
                }
            }
        }
        if (!datesInOrder) {
            Text("The second part cannot have gone before the first.", style = MaterialTheme.typography.labelMedium, color = p.loss)
        }
        // What the position will actually carry, before it carries it. Every figure on the card is
        // measured from this one number, and a reader who has just typed two prices has no way to
        // check it otherwise.
        sale?.let { pending ->
            Tile(modifier = Modifier.fillMaxWidth(), tone = TileTone.INK) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Closes at ${formatPrice(pending.blended)}", style = MaterialTheme.typography.titleMedium.copy(fontFamily = TabularFigures))
                        if (pending.inTwoParts) {
                            Text(
                                "the two prices weighted ${formatPrice(pending.splitPct)} / ${formatPrice(FULL_SPLIT_PCT - pending.splitPct)}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    val ret = (pending.blended - position.entryPrice) / position.entryPrice * 100
                    Text(formatPercent(ret), style = MaterialTheme.typography.headlineSmall.copy(fontFamily = TabularFigures), color = if (ret < 0) tileSignals(TileTone.INK).loss else tileSignals(TileTone.INK).gain)
                }
            }
        }
        Text(
            if (existing != null) "Every figure this trade reports is measured from what is saved here."
            else "The position closes now, whether or not the recommendation's sessions have run out.",
            style = MaterialTheme.typography.bodySmall,
            color = p.muted,
        )
        SheetButtons(
            confirmLabel = if (existing != null) "Save" else "Close position",
            enabled = sale != null,
            onConfirm = { sale?.let(onConfirm) },
            onDismiss = onDismiss,
        )
    }
}

/** A sheet's own heading: the title, and one line saying what it is about. */
@Composable
internal fun SheetTitle(title: String, subtitle: String) {
    Column(Modifier.padding(horizontal = Space.xs)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, color = palette.ink)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = palette.muted)
    }
}

/** Cancel and the sheet's one confirming action, at its foot, the confirming one wider. */
@Composable
internal fun SheetButtons(confirmLabel: String, enabled: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = Space.s), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
        KitButton("Cancel", onClick = onDismiss, kind = ButtonKind.SECONDARY, size = ButtonSize.LARGE, modifier = Modifier.weight(1f))
        KitButton(confirmLabel, onClick = onConfirm, enabled = enabled, size = ButtonSize.LARGE, modifier = Modifier.weight(2f))
    }
}

/**
 * A date field the reader can only fill from the calendar, never by typing.
 *
 * Bought, Sold and Edit trade all asked for `YYYY-MM-DD` on the bare keyboard although the app
 * already carries a date picker everywhere else a date is set. The field still reports the same
 * ISO string every caller here already parses, so nothing downstream of it changes - only how the
 * reader gets there. Read-only rather than disabled, so it keeps the same look as the fields
 * beside it instead of greying out.
 */
@Composable
private fun DateField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean,
    supportingText: String? = null,
    modifier: Modifier = Modifier,
) {
    var picking by remember { mutableStateOf(false) }
    val p = palette
    // A kit field the calendar fills: the same white field and label as the price beside it, with
    // the date set in figures and the calendar glyph saying how it is changed.
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = LocalContentColor.current.copy(alpha = 0.8f))
        Row(
            Modifier
                .padding(top = 6.dp)
                .fillMaxWidth()
                .height(52.dp)
                .clip(Kit.FieldShape)
                .background(p.card)
                .border(BorderStroke(if (isError) 2.dp else 1.5.dp, if (isError) p.loss else p.line), Kit.FieldShape)
                .clickable(role = Role.Button, onClickLabel = "Choose $label") { picking = true }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val shown = runCatching { LocalDate.parse(value.trim()) }.getOrNull()?.let { shortDate(it) } ?: value
            Text(shown, style = MaterialTheme.typography.titleSmall.copy(fontFamily = TabularFigures), color = p.ink, modifier = Modifier.weight(1f))
            Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = p.muted, modifier = Modifier.size(20.dp))
        }
        supportingText?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = p.loss, modifier = Modifier.padding(top = 4.dp)) }
    }
    if (picking) {
        // The kit's own calendar since 5.0, in a sheet over the one this field sits in.
        com.ikverse.egxanalyzer.ui.kit.KitCalendarSheet(
            title = label,
            selected = runCatching { LocalDate.parse(value.trim()) }.getOrNull() ?: LocalDate.now(),
            onPick = { picked ->
                onValueChange(picked.toString())
                picking = false
            },
            onDismiss = { picking = false },
            posture = com.ikverse.egxanalyzer.ui.kit.LocalFoldPosture.current,
        )
    }
}

/** Half the holding, which is what a call naming two targets is usually taken in. */
private const val HALF_SPLIT_PCT = 50.0

/**
 * A share of the holding the app will actually honour, or nothing.
 *
 * Zero is refused along with everything outside the range: a part that is none of the holding is
 * not a part, and it would store a second price that moved nothing.
 */
private fun String.toSplitOrNull(): Double? = trim().replace(',', '.').toDoubleOrNull()
    ?.takeIf { it > 0.0 && it <= FULL_SPLIT_PCT }

/**
 * A price and a date, and nothing else.
 *
 * The date is offered rather than assumed: a trade is often recorded the evening after it was
 * taken, and filing it under today would move the position's whole history by a day.
 */
@Composable
internal fun TradeDialog(
    title: String,
    explanation: String,
    priceLabel: String,
    dateLabel: String,
    confirmLabel: String,
    initialPrice: Double?,
    onDismiss: () -> Unit,
    onConfirm: (price: Double, date: LocalDate, windowSessions: Int?) -> Unit,
    /** Adds the trade-window field, prefilled with this. Absent for a sale, which has no window. */
    initialWindow: Int? = null,
    /** Says where the offered window came from, which differs between buying and editing. */
    windowHelp: String = "",
    /**
     * What the date field opens on. Today for a fresh purchase, which is when it was actually
     * bought; the trade's own entry date for a correction, which must not move a date nobody
     * touched - every figure the card reports (bought-on, sessions held, the peak and trough since
     * entry) is measured from it, and a save that only meant to fix the price silently moved them
     * all when this defaulted to today regardless of what was being edited.
     */
    initialDate: LocalDate = LocalDate.now(),
) {
    var price by remember { mutableStateOf(initialPrice?.let(::formatPrice).orEmpty()) }
    var date by remember { mutableStateOf(initialDate.toString()) }
    var window by remember { mutableStateOf(initialWindow?.toString().orEmpty()) }
    val parsedPrice = price.toPriceOrNull()
    val parsedDate = remember(date) { runCatching { LocalDate.parse(date.trim()) }.getOrNull() }
    // Rejected rather than clamped. A typed 500 quietly becoming 30 is a deadline the user believes
    // is one thing and the app believes is another, and they would not find out for a month.
    val parsedWindow = remember(window) { window.toWindowOrNull() }
    val windowValid = initialWindow == null || parsedWindow != null
    val p = palette

    // **A kit sheet since 5.0**: the price large on a cobalt tile, the day and the window beside each
    // other under it, and what the window means said underneath. A tap outside does not close it.
    KitSheet(onDismiss = onDismiss, posture = LocalFoldPosture.current, title = title, dismissOnOutside = false) {
        SheetTitle(title, explanation.substringBefore(". ") + ".")
        Tile(modifier = Modifier.fillMaxWidth(), tone = TileTone.HERO) {
            KitTextField(
                price, { price = it },
                label = priceLabel,
                figure = true,
                error = if (price.isNotBlank() && parsedPrice == null) "Not a price" else null,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Kit.TileGap)) {
            Tile(modifier = Modifier.weight(1f)) {
                DateField(
                    label = dateLabel,
                    value = date,
                    onValueChange = { date = it },
                    isError = parsedDate == null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (initialWindow != null) {
                Tile(modifier = Modifier.weight(1f)) {
                    Text("Trade window", style = MaterialTheme.typography.labelMedium, color = p.muted)
                    KitStepper(
                        value = parsedWindow ?: initialWindow,
                        onValueChange = { window = it.toString() },
                        range = Scoring.MIN_WINDOW_SESSIONS..Scoring.MAX_WINDOW_SESSIONS,
                        label = "Trade window",
                        modifier = Modifier.padding(top = Space.s),
                    )
                    Text(
                        "sessions · ${Scoring.MIN_WINDOW_SESSIONS} to ${Scoring.MAX_WINDOW_SESSIONS}",
                        style = MaterialTheme.typography.labelMedium,
                        color = p.muted,
                        modifier = Modifier.padding(top = Space.xs),
                    )
                }
            }
        }
        if (initialWindow != null && windowHelp.isNotBlank()) {
            Tile(modifier = Modifier.fillMaxWidth(), tone = TileTone.TILE3) {
                Text(windowHelp, style = MaterialTheme.typography.bodySmall)
            }
        }
        Text(explanation, style = MaterialTheme.typography.bodySmall, color = p.muted)
        SheetButtons(
            confirmLabel = confirmLabel,
            enabled = parsedPrice != null && parsedDate != null && windowValid,
            onConfirm = {
                val value = parsedPrice ?: return@SheetButtons
                val on = parsedDate ?: return@SheetButtons
                onConfirm(value, on, parsedWindow)
            },
            onDismiss = onDismiss,
        )
    }
}

/**
 * A typed price, or nothing.
 *
 * A comma is accepted as the decimal separator: the keyboard a phone offers for a number depends on
 * its locale, and a rejected `1,25` reads as the app refusing a valid price.
 */
private fun String.toPriceOrNull(): Double? =
    trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 && it.isFinite() }

/**
 * A window the app will actually honour, or nothing.
 *
 * The same bounds [Scoring.clampWindow] enforces, checked rather than applied: the field says no to
 * a number outside them instead of accepting one and storing a different one.
 */
private fun String.toWindowOrNull(): Int? = trim().toIntOrNull()
    ?.takeIf { it in Scoring.MIN_WINDOW_SESSIONS..Scoring.MAX_WINDOW_SESSIONS }

/**
 * Where the position stands, in one word, coloured the same everywhere it appears. Tappable since
 * 2026-09-24, like every other note pill on this card.
 */
@Composable
internal fun PositionStatusChip(view: PositionView) {
    var showing by remember(view.position.id) { mutableStateOf(false) }
    // The filled tag every other status in the app wears, so one status reads one way everywhere.
    KitTag(view.status.plainWord(), tone = view.status.tone(), filled = true, ink = palette.ink, onClick = { showing = true })
    if (showing) {
        // `InfoNote`'s own constructor rather than the checked `infoNote` factory: that helper's
        // callers are checked by InfoNoteTest for static prose, and both the label and its
        // explanation here are read off `view.status` rather than typed at the call site.
        InfoSheet(InfoNote(view.status.plainWord(), listOf(view.status.explanation()))) { showing = false }
    }
}

/** What this status means for the trade, in the reader's own words. */
private fun PositionStatus.explanation(): String = when (this) {
    PositionStatus.OPEN -> "Still running: neither a target nor the stop has been reached, and " +
        "the deadline has not passed."
    PositionStatus.PARTIAL_TARGET_HIT -> "Reached target 1. Still open for target 2 unless it was " +
        "sold there, or the stop or the deadline has since ended it."
    PositionStatus.FULL_TARGET_HIT -> "Reached target 2, the best outcome a call offers."
    PositionStatus.STOPPED_OUT -> "Broke the stop by more than 2%, which is where the trade ends " +
        "regardless of what it does afterwards."
    PositionStatus.EXPIRED -> "The window ran out with neither a target nor the stop reached - " +
        "still in the market's hands, not closed by a decision."
    PositionStatus.CLOSED_MANUALLY -> "Closed by a sale you recorded, rather than by a target, the " +
        "stop or the deadline."
}

/**
 * The outline that marks a card as one you are actually in.
 *
 * Colour rather than a badge, and the same colour the status chip carries, so a page of cards can be
 * read for "what am I holding, and how is it going" without opening any of them.
 */
@Composable
internal fun heldBorder(view: PositionView?): BorderStroke? =
    view?.let { BorderStroke(HeldOutline, it.status.tone()) }

/**
 * Thinner than the plain card hairline, so a held card's coloured edge reads as the card's own
 * outline saying something rather than as a heavier ring boxing it in.
 */
private val HeldOutline = 0.5.dp

/**
 * Status colour, borrowed from the roles prices already use.
 *
 * Targets are the tertiary hue everywhere in this app, a stop is the error hue, and a position still
 * running is the primary one the market's own figures use. Nothing new is invented here, so a
 * portfolio card reads in the same language as the recommendation it came from.
 */
@Composable
internal fun PositionStatus.tone(): Color = when (this) {
    // The market's blue since the 2026-11 redesign: the accent is vermilion now, and an open trade
    // drawn in it would read as one losing.
    PositionStatus.OPEN -> extraColors.market
    PositionStatus.PARTIAL_TARGET_HIT, PositionStatus.FULL_TARGET_HIT ->
        MaterialTheme.colorScheme.tertiary
    PositionStatus.STOPPED_OUT -> extraColors.loss
    // Amber, and deliberately not the loss red. A trade that ran out of time can easily be up 5%,
    // so red would report a loss the position never made and would read as the same thing as the
    // stop-out beside it. The overdue pill keeps the loss colour: amber says out of time, red says
    // and you are late, which is one story in two steps rather than two alarms.
    PositionStatus.EXPIRED -> extraColors.expired
    PositionStatus.CLOSED_MANUALLY -> MaterialTheme.colorScheme.onSurfaceVariant
}

