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
import com.ikverse.egxanalyzer.model.PositionStatus
import com.ikverse.egxanalyzer.model.PositionView
import com.ikverse.egxanalyzer.model.RecommendationDataPoint
import com.ikverse.egxanalyzer.model.callDate
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

    fun buy(
        stock: ConsolidatedRecommendation,
        point: RecommendationDataPoint,
        channel: String?,
        price: Double,
        date: LocalDate,
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
    /** Prefills the sale dialog with the latest close, which is the likeliest sale price. */
    suggestedExit: Double? = null,
    onBuy: (price: Double, date: LocalDate) -> Unit,
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
            if (held.awaitingSale) {
                SellButton(suggestedExit ?: held.currentPrice, onSell)
            }
        }
    }

    if (buying) {
        TradeDialog(
            title = "Record the purchase",
            explanation = "The price you actually paid, which is what every figure for this " +
                "position is measured from. The recommendation's deadline is unchanged: it still " +
                "runs from the session the call was made for.",
            priceLabel = "Entry price",
            dateLabel = "Entry date",
            confirmLabel = "Save",
            initialPrice = suggestedEntry,
            onDismiss = { buying = false },
            onConfirm = { price, date ->
                buying = false
                onBuy(price, date)
            },
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
            onConfirm = { price, date ->
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
    onConfirm: (price: Double, date: LocalDate) -> Unit,
) {
    var price by remember { mutableStateOf(initialPrice?.let(::formatPrice).orEmpty()) }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    val parsedPrice = price.toPriceOrNull()
    val parsedDate = remember(date) { runCatching { LocalDate.parse(date.trim()) }.getOrNull() }

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
            }
        },
        confirmButton = {
            TextButton(
                enabled = parsedPrice != null && parsedDate != null,
                onClick = {
                    val value = parsedPrice ?: return@TextButton
                    val on = parsedDate ?: return@TextButton
                    onConfirm(value, on)
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
    PositionStatus.CLOSED, PositionStatus.CLOSED_MANUALLY ->
        MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
internal fun PositionStatus.container(): Color = when (this) {
    PositionStatus.OPEN -> MaterialTheme.colorScheme.primaryContainer
    PositionStatus.PARTIAL_TARGET_HIT, PositionStatus.FULL_TARGET_HIT ->
        MaterialTheme.colorScheme.tertiaryContainer
    PositionStatus.STOPPED_OUT -> MaterialTheme.colorScheme.errorContainer
    PositionStatus.CLOSED, PositionStatus.CLOSED_MANUALLY ->
        MaterialTheme.colorScheme.secondaryContainer
}

@Composable
internal fun PositionStatus.onContainer(): Color = when (this) {
    PositionStatus.OPEN -> MaterialTheme.colorScheme.onPrimaryContainer
    PositionStatus.PARTIAL_TARGET_HIT, PositionStatus.FULL_TARGET_HIT ->
        MaterialTheme.colorScheme.onTertiaryContainer
    PositionStatus.STOPPED_OUT -> MaterialTheme.colorScheme.onErrorContainer
    PositionStatus.CLOSED, PositionStatus.CLOSED_MANUALLY ->
        MaterialTheme.colorScheme.onSecondaryContainer
}
