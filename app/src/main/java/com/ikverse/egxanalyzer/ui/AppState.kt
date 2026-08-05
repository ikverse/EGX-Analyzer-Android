package com.ikverse.egxanalyzer.ui

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import com.ikverse.egxanalyzer.data.AnalysisRepository
import com.ikverse.egxanalyzer.data.RuleRejection
import com.ikverse.egxanalyzer.data.RuleSet
import com.ikverse.egxanalyzer.model.RuleKind
import com.ikverse.egxanalyzer.model.RuleOrigin
import com.ikverse.egxanalyzer.model.RuleScope
import com.ikverse.egxanalyzer.model.RuleSlot
import com.ikverse.egxanalyzer.model.WordingRule
import android.os.Build
import com.ikverse.egxanalyzer.data.ComposedPrompt
import com.ikverse.egxanalyzer.data.PromptComposer
import com.ikverse.egxanalyzer.data.PromptStore
import com.ikverse.egxanalyzer.model.PromptVersion
import com.ikverse.egxanalyzer.data.SyncedRule
import com.ikverse.egxanalyzer.data.mergeRules
import com.ikverse.egxanalyzer.data.rulesToUpload
import com.ikverse.egxanalyzer.data.AnalysisPolicy
import com.ikverse.egxanalyzer.data.EndpointPolicy
import com.ikverse.egxanalyzer.data.EgxCatalog
import com.ikverse.egxanalyzer.data.LocalDataStore
import com.ikverse.egxanalyzer.data.PerformanceCalculator
import com.ikverse.egxanalyzer.data.PriceRepository
import com.ikverse.egxanalyzer.data.SettingsRepository
import com.ikverse.egxanalyzer.data.SyncOutcome
import com.ikverse.egxanalyzer.data.SyncedRun
import com.ikverse.egxanalyzer.data.syncActions
import com.ikverse.egxanalyzer.data.recommendedTickers
import com.ikverse.egxanalyzer.data.TelegramRepository
import com.ikverse.egxanalyzer.model.AnalysedChannel
import com.ikverse.egxanalyzer.model.AnalysisContentType
import com.ikverse.egxanalyzer.model.AnalysisLanguage
import com.ikverse.egxanalyzer.model.AnalysisInput
import com.ikverse.egxanalyzer.model.AnalysisMode
import com.ikverse.egxanalyzer.model.AnalysisRequest
import com.ikverse.egxanalyzer.model.AnalysisReport
import com.ikverse.egxanalyzer.model.AppPreferences
import com.ikverse.egxanalyzer.model.ChannelSelection
import com.ikverse.egxanalyzer.model.ChatKind
import com.ikverse.egxanalyzer.model.CloudConfiguration
import com.ikverse.egxanalyzer.model.CloudProvider
import com.ikverse.egxanalyzer.model.PerformanceReport
import com.ikverse.egxanalyzer.model.PromptSnapshot
import com.ikverse.egxanalyzer.model.SavedAnalysis
import com.ikverse.egxanalyzer.model.Scoring
import com.ikverse.egxanalyzer.model.SourceTrace
import com.ikverse.egxanalyzer.model.TelegramAuthState
import com.ikverse.egxanalyzer.model.TelegramAuthStep
import com.ikverse.egxanalyzer.model.ThemeMode
import com.ikverse.egxanalyzer.model.egxTargetSession
import com.ikverse.egxanalyzer.model.resolveAnalysisWindow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

enum class AppDestination(val label: String, val shortLabel: String) {
    ANALYZE("Analyze", "AI"),
    RESULTS("Results", "RS"),
    INSIGHTS("Insights", "IN"),
    SETTINGS("Settings", "ST"),
}

enum class AnalysisStatus { IDLE, RUNNING, COMPLETED, FAILED, CANCELLED }

class AppState(
    private val settingsRepository: SettingsRepository,
    private val analysisRepository: AnalysisRepository,
    private val localDataStore: LocalDataStore,
    private val telegramRepository: TelegramRepository,
    private val priceRepository: PriceRepository,
    /** The shipped prompt, which every generated version is composed from. */
    private val promptStore: PromptStore,
    /** Announces a run that has started; supplied by the app so this class stays testable. */
    private val analysisRunning: (sources: Int, model: String) -> Unit = { _, _ -> },
    /** Announces a finished run and the analysis it saved. */
    private val analysisFinished: (resultId: Long?, recommendations: Int) -> Unit = { _, _ -> },
    /** Withdraws the running announcement, with a reason when it failed. */
    private val analysisStopped: (reason: String?) -> Unit = {},
) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    var destination by mutableStateOf(AppDestination.ANALYZE)
        private set
    var cloudConfiguration by mutableStateOf(settingsRepository.load())
        private set
    var appPreferences by mutableStateOf(settingsRepository.loadPreferences())
        private set
    var selectedContentTypes by mutableStateOf(appPreferences.defaultContentTypes)
        private set
    var analysisMode by mutableStateOf(AnalysisMode.NEXT_DAY)
        private set
    var recommendationTargetDate by mutableStateOf(egxTargetSession())
        private set
    var settingsMessage by mutableStateOf<String?>(null)
        private set

    /** True once the provider has accepted the key, false once it has rejected it, null untested. */
    var credentialVerified by mutableStateOf<Boolean?>(null)
        private set
    var promptHistory by mutableStateOf(settingsRepository.promptHistory())
        private set
    var catalogMessage by mutableStateOf("${EgxCatalog.size()} seed stocks available offline.")
        private set
    var availableModels by mutableStateOf<List<String>>(emptyList())
        private set
    var modelListLoading by mutableStateOf(false)
        private set
    var modelListMessage by mutableStateOf<String?>(null)
        private set
    // Starts empty: the chat list belongs to the Telegram session, so nothing from a previous run
    // is shown before Telegram reports what actually exists now.
    var channels by mutableStateOf(emptyList<ChannelSelection>())
        private set
    var telegramAuthState by mutableStateOf(TelegramAuthState())
        private set
    var telegramSourceDate by mutableStateOf(LocalDate.now())
        private set
    var telegramSyncMessage by mutableStateOf<String?>(null)
        private set

    /** One-shot banner text for an action that has just finished. */
    var statusMessage by mutableStateOf<StatusMessage?>(null)

    /** Non-null while a named action is running, so the shell can show progress. */
    var busyLabel by mutableStateOf<String?>(null)
        private set

    fun consumeStatusMessage() {
        statusMessage = null
    }

    /**
     * Runs a user-triggered action with progress and a plain-language outcome.
     *
     * Failures surface the provider's own message where there is one, since "no credit" or
     * "wrong key" is far more use than a generic failure.
     */
    suspend fun <T> runAction(
        label: String,
        success: (T) -> String,
        block: suspend () -> T,
    ) {
        busyLabel = label
        statusMessage = null
        try {
            val outcome = block()
            statusMessage = StatusMessage(success(outcome), succeeded = true)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            statusMessage = StatusMessage(
                error.message?.takeIf(String::isNotBlank) ?: "$label failed.",
                succeeded = false,
            )
        } finally {
            busyLabel = null
        }
    }
    var inputs by mutableStateOf<List<AnalysisInput>>(emptyList())
        private set
    var activeSourceChannelId by mutableStateOf(channels.firstOrNull { it.selected }?.id)
        private set
    private var sourceChannelIds by mutableStateOf<Map<String, Long?>>(emptyMap())
    private var telegramTraces by mutableStateOf<Map<String, SourceTrace>>(emptyMap())

    /** What the last preview pulled from Telegram, newest first, so it can be shown before paying. */
    val telegramSources: List<SourceTrace>
        get() = telegramTraces.values.sortedByDescending(SourceTrace::timestamp)

    /**
     * Sources added by hand, which are the only ones worth listing individually.
     *
     * Telegram's are shown by the source preview and can be changed by choosing different chats or
     * a different window; listing them again only invited removing one at a time from a set the
     * next load would rebuild anyway.
     */
    val manualInputs: List<AnalysisInput>
        get() = inputs.filterNot { it.sourceId in telegramTraces.keys }
    var savedResults by mutableStateOf(localDataStore.results())
        private set

    /**
     * The wording the app recognises, shipped rows and the user's together.
     *
     * Held here rather than read per run so the Settings screen and the next analysis are looking
     * at the same set - the filter and the prompt disagreeing about which rules are on would be
     * invisible until a report came out wrong.
     */
    var wordingRules by mutableStateOf(localDataStore.wordingRules())
        private set

    val ruleSet: RuleSet get() = RuleSet(wordingRules)

    /**
     * Whether custom wording reaches the prompt at all.
     *
     * Off means the shipped prompt is sent exactly as it is. It does not delete a rule or stop the
     * local ones working - "restore the default" has to mean something narrower than "throw away
     * what I configured", or nobody dares press it.
     */
    var useDefaultPromptOnly by mutableStateOf(settingsRepository.useDefaultPromptOnly())
        private set

    var promptVersions by mutableStateOf(localDataStore.promptVersions())
        private set

    /** The version a run would use right now. */
    var activePrompt by mutableStateOf(composePrompt())
        private set

    private fun composePrompt(): ComposedPrompt = PromptComposer.compose(
        defaultPrompt = promptStore.consolidatedPrompt(),
        rules = ruleSet,
        useDefaultOnly = useDefaultPromptOnly,
    )

    /**
     * Regenerates the active prompt from the shipped one plus whatever is configured now.
     *
     * Never from the last generated version. Appending to that is how a prompt collects
     * instructions nobody added and keeps ones that were deleted.
     */
    private fun regeneratePrompt(reason: String) {
        val composed = composePrompt()
        if (composed.id != activePrompt.id || promptVersions.none { it.id == composed.id }) {
            localDataStore.rememberPromptVersion(
                PromptVersion(
                    id = composed.id,
                    sequence = localDataStore.nextPromptSequence(),
                    text = composed.text,
                    schemaVersion = composed.schemaVersion,
                    ruleIds = composed.ruleIds,
                    reason = reason,
                    device = deviceName,
                    createdAt = System.currentTimeMillis(),
                ),
            )
            promptVersions = localDataStore.promptVersions()
        }
        activePrompt = composed
    }

    fun usePromptDefaultOnly(value: Boolean) {
        useDefaultPromptOnly = value
        settingsRepository.saveUseDefaultPromptOnly(value)
        regeneratePrompt(if (value) "Switched to the default prompt" else "Custom wording re-enabled")
    }

    /** Whichever device made a change, so a later merge can tell two edits apart. */
    private val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"

    /**
     * Adds or replaces a rule, or says why it cannot.
     *
     * Returns the reason rather than throwing, because every caller is a person typing into a form
     * and the only useful response is the sentence they need to read.
     */
    fun saveWordingRule(rule: WordingRule): RuleRejection? {
        val candidate = rule.copy(
            phrase = rule.phrase.trim(),
            updatedAt = System.currentTimeMillis(),
            updatedBy = deviceName,
        )
        ruleSet.rejectionFor(candidate)?.let { return it }
        val existing = wordingRules.any { it.id == candidate.id }
        localDataStore.saveWordingRule(candidate)
        wordingRules = localDataStore.wordingRules()
        regeneratePrompt("${if (existing) "Edited" else "Added"} \"${candidate.phrase}\"")
        return null
    }

    /**
     * A built-in rule is never removed, only switched off.
     *
     * They are what the app ships knowing, and an app update replaces the whole set; deleting one
     * locally would mean the next update quietly brought it back.
     */
    fun deleteWordingRule(rule: WordingRule) {
        if (rule.origin == RuleOrigin.BUILT_IN) {
            saveWordingRule(rule.copy(enabled = false))
            return
        }
        localDataStore.buryWordingRule(rule.id, System.currentTimeMillis(), deviceName)
        wordingRules = localDataStore.wordingRules()
        regeneratePrompt("Deleted \"${rule.phrase}\"")
    }

    fun setWordingRuleEnabled(rule: WordingRule, enabled: Boolean) {
        val updated = rule.copy(
            enabled = enabled,
            updatedAt = System.currentTimeMillis(),
            updatedBy = deviceName,
        )
        localDataStore.saveWordingRule(updated)
        wordingRules = localDataStore.wordingRules()
        regeneratePrompt("${if (enabled) "Enabled" else "Disabled"} \"${rule.phrase}\"")
    }

    /**
     * Brings the wording tables into line with every other device.
     *
     * Revisions rather than rows: a table can be edited offline on two devices at once, so what
     * travels is what each device did and when, and the merge decides. Returns whether anything
     * here changed, because a rule arriving changes the prompt that gets generated next.
     */
    private suspend fun syncWordingRules(): Boolean {
        val mine = localDataStore.wordingRuleRevisions().map { (rule, deleted) ->
            SyncedRule(rule, deleted)
        }
        val theirs = runCatching { telegramRepository.syncedRules() }.getOrNull() ?: return false

        var changed = false
        val byId = mine.associateBy { it.rule.id }
        theirs.forEach { incoming ->
            val here = byId[incoming.rule.id]
            val newer = here == null ||
                incoming.rule.updatedAt > here.rule.updatedAt ||
                (
                    incoming.rule.updatedAt == here.rule.updatedAt &&
                        incoming.rule.updatedBy > here.rule.updatedBy
                    )
            if (newer) {
                localDataStore.adoptWordingRule(incoming.rule, incoming.deleted)
                changed = true
            }
        }

        // Only what this device knows better. Re-uploading a revision the channel already holds
        // would grow the log without telling anyone anything.
        rulesToUpload(mine, theirs).forEach { revision ->
            runCatching { telegramRepository.uploadRule(revision) }
        }

        if (changed) wordingRules = localDataStore.wordingRules()
        return changed
    }

    /**
     * Carries the old free-text phrase fields over, once.
     *
     * They were one box for includes and one for excludes, applied both here and in the prompt with
     * no way to say which - so they become rules scoped to both, which is what they already did.
     */
    private fun adoptLegacyPhrases() {
        val stored = localDataStore.wordingRules()
        if (stored.any { it.origin == RuleOrigin.USER }) return
        val migrated = listOf(
            RuleSlot.SOURCE_KEEP to appPreferences.includePhrases,
            RuleSlot.SOURCE_DROP to appPreferences.excludePhrases,
        ).flatMap { (slot, raw) ->
            raw.split(",", "\n")
                .map(String::trim)
                .filter(String::isNotEmpty)
                .map { phrase ->
                    WordingRule(
                        id = "legacy:${slot.name.lowercase()}:${WordingRule.normalize(phrase)}",
                        slot = slot,
                        kind = if (slot == RuleSlot.SOURCE_KEEP) RuleKind.INCLUDE else RuleKind.EXCLUDE,
                        phrase = phrase,
                        scope = RuleScope.BOTH,
                        note = "Carried over from the old phrase boxes.",
                        updatedAt = System.currentTimeMillis(),
                        updatedBy = deviceName,
                    )
                }
        }
        if (migrated.isEmpty()) return
        migrated.forEach(localDataStore::saveWordingRule)
        wordingRules = localDataStore.wordingRules()
    }

    /**
     * Saved runs that would not parse, and so are missing from [savedResults].
     *
     * Kept visible rather than swallowed: a report that cannot be read looks exactly like a report
     * that was never produced, and the newest one on screen is then silently an older run.
     */
    var unreadableResults by mutableIntStateOf(localDataStore.unreadableResults)
        private set
    var selectedResult by mutableStateOf<SavedAnalysis?>(savedResults.firstOrNull())
        private set
    var analysisStatus by mutableStateOf(AnalysisStatus.IDLE)
        private set

    /**
     * When the running analysis started, so the button can count.
     *
     * A run takes anywhere from seventy seconds to eleven minutes, and one has already died on a
     * timeout, so "how long has this been going" is a real question while waiting on it.
     */
    var analysisStartedAt by mutableStateOf<Instant?>(null)
        private set
    var analysisMessage by mutableStateOf<String?>(null)
        private set
    private var activeRequestId: String? = null
    private var analysisJob: Job? = null

    /** A saved analysis the user asked to open from a notification, cleared once shown. */
    var pendingResultId by mutableStateOf<Long?>(null)
        private set

    fun openSavedResult(id: Long) {
        pendingResultId = id
        destination = AppDestination.RESULTS
    }

    fun consumePendingResult() {
        pendingResultId = null
    }

    /** A saved analysis covering exactly this session and these chats, awaiting a decision. */
    var duplicateOfSelection by mutableStateOf<SavedAnalysis?>(null)
        private set

    fun dismissDuplicateWarning() {
        duplicateOfSelection = null
    }

    /**
     * A saved analysis of the same session with the same chats.
     *
     * Only an exact match counts. A run over different chats answers a different question even on
     * the same day, so warning about it would train the user to dismiss the warning.
     */
    private fun duplicateOf(targetDate: LocalDate, chosen: Set<Long>): SavedAnalysis? =
        savedResults.firstOrNull { saved ->
            saved.result.recommendationTargetDate == targetDate &&
                saved.result.selectedChannels.isNotEmpty() &&
                saved.result.selectedChannels.map(AnalysedChannel::id).toSet() == chosen
        }

    /**
     * Runs an analysis on the application scope rather than the caller's.
     *
     * It used to be launched from the Analyze screen's own scope, so navigating away cancelled a
     * request that had already been paid for. This outlives any screen, and the service keeps the
     * process alive while the user is elsewhere.
     */
    fun startAnalysis(confirmed: Boolean = false) {
        if (analysisJob?.isActive == true) return
        if (!confirmed) {
            val target = if (analysisMode == AnalysisMode.NEXT_DAY) {
                egxTargetSession()
            } else {
                recommendationTargetDate
            }
            val chosen = channels.filter(ChannelSelection::selected).map(ChannelSelection::id).toSet()
            duplicateOf(target, chosen)?.let {
                duplicateOfSelection = it
                return
            }
        }
        duplicateOfSelection = null
        analysisJob = appScope.launch { analyze() }
    }

    /** How every saved call turned out, recomputed whenever the analyses or the window change. */
    var performance by mutableStateOf(
        PerformanceReport(windowSessions = appPreferences.scoringWindowSessions),
    )
        private set
    var pricesRefreshing by mutableStateOf(false)
        private set

    /**
     * The highest a stock has traded since a call was made, or null when nothing prices it yet.
     *
     * Read from the scored calls rather than recomputed, so a price ladder can never disagree with
     * the figure Insights reports for the same call.
     */
    fun peakSince(ticker: String, openedOn: LocalDate?): Double? {
        if (openedOn == null) return null
        val wanted = Scoring.normalizeTicker(ticker)
        return performance.sessions
            .asSequence()
            .flatMap { it.calls.asSequence() }
            .firstOrNull { Scoring.normalizeTicker(it.ticker) == wanted && it.openedOn == openedOn }
            ?.peakHigh
    }

    init {
        adoptLegacyPhrases()
        // Recorded on first launch too, so the very first run has a version to name rather than
        // a gap where one should be.
        regeneratePrompt("First run")
        appScope.launch {
            recomputePerformance()
            refreshPricesIfStale()
        }
        appScope.launch {
            val stored = localDataStore.stocks()
            EgxCatalog.restore(stored)
            catalogMessage = "${EgxCatalog.size()} stocks available offline."
            // Only when nothing has been downloaded yet, so a launch never waits on the network.
            if (stored.isEmpty()) refreshEgxCatalog()
        }
        appScope.launch {
            // The chat list is the first thing a run depends on, and it used to load in silence:
            // an empty list looked identical whether it was still fetching or had failed.
            var announced: TelegramAuthStep? = null
            telegramRepository.authState.collect { state ->
                telegramAuthState = state
                if (state.step != announced) {
                    announced = state.step
                    when (state.step) {
                        TelegramAuthStep.INITIALIZING ->
                            statusMessage = StatusMessage("Loading Telegram chats…", succeeded = true)
                        // Deliberately no count here: READY arrives before the chat list does, so
                        // reading it now reports zero while six are about to appear. The count is
                        // announced by the collector below, when there is one.
                        TelegramAuthStep.READY -> statusMessage = StatusMessage(
                            "Telegram ready · loading chats…",
                            succeeded = true,
                        )
                        TelegramAuthStep.ERROR -> statusMessage = StatusMessage(
                            state.message?.takeIf(String::isNotBlank)
                                ?: "Telegram could not load your chats.",
                            succeeded = false,
                        )
                        else -> Unit
                    }
                }
            }
        }
        appScope.launch {
            // Announced when the list actually lands, and again after a refresh changes it.
            snapshotFlow { channels.size }
                .distinctUntilChanged()
                .filter { it > 0 }
                .collect { count ->
                    statusMessage = StatusMessage("$count Telegram chats loaded.", succeeded = true)
                }
        }
        // Nothing about a previous session carries into this one: a restart starts from the
        // chat list Telegram reports now, with nothing selected.
        localDataStore.forgetChannelSelections()
        appScope.launch {
            telegramRepository.chats.collect { telegramChats ->
                val stillSelected = channels.filter(ChannelSelection::selected).map(ChannelSelection::id)
                channels = telegramChats
                    // Private chats are conversations, not published sources: the service account
                    // and one-to-one threads can only add noise to a recommendation run.
                    .filterNot { it.kind == ChatKind.DIRECT }
                    .map { chat ->
                        ChannelSelection(
                            id = chat.id,
                            name = chat.title,
                            // Kept across a refresh within the session, never across a restart.
                            selected = chat.id in stillSelected,
                            kind = chat.kind,
                        )
                    }
                if (activeSourceChannelId !in channels.map(ChannelSelection::id)) {
                    activeSourceChannelId = channels.firstOrNull { it.selected }?.id
                }
            }
        }
    }

    fun navigate(destination: AppDestination) {
        this.destination = destination
    }

    fun toggleContentType(type: AnalysisContentType) {
        selectedContentTypes = if (type in selectedContentTypes) selectedContentTypes - type
        else selectedContentTypes + type
        dropLoadedTelegramSources()
    }

    /**
     * Forgets sources fetched for settings that no longer apply.
     *
     * Analysis only collects when nothing is loaded, so a batch left over from an earlier window
     * would be analysed under the new target date without being refetched - the wrong messages
     * under the right heading. Anything added by hand survives: that is the user's own work, not a
     * cache of a query.
     */
    private fun dropLoadedTelegramSources() {
        if (telegramTraces.isEmpty()) return
        val fetched = telegramTraces.keys
        inputs = inputs.filterNot { it.sourceId in fetched }
        sourceChannelIds = sourceChannelIds - fetched
        telegramTraces = emptyMap()
        telegramSyncMessage = null
    }

    fun selectProvider(provider: CloudProvider) {
        cloudConfiguration = settingsRepository.configurationFor(provider)
        settingsMessage = null
        availableModels = emptyList()
        modelListMessage = null
    }

    fun updateEndpoint(endpoint: String) {
        cloudConfiguration = cloudConfiguration.copy(endpoint = endpoint)
        availableModels = emptyList()
        modelListMessage = null
    }

    fun updateModel(model: String) {
        cloudConfiguration = cloudConfiguration.copy(model = model)
    }

    suspend fun saveSettings(credential: String) {
        EndpointPolicy.validate(cloudConfiguration.endpoint)?.let {
            settingsMessage = it
            return
        }
        val chars = credential.sanitizedCredential().takeIf(String::isNotEmpty)?.toCharArray()
        try {
            settingsRepository.save(cloudConfiguration, chars)
        } finally {
            chars?.fill('\u0000')
        }
        cloudConfiguration = settingsRepository.load()
        if (!cloudConfiguration.hasCredential) {
            credentialVerified = null
            settingsMessage = "Connection saved. Enter the provider API key to finish."
            return
        }
        verifyCredential()
    }

    /** Stores the endpoint and model without touching the credential or its verified state. */
    fun persistModelChoice() {
        settingsRepository.save(cloudConfiguration, null)
        cloudConfiguration = settingsRepository.load()
    }

    /**
     * Asks the provider whether the stored key actually works.
     *
     * Storing a key only proves it reached the device. Reporting it as saved without checking made
     * a rejected key look like a working one, and the failure only surfaced later at analysis time.
     * The model list is the cheapest call the providers offer, so this costs nothing meaningful.
     */
    suspend fun verifyCredential() {
        busyLabel = "Verifying API key"
        try {
            val models = analysisRepository.listModels()
            credentialVerified = true
            availableModels = models
            settingsMessage = "API key verified. ${models.size} models available."
            statusMessage = StatusMessage(settingsMessage!!, succeeded = true)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            credentialVerified = false
            settingsMessage = error.message?.takeIf(String::isNotBlank)
                ?: "The provider rejected this API key."
            statusMessage = StatusMessage(settingsMessage!!, succeeded = false)
        } finally {
            busyLabel = null
        }
    }

    suspend fun loadCloudModels() {
        EndpointPolicy.validate(cloudConfiguration.endpoint)?.let {
            modelListMessage = it
            return
        }
        if (!cloudConfiguration.hasCredential) {
            modelListMessage = "Enter and save the provider API key before loading models."
            return
        }
        modelListLoading = true
        modelListMessage = "Loading models from ${cloudConfiguration.provider.displayName}…"
        try {
            availableModels = analysisRepository.listModels()
            modelListMessage = if (availableModels.isEmpty()) {
                "The provider returned no selectable models. You can still enter a model manually."
            } else {
                "Loaded ${availableModels.size} models."
            }
        } catch (error: Exception) {
            availableModels = emptyList()
            modelListMessage = error.message ?: "Could not load models."
        } finally {
            modelListLoading = false
        }
    }

    fun removeCredential() {
        settingsRepository.removeCredential(cloudConfiguration.provider)
        cloudConfiguration = settingsRepository.configurationFor(cloudConfiguration.provider)
        credentialVerified = null
        settingsMessage = "Saved credential removed."
    }

    fun resetProviderConfiguration() {
        settingsRepository.resetProviderConfiguration(cloudConfiguration.provider)
        cloudConfiguration = settingsRepository.configurationFor(cloudConfiguration.provider)
        settingsMessage = "Provider endpoint and model reset to defaults."
    }

    fun updateThemeMode(value: ThemeMode) {
        saveAppPreferences(appPreferences.copy(themeMode = value))
    }

    fun updateAnalysisLanguage(value: AnalysisLanguage) {
        saveAppPreferences(appPreferences.copy(analysisLanguage = value))
    }


    fun updateResponseTimeout(value: Int) {
        saveAppPreferences(appPreferences.copy(responseTimeoutSeconds = value.coerceIn(30, 300)))
    }

    fun toggleDefaultContentType(type: AnalysisContentType) {
        val updated = if (type in appPreferences.defaultContentTypes) {
            appPreferences.defaultContentTypes - type
        } else {
            appPreferences.defaultContentTypes + type
        }
        if (updated.isNotEmpty()) {
            saveAppPreferences(appPreferences.copy(defaultContentTypes = updated))
            selectedContentTypes = updated
        }
    }

    fun updatePromptCustomization(systemPrompt: String, include: String, exclude: String) {
        saveAppPreferences(
            appPreferences.copy(
                customSystemPrompt = systemPrompt.trim(),
                includePhrases = include.trim(),
                excludePhrases = exclude.trim(),
            ),
        )
        settingsRepository.savePromptSnapshot(appPreferences)
        promptHistory = settingsRepository.promptHistory()
        settingsMessage = "Prompt customization saved with a restorable history snapshot."
    }

    fun restorePromptSnapshot(snapshot: PromptSnapshot) {
        saveAppPreferences(
            appPreferences.copy(
                customSystemPrompt = snapshot.systemPrompt,
                includePhrases = snapshot.includePhrases,
                excludePhrases = snapshot.excludePhrases,
            ),
        )
    }

    fun resetPromptCustomization() {
        updatePromptCustomization("", "", "")
        settingsMessage = "Default evidence-backed prompt restored."
    }

    fun updateCorrectionRetries(value: Int) {
        saveAppPreferences(appPreferences.copy(correctionRetries = value.coerceIn(0, 2)))
    }

    fun updateCatalogEnrichment(enabled: Boolean) {
        saveAppPreferences(appPreferences.copy(catalogEnrichmentEnabled = enabled))
    }

    suspend fun refreshEgxCatalog() {
        catalogMessage = "Refreshing the public EGX catalog…"
        catalogMessage = try {
            val downloaded = EgxCatalog.refresh()
            // Stored so correct company names survive a restart instead of falling back to the
            // seed list, which covers only a handful of large caps.
            localDataStore.saveStocks(EgxCatalog.entries())
            "${EgxCatalog.size()} stocks available; $downloaded entries downloaded."
        } catch (error: Exception) {
            "Catalog refresh failed; ${EgxCatalog.size()} offline seed stocks remain available. " +
                (error.message ?: "")
        }
    }

    /**
     * Changes how long a call stays open before it counts as expired.
     *
     * Takes effect on everything already scored, not only future analyses: the outcome of a past
     * call under a shorter window is a fact about that call, so re-scoring is the honest answer.
     */
    fun updateScoringWindow(sessions: Int) {
        val clamped = Scoring.clampWindow(sessions)
        if (clamped == appPreferences.scoringWindowSessions) return
        settingsRepository.savePreferences(appPreferences.copy(scoringWindowSessions = clamped))
        appPreferences = settingsRepository.loadPreferences()
        appScope.launch { recomputePerformance() }
    }

    /**
     * Fetches recent sessions for every stock any analysis has named.
     *
     * Only those are worth pricing: the rest cannot be scored, and each one costs a request to an
     * undocumented public endpoint.
     */
    /**
     * Fetches prices on the first launch of each day.
     *
     * A session's prices are settled once the market closes, so refetching within the same day
     * only costs requests against an undocumented public endpoint. Runs quietly in the background
     * and never blocks the app: a failure leaves yesterday's prices in place.
     */
    private suspend fun refreshPricesIfStale() {
        val today = LocalDate.now(ZoneId.of(EGX_ZONE)).toString()
        if (settingsRepository.lastPriceRefreshDay() == today) return
        if (savedResults.recommendedTickers().isEmpty()) return
        refreshPrices(announce = false)
        settingsRepository.recordPriceRefreshDay(today)
    }

    /**
     * Fetches only the stocks with no stored history at all.
     *
     * Deliberately outside the once-a-day guard: that guard exists so a day's settled prices are
     * not refetched, not so a stock named for the first time this afternoon goes unscored until
     * tomorrow. Quiet, and a failure leaves the card unpriced rather than interrupting the run.
     */
    private suspend fun priceStocksWithNoHistory() {
        val named = savedResults.recommendedTickers()
        if (named.isEmpty()) return
        val unpriced = withContext(Dispatchers.IO) {
            val priced = localDataStore.pricedTickers()
            named.filterNot { it in priced }
        }
        if (unpriced.isEmpty()) return
        runCatching { priceRepository.refresh(unpriced) }
        recomputePerformance()
    }

    suspend fun refreshPrices(announce: Boolean = true) {
        if (pricesRefreshing) return
        val tickers = savedResults.recommendedTickers()
        if (tickers.isEmpty()) {
            if (announce) {
                statusMessage = StatusMessage("No saved analysis names a stock to price.", false)
            }
            return
        }
        pricesRefreshing = true
        busyLabel = if (announce) "Fetching prices" else null
        try {
            // A stock with no history is fetched in full; one already stored only needs the
            // sessions since, so a daily refresh stays small however long the history grows.
            val refresh = priceRepository.refresh(tickers)
            settingsRepository.recordPriceRefreshDay(
                LocalDate.now(ZoneId.of(EGX_ZONE)).toString(),
            )
            recomputePerformance()
            val missing = refresh.unpriced.size
            if (announce) {
                statusMessage = StatusMessage(
                    "Priced ${refresh.priced} of ${refresh.requested} stocks " +
                        "over ${refresh.sessionsStored} sessions" +
                        if (missing > 0) " · $missing have no price history." else ".",
                    succeeded = missing == 0,
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            if (announce) {
                statusMessage = StatusMessage(
                    error.message?.takeIf(String::isNotBlank) ?: "Could not fetch prices.",
                    succeeded = false,
                )
            }
        } finally {
            pricesRefreshing = false
            busyLabel = null
        }
    }

    private suspend fun recomputePerformance() {
        val analyses = savedResults
        val window = appPreferences.scoringWindowSessions
        performance = withContext(Dispatchers.IO) {
            PerformanceCalculator.report(
                analyses = analyses,
                pricesFrom = localDataStore.earliestSessionDate(),
                windowSessions = window,
                sessionsFor = localDataStore::sessionsFrom,
                pricedTickers = localDataStore.pricedTickers(),
            )
        }
    }

    private fun saveAppPreferences(value: AppPreferences) {
        settingsRepository.savePreferences(value)
        appPreferences = settingsRepository.loadPreferences()
        settingsMessage = "App preferences saved."
    }

    fun addChannel(idText: String, name: String): Boolean {
        val id = idText.trim().toLongOrNull()
        if (id == null || name.isBlank()) return false
        val added = ChannelSelection(id, name.trim(), selected = true)
        // Added to the live list rather than reloading from storage, which would drop every chat
        // Telegram reported this session.
        channels = (channels.filterNot { it.id == id } + added).sortedBy { it.name.lowercase() }
        return true
    }

    suspend fun saveTelegramApiConfiguration(apiId: String, apiHash: String) =
        telegramRepository.saveApiConfiguration(apiId, apiHash)

    suspend fun resetTelegramApiConfiguration() =
        telegramRepository.resetApiConfiguration()

    /**
     * Brings this device and the sync channel to the same set of reports.
     *
     * A union, not a merge: a saved run never changes, so whatever either side has, both should
     * have. Nothing is overwritten and nothing is deleted, which is why this can run without asking
     * what to keep.
     */
    suspend fun syncReports() = runAction(
        label = "Syncing reports with Telegram",
        success = SyncOutcome::summary,
    ) {
        // Deletions this device made while offline are carried out first, so nothing it has
        // already discarded is uploaded back a moment later.
        localDataStore.pendingDeletions().forEach { requestId ->
            runCatching { telegramRepository.buryReport(requestId) }
                .onSuccess { localDataStore.clearDeletion(requestId) }
        }

        // Rules first: a report downloaded a moment later was judged under somebody's rules, and
        // arriving with the reports but without them is the one order that explains nothing.
        val rulesChanged = syncWordingRules()

        val remote = telegramRepository.listSyncedReports()
        val local = localDataStore.savedRequestIds()
        val deleted = telegramRepository.listTombstones()
        val actions = syncActions(local, remote.keys, deleted)
        val toUpload = actions.upload
        val toDownload = actions.download

        // A report another device buried goes from here too. That is what makes a delete a delete
        // rather than a local hiding.
        var forgotten = 0
        actions.forget.forEach { requestId ->
            localDataStore.deleteResultByRequestId(requestId)
            forgotten++
        }

        var downloaded = 0
        toDownload.forEach { requestId ->
            val fileId = remote[requestId] ?: return@forEach
            val run = telegramRepository.downloadReport(fileId) ?: return@forEach
            if (localDataStore.adoptResult(
                    run.requestId, run.provider, run.model, run.completedAt, run.payload,
                )
            ) {
                downloaded++
            }
        }

        var uploaded = 0
        savedResults.filter { it.result.requestId in toUpload }.forEach { saved ->
            telegramRepository.uploadReport(saved.toSyncedRun())
            uploaded++
        }

        if (rulesChanged) regeneratePrompt("Rules arrived from another device")

        if (downloaded > 0 || forgotten > 0) {
            savedResults = localDataStore.results()
            unreadableResults = localDataStore.unreadableResults
            recomputePerformance()
        }
        SyncOutcome(uploaded, downloaded, local.size - toUpload.size)
    }

    private fun SavedAnalysis.toSyncedRun() = SyncedRun(
        requestId = result.requestId,
        provider = provider.name,
        model = model,
        completedAt = result.completedAt.toString(),
        payload = localDataStore.storedJsonOf(result),
    )

    suspend fun startTelegramQrSignIn() = runAction(
        label = "Preparing a Telegram sign-in code",
        success = { "Scan the code with Telegram on a signed-in device." },
    ) { telegramRepository.startQrSignIn() }

    suspend fun submitTelegramPhone(phone: String) =
        telegramRepository.submitPhoneNumber(phone)

    suspend fun submitTelegramCode(code: String) =
        telegramRepository.submitVerificationCode(code)

    suspend fun submitTelegramPassword(password: String) =
        telegramRepository.submitPassword(password)

    suspend fun submitTelegramEmail(email: String) =
        telegramRepository.submitEmailAddress(email)

    suspend fun submitTelegramEmailCode(code: String) =
        telegramRepository.submitEmailCode(code)

    suspend fun registerTelegram(firstName: String, lastName: String) =
        telegramRepository.register(firstName, lastName)

    suspend fun logoutTelegram() = telegramRepository.logout()

    /**
     * True only while the chat list is being refreshed.
     *
     * [busyLabel] means "something is running", so a pull indicator driven by it would spin for
     * work the pull did not start - and go on spinning after the chats had arrived.
     */
    var chatsRefreshing by mutableStateOf(false)
        private set

    suspend fun refreshTelegramChats() {
        chatsRefreshing = true
        try {
            telegramRepository.refreshChats()
        } finally {
            chatsRefreshing = false
        }
    }

    fun updateTelegramSourceDate(value: String): Boolean {
        val parsed = runCatching { LocalDate.parse(value.trim()) }.getOrNull() ?: return false
        telegramSourceDate = parsed
        return true
    }

    fun selectAnalysisMode(mode: AnalysisMode) {
        analysisMode = mode
        if (mode == AnalysisMode.NEXT_DAY) {
            recommendationTargetDate = egxTargetSession()
        }
        dropLoadedTelegramSources()
    }

    fun updateRecommendationTargetDate(date: LocalDate) {
        analysisMode = AnalysisMode.SPECIFIC_DATE
        recommendationTargetDate = date
        dropLoadedTelegramSources()
    }

    suspend fun syncTelegramSources() {
        val selectedChannels = channels.filter(ChannelSelection::selected)
        if (selectedChannels.isEmpty()) {
            telegramSyncMessage = "Select at least one Telegram chat."
            return
        }
        val window = resolveAnalysisWindow(analysisMode, recommendationTargetDate)
        recommendationTargetDate = window.targetDate
        telegramSyncMessage =
            "Loading sources from ${window.start} through ${window.endExclusive}…"
        try {
            val batch = telegramRepository.collectSources(
                channelIds = selectedChannels.map(ChannelSelection::id),
                start = window.start,
                endExclusive = window.endExclusive,
                contentTypes = selectedContentTypes,
            )
            inputs = batch.inputs
            telegramTraces = batch.traces.associateBy(SourceTrace::sourceId)
            sourceChannelIds = batch.traces.associate { it.sourceId to it.channelId }
            // A bare zero reads as a failure. Saying how many messages were read, and over what
            // window, separates "nothing posted in this window" from "nothing came back".
            telegramSyncMessage = when {
                batch.traces.isNotEmpty() ->
                    "Loaded ${batch.traces.size} Telegram messages (${batch.inputs.size} model inputs)."
                batch.examined > 0 ->
                    "Read ${batch.examined} messages; none fall between ${window.start} and " +
                        "${window.endExclusive}, or match the chosen content types."
                batch.silentChats > 0 ->
                    "Telegram returned nothing for ${batch.silentChats} of the selected chats. " +
                        "Try again in a moment."
                else -> "The selected chats have no messages."
            }
        } catch (error: Exception) {
            telegramSyncMessage = error.message ?: "Telegram synchronization failed."
        }
    }

    fun toggleChannel(channel: ChannelSelection) {
        val updated = channel.copy(selected = !channel.selected)
        channels = channels.map { if (it.id == channel.id) updated else it }
        dropLoadedTelegramSources()
        if (activeSourceChannelId !in channels.filter { it.selected }.map { it.id }) {
            activeSourceChannelId = channels.firstOrNull { it.selected }?.id
        }
    }

    fun removeChannel(channel: ChannelSelection) {
        channels = channels.filterNot { it.id == channel.id }
        dropLoadedTelegramSources()
        if (activeSourceChannelId == channel.id) {
            activeSourceChannelId = channels.firstOrNull { it.selected }?.id
        }
    }

    fun selectSourceChannel(id: Long?) {
        activeSourceChannelId = id
    }

    fun addText(value: String) {
        if (value.isNotBlank()) {
            addInput(AnalysisInput.Text("text-${UUID.randomUUID()}", value.trim()))
        }
    }

    fun addImages(uris: List<Uri>, mimeType: (Uri) -> String?) {
        uris.forEach {
            addInput(
            AnalysisInput.Image(
                sourceId = "image-${UUID.randomUUID()}",
                uri = it,
                mimeType = mimeType(it) ?: "image/jpeg",
            ),
            )
        }
    }

    fun addVoice(uri: Uri, mimeType: String?) {
        addInput(AnalysisInput.Voice(
            sourceId = "voice-${UUID.randomUUID()}",
            uri = uri,
            mimeType = mimeType ?: "audio/ogg",
            durationMilliseconds = null,
        ))
    }

    private fun addInput(input: AnalysisInput) {
        inputs = inputs + input
        sourceChannelIds = sourceChannelIds + (input.sourceId to activeSourceChannelId)
        telegramTraces = telegramTraces - input.sourceId
    }

    fun removeInput(sourceId: String) {
        inputs = inputs.filterNot { it.sourceId == sourceId }
        sourceChannelIds = sourceChannelIds - sourceId
        telegramTraces = telegramTraces - sourceId
    }

    suspend fun analyze() {
        if (analysisStatus == AnalysisStatus.RUNNING) return
        if (analysisMode == AnalysisMode.NEXT_DAY) {
            recommendationTargetDate = egxTargetSession()
        }
        if (analysisMode == AnalysisMode.SPECIFIC_DATE &&
            recommendationTargetDate.isAfter(LocalDate.now(ZoneId.of("Africa/Cairo")))
        ) {
            analysisMessage = "Historical analysis can only use today or an earlier Cairo date."
            return
        }
        if (!cloudConfiguration.hasCredential || cloudConfiguration.model.isBlank()) {
            analysisMessage = "Save a provider credential and model first."
            return
        }
        if (inputs.isEmpty() &&
            telegramAuthState.step == TelegramAuthStep.READY &&
            channels.any(ChannelSelection::selected)
        ) {
            syncTelegramSources()
        }
        // A caption is part of its photo or voice note, not a text source of its own, so it is
        // selected by whatever selected the media it belongs to. Filtering it as text meant that
        // with only Images chosen every caption was dropped here - the model read each card with
        // none of the words the channel wrote above it, and the phrase filter below, which reads
        // text and nothing else, could never fire.
        val mediaSourceIds = inputs.mapNotNull { input ->
            when (input) {
                is AnalysisInput.Image ->
                    input.sourceId.takeIf { AnalysisContentType.IMAGES in selectedContentTypes }
                is AnalysisInput.Voice ->
                    input.sourceId.takeIf { AnalysisContentType.AUDIO in selectedContentTypes }
                is AnalysisInput.Text -> null
            }
        }.toSet()
        val contentSelectedInputs = inputs.filter {
            when (it) {
                is AnalysisInput.Text -> it.sourceId in mediaSourceIds ||
                    AnalysisContentType.TEXT in selectedContentTypes
                is AnalysisInput.Image -> AnalysisContentType.IMAGES in selectedContentTypes
                is AnalysisInput.Voice -> AnalysisContentType.AUDIO in selectedContentTypes
            }
        }
        val rules = ruleSet
        val filtered = AnalysisPolicy.filter(contentSelectedInputs, rules)
        val selectedInputs = filtered.accepted
        if (selectedInputs.isEmpty()) {
            analysisMessage = if (filtered.excluded.isNotEmpty()) {
                "All selected sources were excluded by the recommendation filters."
            } else {
                "Add at least one selected source."
            }
            return
        }
        val window = resolveAnalysisWindow(analysisMode, recommendationTargetDate)
        recommendationTargetDate = window.targetDate
        val request = AnalysisRequest(
            channelIds = channels.filter(ChannelSelection::selected).map(ChannelSelection::id),
            selectedChannels = channels.filter(ChannelSelection::selected)
                .map { AnalysedChannel(it.id, it.displayName) },
            contentTypes = selectedContentTypes,
            inputs = selectedInputs,
            mode = analysisMode,
            targetDate = recommendationTargetDate,
            provider = cloudConfiguration.provider,
            model = cloudConfiguration.model,
            sourceWindowStart = window.start,
            sourceWindowEnd = window.endExclusive,
            excludedSources = filtered.excluded,
            rules = rules,
            prompt = activePrompt,
            sourceTraces = selectedInputs.map { input ->
                telegramTraces[input.sourceId] ?: run {
                val channel = channels.firstOrNull { it.id == sourceChannelIds[input.sourceId] }
                SourceTrace(
                    sourceId = input.sourceId,
                    channelId = channel?.id,
                    channelName = channel?.displayName ?: "On-device import",
                    messageId = null,
                    timestamp = Instant.now(),
                    contentType = when (input) {
                        is AnalysisInput.Text -> AnalysisContentType.TEXT
                        is AnalysisInput.Image -> AnalysisContentType.IMAGES
                        is AnalysisInput.Voice -> AnalysisContentType.AUDIO
                    },
                    preview = when (input) {
                        is AnalysisInput.Text -> input.value.take(160)
                        is AnalysisInput.Image -> input.uri.lastPathSegment ?: "Image"
                        is AnalysisInput.Voice -> input.uri.lastPathSegment ?: "Voice message"
                    },
                )
                }
            }.distinctBy(SourceTrace::sourceId),
        )
        activeRequestId = request.requestId
        analysisStartedAt = Instant.now()
        analysisStatus = AnalysisStatus.RUNNING
        analysisMessage = "Sending ${selectedInputs.size} sources to ${cloudConfiguration.provider.displayName}…"
        analysisRunning(selectedInputs.size, cloudConfiguration.model)
        try {
            val result = analysisRepository.analyze(request)
            localDataStore.saveResult(result, cloudConfiguration.provider, cloudConfiguration.model)
            savedResults = localDataStore.results()
            unreadableResults = localDataStore.unreadableResults
            selectedResult = savedResults.firstOrNull()
            analysisStatus = AnalysisStatus.COMPLETED
            // Saving and reading back are two different things, and a run that will not read back
            // is a run that is not there. Saying so beats leaving an older report on screen looking
            // like the newest one.
            analysisMessage = if (savedResults.none { it.result.requestId == result.requestId }) {
                "Analysed ${result.recommendations.size} recommendations, but the saved report " +
                    "could not be read back."
            } else {
                "Saved ${result.recommendations.size} recommendations."
            }
            recomputePerformance()
            // A run names stocks the price store has never seen, and until now nothing fetched
            // them: the daily guard had already fired, so an Insights card sat unpriced until the
            // button was pressed or the app was restarted the next day.
            priceStocksWithNoHistory()
            analysisFinished(savedResults.firstOrNull()?.id, result.recommendations.size)
            // Published as soon as it exists, so another device only ever has to pull. A failure
            // here is not the run failing: the report is saved, and the next sync will carry it.
            savedResults.firstOrNull { it.result.requestId == result.requestId }?.let { saved ->
                runCatching { telegramRepository.uploadReport(saved.toSyncedRun()) }
            }
            destination = AppDestination.RESULTS
        } catch (_: CancellationException) {
            analysisStatus = AnalysisStatus.CANCELLED
            analysisMessage = "Analysis cancelled."
            analysisStopped(null)
        } catch (error: Exception) {
            if (analysisStatus != AnalysisStatus.CANCELLED) {
                analysisStatus = AnalysisStatus.FAILED
                analysisMessage = error.message ?: "Analysis failed."
                analysisStopped(analysisMessage)
            }
        } finally {
            activeRequestId = null
            analysisStartedAt = null
        }
    }

    suspend fun cancelAnalysis() {
        activeRequestId?.let { analysisRepository.cancel(it) }
        analysisJob?.cancel()
        analysisStatus = AnalysisStatus.CANCELLED
        analysisMessage = "Analysis cancelled."
        analysisStopped(null)
    }

    fun selectResult(result: SavedAnalysis) {
        selectedResult = result
    }

    /**
     * Removes a report here and everywhere.
     *
     * The intent is recorded before the row goes, so a delete survives being offline, a crash, or
     * Telegram being slow: the next sync buries it in the channel and every other device drops it.
     */
    fun deleteResult(result: SavedAnalysis) {
        localDataStore.recordDeletion(result.result.requestId)
        localDataStore.deleteResult(result.id)
        savedResults = localDataStore.results()
        unreadableResults = localDataStore.unreadableResults
        selectedResult = savedResults.firstOrNull()
        appScope.launch {
            recomputePerformance()
            runCatching { telegramRepository.buryReport(result.result.requestId) }
                .onSuccess { localDataStore.clearDeletion(result.result.requestId) }
        }
    }

    fun deleteAllResults() {
        // Recorded before anything is removed, so a delete that spans every report cannot half
        // happen: whatever the app manages to bury now, the rest goes at the next sync.
        val doomed = savedResults.map { it.result.requestId }
        doomed.forEach(localDataStore::recordDeletion)
        localDataStore.deleteAllResults()
        savedResults = emptyList()
        unreadableResults = 0
        selectedResult = null
        settingsMessage = "All saved analyses deleted."
        appScope.launch {
            recomputePerformance()
            doomed.forEach { requestId ->
                runCatching { telegramRepository.buryReport(requestId) }
                    .onSuccess { localDataStore.clearDeletion(requestId) }
            }
        }
    }

    fun reportFor(saved: SavedAnalysis): AnalysisReport {
        val result = saved.result
        val markdown = buildString {
            appendLine("# EGX analysis · ${result.recommendationTargetDate ?: "target not recorded"}")
            appendLine()
            appendLine("- Provider: ${saved.provider.displayName}")
            appendLine("- Model: ${saved.model}")
            appendLine("- Sources accepted: ${result.diagnostics.acceptedInputCount}")
            appendLine("- Sources excluded: ${result.diagnostics.excludedSources.size}")
            appendLine("- Validation warnings: ${result.diagnostics.validationWarnings.size}")
            appendLine()
            result.recommendations.forEachIndexed { index, recommendation ->
                appendLine("## ${index + 1}. ${recommendation.ticker} — ${recommendation.companyName}")
                appendLine("- Signal: ${recommendation.signal}")
                appendLine("- Entry: ${recommendation.entryLow ?: "—"} to ${recommendation.entryHigh ?: "—"}")
                appendLine("- Targets: ${recommendation.takeProfit1 ?: "—"}, ${recommendation.takeProfit2 ?: "—"}")
                appendLine("- Stop loss: ${recommendation.stopLoss ?: "—"}")
                appendLine("- Source IDs: ${recommendation.sourceIds.joinToString()}")
                recommendation.notesArabic?.let { appendLine("- Notes: $it") }
                appendLine()
            }
        }
        return AnalysisReport(
            title = "EGX analysis ${result.recommendationTargetDate ?: result.completedAt}",
            markdown = markdown,
        )
    }
}

/** Cairo, so "today" turns over with the exchange rather than with the device's timezone. */
private const val EGX_ZONE = "Africa/Cairo"

/** Outcome of a finished action, shown once and dismissed. */
data class StatusMessage(val text: String, val succeeded: Boolean)

/**
 * Strips everything an API key cannot contain.
 *
 * trim() only removes recognised whitespace, so a zero-width space or non-breaking space picked up
 * while pasting on a phone survives into the key and the provider rejects it - with an "incorrect
 * API key" message that gives no hint the key merely has an invisible character in it. Provider
 * keys are printable ASCII, so anything outside that range is not part of the key.
 */
internal fun String.sanitizedCredential(): String =
    filter { it.code in 0x21..0x7E }
