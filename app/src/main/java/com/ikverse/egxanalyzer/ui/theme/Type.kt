package com.ikverse.egxanalyzer.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
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
val Display = interTight()

/**
 * Inter Tight, bundled once as the upstream variable font and instanced here per weight. minSdk 31
 * reads variation settings natively, so one 580 KB file stands in for four statics.
 *
 * **Since the 2026-11 redesign** this is the Swiss half of the design: page names, tickers, and
 * every figure, set large and tracked tight. It has no Arabic, which is why [Body] stays IBM Plex
 * Sans Arabic - every sentence that can carry a company's Arabic name keeps one voice.
 */
@OptIn(ExperimentalTextApi::class)
private fun interTight() = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold).map { weight ->
        Font(
            R.font.inter_tight,
            weight,
            variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
        )
    },
)

/**
 * Tabular figures on every style, so `.copy(fontFamily = Figures)` at a call site inherits them and a
 * column of prices lines up in Inter Tight the way it did in Plex Mono.
 */
private const val Tabular = "tnum"

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
 * **Inter Tight since the 2026-11 redesign**, with tabular figures switched on in every style below.
 * What follows describes the Plex Mono it replaced.
 *
 * IBM Plex Mono, replacing `FontFamily.Monospace` - which resolved to whatever the device called
 * monospace, matched nothing else on screen, and reached two call sites out of the dozens that draw
 * a number. Its digits are all one width, which is the entire point: a column of prices lines up.
 */
val Figures = Display

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
    // The Swiss numerals: the one figure a screen leads with. Tight tracking, because a large
    // figure set at default spacing falls apart into separate digits.
    displayLarge = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.Medium, fontSize = 64.sp, lineHeight = 64.sp,
        letterSpacing = (-2.6).sp, fontFeatureSettings = Tabular,
    ),
    displayMedium = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.Medium, fontSize = 44.sp, lineHeight = 46.sp,
        letterSpacing = (-1.6).sp, fontFeatureSettings = Tabular,
    ),
    displaySmall = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.Medium, fontSize = 34.sp, lineHeight = 38.sp,
        letterSpacing = (-1.0).sp, fontFeatureSettings = Tabular,
    ),
    // The page name, and nothing else at this size.
    headlineLarge = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 34.sp,
        letterSpacing = (-0.9).sp, fontFeatureSettings = Tabular,
    ),
    headlineMedium = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 25.sp, lineHeight = 30.sp,
        letterSpacing = (-0.6).sp, fontFeatureSettings = Tabular,
    ),
    // A ticker, and a figure a card leads with.
    headlineSmall = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 26.sp,
        letterSpacing = (-0.4).sp, fontFeatureSettings = Tabular,
    ),
    titleLarge = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 24.sp,
        letterSpacing = (-0.3).sp, fontFeatureSettings = Tabular,
    ),
    // From here down it is the body face: labels on things, not names of things. Line heights stay
    // tall because Arabic carries marks above and below the line.
    titleMedium = TextStyle(
        fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp,
        fontFeatureSettings = Tabular,
    ),
    titleSmall = TextStyle(
        fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp,
        fontFeatureSettings = Tabular,
    ),
    bodyLarge = TextStyle(
        fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp,
        fontFeatureSettings = Tabular,
    ),
    bodyMedium = TextStyle(
        fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp,
        fontFeatureSettings = Tabular,
    ),
    bodySmall = TextStyle(
        fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp,
        fontFeatureSettings = Tabular,
    ),
    labelLarge = TextStyle(
        fontFamily = Body, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 18.sp,
        fontFeatureSettings = Tabular,
    ),
    labelMedium = TextStyle(
        fontFamily = Body, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp,
        fontFeatureSettings = Tabular,
    ),
    // The key over a figure. Tracked out, the Swiss way, so a small label reads as a caption.
    labelSmall = TextStyle(
        fontFamily = Body, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp,
        letterSpacing = (0.8).sp, fontFeatureSettings = Tabular,
    ),
)
