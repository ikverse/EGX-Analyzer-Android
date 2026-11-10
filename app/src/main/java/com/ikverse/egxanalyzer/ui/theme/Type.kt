package com.ikverse.egxanalyzer.ui.theme

import android.content.res.Resources
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ikverse.egxanalyzer.R

/**
 * The 5.0 type: three faces, each doing one job.
 *
 * - **Bricolage Grotesque** sets every name and sentence - page names, tickers, headings, labels,
 *   body text. It is a variable font with an optical-size axis, so one 400 KB file is instanced
 *   here at two optical sizes: [DisplayOpticalSize] for anything 19sp and up, [TextOpticalSize] for
 *   text. The large instances are drawn tighter and with more contrast, the small ones opener.
 * - **JetBrains Mono** sets every figure: prices, percentages, amounts, counts, dates. Every digit
 *   is one width, so a column of prices lines up without asking for tabular figures at all.
 * - **IBM Plex Sans Arabic** carries every Arabic letter. Neither of the two above has Arabic, and a
 *   row here routinely mixes the two scripts (`COMI · البنك التجاري الدولي`). [rememberAppTypography]
 *   builds Bricolage with Plex Arabic as its *fallback*, so the Latin and the Arabic in one line
 *   each come from their own face instead of the Arabic falling through to the phone's system font.
 *
 * All three are bundled (no network, the app draws its record offline) and all three are SIL Open
 * Font License; see THIRD-PARTY-NOTICES.md. Inter Tight, the 4.5 face, was removed in 5.0.0.
 */

/** The optical size the display instances are drawn at. The axis runs 12 to 96. */
private const val DisplayOpticalSize = 48f

/** The optical size the text instances are drawn at. */
private const val TextOpticalSize = 14f

private val DisplayWeights = listOf(FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold)
private val TextWeights = listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold)

@OptIn(ExperimentalTextApi::class)
private fun bricolage(weights: List<FontWeight>, opticalSize: Float) = FontFamily(
    weights.map { weight ->
        Font(
            R.font.bricolage_grotesque,
            weight,
            variationSettings = FontVariation.Settings(
                FontVariation.weight(weight.weight),
                FontVariation.Setting("opsz", opticalSize),
            ),
        )
    },
)

/**
 * Names, headings, tickers. Latin only as a Compose family; [rememberAppTypography] gives it Plex
 * Arabic as a fallback where a context is available.
 */
val Display = bricolage(DisplayWeights, DisplayOpticalSize)

/** Sentences and labels, at the text optical size. Same fallback arrangement as [Display]. */
val Body = bricolage(TextWeights, TextOpticalSize)

/**
 * Every figure. JetBrains Mono, instanced per weight from its variable file.
 *
 * Read through `TabularFigures` in `DesignSystem.kt` by every call site that sets a number, so this
 * one line is the whole of what sets a figure.
 */
@OptIn(ExperimentalTextApi::class)
val Figures = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold).map { weight ->
        Font(
            R.font.jetbrains_mono,
            weight,
            variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
        )
    },
)

/** Harmless on JetBrains Mono, whose digits are fixed already, and right on Bricolage. */
private const val Tabular = "tnum"

/**
 * The scale, as drawn on the canvas.
 *
 * Page names are 42sp and heavy, lead figures 52/44/34sp, body 15sp. Line heights in the body roles
 * stay taller than a Latin-only scale needs, because the Arabic fallback carries marks above and
 * below the line that a tight multiple clips. Letter spacing tightens as size rises: a large
 * heading at default spacing falls apart into letters.
 */
internal fun appTypography(display: FontFamily, body: FontFamily) = Typography(
    // The lead figures. Call sites add `TabularFigures` (JetBrains Mono); the role decides the size.
    displayLarge = TextStyle(
        fontFamily = display, fontWeight = FontWeight.Bold, fontSize = 52.sp, lineHeight = 56.sp,
        letterSpacing = (-2.0).sp, fontFeatureSettings = Tabular,
    ),
    displayMedium = TextStyle(
        fontFamily = display, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 48.sp,
        letterSpacing = (-1.6).sp, fontFeatureSettings = Tabular,
    ),
    displaySmall = TextStyle(
        fontFamily = display, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 38.sp,
        letterSpacing = (-1.0).sp, fontFeatureSettings = Tabular,
    ),
    // The page name, and nothing else at this size.
    headlineLarge = TextStyle(
        fontFamily = display, fontWeight = FontWeight.ExtraBold, fontSize = 42.sp, lineHeight = 46.sp,
        letterSpacing = (-1.7).sp, fontFeatureSettings = Tabular,
    ),
    headlineMedium = TextStyle(
        fontFamily = display, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, lineHeight = 32.sp,
        letterSpacing = (-0.8).sp, fontFeatureSettings = Tabular,
    ),
    // A ticker, and a tile's heading.
    headlineSmall = TextStyle(
        fontFamily = display, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, lineHeight = 27.sp,
        letterSpacing = (-0.4).sp, fontFeatureSettings = Tabular,
    ),
    titleLarge = TextStyle(
        fontFamily = display, fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = 24.sp,
        letterSpacing = (-0.2).sp, fontFeatureSettings = Tabular,
    ),
    // From here down it is the text instance: labels on things, not names of things.
    titleMedium = TextStyle(
        fontFamily = body, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp,
        fontFeatureSettings = Tabular,
    ),
    titleSmall = TextStyle(
        fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp,
        fontFeatureSettings = Tabular,
    ),
    bodyLarge = TextStyle(
        fontFamily = body, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp,
        fontFeatureSettings = Tabular,
    ),
    bodyMedium = TextStyle(
        fontFamily = body, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp,
        fontFeatureSettings = Tabular,
    ),
    bodySmall = TextStyle(
        fontFamily = body, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp,
        fontFeatureSettings = Tabular,
    ),
    labelLarge = TextStyle(
        fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp,
        fontFeatureSettings = Tabular,
    ),
    labelMedium = TextStyle(
        fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp,
        fontFeatureSettings = Tabular,
    ),
    // The key over a figure, tracked out so a small label reads as a caption.
    labelSmall = TextStyle(
        fontFamily = body, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 14.sp,
        letterSpacing = (0.8).sp, fontFeatureSettings = Tabular,
    ),
)

/**
 * The scale with the Latin-only families: what a test or a preview gets, and the fallback if the
 * platform refuses to build the Arabic-aware ones.
 */
internal val AppTypography = appTypography(Display, Body)

/**
 * The scale with Plex Sans Arabic behind Bricolage, built once per resources.
 *
 * Compose has no per-script fallback between two of its own font families, so this goes to the
 * platform: `Typeface.CustomFallbackBuilder` (API 29, under this app's minSdk 31) takes a family for
 * the Latin and a custom fallback for the scripts it lacks. Each weight Bricolage is instanced at
 * goes into one platform family, so asking for Bold picks the Bold instance rather than smearing
 * the Regular one.
 *
 * Anything going wrong here - a Robolectric sandbox without the native font stack, a resource the
 * platform will not read - lands on [AppTypography], which still draws every Latin letter correctly
 * and lets Arabic fall through to the system font as it did before Plex was bundled.
 */
@Composable
internal fun rememberAppTypography(): Typography {
    val resources = LocalContext.current.resources
    return remember(resources) {
        runCatching {
            appTypography(
                display = arabicAwareFamily(resources, DisplayWeights, DisplayOpticalSize),
                body = arabicAwareFamily(resources, TextWeights, TextOpticalSize),
            )
        }.getOrDefault(AppTypography)
    }
}

private fun arabicAwareFamily(resources: Resources, weights: List<FontWeight>, opticalSize: Float): FontFamily {
    val latin = weights
        .map { weight ->
            android.graphics.fonts.Font.Builder(resources, R.font.bricolage_grotesque)
                .setWeight(weight.weight)
                .setFontVariationSettings("'wght' ${weight.weight}, 'opsz' $opticalSize")
                .build()
        }
        .let { fonts ->
            fonts.drop(1).fold(android.graphics.fonts.FontFamily.Builder(fonts.first())) { builder, font ->
                builder.addFont(font)
            }.build()
        }
    val arabic = listOf(
        R.font.plex_arabic_regular to 400,
        R.font.plex_arabic_medium to 500,
        R.font.plex_arabic_semibold to 600,
    )
        .map { (res, weight) -> android.graphics.fonts.Font.Builder(resources, res).setWeight(weight).build() }
        .let { fonts ->
            fonts.drop(1).fold(android.graphics.fonts.FontFamily.Builder(fonts.first())) { builder, font ->
                builder.addFont(font)
            }.build()
        }
    val typeface = android.graphics.Typeface.CustomFallbackBuilder(latin)
        .addCustomFallback(arabic)
        .setSystemFallback("sans-serif")
        .build()
    return FontFamily(androidx.compose.ui.text.font.Typeface(typeface))
}
