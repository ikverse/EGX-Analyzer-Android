package com.ikverse.egxanalyzer.ui

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
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
    val access = rememberAccessibility()
    CompositionLocalProvider(
        LocalReduceMotion provides access.reduceMotion,
        LocalSolidSurfaces provides (access.reduceMotion || access.highContrast),
    ) {
        EgxAnalyzerTheme(
            themeMode = appState.appPreferences.themeMode,
            pureBlackDarkMode = appState.appPreferences.pureBlackDarkMode,
        ) {
            EgxAnalyzerApp(appState = appState)
        }
    }
}

/** The two system settings the app's own motion and glass answer to. */
private data class Accessibility(val reduceMotion: Boolean, val highContrast: Boolean)

/**
 * Read at the root and again on every resume, because the settings live in another app: somebody
 * turns animations off in system settings, comes back, and expects the app to have noticed. Platform
 * `Settings` rather than anything of this app's, so nothing in `ui` reaches into `data`.
 */
@Composable
private fun rememberAccessibility(): Accessibility {
    val context = LocalContext.current
    var access by remember { mutableStateOf(readAccessibility(context)) }
    LifecycleResumeEffect(context) {
        access = readAccessibility(context)
        onPauseOrDispose { }
    }
    return access
}

private fun readAccessibility(context: Context): Accessibility {
    val resolver = context.contentResolver
    val animatorScale = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    // The key is the platform's own and has no public constant; an absent one reads as off.
    val contrast = Settings.Secure.getInt(resolver, "high_text_contrast_enabled", 0) == 1
    return Accessibility(reduceMotion = animatorScale == 0f, highContrast = contrast)
}
