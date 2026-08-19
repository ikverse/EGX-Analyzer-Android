package com.ikverse.egxanalyzer.next

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.ikverse.egxanalyzer.model.RuleKind
import com.ikverse.egxanalyzer.model.RuleOrigin
import com.ikverse.egxanalyzer.model.RuleScope
import com.ikverse.egxanalyzer.model.RuleSlot
import com.ikverse.egxanalyzer.model.WordingRule
import com.ikverse.egxanalyzer.ui.AppState
import java.util.UUID

/**
 * Writing or correcting one wording rule.
 *
 * A phrase means nothing without the question it answers, so the slot is chosen before the words:
 * the same sentence can mean "this source is stale" in one decision and nothing at all in another.
 * Scope is the second question and the one that costs money - a rule decided on the device sends
 * nothing, and a rule in the prompt travels with every request.
 */
@Composable
internal fun NextRuleSheet(appState: AppState, page: NextPageState) {
    val colors = LocalNextColors.current
    val editing = page.ruleSheet
    if (editing == null && !page.addingRule) return

    val dismiss = {
        page.ruleSheet = null
        page.addingRule = false
    }

    var phrase by remember(editing?.id) { mutableStateOf(editing?.phrase.orEmpty()) }
    var slot by remember(editing?.id) {
        mutableStateOf(editing?.slot ?: RuleSlot.entries.first())
    }
    var kind by remember(editing?.id) { mutableStateOf(editing?.kind ?: RuleKind.EXCLUDE) }
    var scope by remember(editing?.id) { mutableStateOf(editing?.scope ?: RuleScope.LOCAL) }
    var rejection by remember(editing?.id) { mutableStateOf<String?>(null) }

    NextModal(
        kicker = if (editing == null) "Add a phrase" else "Edit this phrase",
        tone = colors.accent,
        onDismiss = dismiss,
        body = {
            NextField(
                label = "The words as the source writes them",
                value = phrase,
                onValueChange = {
                    phrase = it
                    rejection = null
                },
                numeric = false,
                problem = rejection,
            )
            NextText(
                "Matched with diacritics, tatweel and emoji taken off both sides, and the alef, " +
                    "ya and ta-marbuta spellings folded together — so a phrase typed one way still " +
                    "matches a source that wrote it another.",
                NextType.meta,
                colors.figMuted,
            )

            NextLabel("Which decision it feeds")
            NextChipStrip(
                options = RuleSlot.entries.map(RuleSlot::title),
                selectedIndex = RuleSlot.entries.indexOf(slot),
                onPick = { index -> slot = RuleSlot.entries[index] },
            )
            NextText(slot.explanation, NextType.meta, colors.ink3)

            NextLabel("Include or exclude")
            NextChipStrip(
                options = RuleKind.entries.map(RuleKind::title),
                selectedIndex = RuleKind.entries.indexOf(kind),
                onPick = { index -> kind = RuleKind.entries[index] },
                tone = colors.market,
            )

            NextLabel("Where it applies")
            NextChipStrip(
                options = RuleScope.entries.map(RuleScope::title),
                selectedIndex = RuleScope.entries.indexOf(scope),
                onPick = { index -> scope = RuleScope.entries[index] },
                tone = colors.expired,
            )
            NextText(scope.explanation, NextType.meta, colors.ink3)
        },
        actions = {
            NextButton(
                label = if (editing == null) "Add it" else "Save",
                onClick = {
                    val rule = (editing ?: newRule()).copy(
                        phrase = phrase.trim(),
                        slot = slot,
                        kind = kind,
                        scope = scope,
                    )
                    val refused = appState.saveWordingRule(rule)
                    if (refused == null) dismiss() else rejection = refused.message
                },
                tone = colors.accent,
                fill = NextFill.SOLID,
                enabled = phrase.isNotBlank(),
                modifier = Modifier.weight(1f),
            )
            if (editing != null && editing.origin == RuleOrigin.USER) {
                NextButton(
                    label = "Remove",
                    onClick = {
                        appState.deleteWordingRule(editing)
                        dismiss()
                    },
                    tone = colors.stop,
                    labelColor = colors.stop,
                )
            }
            NextButton("Cancel", dismiss, tone = colors.rule, labelColor = colors.ink3)
        },
    )
}

/** A row of the user's own, which is the only kind this sheet creates. */
private fun newRule(): WordingRule = WordingRule(
    id = UUID.randomUUID().toString(),
    slot = RuleSlot.entries.first(),
    kind = RuleKind.EXCLUDE,
    phrase = "",
    scope = RuleScope.LOCAL,
    origin = RuleOrigin.USER,
)
