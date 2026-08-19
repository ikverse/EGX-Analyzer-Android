package com.ikverse.egxanalyzer.next

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing

/**
 * Three curves, seven durations, no exceptions.
 *
 * Motion here is the instrument's mechanism, not its mood: things slide, cut and hinge because a
 * record is being re-ordered in front of the reader. Nothing eases in to be charming, and no figure
 * is ever withheld to be revealed.
 *
 * One rule runs through all of it - **the app animates containers, positions and edges, never
 * values and never the reader's route.** A figure is at full opacity within 90ms of existing, a
 * number never counts up, and if a transition is still running when the next thing is pressed, the
 * press wins.
 *
 * The last redesign shipped with nothing moving at all, which is half of why it read as unfinished.
 * These are requirements, not a finish.
 */
internal object NextMotion {

    /**
     * Everything that moves or resizes.
     *
     * Leaves fast, lands dead. A hinge does not overshoot, and a row that springs is a row you have
     * to wait for.
     */
    val hinge: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /**
     * Anything leaving: a filtered-out row, a closing pane.
     *
     * Slow start, hard finish, so a departure never competes for attention with what remains.
     */
    val exit: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /**
     * Opacity, and the arrival flash. Nothing else.
     *
     * Eased opacity reads as a fade-up; linear opacity reads as a light going on.
     */
    val linear: Easing = LinearEasing

    /** A press acknowledging itself: the fill drops to the well and a spine appears. */
    const val PRESS_MILLIS = 90

    /** The same press letting go. Longer, because releasing is not news. */
    const val RELEASE_MILLIS = 120

    /** Rows moving: a filter narrowing a list, a re-sort, a collapse. */
    const val LIST_MILLIS = 160

    /** Anything leaving. */
    const val EXIT_MILLIS = 120

    /** One destination giving way to another. */
    const val DESTINATION_MILLIS = 200

    /** A card folding open, or shut. */
    const val OPEN_MILLIS = 220

    /** One stagger step. */
    const val STAGGER_MILLIS = 12

    /** How many of them a sequence may take before every later element moves with the sixth. */
    const val STAGGER_CAP = 6

    /**
     * The fold: the phone opening, and the layout genuinely reorganising.
     *
     * Each element moves for [OPEN_MILLIS] and the last of the four beats lands
     * [STAGGER_MILLIS] * 4 after the first, which makes this the longest movement in the app and
     * still under a third of a second.
     */
    const val FOLD_MILLIS = OPEN_MILLIS + STAGGER_MILLIS * 4

    /** A figure's container arriving after a run. The figure itself does not animate. */
    const val FIGURE_MILLIS = 140

    /** The spine flash that marks a row which changed under the reader. */
    const val ARRIVE_MILLIS = 900

    /**
     * How long the one irreversible control has to be held before it fires.
     *
     * Long enough that it cannot happen by accident, short enough that it does not feel like the
     * app is arguing. It is the only duration here measured in intent rather than in perception.
     */
    const val HOLD_MILLIS = 1_400
}
