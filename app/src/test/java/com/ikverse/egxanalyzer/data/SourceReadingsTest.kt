package com.ikverse.egxanalyzer.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * That a card read once is read back as the same card, and never as a different one.
 *
 * The saving is easy and the failure is quiet: a reading laid onto the wrong image would file one
 * channel's levels under another channel's card, in a report that looks entirely ordinary. So most
 * of this is about the readings that must be **refused** - the run has to fall back to sending the
 * message, which costs a request and nothing else.
 */
class SourceReadingsTest {

    private fun row(messageId: String, reference: Any?, ticker: String): JSONObject =
        JSONObject()
            .put("source_message_id", messageId)
            .put("source_image_ref", reference ?: JSONObject.NULL)
            .put("stock_code", ticker)

    private fun rows(vararg entries: Pair<String, List<JSONObject>>): Map<String, JSONArray> =
        SourceReadings.KEYS.associateWith { key ->
            JSONArray().also { array -> entries.toMap()[key].orEmpty().forEach(array::put) }
        }

    @Test
    fun `a reading is written against its own source's images, not the request's`() {
        // The card was image 7 of the run it was read in, and the only image its message carries.
        val stored = SourceReadings.of(
            rows("extracted" to listOf(row("501", 7, "AMOC"))),
            telegramId = "501",
            refs = listOf(7),
        )

        val reading = JSONObject(requireNotNull(stored))
        assertEquals(1, reading.getInt("images"))
        assertEquals(1, reading.getJSONArray("extracted").getJSONObject(0).getInt("source_image_ref"))
    }

    @Test
    fun `laying a reading back puts it on the image the new run gave that card`() {
        val stored = requireNotNull(
            SourceReadings.of(
                rows("extracted" to listOf(row("501", 7, "AMOC"))),
                telegramId = "501",
                refs = listOf(7),
            ),
        )

        // The same message, second in a run of its own this time.
        val laid = requireNotNull(SourceReadings.lay(stored, refs = listOf(2)))

        val extracted = laid.getValue("extracted")
        assertEquals(1, extracted.length())
        assertEquals(2, extracted.getJSONObject(0).getInt("source_image_ref"))
        assertEquals("AMOC", extracted.getJSONObject(0).getString("stock_code"))
    }

    @Test
    fun `only the rows of the message being written down are kept`() {
        val stored = requireNotNull(
            SourceReadings.of(
                rows(
                    "extracted" to listOf(row("501", 1, "AMOC"), row("502", 2, "COMI")),
                    "excluded" to listOf(row("502", 2, "COMI")),
                ),
                telegramId = "501",
                refs = listOf(1),
            ),
        )

        val reading = JSONObject(stored)
        assertEquals(1, reading.getJSONArray("extracted").length())
        assertEquals("AMOC", reading.getJSONArray("extracted").getJSONObject(0).getString("stock_code"))
        assertEquals(0, reading.getJSONArray("excluded").length())
    }

    @Test
    fun `a text message with nothing found is still a reading`() {
        // The commonest case by far - an advertisement, a greeting, a news post - and the one the
        // whole saving rests on: it costs a request to be told nothing twice.
        val stored = requireNotNull(
            SourceReadings.of(rows(), telegramId = "501", refs = emptyList()),
        )

        val laid = requireNotNull(SourceReadings.lay(stored, refs = emptyList()))
        assertTrue(SourceReadings.KEYS.all { laid.getValue(it).length() == 0 })
    }

    @Test
    fun `an excluded card is remembered as excluded`() {
        val stored = requireNotNull(
            SourceReadings.of(
                rows("excluded" to listOf(row("501", 4, "AMOC"))),
                telegramId = "501",
                refs = listOf(4),
            ),
        )

        val laid = requireNotNull(SourceReadings.lay(stored, refs = listOf(9)))
        assertEquals(0, laid.getValue("extracted").length())
        assertEquals(9, laid.getValue("excluded").getJSONObject(0).getInt("source_image_ref"))
    }

    @Test
    fun `a row naming another message's image is not a reading of one message`() {
        // Image 2 belongs to the message beside it in that chunk. Nothing here can say what this
        // message was read to mean, so nothing is written down about it.
        assertNull(
            SourceReadings.of(
                rows("extracted" to listOf(row("501", 2, "AMOC"))),
                telegramId = "501",
                refs = listOf(1),
            ),
        )
    }

    @Test
    fun `a message that has gained an image since is read again`() {
        val stored = requireNotNull(
            SourceReadings.of(
                rows("extracted" to listOf(row("501", 1, "AMOC"))),
                telegramId = "501",
                refs = listOf(1),
            ),
        )

        assertNull(SourceReadings.lay(stored, refs = listOf(3, 4)))
    }

    @Test
    fun `a stored reading naming an image its source does not have is refused`() {
        val forged = JSONObject()
            .put("images", 1)
            .put("extracted", JSONArray().put(row("501", 2, "AMOC")))
            .toString()

        assertNull(SourceReadings.lay(forged, refs = listOf(5)))
    }

    @Test
    fun `text that will not parse is simply not a reading`() {
        assertNull(SourceReadings.lay("not json at all", refs = emptyList()))
    }

    @Test
    fun `a row with no image keeps none when it is laid back`() {
        val stored = requireNotNull(
            SourceReadings.of(
                rows("extracted" to listOf(row("501", null, "AMOC"))),
                telegramId = "501",
                refs = emptyList(),
            ),
        )

        val laid = requireNotNull(SourceReadings.lay(stored, refs = emptyList()))
        assertTrue(laid.getValue("extracted").getJSONObject(0).isNull("source_image_ref"))
    }

    @Test
    fun `an answer citing a message the request never carried is not to be trusted`() {
        val answer = rows("extracted" to listOf(row("501", 1, "AMOC"), row("999", 2, "COMI")))

        assertTrue(SourceReadings.namesOthers(answer, known = setOf("501")))
        assertFalse(SourceReadings.namesOthers(answer, known = setOf("501", "999")))
    }

    @Test
    fun `a row that names no message at all counts as one of somebody else's`() {
        // It cannot be attributed, so it cannot be remembered - and a chunk half remembered is a
        // chunk whose missing row nobody would ever notice.
        val answer = rows("extracted" to listOf(JSONObject().put("stock_code", "AMOC")))

        assertTrue(SourceReadings.namesOthers(answer, known = setOf("501")))
    }
}
