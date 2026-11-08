package com.ikverse.egxanalyzer.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The guard that keeps a sheet open while its content is read. Each case is what a scroller would
 * offer the guard's parent connection, in the order it would offer it; what the guard does not
 * consume is what reaches Material's sheet.
 */
class SheetDragGuardTest {
    private val slop = 100f

    /** What a downward delta the scroller could not use passes on to the sheet. */
    private fun SheetDragGuard.toSheet(
        down: Float,
        consumed: Float = 0f,
        source: NestedScrollSource = NestedScrollSource.UserInput,
    ): Float {
        val taken = onPostScroll(Offset(0f, consumed), Offset(0f, down), source)
        return down - taken.y
    }

    /** The release: what of the leftover speed reaches the sheet. */
    private fun SheetDragGuard.release(speed: Float): Float = runBlocking {
        speed - onPostFling(Velocity.Zero, Velocity(0f, speed)).y
    }

    @Test
    fun `a flick that runs into the top does not close the sheet`() {
        val guard = SheetDragGuard(slop)
        // The finger scrolls the content back towards the top and lets go.
        guard.toSheet(down = 0f, consumed = 40f)
        assertEquals(0f, guard.release(0f), 0f)
        // The fling carries on alone, hits the top, and its leftovers arrive far past the slop.
        val fling = NestedScrollSource.SideEffect
        guard.toSheet(down = 0f, consumed = 300f, source = fling)
        repeat(10) { guard.toSheet(down = 60f, source = fling) }
        // The speed it still had must not reach the sheet.
        assertEquals(0f, guard.release(3000f), 0f)
    }

    @Test
    fun `a drag that scrolled the content never moves the sheet`() {
        val guard = SheetDragGuard(slop)
        guard.toSheet(down = 20f, consumed = 200f)
        // At the top now, and the same finger keeps pulling well past the slop.
        repeat(10) { assertEquals(0f, guard.toSheet(down = 50f), 0f) }
        assertEquals(0f, guard.release(2000f), 0f)
    }

    @Test
    fun `a fresh pull from the top still closes the sheet`() {
        val guard = SheetDragGuard(slop)
        // The slop is held back, then the sheet follows the finger.
        assertEquals(0f, guard.toSheet(down = 60f), 0f)
        assertEquals(20f, guard.toSheet(down = 60f), 0f)
        assertEquals(60f, guard.toSheet(down = 60f), 0f)
        assertEquals(2000f, guard.release(2000f), 0f)
    }

    @Test
    fun `a short pull from the top is held, and the next gesture starts afresh`() {
        val guard = SheetDragGuard(slop)
        assertEquals(0f, guard.toSheet(down = 80f), 0f)
        assertEquals(0f, guard.release(1500f), 0f)
        // A drag after a reading gesture is a new gesture with the whole slop again.
        guard.toSheet(down = 0f, consumed = 50f)
        guard.release(0f)
        assertEquals(0f, guard.toSheet(down = 100f), 0f)
        assertEquals(30f, guard.toSheet(down = 30f), 0f)
    }
}
