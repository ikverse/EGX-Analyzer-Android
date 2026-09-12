package com.ikverse.egxanalyzer.ui

import androidx.compose.runtime.Composable
import com.ikverse.egxanalyzer.ui.theme.EgxAnalyzerTheme

/**
 * The app, themed.
 *
 * There was a second file of this name under `src/next` for as long as the redesign was being built
 * beside the real app, and this one lived in `src/current` so that the two could never be compiled
 * together. The redesign was abandoned on 2026-08-19 and removed on 2026-09-12, so the split has
 * gone with it and there is one root again, in `src/main` with everything else.
 *
 * The theme is applied here rather than in `MainActivity` so that the activity deals in intents and
 * lifecycle and nothing else, and so a screen can be drawn in a `@Preview` without one.
 */
@Composable
internal fun AppRoot(appState: AppState) {
    EgxAnalyzerTheme(themeMode = appState.appPreferences.themeMode) {
        EgxAnalyzerApp(appState = appState)
    }
}
