package com.ikverse.egxanalyzer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.ikverse.egxanalyzer.data.AnalysisNotifier
import com.ikverse.egxanalyzer.data.OverdueNotifier
import com.ikverse.egxanalyzer.data.CallAlertNotifier
import com.ikverse.egxanalyzer.data.TradeStatusNotifier
import com.ikverse.egxanalyzer.data.holdsBackupFolder
import com.ikverse.egxanalyzer.data.writeBackupTo
import com.ikverse.egxanalyzer.ui.AppDestination
import com.ikverse.egxanalyzer.ui.AppRoot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
            // behaviour rather than UI, so both versions of the UI get it without either owning it.
            val scope = rememberCoroutineScope()
            LifecycleResumeEffect(Unit) {
                appState.refreshOverdue()
                backUpIfDue(scope)
                onPauseOrDispose { }
            }
            // Which UI this is depends on the build type, and this activity does not know. Two
            // files named AppRoot.kt - one in src/current, one in src/next - and exactly one of them
            // is compiled. See the sourceSets block in app/build.gradle.kts.
            AppRoot(activity = this, appState = appState)
        }
    }

    /**
     * Writes the day's backup into the folder the user picked, if one is due.
     *
     * On resume rather than on start, so a phone left open across midnight still gets that day's
     * copy, and guarded by day rather than by launch - a phone opened six times before lunch would
     * otherwise write six copies of an unchanged record and push five real days out of the seven a
     * folder keeps. Outside the root like the overdue refresh above and for the same reason: it is
     * app behaviour, so both versions of the UI get it without either owning it.
     *
     * **Only into a chosen folder, never into Downloads.** The manual button falls back there, and
     * that is right for something somebody pressed; doing it daily would pile a file per day into
     * Downloads forever, because MediaStore appends rather than replaces and nothing prunes it.
     *
     * The day is recorded **only on success**, so a write that failed - the phone asleep, the cloud
     * app offline, the card removed - is tried again on the next resume rather than counted as
     * done. Nothing is announced either way: a message about a backup nobody asked for, on top of
     * an app that has just opened, is noise. What reveals a folder that has quietly stopped
     * accepting writes is the line in Settings naming how many copies it holds and the newest one.
     */
    private fun backUpIfDue(scope: CoroutineScope) {
        val folder = appState.backupFolder?.let(Uri::parse) ?: return
        if (!holdsBackupFolder(this, folder)) return
        if (!appState.backupDue()) return
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    writeBackupTo(
                        context = this@MainActivity,
                        folder = folder,
                        database = appState.databaseFile(),
                        settings = appState.settingsDocument(),
                        device = appState.backupDevice(),
                        checkpoint = appState::checkpointDatabase,
                    )
                }
            }.onSuccess { appState.recordBackupDay() }
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

    private fun openRequestedResult(intent: Intent?) {
        val id = intent?.getLongExtra(AnalysisNotifier.EXTRA_RESULT_ID, -1L) ?: -1L
        if (id > 0) appState.openSavedResult(id)
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
            ?.let(appState::openPosition)
        // A buy-zone alert names one call, so it opens that call. `openCall` is the same entrance
        // the Portfolio uses to press through to a recommendation, for the same reason as above.
        intent?.getStringExtra(CallAlertNotifier.EXTRA_CALL_ID)
            ?.takeIf(String::isNotBlank)
            ?.let(appState::openCall)
    }
}
