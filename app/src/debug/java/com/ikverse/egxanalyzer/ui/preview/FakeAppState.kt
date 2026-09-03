package com.ikverse.egxanalyzer.ui.preview

import com.ikverse.egxanalyzer.model.*
import com.ikverse.egxanalyzer.ui.*
import android.content.Intent
import android.net.Uri
import java.io.File
import java.time.Instant
import java.time.LocalDate

/**
 * An [AppState] that holds still.
 *
 * Every screen in this app is a function of the interface above, so one can be drawn with this in
 * place of the running app: no database, no repositories, no network, no provider key, no device.
 * That is what makes a redesign something you can look at in a `@Preview` in seconds rather than
 * something you can only judge after a build, an install and a sign-in.
 *
 * Everything is a constructor parameter with an empty default, so a preview names only what it is
 * about - `FakeAppState(analysisStatus = AnalysisStatus.RUNNING)` - and the other sixty-odd
 * properties stay out of its way. Every action does nothing; a preview is not running anything.
 *
 * In `src/debug`, so none of it reaches a release build.
 */
class FakeAppState(
    override val destination: AppDestination = AppDestination.ANALYZE,
    override val pages: PageState = PageState(),
    override val cloudConfiguration: CloudConfiguration = CloudConfiguration(),
    override val appPreferences: AppPreferences = AppPreferences(),
    override val selectedContentTypes: Set<AnalysisContentType> = AnalysisContentType.OFFERED.toSet(),
    override val analysisMode: AnalysisMode = AnalysisMode.NEXT_DAY,
    override val recommendationTargetDate: LocalDate = LocalDate.now(),
    override val settingsMessage: String? = null,
    override val credentialVerified: Boolean? = null,
    override val promptHistory: List<PromptSnapshot> = emptyList(),
    override val catalogMessage: String = "",
    override val availableModels: List<CloudModelInfo> = emptyList(),
    override val modelUsage: List<ModelUsageRecord> = emptyList(),
    override val modelListLoading: Boolean = false,
    override val modelListMessage: String? = null,
    override val channels: List<ChannelSelection> = emptyList(),
    override val telegramAuthState: TelegramAuthState = TelegramAuthState(),
    override val telegramSourceDate: LocalDate = LocalDate.now(),
    override val telegramSyncMessage: String? = null,
    override val insightsSeenAt: Long = 0L,
    override var statusMessage: StatusMessage? = null,
    override val busyLabel: String? = null,
    override val busyAnnounced: Boolean = false,
    override val inputs: List<AnalysisInput> = emptyList(),
    override val activeSourceChannelId: Long? = null,
    override val telegramSources: List<SourceTrace> = emptyList(),
    override val manualInputs: List<AnalysisInput> = emptyList(),
    override val savedResults: List<SavedAnalysis> = emptyList(),
    override val wordingRules: List<WordingRule> = emptyList(),
    override val ruleSet: RuleSet = RuleSet(emptyList()),
    override val useDefaultPromptOnly: Boolean = false,
    override val promptVersions: List<PromptVersion> = emptyList(),
    override val activePrompt: ComposedPrompt = ComposedPrompt(id = "preview", text = "", schemaVersion = null, ruleIds = emptyList()),
    override val unreadableResults: Int = 0,
    override val selectedResult: SavedAnalysis? = null,
    override val analysisStatus: AnalysisStatus = AnalysisStatus.IDLE,
    override val analysisStartedAt: Instant? = null,
    override val analysisMessage: String? = null,
    override val pendingResultId: Long? = null,
    override val pendingPositionId: String? = null,
    override val pendingCallId: String? = null,
    override val pendingSellPositionId: String? = null,
    override val openStockTicker: String? = null,
    override val duplicateOfSelection: SavedAnalysis? = null,
    override val performance: PerformanceReport = PerformanceReport(),
    override val priceHealth: PriceHealthReport = PriceHealthReport(),
    override val pricesRefreshing: Boolean = false,
    override val portfolio: Portfolio = Portfolio(),
    override val sessionDigest: SessionDigest? = null,
    override val opinions: Map<String, StockOpinion> = emptyMap(),
    override val opinionPending: String? = null,
    override val opinionSettingsRevision: Int = 0,
    override val updateState: UpdateState = UpdateState.Idle,
    override val canGoBack: Boolean = false,
    override val scrollToTopRequest: Pair<AppDestination, Int>? = null,
    override val tradeWatchWanted: Boolean = false,
    override val analysisSchedules: List<AnalysisSchedule> = emptyList(),
    override var openScheduleSettings: Boolean = false,
    override val marketRefreshEnabled: Boolean = false,
    override val marketRefreshNote: String? = null,
    override val marketRefreshNoteAt: Long = 0L,
    override val paidSchedulesEnabled: Boolean = false,
    override val backupFolder: String? = null,
    override val chatsRefreshing: Boolean = false,
) : AppState {

    override fun notificationsPermitted(): Boolean = false

    override fun openNotificationSettings() = Unit

    override fun exactAlarmsAllowed(): Boolean = false

    override fun openExactAlarmSettings() = Unit

    override fun batteryOptimizationExempt(): Boolean = false

    override fun openBatteryOptimizationSettings() = Unit

    override fun traceRoot(): File = File("")

    override fun shareReport(saved: SavedAnalysis) = Unit

    override suspend fun saveReportToDownloads(saved: SavedAnalysis) = Unit

    override suspend fun exportReport(saved: SavedAnalysis) = Unit

    override suspend fun saveDatabaseToDownloads(): String = ""

    override fun holdsBackupFolder(): Boolean = false

    override fun backupsInFolder(): List<String> = emptyList()

    override fun backupFolderLabel(uri: String): String = ""

    override fun keepBackupFolder(uri: String) = Unit

    override suspend fun writeBackup(): String = ""

    override suspend fun restoreFromBackup(source: String): RestoreOutcome =
        RestoreOutcome(0, 0, 0, 0, settingsAdopted = false)

    override fun markInsightsSeen() = Unit

    override fun consumeStatusMessage() = Unit

    override suspend fun <T> runAction(
        label: String,
        success: (T) -> String,
        /**
         * Whether the header carries this run at all. A quiet run keeps its progress bar and its
         * failure - what it gives up is the working line and the confirmation, which are the two
         * the screen that pressed can say better. A failure is never quiet: it is the only account
         * of why a press produced nothing.
         */
        announce: Boolean,
        block: suspend () -> T,
    ) = Unit

    override fun usePromptDefaultOnly(value: Boolean) = Unit

    override fun saveWordingRule(rule: WordingRule): RuleRejection? = null

    override fun deleteWordingRule(rule: WordingRule) = Unit

    override fun setWordingRuleEnabled(rule: WordingRule, enabled: Boolean) = Unit

    override fun openSavedResult(id: Long, returnTo: NavStop?) = Unit

    override fun consumePendingResult() = Unit

    override fun openPosition(id: String, returnTo: NavStop?) = Unit

    override fun consumePendingPosition() = Unit

    override fun openCall(id: String, returnTo: NavStop?) = Unit

    override fun consumePendingCall() = Unit

    override fun openPositionToSell(id: String) = Unit

    override fun consumePendingSell() = Unit

    override fun positionFor(id: String): Position? = null

    override fun openStock(ticker: String) = Unit

    override fun closeStock() = Unit

    override fun dismissDuplicateWarning() = Unit

    override fun startAnalysis(confirmed: Boolean) = Unit

    override fun opinionFor(call: ScoredCall): StockOpinion? = null

    override fun opinionModel(): String = ""

    override fun opinionSearchEnabled(): Boolean = false

    override fun opinionNewsWindowDays(): Int = 0

    override fun updateOpinionNewsWindow(days: Int) = Unit

    override fun opinionSearchResults(): Int = 0

    override fun updateOpinionSearchResults(count: Int) = Unit

    override fun opinionDeepSearch(): Boolean = false

    override fun updateOpinionDeepSearch(enabled: Boolean) = Unit

    override fun updateOpinionModel(model: String) = Unit

    override fun updateOpinionSearch(enabled: Boolean) = Unit

    override fun askAboutCall(call: ScoredCall, askAgain: Boolean) = Unit

    override fun deleteOpinion(call: ScoredCall) = Unit

    override fun heldFor(ticker: String, recommendationDate: LocalDate?): PositionView? = null

    override fun recordPurchase(
        ticker: String,
        companyEnglish: String?,
        companyArabic: String?,
        channel: String?,
        recommendationDate: LocalDate,
        entryPrice: Double,
        entryDate: LocalDate,
        entryLow: Double?,
        entryHigh: Double?,
        target1: Double?,
        target2: Double?,
        stopLoss: Double?,
        /** What the dialog offered, or whatever the user typed over it. */
        windowSessions: Int,
        /**
         * What the dialog put in front of them, which is not always the setting.
         *
         * A T+1 call is offered its own two sessions, and comparing that against the setting would
         * file every accepted T+1 trade as a window the user set by hand - a card claiming a
         * decision nobody made.
         */
        offeredWindow: Int,
        /** Whether the card called it a T+1, taken off the card rather than read back later. */
        isTPlusOne: Boolean,
    ) = Unit

    override fun recordSale(position: Position, sale: Sale) = Unit

    override fun recordSale(position: Position, exitPrice: Double, exitDate: LocalDate) = Unit

    override fun reopenPosition(position: Position) = Unit

    override fun reprice(
        position: Position,
        entryPrice: Double,
        entryDate: LocalDate,
        windowSessions: Int,
    ) = Unit

    override fun setKeepOpen(position: Position, keepOpen: Boolean, note: String?) = Unit

    override fun deletePosition(position: Position) = Unit

    override fun refreshOverdue() = Unit

    override fun peakSince(ticker: String, openedOn: LocalDate?): Double? = null

    override fun enterForeground() = Unit

    override fun checkForUpdate() = Unit

    override fun downloadUpdate(update: AvailableUpdate) = Unit

    override fun dismissUpdate() = Unit

    override fun installUpdate(file: File) = Unit

    override fun reportUpdateProblem(reason: String) = Unit

    override fun canInstallUpdates(): Boolean = false

    override fun installPermissionIntent(): Intent? = null

    override fun releasesPageIntent(): Intent? = null

    override fun updateAutomaticUpdateChecks(enabled: Boolean) = Unit

    override fun navigate(destination: AppDestination) = Unit

    override fun goBack(): Boolean = false

    override fun scrollToTop(destination: AppDestination) = Unit

    override fun toggleContentType(type: AnalysisContentType) = Unit

    override fun selectProvider(provider: CloudProvider) = Unit

    override fun updateEndpoint(endpoint: String) = Unit

    override fun updateModel(model: String) = Unit

    override suspend fun saveSettings(credential: String) = Unit

    override fun persistModelChoice() = Unit

    override suspend fun verifyCredential() = Unit

    override fun refreshModelUsage() = Unit

    override fun clearModelUsage() = Unit

    override fun usageFor(model: String): ModelUsageRecord? = null

    override suspend fun loadCloudModels() = Unit

    override fun removeCredential() = Unit

    override fun resetProviderConfiguration() = Unit

    override fun updateThemeMode(value: ThemeMode) = Unit

    override fun updateAnalysisLanguage(value: AnalysisLanguage) = Unit

    override fun updateResponseTimeout(value: Int) = Unit

    override fun toggleDefaultContentType(type: AnalysisContentType) = Unit

    override fun updatePromptCustomization(systemPrompt: String, include: String, exclude: String) = Unit

    override fun restorePromptSnapshot(snapshot: PromptSnapshot) = Unit

    override fun resetPromptCustomization() = Unit

    override fun updateCorrectionRetries(value: Int) = Unit

    override fun updateCatalogEnrichment(enabled: Boolean) = Unit

    override suspend fun refreshEgxCatalog() = Unit

    override fun updateOverdueReminders(enabled: Boolean) = Unit

    override fun updateTradeAlerts(enabled: Boolean) = Unit

    override fun updateCallAlerts(enabled: Boolean) = Unit

    override fun updateApproachAlerts(enabled: Boolean) = Unit

    override fun updateApproachThreshold(percent: Int) = Unit

    override fun updateSessionDigest(enabled: Boolean) = Unit

    override fun updateFeedAlerts(enabled: Boolean) = Unit

    override fun updateScheduleAlerts(enabled: Boolean) = Unit

    override fun editSchedules() = Unit

    override fun updateMarketRefreshEnabled(enabled: Boolean) = Unit

    override fun updatePaidSchedulesEnabled(enabled: Boolean) = Unit

    override fun saveAnalysisSchedule(schedule: AnalysisSchedule) = Unit

    override fun addAnalysisSchedule() = Unit

    override fun deleteAnalysisSchedule(id: Long) = Unit

    override suspend fun runDueScheduledJobs() = Unit

    override fun updatePortfolioOrder(order: PortfolioOrder) = Unit

    override fun updateCallOrder(order: CallOrder) = Unit

    override fun updateDefaultTradeWindow(sessions: Int) = Unit

    override fun databaseFile(): java.io.File = File("")

    override fun checkpointDatabase() = Unit

    override fun saveBackupFolder(uri: String?) = Unit

    override fun settingsDocument(): String = ""

    override fun backupDevice(): String = ""

    override fun backupDue(): Boolean = false

    override fun recordBackupDay() = Unit

    override suspend fun refreshPrices(announce: Boolean): PriceRefreshOutcome = PriceRefreshOutcome(summary = "", succeeded = true)

    override fun addChannel(idText: String, name: String): Boolean = false

    override suspend fun saveTelegramApiConfiguration(apiId: String, apiHash: String) = Unit

    override suspend fun resetTelegramApiConfiguration() = Unit

    override suspend fun syncReports() = Unit

    override suspend fun startTelegramQrSignIn() = Unit

    override suspend fun submitTelegramPhone(phone: String) = Unit

    override suspend fun submitTelegramCode(code: String) = Unit

    override suspend fun submitTelegramPassword(password: String) = Unit

    override suspend fun submitTelegramEmail(email: String) = Unit

    override suspend fun submitTelegramEmailCode(code: String) = Unit

    override suspend fun registerTelegram(firstName: String, lastName: String) = Unit

    override suspend fun logoutTelegram() = Unit

    override suspend fun refreshTelegramChats() = Unit

    override fun updateTelegramSourceDate(value: String): Boolean = false

    override fun selectAnalysisMode(mode: AnalysisMode) = Unit

    override fun updateRecommendationTargetDate(date: LocalDate) = Unit

    override suspend fun syncTelegramSources() = Unit

    override fun toggleChannel(channel: ChannelSelection) = Unit

    override fun removeChannel(channel: ChannelSelection) = Unit

    override fun selectSourceChannel(id: Long?) = Unit

    override fun addText(value: String) = Unit

    override fun addImages(uris: List<Uri>, mimeType: (Uri) -> String?) = Unit

    override fun addVoice(uri: Uri, mimeType: String?) = Unit

    override fun removeInput(sourceId: String) = Unit

    override suspend fun analyze() = Unit

    override fun scheduledAnalysisFromScreen(): AnalysisAim? = null

    override suspend fun cancelAnalysis() = Unit

    override fun selectResult(result: SavedAnalysis) = Unit

    override fun deleteResult(result: SavedAnalysis) = Unit

    override fun deleteAllResults() = Unit

    override fun reportFor(saved: SavedAnalysis): AnalysisReport = AnalysisReport(title = "", markdown = "")

}