package com.ikverse.egxanalyzer.state

import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import com.ikverse.egxanalyzer.data.AnalysisNotifier
import com.ikverse.egxanalyzer.data.JobScheduler
import com.ikverse.egxanalyzer.data.RequestTrace
import com.ikverse.egxanalyzer.data.saveToDownloads
import com.ikverse.egxanalyzer.data.stageExport
import com.ikverse.egxanalyzer.data.exportIntent
import com.ikverse.egxanalyzer.data.saveDatabaseToDownloads
import com.ikverse.egxanalyzer.data.holdsBackupFolder
import com.ikverse.egxanalyzer.data.backupsInFolder
import com.ikverse.egxanalyzer.data.backupFolderLabel
import com.ikverse.egxanalyzer.data.writeBackupTo
import com.ikverse.egxanalyzer.data.readBackup
import com.ikverse.egxanalyzer.ui.AnalysisStatus
import com.ikverse.egxanalyzer.ui.AppDestination
import com.ikverse.egxanalyzer.ui.AppState
import com.ikverse.egxanalyzer.ui.EGX_ZONE
import com.ikverse.egxanalyzer.ui.NavStack
import com.ikverse.egxanalyzer.ui.NavStop
import com.ikverse.egxanalyzer.ui.PageState
import com.ikverse.egxanalyzer.ui.PriceRefreshOutcome
import com.ikverse.egxanalyzer.ui.StatusMessage
import com.ikverse.egxanalyzer.ui.StatusStage
import com.ikverse.egxanalyzer.ui.StatusUndo
import com.ikverse.egxanalyzer.ui.dayWord
import com.ikverse.egxanalyzer.ui.formatPercent
import com.ikverse.egxanalyzer.ui.formatPrice
import com.ikverse.egxanalyzer.ui.sanitizedCredential
import com.ikverse.egxanalyzer.ui.sessionWord
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.ikverse.egxanalyzer.data.AnalysisPolicy
import com.ikverse.egxanalyzer.data.AnalysisRepository
import com.ikverse.egxanalyzer.data.BackupRecord
import com.ikverse.egxanalyzer.data.EgxCatalog
import com.ikverse.egxanalyzer.data.EndpointPolicy
import com.ikverse.egxanalyzer.data.IntradayRepository
import com.ikverse.egxanalyzer.data.JobRunner
import com.ikverse.egxanalyzer.data.JobSkipped
import com.ikverse.egxanalyzer.data.LocalDataStore
import com.ikverse.egxanalyzer.data.ModelUsageStore
import com.ikverse.egxanalyzer.data.OpinionParser
import com.ikverse.egxanalyzer.data.OpinionPrompt
import com.ikverse.egxanalyzer.data.OpinionPromptStore
import com.ikverse.egxanalyzer.data.OpinionRequest
import com.ikverse.egxanalyzer.data.OpinionSearchBrief
import com.ikverse.egxanalyzer.model.PerformanceCalculator
import com.ikverse.egxanalyzer.data.PortfolioCalculator
import com.ikverse.egxanalyzer.data.PriceHealth
import com.ikverse.egxanalyzer.data.PriceRepository
import com.ikverse.egxanalyzer.data.PromptComposer
import com.ikverse.egxanalyzer.data.PromptStore
import com.ikverse.egxanalyzer.data.ScheduleMigration
import com.ikverse.egxanalyzer.data.SettingsRepository
import com.ikverse.egxanalyzer.data.SettingsSnapshot
import com.ikverse.egxanalyzer.data.SyncOutcome
import com.ikverse.egxanalyzer.data.SyncedPosition
import com.ikverse.egxanalyzer.data.SyncedPromptVersion
import com.ikverse.egxanalyzer.data.SyncedRule
import com.ikverse.egxanalyzer.data.SyncedRun
import com.ikverse.egxanalyzer.data.TelegramRepository
import com.ikverse.egxanalyzer.data.UnorderedSession
import com.ikverse.egxanalyzer.data.UpdateRepository
import com.ikverse.egxanalyzer.data.mergeRules
import com.ikverse.egxanalyzer.data.positionsToRestore
import com.ikverse.egxanalyzer.data.positionsToUpload
import com.ikverse.egxanalyzer.data.promptVersionsToRestore
import com.ikverse.egxanalyzer.model.recommendedTickers
import com.ikverse.egxanalyzer.data.rulesToRestore
import com.ikverse.egxanalyzer.data.rulesToUpload
import com.ikverse.egxanalyzer.data.runsToRestore
import com.ikverse.egxanalyzer.data.settingsWorthUploading
import com.ikverse.egxanalyzer.data.syncActions
import com.ikverse.egxanalyzer.model.AnalysedChannel
import com.ikverse.egxanalyzer.model.AnalysisAim
import com.ikverse.egxanalyzer.model.AnalysisContentType
import com.ikverse.egxanalyzer.model.AnalysisInput
import com.ikverse.egxanalyzer.model.AnalysisLanguage
import com.ikverse.egxanalyzer.model.AnalysisMode
import com.ikverse.egxanalyzer.model.AnalysisPlan
import com.ikverse.egxanalyzer.model.AnalysisReport
import com.ikverse.egxanalyzer.model.AnalysisRequest
import com.ikverse.egxanalyzer.model.AnalysisSchedule
import com.ikverse.egxanalyzer.model.AppPreferences
import com.ikverse.egxanalyzer.model.ApproachAlerts
import com.ikverse.egxanalyzer.model.ApproachChange
import com.ikverse.egxanalyzer.model.AvailableUpdate
import com.ikverse.egxanalyzer.model.CallAlerts
import com.ikverse.egxanalyzer.model.CallChange
import com.ikverse.egxanalyzer.model.CallOrder
import com.ikverse.egxanalyzer.model.ChannelSelection
import com.ikverse.egxanalyzer.model.ChatKind
import com.ikverse.egxanalyzer.model.CloseSweep
import com.ikverse.egxanalyzer.model.CloudConfiguration
import com.ikverse.egxanalyzer.model.CloudProvider
import com.ikverse.egxanalyzer.model.ComposedPrompt
import com.ikverse.egxanalyzer.model.DailySession
import com.ikverse.egxanalyzer.model.DownloadedApk
import com.ikverse.egxanalyzer.model.FULL_SPLIT_PCT
import com.ikverse.egxanalyzer.model.JobOutcome
import com.ikverse.egxanalyzer.model.LatestPrice
import com.ikverse.egxanalyzer.model.MarketRefresh
import com.ikverse.egxanalyzer.model.ModelUsageRecord
import com.ikverse.egxanalyzer.model.Outcome
import com.ikverse.egxanalyzer.model.PerformanceReport
import com.ikverse.egxanalyzer.model.Portfolio
import com.ikverse.egxanalyzer.model.PortfolioOrder
import com.ikverse.egxanalyzer.model.Position
import com.ikverse.egxanalyzer.model.PositionView
import com.ikverse.egxanalyzer.model.PriceHealthReport
import com.ikverse.egxanalyzer.model.PromptSnapshot
import com.ikverse.egxanalyzer.model.PromptVersion
import com.ikverse.egxanalyzer.model.ResponseTimeout
import com.ikverse.egxanalyzer.model.RestoreOutcome
import com.ikverse.egxanalyzer.model.RuleKind
import com.ikverse.egxanalyzer.model.RuleOrigin
import com.ikverse.egxanalyzer.model.RuleRejection
import com.ikverse.egxanalyzer.model.RuleScope
import com.ikverse.egxanalyzer.model.RuleSet
import com.ikverse.egxanalyzer.model.RuleSlot
import com.ikverse.egxanalyzer.model.Sale
import com.ikverse.egxanalyzer.model.SavedAnalysis
import com.ikverse.egxanalyzer.model.ScheduleClock
import com.ikverse.egxanalyzer.model.ScoredCall
import com.ikverse.egxanalyzer.model.ScoredSession
import com.ikverse.egxanalyzer.model.Scoring
import com.ikverse.egxanalyzer.model.SessionDigest
import com.ikverse.egxanalyzer.model.SourceFreshness
import com.ikverse.egxanalyzer.model.SourceTrace
import com.ikverse.egxanalyzer.model.StockOpinion
import com.ikverse.egxanalyzer.model.TelegramAuthState
import com.ikverse.egxanalyzer.model.TelegramAuthStep
import com.ikverse.egxanalyzer.model.ThemeMode
import com.ikverse.egxanalyzer.model.TradeAlerts
import com.ikverse.egxanalyzer.model.TradeChange
import com.ikverse.egxanalyzer.model.UpdateState
import com.ikverse.egxanalyzer.model.WordingRule
import com.ikverse.egxanalyzer.model.egxTargetSession
import com.ikverse.egxanalyzer.model.opinionId
import com.ikverse.egxanalyzer.model.positionId
import com.ikverse.egxanalyzer.model.resolveAnalysisWindow
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * How long settings have to stop changing before they are published.
 *
 * Long enough to cover a slider being dragged and a checkbox being reconsidered, short enough that
 * a phone put down straight after a change still sends them.
 */
private const val SETTINGS_PUBLISH_DELAY_MILLISECONDS = 3_000L

/**
 * How long a scheduled run waits for Telegram to sign back in before giving up on that fire.
 *
 * Ninety seconds covers a cold start on a phone that has been asleep - opening the encrypted
 * database, reconnecting, restoring the session. Long enough that a slow morning does not lose the
 * run; short enough that a genuinely signed-out app says so rather than holding the wake-up open.
 */
private const val TELEGRAM_READY_TIMEOUT_MILLISECONDS = 90_000L

/**
 * How far back an opinion reads the price feed, in calendar days.
 *
 * Sized for the longest average it has to produce: fifty *sessions* is about seventy days once
 * weekends and holidays are taken out, and a year of calendar days reaches that comfortably even
 * on a stock that has been suspended for a stretch. Read once per press and never held.
 */
private const val OPINION_HISTORY_DAYS = 400L

class LiveAppState(
    /**
     * The application context, not an activity's.
     *
     * Held for the length of the process, which is exactly what an application context is for and
     * exactly what an activity's must never be used for. Everything reached through it here is
     * either a system service or a file.
     */
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val analysisRepository: AnalysisRepository,
    private val localDataStore: LocalDataStore,
    /**
     * How to reach Telegram, rather than a Telegram already reached.
     *
     * A function because constructing [TelegramRepository] starts TDLib there and then - it opens
     * its database and connects - and a process the alarm woke to fetch prices has no use for a
     * Telegram session. Behind the lazy below, so every one of the twenty-odd places that reads
     * `telegramRepository` is unchanged and the first of them to run is what starts it.
     */
    private val telegramProvider: () -> TelegramRepository,
    private val priceRepository: PriceRepository,
    private val intradayRepository: IntradayRepository,
    /** The shipped prompt, which every generated version is composed from. */
    private val promptStore: PromptStore,
    /**
     * The Ask AI prompt, which shares nothing with the one above.
     *
     * A separate store rather than a second method on [promptStore] so an opinion can never pick up
     * the analysis prompt, the wording rules folded into it, or its schema. See [OpinionPromptStore].
     */
    private val opinionPromptStore: OpinionPromptStore,
    /**
     * Where a newer build is found and fetched from.
     *
     * Null in tests and nowhere else: it reaches the network and the package manager, neither of
     * which a unit test has, and every path here treats its absence as "no update to offer" rather
     * than as a failure.
     */
    private val updateRepository: UpdateRepository? = null,
    /** Announces a run that has started; supplied by the app so this class stays testable. */
    private val analysisRunning: (sources: Int, model: String) -> Unit = { _, _ -> },
    /** Announces a finished run and the analysis it saved. */
    private val analysisFinished: (resultId: Long?, recommendations: Int) -> Unit = { _, _ -> },
    /** Withdraws the running announcement, with a reason when it failed. */
    private val analysisStopped: (reason: String?) -> Unit = {},
    /**
     * Books or cancels the daily look at the record; supplied by the app so this class stays
     * testable.
     *
     * Named for the work rather than for the overdue reminder that used to be its only reason to
     * exist. Two things now ride on that one daily wake - the overdue reminder and the trade
     * status notifications, which need it for the endings the calendar brings about rather than
     * the market - so it is wanted while *either* is on and cancelled only when both are off.
     */
    private val dailyCheckChanged: (wanted: Boolean) -> Unit = {},
    /**
     * Says what the market has just done to a trade; supplied by the app, like every other
     * announcement here, so a test can watch what would have been said without a notification
     * manager being anywhere near it.
     */
    private val tradesChanged: (changes: List<TradeChange>) -> Unit = {},
    /**
     * Says a stock has traded into the buy zone of a call nobody has taken; supplied like the rest.
     */
    private val callsChanged: (changes: List<CallChange>) -> Unit = {},
    /**
     * Says a trade has come within reach of its stop or of target 2; supplied like the rest.
     *
     * The only alert here that speaks while something can still be done, which is why it is its own
     * callback rather than more work inside [tradesChanged]: that one reports what the market has
     * finished doing, and folding the two together would put one switch over two questions.
     */
    private val approachesChanged: (changes: List<ApproachChange>) -> Unit = {},
    /** Says what a whole session did, once, after the close; supplied like the rest. */
    private val sessionSummarised: (digest: SessionDigest) -> Unit = {},
    /**
     * Says the feed has gone quiet about stocks the record names; supplied like the rest.
     *
     * Takes the count and the calls it is costing rather than the report, because a notifier has no
     * business reasoning about a `PriceHealthReport` - the figure that matters was already decided
     * by the card that draws one.
     */
    private val feedQuiet: (stocks: Int, callsHeld: Int) -> Unit = { _, _ -> },
    /** Says a scheduled analysis was due and did not happen; supplied like the rest. */
    private val scheduleMissed: (schedule: AnalysisSchedule) -> Unit = {},
    /**
     * How many trades are overdue, for the launcher shortcut that counts them.
     *
     * Reported on every rebuild rather than only when the number moves: a launcher can drop a
     * dynamic shortcut when it is updated or when its slots are needed, and one that is only ever
     * pushed on a change would stay gone until the count happened to change again.
     */
    private val overdueCounted: (count: Int) -> Unit = {},
    /**
     * Books or cancels this phone's schedule alarm; supplied by the app so this class stays testable.
     */
    private val schedulesChanged: (
        schedules: List<AnalysisSchedule>,
        marketRefresh: Boolean,
        closeSweep: Boolean,
    ) -> Unit = { _, _, _ -> },
    /**
     * Whether this process was started by the clock rather than by its owner.
     *
     * Headless leaves [enterForeground] unrun, so nothing here connects to Telegram, syncs, or asks
     * GitHub about a newer build. See that function for why. False everywhere but the scheduled
     * worker, including in tests, so the ordinary start is exactly what it always was.
     */
    /**
     * Where each model's token spend is tallied.
     *
     * Null in tests, like [updateRepository]: it writes to the device, and nothing here fails
     * without it - the screens that read it simply have nothing to show.
     */
    private val modelUsageStore: ModelUsageStore? = null,
    private val headless: Boolean = false,
) : AppState {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Started by whichever path first needs Telegram, and never by merely existing. */
    private val telegramRepository: TelegramRepository by lazy { telegramProvider() }
    override var destination by mutableStateOf(AppDestination.ANALYZE)
        private set

    /**
     * What each tab had open and filtered to, kept out of the composition so a fold cannot take it.
     *
     * Here for the same reason [destination] is: the shell rebuilds the whole page when the phone
     * changes shape, and anything a screen was holding in a `remember` goes with it. See [PageState].
     */
    override val pages = PageState()
    override var cloudConfiguration by mutableStateOf(settingsRepository.load())
        private set
    override var appPreferences by mutableStateOf(settingsRepository.loadPreferences())
        private set
    // Intersected with OFFERED rather than taken whole: the stored preference shipped with every
    // type ticked, so a phone that never unticked voice would go on sending it with no control
    // left on screen to stop it. See AnalysisContentType.OFFERED.
    override var selectedContentTypes by mutableStateOf(
        appPreferences.defaultContentTypes.intersect(AnalysisContentType.OFFERED),
    )
        private set
    override var analysisMode by mutableStateOf(AnalysisMode.NEXT_DAY)
        private set
    override var recommendationTargetDate by mutableStateOf(egxTargetSession())
        private set
    override var settingsMessage by mutableStateOf<String?>(null)
        private set

    /** True once the provider has accepted the key, false once it has rejected it, null untested. */
    override var credentialVerified by mutableStateOf<Boolean?>(null)
        private set
    override var promptHistory by mutableStateOf(settingsRepository.promptHistory())
        private set
    override var catalogMessage by mutableStateOf("${EgxCatalog.size()} seed stocks available offline.")
        private set
    // Seeded from disk rather than empty: the picker is the only safe way to choose a model, and a
    // list that died with the process meant every cold start offered a text field instead.
    override var availableModels by mutableStateOf(settingsRepository.modelCatalog(cloudConfiguration.provider))
        private set

    /**
     * What each model has cost, read once rather than on every row drawn.
     *
     * The tally is on disk and the picker draws hundreds of rows, so it is read into state and
     * refreshed at the two moments it can have changed: a request having been made, and the picker
     * being opened.
     */
    override var modelUsage by mutableStateOf(modelUsageStore?.all().orEmpty())
        private set
    override var modelListLoading by mutableStateOf(false)
        private set
    override var modelListMessage by mutableStateOf<String?>(null)
        private set
    // Starts empty: the chat list belongs to the Telegram session, so nothing from a previous run
    // is shown before Telegram reports what actually exists now.
    override var channels by mutableStateOf(emptyList<ChannelSelection>())
        private set
    override var telegramAuthState by mutableStateOf(TelegramAuthState())
        private set
    override var telegramSourceDate by mutableStateOf(LocalDate.now())
        private set
    override var telegramSyncMessage by mutableStateOf<String?>(null)
        private set

    /**
     * When this reader last had Insights open, which is what "new since you last looked" means.
     *
     * Held as state rather than read from the repository at each draw, so the chips vanish the
     * moment it is marked rather than at the next recomposition that happens to re-read the disk.
     *
     * **Zero means a first look and marks nothing.** An install that has never opened the tab would
     * otherwise greet its reader with every session flagged as new, which says the same as none of
     * them being flagged and costs a page of chips to say it.
     */
    override var insightsSeenAt by mutableStateOf(settingsRepository.insightsSeenAt())
        private set

    /**
     * Records that the reader has finished looking at Insights.
     *
     * Called on leaving the tab and on the app going to the background, which are the two ways a
     * look ends. Deliberately **not** on arriving: a session marked read the instant the tab
     * composed would be marked read by the pager, which keeps the neighbouring pages composed and
     * would clear the chips of a tab nobody had turned to.
     */
    override fun notificationsPermitted(): Boolean = AnalysisNotifier(context).permitted()

    override fun openNotificationSettings() {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    override fun exactAlarmsAllowed(): Boolean = JobScheduler(context).canScheduleExact()

    override fun openExactAlarmSettings() {
        context.startActivity(
            Intent(
                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    override fun batteryOptimizationExempt(): Boolean =
        context.getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(context.packageName)

    override fun openBatteryOptimizationSettings() {
        context.startActivity(
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    override fun traceRoot(): File = File(context.filesDir, RequestTrace.TRACE_ROOT)

    override fun shareReport(saved: SavedAnalysis) {
        val report = reportFor(saved)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/markdown"
            putExtra(Intent.EXTRA_SUBJECT, report.title)
            putExtra(Intent.EXTRA_TEXT, report.markdown)
        }
        context.startActivity(
            Intent.createChooser(intent, "Share EGX analysis report")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    override suspend fun saveReportToDownloads(saved: SavedAnalysis) {
        if (!exportable(saved)) return
        runCatching { withContext(Dispatchers.IO) { saveToDownloads(context, saved) } }
            .onSuccess {
                statusMessage = StatusMessage("Saved to Downloads/$it", succeeded = true)
            }
            .onFailure { statusMessage = spreadsheetFailure("save", it) }
    }

    override suspend fun exportReport(saved: SavedAnalysis) {
        if (!exportable(saved)) return
        runCatching { withContext(Dispatchers.IO) { stageExport(context, saved) } }
            .onSuccess { file ->
                context.startActivity(
                    Intent.createChooser(exportIntent(context, file), "Send EGX analysis")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            .onFailure { statusMessage = spreadsheetFailure("write", it) }
    }

    /**
     * Whether there is a table to export at all, and the toast when there is not.
     *
     * Analyses saved before the consolidated contract have only the flat list the screen falls back
     * to. An empty sheet of eighteen headings is a worse answer than saying so.
     */
    private fun exportable(saved: SavedAnalysis): Boolean {
        if (saved.result.consolidated.isNotEmpty()) return true
        statusMessage = StatusMessage(
            "This run predates the table, so there is nothing to export",
            succeeded = false,
        )
        return false
    }

    private fun spreadsheetFailure(verb: String, error: Throwable) = StatusMessage(
        "Could not $verb the Excel file: ${error.message ?: "unknown error"}",
        succeeded = false,
    )

    override suspend fun saveDatabaseToDownloads(): String = withContext(Dispatchers.IO) {
        saveDatabaseToDownloads(context, databaseFile(), ::checkpointDatabase)
    }

    private val backupFolderUri: Uri? get() = backupFolder?.let(Uri::parse)

    override fun holdsBackupFolder(): Boolean =
        backupFolderUri?.let { holdsBackupFolder(context, it) } == true

    override fun backupsInFolder(): List<String> =
        backupFolderUri?.takeIf { holdsBackupFolder() }?.let { backupsInFolder(context, it) }
            .orEmpty()

    override fun backupFolderLabel(uri: String): String = backupFolderLabel(Uri.parse(uri))

    override fun keepBackupFolder(uri: String) {
        val picked = Uri.parse(uri)
        // Persisted, or the grant dies with this process and the daily backup silently stops on the
        // next launch - the failure this whole feature exists to prevent, reproduced inside it.
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                picked,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        saveBackupFolder(uri)
        statusMessage = StatusMessage(
            "Backing up to ${backupFolderLabel(uri)}",
            succeeded = true,
        )
    }

    override suspend fun writeBackup(): String = withContext(Dispatchers.IO) {
        writeBackupTo(
            context = context,
            folder = backupFolderUri?.takeIf { holdsBackupFolder() },
            database = databaseFile(),
            settings = settingsDocument(),
            device = backupDevice(),
            checkpoint = ::checkpointDatabase,
        )
    }

    override suspend fun restoreFromBackup(source: String): RestoreOutcome {
        val record = withContext(Dispatchers.IO) { readBackup(context, Uri.parse(source)) }
        return restoreFrom(record)
    }

    override fun markInsightsSeen() {
        val at = System.currentTimeMillis()
        settingsRepository.recordInsightsSeen(at)
        insightsSeenAt = at
    }

    /** One-shot banner text for an action that has just finished. */
    override var statusMessage by mutableStateOf<StatusMessage?>(null)

    /** Non-null while a named action is running, so the shell can show progress. */
    override var busyLabel by mutableStateOf<String?>(null)
        private set

    /**
     * Whether the running action names itself in the header.
     *
     * False where the screen the press came from already says it. An Ask AI card sits under the
     * reader's thumb reading "Asking…" for as long as the request is out, and the header repeating
     * that a few lines above is the same sentence twice. The bar under the header still runs -
     * it is the one part of the announcement a card cannot carry.
     */
    override var busyAnnounced by mutableStateOf(true)
        private set

    override fun consumeStatusMessage() {
        statusMessage = null
    }

    /**
     * Runs a user-triggered action with progress and a plain-language outcome.
     *
     * Failures surface the provider's own message where there is one, since "no credit" or
     * "wrong key" is far more use than a generic failure.
     */
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
    ) {
        busyLabel = label
        busyAnnounced = announce
        statusMessage = null
        try {
            val outcome = block()
            if (announce) statusMessage = StatusMessage(success(outcome), succeeded = true)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            statusMessage = StatusMessage(
                error.message?.takeIf(String::isNotBlank) ?: "$label failed",
                succeeded = false,
            )
        } finally {
            busyLabel = null
            busyAnnounced = true
        }
    }
    override var inputs by mutableStateOf<List<AnalysisInput>>(emptyList())
        private set
    override var activeSourceChannelId by mutableStateOf(channels.firstOrNull { it.selected }?.id)
        private set
    private var sourceChannelIds by mutableStateOf<Map<String, Long?>>(emptyMap())
    private var telegramTraces by mutableStateOf<Map<String, SourceTrace>>(emptyMap())

    /** What the last preview pulled from Telegram, newest first, so it can be shown before paying. */
    override val telegramSources: List<SourceTrace>
        get() = telegramTraces.values.sortedByDescending(SourceTrace::timestamp)

    /**
     * Sources added by hand, which are the only ones worth listing individually.
     *
     * Telegram's are shown by the source preview and can be changed by choosing different chats or
     * a different window; listing them again only invited removing one at a time from a set the
     * next load would rebuild anyway.
     */
    override val manualInputs: List<AnalysisInput>
        get() = inputs.filterNot { it.sourceId in telegramTraces.keys }
    override var savedResults by mutableStateOf(localDataStore.results())
        private set

    /**
     * The wording the app recognises, shipped rows and the user's together.
     *
     * Held here rather than read per run so the Settings screen and the next analysis are looking
     * at the same set - the filter and the prompt disagreeing about which rules are on would be
     * invisible until a report came out wrong.
     */
    override var wordingRules by mutableStateOf(localDataStore.wordingRules())
        private set

    override val ruleSet: RuleSet get() = RuleSet(wordingRules)

    /**
     * Whether custom wording reaches the prompt at all.
     *
     * Off means the shipped prompt is sent exactly as it is. It does not delete a rule or stop the
     * local ones working - "restore the default" has to mean something narrower than "throw away
     * what I configured", or nobody dares press it.
     */
    override var useDefaultPromptOnly by mutableStateOf(settingsRepository.useDefaultPromptOnly())
        private set

    override var promptVersions by mutableStateOf(localDataStore.promptVersions())
        private set

    /** The version a run would use right now. */
    override var activePrompt by mutableStateOf(composePrompt())
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

    override fun usePromptDefaultOnly(value: Boolean) {
        useDefaultPromptOnly = value
        settingsRepository.saveUseDefaultPromptOnly(value)
        publishSettings()
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
    override fun saveWordingRule(rule: WordingRule): RuleRejection? {
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
    override fun deleteWordingRule(rule: WordingRule) {
        if (rule.origin == RuleOrigin.BUILT_IN) {
            saveWordingRule(rule.copy(enabled = false))
            return
        }
        localDataStore.buryWordingRule(rule.id, System.currentTimeMillis(), deviceName)
        wordingRules = localDataStore.wordingRules()
        regeneratePrompt("Deleted \"${rule.phrase}\"")
    }

    override fun setWordingRuleEnabled(rule: WordingRule, enabled: Boolean) {
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
    override var unreadableResults by mutableIntStateOf(localDataStore.unreadableResults)
        private set
    override var selectedResult by mutableStateOf<SavedAnalysis?>(savedResults.firstOrNull())
        private set
    override var analysisStatus by mutableStateOf(AnalysisStatus.IDLE)
        private set

    /**
     * When the running analysis started, so the button can count.
     *
     * A run takes anywhere from seventy seconds to eleven minutes, and one has already died on a
     * timeout, so "how long has this been going" is a real question while waiting on it.
     */
    override var analysisStartedAt by mutableStateOf<Instant?>(null)
        private set
    override var analysisMessage by mutableStateOf<String?>(null)
        private set
    private var activeRequestId: String? = null
    private var analysisJob: Job? = null

    /** A saved analysis the user asked to open from a notification, cleared once shown. */
    override var pendingResultId by mutableStateOf<Long?>(null)
        private set

    override fun openSavedResult(id: Long, returnTo: NavStop?) {
        returnTo?.let(nav::push)
        pendingResultId = id
        destination = AppDestination.RESULTS
    }

    override fun consumePendingResult() {
        pendingResultId = null
    }

    /**
     * A trade to reveal on the Portfolio tab, named by its position id.
     *
     * The two tabs answer different questions about one call - what the channel's levels did, and
     * what the user's own money did - and reading one used to mean finding the other by hand. A
     * card that has a counterpart carries a press to it, and the counterpart presses back.
     */
    override var pendingPositionId by mutableStateOf<String?>(null)
        private set

    /** A call to reveal on the Insights tab, named by the same id: one call is one holding. */
    override var pendingCallId by mutableStateOf<String?>(null)
        private set

    override fun openPosition(id: String, returnTo: NavStop?) {
        returnTo?.let(nav::push)
        // The trip the user just made is over. Left set, a highlight still counting down on the tab
        // being left would fire again the moment they pressed their way back to it.
        pendingCallId = null
        pendingPositionId = id
        destination = AppDestination.PORTFOLIO
    }

    override fun consumePendingPosition() {
        pendingPositionId = null
    }

    override fun openCall(id: String, returnTo: NavStop?) {
        returnTo?.let(nav::push)
        pendingPositionId = null
        pendingCallId = id
        destination = AppDestination.INSIGHTS
    }

    override fun consumePendingCall() {
        pendingCallId = null
    }

    /**
     * The trade whose sale dialog a notification action asked for, cleared once it has opened.
     *
     * A sale needs a price and a date, so **Record sale** can never be a one-tap action in the
     * shade - the two figures are the user's own and the app must not invent either. What the
     * action can do is land them on the two fields instead of on a tab with a card to find, which
     * is the whole of the distance between knowing a trade stopped out and having recorded it.
     *
     * Separate from [pendingPositionId] although the same path sets both. Revealing a card and
     * opening a price dialog on it are different requests: every cross-tab press reveals, and one
     * entrance that always did both would put a price field in front of the reader every time they
     * followed a call through to its trade.
     */
    override var pendingSellPositionId by mutableStateOf<String?>(null)
        private set

    /**
     * Reveals a trade and opens its sale dialog, for the Record sale action in the shade.
     *
     * `returnTo = null` for the reason every notification entrance uses it: the app may not have
     * been running, so there is no tab to go back to.
     */
    override fun openPositionToSell(id: String) {
        openPosition(id, returnTo = null)
        pendingSellPositionId = id
    }

    override fun consumePendingSell() {
        pendingSellPositionId = null
    }

    /**
     * One stored trade by its id, for a notification action acting with no screen in front of it.
     *
     * Off [positions] rather than the derived portfolio: a broadcast can arrive before the first
     * recompute has run, and [setKeepOpen] takes the stored row rather than the view over it.
     */
    override fun positionFor(id: String): Position? = positions.firstOrNull { it.id == id }

    /**
     * The stock whose sheet is open, or null.
     *
     * Here rather than in a screen for the reason every other piece of navigation is: the shell
     * draws one UI beside a rail and another under a pill, and folding the phone disposes one and
     * composes the other from nothing. A sheet remembered inside either would close itself the
     * moment the phone was opened, which is precisely when a reader has the room to read it.
     *
     * It also has to be one sheet rather than one per screen. A ticker is pressable on four tabs;
     * four hosts would be four places for the same sheet to be drawn slightly differently, and two
     * of them could be open at once on the compact layout, where the pager keeps the neighbouring
     * pages composed.
     *
     * **It changes no destination and records no return.** Opening it leaves the reader on the tab
     * they were reading, which is the point of it being a sheet - see [StockSheet].
     */
    override var openStockTicker by mutableStateOf<String?>(null)
        private set

    /** Opens the sheet on one stock, from wherever its ticker was pressed. */
    override fun openStock(ticker: String) {
        openStockTicker = ticker.trim().takeIf(String::isNotBlank)
    }

    override fun closeStock() {
        openStockTicker = null
    }

    /** A saved analysis covering exactly this session and these chats, awaiting a decision. */
    override var duplicateOfSelection by mutableStateOf<SavedAnalysis?>(null)
        private set

    override fun dismissDuplicateWarning() {
        duplicateOfSelection = null
    }

    /**
     * The most recent saved analysis of the same session with the same chats.
     *
     * Only an exact match counts. A run over different chats answers a different question even on
     * the same day, so warning about it would train the user to dismiss the warning.
     *
     * The **most recent** matters now that a day can hold several reports of one session: a
     * scheduled run compares what it just read against this one to decide whether anything has been
     * posted since, and comparing against the oldest would find every later message new and pay
     * every time. It is the first match because [LocalDataStore.results] is ordered
     * `completed_at DESC` - which is a dependency worth naming, since reordering that query would
     * quietly turn a money guard into a rubber stamp.
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
    override fun startAnalysis(confirmed: Boolean) {
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

    /** How every saved call turned out, recomputed whenever the analyses or the prices change. */
    override var performance by mutableStateOf(PerformanceReport())
        private set

    /**
     * Which stocks the price feed has gone quiet about, and what it is costing the record.
     *
     * Derived beside [performance] and from the same read, never cached: it is a reading of the
     * prices on disk, exactly as an outcome is, so it is right after a restart without anything
     * having been stored. Built from the **whole** record rather than a filtered view - a channel
     * filter is a view of the calls, never a claim about which prices are broken.
     */
    override var priceHealth by mutableStateOf(PriceHealthReport())
        private set
    override var pricesRefreshing by mutableStateOf(false)
        private set

    /**
     * The trades the user has actually taken, and what the market has since done with them.
     *
     * Held apart from [performance] on purpose. That report judges the sources on the levels they
     * printed; this one judges the user's own trades on the prices they paid, and folding either
     * into the other would leave both answering a question nobody asked.
     */
    override var portfolio by mutableStateOf(Portfolio())
        private set

    /**
     * What the market did on the most recent session the price feed actually has.
     *
     * The card on the Portfolio and Insights tabs reads this and nothing else, so both are drawing
     * one answer. Null until the first recompute, and on an install with no prices at all - there
     * is no session to report on, which is different from a session on which nothing happened.
     *
     * The session is taken from the **prices**, never from a clock. That is what makes the list
     * hold still: it is a function of one session's prices, and a closed session's prices do not
     * move, so the card a reader saw at noon is the card they see at six. It turns over when a
     * refresh first brings back a row for the next session, which is the exchange's own answer to
     * when the next session started - and on a holiday it correctly turns over not at all, where a
     * calendar rule would have claimed a session that never traded.
     */
    override var sessionDigest by mutableStateOf<SessionDigest?>(null)
        private set

    private var positions = localDataStore.positions()

    /**
     * What Ask AI has said, by the call it was asked about.
     *
     * Loaded whole at start rather than queried per card: the map is one small row per call the
     * user has actually pressed the button on, and a card has to know on sight whether to offer
     * "Ask AI" or "AI opinion" without a database read while the list scrolls.
     */
    override var opinions by mutableStateOf(localDataStore.stockOpinions())
        private set

    /** Non-null while one opinion is being fetched, so only that card shows a spinner. */
    override var opinionPending by mutableStateOf<String?>(null)
        private set

    override fun opinionFor(call: ScoredCall): StockOpinion? =
        opinions[opinionId(call.ticker, call.openedOn, call.channel)]

    /** What an opinion request would run on and whether it would search, for the confirm dialog. */
    override fun opinionModel(): String = settingsRepository.opinionModel(cloudConfiguration.provider)
        .ifBlank { cloudConfiguration.model }

    override fun opinionSearchEnabled(): Boolean = settingsRepository.opinionSearchEnabled()

    /** How far back a searched request looks for news, in days. */
    override fun opinionNewsWindowDays(): Int = settingsRepository.opinionNewsWindowDays()

    override fun updateOpinionNewsWindow(days: Int) {
        settingsRepository.saveOpinionNewsWindowDays(days)
        opinionSettingsRevision += 1
    }

    /**
     * How many web results a searched request asks for.
     *
     * On the screen because it is the setting that moves the bill most: every result is injected
     * into the request whole, so twelve of them is several thousand characters the model is
     * charged for reading. It shipped as a stored value with no control, which meant a default
     * nobody could turn down.
     */
    override fun opinionSearchResults(): Int = settingsRepository.opinionSearchResults()

    override fun updateOpinionSearchResults(count: Int) {
        settingsRepository.saveOpinionSearchResults(count)
        opinionSettingsRevision += 1
    }

    override fun opinionDeepSearch(): Boolean = settingsRepository.opinionDeepSearchEnabled()

    override fun updateOpinionDeepSearch(enabled: Boolean) {
        settingsRepository.saveOpinionDeepSearchEnabled(enabled)
        opinionSettingsRevision += 1
    }

    override fun updateOpinionModel(model: String) {
        settingsRepository.saveOpinionModel(cloudConfiguration.provider, model)
        // Nothing derives from it, but the Settings summary reads it back through a snapshot that
        // only recomposes when something observable moves.
        opinionSettingsRevision += 1
    }

    override fun updateOpinionSearch(enabled: Boolean) {
        settingsRepository.saveOpinionSearchEnabled(enabled)
        opinionSettingsRevision += 1
    }

    /** Bumped whenever an Ask AI setting changes, so the Settings card redraws its summary. */
    override var opinionSettingsRevision by mutableStateOf(0)
        private set

    /**
     * Asks the model about one call and keeps what it says.
     *
     * A paid request, started only by the user pressing through the confirmation - never by a
     * refresh, a sync, or a card being drawn. The answer is stored against the call so re-opening
     * the sheet costs nothing; [askAgain] is the only way to pay twice.
     */
    override fun askAboutCall(call: ScoredCall, askAgain: Boolean) {
        val id = opinionId(call.ticker, call.openedOn, call.channel)
        if (opinionPending != null) return
        if (!askAgain && opinions.containsKey(id)) return
        val requestId = call.requestId
        if (requestId == null) {
            statusMessage = StatusMessage(
                "This call is not attached to a saved report, so an opinion could not be filed.",
                succeeded = false,
            )
            return
        }
        appScope.launch {
            opinionPending = id
            val model = opinionModel()
            val searched = settingsRepository.opinionSearchEnabled()
            val windowDays = settingsRepository.opinionNewsWindowDays()
            // One date for the whole press. Read twice, a request started either side of midnight
            // would search one window and file the answer against another.
            val today = LocalDate.now()
            try {
                runAction(
                    label = "Asking about ${call.ticker}",
                    success = { "${call.ticker}: ${it.verdict.arabic}" },
                    // The card says "Asking…" while this is out and the sheet opens itself with the
                    // answer the moment it lands, so a working line and a verdict in the header are
                    // both said twice. Only a failure is left for the header to carry.
                    announce = false,
                ) {
                    // Built once and used twice: it goes into the question, where it aims what the
                    // provider searches for, and its preamble goes to OpenRouter, where it decides
                    // which of the results that came back are still inside the window.
                    val brief = if (searched) {
                        OpinionSearchBrief.query(call, today, windowDays)
                    } else {
                        null
                    }
                    val answer = withContext(Dispatchers.IO) {
                        // Read here rather than taken from the call: a call carries only the
                        // sessions it was judged on, and an average of ten sessions called a
                        // fifty-session average is a wrong number, not a rounded one.
                        val history = localDataStore.sessionsFrom(
                            Scoring.normalizeTicker(call.ticker),
                            today.minusDays(OPINION_HISTORY_DAYS),
                        )
                        analysisRepository.ask(
                            OpinionRequest(
                                requestId = "opinion-$id",
                                systemPrompt = opinionPromptStore.opinionPrompt(),
                                question = OpinionPrompt.build(
                                    call = call,
                                    latest = performance.latestPrices[call.ticker],
                                    channel = performance.channels
                                        .firstOrNull { it.channel == call.channel },
                                    held = heldFor(call.ticker, call.openedOn),
                                    today = today,
                                    history = history,
                                    otherCalls = callsOn(call.ticker),
                                    search = brief,
                                ),
                                model = model,
                                search = searched,
                                searchResults = settingsRepository.opinionSearchResults(),
                                searchPrompt = brief?.let {
                                    OpinionSearchBrief.resultPreamble(today, windowDays)
                                },
                                deepSearch = settingsRepository.opinionDeepSearchEnabled(),
                            ),
                        )
                    }
                    val opinion = OpinionParser.parse(
                        response = answer,
                        model = model,
                        askedOn = today,
                        searched = searched,
                        // Zero where nothing was searched, so the sheet says "no live news" rather
                        // than "nothing in 15 days" about a request that never looked.
                        newsWindowDays = if (searched) windowDays else 0,
                    )
                    withContext(Dispatchers.IO) {
                        localDataStore.saveStockOpinion(
                            id = id,
                            requestId = requestId,
                            ticker = call.ticker,
                            openedOn = call.openedOn,
                            channel = call.channel,
                            opinion = opinion,
                        )
                    }
                    opinions = opinions + (id to opinion)
                    // Ask AI spends too, and leaves no report behind to record it.
                    refreshModelUsage()
                    opinion
                }
            } finally {
                opinionPending = null
            }
        }
    }

    /**
     * Every call the report holds on one stock, whoever made it.
     *
     * Two things at once, and deliberately: the ones still open are other channels crowding into
     * the same trade, and the settled ones are the only record the app has of what happened the
     * last times this particular stock was recommended. Neither is on the card, and no web search
     * would find either - they are measurements of this user's own history.
     */
    private fun callsOn(ticker: String): List<ScoredCall> {
        val wanted = Scoring.normalizeTicker(ticker)
        return performance.sessions
            .flatMap(ScoredSession::calls)
            .filter { Scoring.normalizeTicker(it.ticker) == wanted }
    }

    /** Forgets one opinion, so the card offers to ask again from nothing. */
    override fun deleteOpinion(call: ScoredCall) {
        val id = opinionId(call.ticker, call.openedOn, call.channel)
        localDataStore.deleteStockOpinion(id)
        opinions = opinions - id
    }

    /**
     * The position taken on one call, if there is one.
     *
     * Keyed by the call rather than by the stock: the same stock recommended for two sessions is two
     * trades, and a card must only ever light up for its own.
     */
    override fun heldFor(ticker: String, recommendationDate: LocalDate?): PositionView? =
        portfolio.heldFor(ticker, recommendationDate)

    /**
     * Records a trade against a recommendation.
     *
     * The call's levels are copied in rather than referenced: the report can be deleted or replaced
     * by a later run of the same session, and neither may rewrite a trade that has already happened.
     * Pressing Bought again on a card that already has a position replaces it, so a mistyped price
     * is corrected rather than duplicated.
     */
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
    ) {
        val normalized = Scoring.normalizeTicker(ticker)
        val existing = positions.firstOrNull {
            it.ticker == normalized && it.recommendationDate == recommendationDate
        }
        val window = Scoring.clampWindow(windowSessions)
        val position = Position(
            ticker = normalized,
            recommendationDate = recommendationDate,
            companyEnglish = companyEnglish,
            companyArabic = companyArabic,
            channel = channel,
            entryPrice = entryPrice,
            entryDate = entryDate,
            entryLow = entryLow,
            entryHigh = entryHigh,
            target1 = target1,
            target2 = target2,
            stopLoss = stopLoss,
            // The window becomes this trade's deadline and stays with it: changing the default
            // later must not silently move a deadline a trade was taken under. Only an edit of
            // this trade can move it, which is the user doing it on purpose. It is the user's own
            // clock and nothing else - the channel that made the call is judged on how long the
            // call took, not on how long this reader gave it.
            windowSessions = window,
            // Custom means the user typed over what they were offered. Recorded rather than
            // recomputed later: what was offered moves, the choice did not.
            windowCustom = window != Scoring.clampWindow(offeredWindow),
            // What the channel called it, which no edit of this trade can change: typing a longer
            // window over the two it was offered is the user deciding to hold a T+1 call, not the
            // card ceasing to have been one.
            isTPlusOne = isTPlusOne,
            // Re-recording a mistyped price must not quietly cancel a Keep Open already set.
            keepOpen = existing?.keepOpen ?: false,
            // Kept from the first purchase, so re-recording a trade does not restart its life.
            openedAt = existing?.openedAt ?: Instant.now(),
            updatedAt = System.currentTimeMillis(),
            updatedBy = deviceName,
        )
        localDataStore.savePosition(position)
        positions = localDataStore.positions()
        statusMessage = StatusMessage("$normalized bought at ${formatPrice(entryPrice)}", true)
        publishPosition(position, deleted = false)
        appScope.launch {
            // A stock only just named may have no stored history at all, and a position with no
            // price says nothing until it does.
            priceStocksWithNoHistory(listOf(normalized))
            recomputePortfolio()
        }
    }

    /**
     * Closes a position at the price the user actually sold for.
     *
     * Immediate, whatever the recommendation's window still says: the trade is over when the user
     * says it is over, and the window only ever decided when to stop watching.
     */
    /**
     * Closes a position at the price - or the two prices - the user says they got.
     *
     * The blend is what the row carries as its exit, so every figure downstream goes on reading one
     * price; the legs ride along beside it so the card can say how that one price was arrived at.
     * All four are null on a sale made at a single price, which keeps such a sale byte-for-byte the
     * row it has always been.
     */
    override fun recordSale(position: Position, sale: Sale) {
        val closed = position.copy(
            exitPrice = sale.blended,
            exitDate = sale.closedOn,
            exitPrice1 = sale.price1.takeIf { sale.inTwoParts },
            exitDate1 = sale.openedOn,
            exitPrice2 = sale.price2.takeIf { sale.inTwoParts },
            exitSplitPct = sale.splitPct.takeIf { sale.inTwoParts },
            closedManually = true,
            updatedAt = System.currentTimeMillis(),
            updatedBy = deviceName,
        )
        localDataStore.savePosition(closed)
        positions = localDataStore.positions()
        statusMessage = StatusMessage(
            "${position.ticker} closed at ${formatPrice(sale.blended)}" +
                // The parts, where there were parts. The blend alone is a price the user never
                // typed, and a line reporting it back without saying so reads like a mistyped sale.
                if (sale.inTwoParts) {
                    " · ${formatPrice(sale.splitPct)}% at ${formatPrice(sale.price1)}, " +
                        "${formatPrice(FULL_SPLIT_PCT - sale.splitPct)}% at " +
                        formatPrice(sale.price2)
                } else {
                    ""
                },
            true,
            // The one genuinely irreversible thing a reader does here, and until now the only way
            // back from a mistyped sale was Edit trade, which does not clear one. The offer lives
            // as long as the line does - four seconds - which is the window in which a wrong price
            // is noticed at all.
            undo = StatusUndo("Undo") { reopenPosition(position) },
        )
        publishPosition(closed, deleted = false)
        appScope.launch { recomputePortfolio() }
    }

    /**
     * The whole holding at one price, which is a sale with one part.
     *
     * Kept so a caller with nothing to say about parts does not have to build a [Sale] to say
     * nothing - the `next` shell's sheet is one, and every test that closes a trade is another.
     */
    override fun recordSale(position: Position, exitPrice: Double, exitDate: LocalDate) =
        recordSale(position, Sale(price1 = exitPrice, date1 = exitDate))

    /**
     * Puts a trade back the way it was before a sale was recorded against it.
     *
     * Takes the **position as it stood**, not an id: the row on disk has already been replaced by
     * the time this can be pressed, so anything read back would be the closed one. Restoring the
     * original also restores its `updatedAt`, which is deliberate - a revision that travelled to
     * another device is overtaken by this one carrying a newer stamp, so the sale is undone there
     * too rather than being pushed back by the next sync.
     *
     * Announced like any other edit, and silent to the notifications for the reason every user edit
     * is: `recomputePortfolio` without `announceChanges` updates the record without the phone
     * reporting the button that was just pressed back to the person who pressed it.
     */
    override fun reopenPosition(position: Position) {
        val reopened = position.copy(updatedAt = System.currentTimeMillis(), updatedBy = deviceName)
        localDataStore.savePosition(reopened)
        positions = localDataStore.positions()
        statusMessage = StatusMessage("${position.ticker} is open again", true)
        publishPosition(reopened, deleted = false)
        appScope.launch { recomputePortfolio() }
    }

    /**
     * Corrects what a trade was recorded at, and how long it was given.
     *
     * The call it belongs to and any recorded sale are untouched. The window is not: a deadline
     * misjudged at the moment of buying is worth correcting, and this is the only thing allowed to
     * move one. Changing it moves the deadline, so it can close a position that was running or
     * reopen one the deadline had closed - which is the point of being asked for it.
     */
    override fun reprice(
        position: Position,
        entryPrice: Double,
        entryDate: LocalDate,
        windowSessions: Int,
    ) {
        val window = Scoring.clampWindow(windowSessions)
        val movedWindow = window != position.windowSessions
        val corrected = position.copy(
            entryPrice = entryPrice,
            entryDate = entryDate,
            windowSessions = window,
            // Here the offered value is the trade's own window rather than the global setting, so
            // custom means the same thing it means at purchase: typed over what was on screen. A
            // trade already marked stays marked - editing only its price is not un-choosing.
            windowCustom = position.windowCustom || movedWindow,
            updatedAt = System.currentTimeMillis(),
            updatedBy = deviceName,
        )
        localDataStore.savePosition(corrected)
        positions = localDataStore.positions()
        statusMessage = StatusMessage(
            if (movedWindow) {
                "${position.ticker} now runs $window ${window.sessionWord()}"
            } else {
                "${position.ticker} entry now ${formatPrice(entryPrice)}"
            },
            true,
        )
        publishPosition(corrected, deleted = false)
        appScope.launch { recomputePortfolio() }
    }

    /**
     * Lets a trade outlive its deadline, or stops letting it.
     *
     * Almost nothing else closes a position carrying this - not the deadline, not target 1, not the
     * stop - so the Sold button becomes the way out, which is what the user asked for by pressing
     * it. Target 2 is the exception, and ends the trade regardless: it did what it was bought to do.
     * Turning this back off hands the position to the deadline again, and one already past it
     * settles on the next recompute.
     */
    override fun setKeepOpen(position: Position, keepOpen: Boolean, note: String?) {
        if (position.keepOpen == keepOpen && position.keepOpenNote == note) return
        val updated = position.copy(
            keepOpen = keepOpen,
            // Cleared when the trade goes back to its deadline: a reason for keeping something open
            // is nonsense on a trade that is no longer being kept open.
            keepOpenNote = if (keepOpen) note?.trim()?.takeIf(String::isNotBlank) else null,
            updatedAt = System.currentTimeMillis(),
            updatedBy = deviceName,
        )
        localDataStore.savePosition(updated)
        positions = localDataStore.positions()
        statusMessage = StatusMessage(
            if (keepOpen) {
                "${position.ticker} stays open until you sell it"
            } else {
                "${position.ticker} follows its deadline again"
            },
            true,
            // Reversible on its own through the card, so the offer here is a convenience rather
            // than a rescue - but it is the other switch that can silently change what a deadline
            // does to a trade, and it can now be pressed from a notification with the app closed.
            undo = StatusUndo("Undo") {
                setKeepOpen(position, keepOpen = !keepOpen, note = position.keepOpenNote)
            },
        )
        publishPosition(updated, deleted = false)
        appScope.launch { recomputePortfolio() }
    }

    /**
     * Removes a trade recorded by mistake, here and on every device.
     *
     * Buried rather than dropped: a row that simply vanished would be uploaded back by the next
     * device that still held it, and the delete would undo itself. The report it came from is
     * untouched either way - the recommendation is not the trade.
     */
    override fun deletePosition(position: Position) {
        val at = System.currentTimeMillis()
        localDataStore.buryPosition(position.id, at, deviceName)
        positions = localDataStore.positions()
        statusMessage = StatusMessage("${position.ticker} removed", succeeded = true)
        publishPosition(position.copy(updatedAt = at, updatedBy = deviceName), deleted = true)
        appScope.launch { recomputePortfolio() }
    }

    /**
     * Puts one revision in the sync channel, without making the user wait for it.
     *
     * The position is already saved before this runs, so a failure here costs nothing: the next
     * sync's diff finds the revision still unpublished and sends it then. That is why it is silent -
     * an error message about Telegram, raised while someone is recording a trade, would be about
     * something they did not ask for and cannot act on.
     */
    private fun publishPosition(position: Position, deleted: Boolean) {
        // Read back rather than sent as `{}`: whatever a newer app version wrote against this trade
        // has to travel with every revision, or this device erases it simply by editing the price.
        val unknown = localDataStore.unknownFor(position.id)
        publish { telegramRepository.uploadPosition(SyncedPosition(position, deleted, unknown)) }
    }

    /**
     * Work that should reach the channel now but must never hold up what triggered it.
     *
     * Off the main thread as well as off the caller's path: publishing stages a small file before it
     * sends, and that is a disk write on whatever thread asked for it.
     */
    private fun publish(block: suspend () -> Unit) {
        appScope.launch(Dispatchers.IO) { runCatching { block() } }
    }

    /**
     * Brings this device and the channel to the same set of positions.
     *
     * Revisions, not rows, exactly as the wording rules travel: a trade is edited where a report is
     * not, so the merge decides by (updatedAt, device) rather than by who uploaded last.
     */
    private suspend fun syncPositions(): Boolean {
        val mine = localDataStore.positionRevisions().map { revision ->
            SyncedPosition(revision.position, revision.deleted, revision.unknown)
        }
        val theirs = runCatching { telegramRepository.syncedPositions() }.getOrNull() ?: return false

        var changed = false
        val byId = mine.associateBy { it.position.id }
        theirs.forEach { incoming ->
            val here = byId[incoming.position.id]
            val newer = here == null ||
                incoming.position.updatedAt > here.position.updatedAt ||
                (
                    incoming.position.updatedAt == here.position.updatedAt &&
                        incoming.position.updatedBy > here.position.updatedBy
                    )
            if (newer) {
                localDataStore.adoptPosition(
                    incoming.position,
                    incoming.deleted,
                    incoming.unknown,
                )
                changed = true
            }
        }

        // Only what this device knows better. Re-uploading a revision the channel already holds
        // would grow the log without telling anyone anything.
        positionsToUpload(mine, theirs).forEach { revision ->
            runCatching { telegramRepository.uploadPosition(revision) }
        }

        if (changed) {
            positions = localDataStore.positions()
            recomputePortfolio(announceChanges = true)
        }
        return changed
    }

    /**
     * Recounts how overdue everything is, without touching the network.
     *
     * How late a trade is depends on today's date, and nothing tells the app the date has changed.
     * Left open overnight it would go on showing yesterday's count until something else happened to
     * recompute; called when the app comes back to the foreground, it is right whenever it is read.
     * Deliberately not a price refresh - the count is worked out from the stored deadline, so this
     * costs a database read and no requests.
     */
    override fun refreshOverdue() {
        appScope.launch { recomputePortfolio(announceChanges = true) }
    }

    /**
     * Rebuilds the portfolio, and decides whether the phone says anything about what changed.
     *
     * [announceChanges] is about who moved the trade, not about who is watching. The market
     * reaching a target and the user editing a window can both change a status, and only one of
     * them is news:
     * an app that buzzes about the button somebody just pressed is one whose notifications get
     * switched off. So every path records where each trade now stands - which is what stops a later
     * refresh announcing a change the user made themselves - and only the paths where the market or
     * the calendar moved are allowed to speak.
     */
    private suspend fun recomputePortfolio(announceChanges: Boolean = false) {
        val held = positions
        // The exchange's own calendar, not the phone's: a user abroad must not see a trade fall
        // a day further behind simply for having crossed a time zone.
        val today = LocalDate.now(ZoneId.of(EGX_ZONE))
        // And the exchange's own close, which is a different question from the date: a window whose
        // last session ended at 14:30 is spent this afternoon, while the trade it retires only
        // starts counting days overdue tomorrow. Read once for the whole rebuild, like the date.
        val finalThrough = ScheduleClock.lastFinalSession()
        val rebuilt = withContext(Dispatchers.IO) {
            // Read once for the whole rebuild rather than per position: it is one small table, and
            // a trade must be judged on the same reading of the feed as the call it came from.
            val breaks = localDataStore.priceBreakDates()
            PortfolioCalculator.build(
                positions = held,
                sessionsFor = localDataStore::sessionsFrom,
                latestQuoteFor = localDataStore::latestQuote,
                today = today,
                finalThrough = finalThrough,
                priceBreaksFor = { ticker -> breaks[ticker].orEmpty() },
            )
        }
        portfolio = rebuilt
        // Straight off the rebuild, so the shortcut counts exactly what the Overdue card does.
        overdueCounted(rebuilt.positions.count(PositionView::overdue))
        reviewTrades(rebuilt, announceChanges)
        // After the trades, and off the same recompute. Every path that announces recomputes the
        // performance first, so the calls this reads are the ones the prices were just scored
        // against - and the holdings it excludes are the ones set two lines above.
        reviewCalls(performance, announceChanges)
        // Third, off the same rebuild: it reads the trades reviewTrades has just written down, and
        // a trade that settled on this pass is no longer approaching anything.
        reviewApproaches(rebuilt, announceChanges)
        reviewSessions(rebuilt, performance, announceChanges)
    }

    /**
     * Rebuilds what happened on each of the recent sessions, and writes the history down.
     *
     * Third in the same recompute and off the same two objects the sweeps used, which is what stops
     * the card disagreeing with the tabs it sits on: one portfolio, one report, three readings of
     * them. Unlike the sweeps it announces nothing and gates on no preference - it is a screen, not
     * a notification, and there is nothing here for a switch to silence.
     *
     * [SessionDigest.STORED_SESSIONS] sessions are derived where one is drawn, because the table
     * behind this wants a history and deriving thirty costs a pass over lists already in memory.
     * Each is written whole, so a heal that rewrote a stock's prices takes the events derived from
     * the old ones with it rather than leaving them beside their replacements.
     */
    private suspend fun reviewSessions(
        rebuilt: Portfolio,
        report: PerformanceReport,
        announceChanges: Boolean = false,
    ) {
        val calls = report.sessions.flatMap(ScoredSession::calls)
        // The sessions the record actually knows about, which is the exchange's own calendar as
        // this phone has it - a weekend and a public holiday are simply absent. Both sources are
        // needed: a call's window stops at its settlement, so a record with nothing running would
        // otherwise report on a session several days behind the prices on disk.
        val sessions = (
            calls.asSequence().flatMap { call -> call.sessions.asSequence().map(DailySession::date) } +
                report.latestPrices.values.asSequence().map { it.session.date }
            )
            .distinct()
            .sortedDescending()
            .take(SessionDigest.STORED_SESSIONS)
            .toList()
        if (sessions.isEmpty()) {
            sessionDigest = null
            return
        }
        val digests = withContext(Dispatchers.IO) {
            SessionDigest.build(
                sessions = sessions,
                positions = rebuilt.positions,
                calls = calls,
            ).also(localDataStore::saveSessionDigests)
        }
        sessionDigest = digests.firstOrNull()
        announceSession(digests.firstOrNull(), announceChanges)
    }

    /**
     * Says once what the newest session did, if the reader asked to be told.
     *
     * **The one place a digest is spoken.** The card is rebuilt on every recompute and rightly so -
     * a session has one right answer forever - which is exactly why it cannot decide on its own
     * whether it has already been said out loud. `session_digest_announced` is the memory, and it
     * is the same distinction `position_status_seen` draws: what happened is derived, what was
     * *said* is stored.
     *
     * **The newest session only.** A phone switched off for a week comes back with every one of
     * those sessions filled in correctly on the card - that is the whole point of the digest being
     * derived - and announcing each of them would be seven days of news piled onto the evening it
     * was turned on. The card is where a reader catches up; the shade is where they are told.
     *
     * Gated on [announceChanges] like the two sweeps above, so an edit the user just made cannot
     * produce a summary of the session.
     */
    private suspend fun announceSession(digest: SessionDigest?, announceChanges: Boolean) {
        if (!announceChanges || !appPreferences.sessionDigestEnabled) return
        val newest = digest?.takeIf { !it.isEmpty } ?: return
        val fresh = withContext(Dispatchers.IO) {
            if (localDataStore.sessionDigestAnnounced(newest.date)) {
                false
            } else {
                // Written before the notifier is called, not after: a crash between the two would
                // otherwise leave the session unannounced and repeat the whole line on the next
                // recompute. Being told nothing once is better than being told the same thing five
                // times, which is how a channel gets switched off.
                localDataStore.recordSessionDigestAnnounced(newest.date)
                true
            }
        }
        if (fresh) sessionSummarised(newest)
    }

    /**
     * Writes down where every trade stands, and announces the ones the market moved.
     *
     * The sweep runs whether or not anyone will be told, and that is the point of it. A user who
     * has the notifications switched off still has their trades recorded as they change, so
     * switching them on tells them what happens *next* rather than reciting a month of settled
     * history at them. The switch decides whether the phone speaks, never what it remembers.
     */
    private suspend fun reviewTrades(rebuilt: Portfolio, announceChanges: Boolean) {
        val alerts = withContext(Dispatchers.IO) {
            TradeAlerts.sweep(localDataStore.positionStatusSeen(), rebuilt.positions).also {
                localDataStore.savePositionStatusSeen(it.record, it.forgotten)
            }
        }
        if (announceChanges && appPreferences.tradeAlertsEnabled) tradesChanged(alerts.changes)
    }

    /**
     * The same question about the calls the user is **not** in: has one just become takeable?
     *
     * Run beside [reviewTrades] and off the same recompute, because the two are one sweep of one
     * record from two sides - the trades the Portfolio is watching, and everything else. Held calls
     * are handed over so a stock the user already owns is spoken about once, by the feature that
     * knows what they paid for it.
     *
     * The sweep runs whether or not anyone will be told, exactly as the trade one does, and for the
     * identical reason: switching the notification on then reports what happens **next** rather
     * than announcing every band the price happens to be sitting in this morning.
     */
    private suspend fun reviewCalls(report: PerformanceReport, announceChanges: Boolean) {
        val alerts = withContext(Dispatchers.IO) {
            CallAlerts.sweep(
                previous = localDataStore.callAlertSeen(),
                calls = report.sessions.flatMap(ScoredSession::calls),
                latestFor = { ticker -> report.latestPrices[ticker] },
                held = portfolio.positions.mapTo(mutableSetOf()) { it.position.id },
            ).also {
                localDataStore.saveCallAlertSeen(it.record, it.forgotten)
            }
        }
        if (announceChanges && appPreferences.callAlertsEnabled) callsChanged(alerts.changes)
    }

    /**
     * The third sweep of the same recompute: trades closing on a level rather than reaching one.
     *
     * [reviewTrades] and [reviewCalls] both report something the market has finished doing. This
     * reports something it is still doing, which is the only reason it is a third sweep and not a
     * branch inside the first: they answer at opposite ends of the same event, and a reader can
     * reasonably want to be told a stop was taken without being told, twice a week, that one is
     * getting close.
     *
     * The sweep runs whether or not anyone will be told, exactly as the other two do and for the
     * identical reason: switching the notification on reports what happens **next** instead of
     * announcing every level a price happens to be sitting near this morning.
     */
    private suspend fun reviewApproaches(rebuilt: Portfolio, announceChanges: Boolean) {
        val alerts = withContext(Dispatchers.IO) {
            ApproachAlerts.sweep(
                previous = localDataStore.approachSeen(),
                positions = rebuilt.positions,
                thresholdPercent = appPreferences.approachThresholdPercent,
            ).also {
                localDataStore.saveApproachSeen(it.record, it.forgotten)
            }
        }
        if (announceChanges && appPreferences.approachAlertsEnabled) {
            approachesChanged(alerts.changes)
        }
    }

    /**
     * The highest a stock has traded since a call was made, or null when nothing prices it yet.
     *
     * Read from the scored calls rather than recomputed, so a price ladder can never disagree with
     * the figure Insights reports for the same call.
     */
    override fun peakSince(ticker: String, openedOn: LocalDate?): Double? {
        if (openedOn == null) return null
        val wanted = Scoring.normalizeTicker(ticker)
        return performance.sessions
            .asSequence()
            .flatMap { it.calls.asSequence() }
            .firstOrNull { Scoring.normalizeTicker(it.ticker) == wanted && it.openedOn == openedOn }
            ?.peakHigh
    }

    /**
     * Whether [enterForeground] has already run, so that it runs once however it is reached.
     *
     * Declared above `init` on purpose: a property initialiser placed below it runs *after* the
     * init block and would reset this to false, leaving a second call free to start every
     * collector a second time.
     */
    private var foregroundStarted = false

    init {
        adoptLegacyPhrases()
        // Before the first sync, or this device's own settings would look like an empty install's
        // and be quietly overwritten by the other phone's rather than merged with them.
        settingsRepository.claimSettingsIfUnstamped(deviceName)
        // Recorded on first launch too, so the very first run has a version to name rather than
        // a gap where one should be.
        regeneratePrompt("First run")
        appScope.launch {
            recomputePerformance()
            recomputePortfolio(announceChanges = true)
            refreshPricesIfStale()
        }
        appScope.launch {
            val stored = localDataStore.stocks()
            EgxCatalog.restore(stored)
            catalogMessage = "${EgxCatalog.size()} stocks available offline."
            // Only when nothing has been downloaded yet, so a launch never waits on the network.
            if (stored.isEmpty()) refreshEgxCatalog()
        }
        if (!headless) enterForeground()
    }

    /**
     * Brings up the parts of the app that exist only for someone looking at it.
     *
     * Telegram, the sync it carries and the update check are the expensive half of a start, and a
     * process the alarm woke to fetch prices needs none of them: that work reads a public price
     * feed and writes to this phone's own database. Leaving them out turns a wake that connected
     * to Telegram, caught up on four kinds of synced document and asked GitHub about a new build
     * into one that fetches prices - which is the difference between a schedule that can honestly
     * run every fifteen minutes through a session and one that cannot.
     *
     * Idempotent, and called from two places for two reasons: from `init` on an ordinary start,
     * and from the activity when a process the clock woke turns out to have a reader after all. A
     * schedule that wakes the phone and is then opened by its owner must not be an app with no
     * chats in it.
     *
     * Nothing here is load-bearing for a scheduled run. A job that does need Telegram - an
     * analysis - reaches it through the lazy above and starts it then, so a headless start that
     * guessed wrong is slower rather than broken.
     */
    override fun enterForeground() {
        if (foregroundStarted) return
        foregroundStarted = true
        appScope.launch {
            // The chat list is the first thing a run depends on, and it used to load in silence:
            // an empty list looked identical whether it was still fetching or had failed.
            var announced: TelegramAuthStep? = null
            telegramRepository.authState.collect { state ->
                telegramAuthState = state
                if (state.step != announced) {
                    announced = state.step
                    when (state.step) {
                        // Working rather than done: this is a step in flight, and a tick beside it
                        // claimed a connection the app was still waiting on.
                        TelegramAuthStep.INITIALIZING -> statusMessage = StatusMessage(
                            "Connecting to Telegram",
                            succeeded = true,
                            stage = StatusStage.WORKING,
                        )
                        // Deliberately no count here: READY arrives before the chat list does, so
                        // reading it now reports zero while six are about to appear. The count is
                        // announced by the collector below, when there is one.
                        TelegramAuthStep.READY -> {
                            statusMessage = StatusMessage("Telegram ready", succeeded = true)
                            catchUpOnce()
                        }
                        TelegramAuthStep.ERROR -> statusMessage = StatusMessage(
                            state.message?.takeIf(String::isNotBlank)
                                ?: "Telegram could not load chats",
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
                    statusMessage = StatusMessage("$count chats", succeeded = true)
                }
        }
        // Independent of Telegram, unlike the sync: this is one public URL, so it does not have to
        // wait for a session that may never arrive on a phone whose owner has not signed in.
        checkForUpdateQuietly()
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

    /**
     * One full sync per launch, as soon as Telegram can carry it.
     *
     * Triggered by Telegram becoming ready rather than by the app starting: at launch there is no
     * session yet, so a sync then would fail every time and quietly do nothing. A device left alone
     * for a week therefore catches up on what the others published without anyone pressing anything.
     */
    private fun catchUpOnce() {
        if (caughtUp) return
        caughtUp = true
        appScope.launch {
            val outcome = runCatching { performSync() }.getOrNull() ?: return@launch
            // Only when something actually moved. Reporting "already in sync" to someone who never
            // asked is a notification about nothing.
            if (outcome.uploaded > 0 || outcome.downloaded > 0) {
                statusMessage = StatusMessage(outcome.summary, succeeded = true)
            }
        }
    }

    private var caughtUp = false

    /** How far the app has got with finding, fetching and checking a newer build. */
    override var updateState by mutableStateOf<UpdateState>(UpdateState.Idle)
        private set

    /**
     * Asks GitHub whether a newer build exists, and answers either way.
     *
     * The button is a question, so "you are on the newest version" is an answer worth giving. The
     * launch check is not, which is why it is [checkForUpdateQuietly] and not this.
     */
    override fun checkForUpdate() {
        val updates = updateRepository ?: return
        if (updateState is UpdateState.Checking || updateState is UpdateState.Downloading) return
        updateState = UpdateState.Checking
        appScope.launch {
            updateState = runCatching { updates.check() }.fold(
                onSuccess = { update ->
                    if (update == null) {
                        UpdateState.UpToDate(updates.currentVersionName)
                    } else {
                        UpdateState.Available(update)
                    }
                },
                onFailure = { error ->
                    UpdateState.Failed(
                        error.message?.takeIf(String::isNotBlank) ?: "The update check failed.",
                    )
                },
            )
        }
    }

    /**
     * The launch check, which speaks only when there is something new.
     *
     * The same rule the launch sync follows: telling someone who never asked that nothing has
     * changed is a notification about nothing. A failure is silent for the same reason - the phone
     * was offline, which is not news either.
     */
    private fun checkForUpdateQuietly() {
        val updates = updateRepository ?: return
        appScope.launch {
            // The disk before the network, and whatever the setting says: an update already fetched
            // was asked for by someone, and the only thing left to do with it is install it. This is
            // what carries a download through the app being restarted by the permission grant that
            // was needed to install it.
            val waiting = runCatching { updates.downloaded() }.getOrNull()
            if (waiting != null) {
                updateState = UpdateState.Ready(waiting.first, waiting.second)
                statusMessage = StatusMessage(
                    "Version ${waiting.first.versionName} is downloaded and ready to install",
                    succeeded = true,
                )
            }
            if (!appPreferences.updateChecksEnabled) return@launch
            val update = runCatching { updates.check() }.getOrNull() ?: return@launch
            // A download in hand beats an offer of the same version, and loses to a newer one.
            val ready = (updateState as? UpdateState.Ready)?.update?.version
            if (ready != null && ready >= update.version) return@launch
            updateState = UpdateState.Available(update)
            statusMessage = StatusMessage(
                "Version ${update.versionName} is available",
                succeeded = true,
            )
        }
    }

    /**
     * Fetches the APK and checks it before offering to install it.
     *
     * The signing check is what turns Android's "App not installed" into a sentence that says what
     * to do about it. It is not a second opinion on Android's own check - it is the same check,
     * made early enough to be explained.
     */
    override fun downloadUpdate(update: AvailableUpdate) {
        val updates = updateRepository ?: return
        if (updateState is UpdateState.Downloading) return
        updateState = UpdateState.Downloading(update, 0f)
        appScope.launch {
            updateState = runCatching {
                // Nothing to fetch if it is already here. A download interrupted by the permission
                // grant used to be paid for twice, at seventy megabytes a time.
                val waiting = runCatching { updates.downloaded() }.getOrNull()
                if (waiting != null && waiting.first.version >= update.version) {
                    return@runCatching UpdateState.Ready(waiting.first, waiting.second)
                }
                // The progress callback arrives on the thread doing the reading. Compose state
                // takes a write from any thread, and marshalling each percent back to the main one
                // would cost a coroutine per percent to move a number nobody is racing for.
                val file = updates.download(update) { progress ->
                    updateState = UpdateState.Downloading(update, progress)
                }
                when (updates.inspect(file)) {
                    DownloadedApk.MATCHES -> UpdateState.Ready(update, file)
                    // Damaged and wrong-key used to be the same sentence, and it was this one -
                    // so an interrupted download accused the release of being signed by someone
                    // else, which was true of nothing and sent the search a long way from the
                    // network fault that caused it.
                    DownloadedApk.WRONG_KEY -> {
                        file.delete()
                        UpdateState.Failed(
                            "Version ${update.versionName} is signed with a different key, so " +
                                "Android will not install it over this build. Uninstall this one " +
                                "and install that release by hand.",
                        )
                    }
                    DownloadedApk.DAMAGED -> {
                        file.delete()
                        UpdateState.Failed(
                            "The download of version ${update.versionName} arrived damaged. " +
                                "Press Download to fetch it again.",
                        )
                    }
                }
            }.getOrElse { error ->
                UpdateState.Failed(
                    error.message?.takeIf(String::isNotBlank) ?: "The download failed.",
                )
            }
        }
    }

    /** Puts the card back to the button, after an answer has been read. */
    override fun dismissUpdate() {
        updateState = UpdateState.Idle
    }

    /**
     * Hands the downloaded APK to Android to install.
     *
     * The confirmation is Android's own and arrives a moment later, through
     * [com.ikverse.egxanalyzer.data.UpdateInstallReceiver]. A failure to even start says so here,
     * because a button that appears to do nothing is what this whole path cost three releases.
     */
    override fun installUpdate(file: File) {
        val updates = updateRepository ?: return
        appScope.launch {
            runCatching { updates.install(file) }.onFailure { error ->
                reportUpdateProblem(
                    error.message?.takeIf(String::isNotBlank)
                        ?: "The install could not be started.",
                )
            }
        }
    }

    /**
     * Says why a button could not do what it says, without throwing away what the card holds.
     *
     * Android refusing to open the installer used to be invisible: the system closed it without a
     * word and the phone looked like it had ignored the press. A downloaded update is still a
     * downloaded update afterwards, so this speaks rather than resetting anything.
     */
    override fun reportUpdateProblem(reason: String) {
        statusMessage = StatusMessage(reason, succeeded = false)
    }

    /** True once the user has allowed this app to install apps; Android is the only one who can ask. */
    override fun canInstallUpdates(): Boolean = updateRepository?.canInstall() ?: false

    override fun installPermissionIntent(): Intent? = updateRepository?.permissionIntent()

    override fun releasesPageIntent(): Intent? = updateRepository?.releasesPageIntent()

    override fun updateAutomaticUpdateChecks(enabled: Boolean) {
        if (enabled == appPreferences.updateChecksEnabled) return
        persistPreferences(appPreferences.copy(updateChecksEnabled = enabled))
    }

    override fun navigate(destination: AppDestination) {
        // A tab the reader chose ends whatever jump preceded it: back now means "leave", not "undo
        // the press I made two tabs ago". Guarded on the destination actually changing, because
        // this is called with the tab already showing more often than not - the pager publishes its
        // arrival at the end of every travel this class started, and an unguarded clear there would
        // throw the return away in the same breath as the jump that recorded it.
        if (destination != this.destination) nav.clear()
        this.destination = destination
    }

    /**
     * One step of history, for the system's back button. See [NavStack].
     *
     * Held here rather than in the shell for the reason [destination] and [pages] are: folding the
     * phone disposes one shell whole and composes the other from nothing, so anything back
     * remembered from inside the composition would be forgotten by the fold.
     */
    private val nav = NavStack()

    /**
     * Whether back has something to undo before the app should close.
     *
     * Read by the shell to decide whether to hold the press at all, so a reader with nothing to
     * unwind gets the system's own behaviour rather than a handler that swallows it.
     */
    override val canGoBack: Boolean
        get() = nav.canReturn || pages.filtersActive(destination)

    /**
     * Undoes the last thing that narrowed or moved the view, and says whether it found one.
     *
     * **The jump before the filter**, deliberately. Both can be outstanding at once - a reader
     * filters Insights to one stock, then presses a card through to the Portfolio - and the jump is
     * the more recent of the two, so it is the one a back press is about. Answering with the filter
     * first would strip a narrowing the reader set up deliberately while leaving them on a tab they
     * did not choose.
     *
     * A filter is cleared whole rather than one control at a time. Three presses to undo three
     * chips would be back re-enacting the reader's typing, and the screen's own Clear filters
     * button - which is inside the folded panel, and so two presses away when a filter is on -
     * clears them together too.
     */
    override fun goBack(): Boolean {
        nav.pop()?.let { stop ->
            destination = stop.destination
            // Straight onto the fields rather than through openPosition or openCall: those record
            // a jump, and a return that recorded itself would be a back press the next back press
            // has to undo.
            pendingPositionId = stop.positionId
            pendingCallId = stop.callId
            return true
        }
        if (pages.filtersActive(destination)) {
            pages.clearFilters(destination)
            return true
        }
        return false
    }

    /**
     * The page a reader has asked to be taken back to the top of, and how many times they have.
     *
     * Pressing the destination you are already on means "take me back to the top" on every platform
     * that has a bottom bar, and this app had nothing behind that press at all - so the way back
     * from a session card deep inside Insights was to scroll all of it by hand.
     *
     * A destination **and** a counter, and both halves are load-bearing. The destination, because
     * the pager composes the pages either side of the one being read: a bare signal would take the
     * neighbours to the top as well and quietly throw away a position on a tab nobody had touched.
     * The counter, because a second press is a second request - a value that repeated would restart
     * nothing, and the page would answer the first press and ignore every one after it.
     */
    override var scrollToTopRequest by mutableStateOf<Pair<AppDestination, Int>?>(null)
        private set

    private var scrollToTopCount = 0

    /**
     * Asks [destination]'s page to return to the top.
     *
     * Deliberately **not** folded into [navigate]. The pager calls that on every swipe, and its
     * `snapshotFlow` reports the page it is already on the moment it starts collecting - so a
     * scroll-to-top inside `navigate` would fire on first composition and again every time a drag
     * settled, which is a page throwing the reader back to the top for having swiped to it.
     */
    override fun scrollToTop(destination: AppDestination) {
        scrollToTopCount++
        scrollToTopRequest = destination to scrollToTopCount
    }

    override fun toggleContentType(type: AnalysisContentType) {
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

    override fun selectProvider(provider: CloudProvider) {
        cloudConfiguration = settingsRepository.configurationFor(provider)
        settingsMessage = null
        // Each provider keeps its own list, so switching back to one already loaded finds it there.
        availableModels = settingsRepository.modelCatalog(provider)
        modelListMessage = null
    }

    override fun updateEndpoint(endpoint: String) {
        cloudConfiguration = cloudConfiguration.copy(endpoint = endpoint)
        // A different endpoint is a different catalogue - regions do not carry the same models - so
        // the stored list stops being an answer about this connection.
        settingsRepository.clearModelList(cloudConfiguration.provider)
        availableModels = emptyList()
        modelListMessage = null
    }

    override fun updateModel(model: String) {
        cloudConfiguration = cloudConfiguration.copy(model = model)
    }

    override suspend fun saveSettings(credential: String) {
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
        // The connection travels; the key does not. Another device gets the endpoint and the model
        // it should be using and asks for the key itself - see SettingsSnapshot.
        publishSettings()
        if (!cloudConfiguration.hasCredential) {
            credentialVerified = null
            settingsMessage = "Connection saved. Enter the provider API key to finish."
            return
        }
        verifyCredential()
    }

    /** Stores the endpoint and model without touching the credential or its verified state. */
    override fun persistModelChoice() {
        settingsRepository.save(cloudConfiguration, null)
        cloudConfiguration = settingsRepository.load()
        publishSettings()
    }

    /**
     * Asks the provider whether the stored key actually works.
     *
     * Storing a key only proves it reached the device. Reporting it as saved without checking made
     * a rejected key look like a working one, and the failure only surfaced later at analysis time.
     * The model list is the cheapest call the providers offer, so this costs nothing meaningful.
     */
    override suspend fun verifyCredential() {
        busyLabel = "Verifying API key"
        try {
            val models = analysisRepository.listModels()
            credentialVerified = true
            availableModels = models
            settingsRepository.saveModelCatalog(cloudConfiguration.provider, models)
            // Published with the rest: the list is what makes the model picker usable, and a
            // restored install with no list is back to asking for a model name from memory.
            publishSettings()
            // The card keeps the sentence and the toast gets the short form of it: this only ever
            // runs from Settings, so the fuller wording is already on screen behind the toast.
            settingsMessage = "API key verified. ${models.size} models available."
            statusMessage = StatusMessage("Key verified, ${models.size} models", succeeded = true)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            credentialVerified = false
            // Whatever the provider said about the rejection stays on the card, where there is room
            // for "no credit" or "wrong key" to be read and acted on.
            settingsMessage = error.message?.takeIf(String::isNotBlank)
                ?: "The provider rejected this API key."
            statusMessage = StatusMessage("API key rejected", succeeded = false)
        } finally {
            busyLabel = null
        }
    }

    /**
     * Re-reads the token tally.
     *
     * Called where a request has just been paid for and where the tally is about to be looked at.
     * The store is on disk and the picker draws hundreds of rows, so nothing reads it per row.
     */
    override fun refreshModelUsage() {
        modelUsage = modelUsageStore?.all().orEmpty()
    }

    /** Forgets what every model has cost. The spending happened; only this phone's record of it goes. */
    override fun clearModelUsage() {
        modelUsageStore?.clear()
        refreshModelUsage()
        settingsMessage = "Token usage cleared."
    }

    /** What one model has cost, for the row that names it. */
    override fun usageFor(model: String): ModelUsageRecord? =
        modelUsage.firstOrNull { it.provider == cloudConfiguration.provider && it.model == model }

    override suspend fun loadCloudModels() {
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
            settingsRepository.saveModelCatalog(cloudConfiguration.provider, availableModels)
            modelListMessage = if (availableModels.isEmpty()) {
                "The provider returned no selectable models. You can still enter a model manually."
            } else {
                "Loaded ${availableModels.size} models."
            }
        } catch (error: Exception) {
            // The stored list is left alone. It is still what this provider last answered, and
            // emptying it over a dropped connection takes the picker away at the moment the user
            // is least able to type a model name from memory.
            modelListMessage = error.message ?: "Could not load models."
        } finally {
            modelListLoading = false
        }
    }

    override fun removeCredential() {
        settingsRepository.removeCredential(cloudConfiguration.provider)
        // A different key can be a different account with a different catalogue.
        settingsRepository.clearModelList(cloudConfiguration.provider)
        availableModels = emptyList()
        cloudConfiguration = settingsRepository.configurationFor(cloudConfiguration.provider)
        credentialVerified = null
        settingsMessage = "Saved credential removed."
    }

    override fun resetProviderConfiguration() {
        // Clears the stored list too, so the endpoint the reset restores is not left with the
        // catalogue of the one it replaced.
        settingsRepository.resetProviderConfiguration(cloudConfiguration.provider)
        availableModels = emptyList()
        cloudConfiguration = settingsRepository.configurationFor(cloudConfiguration.provider)
        settingsMessage = "Provider endpoint and model reset to defaults."
    }

    override fun updateThemeMode(value: ThemeMode) {
        saveAppPreferences(appPreferences.copy(themeMode = value))
    }

    override fun updateAnalysisLanguage(value: AnalysisLanguage) {
        saveAppPreferences(appPreferences.copy(analysisLanguage = value))
    }


    override fun updateResponseTimeout(value: Int) {
        saveAppPreferences(
            appPreferences.copy(
                responseTimeoutSeconds = value.coerceIn(
                    ResponseTimeout.MIN,
                    ResponseTimeout.MAX,
                ),
            ),
        )
    }

    override fun toggleDefaultContentType(type: AnalysisContentType) {
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

    override fun updatePromptCustomization(systemPrompt: String, include: String, exclude: String) {
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

    override fun restorePromptSnapshot(snapshot: PromptSnapshot) {
        saveAppPreferences(
            appPreferences.copy(
                customSystemPrompt = snapshot.systemPrompt,
                includePhrases = snapshot.includePhrases,
                excludePhrases = snapshot.excludePhrases,
            ),
        )
    }

    override fun resetPromptCustomization() {
        updatePromptCustomization("", "", "")
        settingsMessage = "Default evidence-backed prompt restored."
    }

    override fun updateCorrectionRetries(value: Int) {
        saveAppPreferences(appPreferences.copy(correctionRetries = value.coerceIn(0, 2)))
    }

    override fun updateCatalogEnrichment(enabled: Boolean) {
        saveAppPreferences(appPreferences.copy(catalogEnrichmentEnabled = enabled))
    }

    override suspend fun refreshEgxCatalog() {
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
     * Turns the daily overdue reminder on or off.
     *
     * The scheduling follows the preference rather than being booked once at startup: a user who
     * turns it off wants the phone to stop waking up for it, not to keep waking up and decide each
     * time that it has nothing to say.
     */
    override fun updateOverdueReminders(enabled: Boolean) {
        if (enabled == appPreferences.overdueRemindersEnabled) return
        persistPreferences(appPreferences.copy(overdueRemindersEnabled = enabled))
        dailyCheckChanged(tradeWatchWanted)
        // And the alarm, which carries the sweep at the close. Booked on the same question as the
        // worker so the two can never be left disagreeing about whether anyone is listening.
        rebookSchedules()
    }

    /**
     * Turns the trade status notifications on or off.
     *
     * Most of what these report rides on work the app was doing anyway - a price refresh re-scores
     * every trade whether or not anyone is told - so this mostly decides whether the phone speaks.
     * The wakes are the exception and are why [tradeWatchWanted] is re-asked here: a window running
     * out is brought about by the close and not by a price, so somebody has to look at the record
     * for it to be noticed at all. The sweep that records where each trade stands goes on running
     * either way, so switching this back on reports what happens next rather than reciting
     * everything that happened while it was off.
     */
    override fun updateTradeAlerts(enabled: Boolean) {
        if (enabled == appPreferences.tradeAlertsEnabled) return
        persistPreferences(appPreferences.copy(tradeAlertsEnabled = enabled))
        dailyCheckChanged(tradeWatchWanted)
        rebookSchedules()
    }

    /**
     * Whether the phone says a stock has traded into a buy zone nobody has acted on.
     *
     * Gates the notification and never the sweep, like every other switch here. It books no
     * background work of its own: the sweep rides the price refresh, which is already happening
     * for other reasons, so switching this on adds a notification and not a wake-up.
     */
    override fun updateCallAlerts(enabled: Boolean) {
        if (enabled == appPreferences.callAlertsEnabled) return
        persistPreferences(appPreferences.copy(callAlertsEnabled = enabled))
    }

    /**
     * Whether the phone says a trade is closing on its stop or on target 2.
     *
     * Gates the notification and never the sweep, like every switch here, and books no background
     * work of its own: the sweep rides a recompute that was already happening. Switching it on
     * therefore reports what happens next rather than every level a price is currently sitting
     * near - which is exactly the burst the first-sight rule in `ApproachAlerts` exists to prevent.
     */
    override fun updateApproachAlerts(enabled: Boolean) {
        if (enabled == appPreferences.approachAlertsEnabled) return
        persistPreferences(appPreferences.copy(approachAlertsEnabled = enabled))
    }

    /**
     * How near a level counts as closing on it.
     *
     * Clamped rather than trusted, the way the trade window is: a value arriving from a synced
     * document written by a build with different bounds must not widen this one's.
     */
    override fun updateApproachThreshold(percent: Int) {
        val clamped = percent.coerceIn(
            ApproachAlerts.MIN_THRESHOLD_PERCENT,
            ApproachAlerts.MAX_THRESHOLD_PERCENT,
        )
        if (clamped == appPreferences.approachThresholdPercent) return
        persistPreferences(appPreferences.copy(approachThresholdPercent = clamped))
    }

    /**
     * Whether the phone says once, after the close, what the whole session did.
     *
     * Gates the notification and never the archive: `session_events` and the card are written on
     * every recompute regardless, so switching this on reports the next session rather than
     * announcing a backlog of the last thirty.
     */
    override fun updateSessionDigest(enabled: Boolean) {
        if (enabled == appPreferences.sessionDigestEnabled) return
        persistPreferences(appPreferences.copy(sessionDigestEnabled = enabled))
    }

    /** Whether the phone says the price feed has gone quiet about stocks the record names. */
    override fun updateFeedAlerts(enabled: Boolean) {
        if (enabled == appPreferences.feedAlertsEnabled) return
        persistPreferences(appPreferences.copy(feedAlertsEnabled = enabled))
    }

    /** Whether the phone says a scheduled analysis was due and did not happen. */
    override fun updateScheduleAlerts(enabled: Boolean) {
        if (enabled == appPreferences.scheduleAlertsEnabled) return
        persistPreferences(appPreferences.copy(scheduleAlertsEnabled = enabled))
    }

    /**
     * Whether anything here wants to be told what became of a trade.
     *
     * One question for both background wakes, because one reading of the record answers both of
     * theirs: the overdue reminder and the trade status notifications each need somebody to look
     * while nobody is holding the phone. The sweep at the close does the looking on the afternoon a
     * window runs out; the daily worker is the backstop behind it, for a phone whose alarm the
     * system dropped.
     *
     * Wanted while *either* notification is on. Turning the overdue reminder off used to be enough
     * to cancel the work; doing that now would take the deadline notifications with it silently,
     * which is the shape of bug that only shows up as "it stopped telling me" weeks later. And
     * nothing here is booked once at startup - a user who turns both off wants the phone to stop
     * waking, not to keep waking and decide each time that it has nothing to say.
     */
    override val tradeWatchWanted: Boolean
        get() = appPreferences.overdueRemindersEnabled || appPreferences.tradeAlertsEnabled

    /**
     * The move off the old job table, before anything below reads what it writes.
     *
     * An init block rather than a line in the one further up, and its position is the whole point:
     * Kotlin runs initialisers in source order, so a migration placed above these two properties
     * is a migration whose writes their own initialisers then read - and one placed below them
     * would be overwritten by exactly those initialisers instead. The same trap `foregroundStarted`
     * is declared above `init` to avoid.
     */
    init {
        migrateSchedules()
    }

    /**
     * The analyses this phone runs on its own, at most [AnalysisSchedule.MAX] of them.
     *
     * Held here so a screen can show them, and read back from storage after every change rather
     * than edited in memory: the alarm that fires them can wake a process with no screen in it,
     * and the schedules the runner works from have to be the ones the card is showing.
     */
    override var analysisSchedules by mutableStateOf(settingsRepository.analysisSchedules())
        private set

    /**
     * Asks Settings to open its schedule section, set by the summary on Analyze.
     *
     * A one-shot request rather than a piece of state: the section that consumes it clears it, so
     * coming back to Settings later finds it closed like every other group. Analyze reports on the
     * schedules and Settings owns them, which is the whole reason this has to travel.
     */
    override var openScheduleSettings by mutableStateOf(false)

    /** Sends the reader from the summary on Analyze to the controls in Settings. */
    override fun editSchedules() {
        openScheduleSettings = true
        navigate(AppDestination.SETTINGS)
    }

    /**
     * Whether this phone keeps prices fresh while the market is trading.
     *
     * Off until it is switched on, unlike the daily catch-up beside it. That one runs on a launch
     * the user made; this wakes the phone on its own, and a feature that starts doing things
     * unattended because it shipped is not one anybody agreed to. Stored outside [AppPreferences]
     * so it stays this device's own answer - see `SettingsRepository.marketRefreshEnabled`.
     */
    override var marketRefreshEnabled by mutableStateOf(settingsRepository.marketRefreshEnabled())
        private set

    /**
     * What the last market-hours fetch did, and when.
     *
     * On screen rather than in a log, because the failure mode of everything on this page is
     * silence and the only cure for it is a line that always says something. Mirrored into state
     * so the checkbox's own line moves the moment a run writes one.
     */
    override var marketRefreshNote by mutableStateOf(settingsRepository.marketRefreshNote())
        private set

    override var marketRefreshNoteAt by mutableStateOf(settingsRepository.marketRefreshNoteAt())
        private set

    /**
     * Whether the clock here may start work that spends cloud credits.
     *
     * A second switch behind the schedule's own, off until it is turned on, and the only thing
     * standing between the clock and the owner's money. Separate because the two decisions are
     * separate: letting the phone keep a time says nothing about letting it spend money at that
     * time.
     */
    override var paidSchedulesEnabled by mutableStateOf(settingsRepository.paidSchedulesEnabled())
        private set

    override fun updateMarketRefreshEnabled(enabled: Boolean) {
        if (enabled == marketRefreshEnabled) return
        settingsRepository.saveMarketRefreshEnabled(enabled)
        marketRefreshEnabled = enabled
        rebookSchedules()
    }

    override fun updatePaidSchedulesEnabled(enabled: Boolean) {
        if (enabled == paidSchedulesEnabled) return
        settingsRepository.savePaidSchedulesEnabled(enabled)
        paidSchedulesEnabled = enabled
        // Nothing to re-book: the alarm is booked for the schedule either way, and the runner is
        // what refuses to spend. Booking on this switch would mean a schedule that vanished from
        // the screen rather than one that says why it was passed over.
    }

    /**
     * Saves one schedule, re-arming it where the fire it promises has moved.
     *
     * Re-armed on a changed time, on a changed set of days, and on being switched on, because each
     * makes the last fire a promise under a rule that no longer applies: switching on at 07:30 for
     * 07:00, or ticking a Wednesday at noon, must not owe a run on the spot and have the grace
     * window pay for it. Ticking a day is a decision about the weeks ahead, never about this
     * morning.
     *
     * Matched by id, and a schedule whose id is gone is not re-added: the only way that happens is
     * a screen holding a row that has since been deleted.
     */
    override fun saveAnalysisSchedule(schedule: AnalysisSchedule) {
        val before = analysisSchedules.firstOrNull { it.id == schedule.id } ?: return
        val moved = schedule.at != before.at ||
            schedule.days != before.days ||
            (schedule.enabled && !before.enabled)
        val saved = if (moved) schedule.copy(armedAt = Instant.now()) else schedule
        settingsRepository.saveAnalysisSchedules(
            analysisSchedules.map { if (it.id == saved.id) saved else it },
        )
        analysisSchedules = settingsRepository.analysisSchedules()
        rebookSchedules()
    }

    /**
     * Adds a schedule, switched off and aimed at whatever Analyze has ticked.
     *
     * Off, because a new row that started keeping time would be the app booking a paid run nobody
     * asked for - the same reason nothing here ships switched on. Aimed at the screen's current
     * selection because that is how a schedule is made: set a run up the way you always do, then
     * put a time on it. An empty selection leaves it unaimed, and the card says so.
     */
    override fun addAnalysisSchedule() {
        if (analysisSchedules.size >= AnalysisSchedule.MAX) return
        val aim = scheduledAnalysisFromScreen()
        settingsRepository.saveAnalysisSchedules(
            analysisSchedules + AnalysisSchedule(
                id = AnalysisSchedule.nextId(analysisSchedules),
                channels = aim?.channels.orEmpty(),
                contentTypes = aim?.contentTypes.orEmpty(),
            ),
        )
        analysisSchedules = settingsRepository.analysisSchedules()
        // Nothing to re-book: a schedule that is switched off has no next fire. Booked anyway,
        // because the cost is one comparison and the alternative is a rule about which edits move
        // the alarm.
        rebookSchedules()
    }

    /** Removes a schedule. The alarm is re-booked because the one it was set for may have gone. */
    override fun deleteAnalysisSchedule(id: Long) {
        settingsRepository.saveAnalysisSchedules(analysisSchedules.filterNot { it.id == id })
        analysisSchedules = settingsRepository.analysisSchedules()
        rebookSchedules()
    }

    /**
     * Moves what is on disk to what replaced it, once, on the first start of the build that did it.
     *
     * The rows this reads belonged to a job table that could hold any number of schedules of two
     * kinds. What is left is a checkbox and one analysis, so what a phone was already asking for is
     * carried across rather than lost - and then the table goes, because a table nothing reads is
     * one the next reader of this file has to work out the status of.
     */
    private fun migrateSchedules() {
        if (settingsRepository.schedulesMigrated()) return
        val carried = ScheduleMigration.from(localDataStore.legacyScheduleRows())
        if (carried.marketRefresh) settingsRepository.saveMarketRefreshEnabled(true)
        if (carried.schedules.isNotEmpty()) {
            settingsRepository.saveAnalysisSchedules(carried.schedules)
        }
        localDataStore.dropScheduledJobs()
        settingsRepository.markSchedulesMigrated()
        // Nothing to re-book from here: this runs before the state that would be read, and the
        // application books the alarm on every launch once the state is built.
    }

    /** Books the alarm for whatever is now nearest, after anything that could have moved it. */
    private fun rebookSchedules() =
        schedulesChanged(analysisSchedules, marketRefreshEnabled, tradeWatchWanted)

    /**
     * Does whatever the clock owes, then books the next alarm.
     *
     * The entry point for the worker the alarm wakes, and the only one: re-booking from in here
     * means a run can never leave the phone without an alarm for the fire after it. Prices first,
     * because that is the free half and it finishes in seconds - an analysis that goes on to take
     * a quarter of an hour must not hold up a fetch the market is moving underneath.
     */
    override suspend fun runDueScheduledJobs() {
        runDueMarketRefresh()
        runDueCloseSweep()
        JobRunner(
            schedules = settingsRepository::analysisSchedules,
            record = settingsRepository::recordAnalysisSchedule,
            paidWorkAllowed = settingsRepository::paidSchedulesEnabled,
        ).runDue(::runScheduledAnalysis)
            // Missed and failed only. A skip is the app deliberately doing nothing - most often
            // because paid runs are switched off, which is the standing state of that switch - and
            // a notification restating it every morning would be the app asking to be allowed to
            // spend. These two are the app failing to keep a promise, which is the thing silence
            // hides and this exists to break.
            .filter { it.lastOutcome == JobOutcome.MISSED || it.lastOutcome == JobOutcome.FAILED }
            .takeIf { appPreferences.scheduleAlertsEnabled }
            ?.forEach(scheduleMissed)
        analysisSchedules = settingsRepository.analysisSchedules()
        marketRefreshEnabled = settingsRepository.marketRefreshEnabled()
        rebookSchedules()
    }

    /**
     * The market-hours price fetch, through exactly the path the button on screen takes.
     *
     * A second implementation of a refresh would be a second set of rules about what is fetched,
     * what is re-scored and what the record then says - and the one that drifted would be this
     * one, because nobody is watching it.
     *
     * Every fire that gets this far writes a line, including the ones that did nothing. A run
     * recorded as "Skipped - a refresh was already running" is diagnosable; a blank screen on an
     * afternoon it should have fetched is not, and silence is how this feature fails.
     */
    private suspend fun runDueMarketRefresh() {
        if (!settingsRepository.marketRefreshEnabled()) return
        val last = settingsRepository.lastPriceRefreshAt()
            .takeIf { it > 0L }
            ?.let(Instant::ofEpochMilli)
        // Null covers all three ways to owe nothing: outside a session, past the slot's grace, or
        // already fetched since it came due - the last of which is what stops opening the app
        // inside a missed slot fetching every stock twice within seconds.
        MarketRefresh.dueFire(Instant.now(), last) ?: return
        val note = try {
            val outcome = refreshPrices(announce = false)
            if (outcome.busy) "Skipped - a refresh was already running." else outcome.summary
        } catch (cancelled: CancellationException) {
            // The process is going away underneath us. Nothing to record: the slot was not served,
            // and the next one is a quarter of an hour off.
            throw cancelled
        } catch (error: Exception) {
            error.message ?: "The fetch failed."
        }
        settingsRepository.recordMarketRefreshNote(note)
        marketRefreshNote = note
        marketRefreshNoteAt = settingsRepository.marketRefreshNoteAt()
    }

    /**
     * The one fetch after the exchange has shut, and the notifications that come out of it.
     *
     * This is what makes an ending land on the day it happened. A window runs out because a session
     * closed, not because a price moved, and until something looks at the record nothing knows: the
     * trade sat open until midnight turned the date over, and was announced whenever the phone next
     * happened to wake - which on a phone that keeps no prices fresh is the daily worker, at an hour
     * WorkManager picked and nobody chose.
     *
     * It fetches rather than only sweeping, because a sweep can only judge the rows on disk: with
     * the refresh checkbox off there may be no row for today's session at all, so the window would
     * not be spent, nothing would expire, and the wake would announce nothing. One pass over a free
     * public feed, once a trading day, for a phone whose owner asked to be told about their trades.
     *
     * Nothing is fetched twice. [CloseSweep.dueFire] is answered against the moment prices were
     * last actually fetched, so the 14:45 refresh slot on a phone that keeps prices fresh - or the
     * user pressing the button at four o'clock - has already done this fire's work, and it stands
     * down. Ordered after [runDueMarketRefresh] for exactly that reason.
     *
     * [refreshPrices] does the rest: it re-scores the record and announces what moved, which is the
     * same path the button on screen takes. A failure is left alone - the fetch was not recorded,
     * so the next wake still owes it, and there is nothing here worth stopping the analyses behind
     * it for.
     */
    private suspend fun runDueCloseSweep() {
        if (!tradeWatchWanted) return
        val last = settingsRepository.lastPriceRefreshAt()
            .takeIf { it > 0L }
            ?.let(Instant::ofEpochMilli)
        CloseSweep.dueFire(Instant.now(), last) ?: return
        runCatching { refreshPrices(announce = false) }
    }

    /**
     * An analysis started by the clock rather than by a press.
     *
     * Everything before the run is a reason not to make it. This is the only thing in the app that
     * spends the owner's money without being asked to at that moment, so each guard below is a
     * separate way of being wrong that costs a real request, and every one of them ends in
     * [JobSkipped] - written down, not charged, and tried again at the next fire.
     */
    private suspend fun runScheduledAnalysis(schedule: AnalysisSchedule, due: Instant): String {
        if (analysisStatus == AnalysisStatus.RUNNING) {
            throw JobSkipped("A run was already going when this one came due.")
        }
        if (!cloudConfiguration.hasCredential || cloudConfiguration.model.isBlank()) {
            throw JobSkipped("No provider credential or model is saved.")
        }
        // The alarm wakes a process that may have been dead, and TDLib has to open its database and
        // sign back in before a chat can be read. Without the wait the run would find no session,
        // read nothing, and file itself as a schedule that does not work.
        if (!awaitTelegramReady()) {
            throw JobSkipped("Telegram was not ready in time to read the chats.")
        }
        val plan = schedule.plan()
        val window = resolveAnalysisWindow(plan.mode, plan.targetDate)
        // The session a run is for flips at 14:30 Cairo. A fire delayed across that line - by Doze,
        // by a phone that was off, by the grace window doing its job - would quietly analyse the
        // day after the one it was booked for, and the report would look perfectly ordinary. So the
        // session this job was due for is compared with the one it would run for now, and a
        // disagreement stops it: a schedule is a promise about a particular session.
        val intended = egxTargetSession(due.atZone(ZoneId.of(EGX_ZONE)))
        if (intended != window.targetDate) {
            throw JobSkipped(
                "Due for the $intended session, but by the time this ran the next one was " +
                    "${window.targetDate}. Skipped rather than pay to analyse a different day.",
            )
        }
        // Read first, decided after. A report of this session already exists on any day a second
        // schedule fires, and the question is not whether it exists but whether the chats have
        // said anything since - which is exactly what more than one schedule a day is for.
        val already = duplicateOf(window.targetDate, plan.channelIds.toSet())
        val batch = telegramRepository.collectSources(
            channelIds = plan.channelIds,
            start = window.start,
            endExclusive = window.endExclusive,
            contentTypes = plan.contentTypes,
        )
        if (batch.inputs.isEmpty()) {
            throw JobSkipped("The chats posted nothing in the window for the $intended session.")
        }
        // Collecting is free - it reads Telegram's own store - so this is checked after the read
        // and before the one expensive thing in the whole path. Paying to re-extract the same
        // messages is the mistake the old interval trigger made, and the reason it was dropped.
        already?.let { report ->
            val fresh = SourceFreshness.newSources(report.result.sources, batch.traces)
            if (fresh.isEmpty()) {
                val at = ScheduleClock.clock(
                    report.result.completedAt.atZone(ScheduleClock.ZONE).toLocalTime(),
                )
                throw JobSkipped(
                    "Nothing new since the $at report of the $intended session.",
                )
            }
        }
        val sources = LoadedSources(
            inputs = batch.inputs,
            traces = batch.traces.associateBy(SourceTrace::sourceId),
            channelOf = batch.traces.associate { it.sourceId to it.channelId },
        )
        return when (val outcome = executeRun(plan, sources, onScreen = false)) {
            is RunOutcome.Saved -> outcome.summary
            // Refused means nothing was sent and nothing was charged, which is a skip and not a
            // failure however it reads on the Analyze screen.
            is RunOutcome.Refused -> throw JobSkipped(outcome.reason)
            is RunOutcome.Failed -> error(outcome.reason)
            RunOutcome.Cancelled -> throw JobSkipped("The run was cancelled.")
        }
    }

    /**
     * Waits for a Telegram session to come back, for a run that woke the app rather than found it.
     *
     * Returns at once when one is already up, which is every run started from the screen and most
     * of the ones started by a schedule on a phone that was in use.
     */
    private suspend fun awaitTelegramReady(): Boolean =
        telegramAuthState.step == TelegramAuthStep.READY ||
            withTimeoutOrNull(TELEGRAM_READY_TIMEOUT_MILLISECONDS) {
                telegramRepository.authState.first { it.step == TelegramAuthStep.READY }
            } != null

    /**
     * Remembers the order the Portfolio is being read in.
     *
     * Kept rather than held for the session, because it hides nothing: a user who chose oldest-first
     * to work back through a month wants it still that way tomorrow, and finding it so costs them a
     * moment. The date filter beside it is deliberately not kept - see [AppPreferences.portfolioOrder].
     */
    override fun updatePortfolioOrder(order: PortfolioOrder) {
        if (order == appPreferences.portfolioOrder) return
        persistPreferences(appPreferences.copy(portfolioOrder = order))
    }

    /**
     * Remembers the order the calls inside a session card are being read in.
     *
     * Kept for the same reason, and it recomputes nothing: every option orders the same calls, so
     * this only ever changes where a card sits on screen. No rate, ranking or verdict moves.
     */
    override fun updateCallOrder(order: CallOrder) {
        if (order == appPreferences.callOrder) return
        persistPreferences(appPreferences.copy(callOrder = order))
    }

    /**
     * Changes what a new trade's window is offered as.
     *
     * Nothing already recorded moves. A trade carries the window it was taken under, and no call's
     * outcome depends on this at all - it used to re-score the whole record, back when the same
     * number was also the deadline every channel was judged against.
     */
    override fun updateDefaultTradeWindow(sessions: Int) {
        val clamped = Scoring.clampWindow(sessions)
        if (clamped == appPreferences.defaultTradeWindowSessions) return
        persistPreferences(appPreferences.copy(defaultTradeWindowSessions = clamped))
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
        if (pricedStocks().isEmpty()) return
        refreshPrices(announce = false)
        settingsRepository.recordPriceRefreshDay(today)
    }

    /**
     * The database file and a way to settle it first, for the diagnostics copy in Settings.
     *
     * Exposed rather than handing the store itself to a screen: this is the one thing the UI has
     * any business knowing about where the record lives.
     */
    override fun databaseFile(): java.io.File = localDataStore.databaseFile()

    override fun checkpointDatabase() = localDataStore.checkpoint()

    /**
     * The folder backups are written to, as the tree URI string the user granted.
     *
     * A string rather than a `Uri` because `Uri` is stubbed in unit tests, and this class is meant
     * to stay drivable without Android - the same reason `AnalysisPlan` carries chat ids and not
     * chats. The screen parses it; nothing here needs to.
     */
    override var backupFolder by mutableStateOf(settingsRepository.backupFolder())
        private set

    override fun saveBackupFolder(uri: String?) {
        settingsRepository.saveBackupFolder(uri)
        backupFolder = uri
    }

    /**
     * The settings as they would travel, for the copy that goes inside a backup.
     *
     * The same document `SettingsSync` publishes, so a backup and the sync channel carry settings in
     * one format and one reader serves both. Writing a second shape for the file would be a second
     * thing to keep in step with `AppPreferences`, and the one that drifted would be the one only
     * read on the day somebody had lost their phone.
     */
    override fun settingsDocument(): String = settingsRepository.snapshot().toDocument()

    /** What to write in a backup's metadata as its author. */
    override fun backupDevice(): String = deviceName

    /**
     * Whether today's backup still has to be written.
     *
     * A day, not a launch: the automatic backup rides the same moment the launch sync does, and a
     * phone opened six times before lunch would otherwise write six copies of an unchanged record
     * and push five real days out of the seven a folder keeps.
     */
    override fun backupDue(): Boolean =
        settingsRepository.lastBackupDay() != LocalDate.now(ZoneId.of(EGX_ZONE)).toString()

    override fun recordBackupDay() {
        settingsRepository.recordBackupDay(LocalDate.now(ZoneId.of(EGX_ZONE)).toString())
    }

    /** Every stock worth a request: the ones analyses name, plus the ones actually held. */
    private fun pricedStocks(): Set<String> =
        savedResults.recommendedTickers() + positions.map(Position::ticker)

    /**
     * The oldest session a stock still needs on disk, which is the oldest open trade in it.
     *
     * A trade cannot reach its deadline until every session of its window has been stored, so a
     * hole inside that window has to be fetched again rather than stepped over. Closed trades are
     * left out: their verdict is settled and refetching them would only widen every request.
     */
    private fun oldestOpenCall(ticker: String): LocalDate? = portfolio.positions
        .filter { it.open && it.ticker == ticker }
        .minOfOrNull(PositionView::recommendationDate)

    /**
     * Fetches only the stocks with no stored history at all.
     *
     * Deliberately outside the once-a-day guard: that guard exists so a day's settled prices are
     * not refetched, not so a stock named for the first time this afternoon goes unscored until
     * tomorrow. Quiet, and a failure leaves the card unpriced rather than interrupting the run.
     */
    private suspend fun priceStocksWithNoHistory(also: Collection<String> = emptyList()) {
        val named = savedResults.recommendedTickers() + also
        if (named.isEmpty()) return
        val unpriced = withContext(Dispatchers.IO) {
            val priced = localDataStore.pricedTickers()
            named.filterNot { it in priced }
        }
        if (unpriced.isEmpty()) return
        runCatching { priceRepository.refresh(unpriced) }
        recomputePerformance()
        recomputePortfolio(announceChanges = true)
    }

    override suspend fun refreshPrices(announce: Boolean): PriceRefreshOutcome {
        if (pricesRefreshing) {
            return PriceRefreshOutcome(
                "A price refresh was already running",
                succeeded = false,
                busy = true,
            )
        }
        // A held stock is priced whether or not a report still names it: deleting the analysis a
        // trade came from must not freeze that trade's current price.
        val tickers = pricedStocks()
        if (tickers.isEmpty()) {
            val outcome = PriceRefreshOutcome("No stocks to price", succeeded = false)
            if (announce) statusMessage = StatusMessage(outcome.summary, false)
            return outcome
        }
        pricesRefreshing = true
        busyLabel = if (announce) "Fetching prices" else null
        try {
            // A stock with no history is fetched in full; one already stored is fetched from where
            // its history stops, so a daily refresh stays small however long the history grows -
            // and a gap left by a phone that was not opened for a fortnight is filled rather than
            // stepped over, which is what an open trade needs to reach its deadline at all.
            val refresh = priceRepository.refresh(tickers, ::oldestOpenCall)
            settingsRepository.recordPriceRefreshDay(
                LocalDate.now(ZoneId.of(EGX_ZONE)).toString(),
            )
            recomputePerformance()
            recomputePortfolio(announceChanges = true)
            // Only now, because which sessions need bars is something only a scored record knows.
            // A second recompute is cheap and happens only when bars actually arrived.
            if (resolveUnorderedSessions()) recomputePerformance()
            val missing = refresh.unpriced.size
            // A stock whose prices changed scale, and one whose feed has stopped moving, are
            // both worth saying out loud: neither shows up as a failure, and the app's own
            // figures go quiet about the stock rather than wrong about it.
            val notes = buildList {
                if (missing > 0) add("$missing unpriced")
                if (refresh.suspect.isNotEmpty()) {
                    add("${refresh.suspect.size} changed scale")
                }
                if (refresh.stale.isNotEmpty()) add("${refresh.stale.size} stale")
                // The app built sessions rather than reading them, which it says out loud
                // everywhere else it does anything of the kind.
                if (refresh.rebuilt.isNotEmpty()) {
                    add("${refresh.rebuilt.size} rebuilt from hourly bars")
                }
            }
            // Composed whether or not anyone is listening, because a scheduled run has to write
            // down what it did and there is no screen to read it off.
            val outcome = PriceRefreshOutcome(
                summary = "Priced ${refresh.priced}/${refresh.requested}" +
                    if (notes.isEmpty()) "" else " · ${notes.joinToString(" · ")}",
                succeeded = true,
            )
            // A partial answer is still an answer, which is what a scheduled run files as well.
            // The notes are in the text and the Price feed card names the stocks behind them, so
            // reporting the run itself as a failure said nothing extra and said it in red - on
            // every ordinary refresh, because a feed with stocks it cannot serve is the normal day.
            if (announce) {
                statusMessage = StatusMessage(outcome.summary, succeeded = true)
            }
            return outcome
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            val outcome = PriceRefreshOutcome(
                error.message?.takeIf(String::isNotBlank) ?: "Could not fetch prices",
                succeeded = false,
            )
            if (announce) statusMessage = StatusMessage(outcome.summary, succeeded = false)
            return outcome
        } finally {
            pricesRefreshing = false
            busyLabel = null
        }
    }

    /**
     * Fetches five-minute bars for every session a call could not order on daily figures alone.
     *
     * On the refresh rather than when a card is opened, because the feed keeps intraday bars for
     * about sixty days: a call nobody happens to look at inside that window becomes permanently
     * unorderable, and the reader has no way of knowing a clock was running. Sessions already asked
     * about are skipped inside the repository, so a record full of genuinely unorderable calls
     * costs one query and no requests.
     *
     * Returns whether anything new arrived, so the caller re-scores only when it would say
     * something different. Failures are swallowed: an ambiguous call is the state the record is
     * already in, and a network error raised over an attempt to improve it is about something
     * nobody asked for.
     */
    private suspend fun resolveUnorderedSessions(): Boolean {
        val unordered = performance.sessions
            .asSequence()
            .flatMap { it.calls.asSequence() }
            .filter { it.outcome == Outcome.AMBIGUOUS }
            // The session it could not order is the one it settled on, which is the only date that
            // needs bars - the rest of the window was ordered by the calendar.
            .mapNotNull { call -> call.settledOn?.let { UnorderedSession(call.ticker, it) } }
            .distinct()
            .toList()
        if (unordered.isEmpty()) return false
        return runCatching {
            intradayRepository.fetchMissing(unordered, LocalDate.now(ZoneId.of(EGX_ZONE)))
        }.getOrDefault(0) > 0
    }

    private suspend fun recomputePerformance() {
        val analyses = savedResults
        val computed = withContext(Dispatchers.IO) {
            val breaks = localDataStore.priceBreakDates()
            // Read once for the whole recompute. Only the sessions a call could not order on daily
            // figures have bars at all, so this is a short table however long the record grows.
            val bars = localDataStore.intradayBars()
            // The exchange's own clock, not the phone's, and the close rather than midnight: a
            // session is still trading until 14:30 and its figures settle over the quarter of an
            // hour after it, so nothing it reports about itself is final before then - and
            // everything it reports is final after it.
            val finalThrough = ScheduleClock.lastFinalSession()
            // Beside it and not instead of it: how many days old a stock's newest price is, which
            // is what [PriceHealth] reports below, is a question about the calendar and not about
            // whether this afternoon's session has closed.
            val today = LocalDate.now(ZoneId.of(EGX_ZONE))
            val latest = localDataStore.latestSessions().mapValues { (_, session) ->
                LatestPrice(
                    session = session,
                    provisional = session.date > finalThrough || session.inconsistent,
                )
            }
            // The calls the market has finished with, read in one go like the bars and the breaks.
            // A settled verdict is not re-derived: it is looked up, and the sessions it was reached
            // on come back with it, so a closed call costs neither a query nor a replay.
            val settled = localDataStore.settledCalls()
            val report = PerformanceCalculator.report(
                analyses = analyses,
                pricesFrom = localDataStore.earliestSessionDate(),
                sessionsFor = localDataStore::sessionsFrom,
                pricedTickers = localDataStore.pricedTickers(),
                priceBreaksFor = { ticker -> breaks[ticker].orEmpty() },
                intradayFor = { ticker, date -> bars[ticker to date].orEmpty() },
                latestPrices = latest,
                settled = settled,
                // On this thread and inside this recompute, because it is the same write the read
                // above paid for the connection to.
                onSettled = localDataStore::saveSettledCalls,
                // The same date the provisional flag above is set from, so a session the card calls
                // still trading is never one the scorer has already run a call out of time on.
                finalThrough = finalThrough,
            )
            // On the same thread and from the same read: the breaks and the latest sessions are
            // already in hand here, and asking for them again on the main thread would be a second
            // trip to the database for figures this one has already paid for.
            val health = PriceHealth.assess(
                calls = report.sessions.flatMap(ScoredSession::calls),
                latestPrices = latest,
                breaks = breaks,
                today = today,
            )
            // Logged here and only here - the one place this is computed - rather than from the
            // published `priceHealth` state, which the main thread would have to hop back off of.
            localDataStore.saveFeedHealth(health)
            report to health
        }
        performance = computed.first
        priceHealth = computed.second
        reviewFeedHealth(computed.second)
        markTPlusOneTrades()
    }

    /**
     * Says once, per spell, that the feed has stopped answering about stocks the record names.
     *
     * The state has been computed on every recompute since `PriceHealth` was written and has
     * reached the reader only through a card in Settings - a screen consulted when something
     * already looks wrong. The trouble is that nothing looks wrong: a frozen series answers every
     * request, so a stale symbol is indistinguishable from a stock that has not moved, and every
     * rate on the page quietly rests on fewer calls than the reader thinks.
     *
     * **On the way in, and re-armed on the way out.** `PriceHealth` reports the same fault for as
     * long as the symbol stays retired, so announcing the state rather than the crossing into it
     * would be a daily notification about something that happened in June. This is the same rule
     * `CallAlerts` follows about a price sitting inside a band.
     *
     * The flag is written **whether or not anyone is told**, like every sweep here: switching the
     * notification on reports the next spell rather than the one already running.
     */
    private fun reviewFeedHealth(health: PriceHealthReport) {
        val quiet = !health.clean
        if (quiet == settingsRepository.feedReportedQuiet()) return
        settingsRepository.recordFeedReportedQuiet(quiet)
        if (quiet && appPreferences.feedAlertsEnabled) {
            feedQuiet(health.faults.size, health.callsHeld)
        }
    }

    /**
     * Gives back to the older trades the one thing they were never asked to remember.
     *
     * A trade records what kind of call it was taken on only from the build that added the column;
     * everything bought before it has the two-session window a T+1 was offered and no word about
     * why. The record still holds the card, keyed by the same trade id the Portfolio already uses
     * to lead back to it, so the fact is recoverable exactly once and then written down - after
     * which a deleted analysis can no longer take it away.
     *
     * Set, never cleared. A report the user has since removed says nothing about a trade rather
     * than saying it was ordinary, and a backfill that also unmarked would spend every recompute
     * arguing with the purchase that wrote the flag in the first place.
     *
     * Written without touching [Position.updatedAt] and without publishing: this device is not
     * changing the trade, it is finishing a record it already had. The stamp belongs to the user's
     * own edits, and moving it here would have every phone in a sync overtake the others' real
     * changes with fifty rewrites that say nothing new. The other device reaches the same answer
     * off its own copy of the record, on its own next recompute.
     */
    private suspend fun markTPlusOneTrades() {
        val called = performance.sessions
            .asSequence()
            .flatMap { it.calls.asSequence() }
            .filter(ScoredCall::isTPlusOne)
            .mapTo(mutableSetOf(), ScoredCall::positionId)
        if (called.isEmpty()) return
        val unmarked = positions.filter { !it.isTPlusOne && it.id in called }
        if (unmarked.isEmpty()) return
        positions = withContext(Dispatchers.IO) {
            unmarked.forEach { localDataStore.savePosition(it.copy(isTPlusOne = true)) }
            localDataStore.positions()
        }
    }

    private fun saveAppPreferences(value: AppPreferences) {
        persistPreferences(value)
        settingsMessage = "App preferences saved."
    }

    /**
     * Writes preferences, marks this device as the one that changed them, and publishes them.
     *
     * Every path that saves a preference goes through here. Three of them used to write straight to
     * the repository, which was harmless while settings stayed on one phone and is not harmless now:
     * a setting saved without a stamp is a setting the merge believes was never changed, and the
     * other device's older copy would overtake it on the next sync.
     */
    private fun persistPreferences(value: AppPreferences) {
        settingsRepository.savePreferences(value)
        appPreferences = settingsRepository.loadPreferences()
        publishSettings()
    }

    /**
     * Puts the settings in the channel, a moment after they stop changing.
     *
     * Coalesced rather than sent per change, because a slider is dragged: the scoring window alone
     * would otherwise upload a document for every value it passes through on its way to the one the
     * user wanted. Waiting costs nothing - the settings are already on disk, and the next sync would
     * carry them anyway if this never ran at all.
     */
    private fun publishSettings() {
        settingsRepository.recordSettingsChange(deviceName)
        settingsPublishJob?.cancel()
        settingsPublishJob = appScope.launch {
            delay(SETTINGS_PUBLISH_DELAY_MILLISECONDS)
            val snapshot = settingsRepository.snapshot()
            withContext(Dispatchers.IO) {
                runCatching { telegramRepository.uploadSettings(snapshot) }
            }
        }
    }

    private var settingsPublishJob: Job? = null

    /**
     * Brings this device's settings into line with the newest anyone has saved.
     *
     * Last writer wins, as with rules and trades. The case this exists for is the reinstalled phone,
     * whose stamp is zero: it has nothing to defend, so it takes everything and comes back
     * configured as its owner left it rather than as the app ships. Returns whether anything here
     * changed, because a scoring window arriving re-scores the whole record.
     */
    private suspend fun syncSettings(): Boolean {
        val mine = settingsRepository.snapshot()
        val theirs = runCatching { telegramRepository.syncedSettings() }.getOrNull()
        if (theirs == null || theirs.stamp <= mine.stamp) {
            if (settingsWorthUploading(mine, theirs)) {
                runCatching { telegramRepository.uploadSettings(mine) }
            }
            return false
        }
        adoptSettings(theirs)
        return true
    }

    private fun adoptSettings(snapshot: SettingsSnapshot) {
        val tradeWatchWas = tradeWatchWanted
        settingsRepository.adopt(snapshot)
        appPreferences = settingsRepository.loadPreferences()
        // The Analyze screen seeds its content types from the preference at launch, which on a
        // reinstalled phone happens before this arrives. Left alone it would show the shipped
        // default until the next restart - and what it is being set to is the user's own choice.
        selectedContentTypes = appPreferences.defaultContentTypes
            .intersect(AnalysisContentType.OFFERED)
        cloudConfiguration = settingsRepository.load()
        availableModels = settingsRepository.modelCatalog(cloudConfiguration.provider)
        useDefaultPromptOnly = settingsRepository.useDefaultPromptOnly()
        promptHistory = settingsRepository.promptHistory()
        // The daily check is booked with the system, not with this class: a device that adopts
        // "off" has to have the work cancelled, or it goes on waking up to say nothing.
        if (tradeWatchWanted != tradeWatchWas) {
            dailyCheckChanged(tradeWatchWanted)
            // The alarm follows it for the same reason: a device that adopts "off" has to stop
            // waking at the close too, and one that adopts "on" has to start.
            rebookSchedules()
        }
        regeneratePrompt("Settings arrived from another device")
    }

    /**
     * Brings the generated prompts into line, in both directions.
     *
     * A union like reports: an id is a hash of what produced the prompt, so a version never changes
     * and there is nothing to merge. Without it a restored install holds reports that name a prompt
     * version nothing on the device can show.
     */
    private suspend fun syncPromptVersions() {
        val remote = runCatching { telegramRepository.listSyncedPromptVersions() }.getOrNull()
            ?: return
        val mine = promptVersions.associateBy { SyncedPromptVersion.keyFor(it.id) }
        var adopted = false
        (remote.keys - mine.keys).forEach { key ->
            val fileId = remote[key] ?: return@forEach
            val version = runCatching { telegramRepository.downloadPromptVersion(fileId) }
                .getOrNull() ?: return@forEach
            localDataStore.rememberPromptVersion(version.version)
            adopted = true
        }
        (mine.keys - remote.keys).forEach { key ->
            val version = mine[key] ?: return@forEach
            runCatching { telegramRepository.uploadPromptVersion(SyncedPromptVersion(version)) }
        }
        if (adopted) promptVersions = localDataStore.promptVersions()
    }

    override fun addChannel(idText: String, name: String): Boolean {
        val id = idText.trim().toLongOrNull()
        if (id == null || name.isBlank()) return false
        val added = ChannelSelection(id, name.trim(), selected = true)
        // Added to the live list rather than reloading from storage, which would drop every chat
        // Telegram reported this session.
        channels = (channels.filterNot { it.id == id } + added).sortedBy { it.name.lowercase() }
        return true
    }

    override suspend fun saveTelegramApiConfiguration(apiId: String, apiHash: String) =
        telegramRepository.saveApiConfiguration(apiId, apiHash)

    override suspend fun resetTelegramApiConfiguration() =
        telegramRepository.resetApiConfiguration()

    /**
     * Brings this device and the sync channel to the same set of reports.
     *
     * A union, not a merge: a saved run never changes, so whatever either side has, both should
     * have. Nothing is overwritten and nothing is deleted, which is why this can run without asking
     * what to keep.
     */
    override suspend fun syncReports() = runAction(
        label = "Syncing reports with Telegram",
        success = SyncOutcome::summary,
    ) { performSync() }

    /**
     * The sync itself, without the progress bar and the announcement.
     *
     * Split out so a launch can do the same work quietly. Pressing the button is a question that
     * deserves an answer either way; catching up in the background is not, and a snackbar reporting
     * "already in sync" to someone who never asked is noise.
     */
    private suspend fun performSync(): SyncOutcome {
        // Deletions this device made while offline are carried out first, so nothing it has
        // already discarded is uploaded back a moment later.
        localDataStore.pendingDeletions().forEach { requestId ->
            runCatching { telegramRepository.buryReport(requestId) }
                .onSuccess { localDataStore.clearDeletion(requestId) }
        }

        // Settings before everything, and for the same reason rules come before reports: the whole
        // record that follows is scored against the window these carry. A reinstalled phone that
        // downloaded its record first would show it judged over ten sessions because that is what
        // the app ships with, and correct itself a moment later.
        val settingsChanged = syncSettings()
        // Rules first: a report downloaded a moment later was judged under somebody's rules, and
        // arriving with the reports but without them is the one order that explains nothing.
        val rulesChanged = syncWordingRules()
        // Positions before reports too. A trade arriving ahead of the analysis it was taken on is
        // still a complete trade - it carries its own levels - where the reverse would show a
        // recommendation the user appears never to have acted on.
        syncPositions()
        // After the rules, because a prompt version names the rules that composed it.
        syncPromptVersions()

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
        // A report buried on another device takes its opinions here too, for the same reason a
        // local delete does: an opinion about a card nobody can open is an orphan.
        if (forgotten > 0) opinions = localDataStore.stockOpinions()

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
        } else if (settingsChanged) {
            // A scoring window that arrived from another device re-judges every call already here,
            // whether or not a single report moved.
            recomputePerformance()
            recomputePortfolio(announceChanges = true)
        }
        return SyncOutcome(uploaded, downloaded, local.size - toUpload.size)
    }

    private fun SavedAnalysis.toSyncedRun() = SyncedRun(
        requestId = result.requestId,
        provider = provider.name,
        model = model,
        completedAt = result.completedAt.toString(),
        payload = localDataStore.storedJsonOf(result),
    )

    /**
     * Takes a backup back in, which is what the file was written for.
     *
     * The order is `performSync`'s order and for its reasons: settings first, because the record
     * that follows is read against the window they carry; rules before reports, because a report
     * arriving without the rules it was judged under explains nothing; trades before reports, since
     * a trade carries its own levels and stands alone where the reverse shows a recommendation
     * nobody appears to have acted on.
     *
     * **It only ever adds.** Every comparison below is the same `(updatedAt, device)` rule the sync
     * merge uses, with one deliberate difference: a revision the backup has marked deleted is
     * skipped rather than adopted. A merge between two live devices should carry a delete - that is
     * what makes a delete stick - but a file is one moment preserved, and letting last week's moment
     * remove a trade recorded yesterday would make this dangerous to reach for. Somebody restores
     * because something is missing. Deletes go on travelling through the channel, where both sides
     * are live and a merge belongs.
     *
     * Nothing is uploaded from here. Whatever this brings back is missing from the sync channel too
     * if it was ever lost there, and the next sync's own diff carries it up - which is one place
     * rather than two for the rule about what gets published.
     */
    private suspend fun restoreFrom(record: BackupRecord): RestoreOutcome {
        var settingsAdopted = false
        record.settings?.let { theirs ->
            // The same stamp comparison the channel gets. A reinstall's stamp is zero and so takes
            // everything; a device that has configured itself since the backup keeps what it has.
            if (theirs.stamp > settingsRepository.snapshot().stamp) {
                adoptSettings(theirs)
                settingsAdopted = true
            }
        }

        // Every decision below is a pure function in `BackupRestore.kt`, tested there, exactly as
        // the sync's own `syncActions` and `rulesToUpload` are. What is left here is applying them.
        val adoptedRules = rulesToRestore(
            mine = localDataStore.wordingRuleRevisions().map { (rule, deleted) -> SyncedRule(rule, deleted) },
            backup = record.rules,
        )
        adoptedRules.forEach { localDataStore.adoptWordingRule(it.rule, deleted = false) }

        val adoptedPositions = positionsToRestore(
            mine = localDataStore.positionRevisions().map { SyncedPosition(it.position, it.deleted, it.unknown) },
            backup = record.positions,
        )
        adoptedPositions.forEach { localDataStore.adoptPosition(it.position, deleted = false, unknown = it.unknown) }

        val adoptedPrompts = promptVersionsToRestore(
            mine = promptVersions.map { SyncedPromptVersion.keyFor(it.id) }.toSet(),
            backup = record.promptVersions,
        )
        adoptedPrompts.forEach { localDataStore.rememberPromptVersion(it) }

        val adoptedRuns = runsToRestore(
            held = localDataStore.savedRequestIds(),
            buried = localDataStore.pendingDeletions(),
            backup = record.runs,
        )
        adoptedRuns.forEach {
            localDataStore.adoptResult(it.requestId, it.provider, it.model, it.completedAt, it.payload)
        }

        val rules = adoptedRules.size
        val trades = adoptedPositions.size
        val prompts = adoptedPrompts.size
        val reports = adoptedRuns.size

        if (rules > 0) regeneratePrompt("Rules arrived from a backup")
        if (rules > 0) wordingRules = localDataStore.wordingRules()
        if (prompts > 0) promptVersions = localDataStore.promptVersions()
        if (trades > 0) {
            positions = localDataStore.positions()
            // Silent: every one of these changed because a file was read, not because the market
            // did anything, and a restore that ends in a burst of notifications about trades the
            // user already knew about is the app announcing its own bookkeeping.
            recomputePortfolio(announceChanges = false)
        }
        if (reports > 0) {
            savedResults = localDataStore.results()
            unreadableResults = localDataStore.unreadableResults
        }
        if (reports > 0 || trades > 0 || settingsAdopted) recomputePerformance()
        return RestoreOutcome(reports, rules, trades, prompts, settingsAdopted)
    }

    override suspend fun startTelegramQrSignIn() = runAction(
        label = "Preparing a Telegram sign-in code",
        success = { "Scan the code in Telegram" },
    ) { telegramRepository.startQrSignIn() }

    override suspend fun submitTelegramPhone(phone: String) =
        telegramRepository.submitPhoneNumber(phone)

    override suspend fun submitTelegramCode(code: String) =
        telegramRepository.submitVerificationCode(code)

    override suspend fun submitTelegramPassword(password: String) =
        telegramRepository.submitPassword(password)

    override suspend fun submitTelegramEmail(email: String) =
        telegramRepository.submitEmailAddress(email)

    override suspend fun submitTelegramEmailCode(code: String) =
        telegramRepository.submitEmailCode(code)

    override suspend fun registerTelegram(firstName: String, lastName: String) =
        telegramRepository.register(firstName, lastName)

    override suspend fun logoutTelegram() = telegramRepository.logout()

    /**
     * True only while the chat list is being refreshed.
     *
     * [busyLabel] means "something is running", so a pull indicator driven by it would spin for
     * work the pull did not start - and go on spinning after the chats had arrived.
     */
    override var chatsRefreshing by mutableStateOf(false)
        private set

    override suspend fun refreshTelegramChats() {
        chatsRefreshing = true
        try {
            telegramRepository.refreshChats()
        } finally {
            chatsRefreshing = false
        }
    }

    override fun updateTelegramSourceDate(value: String): Boolean {
        val parsed = runCatching { LocalDate.parse(value.trim()) }.getOrNull() ?: return false
        telegramSourceDate = parsed
        return true
    }

    override fun selectAnalysisMode(mode: AnalysisMode) {
        analysisMode = mode
        if (mode == AnalysisMode.NEXT_DAY) {
            recommendationTargetDate = egxTargetSession()
        }
        dropLoadedTelegramSources()
    }

    override fun updateRecommendationTargetDate(date: LocalDate) {
        analysisMode = AnalysisMode.SPECIFIC_DATE
        recommendationTargetDate = date
        dropLoadedTelegramSources()
    }

    override suspend fun syncTelegramSources() {
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

    override fun toggleChannel(channel: ChannelSelection) {
        val updated = channel.copy(selected = !channel.selected)
        channels = channels.map { if (it.id == channel.id) updated else it }
        dropLoadedTelegramSources()
        if (activeSourceChannelId !in channels.filter { it.selected }.map { it.id }) {
            activeSourceChannelId = channels.firstOrNull { it.selected }?.id
        }
    }

    override fun removeChannel(channel: ChannelSelection) {
        channels = channels.filterNot { it.id == channel.id }
        dropLoadedTelegramSources()
        if (activeSourceChannelId == channel.id) {
            activeSourceChannelId = channels.firstOrNull { it.selected }?.id
        }
    }

    override fun selectSourceChannel(id: Long?) {
        activeSourceChannelId = id
    }

    override fun addText(value: String) {
        if (value.isNotBlank()) {
            addInput(AnalysisInput.Text("text-${UUID.randomUUID()}", value.trim()))
        }
    }

    override fun addImages(uris: List<Uri>, mimeType: (Uri) -> String?) {
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

    override fun addVoice(uri: Uri, mimeType: String?) {
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

    override fun removeInput(sourceId: String) {
        inputs = inputs.filterNot { it.sourceId == sourceId }
        sourceChannelIds = sourceChannelIds - sourceId
        telegramTraces = telegramTraces - sourceId
    }

    /**
     * The sources a run will send, and what is known about where each one came from.
     *
     * Passed in rather than read off the screen's own fields, because a scheduled run loads its
     * own: someone who has pulled up this morning's messages must not find them replaced, mid-read,
     * by the ones a job went and fetched.
     */
    private data class LoadedSources(
        val inputs: List<AnalysisInput>,
        val traces: Map<String, SourceTrace>,
        val channelOf: Map<String, Long?>,
    )

    /**
     * What a run came to, for a caller that has no screen to read the message off.
     *
     * [Refused] is the one worth separating: nothing was sent and nothing was charged, which is a
     * different thing from a request that failed and wants looking at.
     */
    private sealed interface RunOutcome {
        data class Refused(val reason: String) : RunOutcome
        data class Saved(val summary: String) : RunOutcome
        data class Failed(val reason: String) : RunOutcome
        data object Cancelled : RunOutcome
    }

    override suspend fun analyze() {
        if (analysisStatus == AnalysisStatus.RUNNING) return
        if (analysisMode == AnalysisMode.NEXT_DAY) {
            recommendationTargetDate = egxTargetSession()
        }
        if (analysisMode == AnalysisMode.SPECIFIC_DATE &&
            recommendationTargetDate.isAfter(LocalDate.now(ZoneId.of(EGX_ZONE)))
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
        executeRun(
            plan = screenPlan(),
            sources = LoadedSources(inputs, telegramTraces, sourceChannelIds),
            onScreen = true,
        )
    }

    /**
     * The Analyze screen's current selection, as work a schedule can carry.
     *
     * This is how an analysis job is made: configure a run the way you always do, then put a time
     * on it. Nobody should have to re-pick six chats inside a scheduling form, and a second place
     * to choose them would be a second answer to what a run covers. Null when the screen has
     * nothing selected, which is a schedule there is no point offering.
     */
    override fun scheduledAnalysisFromScreen(): AnalysisAim? {
        val plan = screenPlan()
        return if (plan.isEmpty) null else AnalysisAim(plan.channels, plan.contentTypes)
    }

    /** What the Analyze screen is currently set to run. */
    private fun screenPlan() = AnalysisPlan(
        channels = channels.filter(ChannelSelection::selected)
            .map { AnalysedChannel(it.id, it.displayName) },
        contentTypes = selectedContentTypes,
        mode = analysisMode,
        targetDate = recommendationTargetDate,
    )

    /**
     * Runs one plan, whoever asked for it.
     *
     * The single path from sources to a saved report. It was the back half of [analyze] and read
     * the Analyze screen's fields directly, which is exactly why it had to move: a scheduled run
     * has its own answers to every one of them, and two functions assembling a request would be two
     * sets of rules about what gets sent, what gets filtered and what the report then claims to
     * cover. The one that drifted would have been the unattended one.
     *
     * [onScreen] does not change what is run or what is saved - only what is done to the screen
     * afterwards. A background run must not throw the reader onto the Results tab or swap the
     * report they were reading, while the run state itself is set either way, so the Analyze
     * button shows a scheduled run and can cancel it.
     */
    private suspend fun executeRun(
        plan: AnalysisPlan,
        sources: LoadedSources,
        onScreen: Boolean,
    ): RunOutcome {
        // A caption is part of its photo or voice note, not a text source of its own, so it is
        // selected by whatever selected the media it belongs to. Filtering it as text meant that
        // with only Images chosen every caption was dropped here - the model read each card with
        // none of the words the channel wrote above it, and the phrase filter below, which reads
        // text and nothing else, could never fire.
        val mediaSourceIds = sources.inputs.mapNotNull { input ->
            when (input) {
                is AnalysisInput.Image ->
                    input.sourceId.takeIf { AnalysisContentType.IMAGES in plan.contentTypes }
                is AnalysisInput.Voice ->
                    input.sourceId.takeIf { AnalysisContentType.AUDIO in plan.contentTypes }
                is AnalysisInput.Text -> null
            }
        }.toSet()
        val contentSelectedInputs = sources.inputs.filter {
            when (it) {
                is AnalysisInput.Text -> it.sourceId in mediaSourceIds ||
                    AnalysisContentType.TEXT in plan.contentTypes
                is AnalysisInput.Image -> AnalysisContentType.IMAGES in plan.contentTypes
                is AnalysisInput.Voice -> AnalysisContentType.AUDIO in plan.contentTypes
            }
        }
        val rules = ruleSet
        val filtered = AnalysisPolicy.filter(contentSelectedInputs, rules)
        val selectedInputs = filtered.accepted
        if (selectedInputs.isEmpty()) {
            return refused(
                if (filtered.excluded.isNotEmpty()) {
                    "All selected sources were excluded by the recommendation filters."
                } else {
                    "Add at least one selected source."
                },
                onScreen,
            )
        }
        val window = resolveAnalysisWindow(plan.mode, plan.targetDate)
        if (onScreen) recommendationTargetDate = window.targetDate
        val request = AnalysisRequest(
            channelIds = plan.channelIds,
            selectedChannels = plan.channels,
            contentTypes = plan.contentTypes,
            inputs = selectedInputs,
            mode = plan.mode,
            targetDate = window.targetDate,
            provider = cloudConfiguration.provider,
            model = cloudConfiguration.model,
            sourceWindowStart = window.start,
            sourceWindowEnd = window.endExclusive,
            excludedSources = filtered.excluded,
            rules = rules,
            prompt = activePrompt,
            sourceTraces = selectedInputs.map { input ->
                sources.traces[input.sourceId] ?: run {
                val channel = plan.channels.firstOrNull { it.id == sources.channelOf[input.sourceId] }
                SourceTrace(
                    sourceId = input.sourceId,
                    channelId = channel?.id,
                    channelName = channel?.name ?: "On-device import",
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
            // The run has just spent; the tally on disk has moved and the screen's copy has not.
            refreshModelUsage()
            localDataStore.saveResult(result, cloudConfiguration.provider, cloudConfiguration.model)
            savedResults = localDataStore.results()
            unreadableResults = localDataStore.unreadableResults
            // The newest report leads only for the reader who asked for it. A scheduled run must
            // not swap out the report someone has open.
            if (onScreen) selectedResult = savedResults.firstOrNull()
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
            // Published as soon as it exists, so another device only ever has to pull. In the
            // background: a slow Telegram used to hold the tail of a run, leaving the screen on
            // "saved" while nothing moved. A failure here is not the run failing either - the
            // report is on disk, and the next sync's diff carries it.
            savedResults.firstOrNull { it.result.requestId == result.requestId }?.let { saved ->
                publish { telegramRepository.uploadReport(saved.toSyncedRun()) }
            }
            if (onScreen) destination = AppDestination.RESULTS
            return RunOutcome.Saved(analysisMessage ?: "Saved.")
        } catch (_: CancellationException) {
            analysisStatus = AnalysisStatus.CANCELLED
            analysisMessage = "Analysis cancelled."
            analysisStopped(null)
            return RunOutcome.Cancelled
        } catch (error: Exception) {
            if (analysisStatus != AnalysisStatus.CANCELLED) {
                analysisStatus = AnalysisStatus.FAILED
                analysisMessage = error.message ?: "Analysis failed."
                analysisStopped(analysisMessage)
            }
            return RunOutcome.Failed(error.message ?: "Analysis failed.")
        } finally {
            activeRequestId = null
            analysisStartedAt = null
        }
    }

    /**
     * A run that never started, said once.
     *
     * The message reaches the screen only when a reader is there to have asked; a scheduled run
     * carries the same words into its own record instead.
     */
    private fun refused(reason: String, onScreen: Boolean): RunOutcome.Refused {
        if (onScreen) analysisMessage = reason
        return RunOutcome.Refused(reason)
    }

    override suspend fun cancelAnalysis() {
        activeRequestId?.let { analysisRepository.cancel(it) }
        analysisJob?.cancel()
        analysisStatus = AnalysisStatus.CANCELLED
        analysisMessage = "Analysis cancelled."
        analysisStopped(null)
    }

    override fun selectResult(result: SavedAnalysis) {
        selectedResult = result
    }

    /**
     * Removes a report here and everywhere.
     *
     * The intent is recorded before the row goes, so a delete survives being offline, a crash, or
     * Telegram being slow: the next sync buries it in the channel and every other device drops it.
     */
    override fun deleteResult(result: SavedAnalysis) {
        localDataStore.recordDeletion(result.result.requestId)
        localDataStore.deleteResult(result.id)
        // The database dropped this report's opinions on the way out; the map on screen has to
        // agree, or a card would go on offering to reopen an answer that is no longer stored.
        opinions = localDataStore.stockOpinions()
        savedResults = localDataStore.results()
        unreadableResults = localDataStore.unreadableResults
        selectedResult = savedResults.firstOrNull()
        appScope.launch {
            recomputePerformance()
            runCatching { telegramRepository.buryReport(result.result.requestId) }
                .onSuccess { localDataStore.clearDeletion(result.result.requestId) }
        }
    }

    override fun deleteAllResults() {
        // Recorded before anything is removed, so a delete that spans every report cannot half
        // happen: whatever the app manages to bury now, the rest goes at the next sync.
        val doomed = savedResults.map { it.result.requestId }
        doomed.forEach(localDataStore::recordDeletion)
        localDataStore.deleteAllResults()
        opinions = emptyMap()
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

    override fun reportFor(saved: SavedAnalysis): AnalysisReport {
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
            // What was actually done about this session, on the prices actually paid. Closed by
            // hand or closed by the deadline, every position for the session is listed: a record
            // that quietly dropped the trades cut short would flatter itself.
            val held = result.recommendationTargetDate
                ?.let { date -> portfolio.positions.filter { it.recommendationDate == date } }
                .orEmpty()
            if (held.isNotEmpty()) {
                appendLine("## Your positions")
                appendLine()
                held.sortedBy(PositionView::ticker).forEach { view ->
                    val position = view.position
                    appendLine("### ${position.ticker} — ${view.status.label}")
                    appendLine("- Entry: ${formatPrice(position.entryPrice)} on ${position.entryDate}")
                    appendLine(
                        "- Exit: ${formatPrice(view.exitPrice)}" +
                            (position.exitDate?.let { " on $it" } ?: "") +
                            " (${if (view.realized) "realized" else "estimated"})" +
                            // A sale made in two parts says so here too. The figure above it is
                            // the blend, and a record that printed it bare would be quoting a
                            // price the trade was never actually done at.
                            if (view.soldInParts) {
                                " - ${formatPrice(position.exitSplitPct)}% at " +
                                    "${formatPrice(position.exitPrice1)}" +
                                    (position.exitDate1?.let { " on $it" } ?: "") +
                                    ", the rest at ${formatPrice(position.exitPrice2)}"
                            } else {
                                ""
                            },
                    )
                    appendLine("- Return: ${formatPercent(view.returnPct)}")
                    appendLine(
                        "- Deadline: " + (
                            view.deadlineDate?.let { "passed $it" }
                                ?: "${view.sessionsRemaining} of ${position.windowSessions} sessions left"
                            ) +
                            (if (position.windowCustom) " (window set by hand)" else "") +
                            (
                                if (view.overdue) {
                                    " · ${view.overdueDays} ${view.overdueDays.dayWord()} overdue"
                                } else {
                                    ""
                                }
                                ) +
                            (if (position.keepOpen) " · kept open until sold" else ""),
                    )
                    appendLine()
                }
            }
        }
        return AnalysisReport(
            title = "EGX analysis ${result.recommendationTargetDate ?: result.completedAt}",
            markdown = markdown,
        )
    }
}
