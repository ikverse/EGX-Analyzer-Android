package com.ikverse.egxanalyzer.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddShoppingCart
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.ikverse.egxanalyzer.model.RecommendationDataPoint
import com.ikverse.egxanalyzer.model.Scoring
import com.ikverse.egxanalyzer.model.callDate
import com.ikverse.egxanalyzer.model.tradeWindow
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
     * What a new trade's window is offered as, which is this call's own deadline.
     *
     * The scoring setting for an ordinary call; a T+1 call names its own and the setting does not
     * apply to it. Read at the moment the dialog opens rather than captured earlier: a user who has
     * just changed the setting means the new one.
     */
    fun windowFor(point: RecommendationDataPoint): Int =
        point.tradeWindow(appState.appPreferences.scoringWindowSessions).sessions

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
        )
    }

    fun sell(view: PositionView, price: Double, date: LocalDate) =
        appState.recordSale(view.position, price, date)
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
    onBuy: (price: Double, date: LocalDate, windowSessions: Int) -> Unit,
    onSell: (price: Double, date: LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    var buying by remember { mutableStateOf(false) }

    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (held == null) {
            FilledTonalButton(onClick = { buying = true }) {
                Icon(
                    Icons.Outlined.AddShoppingCart,
                    contentDescription = null,
                    modifier = Modifier.size(IconSize.Inline),
                )
                Text("Bought", Modifier.padding(start = Space.s))
            }
        } else {
            PositionStatusChip(held)
            // The same warning the Portfolio card carries, on the card the trade was taken from:
            // a deadline that has passed with nothing recorded is worth seeing wherever the stock
            // is being looked at, not only on the tab the user has to remember to open.
            if (held.overdue) OverdueChip(held.overdueDays)
            if (held.priceScaleChanged) PriceScaleChip()
            if (held.awaitingSale) {
                // The estimate before the live price: they are the same while the trade is open,
                // and once the deadline has closed it the estimate is the better guess at what the
                // user actually got out at.
                SellButton(suggestedExit ?: held.exitPrice ?: held.currentPrice, onSell)
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
                "From your scoring setting. Change it to give this trade its own deadline."
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
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = CircleShape) {
        Text(
            "Overdue $days ${days.dayWord()}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
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
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = CircleShape) {
        Text(
            Outcome.PRICE_BREAK.label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
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
    OutlinedButton(onClick = { asking = true }, modifier = modifier) {
        Icon(
            Icons.Outlined.HourglassEmpty,
            contentDescription = null,
            modifier = Modifier.size(IconSize.Inline),
        )
        Text("Keep open", Modifier.padding(start = Space.s))
    }

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
    suggestedExit: Double?,
    onSell: (price: Double, date: LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selling by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { selling = true }, modifier = modifier) {
        Icon(
            Icons.Outlined.Sell,
            contentDescription = null,
            modifier = Modifier.size(IconSize.Inline),
        )
        Text("Sold", Modifier.padding(start = Space.s))
    }
    if (selling) {
        TradeDialog(
            title = "Record the sale",
            explanation = "The price you actually sold at. The position closes now, whether or " +
                "not the recommendation's sessions have run out.",
            priceLabel = "Selling price",
            dateLabel = "Selling date",
            confirmLabel = "Close position",
            initialPrice = suggestedExit,
            onDismiss = { selling = false },
            onConfirm = { price, date, _ ->
                selling = false
                onSell(price, date)
            },
        )
    }
}

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
    Surface(color = view.status.container(), shape = CircleShape) {
        Text(
            view.status.label,
            style = MaterialTheme.typography.labelMedium,
            color = view.status.onContainer(),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
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

@Composable
internal fun PositionStatus.container(): Color = when (this) {
    PositionStatus.OPEN -> MaterialTheme.colorScheme.primaryContainer
    PositionStatus.PARTIAL_TARGET_HIT, PositionStatus.FULL_TARGET_HIT ->
        MaterialTheme.colorScheme.tertiaryContainer
    PositionStatus.STOPPED_OUT -> MaterialTheme.colorScheme.errorContainer
    PositionStatus.EXPIRED -> extraColors.expiredContainer
    // The most muted pair in the scheme, for the one state that is finished business.
    PositionStatus.CLOSED_MANUALLY -> MaterialTheme.colorScheme.surfaceVariant
}

@Composable
internal fun PositionStatus.onContainer(): Color = when (this) {
    PositionStatus.OPEN -> MaterialTheme.colorScheme.onPrimaryContainer
    PositionStatus.PARTIAL_TARGET_HIT, PositionStatus.FULL_TARGET_HIT ->
        MaterialTheme.colorScheme.onTertiaryContainer
    PositionStatus.STOPPED_OUT -> MaterialTheme.colorScheme.onErrorContainer
    PositionStatus.EXPIRED -> extraColors.onExpiredContainer
    PositionStatus.CLOSED_MANUALLY -> MaterialTheme.colorScheme.onSurfaceVariant
}
