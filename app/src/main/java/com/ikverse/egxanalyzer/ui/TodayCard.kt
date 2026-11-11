package com.ikverse.egxanalyzer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ikverse.egxanalyzer.model.DayEvent
import com.ikverse.egxanalyzer.model.DayEventKind
import com.ikverse.egxanalyzer.model.EventTone
import com.ikverse.egxanalyzer.ui.kit.KitRule
import com.ikverse.egxanalyzer.ui.kit.KitTag
import com.ikverse.egxanalyzer.ui.kit.Tile
import com.ikverse.egxanalyzer.ui.theme.palette

/**
 * What the market did to this record on the session just gone, on the Insights tab.
 *
 * The app has always known every fact on this card and has never had a place to say it. A target
 * reached on Tuesday morning reached the reader as a status on a card inside a folded section on a
 * tab they had to choose - so *what happened today*, the one question with a daily rhythm to it,
 * was the question no screen answered.
 *
 * **Insights only.** It sits first on the tab: what changed since the reader last looked is
 * perishable, so it goes above everything that takes scrolling to reach. The Portfolio carried the
 * same card, narrowed to the reader's own trades, until 2026-09-17; `OpenPositionsCard` answers the
 * question that tab actually asks. `SessionDigest.heldOnly()` survives for `SessionDigestNotifier`.
 *
 * **Open, alone among the tiles that hold a list**: it is read once and scrolled past, and a tile
 * that has to be opened before it says anything is a tile nobody opens.
 *
 * **Not narrowed by any filter**: a channel, a stock or a date picked on screen is a view of the
 * record, never a claim about what the market did.
 *
 * **The 5.1 shape since 2026-11-11.** Until then this card was the last pre-5.1 piece on Insights -
 * four grey boxes of counts whose labels wrapped ("hit target / 1"), then a two- or three-across
 * grid of grey blocks, each with a Material arrow and the event as a lowercase line in the figure
 * face. It is now what the source record's Latest calls already is: a strip of counts on the tile
 * itself, then **one row per event** - logo, ticker, a muted line naming who called it and when, and
 * the outcome as a [KitTag] in plain words. One column at every width, so there is no grid to size
 * against the container and no breakpoint to get wrong.
 */
@Composable
internal fun ColumnScope.TodayCard(appState: AppState) {
    val digest = appState.sessionDigest ?: return
    val p = palette
    val counts = remember(digest) {
        fun count(vararg kinds: DayEventKind) = digest.events.count { it.kind in kinds }
        listOf(
            Triple(count(DayEventKind.TRADE_TARGET1, DayEventKind.CALL_TARGET1), "Target 1", EventTone.GAIN),
            Triple(count(DayEventKind.TRADE_TARGET2, DayEventKind.CALL_TARGET2), "Target 2", EventTone.GAIN),
            Triple(digest.stops, "Stopped", EventTone.LOSS),
            Triple(digest.inRange, "In range", EventTone.MARKET),
        )
    }
    Tile(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(
                "${digest.date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH)}'s session",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            Text(
                AppDates.DayMonth.format(digest.date),
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = TabularFigures),
                color = p.muted,
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = Space.m)) {
            counts.forEachIndexed { index, (value, label, tone) ->
                CountCell(
                    value = value,
                    label = label,
                    tone = if (value > 0) toneColor(tone) else p.ink,
                    divided = index > 0,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (digest.events.isEmpty()) {
            // Said rather than hidden: "nothing moved this session" is a genuine answer to the
            // question being asked, and its absence would read as the app not having looked.
            Text(
                "Nothing moved on this session.",
                style = MaterialTheme.typography.bodyMedium,
                color = p.muted,
                modifier = Modifier.padding(top = Space.m),
            )
        } else {
            Column(Modifier.padding(top = Space.m)) {
                digest.events.forEachIndexed { index, event ->
                    KitRule()
                    EventRow(
                        event = event,
                        onOpen = {
                            // A trade opens in the Portfolio and a call in Insights. Which one is a
                            // property of the event rather than of the tab the card is drawn on.
                            if (event.kind.held) {
                                appState.openPosition(event.positionId)
                            } else {
                                appState.openCall(event.positionId)
                            }
                        },
                        modifier = Modifier.staggerIn(index),
                    )
                }
            }
        }
        // The two facts with no count of their own, under everything as a footnote. They sat
        // between the counts and the list before, where they read as a heading for the list.
        val also = buildList {
            if (digest.expiries > 0) add("${digest.expiries} out of time")
            if (digest.newCalls > 0) {
                add(
                    "${digest.newCalls} new ${plural(digest.newCalls, "call")}" +
                        if (digest.newCallSources > 1) " from ${digest.newCallSources} sources" else "",
                )
            }
        }
        if (also.isNotEmpty()) {
            KitRule(Modifier.padding(top = if (digest.events.isEmpty()) Space.m else 0.dp))
            Text(
                also.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = TabularFigures),
                color = p.muted,
                modifier = Modifier.padding(top = Space.s),
            )
        }
    }
}

/**
 * One count in the strip: the figure in the colour its outcome wears, and a one-line label under it.
 *
 * Sitting on the tile rather than in a box of its own, with a hairline before every cell but the
 * first. The labels are short on purpose - "Target 1", not "hit target 1" - because a quarter of the
 * cover screen is about 80dp, and the old wording wrapped onto two lines there.
 */
@Composable
private fun CountCell(value: Int, label: String, tone: Color, divided: Boolean, modifier: Modifier = Modifier) {
    val line = palette.line
    Column(
        modifier
            .then(
                if (divided) {
                    Modifier.drawBehind {
                        drawLine(line, Offset(0f, 0f), Offset(0f, size.height), strokeWidth = 1.dp.toPx())
                    }
                } else {
                    Modifier
                },
            )
            .padding(start = if (divided) Space.s else 0.dp, end = Space.xs),
    ) {
        Text(
            value.toString(),
            style = MaterialTheme.typography.titleLarge.copy(fontFamily = TabularFigures),
            color = tone,
            maxLines = 1,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = palette.muted,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * One event as one row, pressed as a whole: logo, ticker, who and when, and how it landed.
 *
 * The line under the ticker is the source and the session the call was made for - "called 3 Nov",
 * because a bare date under an event reads as the day it happened, and the heading already names
 * that. A held trade says "Your trade" in place of the source, and carries its return under the tag:
 * what the reader's own money did is the fact a call cannot supply. The line is held to one line
 * with an ellipsis, so a long Arabic source name is what gives way.
 *
 * The ticker is **not** a second target here: the trade or call this opens carries the ticker press
 * itself, and a target inside a row this small is one the reader hits by accident.
 */
@Composable
private fun EventRow(event: DayEvent, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val p = palette
    val who = if (event.kind.held) "Your trade" else event.channel?.takeIf { it.isNotBlank() }
    val sub = listOfNotNull(who, "called ${shortDate(event.openedOn)}").joinToString(" · ")
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                role = Role.Button,
                onClickLabel = if (event.kind.held) "Open this trade" else "Open this call",
                onClick = onOpen,
            )
            .padding(vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        StockLogo(event.ticker, LogoSize.Row)
        Column(Modifier.weight(1f)) {
            Text(
                event.ticker,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                sub,
                style = MaterialTheme.typography.labelSmall,
                color = p.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            KitTag(event.kind.tagWord(), tone = toneColor(event.kind.tone))
            val held = event.returnPct.takeIf { event.kind.held }
            if (held != null) {
                RollingText(
                    AnnotatedString(formatPercent(held)),
                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = TabularFigures),
                    color = PriceRole.forReturn(held),
                    modifier = Modifier.padding(top = Space.xs),
                )
            }
        }
    }
}

/**
 * The event in the plain words every other 5.1 status uses ("Target 1 reached", "Ran out of time").
 * Only the screen says it this way: [DayEventKind.summary] stays the notification's wording.
 */
private fun DayEventKind.tagWord(): String = when (this) {
    DayEventKind.TRADE_TARGET2, DayEventKind.CALL_TARGET2 -> "Target 2 reached"
    DayEventKind.TRADE_TARGET1, DayEventKind.CALL_TARGET1 -> "Target 1 reached"
    DayEventKind.TRADE_STOPPED_AFTER_TARGET1, DayEventKind.CALL_STOPPED_AFTER_TARGET1 -> "Stopped after target 1"
    DayEventKind.TRADE_STOPPED, DayEventKind.CALL_STOPPED -> "Stopped out"
    DayEventKind.TRADE_RAN_OUT -> "Ran out of time"
    DayEventKind.CALL_IN_RANGE -> "Came into range"
}

/**
 * The one place a session's news is turned into a colour - the same four the status tags use
 * everywhere else: gain for a target, loss for a stop, muted for a window that closed, and the
 * running colour for a price the market reached.
 */
@Composable
private fun toneColor(tone: EventTone): Color = when (tone) {
    EventTone.GAIN -> palette.gain
    EventTone.LOSS -> palette.loss
    EventTone.EXPIRY -> palette.muted
    EventTone.MARKET -> palette.heroInk
}

/** "1 call", "2 calls". */
private fun plural(count: Int, word: String): String = if (count == 1) word else "${word}s"
