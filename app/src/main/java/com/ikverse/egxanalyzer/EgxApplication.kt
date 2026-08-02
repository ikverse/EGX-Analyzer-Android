package com.ikverse.egxanalyzer

import android.app.Application
import com.ikverse.egxanalyzer.data.AnalysisNotifier
import com.ikverse.egxanalyzer.data.AnalysisService
import com.ikverse.egxanalyzer.data.AndroidKeystoreCredentialStore
import com.ikverse.egxanalyzer.data.CloudAnalysisRepository
import com.ikverse.egxanalyzer.data.LocalDataStore
import com.ikverse.egxanalyzer.data.PriceRepository
import com.ikverse.egxanalyzer.data.PromptStore
import com.ikverse.egxanalyzer.data.RequestTrace
import com.ikverse.egxanalyzer.data.SymbolMap
import com.ikverse.egxanalyzer.data.SettingsRepository
import com.ikverse.egxanalyzer.data.TelegramRepository
import com.ikverse.egxanalyzer.ui.AppState

class EgxApplication : Application() {
    val appState: AppState by lazy {
        val credentialStore = AndroidKeystoreCredentialStore(this)
        val settingsRepository = SettingsRepository(this, credentialStore)
        val telegramRepository = TelegramRepository(this, credentialStore)
        lateinit var state: AppState
        val analysisRepository = CloudAnalysisRepository(
            contentResolver = contentResolver,
            credentialStore = credentialStore,
            promptStore = PromptStore(assets),
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
        ).also { state = it }
    }
}
