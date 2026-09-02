package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.AnalysisContentType
import com.ikverse.egxanalyzer.model.AnalysisLanguage
import com.ikverse.egxanalyzer.model.AppPreferences
import com.ikverse.egxanalyzer.model.CloudProvider
import com.ikverse.egxanalyzer.model.PortfolioOrder
import com.ikverse.egxanalyzer.model.PromptSnapshot
import com.ikverse.egxanalyzer.model.ResponseTimeout
import com.ikverse.egxanalyzer.model.Scoring
import com.ikverse.egxanalyzer.model.ThemeMode
import org.json.JSONArray
import org.json.JSONObject

/** One provider's connection as it was configured, rather than as it ships. */
data class ProviderSettings(
    val provider: CloudProvider,
    val endpoint: String,
    val model: String,
    /**
     * What the provider last said it had.
     *
     * Carried because it is the only usable way to choose a model: without it a restored install
     * shows a text field and asks someone to type a model name from memory before it will run.
     */
    val models: List<String> = emptyList(),
)

/**
 * Everything Settings holds, as one revision that travels between devices.
 *
 * This is what makes a reinstall survivable. Reports, rules and trades already come back from the
 * channel; without this the device that reinstalls comes back configured like a device that has
 * never been opened - default scoring window, no provider, no model, none of the wording anyone
 * chose - and the record it downloads is scored against settings nobody picked.
 *
 * Two things are deliberately left out. The provider API key, because syncing it would put a live
 * cloud credential in a chat to save typing one field once. And the day prices were last fetched,
 * because it is a fact about a device's disk: carried to an install with no prices at all it says
 * they were fetched today, and the fresh install then waits until tomorrow to fetch any.
 */
data class SettingsSnapshot(
    val preferences: AppPreferences,
    /** The provider in use. Its connection is in [providers] alongside every other. */
    val provider: CloudProvider,
    val providers: List<ProviderSettings>,
    val useDefaultPromptOnly: Boolean,
    val promptHistory: List<PromptSnapshot>,
    val updatedAt: Long,
    val updatedBy: String,
    /**
     * Fields a newer app version wrote that this one does not understand.
     *
     * Kept and written back untouched, exactly as a rule or a trade carries them: an older device
     * reading these settings and saving them back must not strip a setting it has never heard of.
     */
    val unknown: String = "{}",
) {
    /** Names the device and the moment, so two devices changing settings offline are two files. */
    val fileName: String get() = "$PREFIX${deviceKey(updatedBy)}-$updatedAt$SUFFIX"

    /** What decides which revision wins, readable from the file name without downloading it. */
    val stamp: SettingsStamp get() = SettingsStamp(updatedAt, deviceKey(updatedBy))

    fun toDocument(): String {
        val json = runCatching { JSONObject(unknown) }.getOrDefault(JSONObject())
        return json
            .put("updatedAt", updatedAt)
            .put("updatedBy", updatedBy)
            .put("themeMode", preferences.themeMode.name)
            .put("analysisLanguage", preferences.analysisLanguage.name)
            .put("responseTimeoutSeconds", preferences.responseTimeoutSeconds)
            .put(
                "defaultContentTypes",
                JSONArray(preferences.defaultContentTypes.map { it.name }.sorted()),
            )
            .put("customSystemPrompt", preferences.customSystemPrompt)
            .put("includePhrases", preferences.includePhrases)
            .put("excludePhrases", preferences.excludePhrases)
            .put("correctionRetries", preferences.correctionRetries)
            .put("catalogEnrichmentEnabled", preferences.catalogEnrichmentEnabled)
            // The trade window, under the name it travelled as when it also decided scoring. A
            // renamed field reads as absent to every device on an older build, which would hand
            // them the default and sync it back as though the user had chosen it.
            .put("scoringWindowSessions", preferences.defaultTradeWindowSessions)
            .put("overdueRemindersEnabled", preferences.overdueRemindersEnabled)
            .put("tradeAlertsEnabled", preferences.tradeAlertsEnabled)
            .put("callAlertsEnabled", preferences.callAlertsEnabled)
            .put("approachAlertsEnabled", preferences.approachAlertsEnabled)
            .put("approachThresholdPercent", preferences.approachThresholdPercent)
            .put("sessionDigestEnabled", preferences.sessionDigestEnabled)
            .put("feedAlertsEnabled", preferences.feedAlertsEnabled)
            .put("scheduleAlertsEnabled", preferences.scheduleAlertsEnabled)
            .put("portfolioOrder", preferences.portfolioOrder.name)
            .put("callOrder", preferences.callOrder.name)
            .put("updateChecksEnabled", preferences.updateChecksEnabled)
            .put("useDefaultPromptOnly", useDefaultPromptOnly)
            .put("provider", provider.name)
            .put(
                "providers",
                JSONArray().apply {
                    providers.forEach { entry ->
                        put(
                            JSONObject()
                                .put("provider", entry.provider.name)
                                .put("endpoint", entry.endpoint)
                                .put("model", entry.model)
                                .put("models", JSONArray(entry.models)),
                        )
                    }
                },
            )
            .put(
                "promptHistory",
                JSONArray().apply {
                    promptHistory.forEach { snapshot ->
                        put(
                            JSONObject()
                                .put("systemPrompt", snapshot.systemPrompt)
                                .put("includePhrases", snapshot.includePhrases)
                                .put("excludePhrases", snapshot.excludePhrases)
                                .put("savedAt", snapshot.savedAtEpochMilliseconds),
                        )
                    }
                },
            )
            .toString()
    }

    companion object {
        /**
         * Reads settings written by any version, or null when the document is not settings at all.
         *
         * Every field falls back to what the app would have chosen on its own. A setting this build
         * does not recognise - an option renamed or dropped since - must cost that one setting and
         * nothing else: refusing the whole document over one word would leave a reinstalled phone
         * with no settings at all, which is the failure this is here to prevent.
         */
        fun fromDocument(text: String): SettingsSnapshot? = runCatching {
            val json = JSONObject(text)
            if (!json.has("updatedAt") || !json.has("themeMode")) return null
            val defaults = AppPreferences()
            SettingsSnapshot(
                preferences = AppPreferences(
                    themeMode = json.enumOr("themeMode", defaults.themeMode),
                    analysisLanguage = json.enumOr("analysisLanguage", defaults.analysisLanguage),
                    responseTimeoutSeconds = json
                        .optInt("responseTimeoutSeconds", defaults.responseTimeoutSeconds)
                        .coerceIn(ResponseTimeout.MIN, ResponseTimeout.MAX),
                    defaultContentTypes = json.optJSONArray("defaultContentTypes")
                        ?.let { array ->
                            (0 until array.length())
                                .map(array::getString)
                                .mapNotNullTo(mutableSetOf()) { stored ->
                                    AnalysisContentType.entries.firstOrNull { it.name == stored }
                                }
                        }
                        ?.ifEmpty { null }
                        ?: defaults.defaultContentTypes,
                    customSystemPrompt = json.optString("customSystemPrompt"),
                    includePhrases = json.optString("includePhrases"),
                    excludePhrases = json.optString("excludePhrases"),
                    correctionRetries = json.optInt("correctionRetries", defaults.correctionRetries)
                        .coerceIn(0, 2),
                    catalogEnrichmentEnabled = json.optBoolean(
                        "catalogEnrichmentEnabled",
                        defaults.catalogEnrichmentEnabled,
                    ),
                    defaultTradeWindowSessions = Scoring.clampWindow(
                        json.optInt("scoringWindowSessions", defaults.defaultTradeWindowSessions),
                    ),
                    overdueRemindersEnabled = json.optBoolean(
                        "overdueRemindersEnabled",
                        defaults.overdueRemindersEnabled,
                    ),
                    tradeAlertsEnabled = json.optBoolean(
                        "tradeAlertsEnabled",
                        defaults.tradeAlertsEnabled,
                    ),
                    callAlertsEnabled = json.optBoolean(
                        "callAlertsEnabled",
                        defaults.callAlertsEnabled,
                    ),
                    approachAlertsEnabled = json.optBoolean(
                        "approachAlertsEnabled",
                        defaults.approachAlertsEnabled,
                    ),
                    approachThresholdPercent = json.optInt(
                        "approachThresholdPercent",
                        defaults.approachThresholdPercent,
                    ),
                    sessionDigestEnabled = json.optBoolean(
                        "sessionDigestEnabled",
                        defaults.sessionDigestEnabled,
                    ),
                    feedAlertsEnabled = json.optBoolean(
                        "feedAlertsEnabled",
                        defaults.feedAlertsEnabled,
                    ),
                    scheduleAlertsEnabled = json.optBoolean(
                        "scheduleAlertsEnabled",
                        defaults.scheduleAlertsEnabled,
                    ),
                    portfolioOrder = json.enumOr("portfolioOrder", defaults.portfolioOrder),
                    callOrder = json.enumOr("callOrder", defaults.callOrder),
                    updateChecksEnabled = json.optBoolean(
                        "updateChecksEnabled",
                        defaults.updateChecksEnabled,
                    ),
                ),
                provider = json.enumOr("provider", CloudProvider.QWEN),
                providers = json.optJSONArray("providers")
                    ?.let { array -> (0 until array.length()).mapNotNull(array::optJSONObject) }
                    ?.mapNotNull { entry ->
                        val provider = CloudProvider.entries
                            .firstOrNull { it.name == entry.optString("provider") }
                            ?: return@mapNotNull null
                        ProviderSettings(
                            provider = provider,
                            endpoint = entry.optString("endpoint")
                                .ifBlank { provider.defaultEndpoint },
                            model = entry.optString("model").ifBlank { provider.defaultModel },
                            models = entry.optJSONArray("models")
                                ?.let { list -> (0 until list.length()).map(list::getString) }
                                .orEmpty(),
                        )
                    }
                    .orEmpty(),
                useDefaultPromptOnly = json.optBoolean("useDefaultPromptOnly", false),
                promptHistory = json.optJSONArray("promptHistory")
                    ?.let { array -> (0 until array.length()).mapNotNull(array::optJSONObject) }
                    ?.map { entry ->
                        PromptSnapshot(
                            systemPrompt = entry.optString("systemPrompt"),
                            includePhrases = entry.optString("includePhrases"),
                            excludePhrases = entry.optString("excludePhrases"),
                            savedAtEpochMilliseconds = entry.optLong("savedAt"),
                        )
                    }
                    .orEmpty(),
                updatedAt = json.optLong("updatedAt"),
                updatedBy = json.optString("updatedBy"),
                unknown = JSONObject().apply {
                    json.keys().forEach { key -> if (key !in KNOWN) put(key, json.get(key)) }
                }.toString(),
            )
        }.getOrNull()

        /**
         * What a settings file name says about itself, or null when the name is not one of ours.
         *
         * The name carries both facts the merge needs, which is why a device can decide which
         * revision to read without downloading every revision anyone ever wrote.
         */
        fun stampOf(fileName: String): SettingsStamp? {
            val body = fileName
                .takeIf { it.startsWith(PREFIX) && it.endsWith(SUFFIX) }
                ?.removePrefix(PREFIX)
                ?.removeSuffix(SUFFIX)
                ?: return null
            val updatedAt = body.substringAfterLast('-').toLongOrNull() ?: return null
            val device = body.substringBeforeLast('-')
            return SettingsStamp(updatedAt, device)
        }

        /** A device name in the form a file name can carry it. Both sides compare this form. */
        fun deviceKey(device: String): String = device.replace(Regex("[^A-Za-z0-9._]"), "_")

        private val KNOWN = setOf(
            "updatedAt", "updatedBy", "themeMode", "analysisLanguage", "responseTimeoutSeconds",
            "defaultContentTypes", "customSystemPrompt", "includePhrases", "excludePhrases",
            "correctionRetries", "catalogEnrichmentEnabled", "scoringWindowSessions",
            "overdueRemindersEnabled", "tradeAlertsEnabled", "callAlertsEnabled",
            "approachAlertsEnabled", "approachThresholdPercent", "sessionDigestEnabled",
            "feedAlertsEnabled", "scheduleAlertsEnabled",
            "portfolioOrder", "callOrder",
            "updateChecksEnabled",
            "useDefaultPromptOnly", "provider", "providers", "promptHistory",
        )

        private const val PREFIX = "settings-"
        private const val SUFFIX = ".json"

        private inline fun <reified T : Enum<T>> JSONObject.enumOr(key: String, fallback: T): T =
            optString(key).takeIf(String::isNotBlank)
                ?.let { stored -> enumValues<T>().firstOrNull { it.name == stored } }
                ?: fallback
    }
}

/**
 * When settings were last changed and by which device.
 *
 * The device breaks a tie so that two phones whose settings changed in the same millisecond still
 * reach the same answer without talking to each other. It is the sanitized name rather than the
 * typed one, because that is the form the file name carries and the decision is made from names.
 */
data class SettingsStamp(val updatedAt: Long, val device: String) : Comparable<SettingsStamp> {
    override fun compareTo(other: SettingsStamp): Int =
        compareValuesBy(this, other, SettingsStamp::updatedAt, SettingsStamp::device)
}

/**
 * Which revision of the settings counts.
 *
 * Last writer wins, as with rules and trades. A device that has never saved a setting has nothing
 * to defend - its stamp is zero - so a fresh install always takes what the channel holds, which is
 * the whole point of publishing them.
 */
fun mergeSettings(revisions: List<SettingsSnapshot>): SettingsSnapshot? =
    revisions.maxWithOrNull(compareBy(SettingsSnapshot::stamp))

/** Whether this device knows the settings better than the channel does. */
fun settingsWorthUploading(mine: SettingsSnapshot, theirs: SettingsSnapshot?): Boolean =
    mine.updatedAt > 0 && (theirs == null || mine.stamp > theirs.stamp)
