package com.ikverse.egxanalyzer.ui.kit

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.ikverse.egxanalyzer.ui.LocalReduceMotion
import com.ikverse.egxanalyzer.ui.TabularFigures
import com.ikverse.egxanalyzer.ui.theme.palette

/**
 * The dot that says the session is open. It breathes a ring outward while [live], and is a plain
 * dot otherwise - and always a plain dot with reduce motion on.
 */
@Composable
fun LiveDot(live: Boolean, modifier: Modifier = Modifier, color: Color = palette.gain) {
    val still = LocalReduceMotion.current || !live
    val ring = if (still) 0f else {
        val t = rememberInfiniteTransition(label = "liveDot")
        val v by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1_600, easing = LinearEasing)), label = "ring")
        v
    }
    Canvas(modifier.size(18.dp)) {
        val r = 4.dp.toPx()
        if (ring > 0f) {
            drawCircle(color.copy(alpha = 0.5f * (1f - ring)), radius = r + ring * 5.dp.toPx())
        }
        drawCircle(if (live) color else color.copy(alpha = 0.4f), radius = r)
    }
}

/** One stock on the [TickerTape]: its ticker, the last price, and the day's change in percent. */
data class TapeQuote(val ticker: String, val price: String, val changePercent: Double)

/**
 * The strip of prices that scrolls under the tabs while the session is open.
 *
 * The caller decides whether it is drawn at all - it is only for trading hours, so the movement
 * itself says the market is open. With reduce motion on it does not scroll; it is a row that fits
 * what it fits.
 */
@Composable
fun TickerTape(quotes: List<TapeQuote>, modifier: Modifier = Modifier) {
    val p = palette
    val still = LocalReduceMotion.current
    val text = buildAnnotatedString {
        quotes.forEachIndexed { index, q ->
            if (index > 0) append("     ")
            append("${q.ticker} ${q.price} ")
            withStyle(SpanStyle(color = if (q.changePercent >= 0) p.gain else p.loss, fontWeight = FontWeight.Bold)) {
                append((if (q.changePercent >= 0) "+" else "−") + "%.1f%%".format(kotlin.math.abs(q.changePercent)))
            }
        }
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(30.dp)
            .semantics { contentDescription = "Prices: " + quotes.joinToString { "${it.ticker} ${it.price}" } },
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = TabularFigures),
            color = p.ink,
            maxLines = 1,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .then(
                    if (still) Modifier
                    else Modifier.basicMarquee(iterations = Int.MAX_VALUE, animationMode = MarqueeAnimationMode.Immediately, velocity = 40.dp),
                ),
        )
    }
}

/**
 * How far a trade has come from its entry toward target 2, with a tick at target 1.
 *
 * [progress] and [tickAt] are fractions of the entry-to-target-2 distance; the bar grows to
 * [progress] on first show. Drawn in loss red when [losing].
 */
@Composable
fun TargetProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    tickAt: Float? = null,
    losing: Boolean = false,
    label: String,
) {
    val p = palette
    val grow = rememberDrawIn(progress)
    val fill = if (losing) p.loss else p.gain
    Canvas(
        modifier
            .fillMaxWidth()
            .height(12.dp)
            .semantics { contentDescription = label },
    ) {
        val barH = 6.dp.toPx()
        val top = (size.height - barH) / 2
        val radius = CornerRadius(barH / 2)
        drawRoundRect(p.raised, Offset(0f, top), Size(size.width, barH), radius)
        val w = size.width * progress.coerceIn(0f, 1f) * grow.value
        if (w > 0f) drawRoundRect(fill, Offset(0f, top), Size(w.coerceAtLeast(barH), barH), radius)
        tickAt?.let { t ->
            val x = size.width * t.coerceIn(0f, 1f)
            drawRect(p.ink, Offset(x - 1.dp.toPx(), 0f), Size(2.dp.toPx(), size.height))
        }
    }
}

/**
 * How the market split today: risers, flat and fallers, as three runs of one bar. Gain and loss
 * colours, because this one bar is about money moving.
 */
@Composable
fun BreadthBar(up: Int, flat: Int, down: Int, modifier: Modifier = Modifier) {
    val p = palette
    val total = (up + flat + down).coerceAtLeast(1)
    Row(
        modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(Kit.Capsule)
            .semantics { contentDescription = "$up up, $flat flat, $down down" },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (up > 0) Box(Modifier.weight(up / total.toFloat()).height(8.dp).background(p.gain))
        if (flat > 0) Box(Modifier.weight(flat / total.toFloat()).height(8.dp).background(p.faint))
        if (down > 0) Box(Modifier.weight(down / total.toFloat()).height(8.dp).background(p.loss))
    }
}

/** How a source's calls ended. The four counts are drawn in order: hit, stopped, expired, open. */
data class Outcomes(val hit: Int, val stopped: Int, val expired: Int, val open: Int) {
    val total: Int get() = hit + stopped + expired + open
}

/** The result bar: four fixed verdicts in four fixed colours, growing in on first show. */
@Composable
fun OutcomeStrip(outcomes: Outcomes, modifier: Modifier = Modifier) {
    val p = palette
    val grow = rememberDrawIn(outcomes)
    val total = outcomes.total.coerceAtLeast(1)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(8.dp)
            .semantics {
                contentDescription = "${outcomes.hit} hit, ${outcomes.stopped} stopped, ${outcomes.expired} expired, ${outcomes.open} open"
            },
    ) {
        val gap = 2.dp.toPx()
        val width = size.width * grow.value
        var x = 0f
        listOf(outcomes.hit to p.gain, outcomes.stopped to p.loss, outcomes.expired to p.faint, outcomes.open to p.hero)
            .filter { it.first > 0 }
            .forEach { (count, colour) ->
                val w = (width - gap * 3) * count / total
                drawRoundRect(colour, Offset(x, 0f), Size(w.coerceAtLeast(0f), size.height), CornerRadius(size.height / 2))
                x += w + gap
            }
    }
}

/**
 * A placeholder that holds a tile's shape while the record loads, so nothing jumps when it
 * arrives. A light passes across it; with reduce motion on it is a still block.
 */
@Composable
fun Skeleton(modifier: Modifier = Modifier) {
    val p = palette
    val still = LocalReduceMotion.current
    val shift = if (still) 0f else {
        val t = rememberInfiniteTransition(label = "skeleton")
        val v by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1_400, easing = LinearEasing), RepeatMode.Restart), label = "shimmer")
        v
    }
    Canvas(modifier.clip(Kit.TileShape).semantics { contentDescription = "Loading" }) {
        drawRect(p.raised)
        if (!still) {
            val band = size.width * 0.35f
            val x = -band + (size.width + band) * shift
            drawRect(
                androidx.compose.ui.graphics.Brush.horizontalGradient(
                    listOf(Color.Transparent, p.card.copy(alpha = 0.6f), Color.Transparent),
                    startX = x,
                    endX = x + band,
                ),
            )
        }
    }
}
