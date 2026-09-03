package com.ikverse.egxanalyzer.data

import android.content.ContentResolver
import android.util.Base64
import com.ikverse.egxanalyzer.model.AnalysisChunking
import com.ikverse.egxanalyzer.model.AnalysisRequest
import com.ikverse.egxanalyzer.model.AnalysisResult
import com.ikverse.egxanalyzer.model.RuleKind
import com.ikverse.egxanalyzer.model.AnalysisDiagnostics
import com.ikverse.egxanalyzer.model.AnalysisInput
import com.ikverse.egxanalyzer.model.AppPreferences
import com.ikverse.egxanalyzer.model.ResponseTimeout
import com.ikverse.egxanalyzer.model.CloudConfiguration
import com.ikverse.egxanalyzer.model.CloudModelInfo
import com.ikverse.egxanalyzer.model.ModelModality
import com.ikverse.egxanalyzer.model.RuleSet
import com.ikverse.egxanalyzer.model.TokenUsage
import com.ikverse.egxanalyzer.model.RecommendationResult
import com.ikverse.egxanalyzer.model.SourceTrace
import com.ikverse.egxanalyzer.model.UnaccountedImage
import kotlinx.coroutines.CancellationException
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
import com.ikverse.egxanalyzer.model.CloudProvider

interface AnalysisRepository {
    suspend fun analyze(request: AnalysisRequest): AnalysisResult
    suspend fun listModels(): List<CloudModelInfo>
    suspend fun cancel(requestId: String): Boolean

    /**
     * One text-only question, answered by whichever model the caller names.
     *
     * Shares the endpoint, the saved credential, the timeout and the cancel path with [analyze] and
     * nothing else. That is transport; a second repository would mean a second copy of the
     * credential zeroing and the connection map, which drift apart the first time either is
     * touched. The prompt, the model and the response format all come from the caller, so nothing
     * about an analysis run reaches this and nothing here can reach one.
     */
    suspend fun ask(request: OpinionRequest): String
}

/**
 * A question put to the model on its own, with no images and no analysis behind it.
 *
 * [model] is passed rather than read from the configuration because Ask AI has a model of its own:
 * the analysis runs on a vision model because it reads screenshots, and this request carries none.
 */
data class OpinionRequest(
    val requestId: String,
    /** The whole system prompt, already composed by the caller. */
    val systemPrompt: String,
    val question: String,
    val model: String,
    /** Whether to ask the provider to attach a live web search. */
    val search: Boolean,
    /**
     * How many web results to ask for.
     *
     * Five is what OpenRouter attaches when nobody says otherwise, and five results about a company
     * whose news is published in Arabic is one or two usable items. Raised from Settings; ignored
     * by providers that take no count.
     */
    val searchResults: Int = DEFAULT_SEARCH_RESULTS,
    /**
     * What OpenRouter prints above the results it retrieved.
     *
     * Not the query - the query comes from the question. This is the last thing the model reads
     * before deciding which results to believe, which is where the date window has to be repeated.
     * Null leaves OpenRouter's own wording in place.
     */
    val searchPrompt: String? = null,
    /** Whether to ask Qwen to search repeatedly rather than take a single pass. */
    val deepSearch: Boolean = false,
) {
    companion object {
        /** The provider default, so a request that says nothing behaves as it always did. */
        const val DEFAULT_SEARCH_RESULTS = 5
    }
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
    /**
     * Where every request's token count is added up, per model.
     *
     * Null in tests, for the same reason as [traceFor]: there is no device to write to. A run works
     * without it and simply leaves no tally behind.
     */
    private val usageStore: ModelUsageStore? = null,
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
                            promptTokens = harvest.usage.promptTokens,
                            completionTokens = harvest.usage.completionTokens,
                            totalTokens = harvest.usage.totalTokens,
                            unreportedTokenRequests = harvest.unreportedRequests,
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

        /** What the run has spent so far, as the provider reported it request by request. */
        var usage = TokenUsage.NONE

        /** Requests the provider reported nothing for, so [usage] is known to be short. */
        var unreportedRequests = 0

        fun add(answer: CompletionResponse) {
            if (answer.usage == null) unreportedRequests += 1 else usage += answer.usage
        }
    }

    /** One answer from the provider, with what it cost. Null usage means it did not say. */
    private class CompletionResponse(val body: String, val usage: TokenUsage?)

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
        val completion = executeCompletion(request.requestId, body, config, appPreferences, credential)
        trace?.record(label, body, completion.body)
        harvest.requestCount += 1
        harvest.add(completion)
        val answer = ChunkAnswer()
        val payload = runCatching { JSONObject(stripCodeFence(contentOf(completion.body))) }.getOrNull()
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
            val completion = executeCompletion(request.requestId, body, config, appPreferences, credential)
            trace?.record("consolidation", body, completion.body)
            harvest.add(completion)
            runCatching { JSONObject(stripCodeFence(contentOf(completion.body))) }.getOrElse {
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

    /**
     * Sends one body and hands back the answer with what it cost.
     *
     * Every request in the app passes through here - a chunk, a consolidation, an Ask AI - which is
     * why the token tally is written here rather than at each caller: a path added later is counted
     * without anyone remembering to count it.
     */
    private suspend fun executeCompletion(
        requestId: String,
        body: JSONObject,
        config: CloudConfiguration,
        appPreferences: AppPreferences,
        credential: CharArray,
    ): CompletionResponse {
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
            val usage = parseUsage(response)
            // The model the body actually named, not the configured one: Ask AI sends its own, and
            // OpenRouter's online suffix is taken off before sending.
            usageStore?.record(config.provider, body.optString("model"), usage)
            return CompletionResponse(response, usage)
        } finally {
            activeConnections.remove(requestId, connection)
            connection.disconnect()
        }
    }

    /**
     * Asks one question and hands back what the model said, unparsed.
     *
     * Deliberately does not touch [promptStore], the wording rules, or the prompt version: the
     * caller supplies its own prompt, so nothing an analysis is configured with can leak into an
     * opinion and nothing here can change what a run sends.
     */
    override suspend fun ask(request: OpinionRequest): String = withContext(Dispatchers.IO) {
        val config = configuration()
        require(config.endpoint.startsWith("https://")) { "Cloud endpoint must use HTTPS." }
        require(request.model.isNotBlank()) { "Choose a model for Ask AI in Settings." }
        val credential = credentialStore.read(config.provider)
            ?: error("No credential is saved for ${config.provider.displayName}.")
        try {
            val deep = request.deepSearch &&
                request.search &&
                config.provider == CloudProvider.QWEN
            val answer = try {
                executeCompletion(
                    request.requestId, opinionBody(request, config, deep), config,
                    preferences(), credential,
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                // The deep strategy is the one key here a model may not accept, and a rejected key
                // fails the whole request rather than only the search. Asked once more without it
                // rather than left to a user who cannot tell a bad key from an unsupported one.
                if (!deep || !failure.mentionsSearch()) throw failure
                executeCompletion(
                    request.requestId, opinionBody(request, config, deepSearch = false), config,
                    preferences(), credential,
                )
            }
            contentOf(answer.body)
        } finally {
            credential.fill('\u0000')
            activeConnections.remove(request.requestId)?.disconnect()
        }
    }

    /**
     * One opinion request, built for whichever provider is configured.
     *
     * Built by a function rather than inline because it is now built twice: once as asked for, and
     * once again without the deep-search strategy where the model would not take it.
     */
    private fun opinionBody(
        request: OpinionRequest,
        config: CloudConfiguration,
        deepSearch: Boolean,
    ): JSONObject = JSONObject().apply {
        put("model", searchModel(request, config.provider))
        // Warmer than the 0.0 an extraction pins. Reading a price off a card has one right
        // answer; a view on a stock does not, and at zero the same question returns the
        // same cautious paragraph whatever is asked about.
        put("temperature", OPINION_TEMPERATURE)
        put("messages", JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", request.systemPrompt))
            put(JSONObject().put("role", "user").put("content", request.question))
        })
        put("response_format", JSONObject().put("type", "json_object"))
        // Each provider is asked in its own dialect and only in its own dialect. An unknown key is
        // rejected outright by some OpenAI-compatible gateways, which fails the request rather
        // than only the search - so nothing here is sent on the chance that it might be read.
        if (!request.search) return@apply
        when (config.provider) {
            CloudProvider.QWEN -> {
                put("enable_search", true)
                if (deepSearch) {
                    put(
                        "search_options",
                        JSONObject().put("search_strategy", DEEP_SEARCH_STRATEGY),
                    )
                }
            }
            // The web plugin rather than the ONLINE_SUFFIX the model id used to carry. Both attach
            // a search; only this one takes a result count and a preamble, which are the two
            // things that decide whether the search is worth what it costs.
            CloudProvider.OPENROUTER -> put(
                "plugins",
                JSONArray().put(
                    JSONObject().apply {
                        put("id", "web")
                        put(
                            "max_results",
                            request.searchResults.coerceIn(MIN_SEARCH_RESULTS, MAX_SEARCH_RESULTS),
                        )
                        request.searchPrompt
                            ?.takeIf(String::isNotBlank)
                            ?.let { put("search_prompt", it) }
                    },
                ),
            )
            else -> Unit
        }
    }

    /** Whether a failure is the provider refusing the search keys rather than the request. */
    private fun Exception.mentionsSearch(): Boolean =
        message?.contains("search", ignoreCase = true) == true

    /**
     * OpenRouter used to read the online suffix off the model id; it now reads the web plugin.
     *
     * The suffix is taken back off where the user typed it themselves. Left on, it would ask
     * OpenRouter for a second search on top of the plugin's, and bill for both.
     */
    private fun searchModel(request: OpinionRequest, provider: CloudProvider): String = when {
        !request.search -> request.model
        provider != CloudProvider.OPENROUTER -> request.model
        else -> request.model.removeSuffix(ONLINE_SUFFIX)
    }

    override suspend fun cancel(requestId: String): Boolean =
        activeConnections.remove(requestId)?.let {
            it.disconnect()
            true
        } ?: false

    override suspend fun listModels(): List<CloudModelInfo> = withContext(Dispatchers.IO) {
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
            parseModels(response)
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

        /**
         * How much room the opinion request leaves the model.
         *
         * An extraction pins 0.0 because reading a price off a card has one right answer. A view on
         * a stock does not, and at zero every question came back in the same cautious register.
         */
        const val OPINION_TEMPERATURE = 0.4

        /** OpenRouter's older marker for "answer this with a live web search attached". */
        const val ONLINE_SUFFIX = ":online"

        /**
         * Qwen's repeated-search strategy: search, read the pages, search again on what it found.
         *
         * The single pass its default runs is enough to learn that a company exists. It is not
         * enough to find out whether that company published results a fortnight ago.
         */
        const val DEEP_SEARCH_STRATEGY = "agent_max"

        /** Bounds on the result count, so a stored setting can never send a nonsensical one. */
        const val MIN_SEARCH_RESULTS = 1
        const val MAX_SEARCH_RESULTS = 20
    }
}

/**
 * The catalogue a provider answered with, keeping whatever it said about each model.
 *
 * The ids alone used to be kept, which is why the picker could only offer all of them: a name does
 * not say whether a model can see an image, and the run needs one that can. OpenRouter publishes
 * its modalities here and is believed; the rest say nothing, and the id is read instead - see
 * [com.ikverse.egxanalyzer.model.ModelSuitabilityRules].
 */
internal fun parseModels(response: String): List<CloudModelInfo> {
    val envelope = JSONObject(response)
    val models = envelope.optJSONArray("data")
        ?: envelope.optJSONArray("models")
        ?: JSONArray()
    return buildList {
        for (index in 0 until models.length()) {
            when (val item = models.opt(index)) {
                is JSONObject -> {
                    val id = item.optString("id").ifBlank { item.optString("name") }
                    if (id.isNotBlank()) {
                        add(
                            CloudModelInfo(
                                id = id,
                                statedModalities = item.statedModalities(),
                                contextLength = item.optInt("context_length").takeIf { it > 0 },
                            ),
                        )
                    }
                }
                is String -> if (item.isNotBlank()) add(CloudModelInfo(item))
            }
        }
    }
        .distinctBy(CloudModelInfo::id)
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, CloudModelInfo::id))
}

/**
 * What one row said it takes in.
 *
 * OpenRouter states it twice - `input_modalities` as a list, and `modality` as `text+image->text` -
 * and the second is what older rows carry, so both are read.
 */
private fun JSONObject.statedModalities(): Set<ModelModality> {
    val architecture = optJSONObject("architecture") ?: return emptySet()
    architecture.optJSONArray("input_modalities")?.let { listed ->
        val named = (0 until listed.length()).mapNotNull { ModelModality.from(listed.optString(it)) }
        if (named.isNotEmpty()) return named.toSet()
    }
    return architecture.optString("modality")
        .substringBefore("->")
        .split("+")
        .mapNotNull(ModelModality::from)
        .toSet()
}

/**
 * What a request cost, or null where the provider did not say.
 *
 * Null and zero are kept apart on purpose. A provider that reports nothing leaves a run's total
 * short, and a short total that reads as a complete one is worse than no total at all - so the run
 * counts those requests separately and says so. `total_tokens` is derived where it is missing:
 * OpenRouter omits it, and the two halves are always there.
 */
internal fun parseUsage(response: String): TokenUsage? {
    val usage = runCatching { JSONObject(response).optJSONObject("usage") }.getOrNull() ?: return null
    val prompt = usage.firstLong("prompt_tokens", "input_tokens")
    val completion = usage.firstLong("completion_tokens", "output_tokens")
    val total = usage.firstLong("total_tokens").takeIf { it > 0 } ?: (prompt + completion)
    if (prompt == 0L && completion == 0L && total == 0L) return null
    return TokenUsage(promptTokens = prompt, completionTokens = completion, totalTokens = total)
}

private fun JSONObject.firstLong(vararg names: String): Long =
    names.firstNotNullOfOrNull { name -> optLong(name).takeIf { it > 0 } } ?: 0L
