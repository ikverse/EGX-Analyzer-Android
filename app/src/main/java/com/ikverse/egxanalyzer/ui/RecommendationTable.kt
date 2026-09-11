package com.ikverse.egxanalyzer.ui

import com.ikverse.egxanalyzer.model.returnFrom
import com.ikverse.egxanalyzer.model.timing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.max
import com.ikverse.egxanalyzer.model.ConsolidatedRecommendation
import com.ikverse.egxanalyzer.model.RecommendationDataPoint

/**
 * The report's recommendations, one block per stock and one row per call.
 *
 * **It fits, and that is the whole of the redesign.** It was sixteen fixed-width columns behind a
 * horizontal scroll, and the width they came to was a function of nothing but how many of them had
 * been added: 889dp at the Fold's 614dp of container, 1277dp at the tablet's 682dp - because
 * crossing 620dp *appended* 388dp of context columns to buy 68dp of viewport. So the tablet showed
 * 49% of a row where the smaller Fold showed 64%, which is the same mistake `TodayCard` records
 * against itself, made a second time. Three things get the row under the width it actually has:
 *
 * - **A target and its return are one cell**, price over percent. They are read together and they
 *   were 162dp of two columns apiece; the three pairs cost 312dp instead of 486dp.
 * - **Timing rides the source**, as a chip under the channel's name, which is what kills the 96dp
 *   column whose two words wrapped and took the row's height with them.
 * - **The source image leaves the table.** Pressing the row opens [OccurrenceSheet], which already
 *   draws the screenshot at a size worth looking at - the 72dp thumbnail was a column spent
 *   restating that a press was available.
 *
 * What is left of a fixed width is [SourceWidth] and [ChevronWidth]; every figure column is
 * weighted, so a wider window widens the columns rather than adding more of them. See
 * [RiskColumnMinWidth] for the one thing extra width does add, and why that is not the old mistake
 * in a new place.
 */
@Composable
internal fun RecommendationTable(
    stocks: List<ConsolidatedRecommendation>,
    channelFor: (String?) -> String?,
    onSelectPoint: (ConsolidatedRecommendation, RecommendationDataPoint) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Support, resistance and the two dates, under each row.
     *
     * A toggle rather than the width breakpoint it used to be. These are true of a call and are not
     * what anyone judges it by - the file said so itself, above the column list - so appearing
     * because the screen got wider was the one arrangement that could not be right: the reader who
     * wants them cannot ask, and the reader who does not gets them at the cost of the figures that
     * decide something. A second line rather than four more columns, so asking for them never puts
     * the table back into a sideways scroll.
     */
    showContext: Boolean = false,
    /**
     * Drawn above the blocks and pinned with the scroll.
     *
     * The controls that decide what the table shows belong to the table: reaching them used to mean
     * scrolling back past every row to the top of the report.
     */
    toolbar: (@Composable () -> Unit)? = null,
) {
    if (stocks.isEmpty()) return
    // How far the table's own top has been scrolled past the top of the page's viewport. The
    // toolbar is pushed back down by exactly that much, so it looks pinned without leaving the
    // table: once the last row is gone it goes with it rather than hanging over the next card.
    val viewportTop = LocalViewportTop.current
    var pin by remember { mutableFloatStateOf(0f) }
    var pinnedHeight by remember { mutableFloatStateOf(0f) }

    // Measured here rather than from the window: the table sits inside a report card inside a page
    // inside the rail, and window width would promise room three insets have already spent.
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                val top = coordinates.positionInWindow().y
                val height = coordinates.size.height.toFloat()
                pin = (viewportTop - top).coerceIn(0f, max(0f, height - pinnedHeight))
            },
    ) {
        val wide = maxWidth >= RiskColumnMinWidth
        Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
            toolbar?.let {
                // Opaque and full width, because it slides across the blocks rather than pushing
                // them. The report card's own fill, since that is what it slides over.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .graphicsLayer { translationY = pin }
                        .zIndex(1f)
                        .onGloballyPositioned { pinnedHeight = it.size.height.toFloat() }
                        .background(MaterialTheme.colorScheme.surfaceContainer),
                ) { it() }
            }
            stocks.forEach { stock ->
                StockBlock(
                    stock = stock,
                    channelFor = channelFor,
                    wide = wide,
                    showContext = showContext,
                    onSelectPoint = onSelectPoint,
                )
            }
        }
    }
}

/**
 * Where risk to reward stops being a caption and becomes a column.
 *
 * The figure is on every row at every width - it is the entry cell's second line below this, its
 * own column above it - so nothing appears or disappears as a window changes size. That is the
 * distinction from the breakpoint this replaced: extra width buys the same row more room to say
 * what it was already saying, and never a figure the narrower screen was denied.
 *
 * 656dp is where [SourceWidth] + [ChevronWidth] plus five weighted cells of 100dp clears the
 * tablet's 682dp with room to grow. The Fold's 614dp lands under it, which is the intended split:
 * four columns there, five on the tablet and the emulator.
 */
private val RiskColumnMinWidth = 656.dp

/** The channel and its timing chip. The one column that holds words rather than figures. */
private val SourceWidth = 132.dp

/** Just enough for the chevron that says the row opens. */
private val ChevronWidth = 24.dp

/**
 * A minimum rather than a fixed height, so a large font scale grows the row instead of clipping it.
 *
 * Every cell is held to one line per figure, so at any one scale the rows are the same height and
 * the column reads down. That is what the old table could not do: two of its columns wrapped at
 * anything above the default scale, and a wrapped row stood half again as tall as the one above it.
 */
private val RowHeight = 56.dp

@Composable
private fun StockBlock(
    stock: ConsolidatedRecommendation,
    channelFor: (String?) -> String?,
    wide: Boolean,
    showContext: Boolean,
    onSelectPoint: (ConsolidatedRecommendation, RecommendationDataPoint) -> Unit,
) {
    // A card within a card goes one step up, the rule the Portfolio's session cards already follow.
    // It was a full-bleed `surfaceContainerHighest` band under a 2dp rule - the heaviest divider in
    // the app - which is what made a page of these read as a spreadsheet rather than as the report
    // it sits inside.
    Surface(
        Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
        border = cardOutline,
    ) {
        Column {
            StockHeading(stock)
            ColumnHeader(wide)
            stock.dataPoints.forEachIndexed { index, point ->
                CallRow(
                    point = point,
                    channel = channelFor(point.sourceMessageId) ?: point.sourceMessageId,
                    wide = wide,
                    showContext = showContext,
                    // The stripe does the separating on its own now. It shared the job with a rule
                    // under every row and a vertical rule beside the first column, and three grid
                    // devices at once is what a table looks like when none of them is trusted.
                    striped = index % 2 == 1,
                    onClick = { onSelectPoint(stock, point) },
                )
            }
        }
    }
}

@Composable
private fun StockHeading(stock: ConsolidatedRecommendation) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Space.m, vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The logo and the code press together, as they do on a card: one thing to the eye is one
        // target to the finger. The names beside them are deliberately left out - they wrap to two
        // lines and a press target that tall over a heading reads as the whole heading being a
        // button. See LocalOpenStock.
        val openStock = LocalOpenStock.current
        Row(
            Modifier.clickable { openStock(stock.stockCode) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StockLogo(stock.stockCode, LogoSize.Row, Modifier.padding(end = Space.s))
            // The anchor for everything under it, so it carries more weight than a row does.
            Text(
                stock.stockCode,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Column(Modifier.weight(1f).padding(start = Space.s)) {
            stock.stockNameArabic?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Dimmer than the Arabic name because the model supplies it from its own knowledge
            // rather than reading it from the source, and it is regularly wrong.
            stock.stockNameEnglish?.takeIf { it != stock.stockCode }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (stock.dataPoints.isNotEmpty() && stock.dataPoints.all(RecommendationDataPoint::isWatching)) {
            // Every occurrence, not any: a stock called for a buy by one source and merely watched
            // by another was labelled Watch list, which reads as though nobody had called it.
            //
            // The page's own hue, and no longer `tertiaryContainer` - which is the family
            // `PriceRole.target` is drawn from, so a status chip was wearing the colour that means
            // "a target price" on every other surface in the app. An accent is chrome and a signal
            // is a figure; this is the first.
            //
            // [OutlinePill], which is the one pill in the app: this drew its own ring at its own
            // corner and its own padding, so the table's marks and the cards' marks were two
            // families of one thing seen a scroll apart.
            OutlinePill(
                "Watch list",
                outline = MaterialTheme.colorScheme.primary.copy(alpha = 0.42f),
                textColor = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun ColumnHeader(wide: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(start = Space.m, end = Space.m, bottom = Space.xs),
        verticalAlignment = Alignment.Bottom,
    ) {
        HeaderLabel("Source", Modifier.width(SourceWidth), TextAlign.Start)
        HeaderLabel("Entry", Modifier.weight(1f), TextAlign.End)
        HeaderLabel("Target 1", Modifier.weight(1f), TextAlign.End)
        HeaderLabel("Target 2", Modifier.weight(1f), TextAlign.End)
        HeaderLabel("Stop", Modifier.weight(1f), TextAlign.End)
        if (wide) HeaderLabel("Risk / reward", Modifier.weight(1f), TextAlign.End)
        Spacer(Modifier.width(ChevronWidth))
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun HeaderLabel(label: String, modifier: Modifier, align: TextAlign) {
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = align,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(horizontal = Space.xs),
    )
}

@Composable
private fun CallRow(
    point: RecommendationDataPoint,
    channel: String?,
    wide: Boolean,
    showContext: Boolean,
    striped: Boolean,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(
                if (striped) {
                    // A step down from the block rather than the near-invisible
                    // `surfaceContainerLowest` at 0.4 it was, which is why the old table needed a
                    // rule under every row to find the one it was following.
                    MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f)
                } else {
                    Color.Transparent
                },
            )
            .clickable(onClick = onClick),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = RowHeight).padding(horizontal = Space.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SourceCell(channel, timing(point), Modifier.width(SourceWidth))
            StackedCell(
                Modifier.weight(1f),
                value = entry(point),
                tone = PriceRole.entry,
                // Below the risk column's width this is where risk to reward lives, so the figure
                // is on the row at every size. It reads as a caption to the entry rather than as a
                // figure of its own, which is what it is: a ratio measured from the price paid.
                sub = if (wide) null else riskReward(point),
                subTone = PriceRole.muted,
            )
            TargetCell(Modifier.weight(1f), point, point.target1, point.returnTp1Pct)
            TargetCell(Modifier.weight(1f), point, point.target2, point.returnTp2Pct)
            StopCell(Modifier.weight(1f), point)
            if (wide) RiskRewardCell(Modifier.weight(1f), point)
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(ChevronWidth).height(IconSize.Inline),
            )
        }
        if (showContext) ContextLine(point)
    }
}

/**
 * The channel, then what dated the call.
 *
 * Both start at the leading edge. The channel names are Arabic and the app's layout is not, so a
 * cell that aligned them to its trailing edge would put one column of this table out of step with
 * every other name the app draws - including the stock's own, two rows above it.
 */
@Composable
private fun SourceCell(channel: String?, timing: String?, modifier: Modifier) {
    // Space.s, and no longer the 3dp that was never on the spacing scale. That gap was set when
    // the timing was a line of small print carrying 1dp of padding; it is a 20dp pill now, and at
    // 3dp its edge closed on the channel name instead of standing under it. The row does not grow
    // - the name, this gap and the pill come to 45dp inside a 56dp row.
    Column(modifier.padding(horizontal = Space.xs), verticalArrangement = Arrangement.spacedBy(Space.s)) {
        Text(
            channel ?: Dash,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        timing?.let {
            // Neutral, and deliberately not a hue per timing. Every colour this app has spare means
            // something about a price - market blue most of all - and a T+1 chip borrowing one
            // would be spending a signal on chrome to save the reader reading two characters.
            FilledPill(
                it,
                container = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f),
                content = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A figure and, under it, the one thing that qualifies it.
 *
 * Right-aligned and monospaced, so a column can be compared down and not only read across - the
 * reason the old cells were, kept.
 */
@Composable
private fun StackedCell(
    modifier: Modifier,
    value: String,
    tone: Color,
    sub: String?,
    subTone: Color,
) {
    Column(modifier.padding(horizontal = Space.xs), horizontalAlignment = Alignment.End) {
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = TabularFigures),
            color = tone,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        sub?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = TabularFigures),
                color = subTone,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * A target, with what reaching it is worth.
 *
 * The two were columns apart with another target between them; they are one cell because they are
 * one fact, and because the pair is what the width came from. The prompt leaves the percentage null
 * unless a card prints one, so most of these are worked out here - and a derived figure keeps its
 * sign's own hue and is softened, never greyed, exactly as it was when it had a column to itself.
 */
@Composable
private fun TargetCell(
    modifier: Modifier,
    point: RecommendationDataPoint,
    target: Double?,
    stated: Double?,
) {
    val value = stated ?: returnFrom(point, target)
    val tone = PriceRole.forReturn(value)
    StackedCell(
        modifier,
        value = formatPrice(target),
        tone = PriceRole.target,
        sub = formatPercent(value).takeIf { target != null },
        // Only a figure is softened: with no target there is nothing derived, just the dash every
        // other cell draws for an absent value.
        subTone = if (value != null && stated == null) PriceRole.derived(tone) else tone,
    )
}

/**
 * The stop, with the move to it from the entry.
 *
 * The percentage is drawn in the stop's own colour whatever its sign, which is what the Risk %
 * column did: it is the distance to the level that ends the trade, and a figure that turned green
 * because a source printed it unsigned would be reporting a loss as a gain.
 */
@Composable
private fun StopCell(modifier: Modifier, point: RecommendationDataPoint) {
    val stated = point.riskPct
    val value = stated ?: returnFrom(point, point.stopLoss)
    StackedCell(
        modifier,
        value = formatPrice(point.stopLoss),
        tone = PriceRole.stop,
        sub = formatPercent(value).takeIf { point.stopLoss != null },
        subTone = if (value != null && stated == null) {
            PriceRole.derived(PriceRole.stop)
        } else {
            PriceRole.stop
        },
    )
}

/**
 * What the call risks against what it seeks, and the shape of it.
 *
 * The figure the table never carried. It is on the recommendation card and in the occurrence sheet
 * and it is the context a target cannot be read without - 90% at 0.3 to 1 is a losing source - so
 * the extra width a big screen has goes here rather than into Support and Resistance, which the old
 * column list itself described as not what you look at to judge a call.
 */
@Composable
private fun RiskRewardCell(modifier: Modifier, point: RecommendationDataPoint) {
    val ratio = point.riskRewardRatio()
    Column(
        modifier.padding(horizontal = Space.xs),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            ratio?.let { "1 : ${"%.1f".format(it)}" } ?: Dash,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = TabularFigures),
            color = if (ratio == null) PriceRole.muted else PriceRole.entry,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // A length rather than a number to divide in the head, which is the one thing a wide screen
        // can give a table that a narrow one cannot. Only where the ratio is real: an empty track
        // under a dash would draw a proportion out of levels the source never printed.
        ratio?.let { RiskRewardBar(it) }
    }
}

@Composable
private fun RiskRewardBar(ratio: Double) {
    // Clamped off both ends so a lopsided call still draws two segments: at 1 : 20 the risk side
    // rounds to nothing, and a bar that is entirely one colour says "no risk" rather than "little".
    val riskShare = (1.0 / (1.0 + ratio)).coerceIn(0.08, 0.92).toFloat()
    Row(
        Modifier
            .fillMaxWidth()
            .height(BarHeight)
            .clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)),
    ) {
        Box(Modifier.weight(riskShare).fillMaxHeight().background(PriceRole.stop))
        Box(Modifier.weight(1f - riskShare).fillMaxHeight().background(PriceRole.target))
    }
}

private val BarHeight = 4.dp

/**
 * Support, resistance and the two dates, on one muted line under the row that owns them.
 *
 * A line rather than four columns, so the toggle can never put the table back into the sideways
 * scroll this rebuild exists to end. Absent labels are dropped rather than drawn as dashes: this is
 * an aside, and an aside made mostly of em dashes is noise under every row in the report.
 */
@Composable
private fun ContextLine(point: RecommendationDataPoint) {
    val parts = listOfNotNull(
        point.support?.let { "Support ${formatPrice(it)}" },
        point.resistance?.let { "Resistance ${formatPrice(it)}" },
        point.date?.let { "Target date $it" },
        point.visibleSourceDate?.takeIf(String::isNotBlank)?.let { "Source date $it" },
    )
    if (parts.isEmpty()) return
    Text(
        parts.joinToString("  ·  "),
        style = MaterialTheme.typography.labelSmall,
        color = PriceRole.muted,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Space.m, end = Space.m, bottom = Space.s),
    )
}

/** Entry midpoint to target 1, over the distance to the stop. Null where the levels do not say. */
private fun riskReward(point: RecommendationDataPoint): String? =
    point.riskRewardRatio()?.let { "R:R  1 : ${"%.1f".format(it)}" }

private fun entry(point: RecommendationDataPoint): String {
    val low = point.buyPriceLow
    val high = point.buyPriceHigh
    return when {
        low != null && high != null && low != high -> "${formatPrice(low)} – ${formatPrice(high)}"
        else -> formatPrice(point.buyPrice ?: low ?: high)
    }
}
