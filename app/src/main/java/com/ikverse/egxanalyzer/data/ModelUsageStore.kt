package com.ikverse.egxanalyzer.data

import android.content.Context
import com.ikverse.egxanalyzer.model.CloudProvider
import com.ikverse.egxanalyzer.model.TokenUsage
import org.json.JSONObject
import java.time.Instant

/**
 * What each model has cost this phone in tokens, for as long as it has been asked anything.
 *
 * A run's own total lives with the run, in its diagnostics. This is the other question - "which
 * model have I been spending on" - and it cannot be answered from the reports, because Ask AI
 * spends too and leaves no report behind. Kept per provider as well as per model: the same id is
 * billed by two accounts when the endpoint moves, and a total that mixed them would mean nothing.
 *
 * Its own preference file rather than a row in the settings: this is a running tally that changes
 * on every request, and it must never travel to another device the way settings do - a token count
 * describes one phone's spending, and merging two would invent a number nobody was billed for.
 */
class ModelUsageStore(context: Context) {

    private val preferences = context.getSharedPreferences("egx_model_usage", Context.MODE_PRIVATE)

    /**
     * Adds one request to a model's tally.
     *
     * [usage] is null when the provider answered without a `usage` block. The request is still
     * counted - it happened and it was paid for - and it is counted separately, so a total that is
     * missing part of its spend says so rather than reading as a complete figure.
     */
    @Synchronized
    fun record(provider: CloudProvider, model: String, usage: TokenUsage?) {
        if (model.isBlank()) return
        val existing = read(provider, model)
        val updated = existing.copy(
            requests = existing.requests + 1,
            unreportedRequests = existing.unreportedRequests + if (usage == null) 1 else 0,
            usage = existing.usage + (usage ?: TokenUsage.NONE),
            lastUsed = Instant.now(),
        )
        preferences.edit().putString(key(provider, model), updated.toJson().toString()).apply()
    }

    /** Every model this phone has spent on, heaviest first. */
    fun all(): List<ModelUsageRecord> = preferences.all.keys
        .mapNotNull { storedKey -> read(storedKey) }
        .sortedWith(
            compareByDescending<ModelUsageRecord> { it.usage.totalTokens }
                .thenByDescending { it.requests },
        )

    fun forModel(provider: CloudProvider, model: String): ModelUsageRecord? =
        read(provider, model).takeIf { it.requests > 0 }

    @Synchronized
    fun clear() = preferences.edit().clear().apply()

    private fun read(provider: CloudProvider, model: String): ModelUsageRecord =
        read(key(provider, model)) ?: ModelUsageRecord(provider, model)

    /**
     * One stored row, or null where it cannot be read as one.
     *
     * A key written by a build that stored something else, or a provider this build no longer has,
     * is dropped rather than allowed to throw: this is a tally, and no part of it is worth taking a
     * screen down for.
     */
    private fun read(storedKey: String): ModelUsageRecord? {
        val provider = CloudProvider.entries.firstOrNull {
            storedKey.startsWith("${it.name}$SEPARATOR")
        } ?: return null
        val model = storedKey.removePrefix("${provider.name}$SEPARATOR").takeIf(String::isNotBlank)
            ?: return null
        val raw = preferences.getString(storedKey, null) ?: return null
        return runCatching {
            val value = JSONObject(raw)
            ModelUsageRecord(
                provider = provider,
                model = model,
                requests = value.optInt("requests"),
                unreportedRequests = value.optInt("unreportedRequests"),
                usage = TokenUsage(
                    promptTokens = value.optLong("promptTokens"),
                    completionTokens = value.optLong("completionTokens"),
                    totalTokens = value.optLong("totalTokens"),
                ),
                lastUsed = value.optLong("lastUsed").takeIf { it > 0 }
                    ?.let(Instant::ofEpochMilli),
            )
        }.getOrNull()
    }

    private fun key(provider: CloudProvider, model: String) =
        "${provider.name}$SEPARATOR${model.trim()}"

    private companion object {
        /**
         * A pair no provider puts in a model id, so the split back is unambiguous.
         *
         * A single colon would not do - `qwen3-vl:4b` carries one - and a NUL cannot be written
         * at all, because these keys end up in the preference XML.
         */
        const val SEPARATOR = "::"
    }
}

data class ModelUsageRecord(
    val provider: CloudProvider,
    val model: String,
    val requests: Int = 0,
    /** Requests the provider reported no usage for, so the tokens below are known to be short. */
    val unreportedRequests: Int = 0,
    val usage: TokenUsage = TokenUsage.NONE,
    val lastUsed: Instant? = null,
) {
    internal fun toJson() = JSONObject().apply {
        put("requests", requests)
        put("unreportedRequests", unreportedRequests)
        put("promptTokens", usage.promptTokens)
        put("completionTokens", usage.completionTokens)
        put("totalTokens", usage.totalTokens)
        lastUsed?.let { put("lastUsed", it.toEpochMilli()) }
    }
}
