package com.ikverse.egxanalyzer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ikverse.egxanalyzer.model.PortfolioGroup
import com.ikverse.egxanalyzer.model.PortfolioStats
import com.ikverse.egxanalyzer.model.PositionStatus
import com.ikverse.egxanalyzer.model.PositionView
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * What the user actually bought, and what it has done since.
 *
 * The other tabs are readings of the market; this is the only one holding anything of the user's
 * own, so every figure on it is measured from the prices they paid rather than from the levels a
 * channel printed. Positions are grouped by the session their recommendation was for, because that
 * is what dates them - not when the user got round to buying.
 */
@Composable
internal fun PortfolioScreen(appState: AppState) {
    val scope = rememberCoroutineScope()
    val portfolio = appState.portfolio

    Screen(
        title = "Portfolio",
        onRefresh = { scope.launch { appState.refreshPrices() } },
        refreshing = appState.pricesRefreshing,
    ) {
        if (portfolio.isEmpty) {
            EmptyState(
                icon = Icons.Outlined.AccountBalanceWallet,
                title = "No trades recorded yet",
                detail = "Press Bought on a recommendation - on its card in Results, or in the " +
                    "sheet a table row opens - and the position appears here with its deadline, " +
                    "its targets and its running profit.",
            )
            return@Screen
        }

        PortfolioSummary(portfolio.stats)

        PositionSection(
            title = "Open positions",
            empty = "Nothing open. Every trade you recorded has been closed.",
            groups = portfolio.open,
            // Open positions are the ones a decision might still be made about, so they are never
            // folded away behind a header.
            collapsible = false,
            appState = appState,
        )
        PositionSection(
            title = "Closed positions",
            empty = "Nothing closed yet. Positions land here when you sell, or when the " +
                "recommendation's sessions run out.",
            groups = portfolio.closed,
            collapsible = true,
            appState = appState,
        )
    }
}

/**
 * The record as a handful of figures.
 *
 * Percentages rather than pounds, and labelled as averages, because no trade size is recorded: a
 * total in money would be a number the app has no way of knowing. Adding sizes later fills these in
 * without moving anything on the screen.
 */
@Composable
private fun ColumnScope.PortfolioSummary(stats: PortfolioStats) {
    SectionCard(title = "Your record", icon = Icons.Outlined.AccountBalanceWallet) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Space.xl),
            verticalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            StatTile(stats.openCount.toString(), "open", tone = MaterialTheme.colorScheme.primary)
            StatTile(stats.closedCount.toString(), "closed")
            StatTile(
                formatPercent(stats.realizedReturnPct),
                "average closed",
                tone = PriceRole.forReturn(stats.realizedReturnPct),
            )
            StatTile(
                formatPercent(stats.openReturnPct),
                "average open",
                tone = PriceRole.forReturn(stats.openReturnPct),
            )
            StatTile(
                formatPercent(stats.winRate, signed = false),
                "win rate",
                tone = if ((stats.winRate ?: 0.0) >= 50.0) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
        stats.best?.let { best ->
            Text(
                "Best ${best.ticker} ${formatPercent(best.returnPct)}" +
                    (stats.worst?.let { " · worst ${it.ticker} ${formatPercent(it.returnPct)}" } ?: ""),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * One half of the screen: open or closed, grouped by the session each call was made for.
 *
 * Closed groups fold away, exactly as a settled analysis does in Insights - the record stays
 * readable however many sessions have been traded - while open ones stay put.
 */
@Composable
private fun ColumnScope.PositionSection(
    title: String,
    empty: String,
    groups: List<PortfolioGroup>,
    collapsible: Boolean,
    appState: AppState,
) {
    Text(title, style = MaterialTheme.typography.titleLarge)
    if (groups.isEmpty()) {
        Text(
            empty,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    groups.forEach { group ->
        if (collapsible) {
            ExpandableSection(
                title = group.recommendationDate.toString(),
                icon = Icons.Outlined.AccountBalanceWallet,
                summary = group.summary(),
                summaryTone = PriceRole.forReturn(group.averageReturnPct),
            ) {
                PositionGrid(group, appState)
            }
        } else {
            SectionCard(title = group.recommendationDate.toString()) {
                Text(
                    group.summary(),
                    style = MaterialTheme.typography.bodySmall,
                    color = PriceRole.forReturn(group.averageReturnPct),
                )
                PositionGrid(group, appState)
            }
        }
    }
}

/** One line saying what a session's trades came to, so a folded group still informs. */
private fun PortfolioGroup.summary(): String =
    "${positions.size} ${if (positions.size == 1) "position" else "positions"} · " +
        "average ${formatPercent(averageReturnPct)}"

@Composable
private fun ColumnScope.PositionGrid(group: PortfolioGroup, appState: AppState) {
    BoxWithConstraints {
        val columns = responsiveColumns(minColumnWidth = PositionCardMinWidth, maxColumns = 2)
        Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
            ResponsiveRows(group.positions, columns) { view, cardModifier ->
                PositionCard(
                    view = view,
                    onSell = { price, date -> appState.recordSale(view.position, price, date) },
                    onEditEntry = { price, date -> appState.reprice(view.position, price, date) },
                    onRemove = { appState.deletePosition(view.position) },
                    modifier = cardModifier,
                )
            }
        }
    }
}

/**
 * A position card answers three questions in order: what did I buy, where is it now, and how long
 * is left. The status outline says the third at a glance before any of it is read.
 */
@Composable
private fun PositionCard(
    view: PositionView,
    onSell: (Double, LocalDate) -> Unit,
    onEditEntry: (Double, LocalDate) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val position = view.position
    var menuOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }

    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        border = heldBorder(view),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(Space.m), verticalArrangement = Arrangement.spacedBy(Space.s)) {
            // A fixed two lines for the name, so a company whose name wraps does not make its card
            // taller than the one beside it.
            Row(Modifier.heightIn(min = PositionHeaderHeight), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(position.ticker, style = MaterialTheme.typography.titleSmall)
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
                            text = { Text("Edit entry") },
                            leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                            onClick = { menuOpen = false; editing = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Remove") },
                            leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                            onClick = { menuOpen = false; confirmRemove = true },
                        )
                    }
                }
            }

            Text(
                listOfNotNull(
                    position.channel?.takeIf(String::isNotBlank),
                    "called ${position.recommendationDate}",
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            FigureRow(
                listOf(
                    Cell("Entry", formatPrice(position.entryPrice), PriceRole.entry),
                    Cell("Entry date", position.entryDate.toString(), PriceRole.muted),
                    Cell("Market", formatPrice(view.currentPrice), PriceRole.market),
                    Cell(
                        if (view.realized) "Return" else "Return so far",
                        formatPercent(view.returnPct),
                        PriceRole.forReturn(view.returnPct),
                    ),
                ),
            )
            FigureRow(
                listOf(
                    Cell("Target 1", formatPrice(position.target1), PriceRole.target),
                    Cell("Target 2", formatPrice(position.target2), PriceRole.target),
                    Cell("Stop loss", formatPrice(position.stopLoss), PriceRole.stop),
                    Cell("Deadline", view.deadline(), PriceRole.muted),
                ),
            )

            HorizontalDivider()

            Text(
                view.profitLine(),
                style = MaterialTheme.typography.bodySmall,
                color = PriceRole.forReturn(view.returnPct),
            )
            // Selling early is the point of the button, so it stays available for as long as no
            // sale has been recorded - including on a position the deadline closed while the user
            // was still holding it.
            if (view.awaitingSale) {
                SellButton(
                    suggestedExit = view.currentPrice,
                    onSell = onSell,
                    modifier = Modifier.padding(top = Space.xs),
                )
            }
        }
    }

    if (editing) {
        TradeDialog(
            title = "Edit the entry",
            explanation = "Corrects what this trade was recorded at. Everything the position " +
                "reports is measured from it.",
            priceLabel = "Entry price",
            dateLabel = "Entry date",
            confirmLabel = "Save",
            initialPrice = position.entryPrice,
            onDismiss = { editing = false },
            onConfirm = { price, date ->
                editing = false
                onEditEntry(price, date)
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
 * does not move at the weekend, and the user's entry date never enters into it.
 */
private fun PositionView.deadline(): String = deadlineDate?.let { "passed $it" }
    ?: "$sessionsRemaining of ${position.windowSessions} left"

/**
 * The one line that says what the position is worth, and how much of that is a fact.
 *
 * A closed position the user reported selling is realized; everything else is an estimate, and
 * saying which is which matters more than the figure itself.
 */
private fun PositionView.profitLine(): String {
    val amount = formatPercent(returnPct)
    val at = formatPrice(exitPrice)
    return when {
        realized -> "Realized $amount · sold at $at" +
            (position.exitDate?.let { " on $it" } ?: "")
        open -> "Estimated $amount · marked at $at"
        status == PositionStatus.STOPPED_OUT -> "Estimated $amount · stopped at $at"
        status == PositionStatus.FULL_TARGET_HIT -> "Estimated $amount · target reached at $at"
        else -> "Estimated $amount · closed at $at"
    } + if (marketStatus != status) " · the call itself: ${marketStatus.label.lowercase()}" else ""
}

/** One labelled figure, matching how Insights lays a call's numbers out. */
private data class Cell(val label: String, val value: String, val tone: Color)

/**
 * Four figures across, or two when the width cannot take four.
 *
 * The same rule the Insights cards use: a cover screen has height and no width, so wrapping beats
 * shrinking - four columns at 443dp truncates every price they are supposed to show.
 */
@Composable
private fun FigureRow(cells: List<Cell>) {
    BoxWithConstraints {
        val perRow = if (maxWidth >= FourFiguresWidth) cells.size else 2
        Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
            cells.chunked(perRow).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                    row.forEach { cell ->
                        Column(Modifier.weight(1f)) {
                            Text(
                                cell.label.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                cell.value,
                                style = MaterialTheme.typography.bodyMedium,
                                color = cell.tone,
                            )
                        }
                    }
                    repeat(perRow - row.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
    }
}

private val FourFiguresWidth = 420.dp

/** Ticker plus two lines of company name, so every position card starts the same height. */
private val PositionHeaderHeight = 52.dp

/**
 * What a position needs before two share a row.
 *
 * Set against the 750dp an unfolded Fold actually reports, not the 851dp an emulator claims: after
 * the rail and the page padding a generous-looking minimum quietly gives one column on the device
 * this was written for.
 */
private val PositionCardMinWidth = 300.dp
