package com.ikverse.egxanalyzer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ikverse.egxanalyzer.data.ModelUsageRecord
import com.ikverse.egxanalyzer.model.CloudConfiguration
import com.ikverse.egxanalyzer.model.CloudModelInfo
import com.ikverse.egxanalyzer.model.ModelSuitability
import com.ikverse.egxanalyzer.model.ModelSuitabilityRules
import com.ikverse.egxanalyzer.model.formatTokenCount
import kotlinx.coroutines.launch

/**
 * Which model a run will be sent to.
 *
 * One control: the name, and a tap to change it. Managing the list - loading it, searching it,
 * naming a model the provider never offered - belongs to the sheet that tap opens, because that is
 * where the list is. The card it replaces put all of that on the page and still had to be expanded
 * before it would say which model was in force.
 */
@Composable
internal fun ColumnScope.AnalysisModelCard(
    appState: AppState,
    /** Why a run cannot start, so this card can answer for the parts of it that are its own. */
    blocker: AnalyzeBlocker?,
    /** Whether Analyze has been pressed: until it has, a blocker is guidance rather than an error. */
    attempted: Boolean,
) {
    var picking by remember { mutableStateOf(false) }
    SectionCard(title = "Analysis model", icon = Icons.Outlined.SmartToy) {
        ModelTile(appState.cloudConfiguration) { picking = true }
        // Drawn here rather than under the sources, where it used to sit: the card that is wrong is
        // the card that should say so.
        blocker?.takeIf { it.belongsToModelCard }?.let {
            Text(
                it.reason,
                style = MaterialTheme.typography.bodySmall,
                color = if (attempted) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
    if (picking) {
        ModelPickerSheet(appState) { picking = false }
    }
}

/**
 * The chosen model, as a tile rather than a text field.
 *
 * Typing was the primary control here, and a model name mistyped by one character is only ever
 * discovered by a paid run failing.
 */
@Composable
private fun ModelTile(configuration: CloudConfiguration, onClick: () -> Unit) {
    val chosen = configuration.model.isNotBlank()
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(horizontal = Space.m, vertical = Space.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Text(
                    if (chosen) configuration.model else "No model chosen",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (chosen) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    // Ids run long - `meta-llama/Llama-3.3-70B-Instruct` - and this sits in the
                    // narrow pane, so two lines rather than one truncated to uselessness.
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    configuration.connectionLabel(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(Space.s))
            Icon(
                Icons.Outlined.UnfoldMore,
                contentDescription = "Choose the analysis model",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(IconSize.Inline),
            )
        }
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
 * lists hundreds - most of them embedders, rerankers and voice models that could not read a card at
 * any price. What is offered by default is what can read one: see `ModelSuitabilityRules`. The
 * filter can be turned off and an id can still be typed, because those rules read names, and names
 * change faster than this app is rebuilt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelPickerSheet(appState: AppState, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var showAll by remember { mutableStateOf(false) }
    val configuration = appState.cloudConfiguration
    val typed = query.trim()
    // Read once, here: the tally lives on disk and this sheet draws hundreds of rows.
    LaunchedEffect(Unit) { appState.refreshModelUsage() }
    val catalogue = appState.availableModels
    val suitable = remember(catalogue) {
        catalogue.filter {
            ModelSuitabilityRules.capabilitiesOf(it).suitability == ModelSuitability.SUITABLE
        }
    }
    val offered = remember(catalogue, suitable, showAll, configuration.model) {
        if (showAll) {
            catalogue
        } else {
            // The model in force is never filtered away. Hiding what is selected leaves the reader
            // looking at a list that does not contain their own setting.
            suitable + catalogue.filter { it.id == configuration.model && it !in suitable }
        }
    }
    val matches = remember(offered, query, configuration.model) {
        // The model in force leads its own list. Among three hundred rows it is otherwise the one
        // entry the reader cannot find, and sortedByDescending is stable, so everything below it
        // keeps the order the provider gave.
        filterModels(offered, query).sortedByDescending { it.id == configuration.model }
    }
    val choose: (String) -> Unit = { model ->
        appState.updateModel(model)
        // Picking a model does not change the key, so this persists the choice without re-verifying.
        appState.persistModelChoice()
        onDismiss()
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.l)
                .padding(bottom = Space.xl),
            verticalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            Text("Analysis model", style = MaterialTheme.typography.headlineSmall)
            // One field doing both jobs. A second box for naming a model the provider never listed
            // only ever raised the question of which of the two to type into.
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Filter, or type a model id") },
                singleLine = true,
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Search,
                        contentDescription = null,
                        modifier = Modifier.size(IconSize.Inline),
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = "Clear the filter",
                                modifier = Modifier.size(IconSize.Inline),
                            )
                        }
                    }
                },
            )
            if (catalogue.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        // Says what is being held back and why, so a short list never looks like a
                        // catalogue that came back short.
                        "${suitable.size} of ${catalogue.size} can read images",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { showAll = !showAll }) {
                        Text(if (showAll) "Show suitable" else "Show all")
                    }
                }
            }
            when {
                matches.isNotEmpty() -> LazyColumn(
                    // Lazy because this is the one list in the app that runs to hundreds of rows.
                    Modifier.heightIn(max = ModelListMaxHeight),
                    verticalArrangement = Arrangement.spacedBy(Space.xs),
                ) {
                    items(matches, key = { it.id }) { model ->
                        ModelRow(
                            label = model.id,
                            detail = modelDetail(model, appState.usageFor(model.id)),
                            selected = model.id == configuration.model,
                        ) { choose(model.id) }
                    }
                }
                // Nothing on offer answers to what was typed, so what was typed is the answer -
                // which is what the separate manual-entry field used to be for. It is also the way
                // to a model the name rules did not recognise: type the id and it is used.
                typed.isNotEmpty() -> ModelRow(
                    label = "Use “$typed”",
                    detail = null,
                    selected = false,
                ) { choose(typed) }
                catalogue.isNotEmpty() -> Text(
                    "None of the ${catalogue.size} models loaded say they can read images. " +
                        "Show all, or type a model id above.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> Text(
                    "No models loaded. Load the list, or type a model id above.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(
                onClick = { scope.launch { appState.loadCloudModels() } },
                enabled = !appState.modelListLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (appState.modelListLoading) {
                    CircularProgressIndicator(Modifier.size(IconSize.Inline), strokeWidth = 2.dp)
                } else {
                    Icon(
                        Icons.Outlined.CloudDownload,
                        contentDescription = null,
                        modifier = Modifier.size(IconSize.Inline),
                    )
                }
                Spacer(Modifier.width(Space.s))
                Text(
                    when {
                        appState.modelListLoading -> "Loading…"
                        appState.availableModels.isEmpty() -> "Load models"
                        else -> "Reload models"
                    },
                )
            }
            // Sits with the button it is about, and only after it has been pressed: a load that
            // failed has to say why, and "enter the API key first" is the usual reason.
            appState.modelListMessage?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
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

@Composable
private fun ModelRow(label: String, detail: String?, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(horizontal = Space.m, vertical = Space.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Text(label, style = MaterialTheme.typography.bodyMedium)
                detail?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (selected) {
                Spacer(Modifier.width(Space.s))
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = "In use",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(IconSize.Inline),
                )
            }
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
