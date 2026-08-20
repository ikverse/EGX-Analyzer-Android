package com.ikverse.egxanalyzer.data

import android.content.Context
import com.ikverse.egxanalyzer.model.AnalysisContentType
import com.ikverse.egxanalyzer.model.AnalysisLanguage
import com.ikverse.egxanalyzer.model.AppPreferences
import com.ikverse.egxanalyzer.model.ResponseTimeout
import com.ikverse.egxanalyzer.model.CloudConfiguration
import com.ikverse.egxanalyzer.model.CloudProvider
import com.ikverse.egxanalyzer.model.PortfolioOrder
import com.ikverse.egxanalyzer.model.ThemeMode
import com.ikverse.egxanalyzer.model.PromptSnapshot
import com.ikverse.egxanalyzer.model.Scoring
import org.json.JSONArray
import org.json.JSONObject

class SettingsRepository(
    context: Context,
    private val credentialStore: CredentialStore,
) {
    private val preferences = context.getSharedPreferences("egx_android_settings", Context.MODE_PRIVATE)

    fun load(): CloudConfiguration {
        val provider = preferences.getString(KEY_PROVIDER, null)
            ?.let { stored -> CloudProvider.entries.firstOrNull { it.name == stored } }
            ?: CloudProvider.QWEN
        return CloudConfiguration(
            provider = provider,
            endpoint = preferences.getString(provider.endpointKey(), provider.defaultEndpoint)
                ?: provider.defaultEndpoint,
            model = preferences.getString(provider.modelKey(), provider.defaultModel)
                ?: provider.defaultModel,
            hasCredential = credentialStore.contains(provider),
        )
    }

    fun configurationFor(provider: CloudProvider): CloudConfiguration = CloudConfiguration(
        provider = provider,
        endpoint = preferences.getString(provider.endpointKey(), provider.defaultEndpoint)
            ?: provider.defaultEndpoint,
        model = preferences.getString(provider.modelKey(), provider.defaultModel)
            ?: provider.defaultModel,
        hasCredential = credentialStore.contains(provider),
    )

    fun save(configuration: CloudConfiguration, credential: CharArray?) {
        preferences.edit()
            .putString(KEY_PROVIDER, configuration.provider.name)
            .putString(configuration.provider.endpointKey(), configuration.endpoint.trim())
            .putString(configuration.provider.modelKey(), configuration.model.trim())
            .apply()
        if (credential != null && credential.isNotEmpty()) {
            credentialStore.save(configuration.provider, credential)
        }
    }

    fun removeCredential(provider: CloudProvider) = credentialStore.remove(provider)

    fun resetProviderConfiguration(provider: CloudProvider) {
        preferences.edit()
            .remove(provider.endpointKey())
            .remove(provider.modelKey())
            .remove(provider.modelListKey())
            .apply()
    }

    /**
     * The models a provider last said it had.
     *
     * Kept on disk because the list is the only usable way to choose one: held in memory alone it
     * emptied on every launch, and the card was back to asking the user to type a model name from
     * memory before it would let a run start.
     */
    fun modelList(provider: CloudProvider): List<String> {
        val raw = preferences.getString(provider.modelListKey(), null) ?: return emptyList()
        return runCatching {
            val values = JSONArray(raw)
            buildList { for (index in 0 until values.length()) add(values.getString(index)) }
        }.getOrDefault(emptyList())
    }

    fun saveModelList(provider: CloudProvider, models: List<String>) {
        preferences.edit()
            .putString(provider.modelListKey(), JSONArray().apply { models.forEach(::put) }.toString())
            .apply()
    }

    /** Drops a cached list that a changed endpoint or a removed key can no longer vouch for. */
    fun clearModelList(provider: CloudProvider) {
        preferences.edit().remove(provider.modelListKey()).apply()
    }

    fun loadPreferences(): AppPreferences = AppPreferences(
        themeMode = enumPreference(KEY_THEME_MODE, ThemeMode.SYSTEM),
        analysisLanguage = enumPreference(KEY_ANALYSIS_LANGUAGE, AnalysisLanguage.BILINGUAL),
        responseTimeoutSeconds = preferences.getInt(KEY_RESPONSE_TIMEOUT, ResponseTimeout.DEFAULT)
            .coerceIn(ResponseTimeout.MIN, ResponseTimeout.MAX),
        defaultContentTypes = preferences.getStringSet(
            KEY_DEFAULT_CONTENT_TYPES,
            AnalysisContentType.entries.mapTo(mutableSetOf()) { it.name },
        ).orEmpty().mapNotNullTo(mutableSetOf()) { stored ->
            AnalysisContentType.entries.firstOrNull { it.name == stored }
        }.ifEmpty { AnalysisContentType.entries.toSet() },
        customSystemPrompt = preferences.getString(KEY_CUSTOM_PROMPT, "").orEmpty(),
        includePhrases = preferences.getString(KEY_INCLUDE_PHRASES, "").orEmpty(),
        excludePhrases = preferences.getString(KEY_EXCLUDE_PHRASES, "").orEmpty(),
        correctionRetries = preferences.getInt(KEY_CORRECTION_RETRIES, 1).coerceIn(0, 2),
        catalogEnrichmentEnabled = preferences.getBoolean(KEY_CATALOG_ENRICHMENT, true),
        scoringWindowSessions = Scoring.clampWindow(
            preferences.getInt(KEY_SCORING_WINDOW, Scoring.DEFAULT_WINDOW_SESSIONS),
        ),
        overdueRemindersEnabled = preferences.getBoolean(KEY_OVERDUE_REMINDERS, true),
        updateChecksEnabled = preferences.getBoolean(KEY_UPDATE_CHECKS, true),
        portfolioOrder = enumPreference(KEY_PORTFOLIO_ORDER, PortfolioOrder.URGENT),
    )

    fun savePreferences(value: AppPreferences) {
        preferences.edit()
            .putString(KEY_THEME_MODE, value.themeMode.name)
            .putString(KEY_ANALYSIS_LANGUAGE, value.analysisLanguage.name)
            .putInt(
                KEY_RESPONSE_TIMEOUT,
                value.responseTimeoutSeconds.coerceIn(ResponseTimeout.MIN, ResponseTimeout.MAX),
            )
            .putStringSet(
                KEY_DEFAULT_CONTENT_TYPES,
                value.defaultContentTypes.mapTo(mutableSetOf()) { it.name },
            )
            .putString(KEY_CUSTOM_PROMPT, value.customSystemPrompt)
            .putString(KEY_INCLUDE_PHRASES, value.includePhrases)
            .putString(KEY_EXCLUDE_PHRASES, value.excludePhrases)
            .putInt(KEY_CORRECTION_RETRIES, value.correctionRetries.coerceIn(0, 2))
            .putBoolean(KEY_CATALOG_ENRICHMENT, value.catalogEnrichmentEnabled)
            .putInt(KEY_SCORING_WINDOW, Scoring.clampWindow(value.scoringWindowSessions))
            .putBoolean(KEY_OVERDUE_REMINDERS, value.overdueRemindersEnabled)
            .putBoolean(KEY_UPDATE_CHECKS, value.updateChecksEnabled)
            // By name rather than by ordinal: reordering the options or dropping one would otherwise
            // silently reinterpret what every existing install had chosen.
            .putString(KEY_PORTFOLIO_ORDER, value.portfolioOrder.name)
            .apply()
    }

    /**
     * The day prices were last fetched.
     *
     * A closed session's prices never change, so one fetch a day is all the feed can usefully give.
     */
    fun lastPriceRefreshDay(): String? = preferences.getString(KEY_LAST_PRICE_REFRESH, null)

    fun recordPriceRefreshDay(day: String) {
        preferences.edit()
            .putString(KEY_LAST_PRICE_REFRESH, day)
            .putLong(KEY_LAST_PRICE_REFRESH_AT, System.currentTimeMillis())
            .apply()
    }

    /**
     * The moment prices were last fetched, beside the day they were fetched on.
     *
     * The day answers "has today been done"; a scheduled job needs "has this been done since the
     * fire I am answering", which a date cannot say. A job booked for after the close would
     * otherwise be talked out of running by a refresh that happened at breakfast.
     */
    fun lastPriceRefreshAt(): Long = preferences.getLong(KEY_LAST_PRICE_REFRESH_AT, 0L)

    /**
     * Whether this phone runs its schedules.
     *
     * Deliberately not in [AppPreferences]: everything in there is published to the other devices,
     * and a schedule that travelled would have every phone doing the same work. This is one
     * device's own answer, kept beside the settings rather than among them.
     */
    fun schedulesEnabled(): Boolean = preferences.getBoolean(KEY_SCHEDULES_ENABLED, false)

    fun saveSchedulesEnabled(value: Boolean) {
        preferences.edit().putBoolean(KEY_SCHEDULES_ENABLED, value).apply()
    }

    /**
     * Whether a schedule on this phone may start work that spends cloud credits.
     *
     * A second switch behind [schedulesEnabled] rather than a property of the job, and off until it
     * is turned on. Free work proves the alarms, the reboots and whatever the phone's battery
     * manager does to a sleeping app; only once that is believable is it reasonable to let the same
     * machinery send a paid request while nobody is watching. Device-local for the same reason
     * every other schedule setting is - see [schedulesEnabled].
     */
    fun paidSchedulesEnabled(): Boolean = preferences.getBoolean(KEY_PAID_SCHEDULES, false)

    fun savePaidSchedulesEnabled(value: Boolean) {
        preferences.edit().putBoolean(KEY_PAID_SCHEDULES, value).apply()
    }

    fun useDefaultPromptOnly(): Boolean = preferences.getBoolean(KEY_DEFAULT_PROMPT_ONLY, false)

    fun saveUseDefaultPromptOnly(value: Boolean) {
        preferences.edit().putBoolean(KEY_DEFAULT_PROMPT_ONLY, value).apply()
    }

    fun promptHistory(): List<PromptSnapshot> {
        val raw = preferences.getString(KEY_PROMPT_HISTORY, "[]").orEmpty()
        return runCatching {
            val values = JSONArray(raw)
            buildList {
                for (index in 0 until values.length()) {
                    val item = values.getJSONObject(index)
                    add(
                        PromptSnapshot(
                            systemPrompt = item.optString("systemPrompt"),
                            includePhrases = item.optString("includePhrases"),
                            excludePhrases = item.optString("excludePhrases"),
                            savedAtEpochMilliseconds = item.optLong("savedAt"),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun savePromptSnapshot(value: AppPreferences) {
        val updated = listOf(
            PromptSnapshot(
                systemPrompt = value.customSystemPrompt,
                includePhrases = value.includePhrases,
                excludePhrases = value.excludePhrases,
                savedAtEpochMilliseconds = System.currentTimeMillis(),
            ),
        ) + promptHistory().take(9)
        val json = JSONArray().apply {
            updated.forEach { snapshot ->
                put(
                    JSONObject()
                        .put("systemPrompt", snapshot.systemPrompt)
                        .put("includePhrases", snapshot.includePhrases)
                        .put("excludePhrases", snapshot.excludePhrases)
                        .put("savedAt", snapshot.savedAtEpochMilliseconds),
                )
            }
        }
        preferences.edit().putString(KEY_PROMPT_HISTORY, json.toString()).apply()
    }

    /**
     * Everything Settings holds, as one revision that can travel.
     *
     * The credential is not in it, and neither is the day prices were last fetched - see
     * [SettingsSnapshot]. Every provider is carried rather than only the one in use, because
     * switching back to a provider configured months ago has to find it configured.
     */
    fun snapshot(): SettingsSnapshot = SettingsSnapshot(
        preferences = loadPreferences(),
        provider = load().provider,
        providers = CloudProvider.entries.map { provider ->
            val configuration = configurationFor(provider)
            ProviderSettings(
                provider = provider,
                endpoint = configuration.endpoint,
                model = configuration.model,
                models = modelList(provider),
            )
        },
        useDefaultPromptOnly = useDefaultPromptOnly(),
        promptHistory = promptHistory(),
        updatedAt = preferences.getLong(KEY_SETTINGS_UPDATED_AT, 0L),
        updatedBy = preferences.getString(KEY_SETTINGS_UPDATED_BY, "").orEmpty(),
        unknown = preferences.getString(KEY_SETTINGS_UNKNOWN, "{}").orEmpty(),
    )

    /**
     * Records that this device changed a setting, which is what a later merge compares.
     *
     * An install that has never touched a setting keeps a stamp of zero and so defends nothing: it
     * takes whatever the channel holds, which is exactly what a reinstalled phone should do.
     */
    fun recordSettingsChange(device: String, at: Long = System.currentTimeMillis()) {
        preferences.edit()
            .putLong(KEY_SETTINGS_UPDATED_AT, at)
            .putString(KEY_SETTINGS_UPDATED_BY, device)
            .apply()
    }

    /**
     * Claims the settings an install is already holding, once.
     *
     * Settings began travelling after they had been configured, so every device that predates the
     * change holds a full set with no stamp on it - and an unstamped set looks exactly like the
     * empty one a reinstall starts with, so it would never be published and never reach anywhere.
     * Having written a setting is what tells the two apart: a fresh install has written none.
     */
    fun claimSettingsIfUnstamped(device: String) {
        if (preferences.getLong(KEY_SETTINGS_UPDATED_AT, 0L) > 0L) return
        if (preferences.all.isEmpty()) return
        recordSettingsChange(device)
    }

    /**
     * Takes settings that were written on another device.
     *
     * The author's stamp is written verbatim rather than refreshed. Stamping the moment they
     * arrived would make this device claim to be the one that changed them, and the two phones
     * would then hand the same settings back and forth forever, each believing it knew better.
     */
    fun adopt(snapshot: SettingsSnapshot) {
        savePreferences(snapshot.preferences)
        saveUseDefaultPromptOnly(snapshot.useDefaultPromptOnly)
        replacePromptHistory(snapshot.promptHistory)
        snapshot.providers.forEach { entry ->
            preferences.edit()
                .putString(entry.provider.endpointKey(), entry.endpoint)
                .putString(entry.provider.modelKey(), entry.model)
                .apply()
            saveModelList(entry.provider, entry.models)
        }
        preferences.edit()
            .putString(KEY_PROVIDER, snapshot.provider.name)
            .putLong(KEY_SETTINGS_UPDATED_AT, snapshot.updatedAt)
            .putString(KEY_SETTINGS_UPDATED_BY, snapshot.updatedBy)
            // Held on disk rather than only for the length of one sync: a field a newer build added
            // has to survive this device saving its own settings over the top of it.
            .putString(KEY_SETTINGS_UNKNOWN, snapshot.unknown)
            .apply()
    }

    private fun replacePromptHistory(history: List<PromptSnapshot>) {
        val json = JSONArray().apply {
            history.forEach { snapshot ->
                put(
                    JSONObject()
                        .put("systemPrompt", snapshot.systemPrompt)
                        .put("includePhrases", snapshot.includePhrases)
                        .put("excludePhrases", snapshot.excludePhrases)
                        .put("savedAt", snapshot.savedAtEpochMilliseconds),
                )
            }
        }
        preferences.edit().putString(KEY_PROMPT_HISTORY, json.toString()).apply()
    }

    private inline fun <reified T : Enum<T>> enumPreference(key: String, fallback: T): T =
        preferences.getString(key, null)
            ?.let { stored -> enumValues<T>().firstOrNull { it.name == stored } }
            ?: fallback

    private fun CloudProvider.endpointKey() = "endpoint_${name.lowercase()}"
    private fun CloudProvider.modelKey() = "model_${name.lowercase()}"
    private fun CloudProvider.modelListKey() = "model_list_${name.lowercase()}"

    private companion object {
        const val KEY_PROVIDER = "provider"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_ANALYSIS_LANGUAGE = "analysis_language"
        const val KEY_RESPONSE_TIMEOUT = "response_timeout"
        const val KEY_DEFAULT_CONTENT_TYPES = "default_content_types"
        const val KEY_CUSTOM_PROMPT = "custom_system_prompt"
        const val KEY_DEFAULT_PROMPT_ONLY = "use_default_prompt_only"
        const val KEY_INCLUDE_PHRASES = "analysis_include_phrases"
        const val KEY_EXCLUDE_PHRASES = "analysis_exclude_phrases"
        const val KEY_CORRECTION_RETRIES = "correction_retries"
        const val KEY_CATALOG_ENRICHMENT = "catalog_enrichment"
        const val KEY_SCORING_WINDOW = "scoring_window_sessions"
        const val KEY_OVERDUE_REMINDERS = "overdue_reminders_enabled"
        const val KEY_PORTFOLIO_ORDER = "portfolio_order"
        const val KEY_UPDATE_CHECKS = "update_checks_enabled"
        const val KEY_LAST_PRICE_REFRESH = "last_price_refresh_day"
        const val KEY_LAST_PRICE_REFRESH_AT = "last_price_refresh_at"
        const val KEY_SCHEDULES_ENABLED = "schedules_enabled"
        const val KEY_PAID_SCHEDULES = "paid_schedules_enabled"
        const val KEY_PROMPT_HISTORY = "prompt_history"
        const val KEY_SETTINGS_UPDATED_AT = "settings_updated_at"
        const val KEY_SETTINGS_UPDATED_BY = "settings_updated_by"
        const val KEY_SETTINGS_UNKNOWN = "settings_unknown"
    }
}
