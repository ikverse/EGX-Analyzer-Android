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
import androidx.compose.runtime.MutableState
import androidx.compose.foundation.border
import com.ikverse.egxanalyzer.model.reachedTarget1
import com.ikverse.egxanalyzer.ui.kit.KitTag
import com.ikverse.egxanalyzer.ui.kit.tileSignals
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
 * The Insights tiles above the record: the ranking of who delivered, what is still in play, and
 * what the feed cannot price. Every figure is one the scoring already measures. The podium that
 * led the page in 5.0 went in 5.1: the three leaders stand on tiles of their own in the ranking,
 * where the podium had said the same thing a second time.
 */

/** A source's calls as the kit's result bar divides them. Still running is what is left over. */
internal fun ChannelScore.outcomes(): Outcomes = Outcomes(
    hit = fullHits + partialHits,
    stopped = stopped,
    expired = expired,
    open = (calls - judged - notTradable).coerceAtLeast(0),
)

private fun ChannelScore.ranked(): Boolean = judged >= PerformanceCalculator.MINIMUM_JUDGED_TO_RANK

/** The key to every result bar on the page, drawn once above the ranking. */
@Composable
internal fun OutcomeKey(modifier: Modifier = Modifier) {
    val p = palette
    Row(modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        listOf("Hit" to p.gain, "Stopped" to p.loss, "Expired" to p.faint, "Open" to p.heroInk).forEach { (label, colour) ->
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(colour))
                Text(label, style = MaterialTheme.typography.labelMedium, color = p.muted)
            }
        }
    }
}

/**
 * Who delivered: the ranking, in the order [PerformanceCalculator] produced it.
 *
 * The three that lead each stand on a tile of their own, in the page's three light colours, and the
 * rest share one white tile - so the answer the tab exists for is the first thing read, without a
 * podium restating it. The first four, then the rest behind Show all. A row opens that source's
 * record. Only sources with enough settled calls carry a place number; the rest keep their position
 * and say they are too new to rank.
 *
 * [selected] marks the row whose record is open beside the list, in book posture.
 */
@Composable
internal fun RankingTiles(
    channels: List<ChannelScore>,
    tracked: Int,
    photoFor: (String) -> String?,
    selected: String?,
    onOpen: (ChannelScore) -> Unit,
    onExport: () -> Unit,
    showAll: MutableState<Boolean>,
    modifier: Modifier = Modifier,
) {
    if (channels.isEmpty()) return
    val places = remember(channels) {
        var place = 0
        channels.associate { it.channel to if (it.ranked()) ++place else null }
    }
    val shown = if (showAll.value) channels else channels.take(RankingPreview)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Kit.TileGap)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Who delivered", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                "${channels.size} sources · $tracked calls",
                style = MaterialTheme.typography.bodySmall,
                color = palette.muted,
            )
        }
        shown.take(LeaderTones.size).forEachIndexed { index, score ->
            val tone = LeaderTones[index]
            Tile(Modifier.fillMaxWidth(), tone = tone, contentPadding = PaddingValues(6.dp)) {
                RankingRow(score, places[score.channel], photoFor(score.channel), tone, score.channel == selected) { onOpen(score) }
            }
        }
        val rest = shown.drop(LeaderTones.size)
        if (rest.isNotEmpty()) {
            Tile(Modifier.fillMaxWidth(), contentPadding = PaddingValues(6.dp)) {
                rest.forEachIndexed { index, score ->
                    if (index > 0) Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(1.dp).background(palette.ground))
                    RankingRow(score, places[score.channel], photoFor(score.channel), TileTone.CARD, score.channel == selected) { onOpen(score) }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            if (channels.size > RankingPreview) {
                KitButton(
                    if (showAll.value) "Show the first $RankingPreview" else "Show all ${channels.size} sources",
                    onClick = { showAll.value = !showAll.value },
                    kind = ButtonKind.SECONDARY,
                    size = ButtonSize.SMALL,
                )
            }
            // The way to take the ranking off the phone. It changes nothing in the record.
            KitButton("Save as Excel", onClick = onExport, kind = ButtonKind.SECONDARY, size = ButtonSize.SMALL)
        }
    }
}

private const val RankingPreview = 4

/** The leaders' fills, first to third. */
private val LeaderTones = listOf(TileTone.HIGHLIGHT, TileTone.TILE3, TileTone.TILE2)

/**
 * One source: its place, picture and name, then the two figures it is judged on - what a call was
 * worth, and how many finished calls reached a target. Calls posted, the sessions to a target and
 * the rate as a percentage are on its record, a press away.
 */
@Composable
private fun RankingRow(
    score: ChannelScore,
    place: Int?,
    photoPath: String?,
    tone: TileTone,
    selected: Boolean,
    onOpen: () -> Unit,
) {
    val thin = !score.ranked()
    val ink = LocalContentColor.current
    val soft = ink.copy(alpha = 0.66f)
    val signals = tileSignals(tone)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .then(if (selected) Modifier.border(2.dp, ink, RoundedCornerShape(22.dp)) else Modifier)
            .clickable(role = Role.Button, onClickLabel = "Open this source's record", onClick = onOpen)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                place?.toString() ?: "",
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = TabularFigures),
                color = soft,
                modifier = Modifier.width(PlaceWidth),
            )
            ChannelAvatar(score.channel, photoPath, 40.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "⁨${score.channel}⁩",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (thin) "${score.calls} calls" else "${score.judged} finished calls",
                    style = MaterialTheme.typography.labelMedium,
                    color = soft,
                    maxLines = 1,
                )
                if (thin) KitTag("Too new to rank", tone = soft)
            }
        }
        // The two figures on a line of their own, under the name, so the name keeps the whole width
        // of the row and neither figure is pushed past the tile's edge on the cover screen.
        Row(
            Modifier.fillMaxWidth().padding(start = FigureIndent, top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                val average = score.averageReturn
                Text(
                    formatPercent(average),
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = TabularFigures),
                    color = when {
                        thin || average == null -> soft
                        average >= 0 -> signals.gain
                        else -> signals.loss
                    },
                    maxLines = 1,
                )
                Text("earned per call", style = MaterialTheme.typography.bodySmall, color = soft, maxLines = 1)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "${score.reachedTarget1}/${score.judged}",
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = TabularFigures),
                    color = if (thin) soft else ink,
                    maxLines = 1,
                )
                Text("reached target", style = MaterialTheme.typography.bodySmall, color = soft, maxLines = 1)
            }
        }
        val split = score.outcomes()
        if (split.total > 0) {
            OutcomeStrip(split, Modifier.padding(start = PlaceWidth + 12.dp, top = 10.dp))
        }
    }
}

private val PlaceWidth: Dp = 22.dp

/** Where the name starts: past the place, the 40dp picture and the two 12dp gaps. */
private val FigureIndent: Dp = PlaceWidth + 12.dp + 40.dp + 12.dp

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
