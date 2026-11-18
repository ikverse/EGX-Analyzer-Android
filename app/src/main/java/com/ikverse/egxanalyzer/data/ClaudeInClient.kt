package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.ClaudeInOutcome
import com.ikverse.egxanalyzer.model.ClaudeInPairing
import com.ikverse.egxanalyzer.model.CloudModelInfo
import com.ikverse.egxanalyzer.model.ModelModality
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.X509TrustManager
import kotlin.coroutines.coroutineContext

/** ClaudeIn refused, or could not be reached, in a sentence for the owner. */
class ClaudeInException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * The phone's side of ClaudeIn (`ClaudeIn/docs/protocol.md`): Claude Code on the owner's PC, on the
 * owner's Claude plan. HTTPS with a certificate ClaudeIn made itself, trusted by its SHA-256 alone,
 * whatever address it is reached at, and the pairing code with every request.
 *
 * An interface so the repository can be tested against a fake; [HttpClaudeIn] is the real one.
 */
interface ClaudeInConnection {
    /** `/check`: whether the PC is there and takes the code. Throws [ClaudeInException] when not. */
    suspend fun check(pairing: ClaudeInPairing): JSONObject

    /**
     * Asks one question and returns its `done` event. Every other event goes to [onEvent] as it
     * arrives. A run sent with `"detach": true` survives a dropped connection: the stream is picked
     * up again from the last event seen. Cancelling the coroutine stops the run on the PC.
     */
    suspend fun run(pairing: ClaudeInPairing, spec: JSONObject, onEvent: (JSONObject) -> Unit = {}): JSONObject

    /** `/models` where ClaudeIn has it; null where it does not (it is a later ClaudeIn's route). */
    suspend fun models(pairing: ClaudeInPairing): List<CloudModelInfo>?
}

/** What first contact with a PC found: what it says it is, and the certificate it showed. */
data class ClaudeInHello(val hello: JSONObject, val fingerprint: String) {
    /** ClaudeIn itself, or the Kotlin analyst host whose protocol it keeps. */
    val isClaudeIn: Boolean
        get() = hello.optString("service") == "ClaudeIn" || hello.optString("app") == "Signal Lab analyst host"

    val features: Set<String> get() = hello.featureSet()
}

internal fun JSONObject.featureSet(): Set<String> {
    val list = optJSONArray("features") ?: return emptySet()
    return (0 until list.length()).mapTo(mutableSetOf()) { list.optString(it) }
}

class HttpClaudeIn(
    /** Tests run a plain-HTTP server; on the phone this is always false. */
    private val plainHttpForTests: Boolean = false,
    /** How long to keep trying to pick a dropped stream up again, in total. */
    private val reattachForMillis: Long = REATTACH_MILLIS,
    private val reattachEveryMillis: Long = REATTACH_EVERY_MILLIS,
) : ClaudeInConnection {
    /** The address that answered last, per pin, so the next request tries it first. */
    @Volatile
    private var lastGood: Pair<String, String>? = null

    /** First contact: reads the certificate (no pin yet) and what the PC says it is. Needs no code. */
    suspend fun hello(host: String, port: Int): ClaudeInHello = withContext(Dispatchers.IO) {
        val base = base(host, port)
        var seen: String? = null
        val connection = open("$base/hello", pin = null, code = null) { seen = it }
        try {
            val (status, body) = connection.readAll()
            if (status !in 200..299) throw ClaudeInException("Something else answered at $host.")
            val json = runCatching { JSONObject(body) }.getOrNull()
                ?: throw ClaudeInException("Something else answered at $host.")
            val fingerprint = seen ?: if (plainHttpForTests) TEST_FINGERPRINT else throw ClaudeInException("The PC sent no certificate.")
            ClaudeInHello(json, fingerprint)
        } catch (e: ClaudeInException) {
            throw e
        } catch (e: IOException) {
            throw ClaudeInException("Nothing answered at $host. Is ClaudeIn running on the PC, and is this phone on Tailscale or the same Wi-Fi?", e)
        } finally {
            connection.disconnect()
        }
    }

    /** [hello] against a pin: refuses a PC whose certificate is not the one the QR code named. */
    suspend fun helloPinned(base: String, pin: String): ClaudeInHello = withContext(Dispatchers.IO) {
        val connection = open("$base/hello", pin = pin, code = null)
        try {
            val (status, body) = connection.readAll()
            if (status !in 200..299) throw ClaudeInException("Something else answered at $base.")
            ClaudeInHello(JSONObject(body), pin)
        } catch (e: ClaudeInException) {
            throw e
        } catch (e: IOException) {
            throw describe(e)
        } finally {
            connection.disconnect()
        }
    }

    override suspend fun check(pairing: ClaudeInPairing): JSONObject = withContext(Dispatchers.IO) {
        onEachBase(pairing) { base ->
            val connection = open("$base/check", pairing.fingerprint, pairing.code, readTimeoutMs = CHECK_TIMEOUT_MS)
            try {
                val (status, body) = connection.readAll()
                refusal(status, body)?.let { throw it }
                JSONObject(body).takeIf { it.optBoolean("ok") }
                    ?: throw ClaudeInException("The PC did not accept the check.")
            } finally {
                connection.disconnect()
            }
        }
    }

    override suspend fun models(pairing: ClaudeInPairing): List<CloudModelInfo>? = withContext(Dispatchers.IO) {
        onEachBase(pairing) { base ->
            val connection = open("$base/models", pairing.fingerprint, pairing.code, readTimeoutMs = CHECK_TIMEOUT_MS)
            try {
                val (status, body) = connection.readAll()
                if (status == 404) return@onEachBase null
                refusal(status, body)?.let { throw it }
                val list = JSONObject(body).optJSONArray("models") ?: return@onEachBase null
                (0 until list.length()).mapNotNull { index ->
                    list.optJSONObject(index)?.optString("id")?.takeIf(String::isNotBlank)?.let {
                        CloudModelInfo(it, statedModalities = setOf(ModelModality.TEXT, ModelModality.IMAGE))
                    }
                }
            } finally {
                connection.disconnect()
            }
        }
    }

    override suspend fun run(
        pairing: ClaudeInPairing,
        spec: JSONObject,
        onEvent: (JSONObject) -> Unit,
    ): JSONObject {
        val detached = spec.optBoolean("detach")
        val progress = StreamProgress()
        var connection: HttpURLConnection? = null
        try {
            val done = withContext(Dispatchers.IO) {
                onEachBase(pairing) { base ->
                    val c = open("$base/runs", pairing.fingerprint, pairing.code, readTimeoutMs = STREAM_READ_TIMEOUT_MS)
                    connection = c
                    c.requestMethod = "POST"
                    c.doOutput = true
                    c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    c.outputStream.use { it.write(spec.toString().toByteArray(Charsets.UTF_8)) }
                    val status = c.responseCode
                    if (status !in 200..299) {
                        val body = (c.errorStream ?: c.inputStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
                        c.disconnect()
                        throw refusal(status, body) ?: ClaudeInException("ClaudeIn answered $status.")
                    }
                    progress.base = base
                    // From here on a failure is not "this address didn't answer" but "the stream
                    // broke", which the address loop must not retry: the run is already on the PC.
                    try {
                        c.inputStream.bufferedReader(Charsets.UTF_8).use { reader -> readStream(reader, progress, onEvent) }
                    } catch (broken: IOException) {
                        throw StreamBroken(broken)
                    } finally {
                        c.disconnect()
                    }
                }
            } ?: reattach(pairing, detached, progress, onEvent, cause = null)
            if (detached) progress.runId?.let { release(pairing, progress.base, it) }
            return done
        } catch (broken: StreamBroken) {
            val done = reattach(pairing, detached, progress, onEvent, broken.cause)
            if (detached) progress.runId?.let { release(pairing, progress.base, it) }
            return done
        } catch (cancelled: CancellationException) {
            connection?.disconnect()
            progress.runId?.let { id ->
                withContext(NonCancellable + Dispatchers.IO) { runCatching { stop(pairing, progress.base, id) } }
            }
            throw cancelled
        }
    }

    /** The stream ended without a `done` (or broke). A detached run is still on the PC: pick it up. */
    private suspend fun reattach(
        pairing: ClaudeInPairing,
        detached: Boolean,
        progress: StreamProgress,
        onEvent: (JSONObject) -> Unit,
        cause: Throwable?,
    ): JSONObject {
        val runId = progress.runId
        if (!detached || runId == null) {
            throw ClaudeInException("The PC stopped answering in the middle of the run.", cause)
        }
        val until = System.currentTimeMillis() + reattachForMillis
        var last: Throwable? = cause
        while (System.currentTimeMillis() < until) {
            coroutineContext.ensureActive()
            delay(reattachEveryMillis)
            try {
                return withContext(Dispatchers.IO) {
                    onEachBase(pairing) { base ->
                        val c = open(
                            "$base/runs/$runId/events?after=${progress.seq}",
                            pairing.fingerprint, pairing.code, readTimeoutMs = STREAM_READ_TIMEOUT_MS,
                        )
                        try {
                            val status = c.responseCode
                            if (status == 404) {
                                throw ClaudeInException("The PC no longer has this run. ClaudeIn may have restarted; run it again.")
                            }
                            if (status !in 200..299) {
                                val body = c.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                                throw refusal(status, body) ?: ClaudeInException("ClaudeIn answered $status.")
                            }
                            progress.base = base
                            try {
                                c.inputStream.bufferedReader(Charsets.UTF_8).use { readStream(it, progress, onEvent) }
                            } catch (broken: IOException) {
                                throw StreamBroken(broken)
                            }
                        } finally {
                            c.disconnect()
                        }
                    }
                } ?: continue
            } catch (gone: ClaudeInException) {
                if (gone.message?.contains("no longer has this run") == true) throw gone
                last = gone
            } catch (broken: StreamBroken) {
                last = broken.cause
            }
        }
        throw ClaudeInException("Lost touch with the PC during the run, and it didn't come back.", last)
    }

    /**
     * Reads server-sent events until `done`, which it returns; null when the stream simply ended.
     * Comment lines (`: still working`) are keep-alives. Event types this app does not know are
     * passed on and otherwise ignored, as the protocol asks.
     */
    private fun readStream(reader: BufferedReader, progress: StreamProgress, onEvent: (JSONObject) -> Unit): JSONObject? {
        while (true) {
            val line = reader.readLine() ?: return null
            if (!line.startsWith("data:")) continue
            val event = runCatching { JSONObject(line.removePrefix("data:").trim()) }.getOrNull() ?: continue
            val seq = event.optInt("seq", 0)
            // A replay can repeat what was already seen; only what is new is handed on.
            if (seq in 1..progress.seq) continue
            if (seq > 0) progress.seq = seq
            when (event.optString("type")) {
                "run" -> progress.runId = event.optString("id").takeIf(String::isNotBlank)
                "done" -> return event
                else -> onEvent(event)
            }
        }
    }

    /** Frees a detached run's place on the PC once its answer is read. Best effort. */
    private suspend fun release(pairing: ClaudeInPairing, base: String?, runId: String) {
        withContext(NonCancellable + Dispatchers.IO) { runCatching { stop(pairing, base, runId) } }
    }

    private fun stop(pairing: ClaudeInPairing, base: String?, runId: String) {
        val target = base ?: pairing.bases.firstOrNull() ?: return
        val c = open("$target/runs/$runId/stop", pairing.fingerprint, pairing.code, readTimeoutMs = CHECK_TIMEOUT_MS)
        try {
            c.requestMethod = "POST"
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write("{}".toByteArray()) }
            c.responseCode
        } finally {
            c.disconnect()
        }
    }

    /**
     * Runs [block] against each of the pairing's addresses in turn, the last one that answered
     * first, moving on only when an address could not be reached at all. A refusal from the PC is
     * an answer and ends the loop. Every address unreachable is "PC not available".
     */
    private inline fun <T> onEachBase(pairing: ClaudeInPairing, block: (String) -> T): T {
        val bases = bases(pairing)
        var last: IOException? = null
        for (base in bases) {
            try {
                val result = block(base)
                lastGood = pairing.fingerprint to base
                return result
            } catch (e: ClaudeInException) {
                throw e
            } catch (e: StreamBroken) {
                throw e
            } catch (e: IOException) {
                if (e.isCertificateFailure()) throw ClaudeInException("This isn't the PC this phone paired with. Pair again.", e)
                if (!e.isUnreachable()) throw describe(e)
                last = e
            }
        }
        throw ClaudeInException(ClaudeInOutcome.PC_NOT_AVAILABLE, last)
    }

    private fun bases(pairing: ClaudeInPairing): List<String> {
        val all = pairing.addresses.map { base(it, pairing.port) }
        val good = lastGood?.takeIf { it.first == pairing.fingerprint }?.second
        return if (good != null && good in all) listOf(good) + (all - good) else all
    }

    private fun base(host: String, port: Int): String {
        val https = ClaudeInPairing.baseUrl(host, port)
        return if (plainHttpForTests) https.replaceFirst("https://", "http://") else https
    }

    private fun open(
        url: String,
        pin: String?,
        code: String?,
        readTimeoutMs: Int = CHECK_TIMEOUT_MS,
        seen: (String) -> Unit = {},
    ): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        if (connection is HttpsURLConnection) {
            val trust = PinnedTrust(pin, seen)
            val tls = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), SecureRandom()) }
            connection.sslSocketFactory = tls.socketFactory
            // The pinned certificate is the PC's identity; the address it is reached at changes with
            // the network (home Wi-Fi, Tailscale), so the name on it is not checked.
            connection.hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
        }
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = readTimeoutMs
        connection.setRequestProperty("Accept", "application/json, text/event-stream")
        if (code != null) connection.setRequestProperty("Authorization", "Bearer $code")
        return connection
    }

    private fun HttpURLConnection.readAll(): Pair<Int, String> {
        val status = responseCode
        val body = (if (status in 200..299) inputStream else errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        return status to body
    }

    /** ClaudeIn's answer before a stream starts, as a sentence. Null for a success. */
    private fun refusal(status: Int, body: String): ClaudeInException? {
        if (status in 200..299) return null
        val said = runCatching { JSONObject(body).optString("error") }.getOrNull().orEmpty()
        return ClaudeInException(
            when (status) {
                401 -> "ClaudeIn refused this phone's pairing code. Pair again."
                403 -> if (said.contains("match", ignoreCase = true) || said.contains("app", ignoreCase = true)) {
                    ClaudeInOutcome.WRONG_APP
                } else {
                    "This phone is switched off in ClaudeIn. Switch it on in the tray, or pair again."
                }
                429 -> said.ifBlank { "ClaudeIn is refusing this phone for now. Try again in an hour." }
                400 -> "ClaudeIn refused the request: ${said.ifBlank { "it didn't say why" }}"
                else -> said.ifBlank { "ClaudeIn answered $status." }
            },
        )
    }

    private fun describe(e: IOException): ClaudeInException = when {
        e.isCertificateFailure() -> ClaudeInException("This isn't the PC this phone paired with. Pair again.", e)
        e is SocketTimeoutException -> ClaudeInException("The PC stopped answering.", e)
        else -> ClaudeInException(ClaudeInOutcome.PC_NOT_AVAILABLE, e)
    }

    private fun IOException.isUnreachable(): Boolean =
        this is ConnectException || this is UnknownHostException || this is NoRouteToHostException ||
            (this is SocketTimeoutException && message?.contains("connect", ignoreCase = true) == true)

    private fun IOException.isCertificateFailure(): Boolean =
        generateSequence<Throwable>(this) { it.cause }.any { it is CertificateException } ||
            (this is SSLHandshakeException && message?.contains("paired", ignoreCase = true) == true)

    private class StreamProgress {
        var runId: String? = null
        var seq: Int = 0
        var base: String? = null
    }

    /** The stream broke after the run reached the PC; carried apart from "address unreachable". */
    private class StreamBroken(cause: IOException) : IOException(cause.message, cause)

    /** Trusts exactly the certificate whose SHA-256 is [pin]; with no pin, any, and says which. */
    private class PinnedTrust(private val pin: String?, private val seen: (String) -> Unit) : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) =
            throw CertificateException("Not a client.")

        override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
            val fp = fingerprint(chain.firstOrNull() ?: throw CertificateException("No certificate."))
            seen(fp)
            if (pin != null && fp != pin) throw CertificateException("This isn't the PC this phone paired with.")
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    companion object {
        const val CONNECT_TIMEOUT_MS = 5_000
        const val CHECK_TIMEOUT_MS = 8_000

        /** ClaudeIn sends a keep-alive every 5 seconds, so this long with nothing means the stream broke. */
        const val STREAM_READ_TIMEOUT_MS = 30_000

        /** ClaudeIn keeps a finished detached run for an hour; ten minutes covers a walk out of Wi-Fi. */
        const val REATTACH_MILLIS = 10 * 60_000L
        const val REATTACH_EVERY_MILLIS = 5_000L

        const val TEST_FINGERPRINT = "0000000000000000000000000000000000000000000000000000000000000000"

        fun fingerprint(cert: X509Certificate): String =
            MessageDigest.getInstance("SHA-256").digest(cert.encoded).joinToString("") { "%02X".format(it) }
    }
}
