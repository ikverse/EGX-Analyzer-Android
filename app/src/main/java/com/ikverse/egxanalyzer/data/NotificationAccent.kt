package com.ikverse.egxanalyzer.data

/**
 * The app's amber accent, as the colour every notification carries: it tints the small icon and
 * the app name in the shade. Restated here rather than read from the theme because `data` imports
 * nothing from `ui`. It is the light theme's `PageAccent.ink` rather than its fill, because the app
 * name is text and the pale fill does not read as text on a light shade.
 */
internal const val NotificationAccent: Int = 0xFF9A5A06.toInt()
