package com.ikverse.egxanalyzer.next

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The two grounds, and the roles that mean something on both.
 *
 * Neither is a fallback. The app carries its own light/dark setting and ignores the system's, so
 * both were authored - every surface, rule and ink below exists twice, and nothing in the redesign
 * is allowed to name a colour that is not here.
 *
 * Two rules decide how a value ports between them:
 *
 * 1. **Chrome ports by distance from the ground, not by lightness.** The well is darker than the
 *    ground on both (a hole is a hole); [chrome] is lighter than the ground on both; a rule gains
 *    contrast as it strengthens, which means lighter on ink and darker on paper.
 * 2. **The five roles do not mirror.** Their hues are fixed and their separations held, but the
 *    ladder is re-derived on paper: attention goes to the brightest thing on a dark ground and to
 *    the darkest on a light one. So on ink a win glares; on paper a loss bites. In both, the
 *    sentence still holds - a loss sits *down* in the page.
 *
 * Authored in OKLCH, which is where the separations were reasoned about, and converted once to
 * sRGB here; the source value is on every line so the arithmetic can be checked rather than
 * trusted. [target] and [stop] are the read this whole app is built on, so they are held to three
 * invariants on both grounds: hue about 130 degrees apart, lightness at least 0.18 apart, and a
 * filled up/down glyph beside the figure. Any one of the three alone decodes the row.
 */
@Immutable
internal class NextColors(
    /** The bare region everything is drawn on. Lists, tables, most of every screen. */
    val ground: Color,
    /** Cut down into the ground: one thing has been opened, selected or pressed. */
    val well: Color,
    /** The only surface above the ground - rail, filter row, banner, modal. Never holds content. */
    val chrome: Color,
    /** Between rows. */
    val ruleSoft: Color,
    /** Around a block. */
    val rule: Color,
    /** Above a block, and the dashed edge of anything excluded. */
    val ruleStrong: Color,
    /** Body text, and any figure that is the reader's reference. */
    val ink: Color,
    /** Supporting sentences. */
    val ink2: Color,
    /** Labels, captions, and the things a reader skips. */
    val ink3: Color,
    /** The app's own hand: selection, focus, the arrival flash, the run button. Never a figure. */
    val accent: Color,
    /** [accent] as a wash, for the ground under a selected control. */
    val accentFill: Color,
    /** What the call asks you to pay. Chromaless - the reference, not a verdict. */
    val entry: Color,
    /** Where the call says to take profit. */
    val target: Color,
    /** Where the call says to give up. */
    val stop: Color,
    /** A price the market actually reached. Provenance, not verdict. */
    val market: Color,
    /** A trade that ran out of time. Neither red nor green, and always drawn hollow. */
    val expired: Color,
    /** Dates, notes, counts. Recedes. */
    val figMuted: Color,
    /** [target] as a fill. */
    val targetFill: Color,
    /** [target] as an edge. */
    val targetEdge: Color,
    /** [stop] as a fill. */
    val stopFill: Color,
    /** [market] as an edge. */
    val marketEdge: Color,
    /** Everything behind a modal. */
    val scrim: Color,
    /**
     * How far a figure the app worked out is softened from one a source printed.
     *
     * Opacity rather than a colour of its own: greying a derived figure put the column in two hues
     * depending only on whether a channel happened to print the number. Hue keeps saying the role;
     * opacity says the provenance. Paper eats opacity, so a derived figure keeps more of itself
     * there.
     */
    val derivedAlpha: Float,
    /** Which ground this is. Only the window's own system bars should need to ask. */
    val dark: Boolean,
)

/** The authored dark ground: cold ink, not grey, and deliberately not black. */
internal val InkColors = NextColors(
    ground = Color(0xFF080D12), // oklch(0.155 0.014 254)
    well = Color(0xFF030509), // oklch(0.115 0.012 254) - 4 L below the ground
    chrome = Color(0xFF11151B), // oklch(0.195 0.014 254) - 4 L above it
    ruleSoft = Color(0xFF1A2027), // oklch(0.24 0.016 254)
    rule = Color(0xFF2C343D), // oklch(0.32 0.020 254)
    ruleStrong = Color(0xFF505964), // oklch(0.46 0.022 254)
    ink = Color(0xFFEEEBE4), // oklch(0.94 0.010 85)
    ink2 = Color(0xFF9FA5AD), // oklch(0.72 0.014 254)
    ink3 = Color(0xFF6B727B), // oklch(0.55 0.016 254)
    accent = Color(0xFF8F80EB), // oklch(0.66 0.155 288)
    accentFill = Color(0x248F80EB), // the same violet at 0.14
    entry = Color(0xFFE9E4DA), // oklch(0.92 0.014 85) - bone, the one warm note
    target = Color(0xFF63F0A8), // oklch(0.86 0.16 158) - the brightest ink in the app
    stop = Color(0xFFDB4144), // oklch(0.60 0.19 24) - 26 L points below target
    market = Color(0xFF73C4E2), // oklch(0.78 0.09 224)
    expired = Color(0xFFDB9E2E), // oklch(0.74 0.14 78)
    figMuted = Color(0xFF808790), // oklch(0.62 0.016 254)
    targetFill = Color(0x2163F0A8), // 0.13
    targetEdge = Color(0x8C63F0A8), // 0.55
    stopFill = Color(0x2EDB4144), // 0.18
    marketEdge = Color(0x9973C4E2), // 0.60
    scrim = Color(0xC7010304), // oklch(0.09 0.010 254) at 0.78
    derivedAlpha = 0.62f,
    dark = true,
)

/** The authored light ground: cool paper, never white - white is reserved for chrome. */
internal val PaperColors = NextColors(
    ground = Color(0xFFF5F7F9), // oklch(0.975 0.004 254)
    well = Color(0xFFE9EDF2), // oklch(0.945 0.008 254) - still a hole, not a tile
    chrome = Color(0xFFFFFFFF), // oklch(1 0 0)
    ruleSoft = Color(0xFFDCE0E5), // oklch(0.905 0.008 254)
    rule = Color(0xFFC5CBD2), // oklch(0.840 0.012 254)
    ruleStrong = Color(0xFF7A818A), // oklch(0.600 0.016 254)
    ink = Color(0xFF1B2025), // oklch(0.24 0.012 254)
    ink2 = Color(0xFF4D535A), // oklch(0.44 0.014 254)
    ink3 = Color(0xFF767C84), // oklch(0.585 0.014 254)
    accent = Color(0xFF6346C7), // oklch(0.50 0.19 288)
    accentFill = Color(0x1A6346C7), // 0.10
    entry = Color(0xFF312D27), // oklch(0.30 0.012 85) - the page's own ink, 13:1
    target = Color(0xFF009147), // oklch(0.575 0.155 152) - the lighter of the pair
    stop = Color(0xFF8A000F), // oklch(0.395 0.165 25) - oxblood, 0.18 L darker than target
    market = Color(0xFF57738F), // oklch(0.545 0.055 248)
    expired = Color(0xFF8F5000), // oklch(0.495 0.115 62)
    figMuted = Color(0xFF747980), // oklch(0.575 0.012 254)
    targetFill = Color(0x1A009147), // 0.10
    targetEdge = Color(0x8C009147), // 0.55
    stopFill = Color(0x178A000F), // 0.09
    marketEdge = Color(0x9957738F), // 0.60
    // Derived rather than authored: the light theme was designed down to the card and the table,
    // and no modal was drawn on paper. A scrim can only go one way here - a ground at L .975 has
    // nothing above it to dim towards - so it is the page's own ink, at the alpha that lifts a
    // white panel off a near-white ground without turning the screen into the other theme.
    scrim = Color(0x731B2025), // 0.45
    derivedAlpha = 0.72f,
    dark = false,
)

/**
 * The colours in force.
 *
 * Static rather than dynamic: the whole tree re-reads them when the theme changes, which is the one
 * time they change at all.
 */
internal val LocalNextColors = staticCompositionLocalOf { InkColors }
