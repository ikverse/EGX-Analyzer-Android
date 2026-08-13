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
import com.ikverse.egxanalyzer.ui.AppRoot

class MainActivity : ComponentActivity() {

    private val appState by lazy { (application as EgxApplication).appState }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openRequestedResult(intent)
        setContent {
            // How overdue a trade is depends on the date, and nothing announces midnight. A phone
            // left on the Portfolio tab overnight would otherwise show yesterday's count until
            // something else happened to recompute. Outside the root on purpose: it is app
            // behaviour rather than UI, so both versions of the UI get it without either owning it.
            LifecycleResumeEffect(Unit) {
                appState.refreshOverdue()
                onPauseOrDispose { }
            }
            // Which UI this is depends on the build type, and this activity does not know. Two
            // files named AppRoot.kt - one in src/current, one in src/next - and exactly one of them
            // is compiled. See the sourceSets block in app/build.gradle.kts.
            AppRoot(activity = this, appState = appState)
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
