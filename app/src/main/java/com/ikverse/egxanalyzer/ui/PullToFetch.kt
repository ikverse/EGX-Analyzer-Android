package com.ikverse.egxanalyzer.ui

import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.ikverse.egxanalyzer.ui.kit.KitPullBand
import com.ikverse.egxanalyzer.ui.kit.popSpring
import com.ikverse.egxanalyzer.ui.theme.palette
import java.time.Instant
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * What the band a pull opens says, at each of the three points of the gesture.
 *
 * Two sets, because two things are pulled for: prices on Portfolio, Results and Insights, and the
 * chat list on Analyze. [showsPriceAge] puts how old the prices are beside the words, which is the
 * one fact that tells a reader whether the pull is worth finishing.
 */
internal data class PullWords(
    val pull: String,
    val release: String,
    val busy: String,
    val showsPriceAge: Boolean = false,
) {
    companion object {
        val Prices = PullWords(
            pull = "Pull to fetch prices",
            release = "Release to fetch prices",
            busy = "Already fetching prices",
            showsPriceAge = true,
        )
        val Chats = PullWords(
            pull = "Pull to refresh chats",
            release = "Release to refresh chats",
            busy = "Already refreshing chats",
        )
    }
}

/** The band's words: work already running outranks the gesture, since a release then does nothing. */
internal fun pullBandLabel(words: PullWords, armed: Boolean, busy: Boolean): String = when {
    busy -> words.busy
    armed -> words.release
    else -> words.pull
}

/** How old the prices are, for the band: "Prices from today 14:32", or that there are none yet. */
internal fun pricesAsOfLabel(lastPriceRefreshAt: Long, now: Instant = Instant.now()): String =
    if (lastPriceRefreshAt <= 0L) "Prices not fetched yet"
    else "Prices from ${whenLabel(Instant.ofEpochMilli(lastPriceRefreshAt), now)}"

/** How tall the band is, which is also how far the page comes down before a release fetches. */
internal val PullBandHeight = 64.dp

/** The page moves at half the finger's speed, so the pull reads as stretching something. */
private const val PullResistance = 0.5f

/** How far past the band's own height the page may be dragged before it stops. */
private const val PullOvershoot = 1.4f

/**
 * A pull on a page that is already at its top, as a distance the page has come down.
 *
 * Its own connection rather than Material's pull-to-refresh, because that one holds its indicator
 * out for as long as the work runs, and here the page goes back the moment the finger lifts: the
 * status line under the header reports the work, so nothing needs to stay open over the tiles.
 *
 * It only ever takes what the page left over. A downward drag reaches it after the page's own
 * scroll has refused it (the page is at its top), and the header's connection, which is outside
 * this one, has already grown the title back by then.
 */
internal class PullState(
    private val threshold: Float,
    private val scope: CoroutineScope,
    private val onArmed: () -> Unit,
    private val onRelease: () -> Unit,
) : NestedScrollConnection {
    /** The finger's own travel. [offset] is what the page does with it. */
    private var raw by mutableFloatStateOf(0f)
    private var settle: Job? = null

    /** How far down the page is, in pixels. Briefly negative while it springs back. */
    val offset: Float get() = min(raw * PullResistance, threshold * PullOvershoot)

    /** 0 at rest, 1 once a release would fetch. */
    val fraction: Float get() = (offset / threshold).coerceIn(0f, 1f)

    val armed: Boolean get() = offset >= threshold

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        // Pushing back up closes the band before the page underneath is asked to move.
        if (source != NestedScrollSource.UserInput || available.y >= 0f || raw <= 0f) return Offset.Zero
        if (settle?.isActive == true) return Offset.Zero
        val used = max(available.y, -raw)
        move(raw + used)
        return Offset(0f, used)
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
        settle?.cancel()
        move(raw + available.y)
        return Offset(0f, available.y)
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (raw <= 0f || settle?.isActive == true) return Velocity.Zero
        if (armed) onRelease()
        settle = scope.launch {
            animate(raw, 0f, animationSpec = popSpring()) { value, _ -> raw = value }
        }
        // The lift ends the pull; none of its speed is passed on to throw the page.
        return available
    }

    private fun move(to: Float) {
        val was = armed
        raw = to.coerceIn(0f, threshold * PullOvershoot / PullResistance)
        if (!was && armed) onArmed()
    }
}

/**
 * A page that can be pulled down to fetch: the page follows the finger and the gap it opens says
 * what the pull will do, how old the prices are, and when it has gone far enough.
 *
 * On release the page springs back at once and [onRefresh] runs; while [refreshing] is true the
 * band says so and a release does nothing. With motion reduced the page stays where it is and the
 * band is drawn over its top instead.
 */
@Composable
internal fun PullToFetch(
    words: PullWords,
    /** A lambda, so the page is not subscribed to a figure only the open band shows. */
    detail: () -> String?,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    content: @Composable () -> Unit,
) {
    val threshold = with(LocalDensity.current) { PullBandHeight.toPx() }
    val still = LocalReduceMotion.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val busy = rememberUpdatedState(refreshing)
    val refresh = rememberUpdatedState(onRefresh)
    val state = remember(threshold, scope) {
        PullState(
            threshold = threshold,
            scope = scope,
            // One tick at the point of no return, and none when a release would do nothing.
            onArmed = { if (!busy.value) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
            onRelease = { if (!busy.value) refresh.value() },
        )
    }
    val showing by remember(state) { derivedStateOf { state.offset > 0f } }
    val armed by remember(state) { derivedStateOf { state.armed } }
    Box(Modifier.fillMaxSize().clipToBounds().nestedScroll(state)) {
        // Composed only while pulled, so a page at rest has no hidden words for a screen reader.
        if (showing) {
            KitPullBand(
                label = pullBandLabel(words, armed, refreshing),
                detail = detail(),
                fraction = { if (busy.value) 0f else state.fraction },
                lit = armed && !refreshing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PullBandHeight)
                    .then(if (still) Modifier.zIndex(1f).background(palette.ground) else Modifier)
                    .graphicsLayer { alpha = (state.offset / (threshold * 0.4f)).coerceIn(0f, 1f) }
                    .padding(horizontal = Space.l),
            )
        }
        Box(Modifier.fillMaxSize().graphicsLayer { translationY = if (still) 0f else state.offset }) {
            content()
        }
    }
}
