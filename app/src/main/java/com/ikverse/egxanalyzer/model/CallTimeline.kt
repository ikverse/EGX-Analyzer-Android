package com.ikverse.egxanalyzer.model

import java.time.LocalDate

/** How a step of a call's story went: done, under way, still to come, or how it ended. */
enum class CallStepKind { DONE, ACTIVE, WAITING, GOOD, BAD }

/** One step of what happened to a call, with its date where the record keeps one. */
data class CallStep(
    val text: String,
    val detail: String? = null,
    val date: LocalDate? = null,
    val kind: CallStepKind = CallStepKind.DONE,
)

/**
 * What happened to this call, in order, in the reader's words.
 *
 * Only what the record actually holds. The step where the price came into the buying range has no
 * date because the scorer keeps only whether it happened, not when (see `Scoring`); adding one would
 * need a stored field and a database version, which is not this step's business. Target 1's date is
 * known only where it is the session the call settled on.
 */
fun ScoredCall.timeline(): List<CallStep> = buildList {
    add(CallStep("Called by $channel", date = openedOn))
    val traded = outcome != Outcome.ENTRY_NOT_REACHED && outcome != Outcome.UNPRICED
    if (traded) add(CallStep("Price came into the buying range"))
    val took = "in $sessionsElapsed trading ${if (sessionsElapsed == 1) "day" else "days"}"
    when (outcome) {
        Outcome.FULL_HIT -> {
            add(CallStep("Reached target 1"))
            add(CallStep("Reached target 2", detail = took, date = settledOn, kind = CallStepKind.GOOD))
        }
        Outcome.PARTIAL_HIT -> {
            add(CallStep("Reached target 1", detail = took, date = settledOn, kind = CallStepKind.GOOD))
            when {
                stoppedAfterPartial ->
                    add(CallStep("Fell back to the stop", date = stoppedOn, kind = CallStepKind.BAD))
                !windowComplete -> add(CallStep("Target 2 still in reach", kind = CallStepKind.WAITING))
                else -> Unit
            }
        }
        Outcome.STOPPED -> add(CallStep("Broke the stop", detail = took, date = settledOn, kind = CallStepKind.BAD))
        Outcome.EXPIRED -> add(
            CallStep(
                "Ran out of time",
                detail = if (isTPlusOne) {
                    "A next-day call, over after its $windowSessions trading days"
                } else {
                    "Neither a target nor the stop in $windowSessions trading days"
                },
                date = settledOn,
            ),
        )
        Outcome.OPEN -> {
            add(CallStep("Running", detail = "$sessionsElapsed trading ${if (sessionsElapsed == 1) "day" else "days"} so far", kind = CallStepKind.ACTIVE))
            add(CallStep("Target 1", kind = CallStepKind.WAITING))
        }
        Outcome.ENTRY_NOT_REACHED ->
            add(CallStep("The price never came into the buying range", kind = CallStepKind.WAITING))
        Outcome.UNPRICED, Outcome.PRICE_BREAK, Outcome.AMBIGUOUS ->
            add(CallStep("Not counted", kind = CallStepKind.WAITING))
    }
}
