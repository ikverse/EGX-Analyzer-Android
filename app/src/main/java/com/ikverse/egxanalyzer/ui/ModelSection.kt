package com.ikverse.egxanalyzer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ikverse.egxanalyzer.model.CloudConfiguration
import com.ikverse.egxanalyzer.model.CloudModelInfo
import com.ikverse.egxanalyzer.model.ModelSuitability
import com.ikverse.egxanalyzer.model.ModelSuitabilityRules
import com.ikverse.egxanalyzer.model.ModelUsageRecord
import com.ikverse.egxanalyzer.model.formatTokenCount
import com.ikverse.egxanalyzer.ui.kit.ButtonKind
import com.ikverse.egxanalyzer.ui.kit.ButtonSize
import com.ikverse.egxanalyzer.ui.kit.KitButton
import com.ikverse.egxanalyzer.ui.kit.KitSheet
import com.ikverse.egxanalyzer.ui.kit.KitSpinner
import com.ikverse.egxanalyzer.ui.kit.KitTextField
import com.ikverse.egxanalyzer.ui.kit.LocalFoldPosture
import com.ikverse.egxanalyzer.ui.kit.SegmentedControl
import com.ikverse.egxanalyzer.ui.kit.Tile
import com.ikverse.egxanalyzer.ui.kit.TileTone
import com.ikverse.egxanalyzer.ui.theme.palette

/**
 * The model a run is read by, on Settings' Analysis page: a lilac tile naming it, pressed to choose
 * another.
 *
 * One control: the name, and a tap to change it. Managing the list - loading it, searching it,
 * naming a model the provider never offered - belongs to the sheet that tap opens, because that is
 * where the list is. Typing was the primary control once, and a model name mistyped by one character
 * is only ever discovered by a paid run failing.
 */
@Composable
internal fun AnalysisModelCard(appState: AppState) {
    var picking by remember { mutableStateOf(false) }
    val configuration = appState.cloudConfiguration
    val chosen = configuration.model.isNotBlank()
    Tile(
        Modifier.fillMaxWidth(),
        tone = TileTone.TILE2,
        onClick = { picking = true },
        onClickLabel = "Choose the analysis model",
    ) {
        Text("Which AI", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(
            if (chosen) configuration.model else "No model chosen",
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = TabularFigures),
            fontWeight = FontWeight.Bold,
            // Ids run long - `meta-llama/Llama-3.3-70B-Instruct` - so two lines rather than one
            // truncated to uselessness.
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            configuration.connectionLabel() + " · tap to change",
            style = MaterialTheme.typography.bodySmall,
            color = LocalContentColor.current.copy(alpha = 0.78f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    if (picking) {
        ModelPickerSheet(
            appState = appState,
            title = "Analysis model",
            selected = configuration.model,
            requireVision = true,
            onChoose = {
                appState.updateModel(it)
                // Picking a model does not change the key, so this persists the choice without
                // re-verifying.
                appState.persistModelChoice()
            },
            onDismiss = { picking = false },
        )
    }
}

/**
 * Where the connection points, in as few words as it can be said.
 *
 * A matched preset already names the provider - "QwenCloud / International (Singapore)" - so
 * printing the provider beside it says the same thing twice.
 */
private fun CloudConfiguration.connectionLabel(): String =
    provider.endpointPresets.firstOrNull { it.endpoint == endpoint }?.displayName
        ?: provider.displayName

/**
 * The catalogue, the field that searches it, and the filter that keeps it to models that can do it.
 *
 * A flat menu was fine for a provider offering a dozen models and unusable for OpenRouter, which
 * lists hundreds - most of them embedders, rerankers and voice models that could not do the job at
 * any price. What is offered by default is what can: see `ModelSuitabilityRules`. The filter can be
 * turned off and an id can still be typed, because those rules read names, and names change faster
 * than this app is rebuilt.
 *
 * Shared by the analysis model, which needs a model that can see a screenshot, and Ask AI's, which
 * sends no image and only needs a model that chats at all - [requireVision] is the one thing that
 * tells the two jobs apart, so [selected] and [onChoose] are handed in rather than read off
 * `appState.cloudConfiguration` directly, which is only ever true of the first.
 */
@Composable
internal fun ModelPickerSheet(
    appState: AppState,
    title: String,
    selected: String,
    requireVision: Boolean,
    onChoose: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var showAll by remember { mutableStateOf(false) }
    val typed = query.trim()
    // Read once, here: the tally lives on disk and this sheet draws hundreds of rows.
    LaunchedEffect(Unit) {
        appState.refreshModelUsage()
        // Sparing a tap that would only ever do one thing: a key is saved, so the list this sheet
        // opened to show is worth fetching before the reader has to ask for it by hand.
        if (appState.availableModels.isEmpty() && !appState.modelListLoading && appState.cloudConfiguration.hasCredential) {
            appState.loadCloudModels()
        }
    }
    val catalogue = appState.availableModels
    val stated = remember(catalogue) {
        catalogue.count { ModelSuitabilityRules.capabilitiesOf(it).suitability == ModelSuitability.SUITABLE }
    }
    val offered = remember(catalogue, showAll, selected, requireVision) {
        offeredModels(catalogue, showAll, selected, requireVision)
    }
    val matches = remember(offered, query, selected) {
        // The model in force leads its own list. Among three hundred rows it is otherwise the one
        // entry the reader cannot find, and sortedByDescending is stable, so everything below it
        // keeps the order the provider gave.
        filterModels(offered, query).sortedByDescending { it.id == selected }
    }
    // Whatever was typed is offered as its own row unless it is already one of the rows above it -
    // the way to a model the name rules did not recognise, or one this list does not carry at all.
    val exactMatch = typed.isNotEmpty() && matches.any { it.id == typed }
    val choose: (String) -> Unit = { model ->
        onChoose(model)
        onDismiss()
    }
    KitSheet(onDismiss = onDismiss, posture = LocalFoldPosture.current, title = title) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // One field doing both jobs. A second box for naming a model the provider never listed
            // only ever raised the question of which of the two to type into.
            KitTextField(
                value = query,
                onValueChange = { query = it },
                label = "Filter, or type a model id",
                modifier = Modifier.fillMaxWidth(),
            )
            if (catalogue.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        // Says what is being held back and why, so a short list never looks like a
                        // catalogue that came back short.
                        if (requireVision) {
                            "${offered.size} of ${catalogue.size} offered · $stated state image input"
                        } else {
                            "${offered.size} of ${catalogue.size} offered"
                        },
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = TabularFigures),
                        color = palette.muted,
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                    )
                    SegmentedControl(
                        options = listOf(false, true),
                        selected = showAll,
                        onSelect = { showAll = it },
                        label = { if (it) "Show all" else "Offered" },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            when {
                matches.isNotEmpty() || (typed.isNotEmpty() && !exactMatch) -> LazyColumn(
                    // Lazy because this is the one list in the app that runs to hundreds of rows.
                    Modifier.heightIn(max = ModelListMaxHeight),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (typed.isNotEmpty() && !exactMatch) {
                        item(key = "use-typed") {
                            ModelRow(label = "Use “$typed”", detail = null, selected = false) { choose(typed) }
                        }
                    }
                    items(matches, key = { it.id }) { model ->
                        ModelRow(
                            label = model.id,
                            detail = modelDetail(model, appState.usageFor(model.id)),
                            selected = model.id == selected,
                        ) { choose(model.id) }
                    }
                }
                catalogue.isNotEmpty() -> Text(
                    if (requireVision) {
                        "None of the ${catalogue.size} models loaded can read images. Show all, or type a model id above."
                    } else {
                        "None of the ${catalogue.size} models loaded are chat models. Show all, or type a model id above."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.muted,
                )
                else -> Text(
                    "No models loaded. Load the list, or type a model id above.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.muted,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                KitButton(
                    when {
                        appState.modelListLoading -> "Loading…"
                        appState.availableModels.isEmpty() -> "Load models"
                        else -> "Reload models"
                    },
                    onClick = { appState.startAction { appState.loadCloudModels() } },
                    kind = ButtonKind.SECONDARY,
                    size = ButtonSize.SMALL,
                    enabled = !appState.modelListLoading,
                    icon = if (appState.modelListLoading) null else Icons.Outlined.CloudDownload,
                )
                if (appState.modelListLoading) KitSpinner(Modifier.padding(start = 10.dp), color = palette.ink)
            }
            // Sits with the button it is about, and only after it has been pressed: a load that
            // failed has to say why, and "enter the API key first" is the usual reason.
            appState.modelListMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = palette.muted)
            }
        }
    }
}

/**
 * The models the picker puts up, in the order the provider gave them.
 *
 * Only what is known not to work is held back - the embedders, rerankers and voice models that
 * could not read a card at any price. A name this build has never heard of is shown, because the
 * rules that read names are a guess and they age: every model released after this build would
 * otherwise arrive invisible, which is how a new Qwen generation came to be missing from a list
 * that still had the last one. [showAll] drops even that much, and the model in force is never
 * filtered away - a list that does not contain the reader's own setting reads as a broken list.
 */
internal fun offeredModels(
    catalogue: List<CloudModelInfo>,
    showAll: Boolean,
    chosen: String,
    /** False for Ask AI: it sends no image, so a text-only chat model is exactly what it needs. */
    requireVision: Boolean = true,
): List<CloudModelInfo> = when {
    showAll -> catalogue
    requireVision -> catalogue.filter {
        ModelSuitabilityRules.capabilitiesOf(it).suitability != ModelSuitability.UNSUITABLE ||
            it.id == chosen
    }
    else -> catalogue.filter { ModelSuitabilityRules.isChatModel(it) || it.id == chosen }
}

/**
 * The second line of a row: what the model takes in, how much room it has, and what it has cost.
 *
 * Null where none of the three is known, so a bare id is drawn as a bare id rather than with an
 * empty line under it.
 */
private fun modelDetail(model: CloudModelInfo, usage: ModelUsageRecord?): String? = listOfNotNull(
    ModelSuitabilityRules.capabilitiesOf(model).inputLabel(),
    model.contextLength?.let { "${formatTokenCount(it.toLong())} context" },
    usage?.let { "${formatTokenCount(it.usage.totalTokens)} tokens · ${it.requests} requests" },
).joinToString(" · ").takeIf(String::isNotBlank)

/** One model in the picker: its id in mono, what it reads and has cost, lime when it is in use. */
@Composable
private fun ModelRow(label: String, detail: String?, selected: Boolean, onClick: () -> Unit) {
    val p = palette
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) p.highlight else p.ground)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = TabularFigures),
                fontWeight = FontWeight.Bold,
                color = p.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            detail?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) p.ink else p.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (selected) {
            Text(
                "In use ✓",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = p.ink,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

/**
 * The models matching [query].
 *
 * Every term has to appear somewhere in the id, and the query is split on the punctuation providers
 * use as well as on spaces: ids are written three different ways - `qwen3-vl-plus`,
 * `openai/gpt-4o`, `meta-llama/Llama-3.3-70B-Instruct` - and a plain `contains` makes the reader
 * reproduce punctuation they have no way of knowing. "qwen vl" and "qwen3-vl" both find the same
 * model, which is the point.
 */
internal fun filterModels(models: List<CloudModelInfo>, query: String): List<CloudModelInfo> {
    val terms = query.lowercase().split(*ModelQuerySeparators).filter(String::isNotEmpty)
    if (terms.isEmpty()) return models
    return models.filter { model ->
        val candidate = model.id.lowercase()
        terms.all(candidate::contains)
    }
}

private val ModelQuerySeparators =
    charArrayOf(' ', '\t', '\n', '-', '_', '/', '.', ':', ',')

/** Tall enough to browse, short enough that the load button stays on screen beneath it. */
private val ModelListMaxHeight = 360.dp
