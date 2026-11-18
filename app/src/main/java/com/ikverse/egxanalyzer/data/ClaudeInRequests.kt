package com.ikverse.egxanalyzer.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Which answer a request is asking for, so ClaudeIn can be told the shape it must take
 * (`output_schema`). The schemas are deliberately loose - the top-level keys and their types, never
 * the fields inside a row - because the parsers after them already judge each row, and a schema
 * stricter than the prompt is a run that fails on a field the prompt allowed.
 */
enum class AnswerShape {
    EXTRACTION,
    CONSOLIDATION,
    OPINION,
    BRIEF,
    ;

    fun schema(): JSONObject = when (this) {
        EXTRACTION -> objectOf(
            required = listOf("extracted", "excluded"),
            "extracted" to arrayOfObjects(),
            "excluded" to arrayOfObjects(),
            "client_inquiry_responses" to arrayOfObjects(),
        )
        CONSOLIDATION -> objectOf(
            required = listOf("top_consolidated_recommendations"),
            "top_consolidated_recommendations" to arrayOfObjects(),
        )
        // Every key optional: OpinionParser already reads a missing section as "nothing said".
        OPINION -> objectOf(
            required = emptyList(),
            "on_the_call" to JSONObject().put("type", "object"),
            "forecast" to JSONObject().put("type", "object"),
            "news" to arrayOfObjects(),
            "catalysts" to arrayOfObjects(),
            "risks" to arrayOfStrings(),
            "unknowns" to arrayOfStrings(),
        )
        BRIEF -> objectOf(
            required = listOf("summary"),
            "summary" to JSONObject().put("type", "string"),
            "points" to arrayOfStrings(),
            "watch" to arrayOfStrings(),
        )
    }

    private companion object {
        fun objectOf(required: List<String>, vararg properties: Pair<String, JSONObject>): JSONObject =
            JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply { properties.forEach { (k, v) -> put(k, v) } })
                if (required.isNotEmpty()) put("required", JSONArray(required))
            }

        fun arrayOfObjects() = JSONObject().put("type", "array").put("items", JSONObject().put("type", "object"))
        fun arrayOfStrings() = JSONObject().put("type", "array").put("items", JSONObject().put("type", "string"))
    }
}

/**
 * The one place this app's requests become ClaudeIn runs and ClaudeIn's answers become this app's.
 *
 * Every request in the app is built as an OpenAI chat-completions body and read back from
 * `choices[0].message.content`, so the ClaudeIn route translates at the edge instead of teaching
 * the extraction, the retries and the consolidation a second dialect. Pure, so it is tested alone.
 */
object ClaudeInRequests {
    const val APP = "EGX Analyzer"

    /** ClaudeIn takes at most 20 pictures a run; a batch is 8. */
    const val MAX_IMAGES = 20

    /**
     * [body] as a `POST /runs` spec.
     *
     * The system message becomes `system`. The user message's text parts become `prompt`, in order;
     * each picture is lifted into `images` in the same order, and where it stood the prompt says
     * which attached picture it is. ClaudeIn shows Claude the pictures before the prompt, so the
     * `IMAGE_REF n` line the extraction prompt puts before each image still names the right one.
     */
    fun spec(
        body: JSONObject,
        shape: AnswerShape?,
        detach: Boolean,
        background: Boolean,
        search: Boolean,
    ): JSONObject {
        val messages = body.optJSONArray("messages") ?: JSONArray()
        val system = StringBuilder()
        val prompt = StringBuilder()
        val images = JSONArray()
        for (index in 0 until messages.length()) {
            val message = messages.optJSONObject(index) ?: continue
            val target = if (message.optString("role") == "system") system else prompt
            when (val content = message.opt("content")) {
                is String -> target.appendPart(content)
                is JSONArray -> for (p in 0 until content.length()) {
                    val part = content.optJSONObject(p) ?: continue
                    when (part.optString("type")) {
                        "text" -> target.appendPart(part.optString("text"))
                        "image_url" -> {
                            val url = part.optJSONObject("image_url")?.optString("url").orEmpty()
                            val picture = dataUrlPicture(url)
                                ?: throw IllegalArgumentException("A picture could not be prepared for ClaudeIn.")
                            images.put(picture)
                            target.appendPart("(This is attached picture ${images.length()}.)")
                        }
                        "input_audio" -> throw IllegalArgumentException(
                            "ClaudeIn can't take voice messages. Untick voice in Settings, Reading rules.",
                        )
                    }
                }
            }
        }
        if (images.length() > MAX_IMAGES) {
            throw IllegalArgumentException("ClaudeIn takes at most $MAX_IMAGES pictures in one question.")
        }
        return JSONObject().apply {
            put("model", body.optString("model"))
            put("system", system.toString().ifBlank { "You are a careful assistant." })
            put("prompt", prompt.toString())
            put("app", APP)
            put("builtin_tools", if (search) JSONArray(listOf("WebSearch", "WebFetch")) else JSONArray())
            if (images.length() > 0) put("images", images)
            shape?.let { put("output_schema", it.schema()) }
            if (detach) put("detach", true)
            if (background) put("priority", "background")
        }
    }

    /**
     * A `done` event as the chat-completions answer the rest of the repository reads: the shaped
     * answer (or the text, where none was asked) as the message content, the ending as the finish
     * reason, and ClaudeIn's token counts as a `usage` block so the tally adds them up unchanged.
     */
    fun completion(done: JSONObject): String {
        val structured = done.optJSONObject("structured")
        val content = structured?.toString() ?: done.optString("answer")
        val usage = done.optJSONObject("usage") ?: JSONObject()
        val prompt = usage.optLong("input") + usage.optLong("cache_read") + usage.optLong("cache_write")
        val output = usage.optLong("output")
        return JSONObject().apply {
            put(
                "choices",
                JSONArray().put(
                    JSONObject()
                        .put("message", JSONObject().put("role", "assistant").put("content", content))
                        .put("finish_reason", if (done.optString("ending") == "cut short") "length" else "stop"),
                ),
            )
            if (prompt > 0 || output > 0) {
                put(
                    "usage",
                    JSONObject()
                        .put("prompt_tokens", prompt)
                        .put("completion_tokens", output)
                        .put("total_tokens", prompt + output),
                )
            }
        }.toString()
    }

    /** `data:image/jpeg;base64,…` as `{"data", "mime_type"}`, or null when it is not one. */
    internal fun dataUrlPicture(url: String): JSONObject? {
        if (!url.startsWith("data:")) return null
        val comma = url.indexOf(',')
        if (comma < 0) return null
        val header = url.substring(5, comma)
        if (!header.endsWith(";base64")) return null
        val mime = header.removeSuffix(";base64").lowercase().let { if (it == "image/jpg") "image/jpeg" else it }
        if (mime !in setOf("image/png", "image/jpeg", "image/webp", "image/gif")) return null
        return JSONObject().put("data", url.substring(comma + 1)).put("mime_type", mime)
    }

    private fun StringBuilder.appendPart(text: String) {
        if (text.isBlank()) return
        if (isNotEmpty()) append("\n\n")
        append(text)
    }
}
