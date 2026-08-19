package com.ikverse.egxanalyzer.next

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.ikverse.egxanalyzer.model.PositionView
import com.ikverse.egxanalyzer.ui.AppState
import java.time.LocalDate

/**
 * The three things that can be asked about a trade, and their three blast radii.
 *
 * Selling **adds** a fact the app did not have, so it is the accent. Editing **changes** one that
 * is already recorded, so it is steel and says the old value beside the new. Removing **destroys**
 * one, so it is red, it states what goes and what survives, and it does not share a button row with
 * anything ordinary.
 */
@Composable
internal fun NextTradeSheets(appState: AppState, page: NextPageState) {
    val sheet = page.tradeSheet ?: return
    val view = appState.positionById(sheet.positionId)
    if (view == null) {
        // The trade went away underneath the sheet - a sync, or a delete on another device.
        page.tradeSheet = null
        return
    }
    val dismiss = { page.tradeSheet = null }
    when (sheet) {
        is TradeSheet.Sell -> SellSheet(view, appState, dismiss)
        is TradeSheet.Edit -> EditSheet(view, appState, dismiss)
        is TradeSheet.Remove -> RemoveSheet(view, appState, dismiss)
    }
}

/** Record the sale: the price the user actually got, and the day they got it. */
@Composable
private fun SellSheet(view: PositionView, appState: AppState, onDismiss: () -> Unit) {
    val colors = LocalNextColors.current
    var price by remember { mutableStateOf(view.currentPrice?.let(::plainNumber).orEmpty()) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var pickingDate by remember { mutableStateOf(false) }
    val parsedPrice = price.toDoubleOrNull()
    val ready = parsedPrice != null && parsedPrice > 0

    if (pickingDate) {
        NextDateSheet(
            kicker = "Sold on",
            initial = date,
            onPick = { date = it },
            onDismiss = { pickingDate = false },
            earliest = view.position.entryDate,
            latest = LocalDate.now(),
        )
        return
    }

    NextModal(
        kicker = "Record a sale · ${view.ticker}",
        tone = colors.accent,
        onDismiss = onDismiss,
        meta = "bought ${formatDay(view.position.entryDate)}",
        body = {
            NextText(
                "This records what you got for it. The channel's score is untouched — it is graded " +
                    "on the call it printed, not on your execution.",
                NextType.name,
                colors.ink2,
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(NextMetrics.space4),
            ) {
                NextField(
                    label = "Sold at",
                    value = price,
                    onValueChange = { price = it },
                    modifier = Modifier.weight(1f),
                    problem = if (price.isNotBlank() && parsedPrice == null) "not a price" else null,
                )
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(NextMetrics.space2),
                ) {
                    NextLabel("On", color = colors.figMuted)
                    NextButton(
                        label = formatFullDay(date),
                        onClick = { pickingDate = true },
                        tone = colors.rule,
                        labelColor = colors.ink,
                        minHeight = 40.dp,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            NextModalDivider()
            Column(verticalArrangement = Arrangement.spacedBy(NextMetrics.space3)) {
                NextConsequence(
                    mark = "=",
                    markTone = colors.market,
                    what = "Bought at ${formatPrice(view.position.entryPrice)}",
                    where = formatDay(view.position.entryDate),
                )
                NextConsequence(
                    mark = "→",
                    markTone = colors.accent,
                    what = "Return becomes realized",
                    where = parsedPrice?.let {
                        formatPercent((it - view.position.entryPrice) / view.position.entryPrice * 100)
                    } ?: DASH,
                )
            }
        },
        actions = {
            NextButton(
                label = "Record the sale",
                onClick = {
                    parsedPrice?.let { amount ->
                        appState.recordSale(view.position, amount, date)
                        onDismiss()
                    }
                },
                tone = colors.accent,
                fill = NextFill.SOLID,
                enabled = ready,
                modifier = Modifier.weight(1f),
            )
            NextButton("Cancel", onDismiss, tone = colors.rule, labelColor = colors.ink3)
        },
    )
}

/**
 * Change what was recorded.
 *
 * Every field states what it was beside what it will be, because this is the one sheet that
 * rewrites history: the figures on the card are recomputed from these, and a fill price typed in
 * error is indistinguishable afterwards from a bad trade.
 */
@Composable
private fun EditSheet(view: PositionView, appState: AppState, onDismiss: () -> Unit) {
    val colors = LocalNextColors.current
    val position = view.position
    var price by remember { mutableStateOf(plainNumber(position.entryPrice)) }
    var date by remember { mutableStateOf(position.entryDate) }
    var window by remember { mutableStateOf(position.windowSessions.toString()) }
    var pickingDate by remember { mutableStateOf(false) }

    val parsedPrice = price.toDoubleOrNull()
    val parsedWindow = window.toIntOrNull()
    val ready = parsedPrice != null && parsedPrice > 0 && parsedWindow != null && parsedWindow > 0

    if (pickingDate) {
        NextDateSheet(
            kicker = "Bought on",
            initial = date,
            onPick = { date = it },
            onDismiss = { pickingDate = false },
            latest = LocalDate.now(),
        )
        return
    }

    NextModal(
        kicker = "Edit trade · ${view.ticker}",
        tone = colors.market,
        onDismiss = onDismiss,
        meta = "called ${formatDay(position.recommendationDate)}",
        body = {
            WasNow("Bought at", formatPrice(position.entryPrice), price)
            NextField("Bought at", price, { price = it })
            WasNow("Bought on", formatDay(position.entryDate), formatDay(date))
            NextButton(
                label = formatFullDay(date),
                onClick = { pickingDate = true },
                tone = colors.rule,
                labelColor = colors.ink,
                minHeight = 40.dp,
                modifier = Modifier.fillMaxWidth(),
            )
            WasNow(
                "Deadline",
                "${position.windowSessions} ${position.windowSessions.sessionWord()}",
                "$window sessions",
            )
            NextField("Window sessions", window, { window = it })
            NextModalDivider()
            NextText(
                "Editing your fill recomputes this position only. Moving the window can close a " +
                    "running trade, or reopen one the deadline had closed.",
                NextType.meta,
                colors.ink3,
            )
        },
        actions = {
            NextButton(
                label = "Save changes",
                onClick = {
                    val amount = parsedPrice
                    val sessions = parsedWindow
                    if (amount != null && sessions != null) {
                        // reprice rather than re-recording the purchase: it corrects the fill and
                        // the deadline and leaves the call and any recorded sale alone, which is
                        // exactly what an edit means here.
                        appState.reprice(position, amount, date, sessions)
                        onDismiss()
                    }
                },
                tone = colors.market,
                fill = NextFill.SOLID,
                enabled = ready,
                modifier = Modifier.weight(1f),
            )
            NextButton("Discard", onDismiss, tone = colors.rule, labelColor = colors.ink3)
        },
    )
}

/** Take the trade off the record. The call it was made against is untouched. */
@Composable
private fun RemoveSheet(view: PositionView, appState: AppState, onDismiss: () -> Unit) {
    val colors = LocalNextColors.current
    NextModal(
        kicker = "Remove this position",
        tone = colors.stop,
        onDismiss = onDismiss,
        title = "${view.ticker} · bought ${formatDay(view.position.entryDate)}",
        body = {
            NextText(
                "This removes your trade. The call it was made against stays in the record, and " +
                    "the channel's score does not move either way.",
                NextType.name,
                colors.ink2,
            )
            NextModalDivider()
            NextConsequence(
                mark = "−",
                markTone = colors.stop,
                what = "This trade and its ${formatPercent(view.returnPct)} return",
                where = "Portfolio",
            )
            NextConsequence(
                mark = "=",
                markTone = colors.target,
                what = "The recommendation it was taken from",
                where = "kept",
            )
        },
        actions = {
            NextButton(
                label = "Remove it",
                onClick = {
                    appState.deletePosition(view.position)
                    onDismiss()
                },
                tone = colors.stop,
                fill = NextFill.SOLID,
                modifier = Modifier.weight(1f),
            )
            NextButton("Keep it", onDismiss, tone = colors.rule, labelColor = colors.ink3)
        },
    )
}

/** The old value struck through beside the new one, which is what makes an edit reviewable. */
@Composable
private fun WasNow(label: String, was: String, now: String) {
    val colors = LocalNextColors.current
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(NextMetrics.space3),
    ) {
        NextLabel(label, color = colors.figMuted, modifier = Modifier.weight(1f))
        NextText(was, NextType.meta, colors.ink3)
        NextText("→", NextType.meta, colors.market)
        NextText(now.ifBlank { DASH }, NextType.meta, colors.ink)
    }
}

/** A number as a field wants it: no thousands separators, no currency, no trailing noise. */
private fun plainNumber(value: Double): String {
    val rounded = Math.round(value * 1000.0) / 1000.0
    return if (rounded == rounded.toLong().toDouble()) {
        rounded.toLong().toString()
    } else {
        rounded.toString()
    }
}

