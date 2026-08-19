package com.ikverse.egxanalyzer.next

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.ikverse.egxanalyzer.R

/**
 * One family, two voices.
 *
 * IBM Plex Sans Arabic for every word, IBM Plex Mono for every figure. Plex is an engineered face,
 * drawn for machine readouts, with a mechanical rhythm and flat terminals that suit a log and would
 * look wrong on a consumer app - and it is here for one hard reason: the Arabic is a real design
 * metrically matched to the Latin, so a channel name and a ticker share a baseline, a weight axis
 * and a voice on a row that changes direction mid-sentence.
 */
internal val Sans = FontFamily(
    Font(R.font.plex_arabic_regular, FontWeight.Normal),
    Font(R.font.plex_arabic_medium, FontWeight.Medium),
    Font(R.font.plex_arabic_semibold, FontWeight.SemiBold),
)

/**
 * Every figure: prices, percentages, counts, dates, and the two-letter marks in the shell.
 *
 * Plex Mono carries the personality where the content is - a slit zero, a one with a full foot, a
 * flat-topped seven, so 0, 1 and 7 never confuse each other down a column of prices - and its
 * digits are tabular natively, which is the only reason a column of prices lines up at all.
 *
 * The 600 and 700 weights are the two files this build type adds for itself; the shipping app sets
 * no figure above 500 and has no use for them.
 */
internal val Figures = FontFamily(
    Font(R.font.plex_mono_regular, FontWeight.Normal),
    Font(R.font.plex_mono_medium, FontWeight.Medium),
    Font(R.font.plex_mono_semibold, FontWeight.SemiBold),
    Font(R.font.plex_mono_bold, FontWeight.Bold),
)

/**
 * The scale, and the roles that are allowed to use it.
 *
 * Eight sizes - 11, 12, 13, 14, 17, 22, 30, 44 - and the jump from a label to the figure it labels
 * is four times, not a third. That contrast is the thing the last redesign had none of, and it is
 * why nothing here interpolates: a size that is not on this list is not available.
 *
 * Four weights ship. 400 sets sentences, 500 sets names, 600 sets every figure and every label, and
 * 700 is reserved for a rank number. Arabic never goes below 13 and never above 500 - its
 * diacritics fill in and its counters close before Latin's do.
 *
 * Line heights are deliberately taller than a Latin-only scale would need, for the same reason:
 * Arabic carries marks above and below the line that a 1.4 multiple clips.
 */
internal object NextType {

    /** The one figure a screen leads with. One per screen, and never more. */
    val headlineFigure = TextStyle(
        fontFamily = Figures,
        fontWeight = FontWeight.SemiBold,
        fontSize = 44.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.03).em,
        fontFeatureSettings = "tnum",
    )

    /** A stat's value, where several sit side by side. */
    val statFigure = TextStyle(
        fontFamily = Figures,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.02).em,
        fontFeatureSettings = "tnum",
    )

    /** The name of the screen, and nothing else at this size. */
    val screenTitle = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.014).em,
    )

    /** A ticker - the row's own name, set as a figure because that is what it behaves like. */
    val ticker = TextStyle(
        fontFamily = Figures,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.01.em,
        fontFeatureSettings = "tnum",
    )

    /** A figure in a row or a table cell. */
    val figure = TextStyle(
        fontFamily = Figures,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontFeatureSettings = "tnum",
    )

    /** Every sentence the app speaks. */
    val body = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    )

    /** A name: a channel, a stock, a person. The one column allowed to be elastic. */
    val name = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 19.sp,
    )

    /** Context beside a figure: a date, a count, a session number. */
    val meta = TextStyle(
        fontFamily = Figures,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        fontFeatureSettings = "tnum",
    )

    /** A column label, tracked wide and set in capitals by whoever draws it. */
    val columnLabel = TextStyle(
        fontFamily = Figures,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.16.em,
    )

    /** The text inside a chip or a button. Capitals, like a label. */
    val chip = TextStyle(
        fontFamily = Figures,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.13.em,
    )

    /**
     * The two-letter mark in the navigation, and the caption under it.
     *
     * Below the content scale on purpose, and the only two roles that are. The shell is read by
     * shape and position rather than by reading - a mark set at 14 with an 11 caption under it
     * becomes a row of words competing with the screen it frames.
     */
    val navMark = TextStyle(
        fontFamily = Figures,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.5.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.1.em,
    )

    val navLabel = TextStyle(
        fontFamily = Figures,
        fontWeight = FontWeight.SemiBold,
        fontSize = 9.sp,
        lineHeight = 11.sp,
        letterSpacing = 0.11.em,
    )
}

/**
 * Text, with the colour named at the call site.
 *
 * Deliberately not Material's `Text`: that one resolves its colour and style from a theme this app
 * does not have, so a call that forgot to say either would still draw something plausible. Here it
 * would not compile. Everything on screen states the role it is in.
 */
@Composable
internal fun NextText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    textAlign: TextAlign? = null,
) {
    val resolved = if (textAlign == null) {
        style.copy(color = color)
    } else {
        style.copy(color = color, textAlign = textAlign)
    }
    BasicText(
        text = text,
        modifier = modifier,
        style = resolved,
        maxLines = maxLines,
        overflow = overflow,
    )
}
