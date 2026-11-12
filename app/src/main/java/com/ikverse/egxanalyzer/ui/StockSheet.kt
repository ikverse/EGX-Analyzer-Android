package com.ikverse.egxanalyzer.ui

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ikverse.egxanalyzer.model.CallTally
import com.ikverse.egxanalyzer.model.DailySession
import com.ikverse.egxanalyzer.model.DayBar
import com.ikverse.egxanalyzer.model.dayMove
import com.ikverse.egxanalyzer.model.FeedFault
import com.ikverse.egxanalyzer.model.LatestPrice
import com.ikverse.egxanalyzer.model.Outcome
import com.ikverse.egxanalyzer.model.PerformanceCalculator
import com.ikverse.egxanalyzer.model.PositionView
import com.ikverse.egxanalyzer.model.ScoredCall
import com.ikverse.egxanalyzer.model.Scoring
import com.ikverse.egxanalyzer.model.StockScore
import com.ikverse.egxanalyzer.model.plainWord
import com.ikverse.egxanalyzer.model.WatchAlert
import com.ikverse.egxanalyzer.model.inPlay
import com.ikverse.egxanalyzer.model.isEgx33
import com.ikverse.egxanalyzer.model.opinionId
import com.ikverse.egxanalyzer.model.positionId
import com.ikverse.egxanalyzer.ui.kit.ButtonKind
import com.ikverse.egxanalyzer.ui.kit.ButtonSize
import com.ikverse.egxanalyzer.ui.kit.Kit
import com.ikverse.egxanalyzer.ui.kit.KitButton
import com.ikverse.egxanalyzer.ui.kit.KitIconButton
import com.ikverse.egxanalyzer.ui.kit.KitRule
import com.ikverse.egxanalyzer.ui.kit.KitSheet
import com.ikverse.egxanalyzer.ui.kit.KitTag
import com.ikverse.egxanalyzer.ui.kit.KitTextField
import com.ikverse.egxanalyzer.ui.kit.LocalFoldPosture
import com.ikverse.egxanalyzer.ui.kit.OutcomeStrip
import com.ikverse.egxanalyzer.ui.kit.Outcomes
import com.ikverse.egxanalyzer.ui.kit.SegmentedControl
import com.ikverse.egxanalyzer.ui.kit.Tile
import com.ikverse.egxanalyzer.ui.kit.TileTone
import com.ikverse.egxanalyzer.ui.kit.popIn
import com.ikverse.egxanalyzer.ui.theme.palette
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
 * **A fixed header over one scroller, rather than one long column of dividers.** The identity, the
 * price and the held/sources/settled chips are what the sheet was opened to see and do not scroll
 * away; the record, the trades and the calls run beneath them, each in a card of its own. There is
 * deliberately no action bar under the scroller any more - Ask AI and Bought/Sold are not reachable
 * from this sheet at all right now, which is a real gap and not a decision this file argues for.
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
@Composable
internal fun StockSheet(ticker: String, appState: AppState, onDismiss: () -> Unit) {
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
    val held = trades.firstOrNull(PositionView::open)
    // The call the sheet's actions are about: the newest one the market has not finished with.
    val inPlay = remember(calls) { calls.firstOrNull { it.inPlay() } }
    val chartLevels = remember(trades, calls) {
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
    val callDates = remember(calls) {
        calls.groupBy(ScoredCall::openedOn).mapValues { (_, made) ->
            val channels = made.map(ScoredCall::channel).distinct()
            if (channels.size == 1) "⁨${channels.first()}⁩ called it" else "${channels.size} sources called it"
        }
    }
    var history by remember(key) { mutableStateOf(emptyList<DailySession>()) }
    LaunchedEffect(key, report) {
        history = appState.priceHistory(key, ChartRange.Widest.since(LocalDate.now()))
    }
    KitSheet(onDismiss = onDismiss, posture = LocalFoldPosture.current, title = key) {
        StockSheetHeading(
            key, score, calls.firstOrNull(), latest, history,
            watching = key in appState.watchlist,
            onToggleWatch = { appState.toggleWatch(key) },
            onClose = onDismiss,
        )
        StockSheetChips(trades, score, faults)
        StockSheetPrice(
            key, latest, history, chartLevels, callDates,
            newestCall = calls.firstOrNull()?.openedOn,
            pages = appState.pages,
            appState = appState,
            modifier = Modifier.popIn(0),
        )
        StockSheetActions(
            ticker = key,
            appState = appState,
            call = inPlay,
            held = held,
            latest = latest,
            onDismiss = onDismiss,
        )
        if (chartLevels != null && latest?.session?.close != null) {
            StockSheetLevels(chartLevels, latest.session.close!!, inPlay, Modifier.popIn(1))
        }
        if (score != null) StockSheetRecord(score, Modifier.popIn(2))
        if (trades.isNotEmpty()) {
            StockSheetTrades(trades, Modifier.popIn(3)) { id ->
                onDismiss()
                appState.openPosition(id)
            }
        }
        if (calls.isNotEmpty()) {
            StockSheetCalls(calls, Modifier.popIn(4)) { id ->
                onDismiss()
                appState.openCall(id)
            }
        }
        if (score == null && trades.isEmpty() && calls.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.Visibility,
                title = "Nothing recorded yet",
                detail = "Nothing in the record names this stock yet. Watch it to follow its price.",
            )
        }
    }
}

/**
 * Who the stock is and where it stands: the logo, the ticker, the names and the EGX 33 mark, the
 * watch toggle and the close, the price at the display size with the day's move, and the day bar.
 */
@Composable
private fun StockSheetHeading(
    ticker: String,
    score: StockScore?,
    newest: ScoredCall?,
    latest: LatestPrice?,
    history: List<DailySession>,
    watching: Boolean,
    onToggleWatch: () -> Unit,
    onClose: () -> Unit,
) {
    val p = palette
    val english = score?.companyEnglish ?: newest?.companyEnglish
    val arabic = score?.companyArabic ?: newest?.companyArabic
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.m)) {
        StockLogo(ticker, HeadingLogoSize)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(ticker, style = MaterialTheme.typography.headlineLarge.copy(fontSize = 30.sp, lineHeight = 32.sp), color = p.ink)
                Egx33Badge(ticker, Modifier.padding(start = Space.s))
            }
            // A first-strong isolate round the Arabic, as every Arabic name in this app carries.
            listOfNotNull(english, arabic?.let { "⁨$it⁩" }, if (isEgx33(ticker)) "EGX 33" else null)
                .takeIf(List<String>::isNotEmpty)
                ?.let {
                    Text(
                        it.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = p.muted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
        }
        // Following the stock is the one thing in the heading that writes: it joins every refresh.
        KitIconButton(
            if (watching) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
            label = if (watching) "Stop watching $ticker" else "Watch $ticker",
            onClick = onToggleWatch,
            filled = watching,
            size = 44.dp,
        )
        KitIconButton(Icons.Outlined.Close, label = "Close", onClick = onClose, size = 44.dp)
    }
    if (latest != null) {
        val move = history.dayMove()
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Space.m)) {
            Column {
                // Labelled: a session still trading is a price, not a close.
                Text(
                    if (latest.provisional) "LATEST PRICE" else "LAST CLOSE",
                    style = MaterialTheme.typography.labelSmall,
                    color = p.muted,
                )
                RollingText(
                    AnnotatedString(formatPrice(latest.session.close)),
                    style = MaterialTheme.typography.displayLarge.copy(fontFamily = TabularFigures, fontSize = 48.sp, lineHeight = 52.sp),
                    color = p.ink,
                )
            }
            Column(Modifier.padding(bottom = Space.s)) {
                if (move != null) {
                    Text(
                        formatSignedPrice(move.amount) + " · " + formatPercent(move.percent),
                        style = MaterialTheme.typography.titleSmall.copy(fontFamily = TabularFigures),
                        color = if (move.percent >= 0) p.gain else p.loss,
                    )
                }
                Text(
                    if (latest.provisional) {
                        "still trading"
                    } else {
                        (if (move != null) "since " else "") + AppDates.DayMonth.format(move?.from ?: latest.session.date)
                    },
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = TabularFigures),
                    color = p.muted,
                )
            }
        }
        DayRange(latest.session)
    }
}

/** The stock's logo, a step up from the opinion sheet's, beside a 30sp ticker. */
private val HeadingLogoSize = 52.dp

/**
 * The one-word answers before any figure: whether the reader holds it, how many sources have called
 * it, how many calls have settled, and any fault in its prices.
 */
@Composable
private fun StockSheetChips(trades: List<PositionView>, score: StockScore?, faults: Set<FeedFault>) {
    val p = palette
    val open = trades.firstOrNull(PositionView::open)
    val settled = score?.tally?.judged ?: 0
    if (open == null && score == null && faults.isEmpty()) return
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        if (open != null) {
            KitTag("Held " + formatPercent(open.returnPct), if ((open.returnPct ?: 0.0) >= 0) p.gain else p.loss)
        }
        if (score != null) {
            KitTag("${score.sources} " + (if (score.sources == 1) "source" else "sources"), p.muted)
            if (settled > 0) {
                KitTag("$settled settled " + (if (settled == 1) "call" else "calls"), p.muted)
            }
        }
        faults.forEach { fault -> KitTag(fault.label, p.loss) }
    }
}

/**
 * Where the stock has been, on the sheet's dark chart tile: what the line adds up to, the Levels
 * and Expand buttons, the line with its levels tagged down the right, and the range track.
 *
 * The tile was cobalt until 2026-11-11 - see [chartTileColors] for what it is now and why it is
 * not one of the kit's tile tones. The reading under a finger takes the headline's two places
 * rather than a caption of its own, so nothing below the chart moves when it arrives.
 */
@Composable
private fun StockSheetPrice(
    ticker: String,
    latest: LatestPrice?,
    history: List<DailySession>,
    levels: ChartLevels?,
    /** Which sessions carry a call, and who made it, by session. */
    calls: Map<LocalDate, String>,
    /** The newest call's session, which is where the Call range starts. Null with no call. */
    newestCall: LocalDate?,
    pages: PageState,
    /** Where the full-screen chart keeps its indicators and drawings. */
    appState: AppState,
    modifier: Modifier = Modifier,
) {
    if (latest == null && history.isEmpty()) return
    val colors = chartTileColors(palette)
    var range by pages.stockChartRange
    var showLevels by pages.stockChartLevels
    // A stored Call range on a stock nobody has called is a month, never an empty box.
    // The range is one choice for every stock the sheet opens on, so 1D left on from a stock that
    // has a day's bars is a month on one that has none - as Call is on a stock nobody has called.
    val offered = remember(ticker, newestCall) {
        ChartRange.entries.filter { newestCall != null || it != ChartRange.CALL }.offeredFor(appState, ticker)
    }
    val effective = if (range in offered) range else ChartRange.MONTH
    val day = effective == ChartRange.DAY
    val bars = rememberDayBars(appState, ticker, wanted = day)
    val visible = remember(history, effective, newestCall) { history.within(effective, newestCall) }
    val move = remember(visible, bars, day) { if (day) bars?.dayMove() else visible.rangeMove() }
    val shown = if (showLevels) levels else null
    var touched by remember(visible) { mutableStateOf<DailySession?>(null) }
    var touchedBar by remember(bars) { mutableStateOf<DayBar?>(null) }
    var expanded by remember { mutableStateOf(false) }
    CompositionLocalProvider(LocalContentColor provides colors.text) {
        Column(modifier.fillMaxWidth().clip(Kit.TileShape).background(colors.surface).padding(16.dp)) {
            // Who made the call on the touched session, where there was one, in the move's place.
            val reading = if (day) {
                dayReading(bars.orEmpty(), touchedBar)
            } else {
                chartReading(visible, touched, note = touched?.let { calls[it.date] })
            }
            ChartHeadline(
                title = reading?.first ?: when (effective) {
                    ChartRange.DAY -> dayTitle(bars)
                    ChartRange.CALL -> "Since the call · ${visible.size} sessions"
                    else -> effective.title
                },
                figure = reading?.second ?: move?.let { formatPercent(it) }.orEmpty(),
                colors = colors,
            ) {
                // Absent rather than disabled where there are no levels to draw.
                if (levels != null) LevelsButton(showLevels, colors) { showLevels = !showLevels }
                // The same chart on the whole screen. Offered only where there is a line to expand.
                if (history.count { it.close != null } > 1) {
                    val press = remember { MutableInteractionSource() }
                    Box(
                        Modifier
                            .pressScale(press)
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(colors.track)
                            .clickable(
                                interactionSource = press,
                                indication = null,
                                role = Role.Button,
                                onClickLabel = "Expand the chart",
                            ) { expanded = true }
                            .semantics { contentDescription = "Expand chart" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.OpenInFull, contentDescription = null, tint = colors.text, modifier = Modifier.size(18.dp))
                    }
                }
            }
            if (expanded) {
                ExpandedChart(
                    ticker = ticker,
                    history = history,
                    levels = levels,
                    calls = calls.keys,
                    range = effective,
                    ranges = offered,
                    dayBars = bars,
                    onRange = { range = it },
                    showLevels = showLevels,
                    onLevels = { showLevels = it },
                    indicators = appState.chartIndicators,
                    onIndicators = appState::saveChartIndicators,
                    drawings = appState.chartDrawings(ticker),
                    onDrawings = { appState.saveChartDrawings(ticker, it) },
                    onClose = { expanded = false },
                    newestCall = newestCall,
                )
            }
            if (day) {
                DayChartOrNote(bars, shown, colors, touchedBar, { touchedBar = it }, Modifier.padding(top = Space.m))
                if (shown != null && bars != null && bars.size > 1) {
                    ChartLegend("levels from " + shown.source, colors, Modifier.padding(top = Space.s), dot = false)
                }
            } else if (visible.count { it.close != null } > 1) {
                PriceChart(
                    visible,
                    shown,
                    calls.keys,
                    colors = colors,
                    selected = touched,
                    onSelect = { touched = it },
                    modifier = Modifier.padding(top = Space.m),
                )
                val called = visible.any { it.date in calls }
                val legend = listOfNotNull(
                    "A call was made".takeIf { called },
                    shown?.let { "levels from " + it.source },
                ).joinToString(" · ")
                if (legend.isNotEmpty()) {
                    ChartLegend(legend, colors, Modifier.padding(top = Space.s), dot = called)
                }
            } else {
                ChartLegend("No sessions stored in this range.", colors, Modifier.padding(top = Space.m), dot = false)
            }
            ChartRanges(
                options = offered,
                selected = effective,
                onSelect = { range = it },
                label = { it.label },
                colors = colors,
                modifier = Modifier.padding(top = Space.m),
            )
        }
    }
}

/** Whether the chart carries the levels: filled while it does, a quiet wash while it does not. */
@Composable
private fun LevelsButton(on: Boolean, colors: ChartColors, onToggle: () -> Unit) {
    val press = remember { MutableInteractionSource() }
    Row(
        Modifier
            .pressScale(press)
            .height(44.dp)
            .clip(Kit.Capsule)
            .background(if (on) colors.text else colors.track)
            .toggleable(
                value = on,
                interactionSource = press,
                indication = null,
                role = Role.Switch,
                onValueChange = { onToggle() },
            )
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (on) Icon(Icons.Outlined.Check, contentDescription = null, tint = colors.surface, modifier = Modifier.size(16.dp))
        Text(
            "Levels",
            style = MaterialTheme.typography.labelLarge,
            color = if (on) colors.surface else colors.text,
            maxLines = 1,
        )
    }
}

/**
 * What the reader can do about the stock from here, as the mockup puts it under the chart.
 *
 * **I bought it** records a trade on the newest call still in play, through the same trade sheet
 * every other surface uses; it becomes **Open my trade** once the stock is held, and is absent with
 * no call in play - a trade needs a call to be judged against. **Ask AI** asks about that same call,
 * behind the same paid confirmation, and reopens a kept answer free. The bell sets a price alert.
 */
@Composable
private fun StockSheetActions(
    ticker: String,
    appState: AppState,
    call: ScoredCall?,
    held: PositionView?,
    latest: LatestPrice?,
    onDismiss: () -> Unit,
) {
    var alerting by remember { mutableStateOf(false) }
    val occurrence = remember(call, appState.savedResults) { call?.let { appState.occurrenceFor(it) } }
    val book = remember(appState, occurrence?.saved?.id) {
        occurrence?.let { TradeBook(appState, it.saved.result.recommendationTargetDate) }
    }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            held != null -> KitButton(
                "Open my trade",
                onClick = {
                    onDismiss()
                    appState.openPosition(held.position.id)
                },
                size = ButtonSize.LARGE,
                modifier = Modifier.weight(1f),
            )
            call != null && occurrence != null && book != null && book.dateOf(occurrence.point) != null -> Box(Modifier.weight(1f)) {
                TradeAction(
                    held = null,
                    suggestedEntry = occurrence.point.entryMidpoint(),
                    defaultWindow = book.windowFor(occurrence.point),
                    tPlusOne = occurrence.point.isTPlusOne,
                    canSell = false,
                    onBuy = { price, date, window ->
                        book.buy(occurrence.stock, occurrence.point, call.channel, price, date, window)
                    },
                    onSell = {},
                )
            }
        }
        if (call != null) StockSheetAsk(call, appState, Modifier.weight(1f))
        if (call == null && held == null) Spacer(Modifier.weight(1f))
        KitIconButton(
            Icons.Outlined.NotificationsNone,
            label = "Price alerts",
            onClick = { alerting = true },
            size = 52.dp,
        )
    }
    if (alerting) {
        WatchAlertSheet(
            ticker = ticker,
            suggested = latest?.session?.close,
            alerts = appState.watchAlerts.filter { it.ticker == ticker },
            onAdd = { level, above -> appState.addWatchAlert(ticker, level, above) },
            onRemove = appState::removeWatchAlert,
            onDismiss = { alerting = false },
        )
    }
}

/** Ask AI about [call], with the same confirmation and the same kept answer its card offers. */
@Composable
private fun StockSheetAsk(call: ScoredCall, appState: AppState, modifier: Modifier = Modifier) {
    val opinion = appState.opinionFor(call)
    val asking = appState.opinionPending == opinionId(call.ticker, call.openedOn, call.channel)
    var confirming by remember(call) { mutableStateOf(false) }
    var showing by remember(call) { mutableStateOf(false) }
    var awaiting by remember(call) { mutableStateOf(false) }
    LaunchedEffect(opinion) {
        if (awaiting && opinion != null) {
            awaiting = false
            showing = true
        }
    }
    AskAiButton(
        label = when {
            asking -> "Asking…"
            opinion != null -> "AI Response"
            else -> "Ask AI"
        },
        onClick = { if (opinion == null) confirming = true else showing = true },
        look = if (opinion != null && !asking) AiLook.Outlined else AiLook.Filled,
        enabled = !asking,
        working = asking,
        modifier = modifier,
    )
    if (confirming) {
        AskAiDialog(
            call = call,
            model = appState.opinionModel(),
            searching = appState.opinionSearchEnabled(),
            newsWindowDays = appState.opinionNewsWindowDays(),
            onConfirm = {
                confirming = false
                awaiting = true
                appState.askAboutCall(call, false)
            },
            onDismiss = { confirming = false },
        )
    }
    if (showing && opinion != null) {
        StockOpinionSheet(
            call = call,
            opinion = opinion,
            onAskAgain = if (asking) null else ({
                showing = false
                awaiting = true
                appState.askAboutCall(call, true)
            }),
            onDismiss = { showing = false },
        )
    }
}

/**
 * Price alerts on this stock: a level and a direction to add, and the ones already set, each with
 * its own Remove. Prefilled with the last close, so the reader edits a number rather than types one.
 */
@Composable
private fun WatchAlertSheet(
    ticker: String,
    suggested: Double?,
    alerts: List<WatchAlert>,
    onAdd: (level: Double, above: Boolean) -> Unit,
    onRemove: (WatchAlert) -> Unit,
    onDismiss: () -> Unit,
) {
    val p = palette
    var price by remember { mutableStateOf(suggested?.let(::formatPrice).orEmpty()) }
    var above by remember { mutableStateOf(true) }
    val parsed = price.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 && it.isFinite() }
    KitSheet(onDismiss = onDismiss, posture = LocalFoldPosture.current, title = "Alerts on $ticker", dismissOnOutside = false) {
        SheetTitle("Alert me about $ticker", "Checked whenever prices are fetched. Each fires once and then removes itself.")
        Tile(Modifier.fillMaxWidth()) {
            SegmentedControl(
                options = listOf(true, false),
                selected = above,
                onSelect = { above = it },
                label = { if (it) "Rises to" else "Falls to" },
                modifier = Modifier.fillMaxWidth(),
            )
            KitTextField(
                value = price,
                onValueChange = { price = it },
                label = "Price",
                figure = true,
                error = "Not a price".takeIf { price.isNotBlank() && parsed == null },
                modifier = Modifier.fillMaxWidth().padding(top = Space.m),
            )
        }
        if (alerts.isNotEmpty()) {
            Tile(Modifier.fillMaxWidth()) {
                Text("SET ON THIS STOCK", style = MaterialTheme.typography.labelSmall, color = p.muted)
                alerts.forEach { alert ->
                    Row(Modifier.fillMaxWidth().padding(top = Space.s), verticalAlignment = Alignment.CenterVertically) {
                        Text(alert.label, style = MaterialTheme.typography.bodyLarge.copy(fontFamily = TabularFigures), color = p.ink, modifier = Modifier.weight(1f))
                        KitButton("Remove", onClick = { onRemove(alert) }, kind = ButtonKind.SECONDARY, size = ButtonSize.SMALL)
                    }
                }
            }
        }
        SheetButtons(
            confirmLabel = "Save alert",
            enabled = parsed != null,
            onConfirm = {
                parsed?.let { onAdd(it, above) }
                onDismiss()
            },
            onDismiss = onDismiss,
        )
    }
}

/**
 * The levels against where the stock is now, as the mockup lists them: highest first, the Now row
 * standing out where it falls among them, each level with its distance from now. Target 1 carries a
 * "reached" tag once the call has banked it.
 */
@Composable
private fun StockSheetLevels(levels: ChartLevels, now: Double, call: ScoredCall?, modifier: Modifier = Modifier) {
    val p = palette
    val reachedT1 = call?.outcome == Outcome.PARTIAL_HIT || call?.outcome == Outcome.FULL_HIT
    val reachedT2 = call?.outcome == Outcome.FULL_HIT
    val entry = when {
        levels.entryLow != null && levels.entryHigh != null && levels.entryLow != levels.entryHigh ->
            formatPrice(minOf(levels.entryLow, levels.entryHigh)) + "–" + formatPrice(maxOf(levels.entryLow, levels.entryHigh))
        else -> (levels.entryLow ?: levels.entryHigh)?.let(::formatPrice)
    }
    val entryValue = listOfNotNull(levels.entryLow, levels.entryHigh).takeIf { it.isNotEmpty() }?.average()
    data class Line(val label: String, val value: Double, val shown: String, val tone: Color, val tag: String?, val isNow: Boolean = false)
    val lines = buildList {
        levels.target2?.let { add(Line("Target 2", it, formatPrice(it), p.ink, if (reachedT2) "reached" else null)) }
        levels.target1?.let { add(Line("Target 1", it, formatPrice(it), p.ink, if (reachedT1) "reached" else null)) }
        add(Line("Now", now, formatPrice(now), p.ink, null, isNow = true))
        levels.paid?.let { add(Line("You paid", it, formatPrice(it), p.ink, null)) }
        if (entry != null && entryValue != null) add(Line("Entry zone", entryValue, entry, p.ink, null))
        levels.stopLoss?.let { add(Line("Stop", it, formatPrice(it), p.loss, null)) }
    }.sortedByDescending { it.value }
    Tile(modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)) {
        lines.forEachIndexed { index, line ->
            if (index > 0) KitRule(color = p.ground)
            Row(
                Modifier
                    .fillMaxWidth()
                    .then(if (line.isNow) Modifier.clip(RoundedCornerShape(12.dp)).background(p.ground) else Modifier)
                    .padding(vertical = 10.dp, horizontal = if (line.isNow) 8.dp else 0.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    line.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (line.isNow) FontWeight.ExtraBold else FontWeight.Bold,
                    color = line.tone,
                )
                line.tag?.let { KitTag(it, p.gain, modifier = Modifier.padding(start = Space.s)) }
                Spacer(Modifier.weight(1f))
                Text(line.shown, style = MaterialTheme.typography.titleSmall.copy(fontFamily = TabularFigures), fontWeight = FontWeight.Bold, color = line.tone)
                if (!line.isNow && now > 0) {
                    Text(
                        " " + formatPercent((line.value - now) / now * 100),
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = TabularFigures),
                        color = p.muted,
                    )
                }
            }
        }
        Text("levels from " + levels.source, style = MaterialTheme.typography.labelSmall, color = p.muted, modifier = Modifier.padding(vertical = 6.dp))
    }
}

/**
 * What has happened when anybody recommended this stock: the two rates at the display size, the
 * result bar and its key, and the figures behind them. Below the ranking floor the rates lose their
 * colour and say "too few to rank".
 */
@Composable
private fun StockSheetRecord(score: StockScore, modifier: Modifier = Modifier) {
    val p = palette
    val tally: CallTally = score.tally
    val thin = tally.judged < PerformanceCalculator.MINIMUM_JUDGED_TO_RANK
    Tile(modifier.fillMaxWidth()) {
        Text("WHEN THIS STOCK IS RECOMMENDED", style = MaterialTheme.typography.labelSmall, color = p.muted)
        RollingText(
            AnnotatedString(formatPercent(tally.anyTargetRate, signed = false) + " / " + formatPercent(tally.fullHitRate, signed = false)),
            style = MaterialTheme.typography.displaySmall.copy(fontFamily = TabularFigures),
            color = if (thin) p.muted else p.ink,
        )
        Text(
            "reached target 1 / target 2, over ${tally.judged} settled " +
                (if (tally.judged == 1) "call" else "calls") +
                " from ${score.sources} " + (if (score.sources == 1) "source" else "sources") +
                (if (thin) " — too few to rank" else ""),
            style = MaterialTheme.typography.bodySmall,
            color = p.muted,
        )
        val split = Outcomes(
            hit = tally.fullHits + tally.partialHits,
            stopped = tally.stopped,
            expired = tally.expired,
            open = (tally.calls - tally.judged - tally.notTradable).coerceAtLeast(0),
        )
        if (split.total > 0) {
            OutcomeStrip(split, Modifier.padding(top = Space.m))
            OutcomeKey(Modifier.padding(top = Space.s))
        }
        Spacer(Modifier.height(Space.m))
        FigureGroup(
            "Across every source",
            listOf<@Composable RowScope.() -> Unit>(
                {
                    Figure(
                        "Per judged call",
                        formatPercent(tally.averageReturn),
                        Modifier.weight(1f),
                        tone = if (thin) p.muted else PriceRole.forReturn(tally.averageReturn),
                    )
                },
                {
                    Figure(
                        "Risk to reward",
                        tally.averageRiskReward?.let { String.format(Locale.US, "%.1f to 1", it) } ?: Dash,
                        Modifier.weight(1f),
                    )
                },
                { Figure("Calls made", tally.calls.toString(), Modifier.weight(1f)) },
            ),
        )
    }
}

/** The reader's own money in this stock, newest first, each row leading to its card. */
@Composable
private fun StockSheetTrades(trades: List<PositionView>, modifier: Modifier, onOpen: (String) -> Unit) {
    val p = palette
    Tile(modifier.fillMaxWidth()) {
        Text("Your trades", style = MaterialTheme.typography.headlineSmall, color = p.ink)
        trades.forEachIndexed { index, view ->
            if (index > 0) KitRule(color = p.ground)
            Row(
                Modifier
                    .fillMaxWidth()
                    .pressableRow { onOpen(view.position.id) }
                    .padding(vertical = Space.s),
                horizontalArrangement = Arrangement.spacedBy(Space.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    KitTag(view.status.plainWord(), view.status.tone())
                    Text(
                        listOfNotNull(
                            "bought ${AppDates.DayMonth.format(view.position.entryDate)} at " + formatPrice(view.position.entryPrice),
                            view.position.target1?.let { "target 1 " + formatPrice(it) },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = TabularFigures),
                        color = p.muted,
                    )
                }
                Text(
                    formatPercent(view.returnPct),
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = TabularFigures),
                    color = PriceRole.forReturn(view.returnPct),
                )
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, tint = p.muted, modifier = Modifier.size(IconSize.Hint))
            }
        }
    }
}

/**
 * Every call printed on this stock, newest first, on the lilac tile: who, when, what they asked
 * for, and how it went. Five until asked, and the press says how many it is holding.
 */
@Composable
private fun StockSheetCalls(calls: List<ScoredCall>, modifier: Modifier, onOpen: (String) -> Unit) {
    val p = palette
    var all by remember(calls) { mutableStateOf(false) }
    val shown = if (all) calls else calls.take(CallsShown)
    val sources = calls.map(ScoredCall::channel).distinct().size
    Tile(modifier.fillMaxWidth(), tone = TileTone.TILE2) {
        Text(
            "Called by $sources " + (if (sources == 1) "source" else "sources"),
            style = MaterialTheme.typography.titleSmall,
        )
        shown.forEach { call ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .pressableRow { onOpen(call.positionId) }
                    .padding(vertical = Space.s),
                horizontalArrangement = Arrangement.spacedBy(Space.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "⁨${call.channel}⁩",
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOfNotNull(
                            AppDates.DayMonth.format(call.openedOn),
                            call.entryBand()?.let { "buy $it" },
                            if (call.repeatOf != null) "re-posted" else null,
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = TabularFigures),
                        color = LocalContentColor.current.copy(alpha = 0.75f),
                    )
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    KitTag(call.outcome.plainWord(), call.outcome.tone())
                    Text(
                        formatPercent(call.returnPct),
                        style = MaterialTheme.typography.labelLarge.copy(fontFamily = TabularFigures),
                        color = LocalContentColor.current,
                    )
                }
            }
        }
        if (!all && calls.size > CallsShown) {
            KitButton(
                "Show all ${calls.size} calls",
                onClick = { all = true },
                kind = ButtonKind.ON_TONE,
                size = ButtonSize.SMALL,
                modifier = Modifier.padding(top = Space.s),
            )
        }
    }
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

/**
 * A price move with its sign, which [formatPrice] deliberately does not carry.
 *
 * Every other price in this app is a level or a close, where a sign would be noise; a move is the
 * one figure whose direction is half of what it says.
 */
private fun formatSignedPrice(value: Double): String =
    (if (value > 0) "+" else "") + formatPrice(value)

/** Enough recent calls to see who has been saying what, before the list is asked to open. */
private const val CallsShown = 5

/**
 * A row that leads to a card, pressed the way every pressable is since the 2026-11 redesign: it
 * eases down under the finger and springs back. Flat, like the rows it replaces - a row in a list
 * is information first, and the arrow or the outcome beside it is what says it leads somewhere.
 */
@Composable
private fun Modifier.pressableRow(onClick: () -> Unit): Modifier {
    val press = remember { MutableInteractionSource() }
    return this
        .pressScale(press)
        .clickable(interactionSource = press, indication = LocalIndication.current, onClick = onClick)
}
