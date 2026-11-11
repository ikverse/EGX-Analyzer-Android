package com.ikverse.egxanalyzer.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.ikverse.egxanalyzer.model.Outcome
import com.ikverse.egxanalyzer.model.PositionStatus
import com.ikverse.egxanalyzer.ui.theme.palette

/**
 * The one colour each status is drawn in, on every pill, dot and ring that says it.
 *
 * Until 5.1.5 there were five of these - one per screen - and they disagreed: an open trade was the
 * market blue on Portfolio and cobalt on Insights, and a trade that ran out of time was amber on its
 * card and grey everywhere else. Running is the app's blue, a target reached is gain, the stop is
 * loss, and everything that is neither a win nor a loss - out of time, closed by hand, not counted,
 * still watching - is muted. [CallGroup.tone] says the same for a report's calls.
 */
@Composable
internal fun Outcome.tone(): Color = when (this) {
    Outcome.FULL_HIT, Outcome.PARTIAL_HIT -> palette.gain
    Outcome.STOPPED -> palette.loss
    Outcome.OPEN -> palette.heroInk
    else -> palette.muted
}

/** A trade's status in the same colours as [Outcome.tone]. */
@Composable
internal fun PositionStatus.tone(): Color = when (this) {
    PositionStatus.OPEN -> palette.heroInk
    PositionStatus.PARTIAL_TARGET_HIT, PositionStatus.FULL_TARGET_HIT -> palette.gain
    PositionStatus.STOPPED_OUT -> palette.loss
    PositionStatus.EXPIRED, PositionStatus.CLOSED_MANUALLY -> palette.muted
}
