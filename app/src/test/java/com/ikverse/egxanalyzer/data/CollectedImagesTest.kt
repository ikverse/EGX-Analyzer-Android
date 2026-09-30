package com.ikverse.egxanalyzer.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A photo reposted into a channel arrives as a second message but downloads to one file.
 *
 * Sending it twice bills a second image and has the model read every card on it twice: the run
 * saved on 2 August carried the same file as image 14 and image 15, and its report listed ODIN
 * four times and PHAR, HDBK and EMFD twice each.
 */
class CollectedImagesTest {

    @Test
    fun `a file is accepted once`() {
        val collected = CollectedImages()

        assertTrue(collected.accept("/photos/6012722037517915335_121.jpg"))
        assertFalse(collected.accept("/photos/6012722037517915335_121.jpg"))
    }

    @Test
    fun `different files are all accepted`() {
        val collected = CollectedImages()

        assertTrue(collected.accept("/photos/a.jpg"))
        assertTrue(collected.accept("/photos/b.jpg"))
        assertTrue(collected.accept("/photos/c.jpg"))
    }

    @Test
    fun `a repost between other photos is still caught`() {
        val collected = CollectedImages()
        val reposted = "/photos/6012722037517915335_121.jpg"

        assertTrue(collected.accept(reposted))
        assertTrue(collected.accept("/photos/other.jpg"))
        assertFalse(collected.accept(reposted))
    }

    @Test
    fun `each run starts over`() {
        val path = "/photos/6012722037517915335_121.jpg"
        CollectedImages().accept(path)

        assertTrue(CollectedImages().accept(path))
    }
}
