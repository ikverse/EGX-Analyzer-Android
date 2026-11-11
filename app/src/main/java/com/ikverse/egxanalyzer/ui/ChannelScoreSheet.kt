package com.ikverse.egxanalyzer.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.text.style.TextAlign
import com.ikverse.egxanalyzer.ui.kit.Kit
import com.ikverse.egxanalyzer.ui.kit.KitSheet
import com.ikverse.egxanalyzer.ui.kit.LocalFoldPosture
import com.ikverse.egxanalyzer.ui.kit.OutcomeStrip
import com.ikverse.egxanalyzer.ui.kit.Tile
import com.ikverse.egxanalyzer.ui.kit.TileTone
import com.ikverse.egxanalyzer.ui.kit.rememberDrawIn
import com.ikverse.egxanalyzer.ui.theme.palette
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ikverse.egxanalyzer.model.PerformanceCalculator
import com.ikverse.egxanalyzer.model.ChannelScore
import com.ikverse.egxanalyzer.model.Scoring
import com.ikverse.egxanalyzer.model.reachedTarget1
import com.ikverse.egxanalyzer.model.Outcome
import com.ikverse.egxanalyzer.model.ScoredCall
import com.ikverse.egxanalyzer.model.believableReturn
import com.ikverse.egxanalyzer.model.plainWord
import com.ikverse.egxanalyzer.ui.kit.KitTag
import com.ikverse.egxanalyzer.ui.kit.MoreRow
import com.ikverse.egxanalyzer.ui.kit.tileColors
import com.ikverse.egxanalyzer.ui.kit.tileSignals
import androidx.compose.runtime.MutableState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.Role
import com.ikverse.egxanalyzer.ui.theme.extraColors

/**
 * A source's record: what it delivered, what following it would have made, its latest calls, and -
 * behind two closed rows - every other figure and how all of them are worked out.
 *
 * It opens from the source's row in the ranking, as a sheet, and in book posture it is the right
 * half of the page instead ([ChannelRecord] is the body either way). Until 5.1 it was "how this
 * source is scored" and carried no figure the ranking row did not; the ranking row now carries two
 * figures and this carries the rest, so it is a record rather than an explainer.
 *
 * **Bullets, not prose, and every one carries a colour that means something already** - the target's
 * green, the stop's red, the amber for out of time. They arrive in sequence, top to bottom, at
 * about a bullet every 45ms. Every rule stated here is the one the scorer applies; the sheet states
 * no verdict of its own.
 */
@Composable
internal fun ChannelScoreSheet(
    channel: ChannelScore,
    photoPath: String? = null,
    latestCalls: List<ScoredCall> = emptyList(),
    onOpenCall: (ScoredCall) -> Unit = {},
    rowState: (String) -> MutableState<Boolean>,
    onDismiss: () -> Unit,
) {
    KitSheet(onDismiss = onDismiss, posture = LocalFoldPosture.current, title = channel.channel) {
        ChannelRecord(channel, photoPath, latestCalls, onOpenCall, rowState)
    }
}

/** The body of [ChannelScoreSheet], so book posture can draw it as page content. */
@Composable
internal fun ColumnScope.ChannelRecord(
    channel: ChannelScore,
    photoPath: String?,
    latestCalls: List<ScoredCall>,
    onOpenCall: (ScoredCall) -> Unit,
    rowState: (String) -> MutableState<Boolean>,
) {
    val thin = channel.judged < PerformanceCalculator.MINIMUM_JUDGED_TO_RANK
    val p = palette
    // One counter for the whole record, so the reveal runs down the page in one sweep rather than
    // restarting in every section. Read and bumped during composition on purpose: it is derived
    // from position in the list and nothing else, so a recomposition rebuilds the same sequence.
    var step = 0

    // The ink head: who this is, and the three figures the record leads with. Its money colour
    // comes from tileSignals, because the ink tile swaps lightness with the theme - the lime label
    // that stood here was 1.03:1 in dark.
    Tile(Modifier.fillMaxWidth(), tone = TileTone.INK) {
        val soft = LocalContentColor.current.copy(alpha = 0.75f)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            ChannelAvatar(channel.channel, photoPath, 48.dp)
            // A first-strong isolate: the name is Arabic and the figures under it are not, and
            // without it a digit drifts into the name.
            Text(
                "⁨${channel.channel}⁩",
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            "${channel.calls} calls posted · ${channel.judged} finished",
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = TabularFigures),
            color = soft,
            modifier = Modifier.padding(top = Space.s),
        )
        val signals = tileSignals(TileTone.INK)
        Row(Modifier.padding(top = Space.m), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            HeadFigure(
                formatPercent(channel.averageReturn),
                "earned per call",
                when {
                    thin || channel.averageReturn == null -> null
                    channel.averageReturn >= 0 -> signals.gain
                    else -> signals.loss
                },
            )
            HeadFigure(formatPercent(channel.anyTargetRate, signed = false), "reached target")
            HeadFigure(formatPrice(channel.medianSessionsToHit), "trading days to get there")
        }
    }

    // What following every call would have made, under the two rules a reader could actually have
    // kept to. The better of the two is the lime one.
    Tile(Modifier.fillMaxWidth()) {
        Text("IF YOU HAD FOLLOWED EVERY CALL", style = MaterialTheme.typography.labelSmall, color = p.muted)
        if (channel.policyCalls > 0) {
            val sellAll = channel.sellAtTarget1Return
            val split = channel.splitReturn
            val splitWins = (split ?: Double.NEGATIVE_INFINITY) > (sellAll ?: Double.NEGATIVE_INFINITY)
            Row(Modifier.fillMaxWidth().padding(top = Space.s), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                PolicyCell("Sell everything at target 1", sellAll, best = !splitWins, Modifier.weight(1f))
                PolicyCell("Sell half, let half run", split, best = splitWins, Modifier.weight(1f))
            }
            Text(
                "Holding on paid off in ${channel.fullHits} of ${channel.reachedTarget1} calls.",
                style = MaterialTheme.typography.bodySmall,
                color = p.muted,
                modifier = Modifier.padding(top = Space.s),
            )
        } else {
            Text(
                "It takes calls that printed both targets and have finished. This source has none " +
                    "yet, so neither rule has a return to compare.",
                style = MaterialTheme.typography.bodySmall,
                color = p.muted,
                modifier = Modifier.padding(top = Space.s),
            )
        }
    }

    if (latestCalls.isNotEmpty()) {
        Tile(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)) {
            Text("LATEST CALLS", style = MaterialTheme.typography.labelSmall, color = p.muted)
            latestCalls.forEach { call ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(role = Role.Button, onClickLabel = "Open this call") { onOpenCall(call) }
                        .padding(vertical = Space.s),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.s),
                ) {
                    StockLogo(call.ticker, LogoSize.Row)
                    Text(call.ticker, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    KitTag(call.outcome.plainWord(), tone = call.outcome.tone())
                    Text(
                        formatPercent(call.believableReturn),
                        style = MaterialTheme.typography.labelLarge.copy(fontFamily = TabularFigures),
                        color = PriceRole.forReturn(call.believableReturn),
                    )
                }
            }
        }
    }

    MoreRow("More numbers", rowState("record:numbers")) {
        FigureGrid(
            listOf(
                Triple(formatPercent(channel.fullHitRate, signed = false), "reached target 2", PriceRole.target),
                Triple(channel.judged.toString(), if (channel.judged == 1) "settled call" else "settled calls", null),
                Triple(channel.averageRiskReward.asRatio(), "risk : reward", null),
                Triple(formatPrice(channel.medianSessionsToHit), "sessions to target 1", PriceRole.target),
                Triple(formatPrice(channel.medianSessionsToStop), "sessions to a stop", PriceRole.stop),
                Triple(channel.calls.toString(), "calls posted", null),
                Triple(channel.repeats.toString(), "re-posted, counted once", null),
                Triple(channel.notTradable.toString(), "not tradable", null),
            ),
        )
        channel.anyTargetRateFloor?.let { floor ->
            Text(
                "At least ${formatPercent(floor, signed = false)} reached target 1 - the lowest rate " +
                    "the calls behind it can honestly support.",
                style = MaterialTheme.typography.bodySmall,
                color = p.muted,
            )
        }
        // The same picture the ranking row carries, with its key, so the record is plainly about
        // that row.
        val split = channel.outcomes()
        if (split.total > 0) {
            OutcomeStrip(split)
            OutcomeKey()
        }
    }

    MoreRow("How this is scored", rowState("record:how")) {
        ExplainerSection(1, "How a call is judged") {
            Bullet(step++, PriceRole.entry, "The price has to trade into the buy zone first.") {
                "Until it does there is no call to judge. If it never gets there, the call is " +
                    "set aside - it counts neither for nor against this source."
            }
            Bullet(step++, PriceRole.target, "Target 1 reached is a partial hit.") {
                "The reader was in profit at a level the channel itself printed."
            }
            Bullet(step++, PriceRole.target, "Target 2 reached is a full hit.") {
                "The call has done everything it was printed to do, and it is finished."
            }
            Bullet(step++, PriceRole.stop, "The stop has to break by more than 2%.") {
                "A price touching the stop is not a stop-out - the channels say so themselves. " +
                    "A call that banks target 1 and then falls back to the stop keeps its " +
                    "partial hit: it did get there."
            }
            Bullet(step++, extraColors.expired, "Neither, for a long time, is out of time.") {
                "There is no deadline in the ordinary sense - a call runs until the market " +
                    "settles it. ${Scoring.JUDGING_HORIZON_SESSIONS} trading sessions is the " +
                    "outer edge, and a T+1 call gets ${Scoring.T_PLUS_ONE_WINDOW_SESSIONS}, " +
                    "because that is the deadline the channel printed itself."
            }
        }

        ExplainerSection(2, "What the numbers on the ranking mean") {
            Bullet(step++, p.hero, "The two rates are nested.") {
                "${channel.reachedTarget1} of ${channel.judged} settled calls reached " +
                    "target 1 - ${formatPercent(channel.anyTargetRate, signed = false)} - and " +
                    "${channel.fullHits} of those ran on to target 2, which is " +
                    "${formatPercent(channel.fullHitRate, signed = false)} of all judged " +
                    "calls. The second is part of the first, not something beside it."
            }
            Bullet(step++, PriceRole.muted, "The bar divides them differently, on purpose.") {
                "Each call has to sit in exactly one segment or the bar would not be a " +
                    "picture of the ${channel.judged} settled calls, so its first segment is " +
                    "target 1 and no further - ${channel.partialHits} calls - and the " +
                    "${channel.fullHits} that ran on are in the segment beside it. Add the " +
                    "two to get the ${channel.reachedTarget1} above. The rate counts a call " +
                    "that reached target 2 in both figures; the bar cannot count it twice."
            }
            Bullet(step++, PriceRole.forReturn(channel.averageReturn), "Per judged call is what orders the list.") {
                "What one call from this source has been worth on average: " +
                    "${formatPercent(channel.averageReturn)}. A hit rate on its own can be " +
                    "bought - print the target 2% above the entry and nearly every call " +
                    "reaches it, then the one that goes wrong takes back more than the rest " +
                    "made."
            }
            Bullet(step++, PriceRole.muted, "Risk : reward is the shape of the levels.") {
                "How far target 1 sits above the buy zone against how far the stop sits below " +
                    "it - ${channel.averageRiskReward.asRatio()} here. Measured over every " +
                    "call the source printed, whatever the market then did about it."
            }
            Bullet(step++, PriceRole.muted, "Sessions to a target, sessions to a stop.") {
                "The middle call each way, not the average. The pair is the point: quick to " +
                    "be wrong and slow to be right is a source asking you to sit through " +
                    "every loss and rush every gain."
            }
            channel.anyTargetRateFloor?.let { floor ->
                Bullet(step++, PriceRole.muted, "\"at least ${formatPercent(floor, signed = false)}\" is the rate the evidence bears.") {
                    "Six out of six is a true 100% resting on very little. This is the lowest " +
                        "rate the calls behind it can honestly support, and it covers target " +
                        "1 only."
                }
            }
        }

        ExplainerSection(3, "Sell at target 1, or let half run?") {
            Bullet(step++, PriceRole.target, "How often holding paid.") {
                channel.continuationRate?.let {
                    "${formatPercent(it, signed = false)} of the calls that reached target 1 " +
                        "went on to target 2 - ${channel.fullHits} of " +
                        "${channel.reachedTarget1}. This is not the target 2 rate on the card " +
                        "above: that one is measured over every judged call, including the " +
                        "ones that never reached target 1, where there was no decision to take."
                } ?: "No call from this source has reached target 1 yet, so there is nothing " +
                    "to say about holding past it."
            }
            channel.continuationRateFloor?.let { floor ->
                Bullet(step++, PriceRole.muted, "What that rate will actually bear.") {
                    "At least ${formatPercent(floor, signed = false)}, on " +
                        "${channel.reachedTarget1} calls. This rate rests on fewer calls than " +
                        "any other figure here - only the ones that got to target 1 - so it " +
                        "is the shakiest number on the card and the floor under it is wide."
                }
            }
            if (channel.policyCalls > 0) {
                Bullet(step++, PriceRole.forReturn(channel.sellAtTarget1Return), "What each rule was worth.") {
                    "Taking the whole position off at target 1 returned " +
                        "${formatPercent(channel.sellAtTarget1Return)} a call. Selling half " +
                        "there and letting the rest run returned " +
                        "${formatPercent(channel.splitReturn)}. Both over the same " +
                        "${channel.policyCalls} calls, so the difference between them is the " +
                        "decision and nothing else."
                }
                Bullet(step++, PriceRole.muted, "Why these are not the per-call figure above.") {
                    "That one books a partial hit at target 1 and a full hit at target 2 - it " +
                        "sells early on exactly the calls that were going to stall and holds " +
                        "on exactly the ones that were going to run. No one can trade that " +
                        "without knowing which is which in advance. These two are rules you " +
                        "could actually follow."
                }
                Bullet(step++, PriceRole.muted, "What is left out of the pair.") {
                    "Calls that printed only one target, where there is no second target to " +
                        "hold for and so no decision to price. And partial hits still running, " +
                        "whose un-sold half has not finished yet. " +
                        "${channel.judged - channel.policyCalls} of this source's " +
                        "${channel.judged} settled calls are out on one of those two counts."
                }
            } else {
                Bullet(step++, extraColors.expired, "Not enough to price the two rules.") {
                    "It takes calls that printed both targets and have finished. This source " +
                        "has none yet, so neither rule has a return to compare."
                }
            }
        }

        ExplainerSection(4, "Where this source stands") {
            if (thin) {
                Bullet(step++, extraColors.expired, "Too few settled calls to rank.") {
                    "${channel.judged} of this source's calls have finished, and it takes " +
                        "${PerformanceCalculator.MINIMUM_JUDGED_TO_RANK} before it can lead " +
                        "the list. The figures above are measured exactly - there is simply " +
                        "not much behind them yet."
                }
            } else {
                Bullet(step++, PriceRole.forReturn(channel.averageReturn), "Ranked on what a call was worth.") {
                    "${channel.judged} settled calls at ${formatPercent(channel.averageReturn)} " +
                        "each. Two sources with the same average sort by how much is behind " +
                        "it, so a long record is not overtaken by a good week."
                }
            }
            Bullet(step++, PriceRole.muted, "${channel.calls} calls posted, ${channel.judged} settled.") {
                "The rest are still running, or the market never gave them a verdict to have."
            }
            channel.repeats.takeIf { it > 0 }?.let { repeats ->
                Bullet(step++, PriceRole.muted, "$repeats re-posted, counted once.") {
                    "A channel that prints the same call every morning is making one bet, not " +
                        "five. The cards stay on their sessions - it did post them - and no " +
                        "rate counts them twice."
                }
            }
            channel.notTradable.takeIf { it > 0 }?.let { skipped ->
                Bullet(step++, PriceRole.muted, "$skipped not tradable.") {
                    "The buy zone never traded, or the stock has no prices at all. Neither " +
                        "says anything about this source, so neither is in any rate above."
                }
            }
        }

        ExplainerSection(5, "What is deliberately left out", tone = TileTone.TILE1) {
            Bullet(step++, PriceRole.muted, "A split or a bonus issue.") {
                "The levels were printed in the old money and the prices after it are in the " +
                    "new, so the call cannot be judged at all. It is set aside rather than " +
                    "recorded as the collapse it would otherwise look like."
            }
            Bullet(step++, PriceRole.muted, "What you did about the call.") {
                "Buying late, selling early or holding on moves nothing here. This measures " +
                    "the levels the channel printed; your own trades are the Portfolio's " +
                    "question."
            }
        }
    }

    Text(
        "Every figure here is measured from real closing prices. None of it is a " +
            "prediction, and none of it is advice.",
        style = MaterialTheme.typography.labelSmall,
        color = p.muted,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Space.s),
    )
}

@Composable
private fun HeadFigure(value: String, label: String, tone: Color? = null) {
    Column {
        Text(
            value,
            style = MaterialTheme.typography.headlineMedium.copy(fontFamily = TabularFigures),
            color = tone ?: LocalContentColor.current,
        )
        Text(label, style = MaterialTheme.typography.labelMedium, color = LocalContentColor.current.copy(alpha = 0.75f))
    }
}

/** One rule's return a call, the better of the two on lime. */
@Composable
private fun PolicyCell(label: String, value: Double?, best: Boolean, modifier: Modifier = Modifier) {
    val tone = if (best) TileTone.HIGHLIGHT else TileTone.CARD
    val (fill, ink) = tileColors(tone)
    val signals = tileSignals(tone)
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (best) fill else palette.ground)
            .padding(Space.m),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = ink)
        Text(
            formatPercent(value),
            style = MaterialTheme.typography.headlineSmall.copy(fontFamily = TabularFigures),
            color = when {
                value == null -> ink
                value >= 0 -> signals.gain
                else -> signals.loss
            },
        )
        Text("a call", style = MaterialTheme.typography.labelSmall, color = ink.copy(alpha = 0.7f))
    }
}

/** Two figures a row, in ground cells. */
@Composable
private fun FigureGrid(cells: List<Triple<String, String, Color?>>) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.s), modifier = Modifier.padding(bottom = Space.xs)) {
        cells.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                row.forEach { (value, label, tone) -> FigureCell(value, label, Modifier.weight(1f), tone) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** A numbered section of the sheet: a white tile (or [tone]), its number on a cobalt disc. */
@Composable
private fun ExplainerSection(
    number: Int,
    title: String,
    tone: TileTone = TileTone.CARD,
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = palette
    Tile(Modifier.fillMaxWidth(), tone = tone) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            val (disc, onDisc) = if (tone == TileTone.CARD) p.hero to p.onHero else p.ink to p.onInk
            Box(Modifier.size(24.dp).clip(CircleShape).background(disc), contentAlignment = Alignment.Center) {
                Text(number.toString(), style = MaterialTheme.typography.labelMedium.copy(fontFamily = TabularFigures), color = onDisc)
            }
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        Column(
            Modifier.padding(start = 32.dp, top = Space.s),
            verticalArrangement = Arrangement.spacedBy(Space.s),
            content = content,
        )
    }
}

/**
 * One rule, in a colour that already means something on the page behind this sheet.
 *
 * The claim is set in the ordinary text colour and the dot beside it carries the hue: a line of
 * coloured body text reads as a warning, where a coloured dot reads as a key. The explanation
 * underneath is a lambda so it is only built for a bullet that is actually drawn - the sections
 * above skip several depending on what this channel has done.
 *
 * @param index where in the sheet's single sequence this bullet falls, which is what staggers it.
 */
@Composable
private fun Bullet(index: Int, tone: Color, claim: String, detail: () -> String) {
    Reveal(index) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Space.s),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                Modifier
                    // Sits on the first line's optical centre rather than at its top, which is
                    // where a dot aligned to the row would land.
                    .padding(top = DotBaseline)
                    .size(DotSize)
                    .clip(CircleShape)
                    .background(tone),
            )
            Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Text(
                    claim,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    detail(),
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalContentColor.current.copy(alpha = 0.72f),
                )
            }
        }
    }
}

/**
 * Fades a bullet in and lifts it into place, a beat after the one above it.
 *
 * Read in a `graphicsLayer` lambda rather than in the composable body: read at composition it would
 * recompose every bullet on the sheet on every frame of the reveal, where in the draw lambda the
 * frame costs a repaint and nothing else. The same reasoning `AiMotion` sets out at length.
 *
 * The stagger is capped, so a sheet with a dozen bullets does not make the last of them wait half a
 * second longer than the first.
 */
@Composable
private fun Reveal(index: Int, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val progress by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(
            durationMillis = RevealMs,
            easing = UiEase,
            delayMillis = (index * StaggerMs).coerceAtMost(MaxStaggerMs),
        ),
        label = "bullet",
    )
    Box(
        Modifier.graphicsLayer {
            alpha = progress
            translationY = (1f - progress) * RevealLift.toPx()
        },
    ) {
        content()
    }
}

/** Beside the sheet's `headlineMedium` rate, a step down like the card's own. */
private val SheetRateSecondary = 18.sp

private val DotSize = 8.dp

/** Half a `bodyMedium` line, less half the dot: the dot's centre on the claim's centre. */
private val DotBaseline = 6.dp

/** Far enough to read as arriving, short enough not to read as sliding. */
private val RevealLift = 10.dp

private const val RevealMs = 220
private const val StaggerMs = 45
private const val MaxStaggerMs = 600
