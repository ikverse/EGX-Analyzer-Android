package com.ikverse.egxanalyzer.ui

import android.app.Activity
import androidx.compose.runtime.Composable
import com.ikverse.egxanalyzer.ui.theme.EgxAnalyzerTheme

/**
 * The app as it ships today.
 *
 * One of two files with this name and this signature; the other is in `src/next` and draws the
 * redesign. Only one is ever compiled, chosen by build type - see the `sourceSets` block in
 * `app/build.gradle.kts` for why the choice is made there rather than by a branch in the code.
 *
 * The theme is applied here rather than in `MainActivity` because it is part of what the two
 * versions disagree about. The activity is left knowing only that there is a root to draw.
 */
@Composable
internal fun AppRoot(activity: Activity, appState: AppState) {
    EgxAnalyzerTheme(themeMode = appState.appPreferences.themeMode) {
        EgxAnalyzerApp(activity = activity, appState = appState)
    }
}
