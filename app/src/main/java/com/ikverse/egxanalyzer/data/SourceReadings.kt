package com.ikverse.egxanalyzer.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * What one run read out of one message, in a shape the next run can use.
 *
 * A run's window starts at yesterday's opening hour, so a second schedule in the same day covers
 * every message the first one already covered - and re-reading a card the model has already read is
 * the one cost in the whole path nobody chose to pay. A reading written down here is a card that
 * need not be sent again.
 *
 * The whole difficulty is the image reference. `IMAGE_REF` is a position in one request, so it
 * means nothing tomorrow: the same card can be image 3 one morning and image 11 the next. A stored
 * reading therefore numbers each row against **its own source's** images, which is a fact about the
 * message rather than about the run, and is laid back over whatever numbering the new run gives it.
 *
 * Nothing here decides what may be reused - that is the repository's, which knows what the run is
 * sending. This only translates a reading in and out of storage, and refuses to translate one that
 * does not line up exactly. Refusing costs a request; getting it wrong costs a misread card in a
 * report that looks ordinary, and those two are not the same size of mistake.
 */
internal object SourceReadings {

    /**
     * The three arrays a reading holds, in the shape it is stored rather than the shape it is sent.
     *
     * `inquiries` is the model's `client_inquiry_responses` under the name the rest of the code
     * gives it. Nothing queries inside a stored reading, so the shorter name costs nothing.
     */
    val KEYS = listOf("extracted", "excluded", "inquiries")

    private const val IMAGES = "images"
    private const val MESSAGE_ID = "source_message_id"
    private const val IMAGE_REF = "source_image_ref"

    /**
     * A stored reading, translated onto this run's [refs] for that source's images.
     *
     * Null where it cannot be laid over them exactly: text that will not parse, a source that has
     * gained or lost an image since, or a row naming an image its own source does not have. All
     * three mean the same thing - send the message again.
     */
    fun lay(stored: String, refs: List<Int>): Map<String, JSONArray>? {
        val reading = runCatching { JSONObject(stored) }.getOrNull() ?: return null
        if (reading.optInt(IMAGES, -1) != refs.size) return null
        val laid = mutableMapOf<String, JSONArray>()
        for (key in KEYS) {
            val rows = reading.optJSONArray(key) ?: JSONArray()
            val translated = JSONArray()
            for (row in rows.objects()) {
                val copy = JSONObject(row.toString())
                val local = copy.reference()
                when {
                    local == null -> copy.put(IMAGE_REF, JSONObject.NULL)
                    local in 1..refs.size -> copy.put(IMAGE_REF, refs[local - 1])
                    else -> return null
                }
                translated.put(copy)
            }
            laid[key] = translated
        }
        return laid
    }

    /**
     * What [rows] said about the one message [telegramId] names, written down for the next run.
     *
     * [rows] is the whole chunk's answer and [refs] this source's own images in the run's
     * numbering, so each row is filed back under the image of its source rather than of the
     * request. Null where a row of this message names an image belonging to another one: the model
     * has lost track of which card it is reading, and half a reading is worse than none.
     */
    fun of(rows: Map<String, JSONArray>, telegramId: String, refs: List<Int>): String? {
        val reading = JSONObject().put(IMAGES, refs.size)
        for (key in KEYS) {
            val own = JSONArray()
            for (row in rows[key].objects()) {
                if (row.optString(MESSAGE_ID) != telegramId) continue
                val copy = JSONObject(row.toString())
                val reference = copy.reference()
                if (reference == null) {
                    copy.put(IMAGE_REF, JSONObject.NULL)
                } else {
                    val local = refs.indexOf(reference)
                    if (local < 0) return null
                    copy.put(IMAGE_REF, local + 1)
                }
                own.put(copy)
            }
            reading.put(key, own)
        }
        return reading.toString()
    }

    /**
     * Whether the answer names a message the request never carried.
     *
     * The gate on remembering anything from a chunk at all. A row citing a `TELEGRAM_ID` that was
     * not sent is the model inventing an attribution, and the sources beside it in that answer
     * cannot be trusted to have been read as carefully as they look.
     */
    fun namesOthers(rows: Map<String, JSONArray>, known: Set<String>): Boolean =
        KEYS.any { key -> rows[key].objects().any { it.optString(MESSAGE_ID) !in known } }

    /** The row's image, or null where it names none. A zero is how the model writes "no image". */
    private fun JSONObject.reference(): Int? =
        if (isNull(IMAGE_REF)) null else optInt(IMAGE_REF, 0).takeIf { it > 0 }

    private fun JSONArray?.objects(): List<JSONObject> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { optJSONObject(it) }
    }
}
