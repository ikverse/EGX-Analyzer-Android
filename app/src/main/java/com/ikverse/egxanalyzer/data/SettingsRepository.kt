package com.ikverse.egxanalyzer.data

import android.content.Context
import com.ikverse.egxanalyzer.model.AnalysedChannel
import com.ikverse.egxanalyzer.model.AnalysisContentType
import com.ikverse.egxanalyzer.model.AnalysisSchedule
import com.ikverse.egxanalyzer.model.JobOutcome
import com.ikverse.egxanalyzer.model.MarketRefresh
import com.ikverse.egxanalyzer.model.AnalysisLanguage
import com.ikverse.egxanalyzer.model.ApproachAlerts
import com.ikverse.egxanalyzer.model.AppPreferences
import com.ikverse.egxanalyzer.model.CallOrder
import com.ikverse.egxanalyzer.model.ResponseTimeout
import com.ikverse.egxanalyzer.model.CloudConfiguration
import com.ikverse.egxanalyzer.model.CloudModelInfo
import com.ikverse.egxanalyzer.model.CloudProvider
import com.ikverse.egxanalyzer.model.ModelModality
import com.ikverse.egxanalyzer.model.PortfolioOrder
import com.ikverse.egxanalyzer.model.ThemeMode
import com.ikverse.egxanalyzer.model.PromptSnapshot
import com.ikverse.egxanalyzer.model.Scoring
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime

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
            .remove(provider.modelCatalogKey())
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

    /**
     * The same list, with what the provider said about each model.
     *
     * Held beside the ids rather than replacing them: the id list is what travels between devices,
     * and widening what [SettingsSnapshot] carries would make an older build drop a list it cannot
     * read. A device that has only ever been synced therefore has ids and no modalities, which is
     * what the fallback below produces - the picker then reads the names, as it does for every
     * provider but OpenRouter anyway.
     */
    fun modelCatalog(provider: CloudProvider): List<CloudModelInfo> {
        val raw = preferences.getString(provider.modelCatalogKey(), null)
            ?: return modelList(provider).map(::CloudModelInfo)
        return runCatching {
            val values = JSONArray(raw)
            buildList {
                for (index in 0 until values.length()) {
                    val entry = values.getJSONObject(index)
                    val id = entry.optString("id")
                    if (id.isBlank()) continue
                    val modalities = entry.optJSONArray("modalities")
                    add(
                        CloudModelInfo(
                            id = id,
                            statedModalities = buildSet {
                                for (m in 0 until (modalities?.length() ?: 0)) {
                                    ModelModality.from(modalities!!.optString(m))?.let(::add)
                                }
                            },
                            contextLength = entry.optInt("context").takeIf { it > 0 },
                        ),
                    )
                }
            }
        }.getOrElse { modelList(provider).map(::CloudModelInfo) }
    }

    fun saveModelCatalog(provider: CloudProvider, models: List<CloudModelInfo>) {
        val json = JSONArray().apply {
            models.forEach { model ->
                put(
                    JSONObject().apply {
                        put("id", model.id)
                        put(
                            "modalities",
                            JSONArray().apply { model.statedModalities.forEach { put(it.name) } },
                        )
                        model.contextLength?.let { put("context", it) }
                    },
                )
            }
        }
        preferences.edit().putString(provider.modelCatalogKey(), json.toString()).apply()
        saveModelList(provider, models.map(CloudModelInfo::id))
    }

    /** Drops a cached list that a changed endpoint or a removed key can no longer vouch for. */
    fun clearModelList(provider: CloudProvider) {
        preferences.edit()
            .remove(provider.modelListKey())
            .remove(provider.modelCatalogKey())
            .apply()
    }

    /**
     * What Ask AI runs on, per provider.
     *
     * Its own setting because the analysis model is chosen for something this request does not do:
     * the run reads screenshots and needs vision, an opinion is text in and text out, and paying
     * vision rates for it is money for nothing. Falls back to [OPINION_MODEL_DEFAULT] and then to
     * whatever the analysis is set to, so a provider that has never heard of qwen-plus still
     * answers rather than failing on a model name from a different vendor.
     *
     * Device-local and never published, like the analysis model and unlike [AppPreferences]: a
     * model id means nothing on a phone pointed at another provider, and syncing one would rewrite
     * a working setting with a name that does not resolve.
     */
    fun opinionModel(provider: CloudProvider): String =
        preferences.getString(provider.opinionModelKey(), null)?.takeIf(String::isNotBlank)
            ?: OPINION_MODEL_DEFAULT

    fun saveOpinionModel(provider: CloudProvider, model: String) {
        preferences.edit().putString(provider.opinionModelKey(), model.trim()).apply()
    }

    /**
     * Whether an opinion request asks the provider for a live web search.
     *
     * On by default, and that is the whole point of the feature: without it the model has nothing
     * the app does not already have, so a view on a stock can only restate the card it was opened
     * from or half-remember something from training. Off is for a provider that charges too much
     * for it or answers with noise.
     */
    fun opinionSearchEnabled(): Boolean = preferences.getBoolean(KEY_OPINION_SEARCH, true)

    fun saveOpinionSearchEnabled(value: Boolean) {
        preferences.edit().putBoolean(KEY_OPINION_SEARCH, value).apply()
    }

    /**
     * How far back the news search reaches, in days.
     *
     * A setting rather than a constant because the right answer is not the same for two stocks. A
     * fortnight on a bank that discloses weekly is a full picture; a fortnight on a company that
     * says nothing between quarters returns an empty list, and a reader who wants to know why the
     * stock moved needs to be able to reach further back without waiting for a release to do it.
     *
     * Bounded on read rather than on write, so a value stored by an older or a newer build can
     * never send a window nobody meant.
     */
    fun opinionNewsWindowDays(): Int =
        preferences.getInt(KEY_OPINION_NEWS_WINDOW, OPINION_NEWS_WINDOW_DEFAULT)
            .coerceIn(OPINION_NEWS_WINDOW_MIN, OPINION_NEWS_WINDOW_MAX)

    fun saveOpinionNewsWindowDays(days: Int) {
        preferences.edit()
            .putInt(
                KEY_OPINION_NEWS_WINDOW,
                days.coerceIn(OPINION_NEWS_WINDOW_MIN, OPINION_NEWS_WINDOW_MAX),
            )
            .apply()
    }

    /**
     * Whether to pay for a search that reads what it finds and searches again.
     *
     * On by default, because the single pass it replaces is what made a searched answer read like
     * an unsearched one. Off is here for the press where the model refuses the strategy or where
     * the bill matters more than the second pass.
     */
    fun opinionDeepSearchEnabled(): Boolean =
        preferences.getBoolean(KEY_OPINION_DEEP_SEARCH, true)

    fun saveOpinionDeepSearchEnabled(value: Boolean) {
        preferences.edit().putBoolean(KEY_OPINION_DEEP_SEARCH, value).apply()
    }

    /** How many web results to ask for. More costs more, and five was finding one usable item. */
    fun opinionSearchResults(): Int =
        preferences.getInt(KEY_OPINION_SEARCH_RESULTS, OPINION_SEARCH_RESULTS_DEFAULT)
            .coerceIn(1, 20)

    fun saveOpinionSearchResults(count: Int) {
        preferences.edit().putInt(KEY_OPINION_SEARCH_RESULTS, count.coerceIn(1, 20)).apply()
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
        defaultTradeWindowSessions = Scoring.clampWindow(
            preferences.getInt(KEY_SCORING_WINDOW, Scoring.DEFAULT_WINDOW_SESSIONS),
        ),
        overdueRemindersEnabled = preferences.getBoolean(KEY_OVERDUE_REMINDERS, true),
        tradeAlertsEnabled = preferences.getBoolean(KEY_TRADE_ALERTS, true),
        callAlertsEnabled = preferences.getBoolean(KEY_CALL_ALERTS, false),
        approachAlertsEnabled = preferences.getBoolean(KEY_APPROACH_ALERTS, false),
        approachThresholdPercent = preferences.getInt(
            KEY_APPROACH_THRESHOLD,
            ApproachAlerts.DEFAULT_THRESHOLD_PERCENT,
        ),
        sessionDigestEnabled = preferences.getBoolean(KEY_SESSION_DIGEST, false),
        feedAlertsEnabled = preferences.getBoolean(KEY_FEED_ALERTS, true),
        scheduleAlertsEnabled = preferences.getBoolean(KEY_SCHEDULE_ALERTS, true),
        updateChecksEnabled = preferences.getBoolean(KEY_UPDATE_CHECKS, true),
        portfolioOrder = enumPreference(KEY_PORTFOLIO_ORDER, PortfolioOrder.URGENT),
        callOrder = enumPreference(KEY_CALL_ORDER, CallOrder.TICKER),
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
            .putInt(KEY_SCORING_WINDOW, Scoring.clampWindow(value.defaultTradeWindowSessions))
            .putBoolean(KEY_OVERDUE_REMINDERS, value.overdueRemindersEnabled)
            .putBoolean(KEY_TRADE_ALERTS, value.tradeAlertsEnabled)
            .putBoolean(KEY_CALL_ALERTS, value.callAlertsEnabled)
            .putBoolean(KEY_APPROACH_ALERTS, value.approachAlertsEnabled)
            .putInt(KEY_APPROACH_THRESHOLD, value.approachThresholdPercent)
            .putBoolean(KEY_SESSION_DIGEST, value.sessionDigestEnabled)
            .putBoolean(KEY_FEED_ALERTS, value.feedAlertsEnabled)
            .putBoolean(KEY_SCHEDULE_ALERTS, value.scheduleAlertsEnabled)
            .putBoolean(KEY_UPDATE_CHECKS, value.updateChecksEnabled)
            // By name rather than by ordinal: reordering the options or dropping one would otherwise
            // silently reinterpret what every existing install had chosen.
            .putString(KEY_PORTFOLIO_ORDER, value.portfolioOrder.name)
            .putString(KEY_CALL_ORDER, value.callOrder.name)
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
     * Whether the phone has already said the feed has gone quiet, and is waiting for it to come
     * back before saying so again.
     *
     * A spell, not an event. `PriceHealth` reports the same fault on every recompute for as long as
     * a symbol stays retired - which is right for a card somebody goes and looks at, and would be a
     * daily notification about a stock that stopped answering in June. So the notification is
     * raised on the way **in** to a spell and re-armed on the way out, exactly as `CallAlerts`
     * announces the crossing into a band and not the sitting inside one.
     *
     * Here rather than in `AppPreferences` because it travels nowhere: it records what *this* phone
     * has said, and two devices reporting one feed would each want to say it once. It is also not a
     * table, unlike its two cousins, because it is a single boolean rather than a row per thing -
     * see `position_approach_seen` for where the distinction goes the other way.
     */
    /**
     * When this reader last had the Insights tab open, so the page can say what arrived since.
     *
     * Device-local and deliberately not in `AppPreferences`, which is published: what one phone has
     * read is not a fact about the record, and syncing it would let a tablet mark a session read on
     * a phone that never showed it.
     *
     * Zero on an install that has never opened the tab, which reads as "everything is new" - and is
     * handled by the screen, which marks nothing on a first look rather than every session at once.
     */
    /**
     * The overdue count as of the last rebuild, for the home-screen widget.
     *
     * A cache, and named as one on purpose. The widget is drawn by the launcher whenever it likes,
     * in a process that may have just been started for it, and rebuilding the whole portfolio to
     * answer one number would be the most expensive thing in the app running on the cheapest
     * surface it has. What the widget shows is what the app last knew, which is the honest promise
     * a glance makes - and it is rewritten on every recompute, so it is never stale for long.
     */
    fun lastOverdueCount(): Int = preferences.getInt(KEY_OVERDUE_COUNT, 0)

    fun recordOverdueCount(count: Int) {
        preferences.edit().putInt(KEY_OVERDUE_COUNT, count).apply()
    }

    fun insightsSeenAt(): Long = preferences.getLong(KEY_INSIGHTS_SEEN, 0L)

    fun recordInsightsSeen(at: Long) {
        preferences.edit().putLong(KEY_INSIGHTS_SEEN, at).apply()
    }

    fun feedReportedQuiet(): Boolean = preferences.getBoolean(KEY_FEED_QUIET_REPORTED, false)

    fun recordFeedReportedQuiet(reported: Boolean) {
        preferences.edit().putBoolean(KEY_FEED_QUIET_REPORTED, reported).apply()
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
     * The folder the user picked for backups, as the tree URI they granted.
     *
     * Device-local and never published, for the reason [marketRefreshEnabled] is not in
     * [AppPreferences]: a grant belongs to one Android install and means nothing on another phone.
     * Synced, it would point a tablet at a folder it has no permission to write and leave it
     * reporting a backup destination it can do nothing with.
     */
    fun backupFolder(): String? = preferences.getString(KEY_BACKUP_FOLDER, null)

    fun saveBackupFolder(uri: String?) {
        preferences.edit().apply {
            if (uri == null) remove(KEY_BACKUP_FOLDER) else putString(KEY_BACKUP_FOLDER, uri)
        }.apply()
    }

    /**
     * The day this device last wrote a backup, so it writes one a day rather than one a sync.
     *
     * Device-local like the folder above, and excluded from the published settings for the second
     * reason `lastPriceRefreshDay` is: carried to a phone that has never backed up, it would claim
     * today was done and leave that phone with no backup at all until tomorrow.
     */
    fun lastBackupDay(): String? = preferences.getString(KEY_LAST_BACKUP_DAY, null)

    fun recordBackupDay(day: String) {
        preferences.edit().putString(KEY_LAST_BACKUP_DAY, day).apply()
    }

    /**
     * Whether this phone keeps prices fresh while the market is trading.
     *
     * The whole of the configuration: the window and the interval are constants in
     * [MarketRefresh], because there is one shape of this worth having and the form that let a
     * user assemble it out of days, windows and intervals was the entire complexity of the
     * feature it replaced.
     *
     * Deliberately not in [AppPreferences]: everything in there is published to the other devices,
     * and a refresh that travelled would have every phone fetching the same prices from a public
     * feed the app is a guest on. This is one device own answer, kept beside the settings rather
     * than among them.
     */
    fun marketRefreshEnabled(): Boolean = preferences.getBoolean(KEY_MARKET_REFRESH, false)

    fun saveMarketRefreshEnabled(value: Boolean) {
        preferences.edit().putBoolean(KEY_MARKET_REFRESH, value).apply()
    }

    /**
     * What the last market-hours fetch did, and when it said so.
     *
     * Written on every fire including the ones that did nothing, which is the point of it. The
     * failure mode of every scheduler on this platform is silence - the phone puts the app to
     * sleep, nothing fires, and nothing says so - and the only way to tell that from the outside
     * is a line that is never blank. [lastPriceRefreshAt] cannot serve: it moves only when prices
     * were actually fetched, so a run that skipped or failed would leave the screen reporting the
     * last success as though it had just happened.
     */
    fun marketRefreshNote(): String? = preferences.getString(KEY_MARKET_REFRESH_NOTE, null)

    fun marketRefreshNoteAt(): Long = preferences.getLong(KEY_MARKET_REFRESH_NOTE_AT, 0L)

    fun recordMarketRefreshNote(note: String) {
        preferences.edit()
            .putString(KEY_MARKET_REFRESH_NOTE, note)
            .putLong(KEY_MARKET_REFRESH_NOTE_AT, System.currentTimeMillis())
            .apply()
    }

    /**
     * The analyses this phone runs on its own, in the order they were made.
     *
     * Here rather than in the database, for the reason the switch above is: it is this device's
     * own answer and it is never published. A stored value that will not parse comes back as no
     * schedules at all rather than throwing - an unreadable list must not take the screen that
     * draws it down with it, and the failure that leaves is the safe one, since a schedule the
     * app cannot read is one it also cannot spend money on.
     *
     * A phone still holding the single schedule the previous build wrote reads it back as a list
     * of one, keeping its time, its aim and its switch. Not rewritten here: reading is not the
     * place to write, and the first save the user makes moves it across on its own.
     */
    fun analysisSchedules(): List<AnalysisSchedule> = runCatching {
        val stored = preferences.getString(KEY_ANALYSIS_SCHEDULES, null)
        if (stored != null) {
            JSONArray(stored).objectList().mapIndexed { index, json ->
                json.toAnalysisSchedule(fallbackId = index + 1L)
            }
        } else {
            // The shape this app wrote before it could hold more than one. It kept every trading
            // day, which is what an absent day list reads back as.
            preferences.getString(KEY_ANALYSIS_SCHEDULE, null)
                ?.let { listOf(JSONObject(it).toAnalysisSchedule(fallbackId = 1L)) }
                .orEmpty()
        }
    }.getOrDefault(emptyList())

    fun saveAnalysisSchedules(schedules: List<AnalysisSchedule>) {
        val array = JSONArray()
        schedules.take(AnalysisSchedule.MAX).forEach { array.put(it.toJson()) }
        preferences.edit().putString(KEY_ANALYSIS_SCHEDULES, array.toString()).apply()
    }

    /**
     * Writes one schedule back into the stored list, found by its id.
     *
     * Read, replace, write, rather than saving the whole list the caller happens to be holding:
     * the caller here is a run in a process with no screen in it, and the list on disk may have
     * been edited since that run started. Saving its copy would undo the edit. A schedule whose
     * id is no longer there was deleted while it ran, and its outcome is dropped rather than
     * bringing it back.
     */
    fun recordAnalysisSchedule(schedule: AnalysisSchedule) {
        val stored = analysisSchedules()
        if (stored.none { it.id == schedule.id }) return
        saveAnalysisSchedules(stored.map { if (it.id == schedule.id) schedule else it })
    }

    private fun JSONObject.toAnalysisSchedule(fallbackId: Long) = AnalysisSchedule(
        // A list written before ids existed gets its position, once, and keeps it from the next
        // save onwards.
        id = optLong("id", 0L).takeIf { it > 0L } ?: fallbackId,
        enabled = optBoolean("enabled", false),
        at = LocalTime.parse(getString("at")),
        // Absent is the single schedule the previous build stored, which ran every trading day.
        // An empty set that was actually chosen is written as an empty array and read back as one,
        // so a schedule the user emptied stays empty and says so.
        days = if (has("days")) {
            optJSONArray("days").stringList().mapNotNullTo(mutableSetOf()) { name ->
                DayOfWeek.entries.firstOrNull { it.name == name }
            }
        } else {
            AnalysisSchedule.DEFAULT_DAYS
        },
        channels = optJSONArray("channels").objectList().map {
            AnalysedChannel(it.getLong("id"), it.getString("name"))
        },
        contentTypes = optJSONArray("contentTypes").stringList()
            .mapNotNullTo(mutableSetOf()) { name ->
                AnalysisContentType.entries.firstOrNull { it.name == name }
            },
        lastFiredAt = optLong("lastFiredAt", 0L)
            .takeIf { it > 0L }
            ?.let(Instant::ofEpochMilli),
        lastOutcome = JobOutcome.entries
            .firstOrNull { it.name == optString("lastOutcome") }
            ?: JobOutcome.NEVER,
        lastMessage = optString("lastMessage").takeIf(String::isNotBlank),
        armedAt = Instant.ofEpochMilli(optLong("armedAt", 0L)),
    )

    private fun AnalysisSchedule.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("enabled", enabled)
        .put("at", at.toString())
        .put("days", JSONArray().apply { days.forEach { put(it.name) } })
        .put(
            "channels",
            JSONArray().apply {
                channels.forEach { put(JSONObject().put("id", it.id).put("name", it.name)) }
            },
        )
        .put(
            "contentTypes",
            JSONArray().apply { contentTypes.forEach { put(it.name) } },
        )
        .put("lastFiredAt", lastFiredAt?.toEpochMilli() ?: 0L)
        .put("lastOutcome", lastOutcome.name)
        .put("lastMessage", lastMessage.orEmpty())
        .put("armedAt", armedAt.toEpochMilli())

    /**
     * Whether the one-time move off the old job table has happened.
     *
     * A flag rather than a look at the table, because the migration job is to leave that table
     * gone: asking whether there are rows would answer no both before it has run on a phone that
     * never had any and after it has run on one that did, and the difference matters exactly once.
     */
    fun schedulesMigrated(): Boolean = preferences.getBoolean(KEY_SCHEDULES_MIGRATED, false)

    fun markSchedulesMigrated() {
        preferences.edit().putBoolean(KEY_SCHEDULES_MIGRATED, true).apply()
    }

    private fun JSONArray?.objectList(): List<JSONObject> {
        val array = this ?: return emptyList()
        return (0 until array.length()).map(array::getJSONObject)
    }

    private fun JSONArray?.stringList(): List<String> {
        val array = this ?: return emptyList()
        return (0 until array.length()).map(array::getString)
    }

    /**
     * Whether a schedule on this phone may start work that spends cloud credits.
     *
     * A second switch behind the schedule own one, and off until it is turned on. The free
     * market-hours refresh proves the alarms, the reboots and whatever the phone battery
     * manager does to a sleeping app; only once that is believable is it reasonable to let the
     * same machinery send a paid request while nobody is watching. Arming the clock to spend
     * money later is the same act as spending it, so it needs the owner own hand. Device-local
     * for the same reason [marketRefreshEnabled] is.
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
                // The catalogue that described the list this replaces cannot describe the new one,
                // and a stale one would win over the ids that just arrived.
                .remove(entry.provider.modelCatalogKey())
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

    private fun CloudProvider.modelCatalogKey() = "model_catalog_${name.lowercase()}"
    private fun CloudProvider.opinionModelKey() = "opinion_model_${name.lowercase()}"

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
        /**
         * `AppPreferences.defaultTradeWindowSessions`, under the name it was stored as when it
         * still decided scoring. Renaming the key would read as absent on every device that has
         * one and reset the value to the default, which is the whole cost of a cosmetic rename.
         */
        const val KEY_SCORING_WINDOW = "scoring_window_sessions"
        const val KEY_OVERDUE_REMINDERS = "overdue_reminders_enabled"
        const val KEY_TRADE_ALERTS = "trade_alerts_enabled"
        const val KEY_PORTFOLIO_ORDER = "portfolio_order"
        const val KEY_CALL_ORDER = "call_order"
        const val KEY_CALL_ALERTS = "call_alerts"
        const val KEY_UPDATE_CHECKS = "update_checks_enabled"
        const val KEY_LAST_PRICE_REFRESH = "last_price_refresh_day"
        const val KEY_LAST_PRICE_REFRESH_AT = "last_price_refresh_at"
        const val KEY_BACKUP_FOLDER = "backup_folder"
        const val KEY_LAST_BACKUP_DAY = "last_backup_day"
        const val KEY_MARKET_REFRESH = "market_refresh_enabled"

        /** Device-local, never synced: what this phone has already said about the feed. */
        const val KEY_FEED_QUIET_REPORTED = "feed_quiet_reported"
        const val KEY_INSIGHTS_SEEN = "insights_seen_at"
        const val KEY_OVERDUE_COUNT = "last_overdue_count"

        const val KEY_APPROACH_ALERTS = "approach_alerts_enabled"
        const val KEY_APPROACH_THRESHOLD = "approach_threshold_percent"
        const val KEY_SESSION_DIGEST = "session_digest_enabled"
        const val KEY_FEED_ALERTS = "feed_alerts_enabled"
        const val KEY_SCHEDULE_ALERTS = "schedule_alerts_enabled"
        const val KEY_MARKET_REFRESH_NOTE = "market_refresh_note"
        const val KEY_MARKET_REFRESH_NOTE_AT = "market_refresh_note_at"
        /** What the build that could hold only one schedule wrote. Read, never written. */
        const val KEY_ANALYSIS_SCHEDULE = "analysis_schedule"
        const val KEY_ANALYSIS_SCHEDULES = "analysis_schedules"
        const val KEY_SCHEDULES_MIGRATED = "schedules_migrated"
        const val KEY_PAID_SCHEDULES = "paid_schedules_enabled"
        const val KEY_OPINION_SEARCH = "opinion_search_enabled"
        const val KEY_OPINION_NEWS_WINDOW = "opinion_news_window_days"
        const val KEY_OPINION_DEEP_SEARCH = "opinion_deep_search"
        const val KEY_OPINION_SEARCH_RESULTS = "opinion_search_results"

        /**
         * What Ask AI runs on until the user picks something else.
         *
         * A text model, deliberately: the analysis default is a vision model because it reads
         * screenshots, and this request carries no image. A provider whose catalogue has no such
         * name is handled at the call site rather than here - the picker lists what the key
         * actually offers.
         */
        const val OPINION_MODEL_DEFAULT = "qwen-plus"

        /**
         * A fortnight, which is what the user asked for.
         *
         * Short enough that anything it returns is genuinely current, and short enough that most
         * stocks come back with nothing on most days. That is the honest result and the prompt is
         * written to report it as one rather than reaching further back to fill the list.
         */
        const val OPINION_NEWS_WINDOW_DEFAULT = 15
        const val OPINION_NEWS_WINDOW_MIN = 7
        const val OPINION_NEWS_WINDOW_MAX = 365

        /** Twelve rather than the provider's five, which was one usable Arabic item per press. */
        const val OPINION_SEARCH_RESULTS_DEFAULT = 12
        const val KEY_PROMPT_HISTORY = "prompt_history"
        const val KEY_SETTINGS_UPDATED_AT = "settings_updated_at"
        const val KEY_SETTINGS_UPDATED_BY = "settings_updated_by"
        const val KEY_SETTINGS_UNKNOWN = "settings_unknown"
    }
}
