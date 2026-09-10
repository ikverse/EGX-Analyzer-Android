package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.ExtractionPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * That an image keeps its own number whatever the run decides not to send.
 *
 * The failure this is written against is silent: `IMAGE_REF n` resolves to entry n - 1 of
 * `imagePaths`, so numbering over the images actually sent rather than over all of them would hand
 * every card after a skipped one somebody else's picture - in a report that reads perfectly well.
 */
class ExtractionPlanTest {

    /** A run described the way TelegramRepository emits it: each photo followed by its caption. */
    private class Run {
        val sourceIds = mutableListOf<String>()
        val images = mutableListOf<Boolean>()

        fun captionedPhoto(id: String) = apply {
            sourceIds += id; images += true
            sourceIds += id; images += false
        }

        fun photos(id: String, count: Int) = apply {
            repeat(count) { sourceIds += id; images += true }
        }

        fun text(id: String) = apply {
            sourceIds += id; images += false
        }

        fun plan() = ExtractionPlan.of(sourceIds, images)
    }

    @Test
    fun `references run over every image the run carries, in order`() {
        val plan = Run().captionedPhoto("a").captionedPhoto("b").captionedPhoto("c").plan()

        assertEquals(mapOf(0 to 1, 2 to 2, 4 to 3), plan.referenceOf)
        assertEquals(mapOf(1 to "a", 2 to "b", 3 to "c"), plan.sourceOf)
        assertEquals(mapOf("a" to listOf(1), "b" to listOf(2), "c" to listOf(3)), plan.referencesBySource)
    }

    @Test
    fun `a reused source keeps its number and is simply not sent`() {
        // The one that matters: b is answered out of an earlier reading, and c stays image 3.
        val plan = Run().captionedPhoto("a").captionedPhoto("b").captionedPhoto("c").plan()

        val sent = plan.chunks(reused = setOf("b"))

        assertEquals(listOf(1, 3), sent.flatMap(ExtractionPlan.Chunk::references))
        assertEquals(3, plan.referenceOf.getValue(4))
        // And nothing of b travels - not its photo and not the caption that belongs to it.
        assertTrue(sent.flatMap(ExtractionPlan.Chunk::positions).none { it == 2 || it == 3 })
    }

    @Test
    fun `a run whose every source was read before sends nothing at all`() {
        val plan = Run().captionedPhoto("a").captionedPhoto("b").plan()

        assertTrue(plan.chunks(reused = setOf("a", "b")).isEmpty())
    }

    @Test
    fun `a message holding several images keeps them together and in order`() {
        val plan = Run().photos("a", 3).text("a").captionedPhoto("b").plan()

        assertEquals(listOf(1, 2, 3), plan.referencesBySource.getValue("a"))
        assertEquals(listOf(4), plan.referencesBySource.getValue("b"))
        assertEquals("a", plan.sourceOf.getValue(2))
    }

    @Test
    fun `chunking is over what is sent, so a skipped source makes room rather than a hole`() {
        // Nine captioned photos at eight per chunk is two requests; skip one and it is a single one.
        val run = Run()
        repeat(9) { run.captionedPhoto("s$it") }
        val plan = run.plan()

        assertEquals(2, plan.chunks().size)
        assertEquals(1, plan.chunks(reused = setOf("s4")).size)
        // The eight that did travel still carry the numbers the run gave them, 5 among them absent.
        assertEquals(
            listOf(1, 2, 3, 4, 6, 7, 8, 9),
            plan.chunks(reused = setOf("s4")).flatMap(ExtractionPlan.Chunk::references),
        )
    }

    @Test
    fun `every image the run carries is sent exactly once when nothing is reused`() {
        val run = Run()
        repeat(20) { run.captionedPhoto("s$it") }
        val plan = run.plan()

        val sent = plan.chunks().flatMap(ExtractionPlan.Chunk::references)
        assertEquals((1..20).toList(), sent.sorted())
        assertEquals(sent.size, sent.distinct().size)
    }

    @Test
    fun `a text-only run is one request and has no references at all`() {
        val plan = Run().text("a").text("b").plan()

        assertTrue(plan.referenceOf.isEmpty())
        assertEquals(1, plan.chunks().size)
        assertTrue(plan.chunks().single().references.isEmpty())
    }

    @Test
    fun `a chunk's positions are positions in the run's own inputs`() {
        val plan = Run().text("a").captionedPhoto("b").plan()

        val single = plan.chunks().single()
        assertEquals(listOf(0, 1, 2), single.positions)
        assertEquals(listOf(1), single.references)
    }
}
