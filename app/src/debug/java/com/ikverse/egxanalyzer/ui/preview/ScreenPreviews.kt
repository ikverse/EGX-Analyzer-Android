package com.ikverse.egxanalyzer.ui.preview

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.ikverse.egxanalyzer.model.AnalysisMode
import com.ikverse.egxanalyzer.ui.AnalysisStatus
import com.ikverse.egxanalyzer.ui.AnalyzeScreen
import com.ikverse.egxanalyzer.ui.AppState
import com.ikverse.egxanalyzer.ui.EgxAnalyzerApp
import com.ikverse.egxanalyzer.ui.InsightsScreen
import com.ikverse.egxanalyzer.ui.PortfolioScreen
import com.ikverse.egxanalyzer.ui.ResultsScreen
import com.ikverse.egxanalyzer.ui.SettingsScreen
import com.ikverse.egxanalyzer.ui.theme.EgxAnalyzerTheme

/**
 * Every screen, drawn without the app behind it.
 *
 * These are the point of [FakeAppState] and of the [AppState] interface it implements: a change to
 * a screen can be seen here, in both themes, without a build onto a device, a Telegram sign-in or
 * a provider key - none of which a person redesigning this UI should need.
 *
 * Each screen gets a light preview and a dark one, because this app is used in both and the pair
 * catches the colour that was only ever checked in one of them.
 */
@Composable
private fun Previewed(state: AppState = FakeAppState(), content: @Composable () -> Unit) {
    EgxAnalyzerTheme(themeMode = state.appPreferences.themeMode) { content() }
}

@Preview(name = "Analyze", showBackground = true)
@Preview(name = "Analyze · dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AnalyzePreview() {
    val state = FakeAppState(analysisMode = AnalysisMode.NEXT_DAY)
    Previewed(state) { AnalyzeScreen(state) }
}

@Preview(name = "Analyze · running", showBackground = true)
@Composable
private fun AnalyzeRunningPreview() {
    val state = FakeAppState(
        analysisStatus = AnalysisStatus.RUNNING,
        busyLabel = "Reading 3 channels",
        analysisMessage = "Sending 12 sources to the model",
    )
    Previewed(state) { AnalyzeScreen(state) }
}

@Preview(name = "Results", showBackground = true)
@Preview(name = "Results · dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ResultsPreview() {
    val state = FakeAppState()
    Previewed(state) { ResultsScreen(state) }
}

@Preview(name = "Insights", showBackground = true)
@Preview(name = "Insights · dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun InsightsPreview() {
    val state = FakeAppState()
    Previewed(state) { InsightsScreen(state) }
}

@Preview(name = "Portfolio", showBackground = true)
@Preview(name = "Portfolio · dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PortfolioPreview() {
    val state = FakeAppState()
    Previewed(state) { PortfolioScreen(state) }
}

@Preview(name = "Settings", showBackground = true)
@Preview(name = "Settings · dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SettingsPreview() {
    val state = FakeAppState()
    Previewed(state) { SettingsScreen(state) }
}

/** The whole shell, navigation and all, which is the one that shows how a change reads in place. */
@Preview(name = "App shell", showBackground = true, widthDp = 412, heightDp = 892)
@Composable
private fun AppShellPreview() {
    val state = FakeAppState()
    Previewed(state) { EgxAnalyzerApp(state) }
}
