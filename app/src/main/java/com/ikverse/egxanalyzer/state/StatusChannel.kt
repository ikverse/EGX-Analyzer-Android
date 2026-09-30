package com.ikverse.egxanalyzer.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ikverse.egxanalyzer.ui.StatusMessage

/**
 * The one line at the top of the screen, held apart from whoever is writing to it.
 *
 * It exists so a piece of the app can be lifted out of [LiveAppState] and still be able to speak.
 * `statusMessage` was a property of that class, so anything that needed to say something had to
 * *be* that class - which is a good part of why it grew to four and a half thousand lines. A
 * collaborator takes one of these instead of taking the whole state object, which is a dependency
 * on the single thing it actually uses rather than on everything.
 *
 * Still one instance and still one line: [LiveAppState.statusMessage] reads and writes straight
 * through to here, so nothing about how the line is drawn or announced has changed.
 *
 * **An undo is no longer written over.** Every other message here is a passing confirmation - gone
 * in four seconds, and losing it to whatever happens next is nothing lost. An undo is the only way
 * back from something the reader cannot simply redo, and it used to clear on the very next status
 * update at all - a sync tick, a price refresh, anything - which could take it off the line before
 * the reader had turned back to it. One message may now wait behind it: [message]'s setter holds a
 * message that would overwrite a live undo in [pending] instead, and [advance] - called once that
 * undo has had its full ten seconds, or the reader has pressed it - shows whatever was waiting.
 * Silently dropped only by a third message arriving before the second was ever shown, which is the
 * one case a single slot cannot help and no reader is likely to produce by hand.
 */
class StatusChannel {
    private var shown by mutableStateOf<StatusMessage?>(null)
    private var pending: StatusMessage? = null
    private var shownAt: Long = 0L

    var message: StatusMessage?
        get() = shown
        set(value) {
            // Null is a hard clear - something starting fresh (a run about to speak for itself
            // through the busy line) that wants the slate wiped, queue and all, not one more
            // message waiting behind whatever it is about to say.
            if (value == null) {
                pending = null
                shown = null
                return
            }
            if (shown?.undo != null && System.currentTimeMillis() - shownAt < UndoHoldMillis) {
                pending = value
                return
            }
            shown = value
            shownAt = System.currentTimeMillis()
        }

    /** Called once [message] has stood for its full hold, or the reader dismissed it early. */
    fun advance() {
        val next = pending
        pending = null
        message = next
    }

    private companion object {
        const val UndoHoldMillis = 10_000L
    }
}
