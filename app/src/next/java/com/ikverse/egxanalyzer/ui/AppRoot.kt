package com.ikverse.egxanalyzer.ui

import android.app.Activity
import androidx.compose.runtime.Composable
import com.ikverse.egxanalyzer.next.NextApp

/**
 * The redesign.
 *
 * One of two files with this name and this signature; the other is in `src/current` and draws the
 * app as it ships. Only one is ever compiled, chosen by build type - see the `sourceSets` block in
 * `app/build.gradle.kts`.
 *
 * This side deliberately reaches into `next` and nowhere else in `ui`. Everything below the UI -
 * `AppState`, the repositories, the scoring, the database - is shared; not one composable is. That
 * is what "from scratch" was chosen to mean, and this import list is where it is enforced: the
 * moment this file imports a screen from `ui`, the redesign has started copying the thing it is
 * replacing.
 *
 * No theme wrapper here. The redesign brings its own, because a palette and a type scale are among
 * the first things it is meant to disagree about.
 */
@Composable
internal fun AppRoot(activity: Activity, appState: AppState) {
    NextApp(activity = activity, appState = appState)
}
