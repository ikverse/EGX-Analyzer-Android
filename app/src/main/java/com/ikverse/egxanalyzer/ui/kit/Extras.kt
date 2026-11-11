package com.ikverse.egxanalyzer.ui.kit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.MutableState
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ikverse.egxanalyzer.model.FoldPosture
import com.ikverse.egxanalyzer.ui.LocalReduceMotion
import com.ikverse.egxanalyzer.ui.TabularFigures
import com.ikverse.egxanalyzer.ui.ExpandIn
import com.ikverse.egxanalyzer.ui.CollapseOut
import com.ikverse.egxanalyzer.ui.pressScale
import com.ikverse.egxanalyzer.ui.theme.palette
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * A word on a card - a status, an outcome, a timing, a fault, a channel - as a capsule with a light
 * wash of the colour that word already means and the word in [ink]. **The only pill in the app, and
 * always filled** since 5.1.4: the hairline version was the pre-5.0 look, and two styles side by
 * side read as two designs. A note with no meaning of its own takes the muted wash. Pressed, a tag
 * explains itself where [onClick] is given.
 */
@Composable
fun KitTag(
    text: String,
    tone: Color,
    modifier: Modifier = Modifier,
    ink: Color = palette.ink,
    onClick: (() -> Unit)? = null,
) {
    val press = remember { MutableInteractionSource() }
    Box(
        modifier
            .then(if (onClick != null) Modifier.pressScale(press) else Modifier)
            .clip(Kit.Capsule)
            .background(tone.copy(alpha = 0.16f))
            .then(
                if (onClick != null) {
                    Modifier.clickable(interactionSource = press, indication = null, role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * A tile that opens: a header that says what is inside and how it went, and the content under it.
 *
 * [leading] is what stands before the title - a dot in the colour of how a session went, or a
 * glyph. The `+`/`−` disc at the end is the one affordance; the whole header is the press. [fill]
 * is the tile's colour: the white card on the page, the page's ground when nested inside one.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KitExpandable(
    title: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    summary: (@Composable () -> Unit)? = null,
    headerExtra: (@Composable () -> Unit)? = null,
    onHeaderLongClick: (() -> Unit)? = null,
    fill: Color = palette.card,
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = palette
    val turn by animateFloatAsState(if (expanded) 1f else 0f, label = "expandable")
    Column(modifier.fillMaxWidth().clip(Kit.TileShape).background(fill)) {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClickLabel = if (expanded) "Close $title" else "Open $title",
                    onLongClick = onHeaderLongClick,
                    onClick = { onExpandedChange(!expanded) },
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.size(12.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = p.ink)
                summary?.invoke()
            }
            headerExtra?.invoke()
            Box(
                Modifier
                    .padding(start = 8.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(if (expanded) p.ink else p.raised)
                    .graphicsLayer { rotationZ = 90f * turn },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (expanded) "−" else "+",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (expanded) p.onInk else p.ink,
                )
            }
        }
        AnimatedVisibility(expanded, enter = ExpandIn, exit = CollapseOut) {
            Column(
                Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content,
            )
        }
    }
}

/** A dot in [color] before a tile's title: how a session or a group went, before a word is read. */
@Composable
fun StateDot(color: Color) {
    Box(Modifier.size(10.dp).clip(CircleShape).background(color))
}

/**
 * Which of several pages is in front: dots on a track, the front one a wider ink capsule that moves
 * with [position] as the pages do, read in the draw lambda so a drag recomposes nothing.
 */
@Composable
fun KitDots(total: Int, position: () -> Float, modifier: Modifier = Modifier) {
    val p = palette
    Canvas(modifier.size(width = (DotPitch * total), height = 8.dp)) {
        val pitch = DotPitch.toPx()
        val middle = size.height / 2f
        repeat(total) { index ->
            drawCircle(p.faint, 3.dp.toPx(), Offset(pitch * (index + 0.5f), middle))
        }
        val at = position().coerceIn(0f, (total - 1).toFloat())
        val centre = pitch * (at + 0.5f)
        val half = 7.dp.toPx()
        drawLine(p.ink, Offset(centre - half, middle), Offset(centre + half, middle), strokeWidth = size.height, cap = StrokeCap.Round)
    }
}

private val DotPitch = 18.dp

/** A turning arc, for a press that is waiting on an answer. Still when reduce motion is on. */
@Composable
fun KitSpinner(modifier: Modifier = Modifier, color: Color = LocalContentColor.current, size: androidx.compose.ui.unit.Dp = 16.dp) {
    val still = LocalReduceMotion.current
    val turn = if (still) {
        0f
    } else {
        val t = rememberInfiniteTransition(label = "spinner")
        t.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "turn").value
    }
    Canvas(modifier.size(size).semantics { contentDescription = "Working" }) {
        drawArc(
            color = color,
            startAngle = turn,
            sweepAngle = 270f,
            useCenter = false,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

/**
 * Pull-to-refresh, drawn in the kit: an ink disc that comes down with the pull, a cobalt arc that
 * fills with it and turns while the refresh runs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoxScope.KitRefreshIndicator(state: PullToRefreshState, refreshing: Boolean) {
    val p = palette
    val pulled = state.distanceFraction.coerceIn(0f, 1.4f)
    if (!refreshing && pulled <= 0f) return
    val shown = if (refreshing) 1f else pulled
    Box(
        Modifier
            .align(Alignment.TopCenter)
            .graphicsLayer {
                translationY = (56.dp.toPx() * shown) - 20.dp.toPx()
                alpha = shown.coerceIn(0f, 1f)
            }
            .size(40.dp)
            .clip(CircleShape)
            .background(p.ink),
        contentAlignment = Alignment.Center,
    ) {
        if (refreshing) {
            KitSpinner(color = p.hero, size = 20.dp)
        } else {
            Canvas(Modifier.size(20.dp)) {
                drawArc(
                    color = p.hero,
                    startAngle = -90f,
                    sweepAngle = 360f * pulled.coerceAtMost(1f),
                    useCenter = false,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }
    }
}

/**
 * Picks one day: a month grid in a kit sheet, with the month's arrows and the weekdays over it.
 *
 * [allowed] decides which days can be pressed - every other day is drawn faint and refuses a press,
 * so the calendar can never hand back a day the caller would refuse. [latest] caps the arrows so a
 * reader cannot page into months with nothing to pick. The week starts on Saturday, as the
 * Egyptian exchange's own week does.
 */
@Composable
fun KitCalendarSheet(
    title: String,
    selected: LocalDate?,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    posture: FoldPosture,
    allowed: (LocalDate) -> Boolean = { true },
    earliest: LocalDate? = null,
    latest: LocalDate? = null,
) {
    val p = palette
    val opening = selected ?: latest ?: LocalDate.now()
    var month by remember { mutableStateOf(YearMonth.from(opening)) }
    KitSheet(onDismiss = onDismiss, posture = posture, title = title) {
        Text(title, style = MaterialTheme.typography.headlineMedium, color = p.ink, modifier = Modifier.padding(horizontal = 4.dp))
        Tile(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val canBack = earliest == null || month.isAfter(YearMonth.from(earliest))
                val canForward = latest == null || month.isBefore(YearMonth.from(latest))
                KitIconButton(
                    Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                    label = "Previous month",
                    onClick = { if (canBack) month = month.minusMonths(1) },
                    size = 40.dp,
                )
                Text(
                    month.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + month.year,
                    style = MaterialTheme.typography.titleLarge,
                    color = p.ink,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                KitIconButton(
                    Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    label = "Next month",
                    onClick = { if (canForward) month = month.plusMonths(1) },
                    size = 40.dp,
                )
            }
            val week = listOf(
                DayOfWeek.SATURDAY, DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
            )
            Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                week.forEach {
                    Text(
                        it.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).take(2),
                        style = MaterialTheme.typography.labelSmall,
                        color = p.muted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            val lead = (month.atDay(1).dayOfWeek.value - DayOfWeek.SATURDAY.value + 7) % 7
            val days = month.lengthOfMonth()
            val cells = List(lead) { null } + (1..days).map { month.atDay(it) }
            cells.chunked(7).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    row.forEach { day -> CalendarDay(day, day == selected, day?.let(allowed) == true, onPick, Modifier.weight(1f)) }
                    repeat(7 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        KitButton("Cancel", onClick = onDismiss, kind = ButtonKind.SECONDARY, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun CalendarDay(day: LocalDate?, isSelected: Boolean, enabled: Boolean, onPick: (LocalDate) -> Unit, modifier: Modifier) {
    val p = palette
    Box(modifier.aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
        if (day == null) return@Box
        val today = day == LocalDate.now()
        Box(
            Modifier
                .widthIn(max = 44.dp)
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(CircleShape)
                .background(if (isSelected) p.ink else Color.Transparent)
                .then(if (today && !isSelected) Modifier.border(1.5.dp, p.hero, CircleShape) else Modifier)
                .then(
                    if (enabled) {
                        Modifier.clickable(role = Role.Button, onClickLabel = "Pick ${day.dayOfMonth}") { onPick(day) }
                    } else {
                        Modifier
                    },
                )
                .semantics { selected = isSelected },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                day.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = TabularFigures),
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = when {
                    isSelected -> p.onInk
                    enabled -> p.ink
                    else -> p.faint
                },
            )
        }
    }
}

/** A rule between rows: a hairline in [color], the kit's divider. */
@Composable
fun KitRule(modifier: Modifier = Modifier, color: Color = palette.line, thickness: androidx.compose.ui.unit.Dp = 1.dp) {
    Box(modifier.fillMaxWidth().height(thickness).background(color))
}

/**
 * A determinate fill - an update downloading, a share of a whole. [fraction] is clamped to 0..1;
 * the fill springs to a new value rather than jumping, and holds still under reduce motion.
 */
@Composable
fun KitProgress(
    fraction: Float,
    modifier: Modifier = Modifier,
    fill: Color = palette.highlight,
    track: Color = palette.line,
    height: androidx.compose.ui.unit.Dp = 10.dp,
) {
    val shown by animateFloatAsState(fraction.coerceIn(0f, 1f), label = "progress")
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(track)
            .semantics { contentDescription = "${(fraction.coerceIn(0f, 1f) * 100).toInt()}%" },
    ) {
        Box(Modifier.fillMaxWidth(shown).height(height).clip(CircleShape).background(fill))
    }
}

/** Two quantities as one bar - sent against returned - split by a 2dp gap. */
@Composable
fun KitSplitBar(first: Long, total: Long, modifier: Modifier = Modifier, firstColor: Color = palette.hero, secondColor: Color = palette.tile2, height: androidx.compose.ui.unit.Dp = 10.dp) {
    val share = if (total <= 0) 0f else (first.toFloat() / total).coerceIn(0f, 1f)
    Row(modifier.fillMaxWidth().height(height).clip(CircleShape), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        if (share > 0f) Box(Modifier.weight(share).height(height).background(firstColor))
        if (share < 1f) Box(Modifier.weight(1f - share).height(height).background(secondColor))
    }
}

/** How a [TimelineStep] went: done, the one under way, still to come, or how it ended. */
enum class StepTone { DONE, ACTIVE, WAITING, GOOD, BAD }

/** One step of a [Timeline]: what happened, a line under it, and when, if anything kept the date. */
data class TimelineStep(
    val text: String,
    val detail: String? = null,
    val date: String? = null,
    val tone: StepTone = StepTone.DONE,
)

/**
 * What happened to a call or a trade, in order, as dots on a line.
 *
 * A step still to come is a hollow dot on a faint line, so how far along it is reads before a word
 * does. The date is optional because some steps have none the app keeps - see the 5.1 plan.
 */
@Composable
fun Timeline(steps: List<TimelineStep>, modifier: Modifier = Modifier) {
    val p = palette
    Column(modifier.fillMaxWidth()) {
        steps.forEachIndexed { index, step ->
            val colour = when (step.tone) {
                StepTone.DONE -> p.ink
                StepTone.ACTIVE -> p.heroInk
                StepTone.WAITING -> p.faint
                StepTone.GOOD -> p.gain
                StepTone.BAD -> p.loss
            }
            val next = steps.getOrNull(index + 1)
            val lineColour = if (next == null || next.tone == StepTone.WAITING) p.line else p.muted
            Row(
                Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        // The connector runs from this dot to the next one, through the gap.
                        if (next != null) {
                            val x = TimelineDot.toPx() / 2f
                            drawLine(
                                lineColour,
                                Offset(x, TimelineDotTop.toPx() + TimelineDot.toPx()),
                                Offset(x, size.height + TimelineDotTop.toPx()),
                                strokeWidth = 2.dp.toPx(),
                            )
                        }
                    }
                    .padding(bottom = if (next != null) 14.dp else 0.dp),
            ) {
                Box(
                    Modifier
                        .padding(top = TimelineDotTop)
                        .size(TimelineDot)
                        .clip(CircleShape)
                        .then(
                            if (step.tone == StepTone.WAITING) {
                                Modifier.border(2.dp, colour, CircleShape)
                            } else {
                                Modifier.background(colour)
                            },
                        ),
                )
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        step.text,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (step.tone == StepTone.WAITING) p.muted else p.ink,
                    )
                    step.detail?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = p.muted)
                    }
                }
                step.date?.let {
                    Spacer(Modifier.size(8.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = TabularFigures),
                        color = p.muted,
                    )
                }
            }
        }
    }
}

private val TimelineDot = 12.dp
private val TimelineDotTop = 4.dp

/**
 * The closed row the technical detail sits behind: a title, a line saying what is inside, and the
 * detail once opened. [expanded] is handed in because every caller keeps it in `PageState`, so a
 * fold does not shut it.
 */
@Composable
fun MoreRow(
    title: String,
    expanded: MutableState<Boolean>,
    modifier: Modifier = Modifier,
    summary: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = palette
    KitExpandable(
        title = title,
        expanded = expanded.value,
        onExpandedChange = { expanded.value = it },
        modifier = modifier,
        summary = summary?.let { text ->
            { Text(text, style = MaterialTheme.typography.bodySmall, color = p.muted) }
        },
        content = content,
    )
}
