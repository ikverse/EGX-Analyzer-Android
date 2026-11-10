package com.ikverse.egxanalyzer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.ikverse.egxanalyzer.model.RuleKind
import com.ikverse.egxanalyzer.model.RuleOrigin
import com.ikverse.egxanalyzer.model.RuleRejection
import com.ikverse.egxanalyzer.model.RuleScope
import com.ikverse.egxanalyzer.model.RuleSlot
import com.ikverse.egxanalyzer.model.WordingRule
import com.ikverse.egxanalyzer.ui.kit.KitButton
import com.ikverse.egxanalyzer.ui.kit.KitDialog
import com.ikverse.egxanalyzer.ui.kit.KitExpandable
import com.ikverse.egxanalyzer.ui.kit.KitIconButton
import com.ikverse.egxanalyzer.ui.kit.KitRule
import com.ikverse.egxanalyzer.ui.kit.KitSwitch
import com.ikverse.egxanalyzer.ui.kit.KitTextField
import com.ikverse.egxanalyzer.ui.kit.LocalFoldPosture
import com.ikverse.egxanalyzer.ui.kit.Tile
import com.ikverse.egxanalyzer.ui.kit.TileTone
import java.util.UUID

/**
 * The wording the app recognises: two lists, and nothing else to decide.
 *
 * An earlier draft asked which of nine sections a phrase belonged to, whether it was settled here
 * or sent to the model, and put twenty-one shipped phrases in front of the ones you wrote. Adding a
 * phrase is one thought, not four - so it is one field, applied both here and in the prompt, and
 * the shipped wording waits behind a line until it is wanted.
 */
@Composable
internal fun AnalysisRulesSection(appState: AppState) {
    var editing by remember { mutableStateOf<WordingRule?>(null) }
    val rules = appState.ruleSet.all

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        RuleKind.entries.forEach { kind ->
            WordingList(
                kind = kind,
                rules = rules.filter { it.kind == kind },
                onAdd = { editing = blank(kind) },
                onEdit = { editing = it },
                onDelete = { appState.deleteWordingRule(it) },
                onToggle = { rule, on -> appState.setWordingRuleEnabled(rule, on) },
            )
        }
    }

    editing?.let { rule ->
        RuleEditor(
            rule = rule,
            existing = appState.wordingRules.any { it.id == rule.id },
            onSave = { appState.saveWordingRule(it) },
            onDismiss = { editing = null },
        )
    }
}

/**
 * A new phrase, with the decisions already made.
 *
 * Which list it was added from settles include or exclude, and that settles which instruction in
 * the prompt carries it. Both places, because a phrase worth naming is worth applying wherever the
 * judgement happens.
 */
private fun blank(kind: RuleKind) = WordingRule(
    id = UUID.randomUUID().toString(),
    slot = if (kind == RuleKind.INCLUDE) RuleSlot.SOURCE_KEEP else RuleSlot.SOURCE_DROP,
    kind = kind,
    phrase = "",
    scope = RuleScope.BOTH,
)

/**
 * How the filter works, for the heading above whatever draws these lists.
 *
 * It was a card of its own at the top of this section - a tinted block of prose that every visit to
 * the wording lists had to be scrolled past, to say something that is true once. Exported rather
 * than drawn here because the heading it belongs under is Settings' "Wording" group, and a section
 * explaining itself from inside its own content is the shape being removed.
 */
internal val WordingFlowNote = infoNote(
    "How a message is judged",
    "A message's text is read on this device first: an excluded phrase drops it before anything " +
        "is sent, an included phrase keeps it whatever else it says. What is left goes to the " +
        "model, and your wording goes with it.",
    "Matching ignores diacritics, emoji and the spelling variants of ا, ي and ة, so one phrase " +
        "covers the ways a channel writes it.",
)

@Composable
private fun WordingList(
    kind: RuleKind,
    rules: List<WordingRule>,
    onAdd: () -> Unit,
    onEdit: (WordingRule) -> Unit,
    onDelete: (WordingRule) -> Unit,
    onToggle: (WordingRule, Boolean) -> Unit,
) {
    val mine = rules.filter { it.origin == RuleOrigin.USER }
    val shipped = rules.filter { it.origin == RuleOrigin.BUILT_IN }
    var showShipped by remember { mutableStateOf(false) }
    val include = kind == RuleKind.INCLUDE
    val title = if (include) "Included wordings" else "Excluded wordings"

    // Lime for what keeps a message, coral for what drops one: the two lists are told apart by
    // their tile before a word on them is read.
    Tile(Modifier.fillMaxWidth(), tone = if (include) TileTone.HIGHLIGHT else TileTone.ALERT) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                Text(
                    if (include) "a message must say one" else "a message saying one is skipped",
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalContentColor.current.copy(alpha = 0.78f),
                )
            }
            InfoButton(
                infoNote(
                    title,
                    if (include) {
                        "A message carrying one of these is analysed whatever else it says, and the model " +
                            "is told to prioritise it."
                    } else {
                        "A message carrying one of these is dropped before anything is sent, and the model " +
                            "is told to exclude it."
                    },
                ),
            )
        }
        Column(Modifier.padding(top = Space.s)) {
            if (mine.isEmpty()) {
                Text(
                    "Nothing added yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalContentColor.current.copy(alpha = 0.78f),
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }
            mine.forEach { rule ->
                KitRule(color = LocalContentColor.current.copy(alpha = 0.14f))
                RuleRow(rule, onEdit = { onEdit(rule) }, onDelete = { onDelete(rule) }) { on -> onToggle(rule, on) }
            }

            // Out of the way but never hidden: these are load bearing, and one of them misfiring on
            // a newly added channel is exactly the moment someone needs to find them.
            if (shipped.isNotEmpty()) {
                KitExpandable(
                    title = if (showShipped) {
                        "The ${shipped.size} phrases the app ships with"
                    } else {
                        "${shipped.size} phrases the app ships with · ${shipped.count(WordingRule::enabled)} on"
                    },
                    expanded = showShipped,
                    onExpandedChange = { showShipped = it },
                    modifier = Modifier.padding(top = Space.s),
                    fill = LocalContentColor.current.copy(alpha = 0.08f),
                ) {
                    // One reason per group of shipped wording, printed once rather than under every
                    // row, where it buried the phrases it was meant to explain.
                    shipped.groupBy(WordingRule::note).forEach { (note, group) ->
                        note?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = LocalContentColor.current.copy(alpha = 0.72f))
                        }
                        group.forEach { rule ->
                            RuleRow(rule, onEdit = {}, onDelete = {}) { on -> onToggle(rule, on) }
                        }
                    }
                }
            }

            KitButton(
                "Add wording",
                onClick = onAdd,
                modifier = Modifier.fillMaxWidth().padding(top = Space.s),
                icon = Icons.Outlined.Add,
            )
        }
    }
}

@Composable
private fun RuleRow(
    rule: WordingRule,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    val quiet = LocalContentColor.current.copy(alpha = 0.72f)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (rule.origin == RuleOrigin.BUILT_IN) {
            Icon(Icons.Outlined.Lock, contentDescription = "Built in", tint = quiet, modifier = Modifier.size(14.dp))
        }
        Column(Modifier.weight(1f)) {
            // Right to left whatever the phone's language: these are Arabic phrases, and set left
            // to right they read from the wrong end.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Text(
                    rule.phrase,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (rule.enabled) LocalContentColor.current else quiet,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                listOfNotNull(
                    if (rule.origin == RuleOrigin.USER) "yours" else null,
                    rule.note?.takeIf { rule.origin == RuleOrigin.USER },
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = quiet,
            )
        }
        KitSwitch(checked = rule.enabled, onCheckedChange = onToggle, label = rule.phrase)
        if (rule.origin == RuleOrigin.USER) {
            KitIconButton(Icons.Outlined.Edit, "Edit", onClick = onEdit, size = 36.dp)
            KitIconButton(Icons.Outlined.Delete, "Delete", onClick = onDelete, size = 36.dp)
        }
    }
}

@Composable
private fun RuleEditor(
    rule: WordingRule,
    existing: Boolean,
    onSave: (WordingRule) -> RuleRejection?,
    onDismiss: () -> Unit,
) {
    var phrase by remember(rule.id) { mutableStateOf(rule.phrase) }
    var note by remember(rule.id) { mutableStateOf(rule.note.orEmpty()) }
    var rejection by remember(rule.id) { mutableStateOf<String?>(null) }
    val include = rule.kind == RuleKind.INCLUDE

    KitDialog(
        title = (if (existing) "Edit" else "Add") + if (include) " included wording" else " excluded wording",
        text = if (include) "A message saying this is kept and analysed." else "A message saying this is skipped before anything is sent.",
        confirmLabel = "Save",
        onConfirm = {
            val refused = onSave(rule.copy(phrase = phrase, note = note.trim().ifBlank { null }))
            if (refused == null) onDismiss() else rejection = refused.message
        },
        dismissLabel = "Cancel",
        onDismiss = onDismiss,
        posture = LocalFoldPosture.current,
        tone = if (include) TileTone.HIGHLIGHT else TileTone.ALERT,
        glyph = if (include) "+" else "−",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
            KitTextField(
                value = phrase,
                onValueChange = {
                    phrase = it
                    rejection = null
                },
                label = "Phrase",
                modifier = Modifier.fillMaxWidth(),
                error = rejection,
            )
            KitTextField(
                value = note,
                onValueChange = { note = it },
                label = "Note (optional)",
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
