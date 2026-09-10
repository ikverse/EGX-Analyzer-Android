package com.ikverse.egxanalyzer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ikverse.egxanalyzer.model.DailySession
import com.ikverse.egxanalyzer.model.FeedFault
import com.ikverse.egxanalyzer.model.PerformanceCalculator
import com.ikverse.egxanalyzer.model.CallTally
import com.ikverse.egxanalyzer.model.LatestPrice
import com.ikverse.egxanalyzer.model.PositionView
import com.ikverse.egxanalyzer.model.Sale
import com.ikverse.egxanalyzer.model.ScoredCall
import com.ikverse.egxanalyzer.model.Scoring
import com.ikverse.egxanalyzer.model.StockOpinion
import com.ikverse.egxanalyzer.model.StockScore
import com.ikverse.egxanalyzer.model.opinionId
import com.ikverse.egxanalyzer.model.positionId
import java.time.LocalDate
import java.util.Locale

/**
 * How a ticker anywhere in the app opens [StockSheet].
 *
 * A composition local rather than a callback threaded down, and that is a deliberate exception to
 * how this app passes actions around. `onOpenTrade` and `onOpenCall` are threaded because they are
 * carried one or two levels and belong to the card that offers them. A ticker is different: it is
 * drawn on a call card inside a session card inside a band inside Insights, on the same card again
 * from Results, on a position card inside a section inside a card on the Portfolio, on the day's
 * event tiles on two tabs, and in a table row - and none of those intermediate composables has any
 * business knowing about a stock sheet. Threading it would add a parameter to a dozen signatures to
 * reach six leaves.
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
 * **Three fixed bands rather than one long scroll.** The heading and the price are what the sheet
 * was opened to see and they no longer scroll away; the record, the trades and the calls run
 * between them; the two things a reader does about a stock sit on the bottom edge where a thumb is
 * already resting. What was one column of dividers is now a header, a scroller and an action bar,
 * which is the shape every other screen in this app already has.
 *
 * **It states one thing that is new.** Every figure here but the line was drawn somewhere else
 * already; what this adds is that they are drawn together. [Sparkline] is the exception, and it is
 * the one question the record could always answer and no screen asked - what has this stock
 * actually been doing - drawn from sessions the app has been storing since the first refresh. The
 * other thing computed and never shown at all is [StockScore], which reached the reader only
 * through the shortlist signals and the Ask AI prompt.
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
    // Whose levels the chart draws. The reader's own trade where they hold one - those are the
    // stop and the targets they are actually running under, snapshotted at the purchase, so
    // re-running the analysis cannot move a line under a trade already taken - and the newest call's
    // otherwise. Two channels calling one stock print two different sets, and the newest is the one
    // a reader opening this sheet today is acting on, which is the same call the action bar acts on.
    val chartLevels = remember(trades, calls) {
        val held = trades.firstOrNull(PositionView::open)
        when {
            held != null -> ChartLevels(
                source = "your trade",
                stopLoss = held.position.stopLoss,
                entryLow = held.position.entryLow,
                entryHigh = held.position.entryHigh,
                target1 = held.position.target1,
                target2 = held.position.target2,
                paid = held.position.entryPrice,
            )

            else -> calls.firstOrNull()?.let { call ->
                ChartLevels(
                    source = "⁨${call.channel}⁩ · " + AppDates.DayMonth.format(call.openedOn),
                    stopLoss = call.stopLoss,
                    entryLow = call.entryLow,
                    entryHigh = call.entryHigh,
                    target1 = call.target1,
                    target2 = call.target2,
                    paid = null,
                )
            }
        }?.takeIf(ChartLevels::any)
    }
    // Every session anybody named this stock on, which is what the rings on the line mark, and who
    // named it - the chart marks the session and the readout under a thumb says whose it was. Two
    // channels calling it on one morning are one mark, counted rather than one of them picked.
    val callDates = remember(calls) {
        calls.groupBy(ScoredCall::openedOn).mapValues { (_, made) ->
            val channels = made.map(ScoredCall::channel).distinct()
            if (channels.size == 1) "⁨${channels.first()}⁩ called it" else "${channels.size} sources called it"
        }
    }
    // Off the disk rather than out of the report, and after the sheet is already on screen: the
    // rest of this sheet is in memory and must not wait behind a query. An empty list draws no
    // line, which is what a stock the feed has never carried should look like.
    var history by remember(key) { mutableStateOf(emptyList<DailySession>()) }
    LaunchedEffect(key, report) {
        history = appState.priceHistory(key, ChartRange.Widest.since(LocalDate.now()))
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        StockSheetHeading(key, score, calls.firstOrNull(), latest, history)
        Column(
            Modifier
                .fillMaxWidth()
                // Bounded by what the header and the action bar leave, and no taller than its own
                // content: a short record must not stretch the sheet to the full screen with the
                // action bar stranded at the bottom of it.
                .weight(1f, fill = false)
                .sheetDragSlop()
                .scrollableColumn()
                .padding(horizontal = Space.l)
                .padding(top = Space.s, bottom = Space.m),
            verticalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            StockSheetChips(trades, score, faults)
            StockSheetPrice(latest, history, chartLevels, callDates, appState.pages)
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
        calls.firstOrNull()?.let { newest ->
            StockSheetActions(newest, latest, appState)
        }
    }
}

/**
 * The ticker, what the record knows it as, and where it stands - on one fixed line.
 *
 * Four stacked lines before any figure was what the old heading cost: the label, the ticker, and a
 * name in each script, with the price below the fold on a short phone. The names share a line here
 * and the price comes up beside them, so the two things the sheet was opened for are both above
 * everything else and stay there while the record scrolls under them.
 *
 * The names come from the score where there is one and from the newest call otherwise: a stock
 * called once has no score and still has a company behind it. Both scripts, because the channels
 * print Arabic and the catalog holds English, and a reader who knows one should not have to know
 * the other.
 */
@Composable
private fun StockSheetHeading(
    ticker: String,
    score: StockScore?,
    newest: ScoredCall?,
    latest: LatestPrice?,
    history: List<DailySession>,
) {
    val english = score?.companyEnglish ?: newest?.companyEnglish
    val arabic = score?.companyArabic ?: newest?.companyArabic
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.l)
            .padding(bottom = Space.s),
        horizontalArrangement = Arrangement.spacedBy(Space.s),
        verticalAlignment = Alignment.Top,
    ) {
        StockLogo(ticker, LogoSize.Header)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(ticker, style = MaterialTheme.typography.titleLarge)
                Egx33Badge(ticker, Modifier.padding(start = Space.s))
            }
            // One line for both names rather than one line each. A first-strong isolate round the
            // Arabic, as every Arabic name in this app carries: without it a digit in the name
            // drifts to the wrong end of it.
            listOfNotNull(english, arabic?.let { "⁨$it⁩" })
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
        if (latest != null) {
            // The move since the session before it, which is the figure a price is always read
            // against and the one the sheet never carried. Measured off the stored history rather
            // than the report, which keeps one session per stock and so has nothing to compare
            // against.
            val move = history.dayMove()
            Column(horizontalAlignment = Alignment.End) {
                // Labelled, because a bare figure at the top of a sheet is a number the reader has
                // to work out the meaning of - and the label is also where the one thing that
                // changes its meaning is said: a session still trading is a price, not a close.
                Text(
                    if (latest.provisional) "LATEST PRICE" else "LAST CLOSE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    formatPrice(latest.session.close),
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = TabularFigures),
                    color = PriceRole.market,
                )
                if (move != null) {
                    // In pounds as well as percent. The app has never printed a price move in money
                    // anywhere, and on a stock trading at 0.24 a percent is the figure that says
                    // nothing - the two together are what a holder actually reads.
                    Text(
                        formatSignedPrice(move.amount) + " (" + formatPercent(move.percent) + ")",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = TabularFigures,
                        ),
                        color = PriceRole.forReturn(move.percent),
                        textAlign = TextAlign.End,
                    )
                }
                // A session still trading says so instead of naming its own date: the close is
                // going to move, and a date under it reads as settled.
                Text(
                    if (latest.provisional) {
                        "still trading"
                    } else {
                        (if (move != null) "since " else "") +
                            AppDates.DayMonth.format(move?.from ?: latest.session.date)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

/**
 * The one-word answers, before any of the figures behind them.
 *
 * Three of these were on the sheet already and each cost a section to find out: whether the reader
 * holds the stock was three sections down under Your trades, how many sources have called it was a
 * clause in a sentence under the rates, and a broken feed was a red line under the price. A reader
 * who opens a stock to decide something wants those three answers in the first glance and the
 * figures only if the answer surprises them.
 *
 * The fault chips keep [FeedFault.label] and lose the detail: the sentence is still printed in full
 * beside the price where there is room for it, and a chip is a flag rather than an explanation.
 */
@Composable
private fun StockSheetChips(
    trades: List<PositionView>,
    score: StockScore?,
    faults: Set<FeedFault>,
) {
    val open = trades.firstOrNull(PositionView::open)
    val settled = score?.tally?.judged ?: 0
    if (open == null && score == null && faults.isEmpty()) return
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        if (open != null) {
            StockChip(
                "Held " + formatPercent(open.returnPct),
                MaterialTheme.colorScheme.surfaceContainerHighest,
                PriceRole.forReturn(open.returnPct),
            )
        }
        if (score != null) {
            StockChip(
                "${score.sources} " + (if (score.sources == 1) "source" else "sources"),
                MaterialTheme.colorScheme.surfaceContainerHighest,
                MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (settled > 0) {
                StockChip(
                    "$settled settled " + (if (settled == 1) "call" else "calls"),
                    MaterialTheme.colorScheme.surfaceContainerHighest,
                    MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        faults.forEach { fault ->
            StockChip(
                fault.label,
                MaterialTheme.colorScheme.errorContainer,
                MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun StockChip(label: String, container: Color, content: Color) {
    Surface(color = container, shape = RoundedCornerShape(ChipCorner)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = content,
            modifier = Modifier.padding(horizontal = Space.s, vertical = Space.xs),
        )
    }
}

/**
 * Where the stock has been, where it is now, and whether that figure can be trusted.
 *
 * The line comes first because it is the context the figures under it are read in: a close of 86.40
 * means one thing at the top of a month of climbing and another at the bottom of a slide, and the
 * app has held the sessions to say which since the first price refresh. Under it the session's own
 * range, which is what turns a high, a low and a close from three prices into one sentence.
 *
 * The faults keep the wording they had. They are computed on every recompute and, until this sheet,
 * reached the reader only in Settings, a screen away from the stock they are about;
 * [FeedFault.detail] is the short form the record already keeps, and the longer wording belongs to
 * the Settings card, which has the room for it and a count to put it against.
 */
@Composable
private fun StockSheetPrice(
    latest: LatestPrice?,
    history: List<DailySession>,
    levels: ChartLevels?,
    /** Which sessions carry a call, and who made it, by session. */
    calls: Map<LocalDate, String>,
    pages: PageState,
) {
    if (latest == null && history.isEmpty()) return
    var range by pages.stockChartRange
    var showLevels by pages.stockChartLevels
    // Measured back from the newest session the app holds rather than from today's date. The right
    // edge of the line is that session whatever the calendar says, so a week counted from today on
    // a feed three weeks behind would draw an empty box for a stock whose prices are simply old -
    // and the dates under the line say which week it really is.
    val visible = remember(history, range) {
        val anchor = history.lastOrNull()?.date ?: return@remember history
        val since = range.since(anchor)
        history.filter { !it.date.isBefore(since) }
    }
    val move = remember(visible) { visible.rangeMove() }
    val shown = if (showLevels) levels else null
    // Kept after the finger lifts, and cleared by changing the range - which is a `remember` keyed
    // on what is drawn rather than a rule anybody had to write. A reading the reader took on
    // purpose is theirs until they ask a different question.
    var touched by remember(visible) { mutableStateOf<DailySession?>(null) }
    SheetSection {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SectionLabel("Where it stands")
            // What the visible line adds up to, which is the one thing its shape cannot say: the
            // same climb is three percent or forty depending on a scale the chart deliberately
            // does not print.
            if (move != null) {
                Text(
                    formatPercent(move),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = TabularFigures),
                    color = PriceRole.forReturn(move),
                )
            }
        }
        if (visible.count { it.close != null } > 1) {
            PriceChart(
                visible,
                shown,
                calls.keys,
                on = MaterialTheme.colorScheme.surfaceContainerHigh,
                selected = touched,
                onSelect = { touched = it },
            )
            // The readout takes the dates' own line rather than appearing above it: a caption that
            // arrived on touch would push the chart up under the finger that asked for it.
            val reading = touched
            if (reading?.close != null) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Space.xs),
                ) {
                    Text(
                        AppDates.DayMonth.format(reading.date) + " · " +
                            formatPrice(reading.close),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = TabularFigures,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    // What the reader is looking at is a ring on the line; naming the source is
                    // what turns it from a mark into the reason that session is on the chart.
                    val since = visible.moveTo(reading)
                    ChartCaption(
                        calls[reading.date]
                            ?: since?.let { formatPercent(it) + " since " + AppDates.DayMonth.format(visible.first().date) }
                            ?: "",
                    )
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    ChartCaption(AppDates.DayMonth.format(visible.first().date))
                    if (visible.any { it.date in calls }) ChartCaption("○ a call was made")
                    ChartCaption(AppDates.DayMonth.format(visible.last().date))
                }
            }
            // Whose levels these are, which is the one thing a coloured line across a chart cannot
            // say for itself.
            if (shown != null) ChartCaption("levels from " + shown.source)
        } else {
            // Absent rather than an empty box, which reads as a chart that failed to load. A week
            // of a stock that has barely traded is a real answer and this is what it looks like.
            ChartCaption("No sessions stored in this range.")
        }
        ChartControls(
            range = range,
            onRange = { range = it },
            levels = if (levels == null) null else showLevels,
            onLevels = { showLevels = it },
        )
        latest?.let {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            DayRange(it.session)
        }
    }
}

/**
 * How far back the line reaches, and whether it carries the levels.
 *
 * At the foot of the chart, where every chart a reader has used puts its ranges. One line that
 * scrolls sideways rather than a row that wraps - five ranges plus the toggle is about 290dp of
 * controls inside the 355dp a card leaves on the cover screen, so it fits with nothing spare and a
 * large font scale would otherwise clip a chip off the end. `fadingScrollbar` draws nothing when
 * there is nothing to scroll, so at every width it fits the row is indistinguishable from a plain
 * one.
 *
 * The levels chip is **absent rather than disabled** where there are none to draw: a stock nobody
 * has called has no stop and no targets, and a control that answers a press with nothing is worse
 * than one that is not offered.
 */
@Composable
private fun ChartControls(
    range: ChartRange,
    onRange: (ChartRange) -> Unit,
    levels: Boolean?,
    onLevels: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().scrollableRow(),
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChartRange.entries.forEach { option ->
            FilterChip(
                selected = option == range,
                onClick = { onRange(option) },
                label = { Text(option.label, maxLines = 1) },
            )
        }
        if (levels != null) {
            // A fixed gap and not a weight: this row scrolls, so its width is unbounded and a
            // weighted child inside one cannot be measured at all.
            Spacer(Modifier.width(Space.m))
            FilterChip(
                selected = levels,
                onClick = { onLevels(!levels) },
                label = { Text("Levels", maxLines = 1) },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Timeline,
                        contentDescription = null,
                        modifier = Modifier.size(IconSize.Inline),
                    )
                },
            )
        }
    }
}

@Composable
private fun ChartCaption(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * What has happened when anybody recommended this stock.
 *
 * The figure no screen showed. It follows [StockScore]'s own discipline, which is `ChannelScore`'s:
 * below `MINIMUM_JUDGED_TO_RANK` the numbers are reported exactly as measured and lose their
 * colour, and the words "too few to rank" are added - the same rule a call card's source line
 * follows, and for the same reason. A rate resting on two calls is not a smaller rate, it is a
 * different kind of claim.
 *
 * The bar under the rates is the one the channel cards carry, drawn from this stock's own tally.
 * The pair of rates is a division of one quantity and said so in words only; a reader who has read
 * one outcome bar anywhere else in the app already knows how to read this one.
 */
@Composable
private fun StockSheetRecord(score: StockScore) {
    val tally: CallTally = score.tally
    val thin = tally.judged < PerformanceCalculator.MINIMUM_JUDGED_TO_RANK
    SheetSection {
        SectionLabel("When this stock is recommended")
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
        OutcomeBar(tally, on = MaterialTheme.colorScheme.surfaceContainerHigh)
        OutcomeLegend()
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
    SheetSection {
        SectionLabel("Your trades")
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
                        // The deadline beside the entry, because an open trade is the one thing on
                        // this sheet that is still costing the reader something.
                        listOfNotNull(
                            "bought ${AppDates.DayMonth.format(view.position.entryDate)} at " +
                                formatPrice(view.position.entryPrice),
                            view.position.target1?.let { "target 1 " + formatPrice(it) },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    formatPercent(view.returnPct),
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = TabularFigures),
                    color = PriceRole.forReturn(view.returnPct),
                )
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowForward,
                    // Named by the press it belongs to, on the row; a reader announcing the glyph
                    // as well would say it twice.
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Space.xs).size(IconSize.Hint),
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
 *
 * **Capped until asked.** A stock the whole channel list likes carries thirty of these, and thirty
 * rows put the trades and the record above them off the top of a scroll that was opened to compare
 * all three. Five is enough to see who has been saying what lately; the rest are one press away and
 * the press says how many it is holding, so nothing is hidden without being counted.
 */
@Composable
private fun StockSheetCalls(calls: List<ScoredCall>, onOpen: (String) -> Unit) {
    var all by remember(calls) { mutableStateOf(false) }
    val shown = if (all) calls else calls.take(CallsShown)
    SheetSection {
        SectionLabel("Every call on it")
        shown.forEach { call ->
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
                        // The entry beside the date, which is what makes a call comparable to the
                        // one under it: two sources naming one stock a week apart are the same
                        // opinion at two prices, and the date alone does not say that.
                        listOfNotNull(
                            AppDates.DayMonth.format(call.openedOn),
                            call.entryBand(),
                            if (call.repeatOf != null) "re-posted" else null,
                        ).joinToString(" · "),
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
        if (!all && calls.size > CallsShown) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TextButton(onClick = { all = true }) {
                    Text("Show all ${calls.size} calls")
                }
            }
        }
    }
}

/**
 * The two things a reader does about a stock, on the edge the thumb is already on.
 *
 * Both were reachable from here and neither was offered: buying meant finding the call card the
 * trade would be taken on, and the paid question meant the same trip. They are the same two
 * controls those cards carry, taking the same confirmations, writing the same trade - `TradeAction`
 * is the app's only way of recording a purchase and this is one more surface that asks it, not a
 * second way of asking.
 *
 * **Against the newest call, and the sheet says so.** A question about a stock is a question about
 * what somebody said about it, and a trade is recorded against the call it was taken on - neither
 * has any meaning without one. The newest is the only defensible choice: it is the call a reader
 * opening a stock this morning is acting on, and naming it under the buttons is what stops the bar
 * from looking like it belongs to the stock in general.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StockSheetActions(call: ScoredCall, latest: LatestPrice?, appState: AppState) {
    val held = appState.heldFor(call.ticker, call.openedOn)
    val opinion: StockOpinion? = appState.opinionFor(call)
    val asking = appState.opinionPending == opinionId(call.ticker, call.openedOn, call.channel)
    // Chrome, so a `remember` is the right home for all three: the answer behind them is on disk
    // rather than in the composition. See PageState.
    var confirming by remember(call.positionId) { mutableStateOf(false) }
    var showing by remember(call.positionId) { mutableStateOf(false) }
    // Set while this sheet's own request is out, so the answer opens itself when it lands. Without
    // it a sheet opened over a stock that already holds an opinion would spring the answer open the
    // moment any other request anywhere finished.
    var awaiting by remember(call.positionId) { mutableStateOf(false) }
    LaunchedEffect(opinion) {
        if (awaiting && opinion != null) {
            awaiting = false
            showing = true
        }
    }
    // Read once for the bar rather than inside the dialog: each is a SharedPreferences lookup, and
    // a call from inside a conditional would be a `remember` that comes and goes with the dialog.
    // Keyed on the revision, so changing either in Settings reaches a sheet already on screen.
    val askModel = remember(appState.opinionSettingsRevision, appState.cloudConfiguration) {
        appState.opinionModel()
    }
    val searching = remember(appState.opinionSettingsRevision) { appState.opinionSearchEnabled() }
    val newsWindow = remember(appState.opinionSettingsRevision) { appState.opinionNewsWindowDays() }
    if (confirming) {
        AskAiDialog(
            call = call,
            model = askModel,
            searching = searching,
            newsWindowDays = newsWindow,
            onConfirm = {
                confirming = false
                awaiting = true
                appState.askAboutCall(call)
            },
            onDismiss = { confirming = false },
        )
    }
    if (showing && opinion != null) {
        StockOpinionSheet(
            call = call,
            opinion = opinion,
            onAskAgain = if (asking) {
                null
            } else {
                {
                    showing = false
                    awaiting = true
                    appState.askAboutCall(call, askAgain = true)
                }
            },
            onDismiss = { showing = false },
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.l)
            .padding(top = Space.s, bottom = Space.xl),
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        // Wrapped rather than laid in a row, exactly as the call card wraps them: at 280dp the two
        // labels together are wider than the sheet, and a button pushed off the edge is a button
        // nobody can press.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
            AskAiButton(
                label = when {
                    asking -> "Asking…"
                    // Named for what it holds once there is something to open. The button that
                    // spends money and the button that reopens a saved answer must not read alike.
                    opinion != null -> "AI Response"
                    else -> "Ask AI"
                },
                onClick = { if (opinion == null) confirming = true else showing = true },
                look = if (opinion != null && !asking) AiLook.Outlined else AiLook.Filled,
                enabled = !asking,
                working = asking,
                phaseKey = call.ticker,
            )
            TradeAction(
                held = held,
                suggestedEntry = call.entryMidpoint(),
                defaultWindow = call.offeredWindow(
                    appState.appPreferences.defaultTradeWindowSessions,
                ),
                tPlusOne = call.isTPlusOne,
                suggestedExit = latest?.session?.close,
                onBuy = { price, date, window ->
                    appState.recordPurchase(
                        ticker = call.ticker,
                        companyEnglish = call.companyEnglish,
                        companyArabic = call.companyArabic,
                        channel = call.channel,
                        recommendationDate = call.openedOn,
                        entryPrice = price,
                        entryDate = date,
                        entryLow = call.entryLow,
                        entryHigh = call.entryHigh,
                        target1 = call.target1,
                        target2 = call.target2,
                        stopLoss = call.stopLoss,
                        windowSessions = window,
                        // What the dialog showed them, so accepting a T+1 call's two sessions is
                        // not recorded as a deadline they set by hand.
                        offeredWindow = call.offeredWindow(
                            appState.appPreferences.defaultTradeWindowSessions,
                        ),
                        isTPlusOne = call.isTPlusOne,
                    )
                },
                onSell = { sale: Sale -> held?.let { appState.recordSale(it.position, sale) } },
            )
        }
        Text(
            "on ⁨${call.channel}⁩'s call of " +
                AppDates.DayMonth.format(call.openedOn),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * One band of the scroll, on the cards' own surface.
 *
 * The sections were divided by rules and read as one long column that happened to have lines in it.
 * A card each is what the rest of the app uses to say "this is one thing", and it is what lets the
 * heading of a section sit inside the thing it heads rather than floating above the gap.
 */
@Composable
private fun SheetSection(content: @Composable ColumnScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        border = cardOutline,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            Modifier.padding(Space.m),
            verticalArrangement = Arrangement.spacedBy(Space.xs),
            content = content,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** What a stock did between its last two stored sessions, and which one it moved from. */
private data class DayMove(val amount: Double, val percent: Double, val from: LocalDate)

/**
 * The move from the session before last to the last.
 *
 * Null wherever the history cannot support one - fewer than two closes, or a previous close of zero
 * - rather than a zero, which would read as a stock that did not move. [DayMove.from] is the
 * session it moved *from*, because "since 3 Sep" over a close dated the 4th is the only wording
 * that says what the figure above it measured.
 */
private fun List<DailySession>.dayMove(): DayMove? {
    val priced = takeLast(2).filter { it.close != null }
    if (priced.size < 2) return null
    val previous = priced.first().close!!
    val last = priced.last().close!!
    if (previous <= 0.0) return null
    return DayMove(last - previous, (last - previous) / previous * 100, priced.first().date)
}

/** The band a call asked the reader to buy in, as one figure or two. */
private fun ScoredCall.entryBand(): String? = when {
    entryLow != null && entryHigh != null && entryLow != entryHigh ->
        formatPrice(entryLow) + "–" + formatPrice(entryHigh)

    else -> (entryLow ?: entryHigh)?.let(::formatPrice)
}

/** The middle of that band, which is what a fill is usually nearest. */
private fun ScoredCall.entryMidpoint(): Double? = when {
    entryLow != null && entryHigh != null -> (entryLow + entryHigh) / 2
    else -> entryLow ?: entryHigh
}

/**
 * What a trade taken from this sheet is offered as its window.
 *
 * The same rule `offeredTradeWindow` applies to a recommendation on the Results tab: the setting
 * for an ordinary call, and a T+1 call's own two sessions for one the channel dated for tomorrow.
 * What the *channel* is judged over is fixed and is no business of this trade's.
 */
private fun ScoredCall.offeredWindow(setting: Int): Int = if (isTPlusOne) {
    Scoring.T_PLUS_ONE_WINDOW_SESSIONS
} else {
    Scoring.clampWindow(setting)
}

/**
 * A price move with its sign, which [formatPrice] deliberately does not carry.
 *
 * Every other price in this app is a level or a close, where a sign would be noise; a move is the
 * one figure whose direction is half of what it says.
 */
private fun formatSignedPrice(value: Double): String =
    (if (value > 0) "+" else "") + formatPrice(value)

/**
 * What the visible line adds up to, end to end, in percent.
 *
 * The figure the shape cannot carry: a chart scaled to its own range draws the same climb whether
 * the stock moved three percent or forty, which is the first thing a reader asks of it.
 */
/** What the line had done by the touched session, measured from the left-hand end of the range. */
private fun List<DailySession>.moveTo(session: DailySession): Double? {
    val first = firstOrNull { it.close != null }?.close ?: return null
    val close = session.close ?: return null
    if (first <= 0.0) return null
    return (close - first) / first * 100
}

private fun List<DailySession>.rangeMove(): Double? {
    val priced = filter { it.close != null }
    if (priced.size < 2) return null
    val first = priced.first().close!!
    val last = priced.last().close!!
    if (first <= 0.0) return null
    return (last - first) / first * 100
}

/** Enough recent calls to see who has been saying what, before the list is asked to open. */
private const val CallsShown = 5

private val ChipCorner = 8.dp
