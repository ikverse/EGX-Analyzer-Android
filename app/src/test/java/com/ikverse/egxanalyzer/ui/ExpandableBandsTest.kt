package com.ikverse.egxanalyzer.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Where the other cards go when one of them opens.
 *
 * An open card needs the full width for its table, so it takes a row of its own. Every other card
 * stays where it was, including the one that was beside it - which is then left alone on a
 * half-empty row above. That gap is the price of the order holding still, and it is worth paying:
 * these are runs sorted by date, so a list that reorders itself while being read is how the newest
 * report gets mistaken for the second newest.
 */
class ExpandableBandsTest {

    private fun bands(items: List<String>, open: String?) =
        expandableBands(items) { it == open }
            .map { (band, isOpen) -> band.joinToString("") to isOpen }

    @Test
    fun `nothing open is one band of everything`() {
        assertEquals(listOf("ABCD" to false), bands(listOf("A", "B", "C", "D"), null))
    }

    @Test
    fun `opening the first card leaves the rest in order below it`() {
        assertEquals(
            listOf("A" to true, "BCD" to false),
            bands(listOf("A", "B", "C", "D"), "A"),
        )
    }

    /** The case the old push-down got wrong: B used to come out ahead of A. */
    @Test
    fun `opening the second card of a row leaves its neighbour above it`() {
        assertEquals(
            listOf("A" to false, "B" to true, "CD" to false),
            bands(listOf("A", "B", "C", "D"), "B"),
        )
    }

    @Test
    fun `a card in the middle keeps everything before it above`() {
        assertEquals(
            listOf("ABC" to false, "D" to true, "EF" to false),
            bands(listOf("A", "B", "C", "D", "E", "F"), "D"),
        )
    }

    @Test
    fun `the last card opens with the whole list still above it`() {
        assertEquals(
            listOf("DE" to false, "F" to true),
            bands(listOf("D", "E", "F"), "F"),
        )
    }

    /** The whole point, stated once over every card rather than argued case by case. */
    @Test
    fun `the reading order is the same whichever card is open`() {
        val items = listOf("A", "B", "C", "D", "E", "F")
        (items + null).forEach { open ->
            val flattened = expandableBands(items) { it == open }.flatMap { (band, _) -> band }
            assertEquals("open=$open", items, flattened)
        }
    }
}
