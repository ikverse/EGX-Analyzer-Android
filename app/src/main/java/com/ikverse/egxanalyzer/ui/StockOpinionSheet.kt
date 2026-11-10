package com.ikverse.egxanalyzer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.ikverse.egxanalyzer.ui.kit.Kit
import com.ikverse.egxanalyzer.ui.kit.KitSheet
import com.ikverse.egxanalyzer.ui.kit.LocalFoldPosture
import com.ikverse.egxanalyzer.ui.kit.Tile
import com.ikverse.egxanalyzer.ui.kit.TileTone
import com.ikverse.egxanalyzer.ui.theme.palette

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
import com.ikverse.egxanalyzer.model.ScoredCall
import com.ikverse.egxanalyzer.model.StockOpinion
import com.ikverse.egxanalyzer.ui.theme.extraColors

/**
 * What the model made of one stock and the call on it.
 *
 * Four judgements in the order they are read - what the stock has done, what the business is, where
 * it goes, what the call is worth - then what they rest on, then a footer.
 *
 * **Three of those four are new, and the sheet is the reason as much as the prompt is.** The answer
 * used to be one paragraph about the stock and one about the levels, and the reader's complaint was
 * that every stock came back the same. A single free-text field is what a model fills the same way
 * twice; four fields with different questions behind them cannot be, and the two laid out as lists
 * - the three spans, the five printed numbers - are the two the reader can compare across stocks at
 * a glance without reading a word.
 *
 * The lists below them are unchanged: the news a searched request actually found, dated and
 * attributed, what is scheduled ahead, and what goes wrong. Listed rather than written into the
 * prose, because a headline is only worth anything beside its date and prose hides the date.
 *
 * The answer is Arabic and reads right to left; the section headings stay English, exactly as an
 * English label already sits over an Arabic company name everywhere else in the app.
 */
@Composable
internal fun StockOpinionSheet(
    call: ScoredCall,
    opinion: StockOpinion,
    /** Null while a re-ask is running, which is what disables the button rather than hiding it. */
    onAskAgain: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val p = palette
    KitSheet(onDismiss = onDismiss, posture = LocalFoldPosture.current, title = "AI answer about ${call.ticker}") {
        // The lilac head: the stock, the verdict, how sure, and the answer in one line.
        Tile(Modifier.fillMaxWidth(), tone = TileTone.TILE2) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StockLogo(call.ticker, LogoSize.Header, Modifier.padding(end = Space.m))
                Column(Modifier.weight(1f)) {
                    Text(call.ticker, style = MaterialTheme.typography.headlineMedium)
                    listOfNotNull(call.companyEnglish, call.companyArabic)
                        .filter(String::isNotBlank)
                        .distinct()
                        .takeIf(List<String>::isNotEmpty)
                        ?.let { Text(it.joinToString(" · "), style = MaterialTheme.typography.bodySmall) }
                }
                VerdictChip(opinion.verdict)
            }
            // The confidence sits under the name because a verdict does not mean much without it.
            // The horizon joins it **only on an answer with no forecast**, because where there is
            // one, three dated spans sit below and a single holding period is the same claim said worse.
            ArabicText(
                listOfNotNull(
                    opinion.horizon.arabic.takeIf { opinion.forecast == null },
                    opinion.confidence.arabic,
                ).joinToString(" · "),
                MaterialTheme.typography.labelLarge,
                color = LocalContentColor.current.copy(alpha = 0.75f),
                modifier = Modifier.fillMaxWidth().padding(top = Space.m),
            )
            if (opinion.headline.isNotBlank()) {
                ArabicText(
                    opinion.headline,
                    MaterialTheme.typography.titleLarge,
                    color = LocalContentColor.current,
                    modifier = Modifier.fillMaxWidth().padding(top = Space.xs),
                )
            }
        }

        // Absent rather than empty on an answer that carried none - a heading over nothing would
        // read as the app having lost the paragraph.
        if (opinion.standing.isNotBlank()) {
            OpinionSection("How it has traded") {
                ArabicText(opinion.standing, MaterialTheme.typography.bodyMedium)
            }
        }
        OpinionSection("The stock") {
            ArabicText(opinion.outlook, MaterialTheme.typography.bodyMedium)
        }
        opinion.forecast?.let { forecast ->
            OpinionSection("Where it goes") {
                forecast.legs.forEach { ForecastLegRow(it) }
            }
        }
        OpinionSection("The recommendation", trailing = { StanceChip(opinion.onTheCall.stance) }) {
            if (opinion.onTheCall.detail.isBlank()) {
                Text("The model said nothing about the levels.", style = MaterialTheme.typography.bodySmall, color = p.muted)
            } else {
                ArabicText(opinion.onTheCall.detail, MaterialTheme.typography.bodyMedium)
            }
            // Under the prose rather than instead of it: the paragraph is the reading of the
            // levels taken together, and these are what each number is worth on its own.
            opinion.onTheCall.checks.forEach { CheckRow(it) }
        }

        // Shown even when it found nothing: an empty list here is a finding, and it only reads as
        // one beside the window it covered. An answer from before the window existed is left out.
        if (opinion.news.isNotEmpty() || opinion.newsWindowDays > 0) {
            OpinionSection(
                "What it found" + opinion.newsWindowDays.takeIf { it > 0 }?.let { " · last $it days" }.orEmpty(),
            ) {
                if (opinion.news.isEmpty()) {
                    Text(
                        opinion.newsWindowDays
                            .takeIf { it > 0 }
                            ?.let { "Nothing published about this company in the last $it days." }
                            ?: "The search turned up nothing about this company.",
                        style = MaterialTheme.typography.bodySmall,
                        color = p.muted,
                    )
                } else {
                    opinion.news.forEach { NewsRow(it) }
                }
            }
        }

        // What is coming and what goes wrong side by side, sky and coral, when there are both.
        val coming: (@Composable (Modifier) -> Unit)? = opinion.catalysts.takeIf { it.isNotEmpty() }?.let { items ->
            { m ->
                Tile(m, tone = TileTone.TILE3) {
                    SectionHeading("What is coming")
                    items.forEach { CatalystRow(it) }
                }
            }
        }
        val wrong: (@Composable (Modifier) -> Unit)? = opinion.risks.takeIf { it.isNotEmpty() }?.let { items ->
            { m ->
                Tile(m, tone = TileTone.ALERT) {
                    SectionHeading("What goes wrong")
                    items.forEach { ArabicText("• $it", MaterialTheme.typography.bodyMedium, color = LocalContentColor.current) }
                }
            }
        }
        if (coming != null && wrong != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Kit.TileGap)) {
                coming(Modifier.weight(1f))
                wrong(Modifier.weight(1f))
            }
        } else {
            coming?.invoke(Modifier.fillMaxWidth())
            wrong?.invoke(Modifier.fillMaxWidth())
        }

        // Deliberately last and deliberately quiet. It is the part that stops a thin answer reading
        // as an authoritative one, and it is not what the reader came for.
        if (opinion.unknowns.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth().dashedOutline(p.faint).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                SectionHeading("What it could not see")
                opinion.unknowns.forEach { ArabicText("• $it", MaterialTheme.typography.bodySmall, color = p.muted) }
            }
        }

        Row(Modifier.padding(horizontal = Space.xs), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${opinion.model} · asked ${opinion.askedOn}" + opinion.searchNote(),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = TabularFigures),
                    color = p.muted,
                )
                // Said plainly, because everything else on the screen behind this sheet is measured
                // from prices and this is not.
                Text("An opinion, not a measurement.", style = MaterialTheme.typography.labelSmall, color = p.muted)
            }
            // A re-ask is a second billed request, so it is the filled Ask AI pill.
            AskAiButton(
                label = if (onAskAgain == null) "Asking…" else "Ask again",
                onClick = { onAskAgain?.invoke() },
                enabled = onAskAgain != null,
                working = onAskAgain == null,
            )
        }
    }
}

/** A tile section's heading, tracked out over what it holds. */
@Composable
private fun SectionHeading(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = LocalContentColor.current.copy(alpha = 0.72f),
        modifier = Modifier.padding(bottom = Space.xs),
    )
}

/**
 * How the answer was reached, in the footer.
 *
 * The window is named rather than only the fact of a search. "With live search" said nothing about
 * whether the model looked at this fortnight or at whatever it happened to find, and the reader has
 * to know which before deciding what an empty news list means.
 */
private fun StockOpinion.searchNote(): String = when {
    !searched -> " · no live news"
    newsWindowDays > 0 -> " · searched the last $newsWindowDays days"
    else -> " · with live search"
}

/**
 * One thing the search found.
 *
 * The date and the source sit under the headline in grey, and they are the point of the row. A
 * headline with neither is a rumour; with both it is something the reader can go and check, which
 * is the only basis on which any of this is worth paying for.
 */
@Composable
private fun NewsRow(item: StockOpinion.NewsItem) {
    Column(
        Modifier.padding(bottom = Space.s),
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            ArabicText(
                item.headline,
                MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            ToneChip(item.tone)
        }
        Text(
            listOf(item.date, item.source).filter(String::isNotBlank).joinToString(" · ")
                .ifBlank { "undated, unattributed" },
            style = MaterialTheme.typography.labelSmall,
            color = palette.muted,
        )
    }
}

/**
 * One span's reading: what it is, how sure, which way, and why.
 *
 * The span is named twice - the Arabic label a reader recognises, and the window in English
 * underneath. "Medium term" is a phrase every market column uses and none of them define, and a
 * direction over an unstated span is a forecast about nothing.
 *
 * The reason goes under the row rather than beside it. Three columns of Arabic prose is what this
 * looked like first and it does not survive a 411dp cover screen: two sentences in a third of the
 * width wrap to six lines, and the three legs stop being comparable at a glance, which is the whole
 * reason they are laid out as a list.
 */
@Composable
private fun ForecastLegRow(leg: StockOpinion.Forecast.Leg) {
    Column(
        Modifier.padding(bottom = Space.s),
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(leg.span.arabic, style = MaterialTheme.typography.titleSmall)
                Text(
                    leg.span.window,
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.muted,
                )
            }
            Text(
                leg.confidence.arabic,
                style = MaterialTheme.typography.labelSmall,
                color = palette.muted,
                modifier = Modifier.padding(end = Space.s),
            )
            DirectionChip(leg.direction)
        }
        if (leg.why.isNotBlank()) {
            ArabicText(leg.why, MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * One of the printed numbers and what it is worth.
 *
 * The item's name is English and the note is Arabic, which is the split every other section here
 * already makes - the heading says what is being judged, the answer is the answer. The note is
 * where a figure belongs: this is the one part of the sheet the reader opened to have the numbers
 * on the card behind it read back to them.
 */
@Composable
private fun CheckRow(check: StockOpinion.Check) {
    Column(
        Modifier.padding(bottom = Space.s),
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                check.item.label,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            RatingChip(check.rating)
        }
        if (check.note.isNotBlank()) {
            ArabicText(check.note, MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CatalystRow(item: StockOpinion.Catalyst) {
    Column(
        Modifier.padding(bottom = Space.s),
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        ArabicText(item.what, MaterialTheme.typography.bodyMedium)
        listOf(item.on, item.source)
            .filter(String::isNotBlank)
            .takeIf(List<String>::isNotEmpty)
            ?.let {
                Text(
                    it.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.muted,
                )
            }
    }
}

/**
 * Arabic body text, laid out right to left.
 *
 * The layout direction is flipped rather than only the alignment: a line mixing Arabic with a
 * ticker or a price - which every one of these does - puts its punctuation on the wrong end under a
 * left-to-right layout, however it is aligned.
 */
@Composable
private fun ArabicText(
    value: String,
    style: androidx.compose.ui.text.TextStyle,
    color: Color = LocalContentColor.current,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Text(
            value,
            style = style,
            color = color,
            textAlign = TextAlign.Start,
            modifier = modifier,
        )
    }
}

/** One white tile of the answer, with its heading and an optional chip on the heading's line. */
@Composable
private fun OpinionSection(
    title: String,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Tile(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { SectionHeading(title) }
            trailing?.invoke()
        }
        Column(verticalArrangement = Arrangement.spacedBy(Space.s), content = content)
    }
}

/**
 * The answer in one word, on a filled capsule. Lime is where the call says to take it, coral
 * where it says to keep away, sunflower is waiting - neither good nor bad.
 */
@Composable
private fun VerdictChip(verdict: StockOpinion.Verdict) {
    val p = palette
    FillTag(
        verdict.arabic,
        when (verdict) {
            StockOpinion.Verdict.BUY_NOW -> p.highlight
            StockOpinion.Verdict.WAIT -> p.tile1
            StockOpinion.Verdict.AVOID -> p.alert
        },
        large = true,
    )
}

@Composable
private fun StanceChip(stance: StockOpinion.Stance) {
    val p = palette
    FillTag(
        stance.arabic,
        when (stance) {
            StockOpinion.Stance.SOUND -> p.tile3
            StockOpinion.Stance.RISKY, StockOpinion.Stance.OVERTAKEN -> p.tile1
            StockOpinion.Stance.UNSOUND -> p.alert
        },
    )
}

/** Which way one span points: lime up, coral down, the ground for flat - a definite answer too. */
@Composable
private fun DirectionChip(direction: StockOpinion.Direction) {
    val p = palette
    FillTag(
        direction.arabic,
        when (direction) {
            StockOpinion.Direction.UP -> p.highlight
            StockOpinion.Direction.DOWN -> p.alert
            StockOpinion.Direction.SIDEWAYS -> p.ground
        },
    )
}

/** What one printed number is worth: lime, sunflower for workable with a fault, coral for poor. */
@Composable
private fun RatingChip(rating: StockOpinion.Rating) {
    val p = palette
    FillTag(
        rating.arabic,
        when (rating) {
            StockOpinion.Rating.GOOD -> p.highlight
            StockOpinion.Rating.FAIR -> p.tile1
            StockOpinion.Rating.POOR -> p.alert
        },
    )
}

/** What one news item means for the price, as a dot in the gain, loss or neutral colour. */
@Composable
private fun ToneChip(tone: StockOpinion.Tone) {
    val p = palette
    Box(
        Modifier
            .padding(start = Space.s, top = 6.dp)
            .size(10.dp)
            .clip(CircleShape)
            .background(
                when (tone) {
                    StockOpinion.Tone.BULLISH -> p.gain
                    StockOpinion.Tone.BEARISH -> p.loss
                    StockOpinion.Tone.NEUTRAL -> p.faint
                },
            )
            .semantics { contentDescription = tone.arabic },
    )
}

/** A word on a filled capsule, in the tile ink every bright fill takes. */
@Composable
private fun FillTag(text: String, fill: Color, large: Boolean = false) {
    Box(
        Modifier
            .clip(Kit.Capsule)
            .background(fill)
            .padding(horizontal = if (large) 14.dp else 10.dp, vertical = if (large) 7.dp else 3.dp),
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Text(
                text,
                style = if (large) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelMedium,
                color = palette.onTile,
                maxLines = 1,
            )
        }
    }
}

/**
 * What pressing Ask AI costs, before it is spent.
 *
 * Every other paid thing in this app is confirmed before it runs, and this is no different: one
 * press, one billed request.
 *
 * **Two sentences and a footnote**, where it used to be a paragraph. What the reader has to decide
 * is whether to spend one request, and the old text spent most of its length on things that do not
 * change that answer - what a live search can pick up, what the model works from without one, which
 * of this call's figures travel with the question. A confirmation nobody finishes reading is a
 * confirmation that has stopped confirming anything.
 *
 * The model and the search window survive, in grey underneath. They are what the bill and the
 * answer actually depend on, and they are facts rather than explanation - which is exactly what a
 * footnote is for.
 */
@Composable
internal fun AskAiDialog(
    call: ScoredCall,
    model: String,
    searching: Boolean,
    /** The lookback a searched request would be given, in days. */
    newsWindowDays: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    // The kit's dialog since 5.0, with the model and the news window kept as its footnote. The
    // confirming button is the one that spends the money; nothing is sent until it is pressed.
    com.ikverse.egxanalyzer.ui.kit.KitDialog(
        title = "Ask about ${call.ticker}?",
        text = "One paid question about this stock and what ${call.channel} said about it. " +
            "The answer is kept on this card, so opening it again later is free.",
        confirmLabel = "Ask",
        onConfirm = onConfirm,
        dismissLabel = "Cancel",
        onDismiss = onDismiss,
        posture = LocalFoldPosture.current,
        glyph = "✦",
    ) {
        Text(
            listOfNotNull(
                model,
                if (searching) "checks news from the last $newsWindowDays days" else null,
            ).joinToString(" · "),
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = TabularFigures),
            color = palette.muted,
            modifier = Modifier.padding(top = Space.s),
        )
    }
}
