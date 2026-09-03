package com.ikverse.egxanalyzer.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import com.ikverse.egxanalyzer.model.ModelUsageRecord
import com.ikverse.egxanalyzer.model.RuleRejection
import com.ikverse.egxanalyzer.model.RuleSet
import com.ikverse.egxanalyzer.model.WordingRule
import com.ikverse.egxanalyzer.model.ComposedPrompt
import com.ikverse.egxanalyzer.model.PromptVersion
import com.ikverse.egxanalyzer.model.RestoreOutcome
import com.ikverse.egxanalyzer.model.AvailableUpdate
import com.ikverse.egxanalyzer.model.UpdateState
import com.ikverse.egxanalyzer.model.PriceHealthReport
import com.ikverse.egxanalyzer.model.AnalysisAim
import com.ikverse.egxanalyzer.model.AnalysisContentType
import com.ikverse.egxanalyzer.model.AnalysisSchedule
import com.ikverse.egxanalyzer.model.AnalysisLanguage
import com.ikverse.egxanalyzer.model.AnalysisInput
import com.ikverse.egxanalyzer.model.AnalysisMode
import com.ikverse.egxanalyzer.model.AnalysisReport
import com.ikverse.egxanalyzer.model.AppPreferences
import com.ikverse.egxanalyzer.model.CallOrder
import com.ikverse.egxanalyzer.model.ChannelSelection
import com.ikverse.egxanalyzer.model.CloudConfiguration
import com.ikverse.egxanalyzer.model.CloudModelInfo
import com.ikverse.egxanalyzer.model.CloudProvider
import com.ikverse.egxanalyzer.model.PerformanceReport
import com.ikverse.egxanalyzer.model.Portfolio
import com.ikverse.egxanalyzer.model.PortfolioOrder
import com.ikverse.egxanalyzer.model.Position
import com.ikverse.egxanalyzer.model.PositionView
import com.ikverse.egxanalyzer.model.PromptSnapshot
import com.ikverse.egxanalyzer.model.Sale
import com.ikverse.egxanalyzer.model.SavedAnalysis
import com.ikverse.egxanalyzer.model.SourceTrace
import com.ikverse.egxanalyzer.model.TelegramAuthState
import com.ikverse.egxanalyzer.model.ThemeMode
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDate
import com.ikverse.egxanalyzer.model.ScoredCall
import com.ikverse.egxanalyzer.model.SessionDigest
import com.ikverse.egxanalyzer.model.StockOpinion

enum class AppDestination(val label: String, val shortLabel: String) {
    ANALYZE("Analyze", "AI"),
    RESULTS("Results", "RS"),
    INSIGHTS("Insights", "IN"),
    // Between what the sources said and how the app is configured: the portfolio is read after the
    // record it is judged against, and it is not a setting.
    PORTFOLIO("Portfolio", "PF"),
    SETTINGS("Settings", "ST"),
}

enum class AnalysisStatus { IDLE, RUNNING, COMPLETED, FAILED, CANCELLED }

/**
 * Everything a screen may read and every action it may take - and nothing else.
 *
 * The whole of the UI's view of the app. Screens are written against this and never against the
 * class behind it, so a redesign can be built, previewed and judged without a repository, a
 * database, a network or a device anywhere near it. [com.ikverse.egxanalyzer.state.LiveAppState]
 * is the one that talks to those; [com.ikverse.egxanalyzer.ui.preview.FakeAppState] is the one a
 * @Preview draws with.
 *
 * Almost every property is read-only here even though the implementation stores it in a
 * `mutableStateOf`. That is the point: a screen shows what the app decided, and changes it by
 * calling something below rather than by assigning to it. The two exceptions are marked where
 * they are declared.
 */
interface AppState {

    val destination: AppDestination
    val pages: PageState
    val cloudConfiguration: CloudConfiguration
    val appPreferences: AppPreferences
    val selectedContentTypes: Set<AnalysisContentType>
    val analysisMode: AnalysisMode
    val recommendationTargetDate: LocalDate
    val settingsMessage: String?
    val credentialVerified: Boolean?
    val promptHistory: List<PromptSnapshot>
    val catalogMessage: String
    val availableModels: List<CloudModelInfo>
    val modelUsage: List<ModelUsageRecord>
    val modelListLoading: Boolean
    val modelListMessage: String?
    val channels: List<ChannelSelection>
    val telegramAuthState: TelegramAuthState
    val telegramSourceDate: LocalDate
    val telegramSyncMessage: String?
    val insightsSeenAt: Long
    /** Writable: a screen clears the line it is showing, or puts its own there. */
    var statusMessage: StatusMessage?
    val busyLabel: String?
    val busyAnnounced: Boolean
    val inputs: List<AnalysisInput>
    val activeSourceChannelId: Long?
    val telegramSources: List<SourceTrace>
    val manualInputs: List<AnalysisInput>
    val savedResults: List<SavedAnalysis>
    val wordingRules: List<WordingRule>
    val ruleSet: RuleSet
    val useDefaultPromptOnly: Boolean
    val promptVersions: List<PromptVersion>
    val activePrompt: ComposedPrompt
    val unreadableResults: Int
    val selectedResult: SavedAnalysis?
    val analysisStatus: AnalysisStatus
    val analysisStartedAt: Instant?
    val analysisMessage: String?
    val pendingResultId: Long?
    val pendingPositionId: String?
    val pendingCallId: String?
    val pendingSellPositionId: String?
    val openStockTicker: String?
    val duplicateOfSelection: SavedAnalysis?
    val performance: PerformanceReport
    val priceHealth: PriceHealthReport
    val pricesRefreshing: Boolean
    val portfolio: Portfolio
    val sessionDigest: SessionDigest?
    val opinions: Map<String, StockOpinion>
    val opinionPending: String?
    val opinionSettingsRevision: Int
    val updateState: UpdateState
    val canGoBack: Boolean
    val scrollToTopRequest: Pair<AppDestination, Int>?
    val tradeWatchWanted: Boolean
    val analysisSchedules: List<AnalysisSchedule>
    /** Writable: the schedules sheet opens and closes itself. */
    var openScheduleSettings: Boolean
    val marketRefreshEnabled: Boolean
    val marketRefreshNote: String?
    val marketRefreshNoteAt: Long
    val paidSchedulesEnabled: Boolean
    val backupFolder: String?
    val chatsRefreshing: Boolean

    /**
     * What the phone will and will not let this app do, and the settings pages that change it.
     *
     * Read on every recomposition rather than remembered: every one of these is granted on a system
     * page with this screen still underneath, so an answer cached when the screen was built is
     * stale exactly when the user comes back to check it.
     */
    fun notificationsPermitted(): Boolean

    fun openNotificationSettings()

    fun exactAlarmsAllowed(): Boolean

    fun openExactAlarmSettings()

    fun batteryOptimizationExempt(): Boolean

    fun openBatteryOptimizationSettings()

    /** Where request traces are written, for the diagnostics list that counts them. */
    fun traceRoot(): File

    /** Hands the report to whatever the phone offers to send it with. */
    fun shareReport(saved: SavedAnalysis)

    /** Writes the report as a spreadsheet into Downloads, and says so. */
    suspend fun saveReportToDownloads(saved: SavedAnalysis)

    /** Writes the report as a spreadsheet and offers it onward. */
    suspend fun exportReport(saved: SavedAnalysis)

    /** Copies this device's saved record into Downloads, and returns the name it landed under. */
    suspend fun saveDatabaseToDownloads(): String

    /** Whether the chosen backup folder is still one this app may write to. */
    fun holdsBackupFolder(): Boolean

    /** The backups already in that folder, newest first. Empty when there is no reachable folder. */
    fun backupsInFolder(): List<String>

    /** A folder as a person would recognise it, for a line naming where backups go. */
    fun backupFolderLabel(uri: String): String

    /** Keeps the grant on a newly picked folder, so the daily backup survives this process. */
    fun keepBackupFolder(uri: String)

    /** Writes a backup, and returns where it went. */
    suspend fun writeBackup(): String

    /** Reads a backup file and adopts whatever this device is missing from it. */
    suspend fun restoreFromBackup(source: String): RestoreOutcome

    fun markInsightsSeen()

    fun consumeStatusMessage()

    suspend fun <T> runAction(
        label: String,
        success: (T) -> String,
        /**
         * Whether the header carries this run at all. A quiet run keeps its progress bar and its
         * failure - what it gives up is the working line and the confirmation, which are the two
         * the screen that pressed can say better. A failure is never quiet: it is the only account
         * of why a press produced nothing.
         */
        announce: Boolean = true,
        block: suspend () -> T,
    )

    fun usePromptDefaultOnly(value: Boolean)

    fun saveWordingRule(rule: WordingRule): RuleRejection?

    fun deleteWordingRule(rule: WordingRule)

    fun setWordingRuleEnabled(rule: WordingRule, enabled: Boolean)

    fun openSavedResult(id: Long, returnTo: NavStop? = NavStop(destination))

    fun consumePendingResult()

    fun openPosition(id: String, returnTo: NavStop? = NavStop(destination))

    fun consumePendingPosition()

    fun openCall(id: String, returnTo: NavStop? = NavStop(destination))

    fun consumePendingCall()

    fun openPositionToSell(id: String)

    fun consumePendingSell()

    fun positionFor(id: String): Position?

    fun openStock(ticker: String)

    fun closeStock()

    fun dismissDuplicateWarning()

    fun startAnalysis(confirmed: Boolean = false)

    fun opinionFor(call: ScoredCall): StockOpinion?

    fun opinionModel(): String

    fun opinionSearchEnabled(): Boolean

    fun opinionNewsWindowDays(): Int

    fun updateOpinionNewsWindow(days: Int)

    fun opinionSearchResults(): Int

    fun updateOpinionSearchResults(count: Int)

    fun opinionDeepSearch(): Boolean

    fun updateOpinionDeepSearch(enabled: Boolean)

    fun updateOpinionModel(model: String)

    fun updateOpinionSearch(enabled: Boolean)

    fun askAboutCall(call: ScoredCall, askAgain: Boolean = false)

    fun deleteOpinion(call: ScoredCall)

    fun heldFor(ticker: String, recommendationDate: LocalDate?): PositionView?

    fun recordPurchase(
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
        windowSessions: Int = appPreferences.defaultTradeWindowSessions,
        /**
         * What the dialog put in front of them, which is not always the setting.
         *
         * A T+1 call is offered its own two sessions, and comparing that against the setting would
         * file every accepted T+1 trade as a window the user set by hand - a card claiming a
         * decision nobody made.
         */
        offeredWindow: Int = appPreferences.defaultTradeWindowSessions,
        /** Whether the card called it a T+1, taken off the card rather than read back later. */
        isTPlusOne: Boolean = false,
    )

    fun recordSale(position: Position, sale: Sale)

    fun recordSale(position: Position, exitPrice: Double, exitDate: LocalDate)

    fun reopenPosition(position: Position)

    fun reprice(
        position: Position,
        entryPrice: Double,
        entryDate: LocalDate,
        windowSessions: Int = position.windowSessions,
    )

    fun setKeepOpen(position: Position, keepOpen: Boolean, note: String? = null)

    fun deletePosition(position: Position)

    fun refreshOverdue()

    fun peakSince(ticker: String, openedOn: LocalDate?): Double?

    fun enterForeground()

    fun checkForUpdate()

    fun downloadUpdate(update: AvailableUpdate)

    fun dismissUpdate()

    fun installUpdate(file: File)

    fun reportUpdateProblem(reason: String)

    fun canInstallUpdates(): Boolean

    fun installPermissionIntent(): Intent?

    fun releasesPageIntent(): Intent?

    fun updateAutomaticUpdateChecks(enabled: Boolean)

    fun navigate(destination: AppDestination)

    fun goBack(): Boolean

    fun scrollToTop(destination: AppDestination)

    fun toggleContentType(type: AnalysisContentType)

    fun selectProvider(provider: CloudProvider)

    fun updateEndpoint(endpoint: String)

    fun updateModel(model: String)

    suspend fun saveSettings(credential: String)

    fun persistModelChoice()

    suspend fun verifyCredential()

    fun refreshModelUsage()

    fun clearModelUsage()

    fun usageFor(model: String): ModelUsageRecord?

    suspend fun loadCloudModels()

    fun removeCredential()

    fun resetProviderConfiguration()

    fun updateThemeMode(value: ThemeMode)

    fun updateAnalysisLanguage(value: AnalysisLanguage)

    fun updateResponseTimeout(value: Int)

    fun toggleDefaultContentType(type: AnalysisContentType)

    fun updatePromptCustomization(systemPrompt: String, include: String, exclude: String)

    fun restorePromptSnapshot(snapshot: PromptSnapshot)

    fun resetPromptCustomization()

    fun updateCorrectionRetries(value: Int)

    fun updateCatalogEnrichment(enabled: Boolean)

    suspend fun refreshEgxCatalog()

    fun updateOverdueReminders(enabled: Boolean)

    fun updateTradeAlerts(enabled: Boolean)

    fun updateCallAlerts(enabled: Boolean)

    fun updateApproachAlerts(enabled: Boolean)

    fun updateApproachThreshold(percent: Int)

    fun updateSessionDigest(enabled: Boolean)

    fun updateFeedAlerts(enabled: Boolean)

    fun updateScheduleAlerts(enabled: Boolean)

    fun editSchedules()

    fun updateMarketRefreshEnabled(enabled: Boolean)

    fun updatePaidSchedulesEnabled(enabled: Boolean)

    fun saveAnalysisSchedule(schedule: AnalysisSchedule)

    fun addAnalysisSchedule()

    fun deleteAnalysisSchedule(id: Long)

    suspend fun runDueScheduledJobs()

    fun updatePortfolioOrder(order: PortfolioOrder)

    fun updateCallOrder(order: CallOrder)

    fun updateDefaultTradeWindow(sessions: Int)

    fun databaseFile(): java.io.File

    fun checkpointDatabase()

    fun saveBackupFolder(uri: String?)

    fun settingsDocument(): String

    fun backupDevice(): String

    fun backupDue(): Boolean

    fun recordBackupDay()

    suspend fun refreshPrices(announce: Boolean = true): PriceRefreshOutcome

    fun addChannel(idText: String, name: String): Boolean

    suspend fun saveTelegramApiConfiguration(apiId: String, apiHash: String)

    suspend fun resetTelegramApiConfiguration()

    suspend fun syncReports()


    suspend fun startTelegramQrSignIn()

    suspend fun submitTelegramPhone(phone: String)

    suspend fun submitTelegramCode(code: String)

    suspend fun submitTelegramPassword(password: String)

    suspend fun submitTelegramEmail(email: String)

    suspend fun submitTelegramEmailCode(code: String)

    suspend fun registerTelegram(firstName: String, lastName: String)

    suspend fun logoutTelegram()

    suspend fun refreshTelegramChats()

    fun updateTelegramSourceDate(value: String): Boolean

    fun selectAnalysisMode(mode: AnalysisMode)

    fun updateRecommendationTargetDate(date: LocalDate)

    suspend fun syncTelegramSources()

    fun toggleChannel(channel: ChannelSelection)

    fun removeChannel(channel: ChannelSelection)

    fun selectSourceChannel(id: Long?)

    fun addText(value: String)

    fun addImages(uris: List<Uri>, mimeType: (Uri) -> String?)

    fun addVoice(uri: Uri, mimeType: String?)

    fun removeInput(sourceId: String)

    suspend fun analyze()

    fun scheduledAnalysisFromScreen(): AnalysisAim?

    suspend fun cancelAnalysis()

    fun selectResult(result: SavedAnalysis)

    fun deleteResult(result: SavedAnalysis)

    fun deleteAllResults()

    fun reportFor(saved: SavedAnalysis): AnalysisReport

}

/** Cairo, so "today" turns over with the exchange rather than with the device's timezone. */
/** Shared, so a screen naming a session "today" and the app storing one agree on where today is. */
internal const val EGX_ZONE = "Africa/Cairo"

/**
 * How a status line reads, which is not the same question as whether an action worked.
 *
 * The header shows one line for everything the app is doing or has just done, so it has to tell a
 * step still running from an outcome: a spinner against "Connecting to Telegram" and a tick against
 * "Telegram ready". Reading that off `succeeded` alone put a tick beside work that had not finished.
 * It also decides how long the line stays - see `AppStatusLine`.
 */
enum class StatusStage { WORKING, DONE, FAILED }

/**
 * What an action reports, and how it reads.
 *
 * [stage] derives from [succeeded] unless a caller says otherwise, so the fifty-odd places that raise
 * an ordinary outcome are unchanged; only a step that is still running has to name it.
 */
data class StatusMessage(
    val text: String,
    val succeeded: Boolean,
    val stage: StatusStage = if (succeeded) StatusStage.DONE else StatusStage.FAILED,
    /**
     * One word the reader can press to undo what the line has just reported.
     *
     * **A slot on this line rather than a snackbar**, deliberately. The floating toast was removed
     * on 2026-08-25 because it answered from the far end of the screen from the button that had
     * been pressed, and bringing one back for this would undo that on purpose. The line already
     * says what happened, sits where the app's own name is, and clears itself after four seconds -
     * which is exactly the shape an undo wants.
     *
     * **At most one, and only on something destructive.** Recording a sale and closing a trade by
     * hand are the two irreversible things a reader does in this app; everything else is an edit
     * they can simply make again. A confirmation carrying a button after every tap would turn the
     * quietest piece of chrome in the app into the loudest.
     *
     * Null on every other message, which is all but two of the fifty-odd outcomes here.
     */
    val undo: StatusUndo? = null,
)

/** What pressing the word on a [StatusMessage] does, and what that word is. */
data class StatusUndo(val label: String, val action: () -> Unit)

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

/**
 * What a price refresh did, in one line.
 *
 * One verdict, read the same way by both its readers: a partial answer is still an answer. A
 * refresh that came back with three stocks unpriced worked, and neither the status line nor the
 * record a scheduled job leaves behind should call it anything else - only a refresh that threw, or
 * found nothing to fetch, is worth anyone's attention the next morning. What was imperfect about it
 * is in [summary], and which stocks and why is on the Price feed card in Settings.
 */
data class PriceRefreshOutcome(
    val summary: String,
    val succeeded: Boolean,
    /**
     * True where nothing was fetched because a refresh was already under way.
     *
     * Its own answer rather than a failure, because it is the likely shape of a collision between
     * a scheduled run and the refresh a launch starts: nothing went wrong, and filing it as an
     * error would put a red line on a schedule that behaved perfectly.
     */
    val busy: Boolean = false,
)
