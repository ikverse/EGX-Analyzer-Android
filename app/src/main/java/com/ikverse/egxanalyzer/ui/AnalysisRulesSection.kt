package com.ikverse.egxanalyzer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ikverse.egxanalyzer.model.RuleKind
import com.ikverse.egxanalyzer.model.RuleOrigin
import com.ikverse.egxanalyzer.model.RuleScope
import com.ikverse.egxanalyzer.model.RuleSlot
import com.ikverse.egxanalyzer.model.WordingRule
import java.util.UUID

/**
 * The wording the app recognises, and what recognising it does.
 *
 * Written to be read by someone who has just added a channel and found it phrases things
 * differently: the flow is explained at the top, every group says which decision it feeds, and
 * every row says whether it is settled here or sent to the model. Adding a channel used to mean
 * editing Kotlin.
 */
@Composable
internal fun AnalysisRulesSection(appState: AppState) {
    var editing by remember { mutableStateOf<WordingRule?>(null) }
    var confirmDelete by remember { mutableStateOf<WordingRule?>(null) }
    val rules = appState.ruleSet

    Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
        FlowExplainer()

        RuleSlot.entries.forEach { slot ->
            SlotGroup(
                slot = slot,
                rules = rules.of(slot),
                onAdd = {
                    editing = WordingRule(
                        id = UUID.randomUUID().toString(),
                        slot = slot,
                        kind = if (slot == RuleSlot.SOURCE_KEEP) RuleKind.INCLUDE else RuleKind.EXCLUDE,
                        phrase = "",
                        scope = RuleScope.LOCAL,
                    )
                },
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
            onDismiss = { editing = null },
            onSave = { appState.saveWordingRule(it) },
            onSaved = { editing = null },
        )
    }

    confirmDelete?.let { rule ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete \"${rule.phrase}\"?") },
            text = { Text("Sources containing it stop being ${rule.slot.title.lowercase()}.") },
            confirmButton = {
                TextButton(onClick = {
                    appState.deleteWordingRule(rule)
                    confirmDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Keep") }
            },
        )
    }
}

/** What happens to a message, in the order it happens, so the groups below have somewhere to sit. */
@Composable
private fun FlowExplainer() {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    ) {
        Column(Modifier.padding(Space.m), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Text("How a message is judged", fontWeight = FontWeight.Bold)
            Step("1", "Its text is read on this device, and these rules decide what can be settled here.")
            Step("2", "Anything they drop never leaves the phone, and never costs a request.")
            Step("3", "What is left goes to the model, along with the rules marked for the prompt.")
            Step("4", "What comes back is scored against real prices.")
            Text(
                "Phrases are matched loosely: spelling variants of ا, ي and ة, diacritics and " +
                    "emoji are ignored, so one phrase covers the ways a channel writes it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Space.xs),
            )
        }
    }
}

@Composable
private fun Step(number: String, text: String) {
    Row {
        Text(
            number,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(Space.s))
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SlotGroup(
    slot: RuleSlot,
    rules: List<WordingRule>,
    onAdd: () -> Unit,
    onEdit: (WordingRule) -> Unit,
    onDelete: (WordingRule) -> Unit,
    onToggle: (WordingRule, Boolean) -> Unit,
) {
    // Every shipped rule in a group carries the same reason, because the reason is what the group
    // is for. Printed once here rather than under all twelve rows, where it buried the phrases it
    // was meant to explain.
    val sharedNote = rules.mapNotNull(WordingRule::note).distinct().singleOrNull()
        ?.takeIf { note -> rules.all { it.note == null || it.note == note } }
    SectionCard(title = slot.title) {
        Text(
            slot.explanation,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        sharedNote?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (rules.isEmpty()) {
            Text(
                "Nothing here yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        rules.forEach { rule ->
            HorizontalDivider()
            RuleRow(
                rule = rule,
                // Suppressed when the group already says it: one row differing is worth reading,
                // twelve rows agreeing is not.
                showNote = rule.note != null && rule.note != sharedNote,
                onEdit = { onEdit(rule) },
                onDelete = { onDelete(rule) },
                onToggle = { on -> onToggle(rule, on) },
            )
        }
        OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(IconSize.Inline))
            Spacer(Modifier.width(Space.s))
            Text("Add wording")
        }
    }
}

@Composable
private fun RuleRow(
    rule: WordingRule,
    showNote: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (rule.origin == RuleOrigin.BUILT_IN) {
                    Icon(
                        Icons.Outlined.Lock,
                        contentDescription = "Built in",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(Space.xs))
                }
                Text(
                    rule.phrase,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (rule.enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Text(
                "${rule.kind.title} · ${rule.scope.title}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Why this rule in particular exists, when the group's own line does not cover it.
            rule.note?.takeIf { showNote }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
    onDismiss: () -> Unit,
    onSave: (WordingRule) -> com.ikverse.egxanalyzer.data.RuleRejection?,
    onSaved: () -> Unit,
) {
    var phrase by remember(rule.id) { mutableStateOf(rule.phrase) }
    var kind by remember(rule.id) { mutableStateOf(rule.kind) }
    var scope by remember(rule.id) { mutableStateOf(rule.scope) }
    var note by remember(rule.id) { mutableStateOf(rule.note.orEmpty()) }
    var rejection by remember(rule.id) { mutableStateOf<String?>(null) }
    var slotMenu by remember { mutableStateOf(false) }
    var slot by remember(rule.id) { mutableStateOf(rule.slot) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing) "Edit wording" else "Add wording") },
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
                androidx.compose.foundation.layout.Box {
                    TextButton(onClick = { slotMenu = true }) { Text("Applies to: ${slot.title}") }
                    DropdownMenu(expanded = slotMenu, onDismissRequest = { slotMenu = false }) {
                        RuleSlot.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.title) },
                                onClick = {
                                    slot = option
                                    slotMenu = false
                                    rejection = null
                                },
                            )
                        }
                    }
                }
                Text(slot.explanation, style = MaterialTheme.typography.labelSmall)

                Text("Kind", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    RuleKind.entries.forEach { option ->
                        FilterChip(
                            selected = kind == option,
                            onClick = {
                                kind = option
                                rejection = null
                            },
                            label = { Text(option.title) },
                        )
                    }
                }

                Text("Where it applies", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    RuleScope.entries.forEach { option ->
                        FilterChip(
                            selected = scope == option,
                            onClick = { scope = option },
                            label = { Text(option.title) },
                        )
                    }
                }
                Text(scope.explanation, style = MaterialTheme.typography.labelSmall)

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                rejection?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val candidate = rule.copy(
                    slot = slot,
                    kind = kind,
                    phrase = phrase,
                    scope = scope,
                    note = note.trim().ifBlank { null },
                )
                val refused = onSave(candidate)
                if (refused == null) onSaved() else rejection = refused.message
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
