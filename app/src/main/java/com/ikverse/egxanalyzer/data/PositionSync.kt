package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.Position
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate

/**
 * One revision of one position, as it travels between devices.
 *
 * The same shape a wording rule travels in, and for the same reason: a trade is a row, not a file.
 * It is edited when a price was mistyped, closed when it is sold, and removed when it should never
 * have been recorded - and two devices can do different things to one while both are offline. So
 * what travels is the revision, with the merge deciding which one wins rather than whoever happened
 * to upload last.
 *
 * Saved reports are different and stay different: a run never changes once written, so those are a
 * union with nothing to resolve.
 */
/**
 * One stored revision, with whatever a newer app version wrote against it.
 *
 * Separate from [SyncedPosition] because that is the wire shape and this is the disk shape; they
 * happen to carry the same three things, and collapsing them would put a Telegram concern in the
 * database layer.
 */
data class PositionRevision(
    val position: Position,
    val deleted: Boolean,
    val unknown: String = "{}",
)

data class SyncedPosition(
    val position: Position,
    val deleted: Boolean,
    /**
     * Fields a newer app version wrote that this one does not understand.
     *
     * Kept and written back untouched, so an older device reading and re-uploading a position does
     * not quietly strip whatever a newer one had added to it.
     */
    val unknown: String = "{}",
) {
    /**
     * Names the position and the revision.
     *
     * Two edits made offline are then two documents rather than one overwriting the other before
     * anyone has compared them.
     */
    val fileName: String get() = "$PREFIX${position.id.sanitized()}-${position.updatedAt}$SUFFIX"

    fun toDocument(): String {
        // Held as text rather than a JSONObject: that class has no equals, so a position would
        // never compare equal to itself and every sync would look like a change.
        val json = runCatching { JSONObject(unknown) }.getOrDefault(JSONObject())
        return json
            .put("id", position.id)
            .put("ticker", position.ticker)
            .putOrNull("companyEnglish", position.companyEnglish)
            .putOrNull("companyArabic", position.companyArabic)
            .putOrNull("channel", position.channel)
            .put("recommendationDate", position.recommendationDate.toString())
            .put("entryPrice", position.entryPrice)
            .put("entryDate", position.entryDate.toString())
            .putOrNull("exitPrice", position.exitPrice)
            .putOrNull("exitDate", position.exitDate?.toString())
            .put("closedManually", position.closedManually)
            .putOrNull("entryLow", position.entryLow)
            .putOrNull("entryHigh", position.entryHigh)
            .putOrNull("target1", position.target1)
            .putOrNull("target2", position.target2)
            .putOrNull("stopLoss", position.stopLoss)
            .put("windowSessions", position.windowSessions)
            .put("windowCustom", position.windowCustom)
            .put("keepOpen", position.keepOpen)
            .putOrNull("keepOpenNote", position.keepOpenNote)
            .put("openedAt", position.openedAt.toString())
            .put("updatedAt", position.updatedAt)
            .put("updatedBy", position.updatedBy)
            .put("deleted", deleted)
            .toString()
    }

    companion object {
        private val KNOWN = setOf(
            "id", "ticker", "companyEnglish", "companyArabic", "channel", "recommendationDate",
            "entryPrice", "entryDate", "exitPrice", "exitDate", "closedManually", "entryLow",
            "entryHigh", "target1", "target2", "stopLoss", "windowSessions", "windowCustom",
            "keepOpen", "keepOpenNote", "openedAt", "updatedAt", "updatedBy", "deleted",
        )

        /** Null for anything this app version cannot make sense of, which is then skipped. */
        fun fromDocument(text: String): SyncedPosition? = runCatching {
            val json = JSONObject(text)
            val id = json.getString("id").ifBlank { return null }
            val unknown = JSONObject()
            json.keys().forEach { key -> if (key !in KNOWN) unknown.put(key, json.get(key)) }
            SyncedPosition(
                position = Position(
                    id = id,
                    ticker = json.getString("ticker"),
                    companyEnglish = json.optionalString("companyEnglish"),
                    companyArabic = json.optionalString("companyArabic"),
                    channel = json.optionalString("channel"),
                    recommendationDate = LocalDate.parse(json.getString("recommendationDate")),
                    entryPrice = json.getDouble("entryPrice"),
                    entryDate = LocalDate.parse(json.getString("entryDate")),
                    exitPrice = json.optionalDouble("exitPrice"),
                    exitDate = json.optionalString("exitDate")?.let(LocalDate::parse),
                    closedManually = json.optBoolean("closedManually", false),
                    entryLow = json.optionalDouble("entryLow"),
                    entryHigh = json.optionalDouble("entryHigh"),
                    target1 = json.optionalDouble("target1"),
                    target2 = json.optionalDouble("target2"),
                    stopLoss = json.optionalDouble("stopLoss"),
                    windowSessions = json.getInt("windowSessions"),
                    // Absent on anything written before a trade could outlive its deadline, and
                    // false is what those trades were: the offered window, closing when it ran out.
                    windowCustom = json.optBoolean("windowCustom", false),
                    keepOpen = json.optBoolean("keepOpen", false),
                    keepOpenNote = json.optionalString("keepOpenNote"),
                    openedAt = json.optionalString("openedAt")
                        ?.let { runCatching { Instant.parse(it) }.getOrNull() }
                        ?: Instant.EPOCH,
                    updatedAt = json.optLong("updatedAt"),
                    updatedBy = json.optString("updatedBy"),
                ),
                deleted = json.optBoolean("deleted", false),
                unknown = unknown.toString(),
            )
        }.getOrNull()

        /** The position a file name names, or null when the file is not one of ours. */
        fun positionIdOf(fileName: String): String? = fileName
            .takeIf { it.startsWith(PREFIX) && it.endsWith(SUFFIX) }
            ?.removePrefix(PREFIX)
            ?.removeSuffix(SUFFIX)
            ?.substringBeforeLast('-')
            ?.takeIf(String::isNotBlank)

        private const val PREFIX = "position-"
        private const val SUFFIX = ".json"

        /** File names cannot carry every character an id might; the id inside the file is the truth. */
        private fun String.sanitized(): String = replace(Regex("[^A-Za-z0-9._:-]"), "_")

        private fun JSONObject.putOrNull(key: String, value: Any?): JSONObject =
            put(key, value ?: JSONObject.NULL)

        private fun JSONObject.optionalString(key: String): String? =
            if (isNull(key)) null else optString(key).takeIf(String::isNotBlank)

        private fun JSONObject.optionalDouble(key: String): Double? =
            if (!has(key) || isNull(key)) null else optDouble(key).takeUnless(Double::isNaN)
    }
}

/**
 * Decides which revision of each position wins.
 *
 * Last writer wins, by the moment it was written, with the device name breaking a tie so two
 * devices that edited within the same millisecond still agree on the answer. A delete is a revision
 * like any other, so a later edit can overtake it and an earlier one can never resurrect it.
 */
fun mergePositions(revisions: List<SyncedPosition>): List<SyncedPosition> = revisions
    .groupBy { it.position.id }
    .mapNotNull { (_, forId) ->
        forId.maxWithOrNull(
            compareBy<SyncedPosition> { it.position.updatedAt }.thenBy { it.position.updatedBy },
        )
    }
    .sortedBy { it.position.id }

/** What a device has to send, having seen what the channel already holds. */
fun positionsToUpload(
    local: List<SyncedPosition>,
    remote: List<SyncedPosition>,
): List<SyncedPosition> {
    val newest = remote.associateBy { it.position.id }
    return local.filter { mine ->
        val theirs = newest[mine.position.id] ?: return@filter true
        mine.position.updatedAt > theirs.position.updatedAt ||
            (
                mine.position.updatedAt == theirs.position.updatedAt &&
                    mine.position.updatedBy > theirs.position.updatedBy
                )
    }
}
