package com.ikverse.egxanalyzer

import android.app.Application
import com.ikverse.egxanalyzer.data.AndroidKeystoreCredentialStore
import com.ikverse.egxanalyzer.data.CloudAnalysisRepository
import com.ikverse.egxanalyzer.data.LocalDataStore
import com.ikverse.egxanalyzer.data.PriceRepository
import com.ikverse.egxanalyzer.data.PromptStore
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
        )
        val localDataStore = LocalDataStore(this)
        AppState(
            settingsRepository = settingsRepository,
            analysisRepository = analysisRepository,
            localDataStore = localDataStore,
            telegramRepository = telegramRepository,
            priceRepository = PriceRepository(localDataStore, SymbolMap(assets)),
        ).also { state = it }
    }
}
