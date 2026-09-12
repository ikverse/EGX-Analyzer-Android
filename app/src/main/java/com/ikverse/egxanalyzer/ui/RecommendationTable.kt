package com.ikverse.egxanalyzer.ui

import com.ikverse.egxanalyzer.model.returnFrom
import com.ikverse.egxanalyzer.model.timing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
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
import com.ikverse.egxanalyzer.model.LatestPrice
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
 * weighted, so a wider window widens the columns rather than adding more of them. **Width adds
 * nothing at all now**, which is the end of that argument rather than a new position in it: the
 * table draws the same six figure columns at every size it is drawn at.
 *
 * There was a breakpoint here until the last of it was deleted, and the reason it went is worth
 * keeping. It was measured on this composable's own container and compared against 600dp - the
 * same figure as the page's own `TableMinWidth`, which gates whether this table is drawn instead of
 * cards. Its doc argued the two were different numbers because one was measured outside the report
 * card's insets. They are not: the gate and this table sit in the same `BoxWithConstraints`, with
 * nothing but a `Column` between them, so both read one width. The flag was true wherever the table
 * existed and false nowhere, and every branch on it was dead.
 *
 * So the one thing that decides whether a reader sees this table at all is `TableMinWidth`, on the
 * page, and what they see once they do is fixed. The row spends [Space].m either side, [SourceWidth]
 * and [ChevronWidth], and divides what is left over 6.6 shares at [EntryWeight]: at the narrowest
 * container that earns a table, 600dp, that is 64dp a share - 56dp of text inside [Space].xs on
 * either side, against a six-character price of roughly 50dp at 14sp mono. `Resistance` is the one
 * label that does not fit at full size there and [AutoSizeText] steps it down; see [HeaderLabel].
 */
@Composable
internal fun RecommendationTable(
    stocks: List<ConsolidatedRecommendation>,
    channelFor: (String?) -> String?,
    /**
     * Where the stock is now, for the heading of its block.
     *
     * A lambda rather than a map, beside `peakFor` and for the same reason: the report this table
     * draws and the price record are two stores, and handing the table the second one whole would
     * make every block recompose when any stock's price moved.
     */
    latestFor: (String) -> LatestPrice?,
    onSelectPoint: (ConsolidatedRecommendation, RecommendationDataPoint) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * The two dates, under each row.
     *
     * A toggle rather than the width breakpoint it used to be. These are true of a call and are not
     * what anyone judges it by - the file said so itself, above the column list - so appearing
     * because the screen got wider was the one arrangement that could not be right: the reader who
     * wants them cannot ask, and the reader who does not gets them at the cost of the figures that
     * decide something. A second line rather than more columns, so asking for them never puts the
     * table back into a sideways scroll.
     *
     * Support and resistance used to ride this line as well, on any row too narrow to column them.
     * They are columns at every width now, so what is left here is what dated the call.
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

    Box(
        modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                val top = coordinates.positionInWindow().y
                val height = coordinates.size.height.toFloat()
                pin = (viewportTop - top).coerceIn(0f, max(0f, height - pinnedHeight))
            },
    ) {
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
                    latest = latestFor(stock.stockCode),
                    showContext = showContext,
                    onSelectPoint = onSelectPoint,
                )
            }
        }
    }
}

/** The channel and its timing chip. The one column that holds words rather than figures. */
private val SourceWidth = 132.dp

/** Just enough for the chevron that says the row opens. */
private val ChevronWidth = 24.dp

/**
 * What the entry column gets for every 1 the other figure columns get.
 *
 * Entry is the only cell on the row that holds two prices and the dash between them - fifteen
 * characters where a target holds six - and it was given the same weight as the columns holding
 * half its content, so it was the one column that truncated while four beside it sat half empty.
 * 1.6 puts the longest real range inside the cell at the row's own type size at every width the
 * table is drawn at, which is what stops [AutoSizeText] from ever having to shrink it in practice.
 */
private const val EntryWeight = 1.6f

/**
 * A minimum rather than a fixed height, so a large font scale grows the row instead of clipping it.
 *
 * Every cell is held to one line per figure, so at any one scale the rows are the same height and
 * the column reads down. That is what the old table could not do: two of its columns wrapped at
 * anything above the default scale, and a wrapped row stood half again as tall as the one above it.
 *
 * 64dp and no longer 56dp, because the tallest cell is [SourceCell] and it is taller than 56dp: an
 * Arabic channel name, the gap, the timing pill and the air the cell now asks for either side come
 * to 61dp. At 56dp a row carrying a pill would have stood on its own content and a row whose call
 * has no timing would have stopped at the minimum, so the table would have read down in two
 * heights - the one failure this minimum exists to prevent.
 */
private val RowHeight = 64.dp

@Composable
private fun StockBlock(
    stock: ConsolidatedRecommendation,
    channelFor: (String?) -> String?,
    latest: LatestPrice?,
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
            StockHeading(stock, latest)
            ColumnHeader()
            stock.dataPoints.forEachIndexed { index, point ->
                CallRow(
                    point = point,
                    channel = channelFor(point.sourceMessageId) ?: point.sourceMessageId,
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

/**
 * Which stock the block below is about, and where that stock is now.
 *
 * **The price is the point of this row.** Identity took the leading edge and nothing held the
 * trailing one, so a band that named one stock sat over a rule running the full width of the card -
 * left-heavy over something symmetrical, which is what made it read as unfinished. What fills it is
 * not decoration: the block underneath is made entirely of prices the sources chose, and the one
 * price nobody chose - what the stock actually costs today - was the figure the table never
 * carried. An entry of 2.25 - 2.23 against a last close of 2.31 says the entry is gone, and a
 * target of 2.55 at +13.3% says how much of that is still there. Neither could be read off this
 * table before.
 *
 * Drawn as [LatestPrice] is drawn in the stock sheet's own heading: the key over the figure, in
 * [PriceRole.market], and the key itself saying the one thing that changes the figure's meaning -
 * a session still trading is a price, not a close. No day move beside it, which that heading does
 * carry: the move is measured off stored history through a suspend read per stock, and a table
 * draws every stock in the report at once.
 *
 * Absent where the feed has nothing, with no dash and no placeholder. A column of figures earns a
 * dash because the reader is scanning down it for one; a heading is read once.
 */
@Composable
private fun StockHeading(stock: ConsolidatedRecommendation, latest: LatestPrice?) {
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
            // Inside the press target with the logo and the code, as it is on the recommendation
            // card: the badge is a fact about this stock, so it belongs to the thing that opens it.
            // This heading was the one place in the app that drew a stock without it, which meant
            // the same company was marked Shariah-compliant on its card and on its sheet and
            // unmarked on the table between them.
            Egx33Badge(stock.stockCode, Modifier.padding(start = Space.s))
        }
        Column(Modifier.weight(1f).padding(start = Space.m)) {
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
        // Last, so the price closes the row's trailing edge whether or not the pill is there. The
        // pill moves and the price does not, which is the way round that keeps the one figure the
        // eye is looking for in the same place on every block of the report.
        if (latest != null) {
            Column(
                Modifier.padding(start = Space.m),
                horizontalAlignment = Alignment.End,
            ) {
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
            }
        }
    }
}

@Composable
private fun ColumnHeader() {
    Row(
        Modifier.fillMaxWidth().padding(start = Space.m, end = Space.m, bottom = Space.xs),
        verticalAlignment = Alignment.Bottom,
    ) {
        HeaderLabel("Source", Modifier.width(SourceWidth), TextAlign.Start)
        HeaderLabel("Entry", Modifier.weight(EntryWeight), TextAlign.End)
        HeaderLabel("Target 1", Modifier.weight(1f), TextAlign.End)
        HeaderLabel("Target 2", Modifier.weight(1f), TextAlign.End)
        HeaderLabel("Stop", Modifier.weight(1f), TextAlign.End)
        HeaderLabel("Support", Modifier.weight(1f), TextAlign.End)
        HeaderLabel("Resistance", Modifier.weight(1f), TextAlign.End)
        Spacer(Modifier.width(ChevronWidth))
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

/**
 * A column's name, which shrinks with the same rule its figures do.
 *
 * `Resistance` is the one header longer than the column it names is guaranteed to be, and a header
 * reading `Resistan…` is a column whose meaning has to be guessed at from the figures under it.
 */
@Composable
private fun HeaderLabel(label: String, modifier: Modifier, align: TextAlign) {
    AutoSizeText(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = align,
        modifier = modifier.fillMaxWidth().padding(horizontal = Space.xs),
    )
}

@Composable
private fun CallRow(
    point: RecommendationDataPoint,
    channel: String?,
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
                Modifier.weight(EntryWeight),
                value = entry(point),
                tone = PriceRole.entry,
                // Where risk to reward lives at every width now, rather than only below the
                // breakpoint it used to have a column above. It reads as a caption to the entry
                // rather than a figure of its own, which is what it is: a ratio measured from the
                // price paid.
                sub = riskReward(point),
                subTone = PriceRole.muted,
            )
            TargetCell(Modifier.weight(1f), point, point.target1, point.returnTp1Pct)
            TargetCell(Modifier.weight(1f), point, point.target2, point.returnTp2Pct)
            StopCell(Modifier.weight(1f), point)
            LevelCell(Modifier.weight(1f), point.support)
            LevelCell(Modifier.weight(1f), point.resistance)
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
    // 3dp its edge closed on the channel name instead of standing under it.
    //
    // The vertical padding is what keeps the pill off the row's floor. This gap and the pill are
    // 28dp between them, and the line above was budgeted at the 17dp a Latin `bodySmall` measures
    // - 45dp of a 56dp row, with 5.5dp of air either side. The names here are Arabic, and Noto
    // Naskh's ascent and descent make that same line measure about 25dp: the stack came to 53dp
    // and the pill sat 1.5dp off the bottom edge of the row, which reads as touching it. Air
    // asked for here rather than a line box trimmed to a number is the one form of this that
    // holds whatever the script or the font does - a trim tight enough to buy back 8dp would be
    // tighter than the ink of the hamza above `إ` and the tail of `ي`. The Latin case is unmoved
    // at 53dp inside the 56dp minimum; an Arabic row grows to 61dp and says so.
    Column(
        modifier.padding(horizontal = Space.xs, vertical = Space.xs),
        verticalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        // The one place in the table where the floor can actually be reached: channel names run
        // long and this column is fixed, so a name that will not fit at 9sp still ends in a dot.
        AutoSizeText(
            channel ?: Dash,
            style = MaterialTheme.typography.bodySmall,
            color = LocalContentColor.current,
            modifier = Modifier.fillMaxWidth(),
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
        AutoSizeText(
            value,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = TabularFigures),
            color = tone,
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth(),
        )
        sub?.let {
            AutoSizeText(
                it,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = TabularFigures),
                color = subTone,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth(),
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
 * A level the chart says the price has trouble getting through.
 *
 * The two figures the extra width goes to, in place of the risk to reward column and its bar.
 * Neither is the call's to make - a channel does not decide where a stock has been turned back, it
 * reads it off - so both are drawn in [PriceRole.market], the hue that already means a price the
 * market set rather than one the source chose. Entry, target and stop keep their three hues for the
 * levels that are decisions, which is the distinction the rest of the row is built on.
 *
 * One line and no second figure under it, so a support cannot be taken for a price with its
 * percentage beneath. The column head is the only label either of them needs.
 */
@Composable
private fun LevelCell(modifier: Modifier, level: Double?) {
    AutoSizeText(
        formatPrice(level),
        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = TabularFigures),
        color = if (level == null) PriceRole.muted else PriceRole.market,
        textAlign = TextAlign.End,
        modifier = modifier.fillMaxWidth().padding(horizontal = Space.xs),
    )
}

/**
 * The two dates, under the row they date.
 *
 * A line rather than two columns, so the toggle can never put the table back into the sideways
 * scroll this rebuild exists to end. Absent labels are dropped rather than drawn as dashes: this is
 * an aside, and an aside made mostly of em dashes is noise under every row in the report.
 *
 * Support and resistance were here too, on any row too narrow to column them. Nothing is that
 * narrow now - [LevelCell] draws both at every width the table is drawn at - and a figure printed
 * twice on one row teaches the reader that the two are different figures and sends them looking for
 * the difference.
 */
@Composable
private fun ContextLine(point: RecommendationDataPoint) {
    val parts = listOfNotNull(
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
