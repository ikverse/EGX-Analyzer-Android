package com.ikverse.egxanalyzer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.ikverse.egxanalyzer.model.FeedFault
import com.ikverse.egxanalyzer.model.PerformanceCalculator
import com.ikverse.egxanalyzer.model.CallTally
import com.ikverse.egxanalyzer.model.LatestPrice
import com.ikverse.egxanalyzer.model.PositionView
import com.ikverse.egxanalyzer.model.ScoredCall
import com.ikverse.egxanalyzer.model.ScoredSession
import com.ikverse.egxanalyzer.model.Scoring
import com.ikverse.egxanalyzer.model.StockScore
import com.ikverse.egxanalyzer.model.positionId
import java.util.Locale

/**
 * How a ticker anywhere in the app opens [StockSheet].
 *
 * A composition local rather than a callback threaded down, and that is a deliberate exception to
 * how this app passes actions around. `onOpenTrade` and `onOpenCall` are threaded because they are
 * carried one or two levels and belong to the card that offers them. A ticker is different: it is
 * drawn on a call card inside a session card inside a band inside Insights, on the same card again
 * from Results, on a position card inside a section inside a card on the Portfolio, and in a table
 * row - and none of those intermediate composables has any business knowing about a stock sheet.
 * Threading it would add a parameter to eight signatures to reach four leaves.
 *
 * The shell provides it beside [LocalWindowWidth] and `LocalNavBarVisible`, which is where the app
 * already publishes the things every screen may need and no screen owns. The default is a no-op so
 * a preview, or anything composed outside the shell, draws a ticker that simply does nothing rather
 * than failing to compose.
 */
internal val LocalOpenStock = staticCompositionLocalOf<(String) -> Unit> { {} }

/**
 * Everything the app knows about one stock, in one place.
 *
 * The record has always held all of this and has never had a page for it. A ticker's story was
 * spread across four screens - the runs that named it on Results, the calls and their verdicts on
 * Insights, the trade on the Portfolio, the feed's opinion of it in Settings - and the only way to
 * gather it was to type the same ticker into three search boxes. [StockSearch] already made those
 * three boxes ask one question; this is where the answers finally meet.
 *
 * **A sheet and not a sixth destination**, on `ChannelScoreSheet`'s terms - same padding, same
 * scroll, same skipped partial state. Three reasons, and the first is the one that decided it: a
 * stock is not a peer of Analyze and Settings, it is the longer version of a thing that was
 * pressed, which is exactly what a sheet from the bottom already means in this app. It also keeps
 * back simple, because Compose's own `ModalBottomSheet` takes the press before [NavStack] ever sees
 * it. And it can be opened from any tab without moving the reader off the one they are reading.
 *
 * **It states nothing new.** Every figure here is drawn somewhere else already; what this adds is
 * that they are drawn together. The one thing that had been computed and never shown at all is
 * [StockScore], which reached the reader only through the shortlist signals and the Ask AI prompt -
 * so "what happens when anybody recommends this stock" was a question the app could answer and no
 * screen asked.
 *
 * Every row that leads somewhere leads through [AppState.openPosition] or [AppState.openCall], the
 * two entrances every other cross-tab press already uses, so this cannot become a third, quieter
 * way of finding a trade. Each dismisses the sheet first: a sheet left open over the tab it just
 * sent the reader to is covering the card it sent them to read.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StockSheet(ticker: String, appState: AppState, onDismiss: () -> Unit) {
    // Normalized once. COMI and COMI.CA are one stock everywhere else in the app - the stock score
    // is grouped on the normalized ticker for exactly this reason - and a sheet that matched the
    // raw string would show half a record and look like the rest had been lost.
    val key = remember(ticker) { Scoring.normalizeTicker(ticker) }
    val report = appState.performance
    val score = remember(report, key) { report.stocks.firstOrNull { it.ticker == key } }
    val latest = report.latestPrices[key]
    val calls = remember(report, key) {
        report.sessions.asSequence()
            .flatMap { session -> session.calls.asSequence() }
            .filter { Scoring.normalizeTicker(it.ticker) == key }
            .sortedByDescending(ScoredCall::openedOn)
            .toList()
    }
    val trades = remember(appState.portfolio, key) {
        appState.portfolio.positions
            .filter { Scoring.normalizeTicker(it.ticker) == key }
            .sortedByDescending { it.position.entryDate }
    }
    val faults = remember(appState.priceHealth, key) {
        appState.priceHealth.faults.firstOrNull { it.ticker == key }?.faults.orEmpty()
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .scrollableColumn()
                .padding(horizontal = Space.l)
                .padding(bottom = Space.xl),
            verticalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            StockSheetHeading(key, score, calls.firstOrNull())
            StockSheetPrice(latest, faults)
            if (score != null) StockSheetRecord(score)
            if (trades.isNotEmpty()) {
                StockSheetTrades(trades) { id ->
                    onDismiss()
                    appState.openPosition(id)
                }
            }
            if (calls.isNotEmpty()) {
                StockSheetCalls(calls) { id ->
                    onDismiss()
                    appState.openCall(id)
                }
            }
            // Absent rather than empty. A stock reaches this sheet by being on screen somewhere, so
            // "nothing is known about it" should not arise - and if it does, a column of dashes
            // says less than the sections simply not being there.
            if (score == null && trades.isEmpty() && calls.isEmpty()) {
                Text(
                    "Nothing in the record names this stock yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * The ticker, and whatever the record knows it as.
 *
 * The names come from the score where there is one and from the newest call otherwise: a stock
 * called once has no score and still has a company behind it. Both scripts, because the channels
 * print Arabic and the catalog holds English, and a reader who knows one should not have to know
 * the other.
 */
@Composable
private fun StockSheetHeading(ticker: String, score: StockScore?, newest: ScoredCall?) {
    val english = score?.companyEnglish ?: newest?.companyEnglish
    val arabic = score?.companyArabic ?: newest?.companyArabic
    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Text(
            "STOCK",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(ticker, style = MaterialTheme.typography.titleLarge)
            Egx33Badge(ticker, Modifier.padding(start = Space.s))
        }
        if (english != null) {
            Text(
                english,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (arabic != null) {
            // A first-strong isolate, as every Arabic name in this app carries: without it a digit
            // in the name drifts to the wrong end of it.
            Text(
                "⁨$arabic⁩",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Where the stock is now, and whether that figure can be trusted.
 *
 * The same pair the call card draws, for the same reason: a close with no session date behind it is
 * half a fact, and a feed that has gone quiet looks exactly like a stock that has not moved. The
 * faults answer the second - they are computed on every recompute and, until this sheet, reached
 * the reader only in Settings, a screen away from the stock they are about. [FeedFault.detail] is
 * the short form the record already keeps; the longer wording belongs to the Settings card, which
 * has the room for it and a count to put it against.
 */
@Composable
private fun StockSheetPrice(latest: LatestPrice?, faults: Set<FeedFault>) {
    if (latest == null && faults.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
        if (latest != null) {
            FigureGroup(
                "Where it stands",
                listOf<@Composable RowScope.() -> Unit>(
                    {
                        Figure(
                            "Latest close",
                            formatPrice(latest.session.close),
                            Modifier.weight(1f),
                            tone = PriceRole.market,
                            // A session still trading says so instead of naming its own date: the
                            // close is going to move, and a date under it reads as settled.
                            caption = if (latest.provisional) "still trading" else null,
                            on = if (latest.provisional) null else latest.session.date,
                        )
                    },
                    {
                        Figure(
                            "Session high",
                            formatPrice(latest.session.high),
                            Modifier.weight(1f),
                            tone = PriceRole.muted,
                        )
                    },
                    {
                        Figure(
                            "Session low",
                            formatPrice(latest.session.low),
                            Modifier.weight(1f),
                            tone = PriceRole.muted,
                        )
                    },
                ),
            )
        }
        faults.forEach { fault ->
            Text(
                "${fault.label}. ${fault.detail}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * What has happened when anybody recommended this stock.
 *
 * The figure no screen showed. It follows [StockScore]'s own discipline, which is `ChannelScore`'s:
 * below `MINIMUM_JUDGED_TO_RANK` the numbers are reported exactly as measured and lose their
 * colour, and the words "too few to rank" are added - the same rule a call card's source line
 * follows, and for the same reason. A rate resting on two calls is not a smaller rate, it is a
 * different kind of claim.
 */
@Composable
private fun StockSheetRecord(score: StockScore) {
    val tally: CallTally = score.tally
    val thin = tally.judged < PerformanceCalculator.MINIMUM_JUDGED_TO_RANK
    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        HorizontalDivider()
        Text(
            "WHEN THIS STOCK IS RECOMMENDED",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Space.s),
        )
        // Nested, not disjoint, and read exactly as the channel cards' headline is read: reached
        // target 1 on the first figure, ran the whole way on the second.
        Text(
            formatPercent(tally.anyTargetRate, signed = false) + " / " +
                formatPercent(tally.fullHitRate, signed = false),
            style = MaterialTheme.typography.headlineSmall.copy(fontFamily = TabularFigures),
            color = if (thin) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                tally.anyTargetRate.rateTone()
            },
        )
        Text(
            "reached target 1 / target 2, over ${tally.judged} settled " +
                (if (tally.judged == 1) "call" else "calls") +
                " from ${score.sources} " +
                (if (score.sources == 1) "source" else "sources") +
                (if (thin) " — too few to rank" else ""),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FigureGroup(
            "Across every source",
            listOf<@Composable RowScope.() -> Unit>(
                {
                    Figure(
                        "Per judged call",
                        formatPercent(tally.averageReturn),
                        Modifier.weight(1f),
                        tone = if (thin) {
                            PriceRole.muted
                        } else {
                            PriceRole.forReturn(tally.averageReturn)
                        },
                    )
                },
                {
                    Figure(
                        "Risk to reward",
                        tally.averageRiskReward
                            ?.let { String.format(Locale.US, "%.1f to 1", it) }
                            ?: Dash,
                        Modifier.weight(1f),
                        tone = PriceRole.muted,
                    )
                },
                {
                    Figure(
                        "Calls made",
                        tally.calls.toString(),
                        Modifier.weight(1f),
                        tone = PriceRole.muted,
                    )
                },
            ),
        )
    }
}

/** The reader's own money in this stock, newest first, each row leading to its card. */
@Composable
private fun StockSheetTrades(trades: List<PositionView>, onOpen: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        HorizontalDivider()
        Text(
            "YOUR TRADES",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Space.s),
        )
        trades.forEach { view ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(view.position.id) }
                    .padding(vertical = Space.xs),
                horizontalArrangement = Arrangement.spacedBy(Space.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(view.status.label, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "bought ${AppDates.DayMonth.format(view.position.entryDate)} at " +
                            formatPrice(view.position.entryPrice),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    formatPercent(view.returnPct),
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = TabularFigures),
                    color = PriceRole.forReturn(view.returnPct),
                )
            }
        }
    }
}

/**
 * Every call anybody has printed on this stock, newest first.
 *
 * Re-postings are kept and marked rather than dropped, which is the rule the session cards follow:
 * the card is the record of what the channel published that morning, and only the *rates* leave it
 * out. Here that matters more than usual - this list is a history of what has been said about one
 * stock, and a source that said the same thing eleven mornings running has said something.
 */
@Composable
private fun StockSheetCalls(calls: List<ScoredCall>, onOpen: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        HorizontalDivider()
        Text(
            "EVERY CALL ON IT",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Space.s),
        )
        calls.forEach { call ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(call.positionId) }
                    .padding(vertical = Space.xs),
                horizontalArrangement = Arrangement.spacedBy(Space.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "⁨${call.channel}⁩",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        AppDates.DayMonth.format(call.openedOn) +
                            (if (call.repeatOf != null) " · re-posted" else ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        call.outcome.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        formatPercent(call.returnPct),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = TabularFigures,
                        ),
                        color = PriceRole.forReturn(call.returnPct),
                    )
                }
            }
        }
    }
}
