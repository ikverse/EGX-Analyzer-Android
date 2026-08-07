package com.ikverse.egxanalyzer.data

import android.content.ContentResolver
import android.util.Base64
import com.ikverse.egxanalyzer.model.AnalysisRequest
import com.ikverse.egxanalyzer.model.AnalysisResult
import com.ikverse.egxanalyzer.model.RuleKind
import com.ikverse.egxanalyzer.model.AnalysisDiagnostics
import com.ikverse.egxanalyzer.model.AnalysisInput
import com.ikverse.egxanalyzer.model.AppPreferences
import com.ikverse.egxanalyzer.model.ResponseTimeout
import com.ikverse.egxanalyzer.model.CloudConfiguration
import com.ikverse.egxanalyzer.model.RecommendationResult
import com.ikverse.egxanalyzer.model.SourceTrace
import com.ikverse.egxanalyzer.model.UnaccountedImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
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
    /** Records what was sent. Null in tests, where there is no device to write to. */
    private val traceFor: ((String) -> RequestTrace)? = null,
) : AnalysisRepository {
    private val activeConnections = ConcurrentHashMap<String, HttpURLConnection>()

    /**
     * Reads the sources in chunks, then consolidates what came back.
     *
     * One request per run made the model lose track of which image it was describing, so the split
     * exists to keep IMAGE_REF small rather than to spread the work. Extraction judges sources;
     * consolidation ranks the occurrences and never sees an image.
     */
    override suspend fun analyze(request: AnalysisRequest): AnalysisResult = withContext(Dispatchers.IO) {
        val startedAt = System.nanoTime()
        val config = configuration()
        val appPreferences = preferences()
        require(config.endpoint.startsWith("https://")) { "Cloud endpoint must use HTTPS." }
        require(config.model.isNotBlank()) { "Choose a cloud model." }
        val credential = credentialStore.read(config.provider)
            ?: error("No credential is saved for ${config.provider.displayName}.")
        val trace = traceFor?.invoke(request.requestId)
        try {
            val harvest = extract(request, config, appPreferences, credential, trace)
            var attempt = 0
            var correctionInstructions: String? = null
            while (true) {
                val document = consolidate(
                    request, harvest, config, appPreferences, credential, correctionInstructions, trace,
                )
                // Kept apart from the validation warnings below: these describe the answer for the
                // record, and are not worth paying for a correction request over.
                val notes = mutableListOf<String>()
                val parsed = parseResponse(request, document, notes)
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
                            validationWarnings = harvest.warnings + notes + warnings,
                            correctionAttempted = attempt > 0 || harvest.retried,
                            durationMilliseconds = (System.nanoTime() - startedAt) / 1_000_000,
                            requestCount = harvest.requestCount + attempt + 1,
                            imagesSent = harvest.imagesSent,
                            unaccountedImages = harvest.unaccounted,
                            promptId = request.prompt?.id,
                            promptSchemaVersion = request.prompt?.schemaVersion,
                            promptRuleIds = request.prompt?.ruleIds.orEmpty(),
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

    /**
     * What every extraction request together said, with references translated to the run's numbering.
     *
     * A chunk answers in its own IMAGE_REF space, so the mapping back is done here where it is a
     * lookup rather than something the model has to remember.
     */
    private class Harvest {
        val extracted = JSONArray()
        val excluded = JSONArray()
        val inquiries = JSONArray()
        val unaccounted = mutableListOf<UnaccountedImage>()
        val warnings = mutableListOf<String>()
        var requestCount = 0
        var imagesSent = 0
        var retried = false
    }

    private suspend fun extract(
        request: AnalysisRequest,
        config: CloudConfiguration,
        appPreferences: AppPreferences,
        credential: CharArray,
        trace: RequestTrace?,
    ): Harvest {
        val harvest = Harvest()
        var globalRef = 0
        var chunkNumber = 0
        for (chunk in AnalysisChunking.chunk(request.inputs)) {
            chunkNumber += 1
            coroutineContext.ensureActive()
            // The run's reference for each image this chunk holds, in the order it sends them.
            val globalRefs = chunk.filterIsInstance<AnalysisInput.Image>().map { globalRef += 1; globalRef }
            harvest.imagesSent += globalRefs.size
            // A chunk that never answers used to throw out of this loop and take the run with
            // it - including every chunk already answered and already paid for. It is retried once,
            // and if it still will not answer the run keeps what the others returned.
            var answer = try {
                readChunk(
                    request, chunk, globalRefs, harvest, config, appPreferences, credential, null,
                    trace, "chunk-$chunkNumber",
                )
            } catch (timeout: SocketTimeoutException) {
                harvest.retried = true
                runCatching {
                    readChunk(
                        request, chunk, globalRefs, harvest, config, appPreferences, credential, null,
                        trace, "chunk-$chunkNumber-timeout-retry",
                    )
                }.getOrElse {
                    harvest.warnings += "Chunk $chunkNumber was dropped: the model did not answer " +
                        "within ${appPreferences.responseTimeoutSeconds}s, twice. Raise the timeout " +
                        "in Settings, Analysis, Validation."
                    null
                }
            } ?: run {
                // Its images are named as unaccounted, the same as any the model never mentioned,
                // so a report built without them says so rather than looking complete.
                (1..globalRefs.size).forEach { local ->
                    val imageTrace = request.traceForImage(chunk, local)
                    harvest.unaccounted += UnaccountedImage(
                        reference = globalRefs[local - 1],
                        sourceId = imageTrace?.sourceId,
                        caption = imageTrace?.preview,
                    )
                }
                continue
            }
            val missing = (1..globalRefs.size).filterNot(answer.cited::contains)
            if (missing.isNotEmpty()) {
                // Retry this chunk alone rather than the run: eight images, not thirty-two. The
                // second answer replaces the first rather than joining it, or a chunk that merely
                // forgot one image would contribute every other row twice.
                harvest.retried = true
                val second = readChunk(
                    request, chunk, globalRefs, harvest, config, appPreferences, credential,
                    "Your previous response left IMAGE_REF ${missing.joinToString(", ")} out of both " +
                        "`extracted` and `excluded`. Return the full response again, accounting for " +
                        "every IMAGE_REF supplied.",
                    trace, "chunk-$chunkNumber-retry",
                )
                if (second.cited.size > answer.cited.size) answer = second
            }
            harvest.adopt(answer)
            (1..globalRefs.size).filterNot(answer.cited::contains).forEach { local ->
                val trace = request.traceForImage(chunk, local)
                harvest.unaccounted += UnaccountedImage(
                    reference = globalRefs[local - 1],
                    sourceId = trace?.sourceId,
                    caption = trace?.preview,
                )
            }
        }
        if (harvest.unaccounted.isNotEmpty()) {
            harvest.warnings += "${harvest.unaccounted.size} image(s) were neither recommended nor excluded."
        }
        return harvest
    }

    /** One chunk's answer, held apart from the run until it is known to be the one to keep. */
    private class ChunkAnswer {
        val extracted = JSONArray()
        val excluded = JSONArray()
        val inquiries = JSONArray()
        val cited = mutableSetOf<Int>()
    }

    private fun Harvest.adopt(answer: ChunkAnswer) {
        answer.extracted.forEachObject(extracted::put)
        answer.excluded.forEachObject(excluded::put)
        answer.inquiries.forEachObject(inquiries::put)
    }

    private inline fun JSONArray.forEachObject(action: (JSONObject) -> Unit) {
        for (index in 0 until length()) optJSONObject(index)?.let(action)
    }

    /**
     * Sends one chunk and reads its answer, with references translated to the run's numbering.
     *
     * A reference outside the chunk's own range names an image this request never carried, so it is
     * dropped rather than translated into whatever global image happens to sit at that number.
     */
    private suspend fun readChunk(
        request: AnalysisRequest,
        chunk: List<AnalysisInput>,
        globalRefs: List<Int>,
        harvest: Harvest,
        config: CloudConfiguration,
        appPreferences: AppPreferences,
        credential: CharArray,
        correctionInstructions: String?,
        trace: RequestTrace?,
        label: String,
    ): ChunkAnswer {
        val body = request.extractionBody(chunk, config.model, appPreferences, correctionInstructions)
        val response = executeCompletion(request.requestId, body, config, appPreferences, credential)
        trace?.record(label, body, response)
        harvest.requestCount += 1
        val answer = ChunkAnswer()
        val payload = runCatching { JSONObject(stripCodeFence(contentOf(response))) }.getOrNull()
        if (payload == null) {
            harvest.warnings += "A source chunk returned no readable JSON."
            return answer
        }
        payload.optJSONArray("extracted").adopt(answer.extracted, globalRefs, answer.cited, harvest.warnings)
        payload.optJSONArray("excluded").adopt(answer.excluded, globalRefs, answer.cited, harvest.warnings)
        payload.optJSONArray("client_inquiry_responses")
            .adopt(answer.inquiries, globalRefs, answer.cited, harvest.warnings)
        return answer
    }

    /**
     * Moves one chunk's rows into the run, rewriting `source_image_ref` from chunk-local to global.
     */
    private fun JSONArray?.adopt(
        destination: JSONArray,
        globalRefs: List<Int>,
        cited: MutableSet<Int>,
        warnings: MutableList<String>,
    ) {
        if (this == null) return
        for (index in 0 until length()) {
            val row = optJSONObject(index) ?: continue
            val local = if (row.isNull("source_image_ref")) null else row.optInt("source_image_ref", 0)
            when {
                local == null || local == 0 -> Unit
                local in 1..globalRefs.size -> {
                    cited += local
                    row.put("source_image_ref", globalRefs[local - 1])
                }
                else -> {
                    warnings += "Dropped a citation of IMAGE_REF $local, which was not in that request."
                    row.put("source_image_ref", JSONObject.NULL)
                }
            }
            destination.put(row)
        }
    }

    /**
     * Ranks what extraction found, then rebuilds the document the rest of the app already reads.
     *
     * Exclusions and inquiry replies are per-source judgements that extraction has already made, so
     * they are carried across rather than asked for again.
     */
    private suspend fun consolidate(
        request: AnalysisRequest,
        harvest: Harvest,
        config: CloudConfiguration,
        appPreferences: AppPreferences,
        credential: CharArray,
        correctionInstructions: String?,
        trace: RequestTrace?,
    ): String {
        val ranked = if (harvest.extracted.length() == 0) {
            JSONObject().put("top_consolidated_recommendations", JSONArray())
        } else {
            val body = request.consolidationBody(
                harvest.extracted, config.model, appPreferences, correctionInstructions,
            )
            val response = executeCompletion(request.requestId, body, config, appPreferences, credential)
            trace?.record("consolidation", body, response)
            runCatching { JSONObject(stripCodeFence(contentOf(response))) }.getOrElse {
                harvest.warnings += "Consolidation returned no readable JSON."
                JSONObject().put("top_consolidated_recommendations", JSONArray())
            }
        }
        return JSONObject().apply {
            put("analysis_period", "${request.sourceWindowStart} through ${request.sourceWindowEnd}")
            put(
                "top_consolidated_recommendations",
                ranked.optJSONArray("top_consolidated_recommendations") ?: JSONArray(),
            )
            put("excluded", harvest.excluded)
            put("client_inquiry_responses", harvest.inquiries)
        }.toString()
    }

    private fun contentOf(response: String): String = JSONObject(response)
        .getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")

    private fun stripCodeFence(value: String): String = value.trim()
        .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()

    /** The trace behind the chunk's nth image, for naming an image the model never mentioned. */
    private fun AnalysisRequest.traceForImage(chunk: List<AnalysisInput>, local: Int): SourceTrace? {
        val sourceId = chunk.filterIsInstance<AnalysisInput.Image>().getOrNull(local - 1)?.sourceId
        return sourceTraces.firstOrNull { it.sourceId == sourceId }
    }

    private suspend fun executeCompletion(
        requestId: String,
        body: JSONObject,
        config: CloudConfiguration,
        appPreferences: AppPreferences,
        credential: CharArray,
    ): String {
        coroutineContext.ensureActive()
        val url = URL("${config.endpoint.trimEnd('/')}/chat/completions")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = appPreferences.responseTimeoutSeconds.coerceIn(
                ResponseTimeout.MIN,
                ResponseTimeout.MAX,
            ) * 1_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer ${String(credential)}")
        }
        activeConnections[requestId] = connection
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray()) }
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
            activeConnections.remove(requestId, connection)
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
                    readTimeout = preferences().responseTimeoutSeconds.coerceIn(
                        ResponseTimeout.MIN,
                        ResponseTimeout.MAX,
                    ) * 1_000
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

    private suspend fun AnalysisRequest.extractionBody(
        chunk: List<AnalysisInput>,
        modelName: String,
        preferences: AppPreferences,
        correctionInstructions: String? = null,
    ) = JSONObject().apply {
        put("model", modelName)
        // Reading a price off a card has one right answer, printed in the source, so sampling could
        // only ever move away from it - it is what produced English company names that changed
        // between runs of the same card. Fixed rather than offered as a setting: no value above 0
        // is useful for extraction. The desktop pins the same value in its own request builder.
        put("temperature", 0.0)
        put("messages", JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                // The generated prompt when there is one, the shipped one otherwise. There is no
                // longer a box that replaces the whole file: that froze whoever used it out of
                // every prompt improvement an update would have brought.
                put("content", prompt?.text ?: promptStore.consolidatedPrompt())
            })
            put(JSONObject().apply {
                put("role", "user")
                put("content", JSONArray().apply {
                    put(
                        JSONObject().put("type", "text").put(
                            "text",
                            "${requestPrompt(chunk)} ${preferences.analysisLanguage.promptInstruction} " +
                                customizationPrompt(rules) +
                                correctionInstructions.orEmpty(),
                        ),
                    )
                    var imageRef = 0
                    chunk.forEach { input ->
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
     * Runtime context for one extraction request.
     *
     * Only this chunk's messages are listed. Naming the whole run's sources would offer the model
     * TELEGRAM_IDs for images it was not given, which is the citation it cannot check and we
     * cannot either.
     */
    private fun AnalysisRequest.requestPrompt(chunk: List<AnalysisInput>): String = buildString {
        val sourceIds = chunk.mapTo(LinkedHashSet(), AnalysisInput::sourceId)
        val images = chunk.count { it is AnalysisInput.Image }
        appendLine("RUNTIME CONTEXT")
        appendLine("ANALYSIS_PERIOD: $sourceWindowStart through $sourceWindowEnd")
        appendLine("TARGET_DATE: $targetDate")
        if (images > 0) appendLine("IMAGE_REF values in this request run 1 to $images.")
        appendLine("SOURCE ITEMS FOLLOW. Apply the canonical prompt independently to each item.")
        sourceTraces.filter { it.sourceId in sourceIds }.forEach { trace ->
            appendLine(
                "--- MESSAGE | CHANNEL: ${trace.channelName} | DATE: ${trace.timestamp} | " +
                    "TELEGRAM_ID: ${trace.messageId ?: trace.sourceId} ---",
            )
        }
    }

    /**
     * The consolidation request: every occurrence as text, and no images at all.
     *
     * This is the cheapest call in a run and the one that needs the whole picture, which is why the
     * two jobs are separated - ranking wants everything in view, reading a card wants as little as
     * possible.
     */
    private fun AnalysisRequest.consolidationBody(
        extracted: JSONArray,
        modelName: String,
        preferences: AppPreferences,
        correctionInstructions: String?,
    ) = JSONObject().apply {
        put("model", modelName)
        put("temperature", 0.0)
        put("messages", JSONArray().apply {
            put(
                JSONObject().put("role", "system")
                    .put("content", promptStore.consolidationPrompt()),
            )
            put(
                JSONObject().put("role", "user").put(
                    "content",
                    buildString {
                        appendLine("RUNTIME CONTEXT")
                        appendLine("ANALYSIS_PERIOD: $sourceWindowStart through $sourceWindowEnd")
                        appendLine("TARGET_DATE: $targetDate")
                        append(preferences.analysisLanguage.promptInstruction)
                        correctionInstructions?.let { appendLine(it) }
                        appendLine()
                        appendLine("EXTRACTED OCCURRENCES:")
                        append(extracted.toString())
                    },
                ),
            )
        })
        put("response_format", JSONObject().put("type", "json_object"))
    }

    /**
     * What the run's own rules add to the prompt.
     *
     * Only rules that asked to be sent appear here. A rule scoped to this device is a decision
     * already made by the time anything is uploaded, and repeating it to the model would be asking
     * it to re-judge sources it will never see.
     */
    private fun customizationPrompt(rules: RuleSet): String = buildString {
        val include = rules.modelPhrases(RuleKind.INCLUDE)
        if (include.isNotEmpty()) {
            append("Prioritize content matching these phrases: ${include.joinToString(", ")}. ")
        }
        val exclude = rules.modelPhrases(RuleKind.EXCLUDE)
        if (exclude.isNotEmpty()) {
            append("Exclude content matching these phrases: ${exclude.joinToString(", ")}. ")
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

    private fun parseResponse(
        request: AnalysisRequest,
        content: String,
        notes: MutableList<String> = mutableListOf(),
    ): AnalysisResult {
        // The gate needs the session to compare against; without it every re-posted card passes.
        val consolidated = ConsolidatedParser.parse(content, request.targetDate, notes)
        val inquiries = runCatching {
            JSONObject(stripCodeFence(content)).optJSONArray("client_inquiry_responses")?.length() ?: 0
        }.getOrDefault(0)
        return AnalysisResult(
            requestId = request.requestId,
            consolidated = consolidated,
            // Same order the request used, so IMAGE_REF n resolves to entry n - 1.
            imagePaths = request.inputs.filterIsInstance<AnalysisInput.Image>().map { it.uri.toString() },
            recommendations = ConsolidatedParser.flatten(
                consolidated, request.sourceTraces, request.targetDate, notes,
            ),
            inquiryReplyCount = inquiries,
            analysisMode = request.mode,
            recommendationTargetDate = request.targetDate,
            rawResponse = content,
            completedAt = Instant.now(),
            sources = request.sourceTraces,
            selectedChannels = request.selectedChannels,
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
