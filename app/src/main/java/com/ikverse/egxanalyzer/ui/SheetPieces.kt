package com.ikverse.egxanalyzer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.ikverse.egxanalyzer.ui.kit.Kit
import com.ikverse.egxanalyzer.ui.kit.Tile
import com.ikverse.egxanalyzer.ui.kit.TileTone
import com.ikverse.egxanalyzer.ui.theme.palette
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/*
 * The pieces the two call sheets are built from: one trade in the Portfolio and one call in a
 * report. They were two layouts until 2026-11-11 - a different header, a different way of saying
 * where the price stood, levels on a chart in one and in a grid in the other - and a reader who
 * had learned one had learned nothing about the other. Both now open with who it is, then one
 * figure that says where it stands, then the levels on one ladder.
 */

/**
 * How many decimals a sheet prints its prices at: as many as the finest of them needs, and never
 * fewer than two.
 *
 * `formatPrice` trims every figure on its own, which is right in a table and wrong on a sheet about
 * one stock: 2.1, 2.2, 1.85 and 1.59 are four prices in one money and read as four different kinds
 * of number. Three is the most the app keeps.
 */
internal fun priceDecimals(vararg prices: Double?): Int =
    prices.filterNotNull().filterNot(Double::isNaN).maxOfOrNull(::decimalsNeeded)?.coerceIn(MinPriceDecimals, 3)
        ?: MinPriceDecimals

private const val MinPriceDecimals = 2

private fun decimalsNeeded(value: Double): Int {
    val thousandths = abs((value * 1000).roundToLong())
    return when {
        thousandths % 10 != 0L -> 3
        thousandths % 100 != 0L -> 2
        thousandths % 1000 != 0L -> 1
        else -> 0
    }
}

/** [value] at exactly [decimals] places, or the dash where there is no price. */
internal fun formatPrice(value: Double?, decimals: Int): String =
    if (value == null || value.isNaN()) Dash else String.format(Locale.US, "%.${decimals}f", value)

/**
 * Where the price stands against the levels, as the words printed over the ladder's arrow.
 *
 * The arrow is pinned to the end it passed, so past either end the words are what say how the
 * price got there: below the stop, or beyond the last target.
 */
internal fun nowWords(
    now: Double,
    stopLoss: Double?,
    target1: Double?,
    target2: Double?,
    decimals: Int,
    /** What the price is: "now" on anything still running, where it was sold or ended otherwise. */
    standing: String = "now",
): String {
    val price = formatPrice(now, decimals)
    val top = target2 ?: target1
    return when {
        stopLoss != null && now < stopLoss -> "$price $standing, below the stop"
        top != null && now > top -> "$price $standing, above target ${if (target2 != null) 2 else 1}"
        else -> "$price $standing"
    }
}

/** How far [level] sits from [entry], in percent. Null where either is missing. */
internal fun fromEntry(level: Double?, entry: Double?): Double? {
    if (level == null || entry == null || entry == 0.0) return null
    return (level - entry) / entry * 100
}

/**
 * When a card was printed, as the app writes every other date: "11 Nov, 10:54".
 *
 * The model copies the stamp off the card as it found it - "11-Nov-2026 10:54" on most - which is
 * a third date format on a sheet that already has one. Anything it cannot read is left exactly as
 * written rather than guessed at.
 */
internal fun printedWords(raw: String, today: LocalDate = LocalDate.now()): String {
    val text = raw.trim()
    for (pattern in StampPatterns) {
        try {
            val stamp = LocalDateTime.parse(text, pattern)
            return shortDate(stamp.toLocalDate(), today) + ", " + stamp.toLocalTime().format(ClockTime)
        } catch (_: DateTimeParseException) {
            // Not this shape; try the next.
        }
    }
    for (pattern in DayPatterns) {
        try {
            return shortDate(LocalDate.parse(text, pattern), today)
        } catch (_: DateTimeParseException) {
            // Not this shape; try the next.
        }
    }
    return text
}

private val ClockTime = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)

private val StampPatterns = listOf("d-MMM-uuuu HH:mm", "d MMM uuuu HH:mm", "uuuu-MM-dd HH:mm", "d/M/uuuu HH:mm")
    .map { DateTimeFormatter.ofPattern(it, Locale.ENGLISH) }

private val DayPatterns = listOf("d-MMM-uuuu", "d MMM uuuu", "uuuu-MM-dd", "d/M/uuuu")
    .map { DateTimeFormatter.ofPattern(it, Locale.ENGLISH) }

/** [figure] in the figures' face inside a sentence set in the words' face. */
internal fun AnnotatedString.Builder.figure(figure: String, color: Color = Color.Unspecified) {
    withStyle(SpanStyle(fontFamily = TabularFigures, color = color)) { append(figure) }
}

/**
 * Who a sheet is about: the logo, the ticker, and both names on lines of their own.
 *
 * Nothing shares the names' width but the [trailing] menu. The call sheet used to put the peak and
 * its percentage here, which is what cut both names off at "El- Nile Co. for ...".
 */
@Composable
internal fun SheetIdentity(
    ticker: String,
    arabic: String?,
    english: String?,
    modifier: Modifier = Modifier,
    onOpenStock: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StockLogo(ticker, LogoSize.Header)
        Column(Modifier.weight(1f)) {
            Row(
                if (onOpenStock != null) Modifier.clickable(onClick = onOpenStock) else Modifier,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(ticker, style = MaterialTheme.typography.headlineMedium)
                Egx33Badge(ticker, Modifier.padding(start = Space.s))
            }
            arabic?.takeIf(String::isNotBlank)?.let {
                Text(
                    // Isolated, so a name ending in a digit or a bracket keeps it on the right end.
                    "⁨$it⁩",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            english?.takeIf { it.isNotBlank() && it != ticker }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
    }
}

/**
 * The one figure a sheet leads with, and the sentences that say what it measures.
 *
 * [beside] is a few words on the figure's own line - the day a price is from. Everything in
 * [content] is drawn in the tile's own content colour, so a caller colours only what is a gain or
 * a loss, with [com.ikverse.egxanalyzer.ui.kit.tileSignals] for this tile's [tone].
 */
@Composable
internal fun StandsTile(
    tone: TileTone,
    figure: String,
    figureColor: Color,
    modifier: Modifier = Modifier,
    beside: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Tile(modifier.fillMaxWidth(), tone = tone, contentPadding = PaddingValues(20.dp)) {
        Text(
            "WHERE IT STANDS",
            style = MaterialTheme.typography.labelSmall,
            color = LocalContentColor.current.copy(alpha = QuietOnTile),
        )
        Spacer(Modifier.height(Space.xs))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            Text(
                figure,
                style = MaterialTheme.typography.displaySmall.copy(fontFamily = TabularFigures),
                color = figureColor,
            )
            beside?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalContentColor.current.copy(alpha = QuietOnTile),
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
        }
        Spacer(Modifier.height(Space.xs))
        content()
    }
}

/** How strongly a tile's secondary words are drawn against its own content colour. */
internal const val QuietOnTile = 0.76f

/** One level under the ladder: what it is, its price, and how far from the entry it sits. */
internal class LevelColumn(
    val name: String,
    val price: String,
    val note: String?,
    val tone: Color,
    /** The note is a figure, so it is set in the figures' face; a word or a date is not. */
    val noteIsFigure: Boolean = true,
)

/**
 * The levels of a call or a trade: the ladder, the price now over its arrow, and each level in a
 * column of its own underneath.
 *
 * The ladder alone printed each price beside its mark, which is honest about where a level sits and
 * unreadable when two sit close: a stop at 1.85 under an entry at 1.90 are fifty pixels apart on a
 * phone and their labels are sixty wide. The columns are four equal widths whatever the prices, so
 * nothing can collide, and each carries its name - the ladder printed none - and its distance from
 * the entry, which is what the six-cell grid under it used to be for.
 */
@Composable
internal fun LevelsBlock(
    stopLoss: Double?,
    entryLow: Double?,
    entryHigh: Double?,
    target1: Double?,
    target2: Double?,
    now: Double?,
    modifier: Modifier = Modifier,
    entryWord: String = "Entry",
    entryNote: String? = null,
    standing: String = "now",
) {
    val decimals = priceDecimals(stopLoss, entryLow, entryHigh, target1, target2, now)
    val entry = when {
        entryLow != null && entryHigh != null -> (entryLow + entryHigh) / 2
        else -> entryLow ?: entryHigh
    }
    val p = palette
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.s)) {
        now?.let {
            Text(
                buildAnnotatedString {
                    val words = nowWords(it, stopLoss, target1, target2, decimals, standing)
                    val price = formatPrice(it, decimals)
                    figure(price)
                    append(words.removePrefix(price))
                },
                style = MaterialTheme.typography.labelLarge,
                color = p.heroInk,
            )
        }
        PriceLadder(
            stopLoss = stopLoss,
            entryLow = entryLow,
            entryHigh = entryHigh,
            target1 = target1,
            target2 = target2,
            reached = now,
            compact = true,
        )
        val columns = buildList {
            stopLoss?.let {
                add(LevelColumn("Stop", formatPrice(it, decimals), fromEntry(it, entry)?.let(::formatPercent), p.loss))
            }
            entry?.let {
                // A range prints its low end as the price and its high end as the note: both ends
                // on one line are wider than a quarter of the cover screen.
                val range = entryLow != null && entryHigh != null && entryLow != entryHigh
                add(
                    LevelColumn(
                        name = entryWord,
                        price = formatPrice(if (range) entryLow else it, decimals),
                        note = if (range) "to " + formatPrice(entryHigh, decimals) else entryNote,
                        tone = p.ink,
                        noteIsFigure = range,
                    ),
                )
            }
            target1?.let {
                add(LevelColumn("Target 1", formatPrice(it, decimals), fromEntry(it, entry)?.let(::formatPercent), p.gain))
            }
            target2?.let {
                add(LevelColumn("Target 2", formatPrice(it, decimals), fromEntry(it, entry)?.let(::formatPercent), p.gain))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            columns.forEach { column ->
                Column(Modifier.weight(1f)) {
                    Text(column.name, style = MaterialTheme.typography.labelMedium, color = column.tone, maxLines = 1)
                    Text(
                        column.price,
                        style = MaterialTheme.typography.titleSmall.copy(fontFamily = TabularFigures),
                        color = p.ink,
                        maxLines = 1,
                    )
                    column.note?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelMedium.let { style ->
                                if (column.noteIsFigure) style.copy(fontFamily = TabularFigures) else style
                            },
                            color = if (column.noteIsFigure) column.tone else p.muted,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/**
 * A window of trading days as steps: one per day, filled for those already traded. Says the same
 * thing as the "N of M left" beside it, at a glance.
 */
@Composable
internal fun DaySteps(done: Int, total: Int, modifier: Modifier = Modifier) {
    val steps = total.coerceIn(1, MaxDaySteps)
    val filled = done.coerceIn(0, steps)
    val p = palette
    Row(
        modifier
            .fillMaxWidth()
            .semantics { contentDescription = "$filled of $steps trading days of the window traded" },
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        repeat(steps) { i ->
            Box(
                Modifier
                    .weight(1f)
                    .height(8.dp)
                    .clip(Kit.Capsule)
                    .background(if (i < filled) p.ink else p.line),
            )
        }
    }
}

/** Past this many days the steps would be slivers; the figure beside them still says it. */
private const val MaxDaySteps = 20

/**
 * Text a channel wrote, laid out in the direction it was written in.
 *
 * These were drawn right-aligned in a left-to-right paragraph, which puts the letters on the right
 * and leaves the punctuation, the percent signs and the minus signs where a left-to-right line
 * would have them: "؛" opening a line, "%9.38%." closing one. Taking the direction from the text
 * itself lays an Arabic note out as Arabic, and leaves an English one alone.
 */
@Composable
internal fun SourceText(text: String, style: TextStyle, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    Text(
        text,
        style = style.copy(textDirection = TextDirection.Content),
        textAlign = TextAlign.Start,
        color = color,
        modifier = modifier.fillMaxWidth(),
    )
}
