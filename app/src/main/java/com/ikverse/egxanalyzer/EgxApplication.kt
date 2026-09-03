package com.ikverse.egxanalyzer

import android.app.Application
import com.ikverse.egxanalyzer.data.AppShortcuts
import com.ikverse.egxanalyzer.data.ApproachNotifier
import com.ikverse.egxanalyzer.data.AttentionNotifier
import com.ikverse.egxanalyzer.data.SessionDigestNotifier
import com.ikverse.egxanalyzer.data.AnalysisNotifier
import com.ikverse.egxanalyzer.data.AnalysisService
import com.ikverse.egxanalyzer.data.AndroidKeystoreCredentialStore
import com.ikverse.egxanalyzer.data.CallAlertNotifier
import com.ikverse.egxanalyzer.data.CloudAnalysisRepository
import com.ikverse.egxanalyzer.data.IntradayRepository
import com.ikverse.egxanalyzer.data.JobScheduler
import com.ikverse.egxanalyzer.data.LocalDataStore
import com.ikverse.egxanalyzer.data.ModelUsageStore
import com.ikverse.egxanalyzer.data.OverdueWorker
import com.ikverse.egxanalyzer.data.PriceRepository
import com.ikverse.egxanalyzer.data.PromptStore
import com.ikverse.egxanalyzer.data.OpinionPromptStore
import com.ikverse.egxanalyzer.data.RequestTrace
import com.ikverse.egxanalyzer.data.ScheduledJobWorker
import com.ikverse.egxanalyzer.data.SymbolMap
import com.ikverse.egxanalyzer.data.SettingsRepository
import com.ikverse.egxanalyzer.data.TelegramRepository
import com.ikverse.egxanalyzer.data.TradeStatusNotifier
import com.ikverse.egxanalyzer.data.UpdateRepository
import com.ikverse.egxanalyzer.data.refreshTodayWidget
import com.ikverse.egxanalyzer.state.LiveAppState
import com.ikverse.egxanalyzer.ui.AppState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class EgxApplication : Application() {

    /**
     * Whether the thing that started this process was the clock rather than its owner.
     *
     * Set by [ScheduledJobWorker] before it first touches [appState], which is what decides how
     * much of the app comes up with it - see `AppState.enterForeground`. A field on the
     * application rather than an argument because the state is built lazily by whoever asks for
     * it first, and the worker cannot pass anything to a getter.
     *
     * Read once, when the state is built. A process that starts headless and is then opened is
     * put right by the activity rather than by this flag.
     */
    @Volatile
    var startedForSchedule: Boolean = false

    /**
     * Where a widget redraw is dispatched from.
     *
     * Its own scope rather than `AppState`'s: redrawing the home screen is not part of rebuilding
     * the record, and a failure or a cancellation on one must not reach the other. Application-wide
     * and never cancelled, because the thing it outlives is a screen and not a process.
     */
    private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val appState: AppState by lazy {
        val credentialStore = AndroidKeystoreCredentialStore(this)
        val settingsRepository = SettingsRepository(this, credentialStore)
        // Not built here: constructing it starts TDLib, and a wake that only has prices to fetch
        // has no use for a Telegram session. AppState holds this behind a lazy and calls it the
        // first time anything actually needs Telegram.
        val telegramProvider = { TelegramRepository(this, credentialStore) }
        lateinit var state: AppState
        // One instance: the composer reads the same shipped text the repository falls back to, and
        // two readers of one asset that could disagree is a bug waiting for an app update.
        val promptStore = PromptStore(assets)
        // Its own store, reading its own asset. The Ask AI prompt shares nothing with the analysis
        // prompt - no rules, no schema, no version history - and two stores is what keeps it so.
        val opinionPromptStore = OpinionPromptStore(assets)
        // One tally for both callers: the repository writes every request into it, and AppState
        // reads it for the picker and for Settings.
        val modelUsageStore = ModelUsageStore(this)
        val analysisRepository = CloudAnalysisRepository(
            contentResolver = contentResolver,
            credentialStore = credentialStore,
            promptStore = promptStore,
            configuration = { state.cloudConfiguration },
            preferences = { state.appPreferences },
            traceFor = { requestId -> RequestTrace(this, requestId) },
            usageStore = modelUsageStore,
        )
        RequestTrace.prune(this)
        val localDataStore = LocalDataStore(this)
        val notifier = AnalysisNotifier(this)
        // One mapping for both feeds, so the daily series and the bars that order a session inside
        // it can never disagree about which Yahoo symbol a stock is.
        val symbols = SymbolMap(assets)
        // Built first so the daily repository can call it for a stock no daily feed carries. The
        // two stay separate types - different granularity, different retention, different table -
        // and are joined by one function rather than by either holding the other.
        val intraday = IntradayRepository(localDataStore, symbols)
        LiveAppState(
            context = this,
            settingsRepository = settingsRepository,
            analysisRepository = analysisRepository,
            localDataStore = localDataStore,
            telegramProvider = telegramProvider,
            priceRepository = PriceRepository(
                localDataStore,
                symbols,
                derivedHistory = intraday::dailyHistory,
            ),
            intradayRepository = intraday,
            promptStore = promptStore,
            opinionPromptStore = opinionPromptStore,
            // The app is sideloaded, so nothing else will ever offer it an update.
            updateRepository = UpdateRepository(this),
            // The foreground service is what keeps the process alive; the notification is what
            // tells the user so.
            // Wrapped, because from Android 12 an app in the background may not start a foreground
            // service at all. A scheduled run has already put its worker in the foreground before
            // reaching here, which makes this start legal - but if that was itself refused, the run
            // is still under way and paid for, and letting an exception about a notification throw
            // it away would be the worst possible trade.
            analysisRunning = { sources, model ->
                runCatching { AnalysisService.start(this, sources, model) }
            },
            analysisFinished = { resultId, recommendations ->
                AnalysisService.stop(this)
                if (resultId != null) notifier.finished(recommendations, resultId)
            },
            analysisStopped = { reason ->
                AnalysisService.stop(this)
                if (reason == null) notifier.cancelled() else notifier.failed(reason)
            },
            schedulesChanged = { schedules, marketRefresh, closeSweep ->
                JobScheduler(this).rebook(schedules, marketRefresh, closeSweep)
            },            dailyCheckChanged = { wanted ->
                if (wanted) OverdueWorker.schedule(this) else OverdueWorker.cancel(this)
            },
            // Wrapped for the reason the analysis announcements are: the trades have already been
            // re-scored and written down by the time this is called, and losing that to an
            // exception about a notification would trade the record for the announcement of it.
            tradesChanged = { changes ->
                runCatching { TradeStatusNotifier(this).announce(changes) }
            },
            // Wrapped for the same reason: the sweep has already been written down by the time
            // this is called, and losing that to an exception about a notification would trade the
            // record for the announcement of it.
            callsChanged = { changes ->
                runCatching { CallAlertNotifier(this).announce(changes) }
            },
            // Wrapped like the three above, and for the reason all of them are: the sweep has
            // already been written down by the time any of these is called, and losing that record
            // to an exception about a notification would trade the thing for the announcement of it.
            approachesChanged = { changes ->
                runCatching { ApproachNotifier(this).announce(changes) }
            },
            sessionSummarised = { digest ->
                runCatching { SessionDigestNotifier(this).announce(digest) }
            },
            feedQuiet = { stocks, callsHeld ->
                runCatching { AttentionNotifier(this).feedQuiet(stocks, callsHeld) }
            },
            scheduleMissed = { schedule ->
                runCatching { AttentionNotifier(this).scheduleMissed(schedule) }
            },
            // Already swallows its own failures - a launcher that refuses a shortcut is not
            // something the reader asked about - so it needs no wrapper here.
            // Two surfaces off one number: the launcher shortcut that counts overdue trades, and
            // the home-screen widget, which reads the count back rather than deriving it. Both
            // swallow their own failures - neither is something the reader asked about.
            overdueCounted = { count ->
                AppShortcuts.setOverdue(this, count)
                settingsRepository.recordOverdueCount(count)
                widgetScope.launch { refreshTodayWidget(this@EgxApplication) }
            },
            modelUsageStore = modelUsageStore,
            headless = startedForSchedule,
        ).also { state = it }
        // Booked on every launch rather than only when the switch is touched, so an install that
        // has never opened Settings still gets the check, and one whose work was dropped by the
        // system gets it back.
        if (state.appPreferences.overdueRemindersEnabled ||
            state.appPreferences.tradeAlertsEnabled
        ) {
            OverdueWorker.schedule(this)
        }
        // Re-booked on every launch, for the reason the overdue check is: an alarm does not
        // survive a reboot or an app update, and one the system dropped leaves a phone that has
        // silently stopped keeping time. The sweep answers a fire that came due while the phone
        // was off, which is what the grace windows are for.
        JobScheduler(this).rebook(
            state.analysisSchedules,
            state.marketRefreshEnabled,
            state.tradeWatchWanted,
        )
        if (state.analysisSchedules.any { it.enabled } ||
            state.marketRefreshEnabled ||
            state.tradeWatchWanted
        ) {
            ScheduledJobWorker.sweep(this)
        }
        state
    }
}
