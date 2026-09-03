package com.ikverse.egxanalyzer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ikverse.egxanalyzer.model.RuleRejection
import com.ikverse.egxanalyzer.model.RuleKind
import com.ikverse.egxanalyzer.model.RuleOrigin
import com.ikverse.egxanalyzer.model.RuleScope
import com.ikverse.egxanalyzer.model.RuleSlot
import com.ikverse.egxanalyzer.model.WordingRule
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
    var confirmDelete by remember { mutableStateOf<WordingRule?>(null) }
    val rules = appState.ruleSet.all

    Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
        RuleKind.entries.forEach { kind ->
            WordingList(
                kind = kind,
                rules = rules.filter { it.kind == kind },
                onAdd = { editing = blank(kind) },
                onEdit = { editing = it },
                onDelete = { confirmDelete = it },
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

    confirmDelete?.let { rule ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete \"${rule.phrase}\"?") },
            text = {
                Text(
                    if (rule.kind == RuleKind.INCLUDE) {
                        "Messages containing it stop being kept, here and in the prompt."
                    } else {
                        "Messages containing it stop being dropped, here and in the prompt."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    appState.deleteWordingRule(rule)
                    confirmDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Keep") } },
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

    val title = if (kind == RuleKind.INCLUDE) "Included wordings" else "Excluded wordings"
    SectionCard(
        title = title,
        about = infoNote(
            title,
            if (kind == RuleKind.INCLUDE) {
                "A message carrying one of these is analysed whatever else it says, and the model " +
                    "is told to prioritise it."
            } else {
                "A message carrying one of these is dropped before anything is sent, and the model " +
                    "is told to exclude it."
            },
        ),
    ) {
        if (mine.isEmpty()) {
            Text(
                "Nothing added yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        mine.forEach { rule ->
            HorizontalDivider()
            RuleRow(rule, onEdit = { onEdit(rule) }, onDelete = { onDelete(rule) }) { on ->
                onToggle(rule, on)
            }
        }

        OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(IconSize.Inline))
            Spacer(Modifier.width(Space.s))
            Text("Add wording")
        }

        // Out of the way but never hidden: these are load bearing, and one of them misfiring on a
        // newly added channel is exactly the moment someone needs to find them.
        if (shipped.isNotEmpty()) {
            HorizontalDivider()
            TextButton(onClick = { showShipped = !showShipped }) {
                Text(
                    if (showShipped) {
                        "Hide the ${shipped.size} phrases the app ships with"
                    } else {
                        "${shipped.size} phrases the app ships with · " +
                            "${shipped.count(WordingRule::enabled)} on"
                    },
                )
            }
            AnimatedVisibility(showShipped) {
                Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    // One reason per group of shipped wording, printed once rather than under every
                    // row, where it buried the phrases it was meant to explain.
                    shipped.groupBy(WordingRule::note).forEach { (note, group) ->
                        note?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        group.forEach { rule ->
                            RuleRow(rule, onEdit = {}, onDelete = {}) { on -> onToggle(rule, on) }
                        }
                    }
                }
            }
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
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            if (rule.origin == RuleOrigin.BUILT_IN) {
                Icon(
                    Icons.Outlined.Lock,
                    contentDescription = "Built in",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(Space.xs))
            }
            Column {
                Text(
                    rule.phrase,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (rule.enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                rule.note?.takeIf { rule.origin == RuleOrigin.USER }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Switch(checked = rule.enabled, onCheckedChange = onToggle)
        if (rule.origin == RuleOrigin.USER) {
            IconButton(onClick = onEdit) {
                Icon(Icons.Outlined.Edit, contentDescription = "Edit", modifier = Modifier.size(IconSize.Inline))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = "Delete", modifier = Modifier.size(IconSize.Inline))
            }
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                (if (existing) "Edit" else "Add") +
                    if (rule.kind == RuleKind.INCLUDE) " included wording" else " excluded wording",
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                OutlinedTextField(
                    value = phrase,
                    onValueChange = {
                        phrase = it
                        rejection = null
                    },
                    label = { Text("Phrase") },
                    isError = rejection != null,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                rejection?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val refused = onSave(rule.copy(phrase = phrase, note = note.trim().ifBlank { null }))
                if (refused == null) onDismiss() else rejection = refused.message
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
