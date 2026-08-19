package com.ikverse.egxanalyzer.next

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.core.view.WindowCompat
import com.ikverse.egxanalyzer.model.ThemeMode

/**
 * A theme of its own, and now actually one.
 *
 * It holds the colours and nothing else, because that is all a theme in this app is: the type scale
 * lives in [NextType], distance in [NextMetrics] and time in [NextMotion], and none of the three
 * changes when the ground does. What changes is which of the two authored grounds every screen is
 * drawn on, and that is one lookup.
 *
 * There is no Material theme anywhere underneath. That is deliberate rather than purist: this
 * redesign exists because the last one started from the Material baseline and adjusted it, and a
 * `MaterialTheme` in the tree is an invitation for a component to draw a stock shape, a stock
 * ripple or a stock elevation on a screen that has none of those.
 *
 * The mode comes from the app's own setting, not the system's. The shipping app already works this
 * way, and here it is a design requirement rather than a preference: both grounds were authored, so
 * neither is a fallback, and a reader who chose paper gets paper in a dark room.
 */
@Composable
internal fun NextTheme(
    activity: Activity,
    themeMode: ThemeMode,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = if (dark) InkColors else PaperColors

    // The window is drawn edge to edge, so the system's own clock and gesture bar sit on this
    // app's ground and have to be legible against it. Nothing else in here touches the window.
    SideEffect {
        val window = activity.window
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = !dark
        controller.isAppearanceLightNavigationBars = !dark
    }

    CompositionLocalProvider(LocalNextColors provides colors, content = content)
}
