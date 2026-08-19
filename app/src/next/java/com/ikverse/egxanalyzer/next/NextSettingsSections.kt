package com.ikverse.egxanalyzer.next

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ikverse.egxanalyzer.data.saveDatabaseToDownloads
import com.ikverse.egxanalyzer.model.AnalysisLanguage
import com.ikverse.egxanalyzer.model.RuleOrigin
import com.ikverse.egxanalyzer.model.WordingRule
import com.ikverse.egxanalyzer.ui.AppState
import com.ikverse.egxanalyzer.ui.StatusMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The parts of Settings that are more than a switch.
 *
 * Split out of the screen itself because each of them is a small machine: a model list that has to
 * be fetched before it can be chosen from, a rules table that is edited, and a diagnostics copy
 * that is the only way to get a database off a release-signed build.
 */

/**
 * The model, chosen from what the provider actually offers.
 *
 * The list is fetched rather than typed, but typing still works: a provider that answers with
 * nothing selectable would otherwise leave the run unrunnable, and the endpoint region is exactly
 * the setting most likely to be behind that.
 */
@Composable
internal fun ModelChoice(appState: AppState, scope: CoroutineScope) {
    val colors = LocalNextColors.current
    var endpoint by remember(appState.cloudConfiguration.endpoint) {
        mutableStateOf(appState.cloudConfiguration.endpoint)
    }
    Column(verticalArrangement = Arrangement.spacedBy(NextMetrics.space4)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NextMetrics.space3),
        ) {
            NextButton(
                label = if (appState.modelListLoading) "Loading" else "Load the model list",
                onClick = { scope.launch { appState.loadCloudModels() } },
                tone = colors.market,
                labelColor = colors.market,
                minHeight = 40.dp,
                enabled = !appState.modelListLoading,
            )
        }
        appState.modelListMessage?.let { NextText(it, NextType.meta, colors.ink3) }

        if (appState.availableModels.isNotEmpty()) {
            NextChipStrip(
                options = appState.availableModels,
                selectedIndex = appState.availableModels.indexOf(appState.cloudConfiguration.model),
                onPick = { index ->
                    appState.updateModel(appState.availableModels[index])
                    // Written through rather than left on the screen: the model is what the next
                    // run is sent to, and a choice that lived only in memory would be lost with
                    // the process and silently fall back to the default.
                    appState.persistModelChoice()
                },
                tone = colors.market,
            )
        }

        NextField(
            label = "Endpoint",
            value = endpoint,
            onValueChange = {
                endpoint = it
                appState.updateEndpoint(it)
            },
            numeric = false,
        )
        NextText(
            "The region the request goes to. A key saved for one region is refused by another, " +
                "and it fails differently from a wrong key.",
            NextType.meta,
            colors.figMuted,
        )
        NextButton(
            label = "Reset endpoint and model",
            onClick = { appState.resetProviderConfiguration() },
            tone = colors.rule,
            labelColor = colors.ink3,
            minHeight = 40.dp,
        )
    }
}

/**
 * The wording rules, which are the one piece of real table in Settings.
 *
 * Shipped rows can be switched off but never deleted - they carry the incidents that put them
 * there, and a user who removed one would have no way back to it. Their own rows can be edited and
 * removed like anything else they wrote.
 */
@Composable
internal fun WordingRules(appState: AppState, page: NextPageState) {
    val colors = LocalNextColors.current
    val rules = appState.wordingRules
    val enabled = rules.count(WordingRule::enabled)

    Column(verticalArrangement = Arrangement.spacedBy(NextMetrics.space4)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NextMetrics.space4),
        ) {
            NextText(
                "$enabled of ${rules.size} phrases on",
                NextType.meta,
                colors.figMuted,
                modifier = Modifier.weight(1f),
            )
            NextButton(
                label = "Add a phrase",
                onClick = { page.addingRule = true },
                tone = colors.accent,
                labelColor = colors.accent,
                minHeight = 40.dp,
            )
        }

        SettingSwitchRow(
            label = "Built-in wording only",
            on = appState.useDefaultPromptOnly,
            onToggle = { appState.usePromptDefaultOnly(it) },
        )

        rules.forEach { rule ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .ruleTop(colors.ruleSoft)
                    .padding(vertical = NextMetrics.space4),
                verticalArrangement = Arrangement.spacedBy(NextMetrics.space2),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(NextMetrics.space3),
                ) {
                    NextText(
                        text = rule.phrase,
                        style = NextType.name,
                        color = if (rule.enabled) colors.ink else colors.ink3,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    NextButton(
                        label = if (rule.enabled) "On" else "Off",
                        onClick = { appState.setWordingRuleEnabled(rule, !rule.enabled) },
                        tone = if (rule.enabled) colors.accent else colors.rule,
                        labelColor = if (rule.enabled) colors.accent else colors.ink3,
                        fill = if (rule.enabled) NextFill.WASH else NextFill.NONE,
                        minHeight = 40.dp,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(NextMetrics.space3),
                ) {
                    NextChip(rule.kind.title, colors.figMuted, style = ChipStyle.DASHED)
                    NextChip(rule.scope.title, colors.market)
                    NextText(
                        text = rule.slot.title,
                        style = NextType.meta,
                        color = colors.ink3,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (rule.origin == RuleOrigin.USER) {
                        NextButton(
                            label = "Edit",
                            onClick = { page.ruleSheet = rule },
                            tone = colors.rule,
                            minHeight = 40.dp,
                        )
                    } else {
                        NextText("shipped", NextType.navLabel, colors.figMuted)
                    }
                }
                rule.note?.let { NextText(it, NextType.meta, colors.figMuted) }
            }
        }
    }
}

/** How the model is asked, and how hard the app argues with it. */
@Composable
internal fun ModelBehaviour(appState: AppState) {
    val colors = LocalNextColors.current
    val preferences = appState.appPreferences
    Column(verticalArrangement = Arrangement.spacedBy(NextMetrics.space4)) {
        NextLabel("Notes language")
        NextChipStrip(
            options = AnalysisLanguage.entries.map(AnalysisLanguage::displayName),
            selectedIndex = preferences.analysisLanguage.ordinal,
            onPick = { index ->
                appState.updateAnalysisLanguage(AnalysisLanguage.entries[index])
            },
        )
        NextLabel("Response timeout")
        NextChipStrip(
            options = TIMEOUTS.map { "$it s" },
            selectedIndex = TIMEOUTS.indexOf(preferences.responseTimeoutSeconds),
            onPick = { index -> appState.updateResponseTimeout(TIMEOUTS[index]) },
            tone = colors.market,
        )
        NextLabel("Correction retries")
        NextChipStrip(
            options = RETRIES.map(Int::toString),
            selectedIndex = RETRIES.indexOf(preferences.correctionRetries),
            onPick = { index -> appState.updateCorrectionRetries(RETRIES[index]) },
            tone = colors.market,
        )
        NextText(
            "A retry asks the model to fix its own JSON rather than throwing the run away. Each " +
                "one costs another pass.",
            NextType.meta,
            colors.figMuted,
        )
    }
}

private val TIMEOUTS = listOf(60, 120, 180, 240, 300)
private val RETRIES = listOf(0, 1, 2, 3)

/**
 * The only way to get this app's database off a release-signed build.
 *
 * `run-as` refuses a package that is not debuggable and backups are closed, so without this a
 * problem on the phone can only be described rather than examined.
 */
@Composable
internal fun Diagnostics(activity: Activity, appState: AppState, scope: CoroutineScope) {
    val colors = LocalNextColors.current
    Column(verticalArrangement = Arrangement.spacedBy(NextMetrics.space4)) {
        NextText(
            "Copies the app's database into Downloads. It carries the record and no credentials — " +
                "provider keys and the Telegram key are encrypted elsewhere and have never been " +
                "in it.",
            NextType.meta,
            colors.ink3,
        )
        NextButton(
            label = "Save diagnostics",
            onClick = {
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            // The checkpoint is not optional and belongs inside the write: SQLite
                            // keeps the newest commits in a sidecar until something folds them in,
                            // so a copy taken without it is missing exactly the recent activity
                            // worth asking about.
                            saveDatabaseToDownloads(
                                context = activity,
                                database = appState.databaseFile(),
                                checkpoint = appState::checkpointDatabase,
                            )
                        }
                    }
                        .onSuccess {
                            appState.statusMessage =
                                StatusMessage("Saved to Downloads/$it", succeeded = true)
                        }
                        .onFailure {
                            appState.statusMessage = StatusMessage(
                                it.message ?: "Could not save the database",
                                succeeded = false,
                            )
                        }
                }
            },
            tone = colors.market,
            labelColor = colors.market,
            minHeight = 40.dp,
        )
    }
}

/** The same switch the screen uses, reachable from this file. */
@Composable
internal fun SettingSwitchRow(label: String, on: Boolean, onToggle: (Boolean) -> Unit) {
    val colors = LocalNextColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .ruleTop(colors.ruleSoft)
            .padding(vertical = NextMetrics.space4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NextMetrics.space4),
    ) {
        NextText(
            text = label,
            style = NextType.name,
            color = colors.ink2,
            modifier = Modifier.weight(1f),
        )
        NextButton(
            label = if (on) "On" else "Off",
            onClick = { onToggle(!on) },
            tone = if (on) colors.accent else colors.rule,
            labelColor = if (on) colors.accent else colors.ink3,
            fill = if (on) NextFill.WASH else NextFill.NONE,
            minHeight = 40.dp,
        )
    }
}
