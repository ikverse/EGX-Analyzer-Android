package com.ikverse.egxanalyzer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Which of Telegram's sizes a card is fetched at, which is the biggest single lever on what a run
 * costs: a vision model is billed by pixel area, so taking 2560 where 1280 reads the same is paying
 * four times over for every image in every run.
 */
class PhotoSizeTest {

    @Test
    fun `the smallest size that is still legible is taken`() {
        // Telegram's own ladder for a channel card: s, m, x, y, w.
        assertEquals(3, preferredPhotoSize(SIZES))
    }

    @Test
    fun `a card offered at exactly the floor is taken at the floor`() {
        assertEquals(1, preferredPhotoSize(listOf(320, LEGIBLE_LONG_EDGE, 2560)))
    }

    @Test
    fun `a card that never reaches the floor is taken at its largest`() {
        // A small card arrives small, and nothing here resizes anything.
        assertEquals(2, preferredPhotoSize(listOf(100, 320, 800)))
    }

    @Test
    fun `the ladder is read by size and not by the order it arrives in`() {
        assertEquals(0, preferredPhotoSize(listOf(1280, 2560, 320)))
    }

    @Test
    fun `a photo with no sizes at all has none to prefer`() {
        assertNull(preferredPhotoSize(emptyList()))
    }

    private companion object {
        val SIZES = listOf(100, 320, 800, 1280, 2560)
    }
}
