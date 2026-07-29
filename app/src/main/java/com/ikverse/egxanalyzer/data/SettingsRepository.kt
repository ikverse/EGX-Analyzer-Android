package com.ikverse.egxanalyzer.data

import android.content.Context
import com.ikverse.egxanalyzer.model.AnalysisContentType
import com.ikverse.egxanalyzer.model.AnalysisLanguage
import com.ikverse.egxanalyzer.model.AppPreferences
import com.ikverse.egxanalyzer.model.CloudConfiguration
import com.ikverse.egxanalyzer.model.CloudProvider
import com.ikverse.egxanalyzer.model.ThemeMode
import com.ikverse.egxanalyzer.model.PromptSnapshot
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
            .apply()
    }

    fun loadPreferences(): AppPreferences = AppPreferences(
        themeMode = enumPreference(KEY_THEME_MODE, ThemeMode.SYSTEM),
        analysisLanguage = enumPreference(KEY_ANALYSIS_LANGUAGE, AnalysisLanguage.BILINGUAL),
        temperature = preferences.getFloat(KEY_TEMPERATURE, 0.1f).toDouble().coerceIn(0.0, 1.0),
        responseTimeoutSeconds = preferences.getInt(KEY_RESPONSE_TIMEOUT, 180).coerceIn(30, 300),
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
    )

    fun savePreferences(value: AppPreferences) {
        preferences.edit()
            .putString(KEY_THEME_MODE, value.themeMode.name)
            .putString(KEY_ANALYSIS_LANGUAGE, value.analysisLanguage.name)
            .putFloat(KEY_TEMPERATURE, value.temperature.coerceIn(0.0, 1.0).toFloat())
            .putInt(KEY_RESPONSE_TIMEOUT, value.responseTimeoutSeconds.coerceIn(30, 300))
            .putStringSet(
                KEY_DEFAULT_CONTENT_TYPES,
                value.defaultContentTypes.mapTo(mutableSetOf()) { it.name },
            )
            .putString(KEY_CUSTOM_PROMPT, value.customSystemPrompt)
            .putString(KEY_INCLUDE_PHRASES, value.includePhrases)
            .putString(KEY_EXCLUDE_PHRASES, value.excludePhrases)
            .putInt(KEY_CORRECTION_RETRIES, value.correctionRetries.coerceIn(0, 2))
            .putBoolean(KEY_CATALOG_ENRICHMENT, value.catalogEnrichmentEnabled)
            .apply()
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

    private inline fun <reified T : Enum<T>> enumPreference(key: String, fallback: T): T =
        preferences.getString(key, null)
            ?.let { stored -> enumValues<T>().firstOrNull { it.name == stored } }
            ?: fallback

    private fun CloudProvider.endpointKey() = "endpoint_${name.lowercase()}"
    private fun CloudProvider.modelKey() = "model_${name.lowercase()}"

    private companion object {
        const val KEY_PROVIDER = "provider"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_ANALYSIS_LANGUAGE = "analysis_language"
        const val KEY_TEMPERATURE = "temperature"
        const val KEY_RESPONSE_TIMEOUT = "response_timeout"
        const val KEY_DEFAULT_CONTENT_TYPES = "default_content_types"
        const val KEY_CUSTOM_PROMPT = "custom_system_prompt"
        const val KEY_INCLUDE_PHRASES = "analysis_include_phrases"
        const val KEY_EXCLUDE_PHRASES = "analysis_exclude_phrases"
        const val KEY_CORRECTION_RETRIES = "correction_retries"
        const val KEY_CATALOG_ENRICHMENT = "catalog_enrichment"
        const val KEY_PROMPT_HISTORY = "prompt_history"
    }
}
