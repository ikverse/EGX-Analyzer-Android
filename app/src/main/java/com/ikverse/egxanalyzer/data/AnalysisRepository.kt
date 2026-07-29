package com.ikverse.egxanalyzer.data

import android.content.ContentResolver
import android.util.Base64
import com.ikverse.egxanalyzer.model.AnalysisRequest
import com.ikverse.egxanalyzer.model.AnalysisResult
import com.ikverse.egxanalyzer.model.AnalysisDiagnostics
import com.ikverse.egxanalyzer.model.AnalysisInput
import com.ikverse.egxanalyzer.model.AppPreferences
import com.ikverse.egxanalyzer.model.CloudConfiguration
import com.ikverse.egxanalyzer.model.RecommendationResult
import com.ikverse.egxanalyzer.model.SourceTrace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.io.FileInputStream
import java.io.InputStream
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

interface AnalysisRepository {
    suspend fun analyze(request: AnalysisRequest): AnalysisResult
    suspend fun listModels(): List<String>
    suspend fun cancel(requestId: String): Boolean
}

/**
 * Boundary for the upcoming provider adapter.
 *
 * The UI and domain layers already use provider-neutral text, image, and voice inputs. A
 * provider adapter can therefore serialize all three for Qwen or another selected cloud model
 * without introducing a desktop engine or local-model dependency into the Android app.
 */
class CloudAnalysisRepository(
    private val contentResolver: ContentResolver,
    private val credentialStore: CredentialStore,
    private val promptStore: PromptStore,
    private val configuration: () -> CloudConfiguration,
    private val preferences: () -> AppPreferences,
) : AnalysisRepository {
    private val activeConnections = ConcurrentHashMap<String, HttpURLConnection>()

    override suspend fun analyze(request: AnalysisRequest): AnalysisResult = withContext(Dispatchers.IO) {
        val startedAt = System.nanoTime()
        val config = configuration()
        val appPreferences = preferences()
        require(config.endpoint.startsWith("https://")) { "Cloud endpoint must use HTTPS." }
        require(config.model.isNotBlank()) { "Choose a cloud model." }
        val credential = credentialStore.read(config.provider)
            ?: error("No credential is saved for ${config.provider.displayName}.")
        try {
            var attempt = 0
            var correctionInstructions: String? = null
            while (true) {
                val response = executeCompletion(
                    request = request,
                    config = config,
                    appPreferences = appPreferences,
                    credential = credential,
                    correctionInstructions = correctionInstructions,
                )
                val parsed = parseResponse(request, response)
                val (recommendations, warnings) =
                    validateRecommendations(request, parsed.recommendations)
                if (warnings.isEmpty() || attempt >= appPreferences.correctionRetries) {
                    return@withContext parsed.copy(
                        recommendations = recommendations.map {
                            if (appPreferences.catalogEnrichmentEnabled) EgxCatalog.enrich(it) else it
                        },
                        diagnostics = AnalysisDiagnostics(
                            sourceWindowStart = request.sourceWindowStart,
                            sourceWindowEnd = request.sourceWindowEnd,
                            inputCount = request.inputs.size + request.excludedSources.size,
                            acceptedInputCount = request.inputs.size,
                            excludedSources = request.excludedSources,
                            validationWarnings = warnings,
                            correctionAttempted = attempt > 0,
                            durationMilliseconds = (System.nanoTime() - startedAt) / 1_000_000,
                        ),
                    )
                }
                attempt += 1
                correctionInstructions =
                    "Correct the previous JSON response. Validation found: " +
                        warnings.joinToString(" ") +
                        " Cite only supplied source IDs and keep the exact target date. " +
                        "Previous response: ${parsed.rawResponse.take(12_000)}"
            }
            error("Analysis retry loop ended unexpectedly.")
        } finally {
            credential.fill('\u0000')
            activeConnections.remove(request.requestId)?.disconnect()
        }
    }

    private suspend fun executeCompletion(
        request: AnalysisRequest,
        config: CloudConfiguration,
        appPreferences: AppPreferences,
        credential: CharArray,
        correctionInstructions: String?,
    ): String {
        coroutineContext.ensureActive()
        val url = URL("${config.endpoint.trimEnd('/')}/chat/completions")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = appPreferences.responseTimeoutSeconds.coerceIn(30, 300) * 1_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer ${String(credential)}")
        }
        activeConnections[request.requestId] = connection
        try {
            val body = request.toRequestJson(
                config.model,
                appPreferences,
                correctionInstructions,
            ).toString().toByteArray()
            connection.outputStream.use { it.write(body) }
            coroutineContext.ensureActive()
            val status = connection.responseCode
            val response = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
            if (status !in 200..299) {
                val message = runCatching {
                    JSONObject(response).optJSONObject("error")?.optString("message")
                }.getOrNull().takeUnless { it.isNullOrBlank() }
                    ?: "Cloud request failed (HTTP $status)."
                error(message)
            }
            return response
        } finally {
            activeConnections.remove(request.requestId, connection)
            connection.disconnect()
        }
    }

    override suspend fun cancel(requestId: String): Boolean =
        activeConnections.remove(requestId)?.let {
            it.disconnect()
            true
        } ?: false

    override suspend fun listModels(): List<String> = withContext(Dispatchers.IO) {
        val config = configuration()
        require(config.endpoint.startsWith("https://")) { "Cloud endpoint must use HTTPS." }
        val credential = credentialStore.read(config.provider)
            ?: error("Save a credential for ${config.provider.displayName} first.")
        var connection: HttpURLConnection? = null
        try {
            coroutineContext.ensureActive()
            connection = (URL("${config.endpoint.trimEnd('/')}/models").openConnection() as HttpURLConnection)
                .apply {
                    requestMethod = "GET"
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = preferences().responseTimeoutSeconds.coerceIn(30, 300) * 1_000
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("Authorization", "Bearer ${String(credential)}")
                }
            val status = connection.responseCode
            val response = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
            if (status !in 200..299) {
                val message = runCatching {
                    JSONObject(response).optJSONObject("error")?.optString("message")
                }.getOrNull().takeUnless { it.isNullOrBlank() }
                    ?: "Could not load models (HTTP $status)."
                error(message)
            }
            parseModelIds(response)
        } finally {
            credential.fill('\u0000')
            connection?.disconnect()
        }
    }

    private suspend fun AnalysisRequest.toRequestJson(
        modelName: String,
        preferences: AppPreferences,
        correctionInstructions: String? = null,
    ) = JSONObject().apply {
        put("model", modelName)
        put("temperature", preferences.temperature.coerceIn(0.0, 1.0))
        put("messages", JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put(
                    "content",
                    preferences.customSystemPrompt.trim().ifBlank { promptStore.consolidatedPrompt() },
                )
            })
            put(JSONObject().apply {
                put("role", "user")
                put("content", JSONArray().apply {
                    put(
                        JSONObject().put("type", "text").put(
                            "text",
                            "${requestPrompt()} ${preferences.analysisLanguage.promptInstruction} " +
                                customizationPrompt(preferences) +
                                correctionInstructions.orEmpty(),
                        ),
                    )
                    var imageRef = 0
                    inputs.forEach { input ->
                        coroutineContext.ensureActive()
                        val telegramId = telegramIdFor(input.sourceId)
                        when (input) {
                            is AnalysisInput.Text -> put(
                                JSONObject().put("type", "text").put(
                                    "text",
                                    "--- MESSAGE | TELEGRAM_ID: $telegramId ---\n${input.value}",
                                ),
                            )
                            is AnalysisInput.Image -> {
                                imageRef += 1
                                // The prompt cites images by IMAGE_REF, so each one is announced
                                // before its bytes and tied back to its own message.
                                put(
                                    JSONObject().put("type", "text").put(
                                        "text",
                                        "IMAGE_REF $imageRef | TELEGRAM_ID: $telegramId",
                                    ),
                                )
                                put(
                                    JSONObject().put("type", "image_url").put(
                                        "image_url",
                                        JSONObject().put("url", input.dataUrl()),
                                    ),
                                )
                            }
                            is AnalysisInput.Voice -> {
                                put(
                                    JSONObject().put("type", "text").put(
                                        "text",
                                        "--- VOICE | TELEGRAM_ID: $telegramId ---",
                                    ),
                                )
                                put(
                                    JSONObject().put("type", "input_audio").put(
                                        "input_audio",
                                        JSONObject()
                                            .put("data", input.base64())
                                            .put("format", input.audioFormat()),
                                    ),
                                )
                            }
                        }
                    }
                })
            })
        })
    }

    /**
     * Runtime context for the canonical prompt.
     *
     * The prompt defines the output contract itself, so this supplies only the values it refers
     * to - TARGET_DATE and the per-source TELEGRAM_ID / IMAGE_REF labels - in the same shape the
     * desktop assembles, so both clients present sources identically.
     */
    private fun AnalysisRequest.requestPrompt(): String = buildString {
        appendLine("RUNTIME CONTEXT")
        appendLine("ANALYSIS_PERIOD: $sourceWindowStart through $sourceWindowEnd")
        appendLine("TARGET_DATE: $targetDate")
        appendLine("SOURCE ITEMS FOLLOW. Apply the canonical prompt independently to each item.")
        sourceTraces.forEach { trace ->
            appendLine(
                "--- MESSAGE | CHANNEL: ${trace.channelName} | DATE: ${trace.timestamp} | " +
                    "TELEGRAM_ID: ${trace.messageId ?: trace.sourceId} ---",
            )
        }
    }

    private fun customizationPrompt(value: AppPreferences): String = buildString {
        if (value.includePhrases.isNotBlank()) {
            append("Prioritize content matching these phrases: ${value.includePhrases}. ")
        }
        if (value.excludePhrases.isNotBlank()) {
            append("Exclude content matching these phrases: ${value.excludePhrases}. ")
        }
    }

    private fun AnalysisInput.Image.dataUrl(): String =
        "data:$mimeType;base64,${openInput(uri).use { stream ->
            requireNotNull(stream) { "Cannot read image $sourceId." }
            Base64.encodeToString(stream.readBytes(), Base64.NO_WRAP)
        }}"

    private fun AnalysisInput.Voice.base64(): String =
        openInput(uri).use { stream ->
            requireNotNull(stream) { "Cannot read voice message $sourceId." }
            Base64.encodeToString(stream.readBytes(), Base64.NO_WRAP)
        }

    private fun openInput(uri: android.net.Uri): InputStream? =
        if (uri.scheme == "file") uri.path?.let(::FileInputStream)
        else contentResolver.openInputStream(uri)

    private fun AnalysisInput.Voice.audioFormat(): String = when {
        mimeType.contains("mpeg") -> "mp3"
        mimeType.contains("wav") -> "wav"
        else -> "ogg"
    }

    private fun parseResponse(request: AnalysisRequest, response: String): AnalysisResult {
        val envelope = JSONObject(response)
        val content = envelope.getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message").getString("content")
        val consolidated = ConsolidatedParser.parse(content)
        val inquiries = runCatching {
            JSONObject(content.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim())
                .optJSONArray("client_inquiry_responses")?.length() ?: 0
        }.getOrDefault(0)
        return AnalysisResult(
            requestId = request.requestId,
            consolidated = consolidated,
            recommendations = ConsolidatedParser.flatten(
                consolidated, request.sourceTraces, request.targetDate,
            ),
            inquiryReplyCount = inquiries,
            analysisMode = request.mode,
            recommendationTargetDate = request.targetDate,
            rawResponse = content,
            completedAt = Instant.now(),
            sources = request.sourceTraces,
        )
    }

    private fun JSONObject.optNullableString(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).takeIf(String::isNotBlank)

    private fun JSONObject.optNullableDouble(key: String): Double? =
        if (!has(key) || isNull(key)) null else optDouble(key)

    private fun validateRecommendations(
        request: AnalysisRequest,
        values: List<RecommendationResult>,
    ): Pair<List<RecommendationResult>, List<String>> {
        // flatten() resolves citations from source_message_id, so an unresolved row is one whose
        // TELEGRAM_ID did not belong to any supplied source.
        val knownSources = request.sourceTraces.mapTo(mutableSetOf(), SourceTrace::sourceId)
        val warnings = mutableListOf<String>()
        val accepted = values.mapNotNull { recommendation ->
            val ticker = recommendation.ticker.trim().uppercase().removeSuffix(".CA")
            val citedSources = recommendation.sourceIds.filter(knownSources::contains).distinct()
            when {
                ticker.isBlank() || !ticker.matches(Regex("[A-Z][A-Z0-9]{1,9}")) -> {
                    warnings += "Excluded recommendation with invalid ticker '${recommendation.ticker}'."
                    null
                }
                citedSources.isEmpty() -> {
                    warnings += "Excluded $ticker because it did not cite a supplied source."
                    null
                }
                else -> {
                    val unknownCount = recommendation.sourceIds.size - citedSources.size
                    if (unknownCount > 0) {
                        warnings += "$ticker contained $unknownCount unknown source citation(s)."
                    }
                    recommendation.copy(
                        ticker = ticker,
                        targetDate = request.targetDate,
                        sourceIds = citedSources,
                        signal = recommendation.signal.takeIf { it in setOf("BUY", "SELL") } ?: "HOLD",
                        confidence = recommendation.confidence?.coerceIn(0.0, 1.0),
                    )
                }
            }
        }
        return accepted to warnings
    }

    private fun AnalysisRequest.telegramIdFor(sourceId: String): String =
        sourceTraces.firstOrNull { it.sourceId == sourceId }?.messageId?.toString() ?: sourceId

    private companion object {
        const val CONNECT_TIMEOUT_MS = 30_000
    }
}

internal fun parseModelIds(response: String): List<String> {
    val envelope = JSONObject(response)
    val models = envelope.optJSONArray("data")
        ?: envelope.optJSONArray("models")
        ?: JSONArray()
    return buildList {
        for (index in 0 until models.length()) {
            val item = models.opt(index)
            val id = when (item) {
                is JSONObject -> item.optString("id").ifBlank { item.optString("name") }
                is String -> item
                else -> ""
            }
            if (id.isNotBlank()) add(id)
        }
    }.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)
}
