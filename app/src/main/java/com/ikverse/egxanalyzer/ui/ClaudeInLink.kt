package com.ikverse.egxanalyzer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ikverse.egxanalyzer.model.ClaudeInPlan

/** Where this phone stands with ClaudeIn, as the pairing card says it. */
enum class ClaudeInLinkState { NOT_PAIRED, CHECKING, PAIRED, NOT_AVAILABLE, WRONG_APP }

/** The paired PC as a screen may show it: never the code. */
data class ClaudeInPaired(val label: String, val address: String, val shortFingerprint: String)

/** A typed pairing waiting for the owner to compare the fingerprint with ClaudeIn's window. */
data class ClaudeInConfirm(val address: String, val shortFingerprint: String)

/**
 * The ClaudeIn part of [AppState], carved off it the way [AppUpdates] and [HomeFeed] are, and
 * implemented by `state/LiveClaudeIn.kt`.
 *
 * [ClaudeInScanner] is the one composable member: the camera and the code reader belong to the
 * platform, and `ui` imports nothing from `data`, so the scanner is drawn by whoever implements
 * this. The default draws nothing, which is what a preview and a Compose test get.
 */
interface ClaudeInLink {
    val claudeInState: ClaudeInLinkState
    val claudeInPaired: ClaudeInPaired?

    /** The last thing pairing or a check said, for the card. */
    val claudeInMessage: String?

    /** How full the Claude plan is, as the last run or check reported it. */
    val claudeInPlan: ClaudeInPlan?

    /** What ClaudeIn says while a run waits or searches, such as "Waiting for the PC (1 ahead)…". */
    val claudeInWorking: String?

    /** Set while a typed pairing waits for the owner to compare fingerprints. */
    val claudeInConfirm: ClaudeInConfirm?

    /** A QR code's text was read: check the PC and pair. */
    fun pairClaudeInFromQr(text: String)

    /** The typed route: reach the PC, read its certificate, and ask the owner to compare. */
    fun pairClaudeInTyped(address: String, code: String)

    fun confirmClaudeIn()

    fun cancelClaudeInConfirm()

    fun forgetClaudeIn()

    /** Asks the PC whether it is there, now. */
    fun checkClaudeIn()

    /**
     * The camera, reading QR codes. Asks for the camera permission the first time it is drawn,
     * which is only ever when the owner pressed Scan. [onCode] gets each code's text; [onDenied]
     * is called when the permission is refused, so the sheet can say so in its own words.
     */
    @Composable
    fun ClaudeInScanner(modifier: Modifier, onCode: (String) -> Unit, onDenied: () -> Unit) {
        Box(modifier)
    }
}
