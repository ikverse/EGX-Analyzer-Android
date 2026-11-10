package com.ikverse.egxanalyzer.data

import androidx.compose.ui.graphics.toArgb
import com.ikverse.egxanalyzer.R
import com.ikverse.egxanalyzer.ui.theme.LightPalette
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The 5.0 design on the surfaces Android and the launcher draw: notifications, the widget, the
 * shortcuts and the launcher icon. What they look like cannot be rendered in a unit test, so this
 * holds what the app hands them - the colours, the stock picture, the layout choice, the resources.
 */
class OutsideSurfacesTest {

    @Test
    fun `each kind of notification wears its tile from the palette`() {
        assertEquals(LightPalette.hero.toArgb(), NotificationTone.TRADES.argb)
        assertEquals(LightPalette.alert.toArgb(), NotificationTone.APPROACH.argb)
        assertEquals(LightPalette.highlight.toArgb(), NotificationTone.BUY_ZONE.argb)
        assertEquals(LightPalette.tile2.toArgb(), NotificationTone.DIGEST.argb)
        assertEquals(LightPalette.tile1.toArgb(), NotificationTone.ATTENTION.argb)
    }

    @Test
    fun `a stock with a bundled logo is pictured by it, and one without by its monogram`() {
        assertEquals(StockBadge.Logo(R.drawable.logo_comi), StockBadge.forTicker("COMI.CA"))
        val monogram = StockBadge.forTicker(" zzqx ") as StockBadge.Monogram
        assertEquals("ZZ", monogram.letters)
        assertTrue(monogram.fill in StockBadge.MonogramFills)
        // The same five tiles, in the same order, as the app's own monogram picks from.
        assertEquals(
            listOf(LightPalette.highlight, LightPalette.tile1, LightPalette.tile2, LightPalette.tile3, LightPalette.alert)
                .map { it.toArgb() },
            StockBadge.MonogramFills,
        )
    }

    @Test
    fun `the widget grows its row of counts only when it is tall enough to hold them`() {
        assertEquals(WidgetLayout.SMALL, widgetLayoutFor(40f))
        assertEquals(WidgetLayout.SMALL, widgetLayoutFor(99f))
        assertEquals(WidgetLayout.TALL, widgetLayoutFor(TallFromHeight))
        assertEquals(WidgetLayout.TALL, widgetLayoutFor(180f))
    }

    @Test
    fun `the small widget drops its date line only at the one-cell height`() {
        assertEquals(false, showsDate(40f))
        assertEquals(true, showsDate(DateFromHeight))
        assertEquals(WidgetLayout.SMALL, widgetLayoutFor(DateFromHeight))
    }

    @Test
    fun `the strip, the chart, the breadth and the EGX30 are rebuilt with every portfolio`() {
        // Held off the source: LiveAppState needs a phone to run. Launch and a price refresh call
        // recomputePortfolio directly, and the four surfaces stayed empty while only the debounced
        // rebuild redrew them.
        val source = File("src/main/java/com/ikverse/egxanalyzer/state/LiveAppState.kt").readText()
        val start = source.indexOf("private suspend fun recomputePortfolio(")
        // Up to the next declaration's doc comment, which is where the function ends.
        val body = source.substring(start, source.indexOf("/**", start))
        assertTrue("recomputePortfolio must rebuild the strip", "recomputeTape()" in body)
        assertTrue("recomputePortfolio must rebuild the chart and breadth", "recomputePulse()" in body)
        assertTrue("a launch with no refresh must still fetch the EGX30", "if (indexSessions.isEmpty()) refreshIndexLine()" in source)
    }

    private val res = File("src/main/res")

    @Test
    fun `the launcher icon is the vector mark with a monochrome layer and no old artwork`() {
        val icon = File(res, "mipmap-anydpi-v26/ic_launcher.xml").readText()
        assertTrue("the icon should draw the vector foreground", "@drawable/ic_launcher_foreground" in icon)
        assertTrue("the icon needs a monochrome layer for themed icons", "<monochrome" in icon)
        val leftovers = res.listFiles().orEmpty()
            .filter { it.name.startsWith("mipmap") }
            .flatMap { it.listFiles().orEmpty().toList() }
            .filter { it.extension == "png" || it.extension == "webp" }
        assertEquals("old launcher artwork is still bundled", emptyList<File>(), leftovers)
    }

    @Test
    fun `every shortcut names its own icon`() {
        val shortcuts = File(res, "xml/shortcuts.xml").readText()
        assertTrue("@drawable/ic_shortcut_portfolio" in shortcuts)
        assertTrue("@drawable/ic_shortcut_insights" in shortcuts)
        assertTrue("a shortcut still borrows the notification glyph", "ic_egx_notification" !in shortcuts)
        val dynamic = File("src/main/java/com/ikverse/egxanalyzer/data/AppShortcuts.kt").readText()
        assertTrue("R.drawable.ic_shortcut_overdue" in dynamic)
    }
}
