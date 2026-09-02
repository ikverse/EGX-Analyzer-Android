package com.ikverse.egxanalyzer.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one predicate three screens and the shell all read.
 *
 * It is worth pinning because it is the thing that decides both what a Filters chip says and what a
 * back press does, and a filter added to a screen without being added here goes on hiding rows that
 * neither the chip nor back will admit to.
 */
class PageFilterTest {

    @Test
    fun `nothing is filtered on a fresh state`() {
        val pages = PageState()
        AppDestination.entries.forEach { destination ->
            assertFalse(destination.name, pages.filtersActive(destination))
        }
    }

    @Test
    fun `a search box narrows its own tab and no other`() {
        val pages = PageState()
        pages.insightsStock.value = "COMI"
        assertTrue(pages.filtersActive(AppDestination.INSIGHTS))
        assertFalse(pages.filtersActive(AppDestination.RESULTS))
        assertFalse(pages.filtersActive(AppDestination.PORTFOLIO))
    }

    @Test
    fun `every narrowing control on a tab is seen`() {
        val pages = PageState()
        pages.resultsChannels.value = setOf("a channel")
        assertTrue(pages.filtersActive(AppDestination.RESULTS))
        pages.clearFilters(AppDestination.RESULTS)
        pages.resultsDate.value = "2026-08-14"
        assertTrue(pages.filtersActive(AppDestination.RESULTS))
        pages.clearFilters(AppDestination.RESULTS)
        pages.insightsOutcomes.value = setOf("Stopped out")
        assertTrue(pages.filtersActive(AppDestination.INSIGHTS))
        pages.clearFilters(AppDestination.INSIGHTS)
        pages.portfolioDate.value = "2026-08-14"
        assertTrue(pages.filtersActive(AppDestination.PORTFOLIO))
    }

    /**
     * A sort hides nothing, so it is not a filter and back has no business resetting it. This is
     * the rule the screens' own chips already followed.
     */
    @Test
    fun `an order is not a filter`() {
        val pages = PageState()
        pages.resultsOrder.value = RunOrder.entries.last()
        assertFalse(pages.filtersActive(AppDestination.RESULTS))
    }

    @Test
    fun `clearing a tab leaves the others alone`() {
        val pages = PageState()
        pages.insightsStock.value = "COMI"
        pages.portfolioStock.value = "AMOC"
        pages.clearFilters(AppDestination.INSIGHTS)
        assertFalse(pages.filtersActive(AppDestination.INSIGHTS))
        assertTrue(pages.filtersActive(AppDestination.PORTFOLIO))
    }
}
