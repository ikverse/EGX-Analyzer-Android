package com.ikverse.egxanalyzer.model

import org.json.JSONObject
import java.time.Instant

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
