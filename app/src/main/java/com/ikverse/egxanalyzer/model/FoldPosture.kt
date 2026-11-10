package com.ikverse.egxanalyzer.model

/**
 * How the Fold 7 is being held, which decides how every 5.0 screen is laid out.
 *
 * - [Cover]: the cover screen, or any window under 600dp. One column.
 * - [Unfolded]: the inner screen flat, or a tablet. List and detail side by side; sheets open as a
 *   panel on the right.
 * - [Tabletop]: half open with the hinge running across the screen. What is read sits above the
 *   crease and what is pressed sits below it; nothing crosses it.
 * - [Book]: half open with the hinge running top to bottom. List on one half, detail on the other,
 *   split exactly at the hinge.
 *
 * A plain function of the window's width and what the platform says about the hinge, so it can be
 * tested without a phone; the shell reads `WindowLayoutInfo` and hands the answer down.
 */
sealed interface FoldPosture {
    data object Cover : FoldPosture
    data object Unfolded : FoldPosture

    /**
     * @param hingeStartFraction where the crease begins, as a fraction of the window's height.
     * @param hingeEndFraction where it ends. Equal to the start on a hinge with no width.
     */
    data class Tabletop(val hingeStartFraction: Float, val hingeEndFraction: Float) : FoldPosture

    /** As [Tabletop], measured across the window's width. */
    data class Book(val hingeStartFraction: Float, val hingeEndFraction: Float) : FoldPosture

    /** True where a sheet opens beside the page rather than over it. */
    val opensSheetsAsPanel: Boolean
        get() = this is Unfolded || this is Book

    /** Where a side panel starts, as a fraction of the window's width. */
    val panelStartFraction: Float
        get() = when (this) {
            is Book -> hingeEndFraction
            Unfolded -> 0.5f
            else -> 0f
        }
}

/** What the platform reports about a hinge, reduced to the plain values the posture needs. */
data class HingeReport(
    /** The device is half open, rather than flat. */
    val halfOpened: Boolean,
    /** The hinge runs left to right across the window (tabletop when half open). */
    val runsAcross: Boolean,
    /** Where the hinge's bounds start and end, in px along the axis it divides. */
    val start: Float,
    val end: Float,
    /** The window's extent along that same axis, in px. */
    val windowExtent: Float,
)

/** Windows narrower than this are laid out as the cover screen, whatever the hinge says. */
const val CompactWidthDp = 600f

/**
 * The posture for a window [widthDp] wide with [hinge] (null when the platform reports none).
 *
 * A hinge that is flat does not change the layout: the unfolded Fold lays out as one wide screen,
 * with the split coming from the width, not from the crease.
 */
fun foldPosture(widthDp: Float, hinge: HingeReport?): FoldPosture {
    if (hinge != null && hinge.halfOpened && hinge.windowExtent > 0f) {
        val start = (hinge.start / hinge.windowExtent).coerceIn(0f, 1f)
        val end = (hinge.end / hinge.windowExtent).coerceIn(start, 1f)
        // Half open across the screen is tabletop even when the window is narrow: the Fold held as
        // a laptop is the inner screen, 750dp wide, so this check comes before the width one.
        return if (hinge.runsAcross) FoldPosture.Tabletop(start, end) else FoldPosture.Book(start, end)
    }
    return if (widthDp < CompactWidthDp) FoldPosture.Cover else FoldPosture.Unfolded
}

/**
 * The two columns' widths in book posture, so the gap between them falls on the crease rather than
 * near it, or null where they cannot - the row does not reach across the hinge, or one side would
 * be narrower than [minColumn]. Everything is in px along the window's width.
 *
 * The hinge's fractions are of the whole window, while a page's row starts inside its margins, so
 * splitting the row by those fractions put the gap a margin's width off the fold. The gap is the
 * hinge's own width, or [minGap] where the hinge has none - the Fold's inner panel is one sheet of
 * glass, and two columns meeting on the crease with no gap read as one.
 */
fun hingeColumns(
    rowStart: Float,
    rowWidth: Float,
    hingeStart: Float,
    hingeEnd: Float,
    minGap: Float,
    minColumn: Float,
): Pair<Float, Float>? {
    if (rowWidth <= 0f || hingeEnd < hingeStart) return null
    val gap = maxOf(hingeEnd - hingeStart, minGap)
    val centre = (hingeStart + hingeEnd) / 2f
    val left = centre - gap / 2f - rowStart
    val right = rowStart + rowWidth - (centre + gap / 2f)
    if (left < minColumn || right < minColumn) return null
    return left to right
}
