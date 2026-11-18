package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.AppPreferences
import com.ikverse.egxanalyzer.model.ClaudeInModels
import com.ikverse.egxanalyzer.model.ClaudeInPairing
import com.ikverse.egxanalyzer.model.ClaudeInPlan
import com.ikverse.egxanalyzer.model.CloudConfiguration
import com.ikverse.egxanalyzer.model.CloudModelInfo
import com.ikverse.egxanalyzer.model.CloudProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The repository with ClaudeIn as the provider, against a fake PC: a question goes out as a run
 * with its shape and its search, the answer comes back as the text every caller reads, and the
 * PC's refusals reach the caller in words. Nothing here is sent anywhere.
 */
@RunWith(RobolectricTestRunner::class)
class ClaudeInRepositoryTest {
    private val pairing = ClaudeInPairing(listOf("100.101.102.103"), 8790, "ABCDEFGH23", "AB".repeat(32))

    private class FakeCredentials(var stored: String?) : CredentialStore {
        override fun contains(provider: CloudProvider) = stored != null
        override fun save(provider: CloudProvider, credential: CharArray) {
            stored = String(credential)
        }
        override fun read(provider: CloudProvider) = stored?.toCharArray()
        override fun remove(provider: CloudProvider) {
            stored = null
        }
    }

    private class FakePc(
        var features: List<String> = listOf("images", "output_schema", "detach", "priority"),
        var done: JSONObject = JSONObject().put("ending", "done").put("structured", JSONObject().put("risks", JSONArray().put("thin volume"))),
    ) : ClaudeInConnection {
        val specs = mutableListOf<JSONObject>()
        override suspend fun check(pairing: ClaudeInPairing) =
            JSONObject().put("ok", true).put("features", JSONArray(features))
        override suspend fun run(pairing: ClaudeInPairing, spec: JSONObject, onEvent: (JSONObject) -> Unit): JSONObject {
            specs += spec
            onEvent(JSONObject().put("type", "working").put("what", "Searching the web…"))
            return done
        }
        override suspend fun models(pairing: ClaudeInPairing): List<CloudModelInfo>? = null
    }

    private fun repository(pc: FakePc, credentials: CredentialStore = FakeCredentials(pairing.toJson()), said: MutableList<String?> = mutableListOf(), plans: MutableList<ClaudeInPlan> = mutableListOf()) =
        CloudAnalysisRepository(
            contentResolver = RuntimeEnvironment.getApplication().contentResolver,
            credentialStore = credentials,
            promptStore = PromptStore(RuntimeEnvironment.getApplication().assets),
            configuration = { CloudConfiguration(CloudProvider.CLAUDEIN, "", ClaudeInModels.DEFAULT, hasCredential = true) },
            preferences = { AppPreferences() },
            claudeIn = pc,
            onClaudeInWorking = { said += it },
            onClaudeInPlan = { plans += it },
        )

    private fun question(search: Boolean = true) = OpinionRequest(
        requestId = "opinion-1",
        systemPrompt = "You judge calls.",
        question = "Is COMI a buy?",
        model = "claude-haiku-5-5",
        search = search,
    )

    @Test
    fun aQuestionGoesOutAsAShapedRunAndComesBackAsText() = runBlocking {
        val pc = FakePc()
        val said = mutableListOf<String?>()
        val answer = repository(pc, said = said).ask(question())
        assertEquals("thin volume", JSONObject(answer).getJSONArray("risks").getString(0))
        val spec = pc.specs.single()
        assertEquals("claude-haiku-5-5", spec.getString("model"))
        assertEquals("You judge calls.", spec.getString("system"))
        assertEquals("EGX Analyzer", spec.getString("app"))
        assertEquals(AnswerShape.OPINION.schema().toString(), spec.getJSONObject("output_schema").toString())
        assertEquals(2, spec.getJSONArray("builtin_tools").length())
        assertTrue("Searching the web…" in said)
    }

    @Test
    fun theBriefAsksForItsOwnShapeAndNoSearch() = runBlocking {
        val pc = FakePc(done = JSONObject().put("ending", "done").put("structured", JSONObject().put("summary", "Quiet.")))
        repository(pc).ask(question(search = false).copy(shape = AnswerShape.BRIEF))
        val spec = pc.specs.single()
        assertEquals(AnswerShape.BRIEF.schema().toString(), spec.getJSONObject("output_schema").toString())
        assertEquals(0, spec.getJSONArray("builtin_tools").length())
    }

    @Test
    fun aPlanLimitReachesTheCallerInWordsAndTheFillIsKept() = runBlocking {
        val pc = FakePc(
            done = JSONObject()
                .put("ending", "failed: Claude plan limit reached; it resets at 14:00.")
                .put("error_kind", "rate_limited")
                .put("limits", JSONObject().put("five_hour", 1.0).put("seven_day", 0.4)),
        )
        val plans = mutableListOf<ClaudeInPlan>()
        val said = failureOf { repository(pc, plans = plans).ask(question()) }
        assertEquals("Claude plan limit reached. It resets at 14:00.", said)
        assertEquals(1.0, plans.single().fiveHour!!, 1e-9)
    }

    @Test
    fun aClaudeInThatCannotTakeShapedAnswersIsRefusedBeforeAnythingIsSent() = runBlocking {
        val pc = FakePc(features = listOf("images"))
        val said = failureOf { repository(pc).ask(question()) }
        assertTrue(said, said.contains("needs updating"))
        assertTrue(pc.specs.isEmpty())
    }

    @Test
    fun anUnpairedPhoneIsToldToPair() = runBlocking {
        val said = failureOf { repository(FakePc(), credentials = FakeCredentials(null)).ask(question()) }
        assertTrue(said, said.contains("isn't paired with ClaudeIn"))
    }

    @Test
    fun theModelsAreTheThreeClaudeModelsWhileClaudeInListsNone() = runBlocking {
        val models = repository(FakePc()).listModels().map(CloudModelInfo::id)
        assertEquals(listOf("claude-opus-5-5", "claude-sonnet-5-5", "claude-haiku-5-5"), models)
    }

    private suspend fun failureOf(block: suspend () -> Unit): String {
        try {
            block()
        } catch (e: Exception) {
            return e.message.orEmpty()
        }
        fail("Expected a failure")
        error("unreachable")
    }
}
