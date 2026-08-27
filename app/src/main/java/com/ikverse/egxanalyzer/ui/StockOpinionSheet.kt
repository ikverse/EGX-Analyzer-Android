package com.ikverse.egxanalyzer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StockOpinionSheet(
    call: ScoredCall,
    opinion: StockOpinion,
    /** Null while a re-ask is running, which is what disables the button rather than hiding it. */
    onAskAgain: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .scrollableColumn()
                .padding(horizontal = Space.l)
                .padding(bottom = Space.xl),
            verticalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StockLogo(call.ticker, LogoSize.Header, Modifier.padding(end = Space.s))
                        Text(call.ticker, style = MaterialTheme.typography.headlineSmall)
                    }
                    listOfNotNull(call.companyEnglish, call.companyArabic)
                        .filter(String::isNotBlank)
                        .distinct()
                        .takeIf(List<String>::isNotEmpty)
                        ?.let {
                            Text(
                                it.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                }
                VerdictChip(opinion.verdict)
            }
            // The confidence sits under the name because a verdict does not mean much without it:
            // "buy" from a model that has just called its own confidence low is a different
            // sentence from the same word at high. The horizon joins it **only on an answer with no
            // forecast** - a schema 2 row - because where there is one, three dated spans sit a few
            // lines below and a single holding period above them is the same claim said worse.
            Text(
                listOfNotNull(
                    opinion.horizon.arabic.takeIf { opinion.forecast == null },
                    opinion.confidence.arabic,
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (opinion.headline.isNotBlank()) {
                ArabicText(opinion.headline, MaterialTheme.typography.titleSmall)
            }

            // Absent rather than empty on an answer that carried none - every opinion saved before
            // this shipped, and any later one where the model skipped the section. A heading over
            // nothing would read as the app having lost the paragraph.
            if (opinion.standing.isNotBlank()) {
                HorizontalDivider()
                OpinionSection("How it has traded") {
                    ArabicText(opinion.standing, MaterialTheme.typography.bodyMedium)
                }
            }

            HorizontalDivider()
            OpinionSection("The stock") {
                ArabicText(opinion.outlook, MaterialTheme.typography.bodyMedium)
            }

            opinion.forecast?.let { forecast ->
                HorizontalDivider()
                OpinionSection("Where it goes") {
                    forecast.legs.forEach { ForecastLegRow(it) }
                }
            }

            HorizontalDivider()
            OpinionSection("The recommendation", trailing = { StanceChip(opinion.onTheCall.stance) }) {
                if (opinion.onTheCall.detail.isBlank()) {
                    Text(
                        "The model said nothing about the levels.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    ArabicText(opinion.onTheCall.detail, MaterialTheme.typography.bodyMedium)
                }
                // Under the prose rather than instead of it: the paragraph is the reading of the
                // levels taken together, and these are what each number is worth on its own.
                opinion.onTheCall.checks.forEach { CheckRow(it) }
            }

            // Shown even when it found nothing. An empty list here is a finding - it says the
            // fortnight was quiet - and it only reads as one beside the window it covered, which is
            // why the window rather than [StockOpinion.searched] is what admits the section. An
            // answer given before the window existed carries a zero and is left out: it was
            // searched, but under a prompt that told it to report no news either way, so an empty
            // list under this heading would be this build putting words in its mouth.
            if (opinion.news.isNotEmpty() || opinion.newsWindowDays > 0) {
                HorizontalDivider()
                OpinionSection("What it found") {
                    if (opinion.news.isEmpty()) {
                        Text(
                            opinion.newsWindowDays
                                .takeIf { it > 0 }
                                ?.let { "Nothing published about this company in the last $it days." }
                                ?: "The search turned up nothing about this company.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        opinion.news.forEach { NewsRow(it) }
                    }
                }
            }

            if (opinion.catalysts.isNotEmpty()) {
                HorizontalDivider()
                OpinionSection("What is coming") {
                    opinion.catalysts.forEach { CatalystRow(it) }
                }
            }

            if (opinion.risks.isNotEmpty()) {
                HorizontalDivider()
                OpinionSection("What goes wrong") {
                    opinion.risks.forEach { line ->
                        ArabicText("• $line", MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // Deliberately last and deliberately grey. It is the part that stops a thin answer
            // reading as an authoritative one, and it is not what the reader came for.
            if (opinion.unknowns.isNotEmpty()) {
                HorizontalDivider()
                OpinionSection("What it could not see") {
                    opinion.unknowns.forEach { line ->
                        ArabicText(
                            "• $line",
                            MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${opinion.model} · asked ${opinion.askedOn}" + opinion.searchNote(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // Said plainly, because everything else on the screen behind this sheet is
                    // measured from prices and this is not.
                    Text(
                        "An opinion, not a measurement.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Filled, like the button on the card that first spent money here: a re-ask is a
                // second billed request, and the ring is reserved for reopening what is already
                // paid for - which is this sheet.
                AskAiButton(
                    label = if (onAskAgain == null) "Asking…" else "Ask again",
                    onClick = { onAskAgain?.invoke() },
                    enabled = onAskAgain != null,
                    working = onAskAgain == null,
                    phaseKey = call.ticker,
                )
            }
        }
    }
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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                leg.confidence.arabic,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    color: Color = MaterialTheme.colorScheme.onSurface,
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

@Composable
private fun OpinionSection(
    title: String,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            trailing?.invoke()
        }
        content()
    }
}

/**
 * The answer in one word and one colour.
 *
 * The palette is the app's own and unchanged: green is where the call says to take profit, red
 * where it says to give up, amber is the "out of time, not lost" hue the portfolio already uses.
 * Waiting is neither good nor bad, which is exactly what amber means here.
 */
@Composable
private fun VerdictChip(verdict: StockOpinion.Verdict) {
    val outline = when (verdict) {
        StockOpinion.Verdict.BUY_NOW -> MaterialTheme.colorScheme.tertiary
        StockOpinion.Verdict.WAIT -> extraColors.expired
        StockOpinion.Verdict.AVOID -> MaterialTheme.colorScheme.error
    }
    val ink = when (verdict) {
        StockOpinion.Verdict.BUY_NOW -> MaterialTheme.colorScheme.onTertiaryContainer
        StockOpinion.Verdict.WAIT -> extraColors.onExpiredContainer
        StockOpinion.Verdict.AVOID -> MaterialTheme.colorScheme.onErrorContainer
    }
    OutlinePill(verdict.arabic, outline = outline, textColor = ink)
}

@Composable
private fun StanceChip(stance: StockOpinion.Stance) {
    val outline = when (stance) {
        StockOpinion.Stance.SOUND -> MaterialTheme.colorScheme.tertiary
        StockOpinion.Stance.RISKY, StockOpinion.Stance.OVERTAKEN -> extraColors.expired
        StockOpinion.Stance.UNSOUND -> MaterialTheme.colorScheme.error
    }
    val ink = when (stance) {
        StockOpinion.Stance.SOUND -> MaterialTheme.colorScheme.onTertiaryContainer
        StockOpinion.Stance.RISKY, StockOpinion.Stance.OVERTAKEN -> extraColors.onExpiredContainer
        StockOpinion.Stance.UNSOUND -> MaterialTheme.colorScheme.onErrorContainer
    }
    OutlinePill(stance.arabic, outline = outline, textColor = ink)
}

/**
 * Which way one span points.
 *
 * The same three colours a news item's tone wears, and deliberately so: both answer "good or bad
 * for the price", and a fourth palette on one sheet would be three vocabularies to learn. Sideways
 * takes the outline rather than the amber a waiting verdict wears - amber here means the app is
 * unsure, and a flat forecast is a definite answer that the price goes nowhere.
 */
@Composable
private fun DirectionChip(direction: StockOpinion.Direction) {
    val outline = when (direction) {
        StockOpinion.Direction.UP -> MaterialTheme.colorScheme.tertiary
        StockOpinion.Direction.DOWN -> MaterialTheme.colorScheme.error
        StockOpinion.Direction.SIDEWAYS -> MaterialTheme.colorScheme.outline
    }
    val ink = when (direction) {
        StockOpinion.Direction.UP -> MaterialTheme.colorScheme.onTertiaryContainer
        StockOpinion.Direction.DOWN -> MaterialTheme.colorScheme.onErrorContainer
        StockOpinion.Direction.SIDEWAYS -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    OutlinePill(direction.arabic, outline = outline, textColor = ink)
}

/**
 * What one printed number is worth, in the palette a stance already uses.
 *
 * `FAIR` takes the amber a `RISKY` stance takes, for the same reason: neither is a fault and
 * neither is a recommendation, and the app already means "workable, with something wrong with it"
 * by that hue.
 */
@Composable
private fun RatingChip(rating: StockOpinion.Rating) {
    val outline = when (rating) {
        StockOpinion.Rating.GOOD -> MaterialTheme.colorScheme.tertiary
        StockOpinion.Rating.FAIR -> extraColors.expired
        StockOpinion.Rating.POOR -> MaterialTheme.colorScheme.error
    }
    val ink = when (rating) {
        StockOpinion.Rating.GOOD -> MaterialTheme.colorScheme.onTertiaryContainer
        StockOpinion.Rating.FAIR -> extraColors.onExpiredContainer
        StockOpinion.Rating.POOR -> MaterialTheme.colorScheme.onErrorContainer
    }
    OutlinePill(rating.arabic, outline = outline, textColor = ink)
}

/** What one item means for the price, in the same three colours the rest of the sheet uses. */
@Composable
private fun ToneChip(tone: StockOpinion.Tone) {
    val outline = when (tone) {
        StockOpinion.Tone.BULLISH -> MaterialTheme.colorScheme.tertiary
        StockOpinion.Tone.BEARISH -> MaterialTheme.colorScheme.error
        StockOpinion.Tone.NEUTRAL -> MaterialTheme.colorScheme.outline
    }
    val ink = when (tone) {
        StockOpinion.Tone.BULLISH -> MaterialTheme.colorScheme.onTertiaryContainer
        StockOpinion.Tone.BEARISH -> MaterialTheme.colorScheme.onErrorContainer
        StockOpinion.Tone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    OutlinePill(tone.arabic, outline = outline, textColor = ink)
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ask about ${call.ticker}?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                Text(
                    "One paid question about this stock and what ${call.channel} said about it. " +
                        "The answer is kept on this card, so opening it again later is free.",
                )
                Text(
                    listOfNotNull(
                        model,
                        if (searching) "checks news from the last $newsWindowDays days" else null,
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        // The gradient rather than a plain Button: this is the press that actually spends the
        // money, and it should look like the one that opened the dialog.
        confirmButton = { AskAiButton(label = "Ask", onClick = onConfirm, phaseKey = call.ticker) },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
