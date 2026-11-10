package com.ikverse.egxanalyzer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ikverse.egxanalyzer.model.CallsInPlay
import com.ikverse.egxanalyzer.model.ChannelScore
import com.ikverse.egxanalyzer.model.PerformanceCalculator
import com.ikverse.egxanalyzer.model.PerformanceReport
import com.ikverse.egxanalyzer.model.PriceHealthReport
import com.ikverse.egxanalyzer.model.Scoring
import com.ikverse.egxanalyzer.ui.kit.ButtonKind
import com.ikverse.egxanalyzer.ui.kit.ButtonSize
import com.ikverse.egxanalyzer.ui.kit.Kit
import com.ikverse.egxanalyzer.ui.kit.KitButton
import com.ikverse.egxanalyzer.ui.kit.OutcomeStrip
import com.ikverse.egxanalyzer.ui.kit.Outcomes
import com.ikverse.egxanalyzer.ui.kit.Tile
import com.ikverse.egxanalyzer.ui.kit.TileTone
import com.ikverse.egxanalyzer.ui.kit.rememberDrawIn
import com.ikverse.egxanalyzer.ui.theme.palette

/**
 * The 5.0 Insights tiles above the record: who delivered, the ranking, what is still in play, and
 * what the feed cannot price. Every figure is one the scoring already measures; nothing here is
 * computed that the old cards did not already say somewhere.
 */

/** A source's calls as the kit's result bar divides them. Still running is what is left over. */
internal fun ChannelScore.outcomes(): Outcomes = Outcomes(
    hit = fullHits + partialHits,
    stopped = stopped,
    expired = expired,
    open = (calls - judged - notTradable).coerceAtLeast(0),
)

private fun ChannelScore.ranked(): Boolean = judged >= PerformanceCalculator.MINIMUM_JUDGED_TO_RANK

/**
 * The ink podium: the three sources that lead the ranking, the leader in the middle and tallest,
 * each bar as tall as the share of its settled calls that reached a target.
 *
 * Only sources with enough settled calls to rank stand on it - the same floor the ranking uses -
 * so it can never crown a source the list below calls too thin to rank.
 */
@Composable
internal fun InsightsPodium(
    report: PerformanceReport,
    photoFor: (String) -> String?,
    modifier: Modifier = Modifier,
) {
    val p = palette
    val leaders = report.channels.filter { it.ranked() }.take(3)
    Tile(modifier.fillMaxWidth(), tone = TileTone.INK, contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp)) {
        val soft = LocalContentColor.current.copy(alpha = 0.75f)
        Row(Modifier.fillMaxWidth()) {
            Text("Who delivered", style = MaterialTheme.typography.bodySmall, color = soft, modifier = Modifier.weight(1f))
            Text(
                "${report.channels.size} sources · ${report.tracked} calls",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = TabularFigures),
                color = soft,
            )
        }
        if (leaders.isEmpty()) {
            Text(
                "No source has ${PerformanceCalculator.MINIMUM_JUDGED_TO_RANK} settled calls yet, so none " +
                    "of them leads. The rates below are measured exactly; they are just resting on too " +
                    "little to rank.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 18.dp),
            )
        } else {
            val grow = rememberDrawIn(key = leaders.map { it.channel }, durationMs = 900)
            // Second, first, third, as a podium stands.
            val order = listOfNotNull(leaders.getOrNull(1), leaders.first(), leaders.getOrNull(2))
            Row(
                Modifier.fillMaxWidth().padding(top = 18.dp).height(PodiumHeight),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                order.forEach { score ->
                    val place = leaders.indexOf(score) + 1
                    val (bar, onBar) = when (place) {
                        1 -> p.highlight to p.onTile
                        2 -> p.hero to p.onHero
                        else -> p.alert to p.onTile
                    }
                    PodiumStep(score, place, photoFor(score.channel), bar, onBar, grow.value)
                }
            }
        }
        report.pricesTo?.let {
            Text(
                "Prices to ${AppDates.DayMonth.format(it)}",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = TabularFigures),
                color = soft,
                modifier = Modifier.padding(vertical = 10.dp),
            )
        } ?: Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun RowScope.PodiumStep(
    score: ChannelScore,
    place: Int,
    photoPath: String?,
    bar: Color,
    onBar: Color,
    grow: Float,
) {
    val share = ((score.anyTargetRate ?: 0.0) / 100.0).toFloat().coerceIn(0.12f, 1f)
    // The leader's step stands a fixed amount above the others whatever the rates, so a close race
    // still reads as a podium.
    val reach = if (place == 1) 1f else 0.78f
    Column(
        Modifier.weight(1f).fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.Bottom),
    ) {
        ChannelAvatar(score.channel, photoPath, if (place == 1) 48.dp else 44.dp)
        Text(
            "⁨${score.channel}⁩",
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(StepMax * share * reach + StepMin)
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0.5f, 1f)
                    scaleY = grow
                }
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                .background(bar),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                formatPercent(score.anyTargetRate, signed = false),
                style = (if (place == 1) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineSmall)
                    .copy(fontFamily = TabularFigures),
                color = onBar,
                maxLines = 1,
            )
        }
    }
}

private val PodiumHeight = 200.dp
private val StepMin = 40.dp
private val StepMax = 70.dp

/** The key to every result bar on the page, drawn once above the ranking. */
@Composable
internal fun OutcomeKey(modifier: Modifier = Modifier) {
    val p = palette
    Row(modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        listOf("Hit" to p.gain, "Stopped" to p.loss, "Expired" to p.faint, "Open" to p.hero).forEach { (label, colour) ->
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(colour))
                Text(label, style = MaterialTheme.typography.labelMedium, color = p.muted)
            }
        }
    }
}

/**
 * The ranking, in the order [PerformanceCalculator] produced it: the first four, then the rest
 * behind Show all. A row opens how that source is scored. Only sources with enough settled calls
 * carry a place number; the rest keep their position and say they are too thin to rank.
 */
@Composable
internal fun RankingTile(
    channels: List<ChannelScore>,
    photoFor: (String) -> String?,
    onExport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (channels.isEmpty()) return
    var showAll by rememberSaveable { mutableStateOf(false) }
    var explaining by remember { mutableStateOf<ChannelScore?>(null) }
    val places = remember(channels) {
        var place = 0
        channels.associate { it.channel to if (it.ranked()) ++place else null }
    }
    val shown = if (showAll) channels else channels.take(RankingPreview)
    Tile(modifier.fillMaxWidth(), contentPadding = PaddingValues(6.dp)) {
        shown.forEachIndexed { index, score ->
            if (index > 0) Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(1.dp).background(palette.ground))
            RankingRow(score, places[score.channel], photoFor(score.channel)) { explaining = score }
        }
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            if (channels.size > RankingPreview) {
                KitButton(
                    if (showAll) "Show the first $RankingPreview" else "Show all ${channels.size} sources",
                    onClick = { showAll = !showAll },
                    kind = ButtonKind.SECONDARY,
                    size = ButtonSize.SMALL,
                )
            }
            // The way to take the ranking off the phone. It changes nothing in the record.
            KitButton("Save as Excel", onClick = onExport, kind = ButtonKind.SECONDARY, size = ButtonSize.SMALL)
        }
    }
    explaining?.let { score ->
        ChannelScoreSheet(score, photoPath = photoFor(score.channel)) { explaining = null }
    }
}

private const val RankingPreview = 4

@Composable
private fun RankingRow(score: ChannelScore, place: Int?, photoPath: String?, onOpen: () -> Unit) {
    val p = palette
    val thin = !score.ranked()
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .clickable(role = Role.Button, onClickLabel = "Explain how this source is scored", onClick = onOpen)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                place?.toString() ?: "",
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = TabularFigures),
                color = p.faint,
                modifier = Modifier.width(PlaceWidth),
            )
            ChannelAvatar(score.channel, photoPath, 40.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    "⁨${score.channel}⁩",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (thin) {
                        "${score.calls} calls · too few judged to rank"
                    } else {
                        listOfNotNull(
                            "${score.calls} calls",
                            "avg ${formatPercent(score.averageReturn)}",
                            score.medianSessionsToHit?.let { "${formatPrice(it)} sessions" },
                        ).joinToString(" · ")
                    },
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = TabularFigures),
                    color = p.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                formatPercent(score.anyTargetRate, signed = false),
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = TabularFigures),
                color = if (thin) p.muted else p.ink,
            )
        }
        val split = score.outcomes()
        if (split.total > 0) {
            OutcomeStrip(split, Modifier.padding(start = PlaceWidth + 12.dp, top = 10.dp))
        }
    }
}

private val PlaceWidth: Dp = 22.dp

/**
 * The cobalt count of calls still in play, beside the lime one stock the record says is on offer
 * right now. With nothing in a buy zone the lime tile is dropped and the count takes the row.
 */
@Composable
internal fun InPlayRow(play: CallsInPlay, modifier: Modifier = Modifier) {
    val openStock = LocalOpenStock.current
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Kit.TileGap)) {
        Tile(Modifier.weight(1f).heightIn(min = InPlayHeight), tone = TileTone.HERO) {
            Text("Calls in play", style = MaterialTheme.typography.titleSmall)
            Text(
                play.count.toString(),
                style = MaterialTheme.typography.displaySmall.copy(fontFamily = TabularFigures),
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                if (play.count == 0) {
                    "Nothing still running"
                } else {
                    "${play.inEntryZone} in the entry zone\n${play.pastTarget1} past target 1"
                },
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = TabularFigures),
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        play.untaken.firstOrNull()?.let { call ->
            val ticker = Scoring.normalizeTicker(call.ticker)
            Tile(
                Modifier.weight(1f).heightIn(min = InPlayHeight),
                tone = TileTone.HIGHLIGHT,
                onClick = { openStock(ticker) },
                onClickLabel = "Open $ticker",
            ) {
                Text("Untaken buy zone", style = MaterialTheme.typography.titleSmall)
                Text(ticker, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 8.dp))
                Text(
                    "trading inside ${zoneText(call.entryLow, call.entryHigh)} right now",
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = TabularFigures),
                    modifier = Modifier.padding(top = 4.dp),
                )
                if (play.untaken.size > 1) {
                    Text(
                        "and ${play.untaken.size - 1} more",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Text("Open →", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

private fun zoneText(low: Double?, high: Double?): String = when {
    low != null && high != null && low != high -> "${formatPrice(minOf(low, high))}–${formatPrice(maxOf(low, high))}"
    else -> formatPrice(low ?: high)
}

private val InPlayHeight = 150.dp

/**
 * What the feed cannot price, in a dashed tile at the foot of the tiles - absent when every stock
 * is priced. The full account is in Settings, with the diagnostics.
 */
@Composable
internal fun FeedNote(health: PriceHealthReport, modifier: Modifier = Modifier) {
    if (health.clean) return
    val p = palette
    val first = health.faults.first()
    val names = health.faults.take(3).joinToString(", ") { it.ticker } +
        if (health.faults.size > 3) " +${health.faults.size - 3}" else ""
    val text = buildString {
        if (health.faults.size == 1 && first.newestSession != null) {
            append("Feed quiet for ${first.ticker} since ${AppDates.DayMonth.format(first.newestSession)}")
        } else {
            append("No usable price for $names")
        }
        if (health.callsHeld > 0) {
            append(" · ${health.callsHeld} ${if (health.callsHeld == 1) "call" else "calls"} can't be scored yet")
        }
    }
    Row(
        modifier
            .fillMaxWidth()
            .dashedOutline(p.faint)
            .padding(horizontal = 18.dp, vertical = 16.dp)
            .semantics { contentDescription = text },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("!", style = MaterialTheme.typography.titleMedium, color = p.muted)
        Text(text, style = MaterialTheme.typography.bodySmall, color = p.muted, modifier = Modifier.weight(1f))
    }
}

/** A dashed hairline round a tile's shape, for a note rather than a block of information. */
internal fun Modifier.dashedOutline(color: Color): Modifier = drawBehind {
    val width = 1.5.dp.toPx()
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(Kit.TileCorner.toPx()),
        style = Stroke(width = width, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
    )
}

/** A figure over its key, in a ground cell, for the tiles' small figure grids. */
@Composable
internal fun FigureCell(value: String, label: String, modifier: Modifier = Modifier, tone: Color? = null) {
    val p = palette
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(p.ground)
            .padding(horizontal = 8.dp, vertical = 10.dp),
    ) {
        Text(
            value,
            style = MaterialTheme.typography.titleLarge.copy(fontFamily = TabularFigures),
            color = tone ?: p.ink,
            maxLines = 1,
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = p.muted, maxLines = 2)
    }
}
