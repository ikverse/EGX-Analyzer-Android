package com.ikverse.egxanalyzer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.ikverse.egxanalyzer.data.AnalysisNotifier
import com.ikverse.egxanalyzer.data.OverdueNotifier
import com.ikverse.egxanalyzer.data.AppShortcuts
import com.ikverse.egxanalyzer.data.AttentionNotifier
import com.ikverse.egxanalyzer.data.CallAlertNotifier
import com.ikverse.egxanalyzer.data.TradeStatusNotifier
import com.ikverse.egxanalyzer.data.WatchlistNotifier
import com.ikverse.egxanalyzer.ui.AppDestination
import com.ikverse.egxanalyzer.ui.AppRoot
import com.ikverse.egxanalyzer.ui.NavStop

class MainActivity : ComponentActivity() {

    private val appState by lazy { (application as EgxApplication).appState }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Before anything is drawn, and idempotent. Almost always this is the call that built the
        // state a line earlier and it does nothing; the case it exists for is a process the alarm
        // woke to fetch prices, which came up without Telegram and would otherwise show an app
        // with no chats in it. See `AppState.enterForeground`.
        appState.enterForeground()
        enableEdgeToEdge()
        openRequestedResult(intent)
        setContent {
            // How overdue a trade is depends on the date, and nothing announces midnight. A phone
            // left on the Portfolio tab overnight would otherwise show yesterday's count until
            // something else happened to recompute. Outside the root on purpose: it is app
            // behaviour rather than UI, and no screen should have to own it.
            LifecycleResumeEffect(Unit) {
                appState.refreshOverdue()
                // On resume rather than on start, so a phone left open across midnight still gets
                // that day's copy. The write itself runs on the app's scope - see
                // AppState.backUpIfDue - so a fold mid-write is not recorded as a failure.
                appState.backUpIfDue()
                onPauseOrDispose {
                    // The other way a look at Insights ends. The shell takes the same mark when the
                    // reader changes tab; this covers the reader who was still on it when the phone
                    // went in their pocket. See AppState.markInsightsSeen.
                    if (appState.destination == AppDestination.INSIGHTS) {
                        appState.markInsightsSeen()
                    }
                }
            }
            AppRoot(appState = appState)
        }
    }

    /**
     * The activity is `singleTask`, so a notification tap reaches the running app through here
     * rather than starting a second copy of it.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openRequestedResult(intent)
    }

    /**
     * Every jump here records no return, and that is deliberate.
     *
     * A notification arrives at a tab the reader was not on and, most of the time, at an app that
     * was not running - so there is no "where they came from" to go back to. Recording one would
     * make the first back press land them on the Analyze tab they never visited, which is a worse
     * answer than the system's own: leave. See [NavStop].
     */
    private fun openRequestedResult(intent: Intent?) {
        val id = intent?.getLongExtra(AnalysisNotifier.EXTRA_RESULT_ID, -1L) ?: -1L
        if (id > 0) appState.openSavedResult(id, returnTo = null)
        // The overdue reminder names trades, so it has to land on the tab that holds them. Opening
        // the app somewhere else and leaving the user to find them would waste the notification.
        if (intent?.getBooleanExtra(OverdueNotifier.EXTRA_SHOW_PORTFOLIO, false) == true) {
            appState.navigate(AppDestination.PORTFOLIO)
            appState.refreshOverdue()
        }
        // A trade status notification names one trade, so it opens that trade rather than the tab
        // it lives on. `openPosition` is the same entrance a call in Insights uses - it unfolds the
        // session card, scrolls to the trade and flashes its edge - and going through it is what
        // keeps a notification from becoming a second, quieter way of finding a position.
        intent?.getStringExtra(TradeStatusNotifier.EXTRA_POSITION_ID)
            ?.takeIf(String::isNotBlank)
            ?.let { appState.openPosition(it, returnTo = null) }
        // Record sale, from the notification's own action. The same arrival as above plus the
        // dialog, because the two figures a sale needs are the user's and the app has neither.
        intent?.getStringExtra(TradeStatusNotifier.EXTRA_SELL_POSITION_ID)
            ?.takeIf(String::isNotBlank)
            ?.let(appState::openPositionToSell)
        // A buy-zone alert names one call, so it opens that call. `openCall` is the same entrance
        // the Portfolio uses to press through to a recommendation, for the same reason as above.
        // The launcher shortcuts, read off the action. They carry no id and name no card - a
        // shortcut is a way into a tab, which is as much as a long-press on an icon can promise.
        when (intent?.action) {
            AppShortcuts.ACTION_PORTFOLIO -> {
                appState.navigate(AppDestination.PORTFOLIO)
                appState.refreshOverdue()
            }
            AppShortcuts.ACTION_INSIGHTS -> appState.navigate(AppDestination.INSIGHTS)
        }
        // A feed that has gone quiet, answered in Settings.
        if (intent?.getBooleanExtra(AttentionNotifier.EXTRA_SHOW_SETTINGS, false) == true) {
            appState.navigate(AppDestination.SETTINGS)
        }
        intent?.getStringExtra(CallAlertNotifier.EXTRA_CALL_ID)
            ?.takeIf(String::isNotBlank)
            ?.let { appState.openCall(it, returnTo = null) }
        // A watch alert names one stock, so it opens that stock's sheet.
        intent?.getStringExtra(WatchlistNotifier.EXTRA_OPEN_STOCK)
            ?.takeIf(String::isNotBlank)
            ?.let(appState::openStock)
    }
}
