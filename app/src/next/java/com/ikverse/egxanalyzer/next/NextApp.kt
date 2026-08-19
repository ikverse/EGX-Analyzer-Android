package com.ikverse.egxanalyzer.next

import android.app.Activity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.ikverse.egxanalyzer.data.UpdateState
import com.ikverse.egxanalyzer.ui.AppDestination
import com.ikverse.egxanalyzer.ui.AppState
import kotlin.math.roundToInt

/**
 * EGX Analyzer NEXT - the redesign, rebuilt from zero.
 *
 * Everything a screen sits inside is here: the two authored grounds, the type scale, the spacing
 * and motion vocabularies, and the shell that frames all five destinations at every width. And now
 * the five destinations themselves, each in a file of its own, sharing one card, one chip, one
 * figure treatment and one press - because this is one system rather than five screens.
 *
 * It imports nothing from the shipping UI beyond [AppState] and [AppDestination]. Everything below
 * the UI - the repositories, the scoring, the database - is shared; not one composable is. This
 * import list is where that is enforced, and the day a screen appears in it, the redesign has
 * started copying the thing it replaces.
 */
@Composable
internal fun NextApp(activity: Activity, appState: AppState) {
    NextTheme(activity = activity, themeMode = appState.appPreferences.themeMode) {
        val colors = LocalNextColors.current
        // Above the destination switch on purpose: leaving a tab disposes its screen, and a card
        // the reader opened or a sheet they half filled in must not be a casualty of checking
        // something on another tab. See NextPageState.
        val page = remember { NextPageState() }
        // A state, not a message. The rail says whether the app is working; what it is working on
        // is the toast's business, and the failure's.
        val status = if (appState.busyLabel != null) "running" else "idle"
        NextShell(
            destination = appState.destination,
            onNavigate = appState::navigate,
            status = status,
            statusTone = if (appState.busyLabel != null) colors.accent else colors.target,
            banner = updateBanner(appState),
            toast = appState.statusMessage
                ?.takeIf { it.succeeded }
                ?.let { message ->
                    ShellToast(
                        text = message.text,
                        action = ShellAction("Dismiss", appState::consumeStatusMessage),
                    )
                },
            error = appState.statusMessage
                ?.takeIf { !it.succeeded }
                ?.let { message ->
                    ShellError(
                        kicker = "Stopped",
                        title = message.text,
                        // The provider's own sentence is already the whole of what is known.
                        body = "",
                        primary = null,
                        dismiss = ShellAction("Dismiss", appState::consumeStatusMessage),
                    )
                },
            modal = {
                NextTradeSheets(appState, page)
                NextResultSheets(appState, page)
                NextAnalyzeSheets(appState, page)
                NextRuleSheet(appState, page)
                NextBuySheet(appState, page)
            },
        ) { contentPadding ->
            when (appState.destination) {
                AppDestination.PORTFOLIO ->
                    NextPortfolioScreen(appState, page, contentPadding)

                AppDestination.INSIGHTS ->
                    NextInsightsScreen(appState, page, contentPadding)

                AppDestination.RESULTS ->
                    NextResultsScreen(activity, appState, page, contentPadding)

                AppDestination.ANALYZE ->
                    NextAnalyzeScreen(activity, appState, page, contentPadding)

                AppDestination.SETTINGS ->
                    NextSettingsScreen(activity, appState, page, contentPadding)
            }
        }
    }
}

/**
 * The update, as the shell shows it.
 *
 * Only the four states that are worth interrupting a reader for. Checking, up-to-date and idle are
 * answers to a question asked in Settings and belong on that screen, not across the top of whatever
 * this one is.
 */
@Composable
private fun updateBanner(appState: AppState): ShellBanner? {
    val colors = LocalNextColors.current
    val later = ShellAction("Later", appState::dismissUpdate)
    return when (val state = appState.updateState) {
        is UpdateState.Available -> ShellBanner(
            label = "Update",
            text = "${state.update.versionName} available",
            tone = colors.expired,
            primary = ShellAction("Download") { appState.downloadUpdate(state.update) },
            secondary = later,
        )

        is UpdateState.Downloading -> ShellBanner(
            label = "Update",
            text = "${state.update.versionName} · ${(state.progress * 100).roundToInt()}%",
            tone = colors.expired,
            primary = null,
            secondary = later,
        )

        is UpdateState.Ready -> ShellBanner(
            label = "Update",
            text = "${state.update.versionName} ready to install",
            tone = colors.expired,
            primary = ShellAction("Install") { appState.installUpdate(state.file) },
            secondary = later,
        )

        is UpdateState.Failed -> ShellBanner(
            label = "Update failed",
            text = state.reason,
            tone = colors.stop,
            primary = null,
            secondary = ShellAction("Dismiss", appState::dismissUpdate),
        )

        UpdateState.Idle, UpdateState.Checking, is UpdateState.UpToDate -> null
    }
}
