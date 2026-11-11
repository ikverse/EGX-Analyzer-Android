package com.ikverse.egxanalyzer.ui

import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.dp
import com.ikverse.egxanalyzer.ui.kit.popSpring
import kotlin.math.abs
import kotlin.math.sign
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Swiping between tabs, designed for the 5.0 shell rather than carried over from the pager it
 * replaced (see `DestinationSwitch`).
 *
 * **Nothing slides.** Only one page is ever composed, and no neighbour is drawn beside it. While
 * the finger is down two things follow it: the header's emphasis moves toward the neighbouring
 * tab's name ([TabTravel.peek]), and the page leans a little the way it is being pushed
 * ([lean], at most [LeanMax], fading slightly). Let go past [CommitFraction] of the width, or
 * with a flick, and the tab changes exactly as a press changes it - the emphasis finishes its
 * move on the same spring and the new page's tiles pop in. Let go short and both settle back.
 *
 * **A sideways scroller under the finger keeps the gesture.** The chip rows, the wide table, the
 * charts, the ticker tape and the tab row all consume a horizontal drag in the main pass; this
 * reads the final pass and gives up on any gesture something below has already taken. It also
 * gives up on a gesture that starts mostly vertical, so reading down a page never turns it.
 */
@Stable
internal class TabSwipe {
    /**
     * How far the finger has pushed, as a fraction of the page width: negative toward the next
     * tab, positive toward the previous one. At the first or last tab it is resisted and capped,
     * so the page stretches a little and springs back.
     */
    var pull by mutableFloatStateOf(0f)
        private set

    /** How far the finger has actually gone, whatever is drawn: what decides a commit. */
    private var reach = 0f

    /** Where the page is drawn, as a signed fraction of [LeanMax]: its tiles drift with the finger. */
    fun lean(): Float = (pull / CommitFraction).coerceIn(-1f, 1f)

    /** The neighbour the pull is heading for, or null at either end of the row. */
    var toward by mutableStateOf<AppDestination?>(null)
        private set

    internal fun drag(pullNow: Float, current: AppDestination, travel: TabTravel?, still: Boolean) {
        val target = neighbour(current, if (pullNow < 0f) 1 else -1)
        toward = target
        reach = pullNow.coerceIn(-1f, 1f)
        pull = when {
            still -> 0f
            target == null -> (pullNow * EdgeResistance).coerceIn(-EdgeCap, EdgeCap)
            else -> pullNow.coerceIn(-1f, 1f)
        }
        travel?.preview(
            target?.takeUnless { still },
            (abs(pullNow) / HeaderFullAt).coerceIn(0f, 1f),
        )
    }

    /**
     * The finger is up: change tab through [navigate] when the pull or the fling says so, and
     * otherwise settle the page and the header back where they were.
     */
    internal fun release(
        velocity: Float,
        travel: TabTravel?,
        scope: CoroutineScope,
        navigate: (AppDestination) -> Unit,
    ) {
        val target = toward
        val flung = abs(velocity) > FlingVelocity && sign(velocity) == sign(reach)
        val committed = target != null && (abs(reach) >= CommitFraction || flung)
        reach = 0f
        if (committed && target != null) {
            pull = 0f
            toward = null
            // The header's peek is left in place: `TabTravel.goTo` starts the rest of the move
            // from it, so the emphasis finishes rather than jumping back to begin again.
            navigate(target)
            return
        }
        val from = pull
        val headerFrom = travel?.peekFraction ?: 0f
        scope.launch {
            animate(from, 0f, animationSpec = popSpring()) { value, _ ->
                pull = value
                if (from != 0f) travel?.preview(target, headerFrom * (value / from))
            }
            toward = null
            travel?.preview(null, 0f)
        }
    }

    /** A gesture that ended without being a swipe: put everything back at once. */
    internal fun abandon(travel: TabTravel?) {
        reach = 0f
        if (pull == 0f && toward == null) return
        pull = 0f
        toward = null
        travel?.preview(null, 0f)
    }

    companion object {
        /** How far the page leans at most, before the swipe counts. */
        val LeanMax = 24.dp

        /** How much of the page lightens at full lean. */
        const val LeanFade = 0.25f

        /** A quarter of the width is a deliberate swipe. */
        const val CommitFraction = 0.25f

        /** The header reaches the next name by half the width; commit finishes it on a spring. */
        const val HeaderFullAt = 0.5f

        /** Pixels per second; a flick this fast counts however short it was. */
        const val FlingVelocity = 1_400f

        /** Past either end the finger moves the page a third as far, and no further than this. */
        const val EdgeResistance = 0.33f
        const val EdgeCap = 0.08f

        /** The tab [step] places along the header's row, or null past either end. */
        fun neighbour(current: AppDestination, step: Int): AppDestination? =
            AppDestination.entries.getOrNull(current.ordinal + step)
    }
}

/** The shell's [TabSwipe], read by `Screen` to lean the page; null in a test or preview. */
internal val LocalTabSwipe = staticCompositionLocalOf<TabSwipe?> { null }

/**
 * Turns a sideways swipe on the page into a change of tab. Reads the final pass, so anything below
 * that scrolls sideways has had the gesture first.
 */
internal fun Modifier.tabSwipe(
    swipe: TabSwipe,
    current: () -> AppDestination,
    travel: TabTravel?,
    still: () -> Boolean,
    scope: CoroutineScope,
    navigate: (AppDestination) -> Unit,
): Modifier = pointerInput(swipe, travel) {
    val slop = viewConfiguration.touchSlop
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
        val tracker = VelocityTracker()
        tracker.addPosition(down.uptimeMillis, down.position)
        var dx = 0f
        var dy = 0f
        var claimed = false
        val width = size.width.toFloat().coerceAtLeast(1f)
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Final)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) {
                if (claimed) {
                    tracker.addPosition(change.uptimeMillis, change.position)
                    swipe.release(tracker.calculateVelocity().x, travel, scope, navigate)
                    change.consume()
                }
                return@awaitEachGesture
            }
            // Something under the finger - a chip row, the table, a chart - took it first.
            if (!claimed && change.isConsumed) return@awaitEachGesture
            // A second finger is a pinch or a scroll, never a swipe.
            if (event.changes.size > 1) {
                if (claimed) swipe.abandon(travel)
                return@awaitEachGesture
            }
            val delta = change.positionChange()
            dx += delta.x
            dy += delta.y
            tracker.addPosition(change.uptimeMillis, change.position)
            if (!claimed) {
                if (abs(dy) > slop && abs(dy) >= abs(dx)) return@awaitEachGesture
                if (abs(dx) > slop && abs(dx) > abs(dy) * 1.5f) claimed = true else continue
            }
            change.consume()
            swipe.drag(dx / width, current(), travel, still())
        }
        if (claimed) swipe.abandon(travel)
    }
}
