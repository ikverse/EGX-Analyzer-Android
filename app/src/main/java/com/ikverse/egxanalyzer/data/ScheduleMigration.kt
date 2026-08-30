package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.AnalysedChannel
import com.ikverse.egxanalyzer.model.AnalysisContentType
import com.ikverse.egxanalyzer.model.AnalysisSchedule
import org.json.JSONObject
import java.time.Instant
import java.time.LocalTime

/**
 * What the rows of the retired job table mean under the two things that replaced it.
 *
 * The table let a user build any number of jobs out of a kind of work and a choice of trigger.
 * What is left is a checkbox that keeps prices fresh while the market trades, and one analysis at
 * one time - so this reads what was there and answers with the nearest true thing, once, before
 * the table is dropped.
 *
 * Carrying an intent across is not the same as making a new one. A phone that was asking for
 * prices through the session goes on asking; a phone that had switched that off does not have it
 * switched on for it. The one thing this deliberately will not do is invent a schedule nobody set.
 *
 * Pure, so the decisions can be checked without a database: it is handed rows and returns what
 * should be written.
 */
object ScheduleMigration {

    /** What to write in place of the table. A null [schedule] leaves the analysis side untouched. */
    data class Result(val marketRefresh: Boolean, val schedule: AnalysisSchedule?)

    /**
     * Reads the old rows.
     *
     * A price refresh that was switched on becomes the checkbox, whatever shape its trigger had:
     * every one of them - after the close, hourly, through the session - was a way of asking the
     * same question, and the checkbox is now the answer to all of them.
     *
     * An analysis survives only where it repeated on a set of days, which is the only one of the
     * three triggers the new schedule can still express. A one-shot is a button press with a delay
     * on it and its moment has almost certainly passed; an analysis on an interval would have been
     * paying for the same session several times over. Neither is worth reconstructing, and both
     * are dropped with the schedule left switched off rather than guessed at.
     */
    fun from(rows: List<LegacyScheduleRow>, armedAt: Instant = Instant.now()): Result {
        val marketRefresh = rows.any { it.workKind == PRICE_REFRESH && it.enabled }
        val analysis = rows.asSequence()
            .filter { it.workKind == ANALYSIS && it.triggerKind == REPEAT }
            .mapNotNull { it.toSchedule(armedAt) }
            // The first, because a table holding two analyses is a phone whose owner set up more
            // than the new shape can hold, and the earliest one is the one they made first.
            .firstOrNull()
        return Result(marketRefresh, analysis)
    }

    /**
     * One row as a schedule, or null where its settings cannot be read.
     *
     * An unreadable row is dropped rather than turned into an analysis of no chats, which would be
     * a paid request for an empty answer booked by a migration nobody watched.
     */
    private fun LegacyScheduleRow.toSchedule(armedAt: Instant): AnalysisSchedule? = runCatching {
        val json = JSONObject(workConfig)
        val channelArray = json.getJSONArray("channels")
        val channels = (0 until channelArray.length()).map {
            val entry = channelArray.getJSONObject(it)
            AnalysedChannel(entry.getLong("id"), entry.getString("name"))
        }
        val typeArray = json.getJSONArray("contentTypes")
        val types = (0 until typeArray.length())
            .mapNotNullTo(mutableSetOf()) { index ->
                val name = typeArray.getString(index)
                AnalysisContentType.entries.firstOrNull { it.name == name }
            }
        require(channels.isNotEmpty() && types.isNotEmpty())
        AnalysisSchedule(
            enabled = enabled,
            at = LocalTime.parse(triggerAt),
            channels = channels,
            contentTypes = types,
            // Armed now rather than carried, so a schedule whose hour has already gone by today
            // does not owe a run the moment the app finishes migrating and pay for it through the
            // grace window. Tomorrow is the first fire either way.
            armedAt = armedAt,
        )
    }.getOrNull()

    private const val PRICE_REFRESH = "PRICE_REFRESH"
    private const val ANALYSIS = "ANALYSIS"
    private const val REPEAT = "REPEAT"
}
