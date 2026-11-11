package com.ikverse.egxanalyzer.ui

import com.ikverse.egxanalyzer.model.IndexPoint

import com.ikverse.egxanalyzer.model.PortfolioOrder
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ikverse.egxanalyzer.model.DayEventKind
import com.ikverse.egxanalyzer.model.EventTone
import com.ikverse.egxanalyzer.model.NeedReason
import com.ikverse.egxanalyzer.model.NeedsYouItem
import com.ikverse.egxanalyzer.model.Portfolio
import com.ikverse.egxanalyzer.model.PositionView
import com.ikverse.egxanalyzer.model.ReturnPoint
import com.ikverse.egxanalyzer.model.ReturnRange
import com.ikverse.egxanalyzer.model.ScheduleClock
import com.ikverse.egxanalyzer.model.StockOpinion
import com.ikverse.egxanalyzer.model.needsYou
import com.ikverse.egxanalyzer.model.opinionId
import com.ikverse.egxanalyzer.model.targetPath
import com.ikverse.egxanalyzer.model.weeklyHitRates
import com.ikverse.egxanalyzer.model.within
import com.ikverse.egxanalyzer.model.wonLost
import com.ikverse.egxanalyzer.model.PortfolioStats
import com.ikverse.egxanalyzer.model.PositionStatus
import com.ikverse.egxanalyzer.ui.kit.TileSignals
import com.ikverse.egxanalyzer.ui.kit.tileSignals
import androidx.compose.ui.graphics.Color
import com.ikverse.egxanalyzer.ui.kit.BreadthBar
import com.ikverse.egxanalyzer.ui.kit.ButtonKind
import com.ikverse.egxanalyzer.ui.kit.ButtonSize
import com.ikverse.egxanalyzer.ui.kit.Kit
import com.ikverse.egxanalyzer.ui.kit.KitButton
import com.ikverse.egxanalyzer.ui.kit.SegmentedControl
import com.ikverse.egxanalyzer.ui.kit.TargetProgress
import com.ikverse.egxanalyzer.ui.kit.Tile
import com.ikverse.egxanalyzer.ui.kit.TileTone
import com.ikverse.egxanalyzer.ui.kit.popIn
import com.ikverse.egxanalyzer.ui.kit.rememberDrawIn
import com.ikverse.egxanalyzer.ui.theme.palette
import java.time.LocalDate

/**
 * The top of the Portfolio page: how your trades have done, which of them are late, and - unless
 * [compact] - how the market moved, the best source, the AI's last read and the feed. The trades
 * themselves follow, drawn by the screen.
 *
 * Every figure is a percentage. No trade size is recorded, so nothing here is money; averages weight
 * every trade the same, as "Your record" always has.
 *
 * @param heroHeight the hero tile's height in tabletop posture, sized to end at the crease so the
 *   chart sits on the upright half and everything pressed sits on the flat one. Null elsewhere.
 * @param compact the book list and tabletop: the top tile without its chart, and nothing after it -
 *   those postures draw Needs you and the trades themselves.
 */
@Composable
internal fun ColumnScope.PortfolioRecord(
    appState: AppState,
    portfolio: Portfolio,
    heroHeight: Dp?,
    compact: Boolean,
) {
    val today = remember { LocalDate.now(ScheduleClock.ZONE) }
    val positions = portfolio.positions
    HeroTile(appState.returnByDay, appState.indexByDay, portfolio, today, heroHeight, appState.pages.portfolioReturnRange, compact, Modifier.popIn(0))
    if (compact) return
    NeedsYouTile(
        late = remember(positions) { overdueRoster(positions) },
        appState = appState,
        onOpen = { appState.pages.portfolioSelectedTrade.value = it },
        oneLine = false,
        modifier = Modifier.popIn(1),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Kit.TileGap)) {
        TodayTile(appState, Modifier.weight(1f).popIn(2))
        BestSourceTile(appState, Modifier.weight(1f).popIn(3))
    }
    AiReadTile(appState, positions, Modifier.popIn(4))
    FeedFooter(appState)
}

/**
 * The cobalt tile: what a trade has made on average, set beside what the EGX30 did over the range
 * the chart shows, then the return line by day and how many trades are open, won and lost.
 *
 * The two figures are not like for like and the caption does not pretend they are: the average is
 * per trade, and the index's move is over the chart's days, which is the only figure of the market's
 * the app keeps. Naming the range is what keeps the comparison honest.
 *
 * [compact] is the book list's and tabletop's: no chart and no range control, the counts on one line.
 */
@Composable
private fun HeroTile(
    points: List<ReturnPoint>,
    index: List<IndexPoint>,
    portfolio: Portfolio,
    today: LocalDate,
    height: Dp?,
    rangeState: androidx.compose.runtime.MutableState<ReturnRange>,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val p = palette
    val stats = portfolio.stats
    var range by rangeState
    val shown = remember(points, range, today) { points.within(range, today) }
    val market = remember(shown, index) { marketAlong(shown, index) }
    val (won, lost) = remember(portfolio) { wonLost(portfolio.positions) }
    Tile(
        modifier = modifier.fillMaxWidth().then(if (height != null && !compact) Modifier.height(height) else Modifier),
        tone = TileTone.HERO,
        contentPadding = PaddingValues(vertical = 18.dp),
    ) {
        Text(
            "Your trades so far",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Text(
            formatPercent(stats.averageReturnPct),
            style = (if (compact) MaterialTheme.typography.displaySmall else MaterialTheme.typography.displayLarge)
                .copy(fontFamily = TabularFigures),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
        )
        val move = if (market.isEmpty()) null else indexMove(shown, index)
        Text(
            "average per trade" + (move?.let { " · EGX30 did ${formatPercent(it)} over ${range.label}" } ?: ""),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        if (compact) {
            Text(
                "$won won · $lost lost · ${stats.openCount} open",
                style = MaterialTheme.typography.labelLarge.copy(fontFamily = TabularFigures),
                modifier = Modifier.padding(horizontal = 20.dp).padding(top = 8.dp),
            )
            return@Tile
        }
        ReturnLine(
            shown,
            market,
            Modifier
                .fillMaxWidth()
                .then(if (height != null) Modifier.weight(1f) else Modifier.height(96.dp))
                .padding(top = 12.dp),
            key = range,
        )
        if (market.isNotEmpty()) ChartLegend()
        SegmentedControl(
            options = ReturnRange.entries,
            selected = range,
            onSelect = { range = it },
            label = ReturnRange::label,
            modifier = Modifier.padding(horizontal = 14.dp).padding(top = 10.dp).fillMaxWidth(),
        )
        if (shown.isEmpty()) {
            Text(
                "The line starts once a held stock has a stored close.",
                style = MaterialTheme.typography.bodySmall,
                color = p.onHero.copy(alpha = 0.85f),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 12.dp)) {
            HeroCount(stats.openCount, "open now", Modifier.weight(1f))
            HeroCount(won, "won", Modifier.weight(1f))
            HeroCount(lost, "lost", Modifier.weight(1f))
        }
    }
}

@Composable
private fun HeroCount(value: Int, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value.toString(), style = MaterialTheme.typography.headlineSmall.copy(fontFamily = TabularFigures))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

/**
 * How far the EGX30 moved across the shown days: its change on the last of them against the first,
 * compounded off the same base [marketAlong] starts the line from. Null without two such days.
 */
internal fun indexMove(shown: List<ReturnPoint>, index: List<IndexPoint>): Double? {
    val byDay = index.associate { it.date to it.changePct }
    val placed = shown.mapNotNull { byDay[it.date] }
    if (placed.size < 2) return null
    val base = 1.0 + placed.first() / 100.0
    if (base <= 0.0) return null
    return ((1.0 + placed.last() / 100.0) / base - 1.0) * 100.0
}

/**
 * The return line on the hero tile, in the highlight, over a dashed zero line. Draws itself in on
 * first show and again when the range changes - a market event's motion, not an arrival's.
 */
@Composable
private fun ReturnLine(points: List<ReturnPoint>, market: List<Pair<Int, Double>>, modifier: Modifier, key: Any) {
    val p = palette
    val drawn = rememberDrawIn(key = key to points.size)
    val last = points.lastOrNull()
    Canvas(
        modifier.semantics {
            contentDescription = if (last == null) "No return line yet" else
                "Average return by day, ${points.size} days, latest ${formatPercent(last.averageReturnPct)}"
        },
    ) {
        if (points.size < 2) return@Canvas
        val values = points.map { it.averageReturnPct } + market.map { it.second }
        val lo = minOf(values.min(), 0.0)
        val hi = maxOf(values.max(), 0.0)
        val span = (hi - lo).takeIf { it > 0 } ?: 1.0
        fun y(v: Double) = (size.height - ((v - lo) / span * size.height)).toFloat()
        val zero = y(0.0)
        drawLine(
            p.onHero.copy(alpha = 0.45f),
            Offset(0f, zero),
            Offset(size.width, zero),
            strokeWidth = 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f)),
        )
        val step = size.width / (points.size - 1)
        val visible = (points.size * drawn.value).toInt().coerceAtLeast(1)
        // The market first, thin and under the trades' line, so where they cross the trades win.
        if (market.size >= 2) {
            val index = Path()
            market.filter { it.first <= visible }.forEachIndexed { i, (at, value) ->
                if (i == 0) index.moveTo(at * step, y(value)) else index.lineTo(at * step, y(value))
            }
            drawPath(index, p.onHero.copy(alpha = 0.7f), style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round))
        }
        val path = Path()
        points.take(visible + 1).forEachIndexed { i, point ->
            val x = i * step
            if (i == 0) path.moveTo(x, y(point.averageReturnPct)) else path.lineTo(x, y(point.averageReturnPct))
        }
        drawPath(path, p.highlight, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
    }
}

/**
 * The EGX30 along the shown days, as (day's place on the line, value) pairs.
 *
 * Started on the trades' own first point and moved by the index's change from there, so the two
 * lines leave one spot and the gap that opens is the trades against the market over the range shown.
 * A day the index has no figure for is skipped rather than drawn at zero.
 */
internal fun marketAlong(shown: List<ReturnPoint>, index: List<IndexPoint>): List<Pair<Int, Double>> {
    if (shown.size < 2 || index.isEmpty()) return emptyList()
    val byDay = index.associate { it.date to it.changePct }
    val placed = shown.mapIndexedNotNull { i, point -> byDay[point.date]?.let { i to it } }
    val first = placed.firstOrNull() ?: return emptyList()
    val base = 1.0 + first.second / 100.0
    if (base <= 0.0) return emptyList()
    val start = shown[first.first].averageReturnPct
    return placed.map { (i, change) -> i to (((1.0 + change / 100.0) / base - 1.0) * 100.0 + start) }
}

/** What the two lines on the hero are. */
@Composable
private fun ChartLegend() {
    val p = palette
    Row(
        Modifier.padding(horizontal = 20.dp).padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(width = 14.dp, height = 3.dp).background(p.highlight, Kit.Capsule))
        Text("Your trades", style = MaterialTheme.typography.labelMedium)
        Box(Modifier.size(width = 14.dp, height = 2.dp).background(p.onHero.copy(alpha = 0.7f), Kit.Capsule))
        Text("EGX30", style = MaterialTheme.typography.labelMedium)
    }
}

/**
 * The trades that are late, and what to do about each: the coral tile, one ink card per trade, a
 * sentence saying what happened and two buttons. Fed by [overdueRoster] - the whole portfolio,
 * unfiltered, so a date picked on the page cannot hide a trade that is late. Absent with none.
 *
 * Close-to-a-level used to be here too; it is a tag on the trade's own card now, where the reader
 * is already looking at the trade it is about.
 *
 * @param oneLine book posture's form: each trade's title and one question, and a press selects it
 *   into the pane beside the list rather than offering the buttons here.
 */
@Composable
internal fun NeedsYouTile(
    late: List<PositionView>,
    appState: AppState,
    onOpen: (String) -> Unit,
    oneLine: Boolean,
    modifier: Modifier = Modifier,
) {
    if (late.isEmpty()) return
    // The ink tile swaps lightness with the theme, so its money colours come from tileSignals: the
    // dark theme's red on it was 2.7:1.
    val signals = tileSignals(TileTone.INK)
    Tile(modifier = modifier.fillMaxWidth(), tone = TileTone.ALERT) {
        Text("Needs you · ${late.size}", style = MaterialTheme.typography.headlineSmall)
        late.forEach { view ->
            val (title, sentence) = lateWords(view)
            Tile(
                modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
                tone = TileTone.INK,
                contentPadding = PaddingValues(14.dp),
                onClick = if (oneLine) ({ onOpen(view.position.id) }) else null,
                onClickLabel = if (oneLine) "Open this trade" else null,
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (!oneLine) {
                        Text(
                            formatPercent(view.returnPct),
                            style = MaterialTheme.typography.titleSmall.copy(fontFamily = TabularFigures),
                            color = returnTone(view.returnPct, signals),
                        )
                    }
                }
                Text(
                    if (oneLine) "Sold it, or keep it open?" else sentence,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalContentColor.current.copy(alpha = 0.75f),
                )
                if (!oneLine) {
                    Row(Modifier.padding(top = 12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KitButton(
                            "I sold it",
                            // The entrance the Record sale notification action uses.
                            onClick = { appState.openPositionToSell(view.position.id) },
                            kind = ButtonKind.DANGER,
                            size = ButtonSize.SMALL,
                            modifier = Modifier.weight(1f),
                        )
                        if (view.keptOpen) {
                            KitButton(
                                "Stop keeping it open",
                                onClick = { appState.setKeepOpen(view.position, keepOpen = false) },
                                kind = ButtonKind.ON_TONE,
                                size = ButtonSize.SMALL,
                                modifier = Modifier.weight(1f),
                            )
                        } else {
                            KitButton(
                                "Keep it open",
                                onClick = {
                                    appState.pages.portfolioDialog.value =
                                        TradeDialogRequest(view.position.id, TradeDialogKind.KEEP_OPEN)
                                },
                                kind = ButtonKind.ON_TONE,
                                size = ButtonSize.SMALL,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** What a late trade's card says: a title, and the sentence under it. */
internal fun lateWords(view: PositionView): Pair<String, String> {
    val ticker = view.ticker
    if (view.keptOpen) {
        return "$ticker is ${view.overdueDays} ${if (view.overdueDays == 1L) "day" else "days"} past its deadline" to
            "You are keeping it open."
    }
    val ended = view.deadlineDate?.let { " ended ${shortDate(it)}" } ?: " ended"
    val window = view.position.windowSessions
    val how = if (view.marketStatus == PositionStatus.PARTIAL_TARGET_HIT || view.status == PositionStatus.PARTIAL_TARGET_HIT) {
        " after reaching target 1."
    } else {
        " without reaching a target or the stop."
    }
    return "$ticker has run out of time" to "Its $window trading days$ended$how"
}

/** A return's colour on a tile, from that tile's own signals. */
internal fun returnTone(ret: Double?, signals: TileSignals): Color =
    if ((ret ?: 0.0) < 0) signals.loss else signals.gain

/** Unfolded only: the best and the worst trade, each with when and how much. */
@Composable
internal fun BestWorstTile(stats: PortfolioStats, modifier: Modifier = Modifier) {
    val best = stats.best
    val worst = stats.worst
    if (best == null && worst == null) return
    Tile(modifier = modifier.fillMaxWidth()) {
        Text("Best and worst", style = MaterialTheme.typography.titleSmall)
        listOfNotNull(best, worst.takeIf { it !== best }).forEach { view ->
            val on = view.position.exitDate ?: view.settledOn ?: view.currentPriceOn
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                StockLogo(view.ticker, LogoSize.Row, Modifier.padding(end = 8.dp))
                Column(Modifier.weight(1f)) {
                    Text(view.ticker, style = MaterialTheme.typography.titleSmall)
                    on?.let { Text(shortDate(it), style = MaterialTheme.typography.labelSmall, color = palette.muted) }
                }
                Text(
                    formatPercent(view.returnPct),
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = TabularFigures),
                    color = PriceRole.forReturn(view.returnPct),
                )
            }
        }
    }
}

/** The ink tile: how the market split, and what the day did to the calls. */
@Composable
private fun TodayTile(appState: AppState, modifier: Modifier = Modifier) {
    val breadth = appState.marketBreadth
    val digest = appState.sessionDigest
    val hits = digest?.events?.count { it.kind.tone == EventTone.GAIN } ?: 0
    val stops = digest?.events?.count { it.kind == DayEventKind.TRADE_STOPPED || it.kind == DayEventKind.CALL_STOPPED || it.kind == DayEventKind.TRADE_STOPPED_AFTER_TARGET1 || it.kind == DayEventKind.CALL_STOPPED_AFTER_TARGET1 } ?: 0
    Tile(modifier = modifier.heightIn(min = 170.dp), tone = TileTone.INK) {
        Text(
            breadth?.let { "Session · ${shortDate(it.date)}" } ?: "Today",
            style = MaterialTheme.typography.titleSmall,
            color = LocalContentColor.current.copy(alpha = 0.75f),
        )
        if (breadth != null) {
            Text(
                "${breadth.up}↑ ${breadth.flat}· ${breadth.down}↓",
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = TabularFigures),
                modifier = Modifier.padding(top = 8.dp),
            )
            BreadthBar(breadth.up, breadth.flat, breadth.down, Modifier.padding(top = 8.dp), tone = TileTone.INK)
            Text(
                "of ${breadth.total} listings",
                style = MaterialTheme.typography.bodySmall,
                color = LocalContentColor.current.copy(alpha = 0.75f),
                modifier = Modifier.padding(top = 4.dp),
            )
        } else {
            Text("Breadth arrives after two sessions are stored.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        }
        Text(
            "$hits ${if (hits == 1) "call" else "calls"} hit a target\n$stops stopped out",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

/** The sunflower tile: the source whose calls reached a target most often. Opens Insights. */
@Composable
private fun BestSourceTile(appState: AppState, modifier: Modifier = Modifier) {
    val best = remember(appState.performance) {
        appState.performance.channels.filter { it.judged >= MinJudgedForBest && it.anyTargetRate != null }
            .maxByOrNull { it.anyTargetRate!! }
    }
    Tile(
        modifier = modifier.heightIn(min = 170.dp),
        tone = TileTone.TILE1,
        onClick = { appState.navigate(AppDestination.INSIGHTS) },
        onClickLabel = "Open Insights",
    ) {
        Text("Best source", style = MaterialTheme.typography.titleSmall)
        if (best == null) {
            Text("Scored once a source has ${MinJudgedForBest} settled calls.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        } else {
            Text(best.channel, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
            Text(
                formatPercent(best.anyTargetRate!! * 100, signed = false),
                style = MaterialTheme.typography.headlineMedium.copy(fontFamily = TabularFigures),
                modifier = Modifier.padding(top = 6.dp),
            )
            Text("of ${best.judged} calls hit a target", style = MaterialTheme.typography.bodySmall)
        }
    }
}

private const val MinJudgedForBest = 5

/**
 * The lilac tile: the newest saved Ask AI answer about a stock you hold. Both buttons open what is
 * already saved; nothing here can start a paid question.
 */
@Composable
private fun AiReadTile(appState: AppState, positions: List<PositionView>, modifier: Modifier = Modifier) {
    val newest = remember(appState.opinions, positions) {
        positions.mapNotNull { view ->
            val channel = view.position.channel ?: return@mapNotNull null
            appState.opinions[opinionId(view.ticker, view.position.recommendationDate, channel)]?.let { view to it }
        }.maxByOrNull { (_, opinion: StockOpinion) -> opinion.askedOn }
    } ?: return
    val (view, opinion) = newest
    val openStock = LocalOpenStock.current
    Tile(modifier = modifier.fillMaxWidth(), tone = TileTone.TILE2) {
        Row(Modifier.fillMaxWidth()) {
            Text("AI's last read · ${view.ticker}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(shortDate(opinion.askedOn), style = MaterialTheme.typography.bodySmall)
        }
        Text(
            opinion.headline,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KitButton(
                "Read the answer",
                onClick = { appState.openCall(view.position.id, NavStop(AppDestination.PORTFOLIO, positionId = view.position.id)) },
                size = ButtonSize.SMALL,
            )
            KitButton("Open stock", onClick = { openStock(view.ticker) }, kind = ButtonKind.ON_TONE, size = ButtonSize.SMALL)
        }
    }
}

/** Whether every stock the record names is priced, and when the prices came. */
@Composable
private fun FeedFooter(appState: AppState) {
    val p = palette
    val health = appState.priceHealth
    val text = if (health.clean) "Every stock priced" else "${health.faults.size} of ${health.stocksNamed} stocks have no usable price"
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(fontFamily = TabularFigures),
        color = if (health.clean) p.muted else p.loss,
        modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
    )
}
