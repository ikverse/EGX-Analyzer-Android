package com.ikverse.egxanalyzer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ikverse.egxanalyzer.model.PositionStatus
import com.ikverse.egxanalyzer.model.PositionView
import java.time.LocalDate

/**
 * A position card answers three questions in order: what did I buy, where is it now, and how long
 * is left. The status outline says the third at a glance before any of it is read.
 *
 * The prices are drawn before they are listed. Eight figures at equal weight said what every level
 * was and nothing about how they stood against each other - whether the stop was a whisker away or
 * a mile off, whether the price had crept most of the way to target 1 or none of it. The ladder is
 * the drawing Results and Insights already use for the same five levels, with the one difference
 * that is the point of this screen: the entry mark is the price the user actually paid rather than
 * the band the channel printed, and every percentage under the figures is measured from it.
 *
 * Then two groups rather than two unlabelled rows - what the trade is, and where it stands. The
 * split is what lets each figure carry a line of its own underneath, a distance from the entry or
 * the session a high was set on, without the card reading as a table of ten loose numbers.
 */
@Composable
internal fun PositionCard(
    view: PositionView,
    /** Opens the call this trade was taken on, in Insights. Absent once its analysis is gone. */
    onOpenCall: (() -> Unit)?,
    highlighted: Boolean,
    onHighlightShown: () -> Unit,
    onSell: (Double, LocalDate) -> Unit,
    onEditTrade: (Double, LocalDate, Int?) -> Unit,
    onKeepOpen: (keep: Boolean, note: String?) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val position = view.position
    var menuOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }

    val colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
    // The arrival flash takes the edge for as long as it runs, then the status outline has it back.
    val border = arrivalFlash(highlighted, onHighlightShown) ?: heldBorder(view)
    val body: @Composable ColumnScope.() -> Unit = {
        Column(Modifier.padding(Space.m), verticalArrangement = Arrangement.spacedBy(Space.s)) {
            // A fixed two lines for the name, so a company whose name wraps does not make its card
            // taller than the one beside it.
            Row(Modifier.heightIn(min = PositionHeaderHeight), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StockLogo(position.ticker, LogoSize.Row, Modifier.padding(end = Space.s))
                        Text(position.ticker, style = MaterialTheme.typography.titleSmall)
                    }
                    listOfNotNull(position.companyArabic, position.companyEnglish)
                        .filter(String::isNotBlank)
                        .distinct()
                        .takeIf(List<String>::isNotEmpty)
                        ?.let {
                            Text(
                                it.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                }
                PositionStatusChip(view)
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "More actions")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Edit trade") },
                            leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                            onClick = { menuOpen = false; editing = true },
                        )
                        // Undoing Keep Open lives here rather than beside Sold. The pill already
                        // says the trade is being kept open, and a button repeating it took the
                        // place where the user looks for the one action that ends a position. It
                        // has to stay reachable somewhere, though: without it a mistaken press
                        // could only be undone by deleting the trade and recording it again.
                        if (view.keptOpen) {
                            DropdownMenuItem(
                                text = { Text("Follow the deadline again") },
                                leadingIcon = {
                                    Icon(Icons.Outlined.HourglassEmpty, contentDescription = null)
                                },
                                onClick = { menuOpen = false; onKeepOpen(false, null) },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Remove") },
                            leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                            onClick = { menuOpen = false; confirmRemove = true },
                        )
                    }
                }
            }

            // The line that names the call, which is exactly what a press on this card opens. The
            // date is the app's own short form rather than the raw ISO one this line used to
            // print: the tiles on the Overdue card above already date a trade "14 Aug", and this
            // was the only card in the app where two dates disagreed about how to look.
            Row(
                horizontalArrangement = Arrangement.spacedBy(Space.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    listOfNotNull(
                        position.channel?.takeIf(String::isNotBlank),
                        "called ${shortDate(position.recommendationDate)}",
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    // fill = false so the arrow sits against the end of the line rather than out at
                    // the card's edge, where it would read as unrelated to it.
                    modifier = Modifier.weight(1f, fill = false),
                )
                // The one hint that the card leads somewhere: a whole card being pressable is
                // invisible otherwise.
                if (onOpenCall != null) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowForward,
                        // The press is described where it is declared; a reader announcing the
                        // glyph as well would say it twice.
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(IconSize.Inline),
                    )
                }
            }

            // Its own row rather than the header, which is held to a fixed height so cards beside
            // each other start level. Drawn only when there is something to say, so an ordinary
            // position is exactly as tall as it was.
            if (view.overdue || view.keptOpen) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Space.s),
                    verticalArrangement = Arrangement.spacedBy(Space.xs),
                ) {
                    if (view.overdue) OverdueChip(view.overdueDays)
                    // One chip, not two saying the same thing: a trade can only be overdue by being
                    // kept open now, so Overdue already carries the state and adds how late it is.
                    // The instruction the chip also held is not lost - the Sell button below is on
                    // the card for as long as no sale has been recorded.
                    if (view.keptOpen && !view.overdue) KeptOpenChip()
                    if (view.priceScaleChanged) PriceScaleChip()
                }
                position.keepOpenNote?.takeIf(String::isNotBlank)?.let { why ->
                    Text(
                        why,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            PriceLadder(
                stopLoss = position.stopLoss,
                // One price, not a band. The trade opened where it opened, and the drawing keeps a
                // zero-width band visible rather than losing the mark; the levels either side of it
                // are still the call's, which is what makes the picture worth reading at all.
                entryLow = position.entryPrice,
                entryHigh = position.entryPrice,
                target1 = position.target1,
                target2 = position.target2,
                // The arrow is where this trade stands: today's close while it runs, and where it
                // ended once it has. Nothing is plotted across a change of scale - the levels are
                // quoted in the old money and the price in the new, so the arrow would point at a
                // place on the axis that does not exist.
                reached = if (view.priceScaleChanged) null else view.exitPrice ?: view.currentPrice,
            )

            FigureGroup(
                // On the heading rather than in a figure of its own, because it is not a fifth
                // level - it is what the four below come to. Worked out from the price actually
                // paid, which is what makes it a different figure from the one Insights prints for
                // the same call: buying above the band buys a worse trade out of the same advice.
                "Your trade" + (view.riskReward?.let { " · risk : reward ${it.asRatio()}" } ?: ""),
                listOf(
                    {
                        Figure(
                            "Entry",
                            formatPrice(position.entryPrice),
                            Modifier.weight(1f),
                            tone = PriceRole.entry,
                            // The one date that says how long the trade has actually been held.
                            // The session this card sits under is titled by the call's date, and on
                            // a trade bought late the two are not the same day.
                            caption = "bought ${shortDate(position.entryDate)}",
                        )
                    },
                    // A price on its own says nothing across stocks: 7.95 is a wide stop on one
                    // share and a tight one on another, and the distance is the half that compares.
                    // Measured from what was paid, so it is the room this trade actually has left.
                    {
                        Figure(
                            "Stop loss",
                            formatPrice(position.stopLoss),
                            Modifier.weight(1f),
                            tone = PriceRole.stop,
                            caption = view.fromEntry(position.stopLoss).distance(),
                        )
                    },
                    {
                        Figure(
                            "Target 1",
                            formatPrice(position.target1),
                            Modifier.weight(1f),
                            tone = PriceRole.target,
                            caption = view.fromEntry(position.target1).distance(),
                        )
                    },
                    {
                        Figure(
                            "Target 2",
                            formatPrice(position.target2),
                            Modifier.weight(1f),
                            tone = PriceRole.target,
                            caption = view.fromEntry(position.target2).distance(),
                        )
                    },
                ),
            )
            // The heading below is a boundary the eye loses once a group wraps onto three rows, so
            // the two groups are ruled apart, exactly as they are on the call card in Insights.
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            FigureGroup(
                "Where it stands",
                listOf(
                    {
                        Figure(
                            if (view.realized) "Return" else "Return so far",
                            formatPercent(view.returnPct),
                            Modifier.weight(1f),
                            tone = PriceRole.forReturn(view.returnPct),
                        )
                    },
                    {
                        Figure(
                            // "Last close" rather than "Market". The daily feed settles once a day
                            // and can be several sessions behind on a phone that has not
                            // refreshed; undated, the figure claimed to be today's, which is the
                            // one thing it is not.
                            "Last close",
                            formatPrice(view.currentPrice),
                            Modifier.weight(1f),
                            tone = PriceRole.market,
                            // Through the card's own short date rather than the shared `on` slot,
                            // so every date on this card follows one rule: the year comes back for
                            // a session in another one, and stays away for all the rest.
                            caption = view.currentPriceOn?.let(::shortDate),
                        )
                    },
                    // How far the trade actually got, which this card could not say at all. The
                    // scorer has always worked both out across the held sessions and they were
                    // thrown away, so a trade that ran up 7% and gave it all back read exactly
                    // like one that never moved.
                    {
                        Figure(
                            "Peak since entry",
                            formatPrice(view.peakSinceEntry),
                            Modifier.weight(1f),
                            tone = PriceRole.market,
                            caption = view.extremeCaption(view.peakSinceEntry, view.peakOn),
                        )
                    },
                    {
                        Figure(
                            "Trough since entry",
                            formatPrice(view.troughSinceEntry),
                            Modifier.weight(1f),
                            tone = PriceRole.market,
                            caption = view.extremeCaption(view.troughSinceEntry, view.troughOn),
                        )
                    },
                    {
                        Figure(
                            "Deadline",
                            view.deadline(),
                            Modifier.weight(1f),
                            tone = PriceRole.muted,
                            // Sessions held, not sessions elapsed: the deadline counts from the
                            // call and this counts from the entry, and on a trade bought late the
                            // two are answering different questions about one window.
                            caption = "${view.sessionsHeld} ${view.sessionsHeld.sessionWord()} held",
                            // Prose rather than a price. "3 of 10 left" in monospaced digits sets
                            // it apart from the figures beside it for no reason.
                            valueStyle = MaterialTheme.typography.bodyMedium,
                        )
                    },
                ),
            )

            HorizontalDivider()

            Text(
                view.profitLine(),
                style = MaterialTheme.typography.bodySmall,
                // The figure this line used to carry is a figure above it now, and the colour went
                // with it. What is left says whether the return is a fact or an estimate and where
                // it was struck, and that is not in itself good news or bad.
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Selling early is the point of the button, so it stays available for as long as no
            // sale has been recorded - including on a trade that reached target 2, where recording
            // what the user actually got out at turns an estimate into a fact, and on one the
            // deadline closed while they were still holding it.
            if (view.awaitingSale) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Space.s),
                    verticalArrangement = Arrangement.spacedBy(Space.xs),
                    modifier = Modifier.padding(top = Space.xs),
                ) {
                    // The estimate rather than today's close. While the trade is open the two are
                    // the same thing; once the deadline has closed it, today's price is the least
                    // likely figure the user sold at, and the estimate is already marked at the
                    // stop, the target, or the last close of the window.
                    SellButton(
                        suggestedExit = view.exitPrice ?: view.currentPrice,
                        onSell = onSell,
                    )
                    // Not on a trade already being kept open - the pill says that, and the menu
                    // undoes it - and not on one that reached target 2, which is the single ending
                    // Keep Open cannot argue with.
                    if (!view.keptOpen && !view.finished) KeepOpenButton(onKeepOpen = onKeepOpen)
                }
            }
        }
    }

    // Two overloads over one body rather than a clickable wrapped round the card: Material's own
    // pressable card is what keeps the ripple inside the corners, and a trade whose call is gone
    // must not answer a press at all. The menu, Sold and Keep Open take their own taps as before.
    if (onOpenCall == null) {
        Card(
            modifier.fillMaxWidth(),
            colors = colors,
            border = border,
            shape = MaterialTheme.shapes.medium,
            content = body,
        )
    } else {
        Card(
            onClick = onOpenCall,
            // A pressable card announces itself as "activate" and nothing more, which says nothing
            // about where the press goes. The action itself is Material's; only its name is ours.
            modifier = modifier.fillMaxWidth()
                .semantics { onClick(label = "Open this call in Insights", action = null) },
            colors = colors,
            border = border,
            shape = MaterialTheme.shapes.medium,
            content = body,
        )
    }

    if (editing) {
        TradeDialog(
            title = "Edit the trade",
            explanation = "Corrects what this trade was recorded at, and how long it runs. " +
                "Everything the position reports is measured from the entry; changing the window " +
                "moves the deadline, so it can close a running trade or reopen a finished one.",
            priceLabel = "Entry price",
            dateLabel = "Entry date",
            confirmLabel = "Save",
            initialPrice = position.entryPrice,
            initialWindow = position.windowSessions,
            windowHelp = "Trading sessions from ${position.recommendationDate}, the session this " +
                "call was made for.",
            onDismiss = { editing = false },
            onConfirm = { price, date, window ->
                editing = false
                onEditTrade(price, date, window)
            },
        )
    }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove this position?") },
            text = {
                Text(
                    "It stops being counted in your portfolio. The analysis it came from is not " +
                        "touched, so the recommendation itself stays where it is.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRemove = false
                        onRemove()
                    },
                ) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Keep") } },
        )
    }
}

/**
 * How long the recommendation has left, counted in the sessions it is judged in.
 *
 * Trading sessions rather than days, and counted from the session the call was made for: a stock
 * does not move at the weekend, and the user's entry date never enters into it. A window the user
 * set themselves says so here rather than in a chip of its own - the figure it qualifies is this
 * one, and a reader wondering why a trade has fifteen sessions is already looking at it.
 */
private fun PositionView.deadline(): String {
    val left = deadlineDate?.let { "passed ${shortDate(it)}" }
        ?: "$sessionsRemaining of ${position.windowSessions} left"
    return if (position.windowCustom) "$left · custom" else left
}

/**
 * A high or a low, dated and measured from what was paid.
 *
 * Both halves, or the figure says half of it. The date alone leaves the reader dividing two prices
 * to find out whether the peak was ever worth taking; the percentage alone leaves them wondering
 * whether it happened last week or on the first morning.
 */
private fun PositionView.extremeCaption(price: Double?, on: LocalDate?): String? {
    if (price == null) return null
    return listOfNotNull(on?.let(::shortDate), fromEntry(price)?.let { formatPercent(it) })
        .joinToString(" · ")
        .ifBlank { null }
}

/**
 * Says why a trade is still running, on the trades where Overdue is not already saying it.
 *
 * Only up before the deadline passes: after it, Overdue means kept open and this would be the same
 * fact twice on one row. The instruction in it is the point while it is up: the way to end a trade
 * being kept open is to sell it, and the card's menu holds the way to hand it back to its deadline
 * instead.
 */
@Composable
private fun KeptOpenChip() {
    OutlinePill(
        "Keep open · sell to close",
        outline = MaterialTheme.colorScheme.tertiary,
        textColor = MaterialTheme.colorScheme.onTertiaryContainer,
    )
}

/**
 * The one line that says how much of the return above it is a fact, and where it was struck.
 *
 * A closed position the user reported selling is realized; everything else is an estimate, and
 * saying which is which matters more than the figure itself - which is why the figure is no longer
 * here. It was printed twice, once as Return and again in this line, and one number in two places
 * on one card is a number the reader checks against itself.
 *
 * Every ending now carries the day it happened on. "Stopped at 7.95" left the reader to guess
 * whether that was a fortnight ago or this morning, and the session was something the scorer had
 * known all along.
 */
private fun PositionView.profitLine(): String {
    val at = formatPrice(exitPrice)
    val line = when {
        realized -> "Realized · sold at $at" + position.exitDate.dated()
        open -> "Estimated · marked at $at" + currentPriceOn.dated()
        status == PositionStatus.STOPPED_OUT -> "Estimated · stopped at $at" + settledOn.dated()
        status == PositionStatus.FULL_TARGET_HIT ->
            "Estimated · target reached at $at" + settledOn.dated()
        else -> "Estimated · expired at $at" + deadlineDate.dated()
    }
    val disagrees = marketStatus != status
    return line + if (disagrees) " · the call itself: ${marketStatus.label.lowercase()}" else ""
}

/** " on 14 Aug", or nothing at all where the session behind a price was never recorded. */
private fun LocalDate?.dated(): String = this?.let { " on ${shortDate(it)}" }.orEmpty()

/** Ticker plus two lines of company name, so every position card starts the same height. */
private val PositionHeaderHeight = 52.dp
