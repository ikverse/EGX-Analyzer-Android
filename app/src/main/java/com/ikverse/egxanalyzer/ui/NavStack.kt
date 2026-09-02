package com.ikverse.egxanalyzer.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Where a press came from, so back can undo it.
 *
 * A press on a call card in Insights lands on the Portfolio; a digest tile lands on either; a
 * notification lands wherever it names. Every one of those changes tab **on the reader's behalf**,
 * and until this existed the system's back button answered all of them the same way - by closing
 * the app. The reader who followed a trade through to the call it came from had no way back to the
 * card they had been reading but to find it again by hand.
 *
 * [destination] is the tab being left. [positionId] and [callId] are what to reveal on arriving
 * back, where the press knew - a card that carried the reader somewhere should be the card they
 * come back to, unfolded and flashing, the same way it would have been had they got there by
 * pressing. Both are optional and usually one is null: a tile on the digest card knows the tab it
 * is drawn on and nothing about which card the reader had been looking at, and a return that
 * revealed the wrong one is worse than a return that reveals nothing.
 */
data class NavStop(
    val destination: AppDestination,
    val positionId: String? = null,
    val callId: String? = null,
)

/**
 * One step of history, and deliberately only one.
 *
 * **Not a stack of every tab ever visited.** The five destinations are peers - a bar and a rail
 * both say so - and a back button that walked back through a morning's tab presses one at a time
 * would take a dozen presses to leave an app whose reader had merely been browsing. What this
 * remembers is narrower and is the thing that was actually missing: the *last* jump the app made on
 * the reader's behalf. A tab the reader chose themselves is not a jump and is never recorded, which
 * is why [AppState.navigate] clears this rather than adding to it.
 *
 * One deep also settles the loop that a deeper one opens. Portfolio to Insights to Portfolio is two
 * presses through a pair of cards that point at each other, and every extra level of history is one
 * more press of back spent bouncing between the same two tabs before the app will close.
 *
 * No Android in here, so [NavStackTest] can drive the whole rule without a device.
 */
internal class NavStack {

    /**
     * Compose state, not a plain field, and that is load-bearing.
     *
     * The shell enables its `BackHandler` on [canReturn]. Held in a plain field, pushing a jump
     * would change what back should do without telling the composition, and the handler would stay
     * disabled until something else happened to recompose the shell. Today every push is
     * accompanied by a change of destination, which does recompose it - so it would work, by luck,
     * until the first entrance that records a return without moving the reader.
     */
    private var stop: NavStop? by mutableStateOf(null)

    /** Whether back has anywhere to go. */
    val canReturn: Boolean get() = stop != null

    /**
     * Records the tab a jump is leaving.
     *
     * Replaces rather than accumulating, which is what "one deep" means: the newest jump is the one
     * back is about.
     */
    fun push(stop: NavStop) {
        this.stop = stop
    }

    /** Takes the recorded step and forgets it, or answers null where there is none. */
    fun pop(): NavStop? = stop.also { stop = null }

    /**
     * Forgets the recorded step.
     *
     * Called when the reader navigates by hand. A tab they chose is where they are now by their own
     * decision, and a back press after it means "leave", not "undo the jump I made before that".
     */
    fun clear() {
        stop = null
    }
}
