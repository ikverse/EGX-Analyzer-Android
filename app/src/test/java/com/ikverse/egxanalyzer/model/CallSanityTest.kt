package com.ikverse.egxanalyzer.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Whether a call's levels can be believed, checked against the session it was made for.
 *
 * The cost of the two mistakes is not symmetric. A missed misread leaves the app exactly where it
 * has always been; a false one puts a caveat on an honest call, on a screen whose whole purpose is
 * to be trusted about which source is worth reading. So most of what follows is about the calls
 * that must come back **clean**.
 */
class CallSanityTest {

    private val day = LocalDate.of(2026, 8, 10)

    /** The stock traded between 9.5 and 11.0 that session. */
    private fun session(high: Double = 11.0, low: Double = 9.5, close: Double? = 10.5) =
        DailySession("AMOC", day, high = high, low = low, close = close, volume = 1000.0)

    private fun faults(
        entryLow: Double? = 10.0,
        entryHigh: Double? = 10.2,
        target1: Double? = 11.5,
        target2: Double? = 12.5,
        stopLoss: Double? = 9.4,
        session: DailySession? = session(),
    ) = CallSanity.faults(entryLow, entryHigh, target1, target2, stopLoss, session)

    @Test
    fun `an ordinary call is clean`() {
        assertTrue(faults().isEmpty())
    }

    @Test
    fun `a patient call naming a dip well under the price is clean`() {
        // The single most likely false positive. A source telling a reader to wait for a pullback
        // is printing exactly this, and flagging it would caption honest calls on every channel
        // that trades patiently.
        assertTrue(faults(entryLow = 8.0, entryHigh = 8.2, target1 = 9.5, stopLoss = 7.5).isEmpty())
    }

    @Test
    fun `a call with no levels at all raises nothing`() {
        // A card that named a stock and no numbers is not a misread of anything.
        assertTrue(
            faults(
                entryLow = null,
                entryHigh = null,
                target1 = null,
                target2 = null,
                stopLoss = null,
            ).isEmpty(),
        )
    }

    @Test
    fun `an unpriced stock collects no fault for being unpriced`() {
        // The structural checks need no price and still run; the distance check is skipped. The
        // other way round, every call on a stock the feed has never carried would be captioned as
        // misread - which is the app blaming the extraction for its own missing data.
        assertTrue(faults(session = null).isEmpty())
        assertEquals(setOf(CallFault.STOP_ABOVE_ENTRY), faults(stopLoss = 10.5, session = null))
    }

    @Test
    fun `a stop at or above the buy zone is a misread`() {
        // A stop inside the entry would be broken the moment the call was taken. Usually the stop
        // belonging to the card above or below this one on the same screenshot.
        assertEquals(setOf(CallFault.STOP_ABOVE_ENTRY), faults(stopLoss = 10.4))
        // At the midpoint exactly, which is no more takeable than above it.
        assertEquals(setOf(CallFault.STOP_ABOVE_ENTRY), faults(stopLoss = 10.1))
    }

    @Test
    fun `a first target at or below the buy zone is a misread`() {
        assertEquals(setOf(CallFault.TARGET_BELOW_ENTRY), faults(target1 = 9.8, target2 = 12.5))
    }

    @Test
    fun `targets read in the wrong order are a misread`() {
        assertEquals(setOf(CallFault.TARGETS_OUT_OF_ORDER), faults(target1 = 12.5, target2 = 11.5))
        // Equal targets are a card that printed one level twice, not a pair read backwards.
        assertTrue(faults(target1 = 12.5, target2 = 12.5).isEmpty())
    }

    @Test
    fun `an inverted entry band is a misread`() {
        assertTrue(CallFault.ENTRY_BAND_INVERTED in faults(entryLow = 10.2, entryHigh = 10.0))
    }

    @Test
    fun `a decimal point in the wrong place is caught`() {
        // 10.1 read as 101. Everything about the call is internally consistent, which is exactly
        // why nothing else on the page would ever notice.
        assertTrue(
            CallFault.LEVELS_OFF_THE_CHART in
                faults(entryLow = 101.0, entryHigh = 102.0, target1 = 115.0, stopLoss = 94.0),
        )
        // And the other direction: 10.1 read as 1.01.
        assertTrue(
            CallFault.LEVELS_OFF_THE_CHART in
                faults(entryLow = 1.01, entryHigh = 1.02, target1 = 1.15, stopLoss = 0.94),
        )
    }

    @Test
    fun `a band just outside the day's range is not a misread`() {
        // The threshold is deliberately loose, because being wrong here captions an honest call.
        // Just under twice the high, and just over half the low, both stay clean.
        assertTrue(
            faults(
                entryLow = 21.0,
                entryHigh = 21.4,
                target1 = 24.0,
                target2 = 26.0,
                stopLoss = 20.0,
            ).isEmpty(),
        )
        assertTrue(
            faults(
                entryLow = 4.9,
                entryHigh = 5.0,
                target1 = 5.6,
                target2 = 6.0,
                stopLoss = 4.6,
            ).isEmpty(),
        )
    }

    @Test
    fun `a session that never really traded raises no distance fault`() {
        // A session in progress can arrive with zeros. Reading those as the day's range would put
        // every call on that stock a long way from a price of nothing.
        val notTraded = DailySession("AMOC", day, high = 0.0, low = 0.0, close = 0.0, volume = null)

        assertTrue(faults(session = notTraded).isEmpty())
    }

    @Test
    fun `one call can carry more than one fault`() {
        val found = faults(entryLow = 10.2, entryHigh = 10.0, stopLoss = 10.5, target1 = 9.0, target2 = 8.0)

        assertEquals(
            setOf(
                CallFault.ENTRY_BAND_INVERTED,
                CallFault.STOP_ABOVE_ENTRY,
                CallFault.TARGET_BELOW_ENTRY,
                CallFault.TARGETS_OUT_OF_ORDER,
            ),
            found,
        )
    }
}
