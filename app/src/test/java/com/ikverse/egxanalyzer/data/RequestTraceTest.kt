package com.ikverse.egxanalyzer.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RequestTraceTest {

    /** A request body shaped the way the repository builds one. */
    private fun body(base64: String): JSONObject = JSONObject().apply {
        put("model", "qwen-vl")
        put("messages", JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", "the prompt"))
            put(
                JSONObject().put("role", "user").put("content", JSONArray().apply {
                    put(JSONObject().put("type", "text").put("text", "IMAGE_REF 1 | TELEGRAM_ID: 42"))
                    put(
                        JSONObject().put("type", "image_url").put(
                            "image_url", JSONObject().put("url", "data:image/jpeg;base64,$base64"),
                        ),
                    )
                }),
            )
        })
    }

    @Test
    fun `image bytes never reach the trace`() {
        // A real request carries about five megabytes of base64. Keeping that per run would fill
        // the device, and the pixels are never what a diagnosis needs.
        val payload = "A".repeat(50_000)
        val url = "data:image/jpeg;base64,$payload"
        val redacted = RequestTrace.redact(body(payload)).toString()

        assertFalse(redacted.contains(payload))
        assertTrue(redacted.contains("${url.length} chars"))
        assertTrue(redacted.contains("data:image/jpeg;base64"))
    }

    @Test
    fun `everything a diagnosis needs survives`() {
        val redacted = RequestTrace.redact(body("AAAA")).toString()

        assertTrue(redacted.contains("the prompt"))
        assertTrue(redacted.contains("IMAGE_REF 1 | TELEGRAM_ID: 42"))
        assertTrue(redacted.contains("qwen-vl"))
    }

    @Test
    fun `a body without images is unchanged`() {
        val plain = JSONObject().put("model", "qwen").put("messages", JSONArray())
        assertTrue(RequestTrace.redact(plain).toString().contains("qwen"))
    }
}
