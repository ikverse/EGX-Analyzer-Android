package com.ikverse.egxanalyzer.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Where a card goes when the one beside it opens.
 *
 * An open card needs the full width for its table, so it takes a row of its own. The question is
 * what happens to the card that was next to it: leaving it above with an empty half made a card
 * opened on the right look like it had jumped to a new line, while one opened on the left grew in
 * place. It now moves below, so both behave the same way.
 */
class ExpandableBandsTest {

    private fun bands(items: List<String>, columns: Int, open: String?) =
        expandableBands(items, columns) { it == open }
            .map { (band, isOpen) -> band.joinToString("") to isOpen }

    @Test
    fun `nothing open is one band of everything`() {
        assertEquals(listOf("ABCD" to false), bands(listOf("A", "B", "C", "D"), 2, null))
    }

    @Test
    fun `opening the first card of a row leaves the rest in order`() {
        assertEquals(
            listOf("A" to true, "BCD" to false),
            bands(listOf("A", "B", "C", "D"), 2, "A"),
        )
    }

    @Test
    fun `opening the second card of a row pushes its neighbour below it`() {
        assertEquals(
            listOf("B" to true, "ACD" to false),
            bands(listOf("A", "B", "C", "D"), 2, "B"),
        )
    }

    @Test
    fun `a card that starts its own row keeps everything before it above`() {
        assertEquals(
            listOf("AB" to false, "C" to true, "D" to false),
            bands(listOf("A", "B", "C", "D"), 2, "C"),
        )
    }

    @Test
    fun `only the cards actually beside it are pushed down`() {
        // Three columns: D opens with A B C already placed as a full row above it.
        assertEquals(
            listOf("ABC" to false, "D" to true, "EF" to false),
            bands(listOf("A", "B", "C", "D", "E", "F"), 3, "D"),
        )
    }

    @Test
    fun `the whole partial row moves below a card opened at its end`() {
        assertEquals(
            listOf("F" to true, "DE" to false),
            bands(listOf("D", "E", "F"), 3, "F"),
        )
    }

    @Test
    fun `a single column keeps the order it was given`() {
        assertEquals(
            listOf("A" to false, "B" to true, "CD" to false),
            bands(listOf("A", "B", "C", "D"), 1, "B"),
        )
    }
}
