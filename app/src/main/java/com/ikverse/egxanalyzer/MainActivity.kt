package com.ikverse.egxanalyzer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ikverse.egxanalyzer.ui.EgxAnalyzerApp
import com.ikverse.egxanalyzer.ui.theme.EgxAnalyzerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val appState = (application as EgxApplication).appState
            EgxAnalyzerTheme(themeMode = appState.appPreferences.themeMode) {
                EgxAnalyzerApp(activity = this, appState = appState)
            }
        }
    }
}
