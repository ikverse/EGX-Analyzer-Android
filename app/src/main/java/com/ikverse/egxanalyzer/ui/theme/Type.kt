package com.ikverse.egxanalyzer.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ikverse.egxanalyzer.R

/**
 * Three faces, chosen for the one problem this app's text actually has.
 *
 * A row here reads `AMOC · الإسكندرية للزيوت المعدنية · 7.20 – 7.45`: Arabic, Latin and figures in a
 * single line, with the figures meant to be compared down a column. Roboto at Material's default
 * scale answered none of that - it set the Arabic in whatever the system fell back to, and the
 * figures in proportional digits that put the same price at a different width on every card.
 *
 * Bundled rather than downloadable: this app is sideloaded onto two phones and has to draw its own
 * record with no network. All three are SIL Open Font License; see THIRD-PARTY-NOTICES.md.
 */

/**
 * Page names, tickers, and the one figure a screen leads with. Nothing else.
 *
 * Cairo, and named for the city the exchange is in. It is Arabic-first with a Latin companion drawn
 * against it rather than the other way round, which is the right way up for a screen whose headings
 * are English and whose subjects are Arabic. Used with restraint deliberately - it carries the
 * personality, and a page set entirely in it would be a poster rather than a record.
 *
 * Instanced from the upstream variable font at two weights; see the download step in the commit
 * that added these files.
 */
val Display = FontFamily(
    Font(R.font.cairo_semibold, FontWeight.SemiBold),
    Font(R.font.cairo_bold, FontWeight.Bold),
)

/**
 * Every sentence the app speaks, and every label on a figure.
 *
 * IBM Plex Sans Arabic, which is one of the few families with a genuine Arabic companion rather than
 * a fallback - so a channel name that wraps keeps the same voice as the line above it - and which is
 * metrically the sibling of [Figures], so a label and the number under it belong together.
 */
val Body = FontFamily(
    Font(R.font.plex_arabic_regular, FontWeight.Normal),
    Font(R.font.plex_arabic_medium, FontWeight.Medium),
    Font(R.font.plex_arabic_semibold, FontWeight.SemiBold),
)

/**
 * Every figure: prices, percentages, counts, dates.
 *
 * IBM Plex Mono, replacing `FontFamily.Monospace` - which resolved to whatever the device called
 * monospace, matched nothing else on screen, and reached two call sites out of the dozens that draw
 * a number. Its digits are all one width, which is the entire point: a column of prices lines up.
 */
val Figures = FontFamily(
    Font(R.font.plex_mono_regular, FontWeight.Normal),
    Font(R.font.plex_mono_medium, FontWeight.Medium),
)

/**
 * The scale, set rather than inherited.
 *
 * What this replaces was Material's default `Typography()` with a few weights overridden, which is
 * to say no scale at all. The steps below tighten as they descend - generous tracking is for
 * headings and would loosen a table - and the body line heights are deliberately taller than a
 * Latin-only scale would need, because Arabic carries marks above and below the line that a 1.4
 * multiple clips.
 *
 * Where a role sets numbers rather than words, the call site adds [Figures] on top; the role only
 * decides the size.
 */
internal val AppTypography = Typography(
    // The page name, and nothing else at this size.
    headlineLarge = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.Bold,
        fontSize = 25.sp,
        lineHeight = 31.sp,
        letterSpacing = (-0.4).sp,
    ),
    // A ticker, and a figure a card leads with.
    headlineSmall = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp,
        lineHeight = 27.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 25.sp,
    ),
    // From here down it is the body face: these are labels on things, not names of things.
    titleMedium = TextStyle(
        fontFamily = Body,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = Body,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Body,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Body,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Body,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Body,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Body,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    // The uppercase key over a figure. Tracked out, because uppercase set solid is a wall.
    labelSmall = TextStyle(
        fontFamily = Body,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.6.sp,
    ),
)
