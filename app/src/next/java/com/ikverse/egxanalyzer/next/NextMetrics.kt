package com.ikverse.egxanalyzer.next

import androidx.compose.ui.unit.dp

/**
 * Distance, edge and corner, named once.
 *
 * The spacing scale is 2, 4, 6, 9, 12, 14, 22, 34 and nothing between. It is tighter than a
 * consumer app's because this is a record: a row's rhythm is 9 or 12, 22 separates sections, and 34
 * appears once on a screen, under its title. A list row is 44 tall and still clears the touch
 * minimum, because the tap target is the whole row rather than a control inside it.
 *
 * There is no elevation anywhere in this app, and so no shadow: hierarchy is carried by an edge and
 * by which of the three surfaces something sits on. A Compose shadow would be the one thing here
 * that could not be drawn deliberately.
 */
internal object NextMetrics {

    // The spacing scale.
    val space1 = 2.dp
    val space2 = 4.dp
    val space3 = 6.dp
    /** A row's own rhythm, and the tighter of the two. */
    val space4 = 9.dp
    /** The other one. Vertical padding inside almost everything. */
    val space5 = 12.dp
    /** Horizontal padding inside almost everything. */
    val space6 = 14.dp
    /** Between sections. */
    val space7 = 22.dp
    /** Once per screen, under the title. */
    val space8 = 34.dp

    /** Every edge in the app. One pixel, and nothing else. */
    val hairline = 1.dp

    /** The mark down the side of something opened, selected, or arriving. */
    val spine = 2.dp

    /**
     * Two on a chip or an input, zero everywhere else.
     *
     * Rules do not have corners, and this alone kills the Material read.
     */
    val chipCorner = 2.dp

    /** A row in a list, including the whole of its tap target. */
    val rowHeight = 44.dp

    /** The shortest anything pressable is allowed to be. */
    val tapMinimum = 44.dp

    // ---- The shell -------------------------------------------------------------------------

    /**
     * Where the navigation stops being a bar and becomes a rail.
     *
     * Measured on the window rather than on the device, so the phone unfolding and an app moving
     * into a smaller window are the same event to everything downstream.
     */
    val railBreakpoint = 600.dp

    /** The bar, above whatever inset the system asks for underneath it. */
    val navBarHeight = 56.dp

    /** The rail. */
    val navRailWidth = 86.dp

    /** One destination, in either arrangement. */
    val navItemHeight = 52.dp

    /** What a screen keeps between itself and the edge of the window, with a bar below. */
    val screenPadding = 13.dp

    /** And with a rail beside it, where there is room to breathe. */
    val screenPaddingWide = 15.dp

    /** How far a modal is allowed to grow once there is a rail beside it. */
    val modalWidth = 430.dp

    /** How wide a toast is allowed to get before it stops following the window. */
    val toastWidth = 420.dp
}
