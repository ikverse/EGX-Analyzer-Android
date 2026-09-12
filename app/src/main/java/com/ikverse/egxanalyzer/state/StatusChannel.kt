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
 * through to here, so nothing about how the message is drawn, announced or consumed has changed.
 */
class StatusChannel {
    var message: StatusMessage? by mutableStateOf(null)
}
