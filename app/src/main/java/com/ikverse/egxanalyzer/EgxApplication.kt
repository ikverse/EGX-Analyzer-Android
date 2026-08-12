package com.ikverse.egxanalyzer

import android.app.Application
import com.ikverse.egxanalyzer.data.AnalysisNotifier
import com.ikverse.egxanalyzer.data.AnalysisService
import com.ikverse.egxanalyzer.data.AndroidKeystoreCredentialStore
import com.ikverse.egxanalyzer.data.CloudAnalysisRepository
import com.ikverse.egxanalyzer.data.LocalDataStore
import com.ikverse.egxanalyzer.data.OverdueWorker
import com.ikverse.egxanalyzer.data.PriceRepository
import com.ikverse.egxanalyzer.data.PromptStore
import com.ikverse.egxanalyzer.data.RequestTrace
import com.ikverse.egxanalyzer.data.SymbolMap
import com.ikverse.egxanalyzer.data.SettingsRepository
import com.ikverse.egxanalyzer.data.TelegramRepository
import com.ikverse.egxanalyzer.data.UpdateRepository
import com.ikverse.egxanalyzer.ui.AppState

class EgxApplication : Application() {
    val appState: AppState by lazy {
        val credentialStore = AndroidKeystoreCredentialStore(this)
        val settingsRepository = SettingsRepository(this, credentialStore)
        val telegramRepository = TelegramRepository(this, credentialStore)
        lateinit var state: AppState
        // One instance: the composer reads the same shipped text the repository falls back to, and
        // two readers of one asset that could disagree is a bug waiting for an app update.
        val promptStore = PromptStore(assets)
        val analysisRepository = CloudAnalysisRepository(
            contentResolver = contentResolver,
            credentialStore = credentialStore,
            promptStore = promptStore,
            configuration = { state.cloudConfiguration },
            preferences = { state.appPreferences },
            traceFor = { requestId -> RequestTrace(this, requestId) },
        )
        RequestTrace.prune(this)
        val localDataStore = LocalDataStore(this)
        val notifier = AnalysisNotifier(this)
        AppState(
            settingsRepository = settingsRepository,
            analysisRepository = analysisRepository,
            localDataStore = localDataStore,
            telegramRepository = telegramRepository,
            priceRepository = PriceRepository(localDataStore, SymbolMap(assets)),
            promptStore = promptStore,
            // The app is sideloaded, so nothing else will ever offer it an update.
            updateRepository = UpdateRepository(this),
            // The foreground service is what keeps the process alive; the notification is what
            // tells the user so.
            analysisRunning = { sources, model -> AnalysisService.start(this, sources, model) },
            analysisFinished = { resultId, recommendations ->
                AnalysisService.stop(this)
                if (resultId != null) notifier.finished(recommendations, resultId)
            },
            analysisStopped = { reason ->
                AnalysisService.stop(this)
                if (reason == null) notifier.cancelled() else notifier.failed(reason)
            },
            overdueRemindersChanged = { enabled ->
                if (enabled) OverdueWorker.schedule(this) else OverdueWorker.cancel(this)
            },
        ).also { state = it }
        // Booked on every launch rather than only when the switch is touched, so an install that
        // has never opened Settings still gets the check, and one whose work was dropped by the
        // system gets it back.
        if (state.appPreferences.overdueRemindersEnabled) OverdueWorker.schedule(this)
        state
    }
}
