package com.ikverse.egxanalyzer.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** This app's chat-completions bodies as ClaudeIn runs, and ClaudeIn's answers back. */
class ClaudeInRequestsTest {
    private fun image(mime: String = "image/jpeg", data: String = "QUJD") =
        JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:$mime;base64,$data"))

    private fun text(value: String) = JSONObject().put("type", "text").put("text", value)

    /** The shape `extractionBody` builds: a system prompt, then text and images interleaved. */
    private fun extraction(vararg parts: JSONObject) = JSONObject().apply {
        put("model", "claude-sonnet-5-5")
        put("temperature", 0.0)
        put(
            "messages",
            JSONArray()
                .put(JSONObject().put("role", "system").put("content", JSONArray().put(text("THE PROMPT"))))
                .put(JSONObject().put("role", "user").put("content", JSONArray().apply { parts.forEach(::put) })),
        )
    }

    @Test
    fun picturesAreLiftedOutInOrderAndNamedWhereTheyStood() {
        val body = extraction(
            text("RUNTIME CONTEXT"),
            text("IMAGE_REF 1 | TELEGRAM_ID: 10"),
            image(data = "AAAA"),
            text("IMAGE_REF 2 | TELEGRAM_ID: 11"),
            image(mime = "image/jpg", data = "BBBB"),
        )
        val spec = ClaudeInRequests.spec(body, AnswerShape.EXTRACTION, detach = true, background = true, search = false)
        assertEquals("claude-sonnet-5-5", spec.getString("model"))
        assertEquals("THE PROMPT", spec.getString("system"))
        assertEquals(
            "RUNTIME CONTEXT\n\nIMAGE_REF 1 | TELEGRAM_ID: 10\n\n(This is attached picture 1.)\n\n" +
                "IMAGE_REF 2 | TELEGRAM_ID: 11\n\n(This is attached picture 2.)",
            spec.getString("prompt"),
        )
        val images = spec.getJSONArray("images")
        assertEquals(2, images.length())
        assertEquals("AAAA", images.getJSONObject(0).getString("data"))
        assertEquals("image/jpeg", images.getJSONObject(0).getString("mime_type"))
        assertEquals("image/jpeg", images.getJSONObject(1).getString("mime_type"))
        assertEquals("EGX Analyzer", spec.getString("app"))
        assertEquals(0, spec.getJSONArray("builtin_tools").length())
        assertTrue(spec.getBoolean("detach"))
        assertEquals("background", spec.getString("priority"))
        val schema = spec.getJSONObject("output_schema")
        assertEquals("object", schema.getString("type"))
        assertEquals(listOf("extracted", "excluded"), schema.getJSONArray("required").let { r -> (0 until r.length()).map(r::getString) })
    }

    @Test
    fun aQuestionWithoutPicturesSendsNoImagesAndCanSearch() {
        val body = JSONObject().put("model", "claude-haiku-5-5").put(
            "messages",
            JSONArray()
                .put(JSONObject().put("role", "system").put("content", "You judge calls."))
                .put(JSONObject().put("role", "user").put("content", "Is COMI a buy?")),
        )
        val spec = ClaudeInRequests.spec(body, AnswerShape.OPINION, detach = false, background = false, search = true)
        assertEquals("You judge calls.", spec.getString("system"))
        assertEquals("Is COMI a buy?", spec.getString("prompt"))
        assertFalse(spec.has("images"))
        assertFalse(spec.has("detach"))
        assertFalse(spec.has("priority"))
        assertEquals(listOf("WebSearch", "WebFetch"), spec.getJSONArray("builtin_tools").let { t -> (0 until t.length()).map(t::getString) })
    }

    @Test
    fun everySchemaIsAnObjectAndSmallEnoughForClaudeIn() {
        AnswerShape.entries.forEach { shape ->
            val schema = shape.schema()
            assertEquals("object", schema.getString("type"))
            // ClaudeIn refuses a schema over 16 KB.
            assertTrue(shape.name, schema.toString().length < 16 * 1024)
        }
    }

    @Test
    fun voiceAndTooManyPicturesAreRefusedInWords() {
        val voice = JSONObject().put("type", "input_audio").put("input_audio", JSONObject().put("data", "x").put("format", "ogg"))
        expectRefusal(extraction(voice), "voice")
        expectRefusal(extraction(*Array(21) { image() }), "at most 20")
        expectRefusal(extraction(image(mime = "image/bmp")), "picture")
    }

    @Test
    fun aShapedAnswerComesBackAsTheMessageWithItsTokens() {
        val done = JSONObject("""
            {"type":"done","ending":"done","answer":"Here it is","structured":{"extracted":[],"excluded":[{"source_message_id":"1"}]},
             "usage":{"input":12,"output":340,"cache_read":5000,"cache_write":800}}
        """.trimIndent())
        val completion = JSONObject(ClaudeInRequests.completion(done))
        val message = completion.getJSONArray("choices").getJSONObject(0)
        assertEquals("stop", message.getString("finish_reason"))
        val content = JSONObject(message.getJSONObject("message").getString("content"))
        assertEquals(1, content.getJSONArray("excluded").length())
        val usage = completion.getJSONObject("usage")
        assertEquals(5812, usage.getLong("prompt_tokens"))
        assertEquals(340, usage.getLong("completion_tokens"))
        assertEquals(6152, usage.getLong("total_tokens"))
        // And the repository's own reader adds the same numbers up.
        assertEquals(6152, parseUsage(completion.toString())!!.totalTokens)
    }

    @Test
    fun anUnshapedAnswerIsTheTextAndACutShortOneSaysSo() {
        val completion = JSONObject(ClaudeInRequests.completion(JSONObject().put("ending", "cut short").put("answer", "{\"a\":1")))
        val message = completion.getJSONArray("choices").getJSONObject(0)
        assertEquals("length", message.getString("finish_reason"))
        assertEquals("{\"a\":1", message.getJSONObject("message").getString("content"))
        assertFalse(completion.has("usage"))
    }

    private fun expectRefusal(body: JSONObject, words: String) {
        try {
            ClaudeInRequests.spec(body, AnswerShape.EXTRACTION, detach = true, background = false, search = false)
            fail("Accepted a body that should be refused")
        } catch (refused: IllegalArgumentException) {
            assertTrue("'${refused.message}' should mention '$words'", refused.message!!.contains(words))
        }
    }
}
