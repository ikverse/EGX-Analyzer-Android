package com.ikverse.egxanalyzer.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.ikverse.egxanalyzer.R
import com.ikverse.egxanalyzer.ui.StockLogos

/**
 * The colour each kind of notification carries since 5.0, from the palette's own tiles.
 *
 * Android draws the notification; what the app sets is the colour of the small mark and the app name
 * beside it. One colour for everything said them all in one voice, so a stop being approached read
 * like a run finishing. Each kind takes the tile it wears inside the app: cobalt for your trades and
 * runs, coral for a level being approached, lime for a price reaching a zone, lilac for the
 * session's digest, sunflower for the app reporting on itself.
 *
 * The values are LightPalette's, in `ui/theme/Palette.kt`; `NotificationToneTest` holds the two
 * together.
 */
internal enum class NotificationTone(val argb: Int) {
    /** A trade of yours changed, or is overdue, or a run finished. */
    TRADES(0xFF1E2BFF.toInt()),
    /** A trade of yours is closing on its stop or target 2. */
    APPROACH(0xFFFF6B4A.toInt()),
    /** A price reached a buy zone, or a level you set an alert at. */
    BUY_ZONE(0xFFC8FF4D.toInt()),
    /** What the whole session did. */
    DIGEST(0xFFE2D4FF.toInt()),
    /** The feed went quiet, a schedule did not run, the backup cannot reach its folder. */
    ATTENTION(0xFFFFD12E.toInt()),
}

/**
 * The picture on the right of a notification about one stock: its bundled logo on a white tile, or,
 * for the one company without a logo, the same two-letter monogram the app draws, on the same tile
 * colour.
 */
internal sealed interface StockBadge {
    data class Logo(@DrawableRes val drawable: Int) : StockBadge
    data class Monogram(val letters: String, val fill: Int) : StockBadge

    companion object {
        /**
         * The tile colours a monogram can take, in the order `Monogram` in `ui/StockLogo.kt` lists
         * them, so a stock is the same colour in a notification as on every screen.
         */
        val MonogramFills = listOf(
            0xFFC8FF4D.toInt(), // highlight
            0xFFFFD12E.toInt(), // tile1
            0xFFE2D4FF.toInt(), // tile2
            0xFFBFE3FF.toInt(), // tile3
            0xFFFF6B4A.toInt(), // alert
        )

        fun forTicker(ticker: String): StockBadge {
            StockLogos.forTicker(ticker)?.let { return Logo(it) }
            val cleaned = ticker.trim().uppercase().removeSuffix(".CA")
            val fills = MonogramFills
            return Monogram(cleaned.take(2), fills[((cleaned.hashCode() % fills.size) + fills.size) % fills.size])
        }
    }
}

/** The ink every badge's letters are set in, and the white a logo sits on. */
private const val BadgeInk = 0xFF101828.toInt()
private const val BadgeWhite = 0xFFFFFFFF.toInt()

/** Large enough for a 3x shade, small enough that a burst of notifications costs nothing. */
private const val BadgePixels = 144

/**
 * The badge as the bitmap a notification's large icon has to be. Null where drawing fails - a
 * notification without its picture is still the notification, and one that throws is not sent.
 */
internal fun stockBadgeBitmap(context: Context, ticker: String): Bitmap? = runCatching {
    val badge = StockBadge.forTicker(ticker)
    val bitmap = Bitmap.createBitmap(BadgePixels, BadgePixels, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val tile = RectF(0f, 0f, BadgePixels.toFloat(), BadgePixels.toFloat())
    val corner = BadgePixels * 0.3f
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    when (badge) {
        is StockBadge.Logo -> {
            fill.color = BadgeWhite
            canvas.drawRoundRect(tile, corner, corner, fill)
            val logo = ContextCompat.getDrawable(context, badge.drawable) ?: error("no logo")
            val inset = (BadgePixels * 0.16f).toInt()
            logo.setBounds(inset, inset, BadgePixels - inset, BadgePixels - inset)
            logo.draw(canvas)
        }
        is StockBadge.Monogram -> {
            fill.color = badge.fill
            canvas.drawRoundRect(tile, corner, corner, fill)
            val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = BadgeInk
                textAlign = Paint.Align.CENTER
                textSize = BadgePixels * 0.36f
                typeface = runCatching { ResourcesCompat.getFont(context, R.font.bricolage_grotesque) }.getOrNull()
                    ?.let { Typeface.create(it, Typeface.BOLD) } ?: Typeface.DEFAULT_BOLD
            }
            val baseline = BadgePixels / 2f - (text.descent() + text.ascent()) / 2f
            canvas.drawText(badge.letters, BadgePixels / 2f, baseline, text)
        }
    }
    bitmap
}.getOrNull()
