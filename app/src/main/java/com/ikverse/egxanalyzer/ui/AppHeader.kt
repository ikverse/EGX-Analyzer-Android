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
 * The tab row: the page's own name large, the other four small beside it, each pressable.
 *
 * Every page draws its own header, so this row slides with the page on a swipe and its current
 * tab is always the page it sits on. On the cover screen the row is wider than the window and
 * scrolls sideways; it is scrolled so the current name starts at the row's edge, **by its own
 * scroll state and never by `bringIntoView`** - a bring-into-view request would climb past this
 * row to the pager and turn the page (see `revealIfOnScreen` for that trap).
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
    val scroll = rememberScrollState()
    val starts = remember { mutableStateMapOf<AppDestination, Int>() }
    LaunchedEffect(current, starts[current]) {
        starts[current]?.let { scroll.scrollTo(it) }
    }
    Row(
        modifier
            .horizontalScroll(scroll)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        AppDestination.entries.forEach { destination ->
            val selected = destination == current
            Text(
                destination.label,
                style = if (selected) {
                    MaterialTheme.typography.headlineLarge.copy(
                        fontSize = lerp(ExpandedTab, CollapsedTab, collapse),
                        letterSpacing = lerp(ExpandedTracking, CollapsedTracking, collapse),
                    )
                } else {
                    MaterialTheme.typography.titleLarge.copy(fontSize = lerp(OtherTab, OtherTabCollapsed, collapse), fontWeight = FontWeight.SemiBold)
                },
                color = if (selected) p.ink else p.faint,
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
