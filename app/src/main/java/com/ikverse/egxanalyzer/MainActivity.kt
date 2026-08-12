package com.ikverse.egxanalyzer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.ikverse.egxanalyzer.data.AnalysisNotifier
import com.ikverse.egxanalyzer.data.OverdueNotifier
import com.ikverse.egxanalyzer.ui.AppDestination
import com.ikverse.egxanalyzer.ui.EgxAnalyzerApp
import com.ikverse.egxanalyzer.ui.theme.EgxAnalyzerTheme

class MainActivity : ComponentActivity() {

    private val appState by lazy { (application as EgxApplication).appState }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openRequestedResult(intent)
        setContent {
            EgxAnalyzerTheme(themeMode = appState.appPreferences.themeMode) {
                // How overdue a trade is depends on the date, and nothing announces midnight. A
                // phone left on the Portfolio tab overnight would otherwise show yesterday's count
                // until something else happened to recompute.
                LifecycleResumeEffect(Unit) {
                    appState.refreshOverdue()
                    onPauseOrDispose { }
                }
                EgxAnalyzerApp(activity = this, appState = appState)
            }
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
    }
}
