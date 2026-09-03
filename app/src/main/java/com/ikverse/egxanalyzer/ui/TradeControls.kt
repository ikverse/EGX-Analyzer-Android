package com.ikverse.egxanalyzer.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddShoppingCart
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import java.time.LocalDate

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
            ActionPill("Bought", Icons.Outlined.AddShoppingCart, onClick = { buying = true })
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
            onDismiss = { buying = false },
            onConfirm = { price, date, window ->
                buying = false
                onBuy(price, date, window ?: defaultWindow)
            },
        )
    }
}

/**
 * How far past its deadline a trade has run with nothing recorded about how it ended.
 *
 * The error colour rather than a neutral one, and next to the status rather than buried in the
 * figures: it is the one thing on a card that is asking the user to do something.
 */
@Composable
internal fun OverdueChip(days: Long) {
    OutlinePill(
        "Overdue $days ${days.dayWord()}",
        outline = MaterialTheme.colorScheme.error,
        textColor = MaterialTheme.colorScheme.onErrorContainer,
    )
}

/**
 * Says that the stock split under this trade, so nothing here is being valued.
 *
 * Neutral rather than a warning colour, and deliberately the same neutral the unjudged outcomes wear
 * in Insights: nothing has gone wrong with the trade, and nothing about it is known either. The
 * wording is [Outcome.PRICE_BREAK]'s own label rather than a second phrasing of it, so a card and the
 * report it came from can never end up describing this two different ways.
 */
@Composable
internal fun PriceScaleChip() {
    OutlinePill(
        Outcome.PRICE_BREAK.label,
        outline = MaterialTheme.colorScheme.outline,
        textColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
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
    ActionPill(
        "Keep open",
        Icons.Outlined.HourglassEmpty,
        onClick = { asking = true },
        modifier = modifier,
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
private fun KeepOpenDialog(onDismiss: () -> Unit, onConfirm: (String?) -> Unit) {
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Keep this trade open?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
                Text(
                    "It stops closing on its own. The deadline, target 1 and the stop all stop " +
                        "ending it, and it stays open until you record a sale with Sold - or " +
                        "until it reaches target 2, which finishes any trade. What the call " +
                        "itself did is still tracked and still shown.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Why (optional)") },
                    singleLine = true,
                    supportingText = { Text("Shown on the card, so the reason outlives the decision.") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(note.trim().takeIf(String::isNotBlank)) },
            ) { Text("Keep open") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
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
    /**
     * Opens the dialog without the button being pressed, for the Record sale action in the shade.
     *
     * A sale needs a price and a date, so it can never be a one-tap action from a notification -
     * what the action can do is put the reader in front of the two fields rather than in front of
     * the tab and a hunt for the card. See `TradeStatusNotifier`.
     */
    openNow: Boolean = false,
    /** Clears the request, so dismissing the dialog does not immediately reopen it. */
    onOpened: () -> Unit = {},
) {
    var selling by remember { mutableStateOf(false) }
    // Keyed on the request rather than run once: a second notification acted on while the app is
    // already open has to open the dialog again.
    LaunchedEffect(openNow) {
        if (openNow) {
            selling = true
            onOpened()
        }
    }
    ActionPill("Sold", Icons.Outlined.Sell, onClick = { selling = true }, modifier = modifier)
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
 */
@Composable
private fun SellDialog(
    held: PositionView,
    suggestedExit: Double?,
    onDismiss: () -> Unit,
    onConfirm: (Sale) -> Unit,
) {
    val position = held.position
    // The call's own targets, which is where a holder following it gets out. Today's estimate
    // stands in where the call never printed one, so a call without targets still sells.
    var price1 by remember {
        mutableStateOf((position.target1 ?: suggestedExit)?.let(::formatPrice).orEmpty())
    }
    var price2 by remember {
        mutableStateOf((position.target2 ?: suggestedExit)?.let(::formatPrice).orEmpty())
    }
    var split by remember { mutableStateOf(formatPrice(HALF_SPLIT_PCT)) }
    var date1 by remember { mutableStateOf(LocalDate.now().toString()) }
    var date2 by remember { mutableStateOf(LocalDate.now().toString()) }

    val parsedPrice1 = price1.toPriceOrNull()
    val parsedPrice2 = price2.toPriceOrNull()
    val parsedSplit = remember(split) { split.toSplitOrNull() }
    val parsedDate1 = remember(date1) { runCatching { LocalDate.parse(date1.trim()) }.getOrNull() }
    val parsedDate2 = remember(date2) { runCatching { LocalDate.parse(date2.trim()) }.getOrNull() }
    // The whole lot at one price is the sale this dialog collapses to, and it asks two fewer
    // questions rather than asking them and ignoring the answers.
    val inTwoParts = parsedSplit != null && parsedSplit < FULL_SPLIT_PCT
    // Rejected rather than silently reordered. The two parts are typed together, so a second date
    // before the first is a typo, and a dialog that quietly swapped them would store a day the
    // user never gave.
    val datesInOrder = !inTwoParts || parsedDate1 == null || parsedDate2 == null ||
        !parsedDate2.isBefore(parsedDate1)
    val sale = when {
        parsedPrice1 == null || parsedSplit == null || parsedDate1 == null -> null
        !inTwoParts -> Sale(price1 = parsedPrice1, date1 = parsedDate1)
        parsedPrice2 == null || parsedDate2 == null || !datesInOrder -> null
        else -> Sale(parsedPrice1, parsedDate1, parsedPrice2, parsedDate2, parsedSplit)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record the sale") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(Space.m),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(
                    "The prices you actually sold at. A call names two targets and the usual way " +
                        "out is half at the first and the rest at the second, so both are " +
                        "offered - set the share to 100% where the whole holding went at one " +
                        "price. The position closes now, whether or not the recommendation's " +
                        "sessions have run out.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = price1,
                    onValueChange = { price1 = it },
                    label = { Text(if (inTwoParts) "Target 1 price" else "Selling price") },
                    singleLine = true,
                    isError = price1.isNotBlank() && parsedPrice1 == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = date1,
                    onValueChange = { date1 = it },
                    label = { Text(if (inTwoParts) "Target 1 date" else "Selling date") },
                    singleLine = true,
                    isError = parsedDate1 == null,
                    supportingText = { Text("YYYY-MM-DD") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = split,
                    onValueChange = { split = it },
                    label = { Text("Sold at this price (%)") },
                    singleLine = true,
                    isError = parsedSplit == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    supportingText = {
                        Text(
                            when {
                                parsedSplit == null -> "Between 1 and 100."
                                inTwoParts -> "The rest went at the target 2 price below."
                                else -> "The whole holding, at the one price above."
                            },
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (inTwoParts) {
                    OutlinedTextField(
                        value = price2,
                        onValueChange = { price2 = it },
                        label = { Text("Target 2 price") },
                        singleLine = true,
                        isError = price2.isNotBlank() && parsedPrice2 == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = date2,
                        onValueChange = { date2 = it },
                        label = { Text("Target 2 date") },
                        singleLine = true,
                        isError = parsedDate2 == null || !datesInOrder,
                        supportingText = {
                            Text(
                                if (!datesInOrder) {
                                    "The second part cannot have gone before the first."
                                } else {
                                    "YYYY-MM-DD"
                                },
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                // What the position will actually carry, before it carries it. Every figure on the
                // card is measured from this one number, and a reader who has just typed two
                // prices has no way to check it otherwise.
                sale?.takeIf(Sale::inTwoParts)?.let { pending ->
                    Text(
                        "Closes at ${formatPrice(pending.blended)} - the two prices weighted " +
                            "${formatPrice(pending.splitPct)} / " +
                            "${formatPrice(FULL_SPLIT_PCT - pending.splitPct)}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = sale != null,
                onClick = { onConfirm(sale ?: return@TextButton) },
            ) { Text("Close position") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
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
) {
    var price by remember { mutableStateOf(initialPrice?.let(::formatPrice).orEmpty()) }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var window by remember { mutableStateOf(initialWindow?.toString().orEmpty()) }
    val parsedPrice = price.toPriceOrNull()
    val parsedDate = remember(date) { runCatching { LocalDate.parse(date.trim()) }.getOrNull() }
    // Rejected rather than clamped. A typed 500 quietly becoming 30 is a deadline the user believes
    // is one thing and the app believes is another, and they would not find out for a month.
    val parsedWindow = remember(window) { window.toWindowOrNull() }
    val windowValid = initialWindow == null || parsedWindow != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
                Text(
                    explanation,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it },
                    label = { Text(priceLabel) },
                    singleLine = true,
                    isError = price.isNotBlank() && parsedPrice == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text(dateLabel) },
                    singleLine = true,
                    isError = parsedDate == null,
                    supportingText = { Text("YYYY-MM-DD") },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (initialWindow != null) {
                    OutlinedTextField(
                        value = window,
                        onValueChange = { window = it },
                        label = { Text("Trade window") },
                        singleLine = true,
                        isError = parsedWindow == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        supportingText = {
                            Text(
                                if (parsedWindow == null) {
                                    "Between ${Scoring.MIN_WINDOW_SESSIONS} and " +
                                        "${Scoring.MAX_WINDOW_SESSIONS} trading sessions."
                                } else {
                                    windowHelp
                                },
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = parsedPrice != null && parsedDate != null && windowValid,
                onClick = {
                    val value = parsedPrice ?: return@TextButton
                    val on = parsedDate ?: return@TextButton
                    onConfirm(value, on, parsedWindow)
                },
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
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

/** Where the position stands, in one word, coloured the same everywhere it appears. */
@Composable
internal fun PositionStatusChip(view: PositionView) {
    OutlinePill(
        view.status.label,
        outline = view.status.pillOutline(),
        textColor = view.status.onContainer(),
    )
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

/** Thick enough to read as deliberate at a glance, thin enough not to shout over the card. */
private val HeldOutline = 2.dp

/**
 * Status colour, borrowed from the roles prices already use.
 *
 * Targets are the tertiary hue everywhere in this app, a stop is the error hue, and a position still
 * running is the primary one the market's own figures use. Nothing new is invented here, so a
 * portfolio card reads in the same language as the recommendation it came from.
 */
@Composable
internal fun PositionStatus.tone(): Color = when (this) {
    PositionStatus.OPEN -> MaterialTheme.colorScheme.primary
    PositionStatus.PARTIAL_TARGET_HIT, PositionStatus.FULL_TARGET_HIT ->
        MaterialTheme.colorScheme.tertiary
    PositionStatus.STOPPED_OUT -> MaterialTheme.colorScheme.error
    // Amber, and deliberately not the error red. A trade that ran out of time can easily be up 5%,
    // so red would report a loss the position never made and would read as the same thing as the
    // stop-out beside it. The overdue pill keeps the error colour: amber says out of time, red says
    // and you are late, which is one story in two steps rather than two alarms.
    PositionStatus.EXPIRED -> extraColors.expired
    PositionStatus.CLOSED_MANUALLY -> MaterialTheme.colorScheme.onSurfaceVariant
}

/**
 * The ring a status pill wears, which is [tone] wherever the status means something.
 *
 * The exception is a position the user closed by hand. Its tone is `onSurfaceVariant`, a colour
 * meant for text, and a ring drawn in it reads louder than the red on the card beside it - which is
 * backwards for the one state that is finished business. `outline` is the role Material keeps for a
 * border with nothing to say.
 */
@Composable
internal fun PositionStatus.pillOutline(): Color =
    if (this == PositionStatus.CLOSED_MANUALLY) MaterialTheme.colorScheme.outline else tone()

@Composable
internal fun PositionStatus.onContainer(): Color = when (this) {
    PositionStatus.OPEN -> MaterialTheme.colorScheme.onPrimaryContainer
    PositionStatus.PARTIAL_TARGET_HIT, PositionStatus.FULL_TARGET_HIT ->
        MaterialTheme.colorScheme.onTertiaryContainer
    PositionStatus.STOPPED_OUT -> MaterialTheme.colorScheme.onErrorContainer
    PositionStatus.EXPIRED -> extraColors.onExpiredContainer
    PositionStatus.CLOSED_MANUALLY -> MaterialTheme.colorScheme.onSurfaceVariant
}
