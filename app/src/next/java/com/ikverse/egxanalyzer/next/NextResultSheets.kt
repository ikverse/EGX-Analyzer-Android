package com.ikverse.egxanalyzer.next

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import com.ikverse.egxanalyzer.ui.AppState

/**
 * Deleting a saved run, with the blast radius stated before anything is pressed.
 *
 * One honest difference from the shipping app, forced by what this build is: there, deleting a
 * report publishes a tombstone and takes it off every device. This build never writes to the sync
 * channel - that is the guard that makes it safe to run beside the real app - so a delete here
 * removes the local copy and the next sync brings it back. The sheet says so rather than letting
 * the reader discover it.
 */
@Composable
internal fun NextResultSheets(appState: AppState, page: NextPageState) {
    val colors = LocalNextColors.current
    val id = page.deletingRun ?: return
    val saved = appState.savedResults.firstOrNull { it.id == id }
    if (saved == null) {
        page.deletingRun = null
        return
    }
    val dismiss = { page.deletingRun = null }
    NextModal(
        kicker = "Delete this run",
        tone = colors.stop,
        onDismiss = dismiss,
        title = targetWordsFor(saved.result.recommendationTargetDate),
        body = {
            NextText(
                "Removes this run and everything Insights computed from it. Trades you recorded " +
                    "in Portfolio stay exactly where they are.",
                NextType.name,
                colors.ink2,
            )
            NextModalDivider()
            NextConsequence(
                mark = "\u2212",
                markTone = colors.stop,
                what = "${saved.result.consolidated.size} stocks and their calls",
                where = "Results, Insights",
            )
            NextConsequence(
                mark = "=",
                markTone = colors.target,
                what = "Recorded trades",
                where = "kept",
            )
            NextConsequence(
                mark = "!",
                markTone = colors.expired,
                what = "This build cannot delete it on your other device",
                where = "local only",
            )
        },
        actions = {
            NextButton(
                label = "Delete it here",
                onClick = {
                    appState.deleteResult(saved)
                    dismiss()
                },
                tone = colors.stop,
                fill = NextFill.SOLID,
                modifier = Modifier.weight(1f),
            )
            NextButton("Keep it", dismiss, tone = colors.rule, labelColor = colors.ink3)
        },
    )
}
