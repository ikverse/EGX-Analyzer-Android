package com.ikverse.egxanalyzer.ui

import android.content.Intent
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
import com.ikverse.egxanalyzer.data.BackupRecord
import com.ikverse.egxanalyzer.data.promptVersionsToRestore
import com.ikverse.egxanalyzer.data.positionsToRestore
import com.ikverse.egxanalyzer.data.rulesToRestore
import com.ikverse.egxanalyzer.data.runsToRestore
import com.ikverse.egxanalyzer.data.RestoreOutcome
import com.ikverse.egxanalyzer.data.AvailableUpdate
import com.ikverse.egxanalyzer.data.DownloadedApk
import com.ikverse.egxanalyzer.data.SettingsSnapshot
import com.ikverse.egxanalyzer.data.SyncedPosition
import com.ikverse.egxanalyzer.data.SyncedPromptVersion
import com.ikverse.egxanalyzer.data.SyncedRule
import com.ikverse.egxanalyzer.data.UpdateRepository
import com.ikverse.egxanalyzer.data.UpdateState
import com.ikverse.egxanalyzer.data.mergeRules
import com.ikverse.egxanalyzer.data.settingsWorthUploading
import com.ikverse.egxanalyzer.data.positionsToUpload
import com.ikverse.egxanalyzer.data.rulesToUpload
import com.ikverse.egxanalyzer.data.AnalysisPolicy
import com.ikverse.egxanalyzer.data.EndpointPolicy
import com.ikverse.egxanalyzer.data.EgxCatalog
import com.ikverse.egxanalyzer.data.JobRunner
import com.ikverse.egxanalyzer.data.JobSkipped
import com.ikverse.egxanalyzer.data.LocalDataStore
import com.ikverse.egxanalyzer.data.PerformanceCalculator
import com.ikverse.egxanalyzer.data.PortfolioCalculator
import com.ikverse.egxanalyzer.data.IntradayRepository
import com.ikverse.egxanalyzer.data.PriceHealth
import com.ikverse.egxanalyzer.data.PriceHealthReport
import com.ikverse.egxanalyzer.data.PriceRepository
import com.ikverse.egxanalyzer.data.UnorderedSession
import com.ikverse.egxanalyzer.data.SettingsRepository
import com.ikverse.egxanalyzer.data.SyncOutcome
import com.ikverse.egxanalyzer.data.SyncedRun
import com.ikverse.egxanalyzer.data.syncActions
import com.ikverse.egxanalyzer.data.recommendedTickers
import com.ikverse.egxanalyzer.data.TelegramRepository
import com.ikverse.egxanalyzer.model.AnalysedChannel
import com.ikverse.egxanalyzer.model.AnalysisPlan
import com.ikverse.egxanalyzer.model.AnalysisContentType
import com.ikverse.egxanalyzer.model.AnalysisLanguage
import com.ikverse.egxanalyzer.model.AnalysisInput
import com.ikverse.egxanalyzer.model.AnalysisMode
import com.ikverse.egxanalyzer.model.AnalysisRequest
import com.ikverse.egxanalyzer.model.AnalysisReport
import com.ikverse.egxanalyzer.model.AppPreferences
import com.ikverse.egxanalyzer.model.ResponseTimeout
import com.ikverse.egxanalyzer.model.CallAlerts
import com.ikverse.egxanalyzer.model.CallChange
import com.ikverse.egxanalyzer.model.CallOrder
import com.ikverse.egxanalyzer.model.ChannelSelection
import com.ikverse.egxanalyzer.model.ChatKind
import com.ikverse.egxanalyzer.model.CloudConfiguration
import com.ikverse.egxanalyzer.model.CloudProvider
import com.ikverse.egxanalyzer.model.LatestPrice
import com.ikverse.egxanalyzer.model.Outcome
import com.ikverse.egxanalyzer.model.PerformanceReport
import com.ikverse.egxanalyzer.model.DailySession
import com.ikverse.egxanalyzer.model.Portfolio
import com.ikverse.egxanalyzer.model.PortfolioOrder
import com.ikverse.egxanalyzer.model.JobWork
import com.ikverse.egxanalyzer.model.Position
import com.ikverse.egxanalyzer.model.ScheduledJob
import com.ikverse.egxanalyzer.model.PositionView
import com.ikverse.egxanalyzer.model.PromptSnapshot
import com.ikverse.egxanalyzer.model.SavedAnalysis
import com.ikverse.egxanalyzer.model.Scoring
import com.ikverse.egxanalyzer.model.SourceTrace
import com.ikverse.egxanalyzer.model.TelegramAuthState
import com.ikverse.egxanalyzer.model.TelegramAuthStep
import com.ikverse.egxanalyzer.model.ThemeMode
import com.ikverse.egxanalyzer.model.TradeAlerts
import com.ikverse.egxanalyzer.model.TradeChange
import com.ikverse.egxanalyzer.model.egxTargetSession
import com.ikverse.egxanalyzer.model.resolveAnalysisWindow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import com.ikverse.egxanalyzer.data.OpinionParser
import com.ikverse.egxanalyzer.data.OpinionPrompt
import com.ikverse.egxanalyzer.data.OpinionPromptStore
import com.ikverse.egxanalyzer.data.OpinionRequest
import com.ikverse.egxanalyzer.data.OpinionSearchBrief
import com.ikverse.egxanalyzer.model.ScoredCall
import com.ikverse.egxanalyzer.model.ScoredSession
import com.ikverse.egxanalyzer.model.SessionDigest
import com.ikverse.egxanalyzer.model.StockOpinion
import com.ikverse.egxanalyzer.model.opinionId

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

class AppState(
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
     * Books or cancels this phone's schedule alarm; supplied by the app so this class stays testable.
     */
    private val schedulesChanged: (jobs: List<ScheduledJob>, enabled: Boolean) -> Unit = { _, _ -> },
    /**
     * Whether this process was started by the clock rather than by its owner.
     *
     * Headless leaves [enterForeground] unrun, so nothing here connects to Telegram, syncs, or asks
     * GitHub about a newer build. See that function for why. False everywhere but the scheduled
     * worker, including in tests, so the ordinary start is exactly what it always was.
     */
    private val headless: Boolean = false,
) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Started by whichever path first needs Telegram, and never by merely existing. */
    private val telegramRepository: TelegramRepository by lazy { telegramProvider() }
    var destination by mutableStateOf(AppDestination.ANALYZE)
        private set

    /**
     * What each tab had open and filtered to, kept out of the composition so a fold cannot take it.
     *
     * Here for the same reason [destination] is: the shell rebuilds the whole page when the phone
     * changes shape, and anything a screen was holding in a `remember` goes with it. See [PageState].
     */
    internal val pages = PageState()
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
    // Seeded from disk rather than empty: the picker is the only safe way to choose a model, and a
    // list that died with the process meant every cold start offered a text field instead.
    var availableModels by mutableStateOf(settingsRepository.modelList(cloudConfiguration.provider))
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
                error.message?.takeIf(String::isNotBlank) ?: "$label failed",
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

    /**
     * A trade to reveal on the Portfolio tab, named by its position id.
     *
     * The two tabs answer different questions about one call - what the channel's levels did, and
     * what the user's own money did - and reading one used to mean finding the other by hand. A
     * card that has a counterpart carries a press to it, and the counterpart presses back.
     */
    var pendingPositionId by mutableStateOf<String?>(null)
        private set

    /** A call to reveal on the Insights tab, named by the same id: one call is one holding. */
    var pendingCallId by mutableStateOf<String?>(null)
        private set

    fun openPosition(id: String) {
        // The trip the user just made is over. Left set, a highlight still counting down on the tab
        // being left would fire again the moment they pressed their way back to it.
        pendingCallId = null
        pendingPositionId = id
        destination = AppDestination.PORTFOLIO
    }

    fun consumePendingPosition() {
        pendingPositionId = null
    }

    fun openCall(id: String) {
        pendingPositionId = null
        pendingCallId = id
        destination = AppDestination.INSIGHTS
    }

    fun consumePendingCall() {
        pendingCallId = null
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

    /** How every saved call turned out, recomputed whenever the analyses or the prices change. */
    var performance by mutableStateOf(PerformanceReport())
        private set

    /**
     * Which stocks the price feed has gone quiet about, and what it is costing the record.
     *
     * Derived beside [performance] and from the same read, never cached: it is a reading of the
     * prices on disk, exactly as an outcome is, so it is right after a restart without anything
     * having been stored. Built from the **whole** record rather than a filtered view - a channel
     * filter is a view of the calls, never a claim about which prices are broken.
     */
    var priceHealth by mutableStateOf(PriceHealthReport())
        private set
    var pricesRefreshing by mutableStateOf(false)
        private set

    /**
     * The trades the user has actually taken, and what the market has since done with them.
     *
     * Held apart from [performance] on purpose. That report judges the sources on the levels they
     * printed; this one judges the user's own trades on the prices they paid, and folding either
     * into the other would leave both answering a question nobody asked.
     */
    var portfolio by mutableStateOf(Portfolio())
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
    var sessionDigest by mutableStateOf<SessionDigest?>(null)
        private set

    private var positions = localDataStore.positions()

    /**
     * What Ask AI has said, by the call it was asked about.
     *
     * Loaded whole at start rather than queried per card: the map is one small row per call the
     * user has actually pressed the button on, and a card has to know on sight whether to offer
     * "Ask AI" or "AI opinion" without a database read while the list scrolls.
     */
    var opinions by mutableStateOf(localDataStore.stockOpinions())
        private set

    /** Non-null while one opinion is being fetched, so only that card shows a spinner. */
    var opinionPending by mutableStateOf<String?>(null)
        private set

    fun opinionFor(call: ScoredCall): StockOpinion? =
        opinions[opinionId(call.ticker, call.openedOn, call.channel)]

    /** What an opinion request would run on and whether it would search, for the confirm dialog. */
    fun opinionModel(): String = settingsRepository.opinionModel(cloudConfiguration.provider)
        .ifBlank { cloudConfiguration.model }

    fun opinionSearchEnabled(): Boolean = settingsRepository.opinionSearchEnabled()

    /** How far back a searched request looks for news, in days. */
    fun opinionNewsWindowDays(): Int = settingsRepository.opinionNewsWindowDays()

    fun updateOpinionNewsWindow(days: Int) {
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
    fun opinionSearchResults(): Int = settingsRepository.opinionSearchResults()

    fun updateOpinionSearchResults(count: Int) {
        settingsRepository.saveOpinionSearchResults(count)
        opinionSettingsRevision += 1
    }

    fun opinionDeepSearch(): Boolean = settingsRepository.opinionDeepSearchEnabled()

    fun updateOpinionDeepSearch(enabled: Boolean) {
        settingsRepository.saveOpinionDeepSearchEnabled(enabled)
        opinionSettingsRevision += 1
    }

    fun updateOpinionModel(model: String) {
        settingsRepository.saveOpinionModel(cloudConfiguration.provider, model)
        // Nothing derives from it, but the Settings summary reads it back through a snapshot that
        // only recomposes when something observable moves.
        opinionSettingsRevision += 1
    }

    fun updateOpinionSearch(enabled: Boolean) {
        settingsRepository.saveOpinionSearchEnabled(enabled)
        opinionSettingsRevision += 1
    }

    /** Bumped whenever an Ask AI setting changes, so the Settings card redraws its summary. */
    var opinionSettingsRevision by mutableStateOf(0)
        private set

    /**
     * Asks the model about one call and keeps what it says.
     *
     * A paid request, started only by the user pressing through the confirmation - never by a
     * refresh, a sync, or a card being drawn. The answer is stored against the call so re-opening
     * the sheet costs nothing; [askAgain] is the only way to pay twice.
     */
    fun askAboutCall(call: ScoredCall, askAgain: Boolean = false) {
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
    fun deleteOpinion(call: ScoredCall) {
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
    fun heldFor(ticker: String, recommendationDate: LocalDate?): PositionView? =
        portfolio.heldFor(ticker, recommendationDate)

    /**
     * Records a trade against a recommendation.
     *
     * The call's levels are copied in rather than referenced: the report can be deleted or replaced
     * by a later run of the same session, and neither may rewrite a trade that has already happened.
     * Pressing Bought again on a card that already has a position replaces it, so a mistyped price
     * is corrected rather than duplicated.
     */
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
    fun recordSale(position: Position, exitPrice: Double, exitDate: LocalDate) {
        val closed = position.copy(
            exitPrice = exitPrice,
            exitDate = exitDate,
            closedManually = true,
            updatedAt = System.currentTimeMillis(),
            updatedBy = deviceName,
        )
        localDataStore.savePosition(closed)
        positions = localDataStore.positions()
        statusMessage = StatusMessage("${position.ticker} closed at ${formatPrice(exitPrice)}", true)
        publishPosition(closed, deleted = false)
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
    fun reprice(
        position: Position,
        entryPrice: Double,
        entryDate: LocalDate,
        windowSessions: Int = position.windowSessions,
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
    fun setKeepOpen(position: Position, keepOpen: Boolean, note: String? = null) {
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
    fun deletePosition(position: Position) {
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
    fun refreshOverdue() {
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
        val rebuilt = withContext(Dispatchers.IO) {
            // Read once for the whole rebuild rather than per position: it is one small table, and
            // a trade must be judged on the same reading of the feed as the call it came from.
            val breaks = localDataStore.priceBreakDates()
            PortfolioCalculator.build(
                positions = held,
                sessionsFor = localDataStore::sessionsFrom,
                latestQuoteFor = localDataStore::latestQuote,
                today = today,
                priceBreaksFor = { ticker -> breaks[ticker].orEmpty() },
            )
        }
        portfolio = rebuilt
        reviewTrades(rebuilt, announceChanges)
        // After the trades, and off the same recompute. Every path that announces recomputes the
        // performance first, so the calls this reads are the ones the prices were just scored
        // against - and the holdings it excludes are the ones set two lines above.
        reviewCalls(performance, announceChanges)
        reviewSessions(rebuilt, performance)
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
    private suspend fun reviewSessions(rebuilt: Portfolio, report: PerformanceReport) {
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
    fun enterForeground() {
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
    var updateState by mutableStateOf<UpdateState>(UpdateState.Idle)
        private set

    /**
     * Asks GitHub whether a newer build exists, and answers either way.
     *
     * The button is a question, so "you are on the newest version" is an answer worth giving. The
     * launch check is not, which is why it is [checkForUpdateQuietly] and not this.
     */
    fun checkForUpdate() {
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
    fun downloadUpdate(update: AvailableUpdate) {
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
    fun dismissUpdate() {
        updateState = UpdateState.Idle
    }

    /**
     * Hands the downloaded APK to Android to install.
     *
     * The confirmation is Android's own and arrives a moment later, through
     * [com.ikverse.egxanalyzer.data.UpdateInstallReceiver]. A failure to even start says so here,
     * because a button that appears to do nothing is what this whole path cost three releases.
     */
    fun installUpdate(file: File) {
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
    fun reportUpdateProblem(reason: String) {
        statusMessage = StatusMessage(reason, succeeded = false)
    }

    /** True once the user has allowed this app to install apps; Android is the only one who can ask. */
    fun canInstallUpdates(): Boolean = updateRepository?.canInstall() ?: false

    fun installPermissionIntent(): Intent? = updateRepository?.permissionIntent()

    fun releasesPageIntent(): Intent? = updateRepository?.releasesPageIntent()

    fun updateAutomaticUpdateChecks(enabled: Boolean) {
        if (enabled == appPreferences.updateChecksEnabled) return
        persistPreferences(appPreferences.copy(updateChecksEnabled = enabled))
    }

    fun navigate(destination: AppDestination) {
        this.destination = destination
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
    var scrollToTopRequest by mutableStateOf<Pair<AppDestination, Int>?>(null)
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
    fun scrollToTop(destination: AppDestination) {
        scrollToTopCount++
        scrollToTopRequest = destination to scrollToTopCount
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
        // Each provider keeps its own list, so switching back to one already loaded finds it there.
        availableModels = settingsRepository.modelList(provider)
        modelListMessage = null
    }

    fun updateEndpoint(endpoint: String) {
        cloudConfiguration = cloudConfiguration.copy(endpoint = endpoint)
        // A different endpoint is a different catalogue - regions do not carry the same models - so
        // the stored list stops being an answer about this connection.
        settingsRepository.clearModelList(cloudConfiguration.provider)
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
    fun persistModelChoice() {
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
    suspend fun verifyCredential() {
        busyLabel = "Verifying API key"
        try {
            val models = analysisRepository.listModels()
            credentialVerified = true
            availableModels = models
            settingsRepository.saveModelList(cloudConfiguration.provider, models)
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
            settingsRepository.saveModelList(cloudConfiguration.provider, availableModels)
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

    fun removeCredential() {
        settingsRepository.removeCredential(cloudConfiguration.provider)
        // A different key can be a different account with a different catalogue.
        settingsRepository.clearModelList(cloudConfiguration.provider)
        availableModels = emptyList()
        cloudConfiguration = settingsRepository.configurationFor(cloudConfiguration.provider)
        credentialVerified = null
        settingsMessage = "Saved credential removed."
    }

    fun resetProviderConfiguration() {
        // Clears the stored list too, so the endpoint the reset restores is not left with the
        // catalogue of the one it replaced.
        settingsRepository.resetProviderConfiguration(cloudConfiguration.provider)
        availableModels = emptyList()
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
        saveAppPreferences(
            appPreferences.copy(
                responseTimeoutSeconds = value.coerceIn(
                    ResponseTimeout.MIN,
                    ResponseTimeout.MAX,
                ),
            ),
        )
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
     * Turns the daily overdue reminder on or off.
     *
     * The scheduling follows the preference rather than being booked once at startup: a user who
     * turns it off wants the phone to stop waking up for it, not to keep waking up and decide each
     * time that it has nothing to say.
     */
    fun updateOverdueReminders(enabled: Boolean) {
        if (enabled == appPreferences.overdueRemindersEnabled) return
        persistPreferences(appPreferences.copy(overdueRemindersEnabled = enabled))
        dailyCheckChanged(dailyCheckWanted)
    }

    /**
     * Turns the trade status notifications on or off.
     *
     * Most of what these report rides on work the app was doing anyway - a price refresh re-scores
     * every trade whether or not anyone is told - so this mostly decides whether the phone speaks.
     * The daily wake is the exception and is why [dailyCheckWanted] is re-asked here: a window
     * running out has no prices behind it, and that ending is only ever noticed by the once-a-day
     * look at the record. The sweep that records where each trade stands goes on running either
     * way, so switching this back on reports what happens next rather than reciting everything
     * that happened while it was off.
     */
    fun updateTradeAlerts(enabled: Boolean) {
        if (enabled == appPreferences.tradeAlertsEnabled) return
        persistPreferences(appPreferences.copy(tradeAlertsEnabled = enabled))
        dailyCheckChanged(dailyCheckWanted)
    }

    /**
     * Whether the phone says a stock has traded into a buy zone nobody has acted on.
     *
     * Gates the notification and never the sweep, like every other switch here. It books no
     * background work of its own: the sweep rides the price refresh, which is already happening
     * for other reasons, so switching this on adds a notification and not a wake-up.
     */
    fun updateCallAlerts(enabled: Boolean) {
        if (enabled == appPreferences.callAlertsEnabled) return
        persistPreferences(appPreferences.copy(callAlertsEnabled = enabled))
    }

    /**
     * Whether the daily wake is worth booking at all.
     *
     * One run answers both questions off one reading of the record, so the work is wanted while
     * either notification is on. Turning the overdue reminder off used to be enough to cancel it;
     * doing that now would take the deadline notifications with it silently, which is the shape of
     * bug that only shows up as "it stopped telling me" weeks later.
     */
    private val dailyCheckWanted: Boolean
        get() = appPreferences.overdueRemindersEnabled || appPreferences.tradeAlertsEnabled

    /**
     * The jobs this phone runs on its own.
     *
     * Held here so a screen can list them, and read back from disk after every change rather than
     * edited in memory: the alarm that fires them can wake a process with no screen in it, and the
     * list the runner works from has to be the same list the card is showing.
     */
    var scheduledJobs by mutableStateOf(localDataStore.scheduledJobs())
        private set

    /**
     * Whether this phone keeps its schedules at all.
     *
     * Off until it is switched on, unlike the overdue reminder beside it. That one reads a date
     * already on disk; this one wakes the phone to do work, and a feature that starts doing things
     * unattended because it shipped is not one the user agreed to. Stored outside [AppPreferences]
     * so it stays this device's own answer - see `SettingsRepository.schedulesEnabled`.
     */
    var schedulesEnabled by mutableStateOf(settingsRepository.schedulesEnabled())
        private set

    /**
     * Whether a schedule here may start work that spends cloud credits.
     *
     * A second switch behind [schedulesEnabled], off until it is turned on, and the only thing
     * standing between the clock and the owner's money. Separate because the two decisions are
     * separate: letting the phone refresh prices while it is asleep says nothing about letting it
     * send a paid request.
     */
    var paidSchedulesEnabled by mutableStateOf(settingsRepository.paidSchedulesEnabled())
        private set

    fun updateSchedulesEnabled(enabled: Boolean) {
        if (enabled == schedulesEnabled) return
        settingsRepository.saveSchedulesEnabled(enabled)
        schedulesEnabled = enabled
        rebookSchedules()
    }

    fun updatePaidSchedulesEnabled(enabled: Boolean) {
        if (enabled == paidSchedulesEnabled) return
        settingsRepository.savePaidSchedulesEnabled(enabled)
        paidSchedulesEnabled = enabled
        // Nothing to re-book: the alarm is booked for a paid job either way, and the runner is
        // what refuses it. Booking on the switch would mean a job that vanished from the schedule
        // rather than one that says why it was passed over.
    }

    fun saveScheduledJob(job: ScheduledJob) {
        localDataStore.saveScheduledJob(job)
        scheduledJobs = localDataStore.scheduledJobs()
        rebookSchedules()
    }

    fun deleteScheduledJob(id: String) {
        localDataStore.deleteScheduledJob(id)
        scheduledJobs = localDataStore.scheduledJobs()
        rebookSchedules()
    }

    /** Books the alarm for whatever is now nearest, after anything that could have moved it. */
    private fun rebookSchedules() = schedulesChanged(scheduledJobs, schedulesEnabled)

    /**
     * Serves every schedule whose fire has come and gone unanswered, then books the next alarm.
     *
     * The entry point for the worker the alarm wakes, and the only one: re-booking from in here
     * means a run can never leave the phone without an alarm for the fire after it.
     */
    suspend fun runDueScheduledJobs() {
        JobRunner(
            jobs = localDataStore::scheduledJobs,
            record = localDataStore::saveScheduledJob,
            schedulesEnabled = settingsRepository::schedulesEnabled,
            paidWorkAllowed = settingsRepository::paidSchedulesEnabled,
        ).runDue(::performScheduledWork)
        scheduledJobs = localDataStore.scheduledJobs()
        schedulesEnabled = settingsRepository.schedulesEnabled()
        rebookSchedules()
    }

    /**
     * Does one job's work, through exactly the path a press on screen would take.
     *
     * Given the moment the job came due rather than the moment it actually started, because a run
     * that is late still has to reason about the slot it is filling.
     */
    private suspend fun performScheduledWork(work: JobWork, due: Instant): String = when (work) {
        JobWork.PriceRefresh -> {
            // A session's prices settle once and stay settled, so a refresh that already happened
            // after this fire came due has done this job's work for it. Without the check, opening
            // the app inside a missed job's grace window fetches every stock twice within seconds -
            // once for the launch, once for the job - from a public feed the app is a guest on.
            if (settingsRepository.lastPriceRefreshAt() >= due.toEpochMilli()) {
                throw JobSkipped("Prices had already been fetched since this run came due.")
            }
            val outcome = refreshPrices(announce = false)
            if (outcome.busy) throw JobSkipped(outcome.summary)
            // A partial answer is still an answer and is recorded as one; only a refresh that threw
            // or found nothing to fetch is worth a reader's attention the next morning.
            if (!outcome.succeeded) error(outcome.summary)
            outcome.summary
        }

        is JobWork.Analysis -> runScheduledAnalysis(work, due)

        // Never reached: the runner refuses a job this build cannot run before it gets this far.
        is JobWork.Unsupported -> error("This version cannot run ${work.kind}.")
    }

    /**
     * An analysis started by the clock rather than by a press.
     *
     * Everything before the run is a reason not to make it. This is the only thing in the app that
     * spends the owner's money without being asked to at that moment, so each guard below is a
     * separate way of being wrong that costs a real request, and every one of them ends in
     * [JobSkipped] - written down, not charged, and tried again at the next fire.
     */
    private suspend fun runScheduledAnalysis(work: JobWork.Analysis, due: Instant): String {
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
        val plan = work.plan()
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
        duplicateOf(window.targetDate, plan.channelIds.toSet())?.let {
            throw JobSkipped("The $intended session is already analysed for these chats.")
        }
        val batch = telegramRepository.collectSources(
            channelIds = plan.channelIds,
            start = window.start,
            endExclusive = window.endExclusive,
            contentTypes = plan.contentTypes,
        )
        if (batch.inputs.isEmpty()) {
            throw JobSkipped("The chats posted nothing in the window for the $intended session.")
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
    fun updatePortfolioOrder(order: PortfolioOrder) {
        if (order == appPreferences.portfolioOrder) return
        persistPreferences(appPreferences.copy(portfolioOrder = order))
    }

    /**
     * Remembers the order the calls inside a session card are being read in.
     *
     * Kept for the same reason, and it recomputes nothing: every option orders the same calls, so
     * this only ever changes where a card sits on screen. No rate, ranking or verdict moves.
     */
    fun updateCallOrder(order: CallOrder) {
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
    fun updateDefaultTradeWindow(sessions: Int) {
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
    fun databaseFile(): java.io.File = localDataStore.databaseFile()

    fun checkpointDatabase() = localDataStore.checkpoint()

    /**
     * The folder backups are written to, as the tree URI string the user granted.
     *
     * A string rather than a `Uri` because `Uri` is stubbed in unit tests, and this class is meant
     * to stay drivable without Android - the same reason `AnalysisPlan` carries chat ids and not
     * chats. The screen parses it; nothing here needs to.
     */
    var backupFolder by mutableStateOf(settingsRepository.backupFolder())
        private set

    fun saveBackupFolder(uri: String?) {
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
    fun settingsDocument(): String = settingsRepository.snapshot().toDocument()

    /** What to write in a backup's metadata as its author. */
    fun backupDevice(): String = deviceName

    /**
     * Whether today's backup still has to be written.
     *
     * A day, not a launch: the automatic backup rides the same moment the launch sync does, and a
     * phone opened six times before lunch would otherwise write six copies of an unchanged record
     * and push five real days out of the seven a folder keeps.
     */
    fun backupDue(): Boolean =
        settingsRepository.lastBackupDay() != LocalDate.now(ZoneId.of(EGX_ZONE)).toString()

    fun recordBackupDay() {
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

    suspend fun refreshPrices(announce: Boolean = true): PriceRefreshOutcome {
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
                clean = notes.isEmpty(),
            )
            if (announce) {
                statusMessage = StatusMessage(outcome.summary, succeeded = outcome.clean)
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
            // The exchange's calendar, not the phone's: a session dated today is still trading, so
            // nothing it reports about itself is final.
            val today = LocalDate.now(ZoneId.of(EGX_ZONE))
            val latest = localDataStore.latestSessions().mapValues { (_, session) ->
                LatestPrice(
                    session = session,
                    provisional = !session.date.isBefore(today) || session.inconsistent,
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
                today = today,
            )
            // On the same thread and from the same read: the breaks and the latest sessions are
            // already in hand here, and asking for them again on the main thread would be a second
            // trip to the database for figures this one has already paid for.
            report to PriceHealth.assess(
                calls = report.sessions.flatMap(ScoredSession::calls),
                latestPrices = latest,
                breaks = breaks,
                today = today,
            )
        }
        performance = computed.first
        priceHealth = computed.second
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
        val dailyCheckWas = dailyCheckWanted
        settingsRepository.adopt(snapshot)
        appPreferences = settingsRepository.loadPreferences()
        // The Analyze screen seeds its content types from the preference at launch, which on a
        // reinstalled phone happens before this arrives. Left alone it would show the shipped
        // default until the next restart - and what it is being set to is the user's own choice.
        selectedContentTypes = appPreferences.defaultContentTypes
        cloudConfiguration = settingsRepository.load()
        availableModels = settingsRepository.modelList(cloudConfiguration.provider)
        useDefaultPromptOnly = settingsRepository.useDefaultPromptOnly()
        promptHistory = settingsRepository.promptHistory()
        // The daily check is booked with the system, not with this class: a device that adopts
        // "off" has to have the work cancelled, or it goes on waking up to say nothing.
        if (dailyCheckWanted != dailyCheckWas) dailyCheckChanged(dailyCheckWanted)
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
    suspend fun restoreFrom(record: BackupRecord): RestoreOutcome {
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

    suspend fun startTelegramQrSignIn() = runAction(
        label = "Preparing a Telegram sign-in code",
        success = { "Scan the code in Telegram" },
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

    suspend fun analyze() {
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
    fun scheduledAnalysisFromScreen(): JobWork.Analysis? {
        val plan = screenPlan()
        return if (plan.isEmpty) null else JobWork.Analysis(plan.channels, plan.contentTypes)
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

    fun deleteAllResults() {
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
                            " (${if (view.realized) "realized" else "estimated"})",
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
)

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
 * Two verdicts rather than one, because two readers want different answers from the same run.
 * [clean] colours the status line: a refresh that came back with three stocks unpriced worked, and
 * painting that red would be wrong. [succeeded] is what a scheduled job files, and there a partial
 * answer is still an answer - only a refresh that threw, or found nothing to fetch, is worth
 * anyone's attention the next morning.
 */
data class PriceRefreshOutcome(
    val summary: String,
    val succeeded: Boolean,
    val clean: Boolean = false,
    /**
     * True where nothing was fetched because a refresh was already under way.
     *
     * Its own answer rather than a failure, because it is the likely shape of a collision between
     * a scheduled run and the refresh a launch starts: nothing went wrong, and filing it as an
     * error would put a red line on a schedule that behaved perfectly.
     */
    val busy: Boolean = false,
)
