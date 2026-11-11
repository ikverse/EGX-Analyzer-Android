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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.text.lerp as lerpStyle
import com.ikverse.egxanalyzer.ui.kit.popSpring
import kotlin.math.roundToInt
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
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
     * How large [destination]'s name is drawn on the page showing [current]: 0 small, 1 large.
     *
     * A page is composed a frame before [goTo] is told about it. For that frame the row is drawn
     * exactly as the page being left drew it, so the travel starts from what was on screen.
     */
    fun emphasis(destination: AppDestination, current: AppDestination): Float = when {
        to != current -> if (destination == to) 1f else 0f
        destination == to -> progress.value
        destination == from -> 1f - progress.value
        else -> 0f
    }

    /** Whether [current]'s page is the one this travel has been told about. */
    fun knows(current: AppDestination): Boolean = to == current

    suspend fun goTo(destination: AppDestination, still: Boolean) {
        if (destination == to) return
        from = to
        to = destination
        if (still) {
            progress.snapTo(1f)
        } else {
            progress.snapTo(0f)
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
    val p = palette
    val travel = LocalTabTravel.current
    val scroll = travel?.scroll ?: rememberScrollState()
    val starts = remember { mutableStateMapOf<AppDestination, Int>() }
    LaunchedEffect(current, travel) {
        snapshotFlow {
            // Not told about this page yet: hold the row where the last page left it.
            if (travel != null && !travel.knows(current)) return@snapshotFlow null
            val landing = starts[tabBefore(current)] ?: return@snapshotFlow null
            val leaving = travel?.from?.let { starts[tabBefore(it)] } ?: landing
            val along = travel?.fraction?.coerceIn(0f, 1f) ?: 1f
            (leaving + (landing - leaving) * along).roundToInt()
        }.collect { target -> if (target != null) scroll.scrollTo(target) }
    }
    val large = MaterialTheme.typography.headlineLarge.copy(
        fontSize = lerp(ExpandedTab, CollapsedTab, collapse),
        letterSpacing = lerp(ExpandedTracking, CollapsedTracking, collapse),
    )
    val small = MaterialTheme.typography.titleLarge.copy(
        fontSize = lerp(OtherTab, OtherTabCollapsed, collapse),
        fontWeight = FontWeight.SemiBold,
    )
    Row(
        modifier
            .horizontalScroll(scroll)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        AppDestination.entries.forEach { destination ->
            val selected = destination == current
            val emphasis = travel?.emphasis(destination, current) ?: if (selected) 1f else 0f
            Text(
                destination.label,
                style = when (emphasis) {
                    0f -> small
                    1f -> large
                    else -> lerpStyle(small, large, emphasis)
                },
                color = lerpColor(p.faint, p.ink, emphasis.coerceIn(0f, 1f)),
                maxLines = 1,
                modifier = Modifier
                    .onGloballyPositioned { starts[destination] = it.positionInParent().x.toInt() }
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

private val ExpandedTab = 42.sp
private val CollapsedTab = 28.sp
private val ExpandedTracking = (-1.7).sp
private val CollapsedTracking = (-0.9).sp
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
