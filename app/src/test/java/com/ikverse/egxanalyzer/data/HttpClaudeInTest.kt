package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.ClaudeInOutcome
import com.ikverse.egxanalyzer.model.ClaudeInPairing
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.Collections

/**
 * The transport against a fake ClaudeIn on this machine, over plain HTTP. What it holds is the
 * protocol: the pairing code on every request, the stream read to its `done`, a refusal turned
 * into the owner's words, a detached run picked up again after its stream broke, and every
 * address unreachable being "PC not available". No real ClaudeIn and no Claude run.
 */
class HttpClaudeInTest {
    private lateinit var server: HttpServer
    private val seen = Collections.synchronizedList(mutableListOf<String>())
    private var handler: (HttpExchange) -> Unit = { it.reply(404, "{}") }

    private val port get() = server.address.port
    private fun pairing(addresses: List<String> = listOf("127.0.0.1"), port: Int = this.port) =
        ClaudeInPairing(addresses, port, "ABCDEFGH23", HttpClaudeIn.TEST_FINGERPRINT)

    private val client = HttpClaudeIn(plainHttpForTests = true, reattachForMillis = 3_000, reattachEveryMillis = 50)

    @Before
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            seen += "${exchange.requestMethod} ${exchange.requestURI} ${exchange.requestHeaders.getFirst("Authorization")}"
            try {
                handler(exchange)
            } finally {
                exchange.close()
            }
        }
        server.start()
    }

    @After
    fun stop() = server.stop(0)

    @Test
    fun checkSendsTheCodeAndReadsTheFeatures() = runBlocking {
        handler = { it.reply(200, """{"ok":true,"busy":false,"queue":0,"features":["images","output_schema"]}""") }
        val check = client.check(pairing())
        assertEquals(setOf("images", "output_schema"), check.featureSet())
        assertEquals("GET /check Bearer ABCDEFGH23", seen.single())
    }

    @Test
    fun refusalsBecomeTheOwnersWords() = runBlocking {
        handler = { it.reply(401, """{"error":"Wrong code."}""") }
        assertEquals("ClaudeIn refused this phone's pairing code. Pair again.", failureOf { client.check(pairing()) })
        handler = { it.reply(403, """{"error":"\"app\" doesn't match the pairing."}""") }
        assertEquals(ClaudeInOutcome.WRONG_APP, failureOf { client.check(pairing()) })
        handler = { it.reply(403, """{"error":"This device is switched off."}""") }
        assertTrue(failureOf { client.check(pairing()) }.contains("switched off"))
    }

    @Test
    fun aRunIsReadToItsDoneAndHandsOnWhatItSaysMeanwhile() = runBlocking {
        handler = { exchange ->
            val spec = JSONObject(exchange.requestBody.readBytes().toString(Charsets.UTF_8))
            assertEquals("EGX Analyzer", spec.getString("app"))
            exchange.stream(
                """{"type":"run","id":"r1","seq":1}""",
                """{"type":"working","what":"Waiting for the PC (1 ahead)…","seq":2}""",
                """{"type":"text","delta":"{}","seq":3}""",
                """{"type":"done","ending":"done","answer":"{}","structured":{"a":1},"seq":4}""",
            )
        }
        val said = mutableListOf<String>()
        val done = client.run(pairing(), JSONObject().put("app", "EGX Analyzer")) { said += it.optString("type") }
        assertEquals(1, done.getJSONObject("structured").getInt("a"))
        assertEquals(listOf("working", "text"), said)
    }

    @Test
    fun aDetachedRunIsPickedUpWhereItsStreamBroke() = runBlocking {
        handler = { exchange ->
            val path = exchange.requestURI.toString()
            when {
                path == "/runs" -> exchange.stream(
                    """{"type":"run","id":"r9","seq":1}""",
                    """{"type":"working","what":"Reading","seq":2}""",
                    // The stream ends here with no done: the phone walked out of Wi-Fi.
                )
                path.startsWith("/runs/r9/events?after=2") -> exchange.stream(
                    // A replay may repeat what was seen; it must not be handed on twice.
                    """{"type":"working","what":"Reading","seq":2}""",
                    """{"type":"done","ending":"done","answer":"ok","seq":3}""",
                )
                path == "/runs/r9/stop" -> exchange.reply(200, """{"ok":true}""")
                else -> exchange.reply(404, "{}")
            }
        }
        val said = mutableListOf<String>()
        val done = client.run(pairing(), JSONObject().put("detach", true)) { said += it.optString("what") }
        assertEquals("ok", done.getString("answer"))
        assertEquals(listOf("Reading"), said)
        // The kept answer is released on the PC once it has been read.
        assertTrue(seen.any { it.startsWith("POST /runs/r9/stop") })
    }

    @Test
    fun aRunThatIsNotDetachedFailsWhenItsStreamBreaks() = runBlocking {
        handler = { it.stream("""{"type":"run","id":"r2","seq":1}""") }
        assertTrue(failureOf { client.run(pairing(), JSONObject()) }.contains("stopped answering"))
    }

    @Test
    fun theNextAddressIsTriedWhenOneCannotBeReached() = runBlocking {
        handler = { it.reply(200, """{"ok":true}""") }
        val dead = ServerSocket(0).use { it.localPort }
        // Both addresses on this machine; the server listens on 127.0.0.1 alone, so .2 refuses.
        val unreachableFirst = ClaudeInPairing(listOf("127.0.0.2", "127.0.0.1"), port, "ABCDEFGH23", HttpClaudeIn.TEST_FINGERPRINT)
        client.check(unreachableFirst)
        assertEquals(1, seen.size)
        assertEquals(
            ClaudeInOutcome.PC_NOT_AVAILABLE,
            failureOf { client.check(pairing(port = dead)) },
        )
    }

    private suspend fun failureOf(block: suspend () -> Unit): String {
        try {
            block()
        } catch (e: ClaudeInException) {
            return e.message.orEmpty()
        }
        fail("Expected a ClaudeInException")
        error("unreachable")
    }

    private fun HttpExchange.reply(status: Int, body: String) {
        val bytes = body.toByteArray()
        responseHeaders.add("Content-Type", "application/json")
        sendResponseHeaders(status, bytes.size.toLong())
        responseBody.use { it.write(bytes) }
    }

    private fun HttpExchange.stream(vararg events: String) {
        responseHeaders.add("Content-Type", "text/event-stream")
        sendResponseHeaders(200, 0)
        responseBody.use { out ->
            out.write(": still working\n\n".toByteArray())
            events.forEach { out.write("data: $it\n\n".toByteArray()) }
        }
    }
}
