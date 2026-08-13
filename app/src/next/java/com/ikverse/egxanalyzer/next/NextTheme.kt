package com.ikverse.egxanalyzer.next

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * A theme of its own, and at the moment barely one.
 *
 * It exists so the redesign has a single place to put a palette, a type scale and a shape system
 * when it has them, rather than reaching for the shipping app's theme and inheriting the look it is
 * supposed to replace. Right now it is Material's stock dark scheme with nothing said about it -
 * which is not a starting point to build on, it is a blank the design has to fill.
 *
 * Deliberately not wired to `appPreferences.themeMode` yet. Whether this app even has a light mode
 * is a design question; the shipping app answers yes, and copying that answer before it is asked
 * would be the same mistake as copying the palette.
 */
@Composable
internal fun NextTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(), content = content)
}
