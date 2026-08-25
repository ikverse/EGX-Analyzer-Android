package com.ikverse.egxanalyzer.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.ikverse.egxanalyzer.model.DayEvent
import com.ikverse.egxanalyzer.model.EventTone
import com.ikverse.egxanalyzer.model.SessionDigest
import com.ikverse.egxanalyzer.ui.theme.extraColors

/**
 * What the market did to this record on the session just gone, on the two tabs that ask.
 *
 * The app has always known every fact on this card and has never had a place to say it. A target
 * reached on Tuesday morning reached the reader as a status on a card inside a folded section on a
 * tab they had to choose - so *what happened today*, the one question with a daily rhythm to it,
 * was the question no screen answered. Everything here was already derived on every recompute and
 * dropped on the floor.
 *
 * **One card, drawn on two screens, from one [SessionDigest].** On the Portfolio it sits under
 * Overdue, which is the only thing on that tab asking to be acted on; on Insights under the hero,
 * which is the standing verdict that tab exists for. Second on both, and for one reason: each of
 * those is the thing its page is *for*, and this is what changed since the reader last looked -
 * perishable, so it goes above everything that takes scrolling to reach, and no higher. Its open
 * state is shared through
 * [PageState.todayExpanded] for the same reason the digest is: two cards reporting one session that
 * could be folded apart would read as two different cards that happen to agree.
 *
 * **The two scopes are different, and the arithmetic behind them is not.** [heldOnly] narrows the
 * Portfolio's card to the user's own trades through `SessionDigest.heldOnly`, so both cards are
 * still one pass over one set of rules and cannot count a session two ways - they simply count
 * different things. It began as one card on both tabs, which was right about the arithmetic and
 * wrong about the question: the Portfolio is the tab holding the reader's money, and a session
 * where three channels' calls reached targets and the reader held none of them was reported there
 * as though something of theirs had happened. On Insights the card is unchanged and still says
 * everything, including those calls.
 *
 * **Expanded by default**, alone among the cards in this app. Everything else here folds away
 * because a screen of open cards is unreadable; this one is read once and then scrolled past, and a
 * card that has to be opened before it says anything is a card nobody opens.
 *
 * **Not narrowed by any filter on either tab**, for the reason the Overdue card is not: a channel,
 * a stock or a date picked on screen is a view of the record, never a claim about what the market
 * did.
 */
@Composable
internal fun ColumnScope.TodayCard(appState: AppState, heldOnly: Boolean = false) {
    val digest = appState.sessionDigest?.let { if (heldOnly) it.heldOnly() else it } ?: return
    var expanded by appState.pages.todayExpanded
    ExpandableSection(
        // Named for what it holds. The same title over two cards showing different things is the
        // reading this split exists to prevent - a reader who has learnt what "What happened" means
        // on Insights would carry that meaning to the Portfolio, where it is now narrower.
        title = if (heldOnly) "What happened to your trades" else "What happened",
        icon = Icons.Outlined.Today,
        iconTone = digest.headline?.let { toneColor(it) },
        summaryContent = { Text(digest.headlineLine(), style = MaterialTheme.typography.bodySmall) },
        expandedState = expanded,
        onExpandedChange = { expanded = it },
    ) {
        if (digest.events.isEmpty()) {
            // Said rather than hidden, which is the one place this card parts company with Overdue.
            // "Nothing is late" is the state the app is normally in and a permanent card announcing
            // it would be furniture; "nothing moved this session" is a genuine answer to the
            // question being asked, and its absence would read as the app not having looked.
            Text(
                when {
                    // Narrower than "nothing moved", because on this tab it now is: the market can
                    // have had a busy session that none of the reader's own trades were in, and
                    // saying nothing moved would be the card overstating its own scope.
                    heldOnly -> "None of your trades moved on this session"
                    digest.newCalls > 0 -> "Nothing moved on this session, beyond the new calls above."
                    else -> "Nothing moved on this session."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@ExpandableSection
        }
        BoxWithConstraints {
            // The Overdue card's helper, deliberately not its numbers - see [EventTileMinWidth].
            // Three across at most, so a wide screen spends its width on tiles that fit their
            // contents rather than on more tiles that do not.
            val columns = responsiveColumns(minColumnWidth = EventTileMinWidth, maxColumns = 3)
            Column {
                ResponsiveRows(digest.events, columns, spacing = Space.s) { event, tileModifier ->
                    EventTile(
                        event = event,
                        onOpen = {
                            // A trade opens in the Portfolio and a call in Insights, through the
                            // two entrances every other cross-tab press already uses. Which one is
                            // a property of the event rather than of the tab the card is drawn on,
                            // so the same tile leads to the same place from either side.
                            if (event.kind.held) {
                                appState.openPosition(event.positionId)
                            } else {
                                appState.openCall(event.positionId)
                            }
                        },
                        modifier = tileModifier,
                    )
                }
            }
        }
    }
}

/**
 * The heading's own line: which session, and what it amounted to, each part in its own colour.
 *
 * The card has to inform while folded, and a count of events would not - "4 changes" is a number
 * the reader has to open the card to interpret. Colour is doing the work a second line of prose
 * would otherwise do: green is a target, red a stop, amber a window that ran out, blue a price the
 * market reached, and every one of those already means that everywhere else in the app.
 *
 * Zeros are omitted rather than printed. A session that saw one stop should say so and not sit
 * behind three noughts.
 */
@Composable
private fun SessionDigest.headlineLine(): AnnotatedString {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val parts = buildList {
        if (targets > 0) add(EventTone.GAIN to "$targets ${plural(targets, "target")}")
        if (stops > 0) add(EventTone.LOSS to "$stops ${plural(stops, "stop")}")
        if (expiries > 0) add(EventTone.EXPIRY to "$expiries out of time")
        if (inRange > 0) add(EventTone.MARKET to "$inRange in range")
    }
    // Read outside the builder: each is a composable lookup and the string is plain text.
    val tones = parts.associate { (tone, _) -> tone to toneColor(tone) }
    val sessionDate = remember(date) { AppDates.WeekdaySession.format(date) }
    return buildAnnotatedString {
        withStyle(SpanStyle(color = muted)) { append(sessionDate) }
        parts.forEach { (tone, text) ->
            withStyle(SpanStyle(color = muted)) { append(" · ") }
            withStyle(SpanStyle(color = tones.getValue(tone))) { append(text) }
        }
        if (newCalls > 0) {
            withStyle(SpanStyle(color = muted)) {
                append(" · $newCalls new ${plural(newCalls, "call")}")
                // The sources are the half that says whether a busy session was one channel
                // talking or several agreeing, which is a different fact from the count.
                if (newCallSources > 1) append(" from $newCallSources sources")
            }
        }
        if (parts.isEmpty() && newCalls == 0) {
            withStyle(SpanStyle(color = muted)) { append(" · nothing moved") }
        }
    }
}

/**
 * One event, on three lines and a press.
 *
 * The mark and the ticker take the top line with the arrow against the ticker's end, exactly as the
 * Overdue tile does. Under them what happened gets **a line to itself**, and the figure that
 * qualifies it gets another. Both used to share one line joined by a separator, which put the
 * longest phrase this card can print - "stopped out after target 1" - into competition for width
 * with the one fact that says how the event landed, and on a narrow tile the reader lost the end of
 * one to keep the start of the other.
 *
 * What happened is the coloured part, because it is what the tile is for, and it is the line
 * allowed to **wrap rather than ellipse**. A width is a bet about the font scale and the reader's
 * may not be the default; wrapping is what actually keeps the phrase whole, and `ResponsiveRows`
 * already sizes every card in a row to the tallest, so a tile that takes two lines costs the
 * alignment nothing.
 *
 * The line under it is a trade's return, in the green or red it wears everywhere else - what the
 * user's own money did is the fact a call cannot supply - or, on a call, the channel that printed
 * it, because a stop against the name behind it is the whole point of this app. It is the one line
 * held to a single line with an ellipsis: a long Arabic source name is what should give way here,
 * and it is **absent rather than blank** where an event has neither figure, so a tile never carries
 * an empty row.
 */
@Composable
private fun EventTile(event: DayEvent, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val eventColor = toneColor(event.kind.tone)
    val detail: Pair<String, Color>? = when {
        event.kind.held && event.returnPct != null ->
            formatPercent(event.returnPct) to PriceRole.forReturn(event.returnPct)

        !event.channel.isNullOrBlank() ->
            event.channel to MaterialTheme.colorScheme.onSurfaceVariant

        else -> null
    }
    Card(
        onClick = onOpen,
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                onClick(label = if (event.kind.held) "Open this trade" else "Open this call", action = null)
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        border = cardOutline,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(horizontal = Space.m, vertical = Space.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StockLogo(event.ticker, LogoSize.Row, Modifier.padding(end = Space.s))
                Text(
                    event.ticker,
                    // fill = false so the arrow sits against the end of the ticker rather than out
                    // at the tile's edge, where it reads as unrelated to it.
                    Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowForward,
                    // Named by the press it belongs to, one level up; announcing the glyph as well
                    // would say it twice.
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Space.xs).size(IconSize.Hint),
                )
            }
            Text(
                event.kind.summary,
                style = MaterialTheme.typography.labelSmall,
                color = eventColor,
                // Two lines rather than one, and an ellipsis only past them: this is the phrase the
                // tile exists to carry, and losing its end is losing which way the news went.
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            detail?.let { (text, tone) ->
                Text(
                    text,
                    style = MaterialTheme.typography.labelSmall,
                    color = tone,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Only where there is room for it, which is the app's own answer to that question
            // rather than a second measurement taken here - see LocalWindowWidth, published by the
            // shell exactly so that screens cannot disagree about where the line falls. The cover
            // panel is already spending its width on the phrase above.
            if (LocalWindowWidth.current != WindowWidth.COMPACT) {
                Text(
                    // "called" earns its place: a bare date under an event reads as the day the
                    // event happened, and it is not - the heading above already names that session.
                    // This is the session the call was printed for, which on a card that can carry
                    // a stop from a recommendation three weeks old is the context the rest lacks.
                    "called ${shortDate(event.openedOn)}",
                    style = MaterialTheme.typography.labelSmall,
                    // The role for dates, notes and counts, which is what this is. Everything above
                    // it on the tile is a claim about a price and wears a hue that says so.
                    color = PriceRole.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * The one place a session's news is turned into a colour.
 *
 * Every hue here already means what it means: green is a level the call named as profit, red the
 * level it named as the place to get out, amber a window that closed without either, and blue a
 * price the market reached rather than one a channel chose. Nothing is invented for this card -
 * a fifth hue on a screen that already teaches four would be a fifth thing to learn.
 */
@Composable
private fun toneColor(tone: EventTone): Color = when (tone) {
    EventTone.GAIN -> PriceRole.target
    EventTone.LOSS -> PriceRole.stop
    EventTone.EXPIRY -> extraColors.expired
    EventTone.MARKET -> extraColors.market
}

/** "1 target", "2 targets" - the same rule the session and day counts already follow. */
private fun plural(count: Int, word: String): String = if (count == 1) word else "${word}s"

/**
 * Sized against the **container**, which is much narrower than the device, and that is the trap.
 *
 * `responsiveColumns` measures `BoxWithConstraints`, and by the time the grid is drawn the width has
 * lost the page's 16dp either side, the card's own 16dp either side, and on a wide window the
 * navigation rail as well. Reading it off the device instead gives numbers that are wrong by 64dp
 * on the cover screen and by about 144dp on the Fold - which is exactly the mistake that shipped
 * here, and it collapsed the cover screen to a single full-width tile:
 *
 * | screen | device | container | at 150dp | at 200dp | at 170dp |
 * |---|---|---|---|---|---|
 * | Fold cover | 411 | **347** | 2 x 173 | **1 x 347** | 2 x 173 |
 * | Fold inner | 750 | **606** | 4 x 151 | 3 x 202 | 3 x 202 |
 * | Tablet | 818 | **674** | 4 x 168 | 3 x 225 | 3 x 225 |
 *
 * So 170dp with at most three columns. The **cap is what fixed the wide screens** - the original
 * four columns spent a big screen's surplus on more tiles rather than wider ones, so the unfolded
 * panel truncated harder than the cover did - and the minimum only has to stay under half the
 * narrowest container, which 200 did not. 170 rather than back to 150 is for the widths in between:
 * a 600dp window (456dp of container) takes three cramped 152dp columns at 150 and two roomy 228dp
 * ones at 170.
 *
 * The Overdue card keeps 150dp across four columns and is right to: "6d · 12 Aug · +3.4%" is half
 * the length of "stopped out after target 1". The shared helper stays, because a grid derived from
 * the space actually available is right for both; only the numbers differ, because what has to fit
 * in a column is a property of that column's contents rather than of a house grid.
 */
private val EventTileMinWidth = 170.dp
