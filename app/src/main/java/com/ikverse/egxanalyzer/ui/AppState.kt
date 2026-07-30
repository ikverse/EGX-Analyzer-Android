package com.ikverse.egxanalyzer.ui

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ikverse.egxanalyzer.data.AnalysisRepository
import com.ikverse.egxanalyzer.data.AnalysisPolicy
import com.ikverse.egxanalyzer.data.EndpointPolicy
import com.ikverse.egxanalyzer.data.EgxCatalog
import com.ikverse.egxanalyzer.data.LocalDataStore
import com.ikverse.egxanalyzer.data.PerformanceCalculator
import com.ikverse.egxanalyzer.data.PriceRepository
import com.ikverse.egxanalyzer.data.SettingsRepository
import com.ikverse.egxanalyzer.data.recommendedTickers
import com.ikverse.egxanalyzer.data.TelegramRepository
import com.ikverse.egxanalyzer.model.AnalysisContentType
import com.ikverse.egxanalyzer.model.AnalysisLanguage
import com.ikverse.egxanalyzer.model.AnalysisInput
import com.ikverse.egxanalyzer.model.AnalysisMode
import com.ikverse.egxanalyzer.model.AnalysisRequest
import com.ikverse.egxanalyzer.model.AnalysisReport
import com.ikverse.egxanalyzer.model.AppPreferences
import com.ikverse.egxanalyzer.model.ChannelSelection
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
    var savedResults by mutableStateOf(localDataStore.results())
        private set
    var selectedResult by mutableStateOf<SavedAnalysis?>(savedResults.firstOrNull())
        private set
    var analysisStatus by mutableStateOf(AnalysisStatus.IDLE)
        private set
    var analysisMessage by mutableStateOf<String?>(null)
        private set
    private var activeRequestId: String? = null

    /** How every saved call turned out, recomputed whenever the analyses or the window change. */
    var performance by mutableStateOf(
        PerformanceReport(windowSessions = appPreferences.scoringWindowSessions),
    )
        private set
    var pricesRefreshing by mutableStateOf(false)
        private set

    init {
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
            telegramRepository.authState.collect { telegramAuthState = it }
        }
        // Nothing about a previous session carries into this one: a restart starts from the
        // chat list Telegram reports now, with nothing selected.
        localDataStore.forgetChannelSelections()
        appScope.launch {
            telegramRepository.chats.collect { telegramChats ->
                val stillSelected = channels.filter(ChannelSelection::selected).map(ChannelSelection::id)
                channels = telegramChats.map { chat ->
                    ChannelSelection(
                        id = chat.id,
                        name = chat.title,
                        // Kept across a refresh within the session, but never across a restart.
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

    fun updateTemperature(value: Double) {
        saveAppPreferences(appPreferences.copy(temperature = value.coerceIn(0.0, 1.0)))
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

    suspend fun refreshTelegramChats() = telegramRepository.refreshChats()

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
    }

    fun updateRecommendationTargetDate(date: LocalDate) {
        analysisMode = AnalysisMode.SPECIFIC_DATE
        recommendationTargetDate = date
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
            telegramSyncMessage =
                "Loaded ${batch.traces.size} Telegram messages (${batch.inputs.size} model inputs)."
            destination = AppDestination.ANALYZE
        } catch (error: Exception) {
            telegramSyncMessage = error.message ?: "Telegram synchronization failed."
        }
    }

    fun toggleChannel(channel: ChannelSelection) {
        val updated = channel.copy(selected = !channel.selected)
        channels = channels.map { if (it.id == channel.id) updated else it }
        if (activeSourceChannelId !in channels.filter { it.selected }.map { it.id }) {
            activeSourceChannelId = channels.firstOrNull { it.selected }?.id
        }
    }

    fun removeChannel(channel: ChannelSelection) {
        channels = channels.filterNot { it.id == channel.id }
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
        val contentSelectedInputs = inputs.filter {
            when (it) {
                is AnalysisInput.Text -> AnalysisContentType.TEXT in selectedContentTypes
                is AnalysisInput.Image -> AnalysisContentType.IMAGES in selectedContentTypes
                is AnalysisInput.Voice -> AnalysisContentType.AUDIO in selectedContentTypes
            }
        }
        val filtered = AnalysisPolicy.filter(
            inputs = contentSelectedInputs,
            includePhrases = appPreferences.includePhrases,
            excludePhrases = appPreferences.excludePhrases,
        )
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
            contentTypes = selectedContentTypes,
            inputs = selectedInputs,
            mode = analysisMode,
            targetDate = recommendationTargetDate,
            provider = cloudConfiguration.provider,
            model = cloudConfiguration.model,
            sourceWindowStart = window.start,
            sourceWindowEnd = window.endExclusive,
            excludedSources = filtered.excluded,
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
        analysisStatus = AnalysisStatus.RUNNING
        analysisMessage = "Sending ${selectedInputs.size} sources to ${cloudConfiguration.provider.displayName}…"
        try {
            val result = analysisRepository.analyze(request)
            localDataStore.saveResult(result, cloudConfiguration.provider, cloudConfiguration.model)
            savedResults = localDataStore.results()
            selectedResult = savedResults.firstOrNull()
            analysisStatus = AnalysisStatus.COMPLETED
            analysisMessage = "Saved ${result.recommendations.size} recommendations."
            recomputePerformance()
            destination = AppDestination.RESULTS
        } catch (_: CancellationException) {
            analysisStatus = AnalysisStatus.CANCELLED
            analysisMessage = "Analysis cancelled."
        } catch (error: Exception) {
            if (analysisStatus != AnalysisStatus.CANCELLED) {
                analysisStatus = AnalysisStatus.FAILED
                analysisMessage = error.message ?: "Analysis failed."
            }
        } finally {
            activeRequestId = null
        }
    }

    suspend fun cancelAnalysis() {
        activeRequestId?.let { analysisRepository.cancel(it) }
        analysisStatus = AnalysisStatus.CANCELLED
        analysisMessage = "Analysis cancelled."
    }

    fun selectResult(result: SavedAnalysis) {
        selectedResult = result
    }

    fun deleteResult(result: SavedAnalysis) {
        localDataStore.deleteResult(result.id)
        savedResults = localDataStore.results()
        selectedResult = savedResults.firstOrNull()
        appScope.launch { recomputePerformance() }
    }

    fun deleteAllResults() {
        localDataStore.deleteAllResults()
        savedResults = emptyList()
        selectedResult = null
        settingsMessage = "All saved analyses deleted."
        appScope.launch { recomputePerformance() }
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
