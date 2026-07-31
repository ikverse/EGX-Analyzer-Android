package com.ikverse.egxanalyzer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisChunkingTest {

    /**
     * A run described the way TelegramRepository emits it: each photo is followed by its caption,
     * both carrying the same source id.
     */
    private class Run {
        val sourceIds = mutableListOf<String>()
        val images = mutableListOf<Boolean>()

        fun captionedPhoto(id: String = "tg:-100:${sourceIds.size}") = apply {
            sourceIds += id; images += true
            sourceIds += id; images += false
        }

        fun text(id: String = "tg:-100:t${sourceIds.size}") = apply {
            sourceIds += id; images += false
        }

        fun album(id: String, photos: Int) = apply {
            repeat(photos) { sourceIds += id; images += true }
            sourceIds += id; images += false
        }

        fun plan(imagesPerChunk: Int = AnalysisChunking.IMAGES_PER_CHUNK) =
            AnalysisChunking.plan(sourceIds, images, imagesPerChunk)

        fun imageCounts(imagesPerChunk: Int = AnalysisChunking.IMAGES_PER_CHUNK) =
            plan(imagesPerChunk).map { range -> range.count { images[it] } }

        fun sourcesPerChunk(imagesPerChunk: Int = AnalysisChunking.IMAGES_PER_CHUNK) =
            plan(imagesPerChunk).map { range -> range.map { sourceIds[it] }.distinct() }
    }

    private fun run(build: Run.() -> Unit) = Run().apply(build)

    @Test
    fun `a run under the limit stays one request`() {
        val subject = run { repeat(5) { captionedPhoto() } }
        assertEquals(1, subject.plan().size)
    }

    @Test
    fun `the run that broke splits into chunks the model can count`() {
        // 32 images in one request is where IMAGE_REF 4 was cited for image 15.
        val subject = run { repeat(32) { captionedPhoto() } }
        assertEquals(listOf(8, 8, 8, 8), subject.imageCounts())
    }

    @Test
    fun `a caption is never separated from its photo`() {
        val subject = run { repeat(20) { captionedPhoto() } }
        // A source appearing in two chunks means a card was judged without its caption.
        val everySource = subject.sourcesPerChunk().flatten()
        assertEquals(everySource.size, everySource.distinct().size)
    }

    @Test
    fun `a message is never split even when it alone exceeds the limit`() {
        val subject = run { album("tg:-100:crowded", photos = 11) }
        assertEquals(1, subject.plan(imagesPerChunk = 8).size)
    }

    @Test
    fun `an oversized message does not drag its neighbours along`() {
        val subject = run {
            captionedPhoto("tg:-100:before")
            album("tg:-100:crowded", photos = 11)
            captionedPhoto("tg:-100:after")
        }
        assertEquals(listOf(1, 11, 1), subject.imageCounts(imagesPerChunk = 8))
    }

    @Test
    fun `text-only sources ride along without forcing a split`() {
        val subject = run {
            text(); text()
            repeat(8) { captionedPhoto() }
        }
        assertEquals(1, subject.plan().size)
        assertEquals(10, subject.sourcesPerChunk().single().size)
    }

    @Test
    fun `nothing is lost or reordered`() {
        val subject = run { repeat(25) { captionedPhoto() } }
        val covered = subject.plan().flatMap { it.toList() }
        assertEquals(subject.sourceIds.indices.toList(), covered)
    }

    @Test
    fun `an empty run makes no requests`() {
        assertTrue(AnalysisChunking.plan(emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun `a text-only run is still one request`() {
        val subject = run { repeat(30) { text() } }
        assertEquals(1, subject.plan().size)
    }
}
