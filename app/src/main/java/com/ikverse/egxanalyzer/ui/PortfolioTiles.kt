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
 * The top of the 5.0 Portfolio page: the tiles that say how the open trades are going, which of
 * them want a decision, and how the market moved - above the full position list, which follows.
 *
 * Every figure is a percentage. No trade size is recorded, so nothing here is money; averages weight
 * every trade the same, as "Your record" always has.
 *
 * @param heroHeight the hero tile's height in tabletop posture, sized to end at the crease so the
 *   chart sits on the upright half and everything pressed sits on the flat one. Null elsewhere.
 * @param onSelectTrade a press on a position row. Unfolded this selects the trade into the pane
 *   beside the tiles; on the cover screen it reveals the trade in the list below.
 */
@Composable
internal fun ColumnScope.PortfolioTiles(
    appState: AppState,
    portfolio: Portfolio,
    heroHeight: Dp?,
    onSelectTrade: (String) -> Unit,
) {
    val today = remember { LocalDate.now(ScheduleClock.ZONE) }
    val positions = portfolio.positions
    val needs = remember(positions, appState.appPreferences.approachThresholdPercent) {
        needsYou(positions, appState.appPreferences.approachThresholdPercent)
    }
    var decisionsOpen by appState.pages.portfolioDecisionsOpen

    HeroTile(appState.returnByDay, appState.indexByDay, portfolio, today, heroHeight, appState.pages.portfolioReturnRange, Modifier.popIn(0))

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Kit.TileGap)) {
        HitRateTile(portfolio, today, Modifier.weight(1f).popIn(1))
        NeedsYouTile(
            needs = needs,
            open = decisionsOpen,
            onToggle = { decisionsOpen = !decisionsOpen },
            modifier = Modifier.weight(1f).popIn(2),
        )
    }
    AnimatedVisibility(visible = decisionsOpen && needs.isNotEmpty(), enter = ExpandIn, exit = CollapseOut) {
        DecisionPanel(needs, appState, onSelectTrade)
    }

    PositionsTile(
        open = remember(positions) { positions.filter { it.open }.sortedWith(PortfolioOrder.URGENT.positions) },
        onSelect = onSelectTrade,
        modifier = Modifier.popIn(3),
    )

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Kit.TileGap)) {
        TodayTile(appState, Modifier.weight(1f).popIn(4))
        BestSourceTile(appState, Modifier.weight(1f).popIn(5))
    }

    AiReadTile(appState, positions, Modifier.popIn(6))
    ClosedThisMonthTile(positions, today, onSelectTrade, Modifier.popIn(7))
    FeedFooter(appState)
}

/** The cobalt tile: average open return, the open count, and the return line by day. */
@Composable
private fun HeroTile(
    points: List<ReturnPoint>,
    index: List<IndexPoint>,
    portfolio: Portfolio,
    today: LocalDate,
    height: Dp?,
    rangeState: androidx.compose.runtime.MutableState<ReturnRange>,
    modifier: Modifier = Modifier,
) {
    val p = palette
    val stats = portfolio.stats
    var range by rangeState
    val shown = remember(points, range, today) { points.within(range, today) }
    val market = remember(shown, index) { marketAlong(shown, index) }
    Tile(
        modifier = modifier.fillMaxWidth().then(if (height != null) Modifier.height(height) else Modifier),
        tone = TileTone.HERO,
        contentPadding = PaddingValues(vertical = 18.dp),
    ) {
        Text(
            "Open trades · ${stats.openCount} · average return",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Text(
            formatPercent(stats.openReturnPct),
            style = MaterialTheme.typography.displayLarge.copy(fontFamily = TabularFigures),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
        )
        Text(
            "Settled ${formatPercent(stats.settledReturnPct)} · ${stats.settledCount} trades",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
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
    }
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

/** The lime tile: win rate, and the last five weeks as bars that grow in. */
@Composable
private fun HitRateTile(portfolio: Portfolio, today: LocalDate, modifier: Modifier = Modifier) {
    val p = palette
    val weeks = remember(portfolio, today) { weeklyHitRates(portfolio.positions, 5, today) }
    val grow = rememberDrawIn(key = weeks, durationMs = 900)
    Tile(modifier = modifier.height(160.dp), tone = TileTone.HIGHLIGHT) {
        Text("Hit rate · 5 weeks", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.weight(1f))
        Text(
            formatPercent(portfolio.stats.winRate, signed = false),
            style = MaterialTheme.typography.displaySmall.copy(fontFamily = TabularFigures),
        )
        Row(
            Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .height(30.dp)
                .semantics {
                    contentDescription = weeks.joinToString(prefix = "Weekly hit rate: ") {
                        it.rate?.let { r -> "${(r * 100).toInt()}%" } ?: "none settled"
                    }
                },
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            weeks.forEach { week ->
                val share = (week.rate ?: 0.0).toFloat().coerceIn(0.06f, 1f)
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight((share * grow.value).coerceAtLeast(0.02f))
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (week.rate == null) p.onTile.copy(alpha = 0.2f) else p.onTile),
                )
            }
        }
    }
}

/** The coral tile: how many trades want a decision. Pressing it opens the decision panel. */
@Composable
private fun NeedsYouTile(needs: List<NeedsYouItem>, open: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val lines = needs.take(2).joinToString("\n") { it.line() }
    Tile(
        modifier = modifier.height(160.dp),
        tone = if (needs.isEmpty()) TileTone.CARD else TileTone.ALERT,
        onClick = if (needs.isEmpty()) null else onToggle,
        onClickLabel = if (open) "Hide the decisions" else "Show the decisions",
    ) {
        Text("Needs you", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.weight(1f))
        Text("${needs.size}", style = MaterialTheme.typography.displaySmall.copy(fontFamily = TabularFigures))
        Text(
            if (needs.isEmpty()) "Nothing is near a level or past its deadline" else lines,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun NeedsYouItem.line(): String = when (reason) {
    NeedReason.OVERDUE -> "${view.ticker} overdue ${view.overdueDays}d"
    NeedReason.NEAR_STOP -> "${view.ticker} ${"%.1f".format(distancePercent ?: 0.0)}% from stop"
    NeedReason.NEAR_TARGET2 -> "${view.ticker} ${"%.1f".format(distancePercent ?: 0.0)}% to T2"
}

/**
 * The decision panel the coral tile opens: each trade, how close it is, and what can be done now.
 * Record sale is the entrance the Record sale notification action uses. An overdue trade also offers
 * Follow the deadline again; a trade near a level offers Open trade, since deciding whether to sell
 * near a stop starts with looking at it.
 */
@Composable
private fun DecisionPanel(needs: List<NeedsYouItem>, appState: AppState, onSelectTrade: (String) -> Unit) {
    val p = palette
    Tile(modifier = Modifier.fillMaxWidth(), tone = TileTone.ALERT) {
        Text("Needs you · ${needs.size}", style = MaterialTheme.typography.headlineSmall)
        needs.forEach { item ->
            val view = item.view
            run {
                Tile(modifier = Modifier.padding(top = 12.dp).fillMaxWidth(), tone = TileTone.INK, contentPadding = PaddingValues(14.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(view.ticker, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text(
                            "${view.currentPrice?.let { "%.2f".format(it) } ?: "—"} · ${formatPercent(view.returnPct)}",
                            style = MaterialTheme.typography.titleSmall.copy(fontFamily = TabularFigures),
                            color = if ((view.returnPct ?: 0.0) < 0) p.loss else LocalContentColor.current,
                        )
                    }
                    Text(
                        item.detail(),
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalContentColor.current.copy(alpha = 0.75f),
                    )
                    if (item.reason == NeedReason.NEAR_STOP) {
                        val stop = view.position.stopLoss
                        val entry = view.position.entryPrice
                        val price = view.currentPrice
                        if (stop != null && price != null && entry > stop) {
                            TargetProgress(
                                progress = ((price - stop) / (entry - stop)).toFloat(),
                                losing = true,
                                label = "${"%.1f".format(item.distancePercent ?: 0.0)}% above the stop",
                                modifier = Modifier.padding(top = 10.dp),
                            )
                        }
                    }
                    Row(Modifier.padding(top = 12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KitButton(
                            "Record sale",
                            onClick = { appState.openPositionToSell(view.position.id) },
                            kind = ButtonKind.DANGER,
                            size = ButtonSize.SMALL,
                            modifier = Modifier.weight(1f),
                        )
                        if (item.reason == NeedReason.OVERDUE) {
                            KitButton(
                                "Follow the deadline",
                                onClick = { appState.setKeepOpen(view.position, keepOpen = false) },
                                kind = ButtonKind.ON_TONE,
                                size = ButtonSize.SMALL,
                                modifier = Modifier.weight(1f),
                            )
                        } else {
                            KitButton(
                                "Open trade",
                                onClick = { onSelectTrade(view.position.id) },
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

private fun NeedsYouItem.detail(): String {
    val channel = view.position.channel?.let { "$it · " }.orEmpty()
    return channel + when (reason) {
        NeedReason.OVERDUE -> "kept open ${view.overdueDays} days past its deadline"
        NeedReason.NEAR_STOP -> "${"%.1f".format(distancePercent ?: 0.0)}% from its stop at ${view.position.stopLoss?.let { "%.2f".format(it) }}"
        NeedReason.NEAR_TARGET2 -> "${"%.1f".format(distancePercent ?: 0.0)}% from target 2 at ${view.position.target2?.let { "%.2f".format(it) }}"
    }
}

/** The open trades, closest to target first, each with the bar from entry to target 2. */
@Composable
private fun PositionsTile(open: List<PositionView>, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    if (open.isEmpty()) return
    val p = palette
    Tile(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 10.dp, horizontal = 6.dp)) {
        Text("Open positions", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
        open.take(PositionsShown).forEachIndexed { index, view ->
            val path = targetPath(view)
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(Kit.PanelShape)
                    .clickable(role = Role.Button, onClickLabel = "Open this trade") { onSelect(view.position.id) }
                    .padding(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StockLogo(view.ticker, LogoSize.Row)
                    Column(Modifier.weight(1f)) {
                        Text(view.ticker, style = MaterialTheme.typography.titleMedium)
                        Text(
                            listOfNotNull(view.position.channel, "${view.sessionsHeld} sessions").joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = p.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            formatPercent(view.returnPct),
                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = TabularFigures),
                            color = if ((view.returnPct ?: 0.0) < 0) p.loss else p.gain,
                        )
                        Text(
                            view.currentPrice?.let { "%.2f".format(it) } ?: "—",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = TabularFigures),
                            color = p.muted,
                        )
                    }
                }
                if (path != null) {
                    TargetProgress(
                        progress = path.progress,
                        tickAt = path.target1At,
                        losing = path.losing,
                        label = "${view.ticker}: ${(path.progress * 100).toInt()}% of the way to target 2",
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
            if (index < open.take(PositionsShown).lastIndex) {
                com.ikverse.egxanalyzer.ui.kit.KitRule(Modifier.padding(horizontal = 12.dp), thickness = 1.dp, color = p.ground)
            }
        }
        if (open.size > PositionsShown) {
            Text(
                "${open.size - PositionsShown} more in the list below",
                style = MaterialTheme.typography.labelLarge,
                color = p.heroInk,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

private const val PositionsShown = 5

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
            BreadthBar(breadth.up, breadth.flat, breadth.down, Modifier.padding(top = 8.dp))
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

/** Trades sold or settled this calendar month, newest first. */
@Composable
private fun ClosedThisMonthTile(positions: List<PositionView>, today: LocalDate, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val p = palette
    val closed = remember(positions, today) {
        positions.filter { !it.open }
            .mapNotNull { v -> (v.position.exitDate ?: v.settledOn)?.let { v to it } }
            .filter { (_, on) -> on.year == today.year && on.month == today.month }
            .sortedByDescending { it.second }
    }
    if (closed.isEmpty()) return
    Tile(modifier = modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Closed this month", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            val avg = closed.mapNotNull { it.first.returnPct }.takeIf { it.isNotEmpty() }?.average()
            Text(formatPercent(avg), style = MaterialTheme.typography.titleMedium.copy(fontFamily = TabularFigures), color = if ((avg ?: 0.0) < 0) p.loss else p.gain)
        }
        closed.take(ClosedShown).forEach { (view, on) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(Kit.PanelShape)
                    .clickable(role = Role.Button, onClickLabel = "Open this trade") { onSelect(view.position.id) }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(view.ticker, style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(72.dp))
                Text("${view.status.label.lowercase()} · ${shortDate(on)}", style = MaterialTheme.typography.bodySmall, color = p.muted, modifier = Modifier.weight(1f))
                Text(
                    formatPercent(view.returnPct),
                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = TabularFigures),
                    color = if ((view.returnPct ?: 0.0) < 0) p.loss else p.gain,
                )
            }
        }
    }
}

private const val ClosedShown = 5

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
