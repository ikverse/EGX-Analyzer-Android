package com.ikverse.egxanalyzer.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Keeps a readable record of what was actually sent to the model, and what came back.
 *
 * A saved analysis stores only the response. Diagnosing a run therefore meant reconstructing the
 * request from the sources table and hoping the reconstruction matched - which is exactly the kind
 * of guess that let a mis-cited image go unnoticed for two runs.
 *
 * Image bytes are replaced by a note of their size. A real request carries about five megabytes of
 * base64; the trace of a whole run is a few kilobytes, which is the difference between something
 * worth keeping and something that fills the device.
 */
class RequestTrace(private val context: Context, private val requestId: String) {

    private val directory: File by lazy {
        File(File(context.filesDir, TRACE_ROOT), requestId).apply { mkdirs() }
    }

    /** @param label distinguishes the calls in a run: `chunk-1`, `chunk-1-retry`, `consolidation`. */
    fun record(label: String, body: JSONObject, response: String?) {
        runCatching {
            File(directory, "$label-request.json").writeText(redact(body).toString(2))
            response?.let { File(directory, "$label-response.json").writeText(it) }
        }
    }

    fun location(): String = directory.path

    companion object {
        const val TRACE_ROOT = "traces"

        /** Only the most recent runs are kept, so traces cannot grow without bound. */
        const val KEEP_RUNS = 10

        fun prune(context: Context) {
            runCatching {
                File(context.filesDir, TRACE_ROOT).listFiles()
                    ?.filter(File::isDirectory)
                    ?.sortedByDescending(File::lastModified)
                    ?.drop(KEEP_RUNS)
                    ?.forEach { it.deleteRecursively() }
            }
        }

        /**
         * Replaces every base64 payload with a note of its size.
         *
         * The prompt, the source labels and the model's own answer are what a diagnosis needs; the
         * pixels never are.
         */
        fun redact(body: JSONObject): JSONObject {
            val copy = JSONObject(body.toString())
            val messages = copy.optJSONArray("messages") ?: return copy
            for (index in 0 until messages.length()) {
                val content = messages.optJSONObject(index)?.opt("content")
                if (content is JSONArray) redactParts(content)
            }
            return copy
        }

        private fun redactParts(parts: JSONArray) {
            for (index in 0 until parts.length()) {
                val part = parts.optJSONObject(index) ?: continue
                part.optJSONObject("image_url")?.let { image ->
                    val url = image.optString("url")
                    image.put("url", "<${url.substringBefore(",").ifBlank { "image" }}, ${url.length} chars>")
                }
                part.optJSONObject("input_audio")?.let { audio ->
                    audio.put("data", "<audio, ${audio.optString("data").length} chars>")
                }
            }
        }
    }
}
