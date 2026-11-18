package com.ikverse.egxanalyzer.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * The pure half of ClaudeIn: what a pairing is, how one is read off ClaudeIn's QR code or typed in,
 * and what a finished run's `done` event means in words. Nothing here touches the network; the
 * transport is `data/ClaudeInClient.kt`. ClaudeIn's own protocol is `ClaudeIn/docs/protocol.md`.
 */

/** The Claude models offered when ClaudeIn does not list its own (it has no `/models` yet). */
object ClaudeInModels {
    const val DEFAULT = "claude-sonnet-5-5"

    val BUILT_IN: List<CloudModelInfo> = listOf(
        "claude-opus-5-5",
        "claude-sonnet-5-5",
        "claude-haiku-5-5",
    ).map { CloudModelInfo(it, statedModalities = setOf(ModelModality.TEXT, ModelModality.IMAGE)) }
}

/**
 * The PC this phone is paired with.
 *
 * Stored whole, as JSON, in the encrypted credential slot for [CloudProvider.CLAUDEIN]: the code is
 * the secret, and the addresses and the pin are what make it usable, so there is nothing to gain
 * by keeping them apart. Device-local, like every credential - never synced, never in a backup.
 */
data class ClaudeInPairing(
    /** Tried in this order. ClaudeIn lists its Tailscale address first: it works at home and away. */
    val addresses: List<String>,
    val port: Int,
    /** The pairing code, normalised: upper case, no spaces or dashes. */
    val code: String,
    /** SHA-256 of the PC's certificate, upper-case hex. The phone trusts this certificate alone. */
    val fingerprint: String,
    /** The PC's Tailscale name, when the QR carried one. */
    val name: String? = null,
) {
    /** The part of the fingerprint a person compares with ClaudeIn's window. */
    val shortFingerprint: String get() = fingerprint.takeLast(6)

    /** One `https://host:port` per address, in order. */
    val bases: List<String> get() = addresses.map { baseUrl(it, port) }

    /** What the card shows: the PC's name, or the first address. */
    val label: String get() = name?.takeIf(String::isNotBlank) ?: addresses.firstOrNull().orEmpty()

    fun toJson(): String = JSONObject().apply {
        put("addresses", JSONArray(addresses))
        put("port", port)
        put("code", code)
        put("fingerprint", fingerprint)
        name?.let { put("name", it) }
    }.toString()

    companion object {
        const val PORT = 8790

        fun fromJson(text: String): ClaudeInPairing? = runCatching {
            val json = JSONObject(text)
            val list = json.getJSONArray("addresses")
            ClaudeInPairing(
                addresses = (0 until list.length()).map(list::getString),
                port = json.getInt("port"),
                code = json.getString("code"),
                fingerprint = json.getString("fingerprint"),
                name = json.optString("name").takeIf(String::isNotBlank),
            ).takeIf { it.addresses.isNotEmpty() }
        }.getOrNull()

        /** An IPv6 address goes in brackets; anything else as it is. */
        fun baseUrl(address: String, port: Int): String {
            val host = address.trim().removePrefix("[").removeSuffix("]")
            return if (host.contains(':')) "https://[$host]:$port" else "https://$host:$port"
        }
    }
}

/** Reading the two ways a pairing reaches the phone. Each failure is a sentence for the owner. */
object ClaudeInPairingInput {
    private val CODE_ALPHABET = Regex("^[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{10}$")
    private val FINGERPRINT = Regex("^[0-9A-F]{64}$")

    /** Case, spaces and dashes don't matter in a code, as ClaudeIn says. */
    fun normaliseCode(code: String): String = code.filterNot { it.isWhitespace() || it == '-' }.uppercase()

    fun isCode(code: String): Boolean = CODE_ALPHABET.matches(normaliseCode(code))

    /**
     * ClaudeIn's pairing QR, exactly as `qr_payload` in ClaudeIn's `windows.py` writes it:
     * `{"claudein": 1, "addresses": [...], "port": 8790, "code": "ABCDE-FGH23", "fingerprint": "...", "name": "..."}`.
     * Fields this app does not know are ignored, so a later ClaudeIn may add some.
     */
    fun fromQr(text: String): ClaudeInPairing {
        val json = runCatching { JSONObject(text.trim()) }.getOrNull()
            ?: throw IllegalArgumentException("That QR code isn't from ClaudeIn.")
        if (!json.has("claudein")) throw IllegalArgumentException("That QR code isn't from ClaudeIn.")
        val list = json.optJSONArray("addresses")
        val addresses = (0 until (list?.length() ?: 0))
            .mapNotNull { list!!.optString(it).trim().takeIf(String::isNotEmpty) }
        if (addresses.isEmpty()) throw IllegalArgumentException("The QR code has no address for the PC. Make a new one in ClaudeIn.")
        val port = json.optInt("port", ClaudeInPairing.PORT)
        if (port !in 1..65535) throw IllegalArgumentException("The QR code's port isn't usable. Make a new one in ClaudeIn.")
        val code = normaliseCode(json.optString("code"))
        if (!CODE_ALPHABET.matches(code)) throw IllegalArgumentException("The QR code's pairing code isn't usable. Make a new one in ClaudeIn.")
        val fingerprint = json.optString("fingerprint").filterNot { it == ':' || it.isWhitespace() }.uppercase()
        if (!FINGERPRINT.matches(fingerprint)) throw IllegalArgumentException("The QR code's certificate fingerprint isn't usable. Make a new one in ClaudeIn.")
        return ClaudeInPairing(
            addresses = addresses,
            port = port,
            code = code,
            fingerprint = fingerprint,
            name = json.optString("name").trim().takeIf(String::isNotEmpty),
        )
    }

    /**
     * A typed address - `100.101.102.103`, `100.101.102.103:8790`, `my-pc.tail1234.ts.net`, or an
     * IPv6 address in brackets - as one host and its port. Null when it is not an address.
     */
    fun parseAddress(address: String): Pair<String, Int>? {
        val a = address.trim().removePrefix("https://").removePrefix("http://").trimEnd('/')
        if (a.isEmpty() || a.any { it.isWhitespace() || it == '/' || it == '@' }) return null
        if (a.startsWith("[")) {
            val close = a.indexOf(']')
            if (close < 0) return null
            val host = a.substring(1, close)
            val rest = a.substring(close + 1)
            val port = if (rest.isEmpty()) ClaudeInPairing.PORT else rest.removePrefix(":").toIntOrNull() ?: return null
            return (host to port).takeIf { host.isNotEmpty() && port in 1..65535 }
        }
        // More than one colon and no brackets: a bare IPv6 address, which carries no port.
        if (a.count { it == ':' } > 1) return a to ClaudeInPairing.PORT
        val host = a.substringBefore(':')
        val port = if (a.contains(':')) a.substringAfter(':').toIntOrNull() ?: return null else ClaudeInPairing.PORT
        return (host to port).takeIf { host.isNotEmpty() && port in 1..65535 }
    }
}

/** How full the Claude plan is, as the last run (or the PC itself) reported it. 0-1 each. */
data class ClaudeInPlan(
    val fiveHour: Double?,
    val sevenDay: Double?,
    /** When it was read, so a screen can say how old it is. */
    val at: Long,
) {
    companion object {
        /** `limits` off a `done` event or `/check`, or null when it carries no figure. */
        fun from(limits: JSONObject?, at: Long): ClaudeInPlan? {
            if (limits == null) return null
            fun share(key: String) = limits.optDouble(key).takeIf { !it.isNaN() }?.coerceIn(0.0, 1.0)
            val five = share("five_hour")
            val seven = share("seven_day")
            return if (five == null && seven == null) null else ClaudeInPlan(five, seven, at)
        }
    }
}

/** What a finished run's `done` event means, in the words the status line uses. */
object ClaudeInOutcome {
    /** The `ending` of a run that answered. */
    const val DONE = "done"

    /**
     * Null for a run that answered; otherwise the sentence to show. ClaudeIn's own `failed: …`
     * wording is already for a person, so it is kept where it names a time or an app; the kinds
     * below are what the app adds on top.
     */
    fun failure(done: JSONObject): String? {
        val ending = done.optString("ending")
        if (ending == DONE) return null
        val reason = ending.removePrefix("failed:").trim().ifBlank { ending }
        return when (done.optString("error_kind")) {
            "rate_limited" -> "Claude plan limit reached. " + resetsPart(reason)
            "cap" -> "Today's ClaudeIn cap for EGX Analyzer is used up. It resets at midnight on the PC."
            "paused" -> "ClaudeIn is paused on the PC."
            "not_allowed" -> "ClaudeIn doesn't allow EGX Analyzer to use this model. Pick another, or allow it in ClaudeIn."
            "format" -> "Claude didn't answer in the requested shape."
            "unavailable" -> "PC not available. $reason".trim()
            "limit" -> "The question was too big for one ClaudeIn run. $reason".trim()
            "service" -> "Claude had a problem. Try again later. $reason".trim()
            else -> when (ending) {
                "stopped" -> "The run was stopped on the PC."
                "declined" -> "Claude declined to answer."
                "rounds" -> "The question was too big for one ClaudeIn run."
                "cut short" -> "Claude's answer was cut short."
                else -> reason.ifBlank { "ClaudeIn couldn't answer." }
            }
        }
    }

    /** Whether trying again soon is pointless: the plan, the cap, the pause and the model only change later. */
    fun isStanding(done: JSONObject): Boolean =
        done.optString("error_kind") in setOf("rate_limited", "cap", "paused", "not_allowed")

    private fun resetsPart(reason: String): String {
        val at = Regex("""resets at (\d{1,2}:\d{2})""").find(reason)?.groupValues?.get(1)
        return if (at != null) "It resets at $at." else "It resets later."
    }

    const val PC_NOT_AVAILABLE = "PC not available."
    const val WRONG_APP = "This pairing is for another app. Pair again and choose EGX Analyzer."
}
