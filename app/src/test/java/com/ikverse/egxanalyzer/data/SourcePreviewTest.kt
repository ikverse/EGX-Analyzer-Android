package com.ikverse.egxanalyzer.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A preview is stored inside the saved report, so anything it carries has to survive JSON.
 *
 * Two runs saved on 2 August did not: a preview cut in the middle of an emoji ended in a lone
 * high surrogate, that half merged with the quote closing its own string, and the whole payload
 * became unparseable - taking both reports out of the results list without a word.
 */
class SourcePreviewTest {

    private val horse = "🐎"
    private val turtle = "🐢"

    private fun String.hasBrokenPair(): Boolean = indices.any { i ->
        val c = this[i]
        when {
            c.isHighSurrogate() -> i + 1 >= length || !this[i + 1].isLowSurrogate()
            c.isLowSurrogate() -> i == 0 || !this[i - 1].isHighSurrogate()
            else -> false
        }
    }

    @Test
    fun `a short message is kept whole`() {
        val message = "سهم المصرية للاتصالات $horse$turtle"

        assertEquals(message, message.asPreview())
    }

    @Test
    fun `the limit still applies`() {
        assertEquals(PREVIEW_LENGTH, "a".repeat(PREVIEW_LENGTH * 2).asPreview().length)
    }

    @Test
    fun `an emoji straddling the limit is dropped rather than halved`() {
        val message = "a".repeat(PREVIEW_LENGTH - 1) + horse + "tail"

        val preview = message.asPreview()

        assertEquals(PREVIEW_LENGTH - 1, preview.length)
        assertFalse(preview.hasBrokenPair())
    }

    @Test
    fun `an emoji ending exactly at the limit is kept whole`() {
        val message = "a".repeat(PREVIEW_LENGTH - 2) + horse + "tail"

        val preview = message.asPreview()

        assertEquals(PREVIEW_LENGTH, preview.length)
        assertTrue(preview.endsWith(horse))
    }

    @Test
    fun `a half emoji already in the message is stripped`() {
        val preview = ("text " + "\uD83D" + " more").asPreview()

        assertEquals("text  more", preview)
        assertFalse(preview.hasBrokenPair())
    }

    @Test
    fun `the caption that broke two saved runs round-trips through JSON`() {
        // The real message: long enough that the limit lands inside the trailing emoji.
        val caption = "*سهم \"المصرية للاتصالات   ETEL.CA\"من توصياتنا في الجلسة \"  السابقه\" من " +
            "ترشيحات  T+1 حقق المستهدف الثاني \"  107.50 \" بنسبه صعود 3.37% ومازال يتميز " +
            "بالإيجابية $horse$turtle$horse"

        val preview = caption.asPreview()

        assertFalse(preview.hasBrokenPair())
        val written = JSONObject().put("preview", preview).toString()
        assertEquals(preview, JSONObject(written).getString("preview"))
    }
}
