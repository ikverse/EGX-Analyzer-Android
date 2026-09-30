package com.ikverse.egxanalyzer.model

import java.time.LocalDate

/**
 * What the market did to this record on one trading session.
 *
 * The app has always known this and had nowhere to say it. A target reached on Tuesday reaches the
 * reader as a status on a card they have to go and find, in a section they have to unfold, on a tab
 * they have to choose - so the one question with a natural daily rhythm, *what happened today*, was
 * the one question no screen answered. Every figure behind it was already being computed and thrown
 * away on every recompute.
 *
 * **A session, not a day.** The unit is the trading session the exchange ran, because that is what
 * moves a price: a Friday produces nothing, a holiday produces nothing, and an event is dated by
 * the session that caused it rather than by the day this phone happened to notice. So a digest is
 * settled the moment its session closes and cannot move again, which is the whole promise of the
 * card - the list a reader saw at noon is the list they see at six, and it changes when the next
 * session opens and not before.
 *
 * **Derived, never remembered**, exactly like the portfolio and unlike `position_status_seen`.
 * Every event here is a function of prices already on disk, so a phone that was switched off for a
 * week comes back with each of those sessions filled in correctly rather than with seven days of
 * news piled onto the day it was turned on. This is the difference between this file and
 * [TradeAlerts]: that one answers "what should I say out loud, once", which is a question about
 * what the user has already been told; this one answers "what happened on that session", which has
 * one right answer forever and no memory in it at all. A trade recorded long after the fact still
 * reports what the market did to it, because it did.
 *
 * Pure, with no Android and no storage in it, for the reason [TradeAlerts] and `ScheduleClock` are:
 * what counts as an event is a rule about trading, and a rule about trading has to be checkable
 * without a device.
 */
data class SessionDigest(
    val date: LocalDate,
    /**
     * What the market did, in the order the card draws them.
     *
     * The user's own trades first, then the calls they only watched, and inside each group the
     * endings before the progress before the information. See [DayEventKind]'s declaration order,
     * which is that order and the only place it is stated.
     */
    val events: List<DayEvent> = emptyList(),
    /**
     * Calls published for this session, and how many sources printed them.
     *
     * A count rather than an event each, and deliberately **not** stored. Every one of these calls
     * is already on disk in full, in the analysis it came from - a row per call in a second table
     * would be a copy of the record that can fall out of step with it. What the market *did* has no
     * such home, which is why the rest of this is written down.
     */
    val newCalls: Int = 0,
    val newCallSources: Int = 0,
) {

    val isEmpty: Boolean get() = events.isEmpty() && newCalls == 0

    /** Trades of the user's own that moved, which is the half of the card costing them money. */
    val heldEvents: Int get() = events.count { it.kind.held }

    val targets: Int get() = events.count { it.kind.tone == EventTone.GAIN }
    val stops: Int get() = events.count { it.kind.tone == EventTone.LOSS }
    val expiries: Int get() = events.count { it.kind.tone == EventTone.EXPIRY }
    val inRange: Int get() = events.count { it.kind == DayEventKind.CALL_IN_RANGE }

    /**
     * The one colour the session gets, where the heading can only wear one.
     *
     * A stop outranks a target on a day that saw both, and that is a deliberate asymmetry rather
     * than pessimism: the reader who has lost money on a session needs to know before they read
     * anything else, and a green heading over a red event is the card burying its own worst news.
     * A session that only ran a window out is amber, and one that only brought stocks into range is
     * the blue that means a price the market reached. Null on a session where nothing happened, so
     * the heading falls back to the app's own voice.
     */
    val headline: EventTone?
        get() = when {
            stops > 0 -> EventTone.LOSS
            targets > 0 -> EventTone.GAIN
            expiries > 0 -> EventTone.EXPIRY
            inRange > 0 -> EventTone.MARKET
            else -> null
        }

    /**
     * The same session, narrowed to the user's own trades.
     *
     * What the Portfolio's card reports. The two tabs used to draw one digest between them, on the
     * reasoning that one card cannot then report a session two ways - which was right about the
     * arithmetic and wrong about the question. The Portfolio is the tab holding the user's money,
     * and a session where three channels' calls reached targets and the user held none of them
     * belongs on Insights: on the Portfolio it reads as news about their own trades until they open
     * the card and find nothing of theirs in it.
     *
     * A filter over the built digest rather than a second build, so every figure on both cards
     * still comes from one pass and one set of rules. The scope changes what is counted; nothing
     * changes how.
     *
     * [newCalls] goes with the call events. It is a count of what channels published for the
     * session, which is a fact about the sources rather than about anything the reader owns - and
     * on Insights it sits under a page of those calls, where it has something to refer to.
     */
    fun heldOnly(): SessionDigest = copy(
        events = events.filter { it.kind.held },
        newCalls = 0,
        newCallSources = 0,
    )

    companion object {

        /**
         * How many sessions back are derived and written on each recompute.
         *
         * The card wants one. The table wants a history, and a bound is what stops every recompute
         * rewriting a year of rows to change nothing. Thirty sessions is comfortably longer than
         * any gap between two launches of an app that is opened to read the market, so a session
         * cannot pass through the window unrecorded, and it is the same horizon a call is judged
         * over - past it a session holds nothing this file could still be learning about.
         */
        const val STORED_SESSIONS = 30

        /**
         * Every session in [sessions], with what the market did on each.
         *
         * One pass over the trades and one over the calls, bucketed by the session that caused each
         * event - rather than a pass over the positions per session, which is the same arithmetic
         * done thirty times.
         *
         * @param sessions the sessions to report on, newest first. Anything dated outside them is
         *   dropped rather than filed under the nearest one.
         */
        fun build(
            sessions: List<LocalDate>,
            positions: List<PositionView>,
            calls: List<ScoredCall>,
        ): List<SessionDigest> {
            val wanted = sessions.toSet()
            val byDate = mutableMapOf<LocalDate, MutableList<DayEvent>>()
            fun emit(on: LocalDate?, event: DayEvent) {
                if (on == null || on !in wanted) return
                byDate.getOrPut(on) { mutableListOf() } += event
            }

            positions.forEach { view -> tradeEvents(view, ::emit) }

            // The trades speak for the calls they were taken on. Two entries for one stock - the
            // channel's call reaching a target and the user's trade reaching it - would be the card
            // reporting one event twice, and the trade is the one carrying the money. It is the
            // same rule `CallAlerts` follows for the same reason.
            val held = positions.mapTo(mutableSetOf()) { it.position.id }
            val fresh = mutableMapOf<LocalDate, MutableSet<String>>()
            calls.forEach { call ->
                // A standing recommendation re-posted every morning is one call, everywhere in this
                // app. Counting each posting would make a source running a daily table look like a
                // busier session than one that posts when it has something to say.
                if (call.repeatOf != null) return@forEach
                if (call.openedOn in wanted) {
                    fresh.getOrPut(call.openedOn) { mutableSetOf() } += call.channel
                }
                if (call.positionId in held) return@forEach
                callEvents(call, ::emit)
            }

            val newCalls = calls
                .filter { it.repeatOf == null && it.openedOn in wanted }
                .groupingBy { it.openedOn }
                .eachCount()

            return sessions.map { date ->
                SessionDigest(
                    date = date,
                    events = byDate[date].orEmpty().sortedWith(EventOrder),
                    newCalls = newCalls[date] ?: 0,
                    newCallSources = fresh[date]?.size ?: 0,
                )
            }
        }

        /**
         * What the market did to one trade, on the sessions it did it.
         *
         * Read off the dates the scorer already produced rather than by replaying anything: a
         * verdict and the session behind it are one fact, and deriving the day a second way is how
         * a card ends up disagreeing with the trade it opens.
         *
         * A trade whose prices changed scale is skipped whole. The app refuses to value one - the
         * entry was paid in the old money and every price since is quoted in the new - and a card
         * announcing a stop it has just admitted it cannot read would be the one place that refusal
         * did not hold.
         */
        private fun tradeEvents(view: PositionView, emit: (LocalDate?, DayEvent) -> Unit) {
            if (view.priceScaleChanged) return
            val of = { kind: DayEventKind ->
                DayEvent(
                    kind = kind,
                    ticker = view.ticker,
                    channel = view.position.channel,
                    positionId = view.position.id,
                    openedOn = view.position.recommendationDate,
                    returnPct = view.returnPct,
                )
            }
            when (view.marketStatus) {
                PositionStatus.FULL_TARGET_HIT -> emit(view.settledOn, of(DayEventKind.TRADE_TARGET2))
                PositionStatus.STOPPED_OUT -> emit(view.settledOn, of(DayEventKind.TRADE_STOPPED))
                PositionStatus.PARTIAL_TARGET_HIT ->
                    // One event on a session that saw both. The stop is the half worth reporting -
                    // it is where the trade ended - and "took target 1" beside it would read as two
                    // separate pieces of news about one stock on one day.
                    if (view.stoppedAfterPartial && view.stoppedOn == view.settledOn) {
                        emit(view.stoppedOn, of(DayEventKind.TRADE_STOPPED_AFTER_TARGET1))
                    } else {
                        emit(view.settledOn, of(DayEventKind.TRADE_TARGET1))
                        if (view.stoppedAfterPartial) {
                            emit(view.stoppedOn, of(DayEventKind.TRADE_STOPPED_AFTER_TARGET1))
                        }
                    }

                else -> Unit
            }
            // Separate from the verdict rather than part of it, because it is not one: a window
            // runs out because a date passed, and it can run out on a trade that reached target 1
            // weeks earlier. Both events are real and both land on their own session.
            if (view.ranOutOfTime) emit(view.deadlineDate, of(DayEventKind.TRADE_RAN_OUT))
        }

        /** The same questions about a call the user is not in, plus the one only they can ask. */
        private fun callEvents(call: ScoredCall, emit: (LocalDate?, DayEvent) -> Unit) {
            if (call.outcome == Outcome.PRICE_BREAK) return
            val of = { kind: DayEventKind, price: Double? ->
                DayEvent(
                    kind = kind,
                    ticker = Scoring.normalizeTicker(call.ticker),
                    channel = call.channel,
                    positionId = call.positionId,
                    openedOn = call.openedOn,
                    returnPct = call.returnPct,
                    price = price,
                )
            }
            when (call.outcome) {
                Outcome.FULL_HIT -> emit(call.settledOn, of(DayEventKind.CALL_TARGET2, null))
                Outcome.STOPPED -> emit(call.settledOn, of(DayEventKind.CALL_STOPPED, null))
                Outcome.PARTIAL_HIT ->
                    if (call.stoppedAfterPartial && call.stoppedOn == call.settledOn) {
                        emit(call.stoppedOn, of(DayEventKind.CALL_STOPPED_AFTER_TARGET1, null))
                    } else {
                        emit(call.settledOn, of(DayEventKind.CALL_TARGET1, null))
                        if (call.stoppedAfterPartial) {
                            emit(call.stoppedOn, of(DayEventKind.CALL_STOPPED_AFTER_TARGET1, null))
                        }
                    }

                else -> Unit
            }
            crossings(call).forEach { (on, close) ->
                emit(on, of(DayEventKind.CALL_IN_RANGE, close))
            }
        }

        /**
         * The sessions on which the close moved into the buy zone from outside it.
         *
         * The same fact `CallAlerts` notifies on, dated instead of announced, and it carries that
         * file's two rules for the same reasons. Only the crossing **in** counts - a price drifting
         * back out is not news, and reporting both would double every line for a stock moving
         * around inside its own zone. And a session with no close is passed over **without
         * forgetting which side the price was last on**, so a fortnight of a quiet feed does not
         * end in a band the price had been sitting in all along being reported as newly reached.
         *
         * The first session of the window can never be a crossing: there is nothing before it to
         * have crossed from, and a call printed with the price already inside its zone is the new
         * call itself rather than something that happened to it.
         */
        private fun crossings(call: ScoredCall): List<Pair<LocalDate, Double>> {
            val first = call.entryLow ?: call.entryHigh ?: return emptyList()
            val second = call.entryHigh ?: call.entryLow ?: return emptyList()
            val low = minOf(first, second)
            val high = maxOf(first, second)
            var wasInside: Boolean? = null
            val found = mutableListOf<Pair<LocalDate, Double>>()
            call.sessions.forEach { session ->
                val close = session.traded.close ?: return@forEach
                val inside = close in low..high
                if (wasInside == false && inside) found += session.date to close
                wasInside = inside
            }
            return found
        }

        /**
         * The card's order, stated once.
         *
         * By kind before ticker, and [DayEventKind] is declared in the order it should read, so
         * moving an event up the card is moving one line in that enum rather than editing a
         * comparator that no longer matches it.
         */
        private val EventOrder = compareBy<DayEvent>({ it.kind.ordinal }, { it.ticker })
    }
}

/** What a session's news is worth, in the four things a price can say. */
enum class EventTone {
    /** A level the call named as profit. */
    GAIN,

    /** A level the call named as the place to get out. */
    LOSS,

    /** A window that closed without the market reaching either. */
    EXPIRY,

    /** Where the price got to, with no verdict attached. */
    MARKET,
}

/**
 * One thing the market did, and who it happened to.
 *
 * The wording is [TradeAlerts]' own, deliberately: a notification saying "AMOC stopped out" and a
 * line on this card saying the same thing are one event reaching the reader twice, and two
 * vocabularies for it would leave them wondering whether they were two.
 */
enum class DayEventKind(
    val summary: String,
    val tone: EventTone,
    /** The user's own trade, rather than a call they only watched. Decides where a press goes. */
    val held: Boolean,
) {
    TRADE_TARGET2("reached target 2", EventTone.GAIN, held = true),
    TRADE_TARGET1("took target 1", EventTone.GAIN, held = true),
    TRADE_STOPPED_AFTER_TARGET1("stopped out after target 1", EventTone.LOSS, held = true),
    TRADE_STOPPED("stopped out", EventTone.LOSS, held = true),
    TRADE_RAN_OUT("ran out of time", EventTone.EXPIRY, held = true),

    CALL_TARGET2("reached target 2", EventTone.GAIN, held = false),
    CALL_TARGET1("took target 1", EventTone.GAIN, held = false),
    CALL_STOPPED_AFTER_TARGET1("stopped out after target 1", EventTone.LOSS, held = false),
    CALL_STOPPED("stopped out", EventTone.LOSS, held = false),
    CALL_IN_RANGE("came into range", EventTone.MARKET, held = false),
    ;

    companion object {
        /** The stored name back to the kind, or null for a row a newer build wrote. */
        fun from(name: String): DayEventKind? = entries.firstOrNull { it.name == name }
    }
}

/**
 * One event, ready to be drawn and ready to be written down.
 *
 * [positionId] is carried on every event, held or not, because it is the entrance both tabs already
 * use - `AppState.openPosition` for a trade, `AppState.openCall` for a call - and a tile that built
 * its own way to the card would be a second path for the app to disagree with itself over.
 */
data class DayEvent(
    val kind: DayEventKind,
    /** Normalized, so a tile and the trade it opens are never two spellings of one stock. */
    val ticker: String,
    /** Who called it. Null only on a trade recorded before the channel was kept. */
    val channel: String? = null,
    val positionId: String,
    /** The session the call was made for, which dates the card a press lands on. */
    val openedOn: LocalDate,
    /** Where the trade or the call stood, in percent, or null where nothing can be measured. */
    val returnPct: Double? = null,
    /** The close that caused it, on the events a single price explains. */
    val price: Double? = null,
) {

    /**
     * What this event is filed under, unique inside its session.
     *
     * A trade is identified by its holding, because one call is one holding however many channels
     * printed it. A call is identified by ticker, session **and** channel - two sources calling one
     * stock print two different buy zones, and the price can cross into one and not the other, so a
     * key they shared would silently swallow the second event. It is [opinionId]'s key rather than
     * a third spelling of the same idea.
     */
    val id: String
        get() = if (kind.held) {
            "${kind.name}@$positionId"
        } else {
            "${kind.name}@${opinionId(ticker, openedOn, channel.orEmpty())}"
        }
}
