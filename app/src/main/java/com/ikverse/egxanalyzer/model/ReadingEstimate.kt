package com.ikverse.egxanalyzer.model

import java.time.Duration
import java.time.Instant
import kotlin.math.roundToLong

/**
 * How long a run has left to read, from how fast it has read so far.
 *
 * Only the reading: it is the part made of countable batches, so the pace of the ones done says
 * something about the ones left. The writing is one answer of unknown length and is never
 * estimated - see [AnalysisProgress.fraction] for the same reasoning about the bar. Set once per
 * batch, as an end time, so the phrase counts down between batches without anything being
 * worked out again.
 */
object ReadingEstimate {

    /**
     * When the reading should end, or null where there is nothing to go on yet: before the first
     * unit is back, once every unit is, or with no time elapsed.
     */
    fun endsAt(readingStarted: Instant, now: Instant, done: Int, total: Int): Instant? {
        if (done <= 0 || total <= done) return null
        val elapsed = Duration.between(readingStarted, now).toMillis()
        if (elapsed <= 0) return null
        val left = (elapsed.toDouble() * (total - done) / done).roundToLong()
        return now.plusMillis(left)
    }

    /** The end time said the way a person would, or null where there is no estimate. */
    fun phrase(now: Instant, endsAt: Instant?): String? {
        endsAt ?: return null
        val seconds = Duration.between(now, endsAt).seconds
        if (seconds < 45) return "Less than a minute to go"
        val minutes = ((seconds + 30) / 60).coerceAtLeast(1)
        return if (minutes == 1L) "About a minute to go" else "About $minutes minutes to go"
    }
}
