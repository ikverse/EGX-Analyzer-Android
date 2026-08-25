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
 * Overdue, which is the only thing on that tab asking to be acted on; on Insights it leads, because
 * the record beneath it is reference and this is news. Its open state is shared through
 * [PageState.todayExpanded] for the same reason the digest is: two cards reporting one session that
 * could be folded apart would read as two different cards that happen to agree.
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
internal fun ColumnScope.TodayCard(appState: AppState) {
    val digest = appState.sessionDigest ?: return
    var expanded by appState.pages.todayExpanded
    ExpandableSection(
        title = "What happened",
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
                "Nothing moved on this session" +
                    if (digest.newCalls > 0) ", beyond the new calls above." else ".",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@ExpandableSection
        }
        BoxWithConstraints {
            // The Overdue card's own grid, through the same helper: two tiles across on the cover
            // screen, four on the Fold and the tablet. Two lists of small facts on two tabs laid
            // out by two rules would be a difference the reader has to account for.
            val columns = responsiveColumns(minColumnWidth = EventTileMinWidth, maxColumns = 4)
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
 * One event, in three facts and a press.
 *
 * Modelled on the Overdue tile, deliberately down to the layout: the mark and the ticker on the top
 * line with the arrow against the ticker's end, and everything measured on a line under them that
 * takes the tile's full width. Two grids of small cards on two tabs that looked subtly different
 * would be a difference the reader has to work out is meaningless.
 *
 * What happened is the coloured part, because it is what the tile is for. The channel follows it
 * where the event belongs to a call - a stop against the name that printed it is the whole point of
 * this app - and gives way first when the tile is narrow. A trade carries its return instead, in
 * the green or red it wears everywhere else: what the user's own money did is the fact a call
 * cannot supply.
 */
@Composable
private fun EventTile(event: DayEvent, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val eventColor = toneColor(event.kind.tone)
    val returnColor = PriceRole.forReturn(event.returnPct)
    val meta = buildAnnotatedString {
        withStyle(SpanStyle(color = eventColor)) { append(event.kind.summary) }
        if (event.kind.held && event.returnPct != null) {
            append(" · ")
            withStyle(SpanStyle(color = returnColor)) { append(formatPercent(event.returnPct)) }
        } else if (!event.channel.isNullOrBlank()) {
            append(" · ${event.channel}")
        }
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
                meta,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
 * Narrower than the Overdue tile, and for the reason that tile is 150dp: what has to fit.
 *
 * "stopped out after target 1" is the longest thing said here and it is longer than any overdue
 * line, but it is also the one line that may be ellipsed without losing the tile - the colour has
 * already said which way the news went, and the card behind the press says the rest. The ticker and
 * the mark are what must never wrap, and they need no more room than they do over there.
 */
private val EventTileMinWidth = 150.dp
