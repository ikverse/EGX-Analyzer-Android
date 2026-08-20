package com.ikverse.egxanalyzer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
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
 * Two answers and a footer. Every figure the reader might otherwise want is on the card this opened
 * from, which is why the prompt forbids the model from repeating them - a sheet that restated the
 * entry band and the sessions elapsed would be a second copy of the card, and the reader pressed
 * the button to get something the card cannot tell them.
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
                    Text(call.ticker, style = MaterialTheme.typography.headlineSmall)
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
            // The horizon and the confidence sit together under the name because neither means much
            // alone: "short term" from a model that has just called its own confidence low is a
            // different sentence from the same words at high.
            Text(
                "${opinion.horizon.arabic} · ${opinion.confidence.arabic}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (opinion.headline.isNotBlank()) {
                ArabicText(opinion.headline, MaterialTheme.typography.titleSmall)
            }

            HorizontalDivider()
            OpinionSection("The stock") {
                ArabicText(opinion.outlook, MaterialTheme.typography.bodyMedium)
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
                        "${opinion.model} · asked ${opinion.askedOn}" +
                            (if (opinion.searched) " · with live search" else " · no live news"),
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
                TextButton(onClick = { onAskAgain?.invoke() }, enabled = onAskAgain != null) {
                    Text("Ask again")
                }
            }
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
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Text(
            value,
            style = style,
            color = color,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth(),
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
    val container = when (verdict) {
        StockOpinion.Verdict.BUY_NOW -> MaterialTheme.colorScheme.tertiaryContainer
        StockOpinion.Verdict.WAIT -> extraColors.expiredContainer
        StockOpinion.Verdict.AVOID -> MaterialTheme.colorScheme.errorContainer
    }
    val onContainer = when (verdict) {
        StockOpinion.Verdict.BUY_NOW -> MaterialTheme.colorScheme.onTertiaryContainer
        StockOpinion.Verdict.WAIT -> extraColors.onExpiredContainer
        StockOpinion.Verdict.AVOID -> MaterialTheme.colorScheme.onErrorContainer
    }
    Chip(verdict.arabic, container, onContainer)
}

@Composable
private fun StanceChip(stance: StockOpinion.Stance) {
    val container = when (stance) {
        StockOpinion.Stance.SOUND -> MaterialTheme.colorScheme.tertiaryContainer
        StockOpinion.Stance.RISKY, StockOpinion.Stance.OVERTAKEN -> extraColors.expiredContainer
        StockOpinion.Stance.UNSOUND -> MaterialTheme.colorScheme.errorContainer
    }
    val onContainer = when (stance) {
        StockOpinion.Stance.SOUND -> MaterialTheme.colorScheme.onTertiaryContainer
        StockOpinion.Stance.RISKY, StockOpinion.Stance.OVERTAKEN -> extraColors.onExpiredContainer
        StockOpinion.Stance.UNSOUND -> MaterialTheme.colorScheme.onErrorContainer
    }
    Chip(stance.arabic, container, onContainer)
}

@Composable
private fun Chip(text: String, container: Color, onContainer: Color) {
    Surface(color = container, shape = CircleShape) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = onContainer,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/**
 * What pressing Ask AI costs, before it is spent.
 *
 * Every other paid thing in this app is confirmed before it runs, and this is no different: one
 * press, one billed request. It names the model and whether a search is attached, because those are
 * the two things that change what is charged and what comes back.
 */
@Composable
internal fun AskAiDialog(
    call: ScoredCall,
    model: String,
    searching: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ask about ${call.ticker}?") },
        text = {
            Text(
                "This sends one paid request to $model, asking what it makes of the stock at " +
                    "today's price and what it makes of ${call.channel}'s levels. " +
                    (
                        if (searching) {
                            "A live web search is attached, which costs more and can pick up " +
                                "anything that is on the web, verified or not. "
                        } else {
                            "No live search, so it works from this call's own figures and " +
                                "whatever it learned before it was trained. "
                        }
                        ) +
                    "The answer is saved on this card, so opening it again is free.",
            )
        },
        confirmButton = { Button(onClick = onConfirm) { Text("Ask") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
