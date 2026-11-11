package com.ikverse.egxanalyzer.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import com.ikverse.egxanalyzer.ui.kit.popSpring
import kotlin.math.roundToInt
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.unit.sp
import com.ikverse.egxanalyzer.model.AnalysisProgress
import com.ikverse.egxanalyzer.model.ScheduleClock
import com.ikverse.egxanalyzer.model.SessionState
import com.ikverse.egxanalyzer.ui.kit.LiveDot
import com.ikverse.egxanalyzer.ui.theme.palette
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * The 5.0 header's parts: the session line, the tab row and the run's progress. [PageHeader]
 * arranges them; each lives here so it can be read, and tested, on its own.
 */

/** The market's state, re-read every half minute so "open" turns to "closed" at the bell. */
@Composable
internal fun rememberSessionState(): SessionState {
    val state by produceState(ScheduleClock.sessionState()) {
        while (true) {
            delay(SessionTickMillis)
            value = ScheduleClock.sessionState()
        }
    }
    return state
}

private const val SessionTickMillis = 30_000L

/**
 * What the session line says: "Session open · closes 14:30", or when it next opens - by time
 * alone today, "tomorrow" for tomorrow, and the weekday beyond that.
 */
internal fun sessionText(state: SessionState, now: Instant = Instant.now(), zone: ZoneId = ScheduleClock.ZONE): String {
    val next = state.nextChange?.atZone(zone)
    val time = next?.toLocalTime()?.let(ScheduleClock::clock)
    if (state.open) return if (time != null) "Session open · closes $time" else "Session open"
    if (next == null || time == null) return "Market closed"
    val today = now.atZone(zone).toLocalDate()
    val day = when (next.toLocalDate()) {
        today -> ""
        today.plusDays(1) -> "tomorrow "
        else -> next.dayOfWeek.getDisplayName(JavaTextStyle.SHORT, Locale.ENGLISH) + " "
    }
    return "Market closed · opens $day$time"
}

/** The live dot and the line beside it. */
@Composable
internal fun SessionLine(modifier: Modifier = Modifier) {
    val state = rememberSessionState()
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        LiveDot(live = state.open)
        Text(
            sessionText(state),
            style = MaterialTheme.typography.bodySmall,
            color = palette.muted,
            maxLines = 1,
        )
    }
}

/**
 * One change of tab, as the header draws it: which name is growing, which is shrinking, and how
 * far along they are.
 *
 * **Held by the shell and outliving every page**, because a page - header included - is built
 * afresh each time its tab is pressed. The mockups draw the same header on all five pages with
 * only the large name moving, so a tab change has to read as one header holding still while the
 * emphasis passes from one name to another. That needs something that was there before the press
 * and is still there after it: this, and the row's own scroll position, which is kept here for
 * the same reason.
 */
@Stable
internal class TabTravel(start: AppDestination) {
    /** The tab being left, or null before the first change. */
    var from by mutableStateOf<AppDestination?>(null)
        private set

    /** The tab being arrived at. */
    var to by mutableStateOf(start)
        private set
    private val progress = Animatable(1f)

    /** Where the tab row is scrolled to, carried from one page's header to the next. */
    val scroll = ScrollState(0)

    /** 0 as the travel starts, 1 once it has landed, and a little past 1 on the way. */
    val fraction: Float get() = progress.value

    /**
     * The neighbour a swipe is heading for while the finger is still down, and how far toward it
     * the emphasis has moved (0 none, 1 all the way). Null when no swipe is under way. See
     * `TabSwipe`.
     */
    var peek by mutableStateOf<AppDestination?>(null)
        private set
    var peekFraction by mutableFloatStateOf(0f)
        private set

    /** Moves the emphasis part of the way toward [destination], following a finger. */
    fun preview(destination: AppDestination?, fraction: Float) {
        peek = destination
        peekFraction = if (destination == null) 0f else fraction
    }

    /**
     * How large [destination]'s name is drawn on the page showing [current]: 0 small, 1 large.
     *
     * A page is composed a frame before [goTo] is told about it. For that frame the row is drawn
     * exactly as the page being left drew it, so the travel starts from what was on screen.
     */
    fun emphasis(destination: AppDestination, current: AppDestination): Float = when {
        // The first frame of a swipe's arrival: the row is drawn where the finger left it.
        to != current && peek == current -> when (destination) {
            current -> peekFraction
            to -> 1f - peekFraction
            else -> 0f
        }
        to != current -> if (destination == to) 1f else 0f
        // A finger is moving the emphasis toward a neighbour.
        peek != null -> when (destination) {
            to -> 1f - peekFraction
            peek -> peekFraction
            else -> 0f
        }
        destination == to -> progress.value
        destination == from -> 1f - progress.value
        else -> 0f
    }

    /** Whether [current]'s page is the one this travel has been told about. */
    fun knows(current: AppDestination): Boolean = to == current

    suspend fun goTo(destination: AppDestination, still: Boolean) {
        if (destination == to) return
        // A swipe that committed has already moved the emphasis part of the way: finish from there.
        val begin = if (peek == destination) peekFraction else 0f
        from = to
        to = destination
        preview(null, 0f)
        if (still) {
            progress.snapTo(1f)
        } else {
            progress.snapTo(begin)
            // **One frame held before the motion starts** (2026-11-11). The arriving page is built
            // on the press, header and all, and that frame and the next are the slowest the app
            // draws; a spring started on them spent its first steps on frames that never showed.
            withFrameNanos { }
            progress.animateTo(1f, popSpring())
        }
    }
}

/** The shell's [TabTravel], or null where a header is drawn on its own - a test, a preview. */
internal val LocalTabTravel = staticCompositionLocalOf<TabTravel?> { null }

/**
 * The tab row: the page's own name large, the other four small beside it, each pressable.
 *
 * **A change of tab is the emphasis moving along this row** ([TabTravel]): the name pressed grows
 * to the large size and takes the ink while the one being left shrinks and greys, on the spring
 * the tiles arrive on. On the cover screen the row is wider than the window and scrolls sideways;
 * it is scrolled so **the tab before the current one still shows at the row's start**, as the
 * mockups draw it - Results reads `Analyze Results Insights Settings` - and that scroll travels
 * with the emphasis rather than jumping. By its own scroll state and never by `bringIntoView`.
 *
 * Pressing the tab already showing takes that page to the top; pressing another goes there.
 */
@Composable
internal fun HeaderTabs(
    current: AppDestination,
    collapse: Float,
    onSelect: (AppDestination) -> Unit,
    onReselect: (AppDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val travel = LocalTabTravel.current
    val scroll = travel?.scroll ?: rememberScrollState()
    // **Each name's width at rest and small**, written by [TabName] only when it differs - which is
    // once per collapse step, and never during a change of tab. Every name before the one the row
    // is scrolled to is small at rest, so these alone place it, and nothing about the row has to be
    // read back off the screen on every frame of a travel, which is what it did until 2026-11-11.
    val smallWidths = remember { mutableStateMapOf<AppDestination, Int>() }
    val gap = with(LocalDensity.current) { TabGap.roundToPx() }
    LaunchedEffect(current, travel) {
        fun startOf(destination: AppDestination): Int? {
            var x = 0
            for (earlier in AppDestination.entries.take(AppDestination.entries.indexOf(destination))) {
                x += (smallWidths[earlier] ?: return null) + gap
            }
            return x
        }
        snapshotFlow {
            // Not told about this page yet: hold the row where the last page left it.
            if (travel != null && !travel.knows(current)) return@snapshotFlow null
            val starts = AppDestination.entries.associateWith { startOf(it) }
            val landing = starts[tabBefore(current)] ?: return@snapshotFlow null
            // A finger heading for a neighbour carries the row with it.
            travel?.peek?.let { peek ->
                val toward = starts[tabBefore(peek)] ?: landing
                return@snapshotFlow (landing + (toward - landing) * travel.peekFraction).roundToInt()
            }
            val leaving = travel?.from?.let { starts[tabBefore(it)] } ?: landing
            val along = travel?.fraction?.coerceIn(0f, 1f) ?: 1f
            (leaving + (landing - leaving) * along).roundToInt()
        }.collect { target -> if (target != null) scroll.scrollTo(target) }
    }
    // Both at their uncollapsed sizes, and fixed: the collapse and the travel are scales on them.
    val large = MaterialTheme.typography.headlineLarge.copy(fontSize = ExpandedTab, letterSpacing = ExpandedTracking)
    val small = MaterialTheme.typography.titleLarge.copy(fontSize = OtherTab, fontWeight = FontWeight.SemiBold)
    Row(
        modifier
            .horizontalScroll(scroll)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(TabGap),
        verticalAlignment = Alignment.Bottom,
    ) {
        AppDestination.entries.forEach { destination ->
            val selected = destination == current
            TabName(
                label = destination.label,
                small = small,
                large = large,
                collapse = collapse,
                emphasis = { travel?.emphasis(destination, current) ?: if (selected) 1f else 0f },
                onSmallWidth = { if (smallWidths[destination] != it) smallWidths[destination] = it },
                modifier = Modifier
                    .then(if (selected) Modifier.semantics { heading() } else Modifier)
                    .selectable(
                        selected = selected,
                        role = Role.Tab,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { if (selected) onReselect(destination) else onSelect(destination) },
                    ),
            )
        }
    }
}

/** The tab one place earlier in the row, which is what the row is scrolled to show first. */
private fun tabBefore(destination: AppDestination): AppDestination {
    val all = AppDestination.entries
    return all[(all.indexOf(destination) - 1).coerceAtLeast(0)]
}

/**
 * One tab's name: laid out once, small and large, and moved between the two by a scale and a
 * cross-fade rather than by laying the text out again.
 *
 * **Why it is drawn twice** (2026-11-11). It used to be one `Text` whose style was interpolated
 * from the small to the large on every frame of a tab change and of a swipe, so every frame
 * re-measured and re-shaped five names at a new font size, and recomposed the row to do it - the
 * change of tab read as laggy on the Fold. Here both copies keep one style for good: [emphasis] is
 * read in the measure block and the layer, never in composition, so a travel is a relayout of
 * already-measured text and a change of scale. The small copy is faint and the large one ink, so
 * the cross-fade is also the change of colour. The collapse is the same scale, applied to both.
 *
 * [onSmallWidth] reports the width this name takes at rest and small, which is what the row's
 * scroll is placed by. See [HeaderTabs].
 */
@Composable
private fun TabName(
    label: String,
    small: TextStyle,
    large: TextStyle,
    collapse: Float,
    emphasis: () -> Float,
    onSmallWidth: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = palette
    Layout(
        content = {
            // The words are said once, by the large copy; the small one is drawing only.
            Text(label, style = small, color = p.faint, maxLines = 1, softWrap = false, modifier = Modifier.clearAndSetSemantics { })
            Text(label, style = large, color = p.ink, maxLines = 1, softWrap = false)
        },
        modifier = modifier,
    ) { measurables, _ ->
        val loose = Constraints()
        val smallText = measurables[0].measure(loose)
        val largeText = measurables[1].measure(loose)
        val e = emphasis()
        val smallSize = mix(OtherTab.value, OtherTabCollapsed.value, collapse)
        val largeSize = mix(ExpandedTab.value, CollapsedTab.value, collapse)
        val size = mix(smallSize, largeSize, e)
        val smallScale = size / OtherTab.value
        val largeScale = size / ExpandedTab.value
        onSmallWidth((smallText.width * smallSize / OtherTab.value).roundToInt())
        val width = mix(smallText.width * smallScale, largeText.width * largeScale, e).roundToInt().coerceAtLeast(0)
        val height = mix(smallText.height * smallScale, largeText.height * largeScale, e).roundToInt().coerceAtLeast(0)
        // Mostly one or the other: the two copies overlap only through the middle of the travel.
        val shown = ((e - 0.2f) / 0.6f).coerceIn(0f, 1f)
        layout(width, height) {
            // Each scaled from its bottom-left corner, so both stand on the row's line.
            smallText.placeWithLayer(0, height - smallText.height) {
                scaleX = smallScale
                scaleY = smallScale
                transformOrigin = BottomStart
                alpha = 1f - shown
            }
            largeText.placeWithLayer(0, height - largeText.height) {
                scaleX = largeScale
                scaleY = largeScale
                transformOrigin = BottomStart
                alpha = shown
            }
        }
    }
}

private val BottomStart = TransformOrigin(0f, 1f)

private fun mix(start: Float, stop: Float, fraction: Float): Float = start + (stop - start) * fraction

private val TabGap = 18.dp
private val ExpandedTab = 42.sp
private val CollapsedTab = 28.sp
private val ExpandedTracking = (-1.7).sp
private val OtherTab = 20.sp
private val OtherTabCollapsed = 17.sp

/**
 * A run's progress, on every page while an analysis runs: a cobalt bar under the tabs and one line
 * saying what is happening and for how long.
 *
 * **Determinate while the model reads and sweeping while it writes**, as [AnalysisProgress]
 * explains: the batches can be counted, the report cannot.
 */
@Composable
internal fun RunProgress(
    progress: AnalysisProgress?,
    startedAt: Instant?,
    modifier: Modifier = Modifier,
) {
    val p = palette
    val elapsed by produceState(elapsedSince(startedAt), startedAt) {
        while (true) {
            value = elapsedSince(startedAt)
            delay(1_000)
        }
    }
    val fraction = progress?.fraction
    val line = listOfNotNull(progress?.badge() ?: "Starting", elapsed).joinToString(" · ")
    val still = LocalReduceMotion.current
    val sweep = if (fraction != null || still) 0f else {
        val t = rememberInfiniteTransition(label = "runSweep")
        val v by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1_400, easing = LinearEasing)), label = "sweep")
        v
    }
    androidx.compose.foundation.layout.Column(modifier.semantics { contentDescription = "Analysis running: $line" }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LiveDot(live = true, color = p.hero)
            Text(line, style = MaterialTheme.typography.bodySmall, color = p.ink, maxLines = 1)
        }
        Canvas(
            Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .height(4.dp),
        ) {
            val radius = CornerRadius(size.height / 2)
            drawRoundRect(p.line, size = size, cornerRadius = radius)
            if (fraction != null) {
                drawRoundRect(p.hero, size = Size(size.width * fraction, size.height), cornerRadius = radius)
            } else {
                val band = size.width * 0.3f
                val x = -band + (size.width + band) * sweep
                drawRoundRect(p.hero, topLeft = Offset(x.coerceAtLeast(0f), 0f), size = Size((band + x.coerceAtMost(0f)).coerceAtMost(size.width - x.coerceAtLeast(0f)), size.height), cornerRadius = radius)
            }
        }
    }
}

/** "1:42", or null before a run has a start time. */
internal fun elapsedSince(startedAt: Instant?, now: Instant = Instant.now()): String? {
    if (startedAt == null) return null
    val seconds = Duration.between(startedAt, now).seconds.coerceAtLeast(0)
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
