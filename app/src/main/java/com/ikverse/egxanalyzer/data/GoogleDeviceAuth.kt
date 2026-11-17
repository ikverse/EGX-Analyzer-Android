package com.ikverse.egxanalyzer.data

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Signing in to Google with a code, for a device with no Google Play services.
 *
 * The Huawei tablet is the case: `play-services-auth` has nothing to talk to there. A redirect back
 * into the app is not available either - Google no longer offers custom URI schemes to new Android
 * clients - so this is Google's flow for devices that cannot take a redirect: the app asks for a
 * short code, the owner types it at `google.com/device` on any browser and presses Allow, and the
 * app, which has been asking every few seconds, is handed its tokens. Google allows `drive.file`
 * through it.
 *
 * It needs its own OAuth client, of type **TVs and Limited Input devices**, whose id and secret come
 * from the build. Google treats that secret as not confidential - it ships inside every copy - but it
 * is still kept out of this public repository, in `local.properties` and the CI secrets.
 *
 * **What the app holds that the Play services path never did: a refresh token.** It keeps the device
 * signed in between launches, so it is stored the way the provider API key is - encrypted by Android
 * Keystore through [NamedSecretStore] - and never reaches the database, a backup, the sync or
 * diagnostics. Disconnecting revokes it with Google. While the project is in Testing, Google expires
 * it after seven days.
 */
class GoogleDeviceAuth(
    private val secrets: NamedSecretStore,
    private val clientId: String,
    private val clientSecret: String,
) {
    @Volatile
    private var accessToken: String? = null

    @Volatile
    private var expiresAt: Long = 0L

    val configured: Boolean get() = clientId.isNotBlank() && clientSecret.isNotBlank()

    /** Asks Google for a code to show. */
    fun start(): DeviceCode {
        if (!configured) throw IOException("This build has no Google sign-in for this device")
        return parseDeviceCode(post(DEVICE_CODE_URL, deviceCodeBody(clientId, DRIVE_SCOPE)))
    }

    /**
     * Asks once whether the owner has approved [code]. Null while they have not; the access token
     * once they have, with the refresh token stored.
     */
    fun poll(code: DeviceCode): String? {
        val answer = postForAnswer(TOKEN_URL, devicePollBody(clientId, clientSecret, code.deviceCode))
        return when (val outcome = parsePollAnswer(answer.first, answer.second)) {
            is PollOutcome.Waiting -> null
            is PollOutcome.Refused -> throw IOException(outcome.reason)
            is PollOutcome.Granted -> {
                val refresh = outcome.tokens.refreshToken ?: throw IOException("Google sent no lasting sign-in")
                secrets.saveSecret(KEY_REFRESH, refresh.toCharArray())
                remember(outcome.tokens)
                outcome.tokens.accessToken
            }
        }
    }

    /** A current access token, refreshed when it is near expiry or [fresh] is asked for. */
    fun token(fresh: Boolean): String {
        val held = accessToken
        if (!fresh && held != null && System.currentTimeMillis() < expiresAt) return held
        val refresh = secrets.readSecret(KEY_REFRESH)
            ?: throw IOException("Google Drive needs signing in again - connect it in Settings")
        val tokens = try {
            parseTokenResponse(post(TOKEN_URL, refreshBody(clientId, clientSecret, String(refresh))))
        } finally {
            refresh.fill('\u0000')
        }
        remember(tokens)
        return tokens.accessToken
    }

    /** Revokes the lasting sign-in with Google and forgets it here, whether or not Google answered. */
    fun signOut() {
        val refresh = secrets.readSecret(KEY_REFRESH)
        secrets.removeSecret(KEY_REFRESH)
        accessToken = null
        expiresAt = 0L
        refresh?.let {
            runCatching { post(REVOKE_URL, "token=" + encode(String(it))) }
            it.fill('\u0000')
        }
    }

    private fun remember(tokens: TokenResponse) {
        accessToken = tokens.accessToken
        // A minute early, so a token never expires between being handed out and being used.
        expiresAt = System.currentTimeMillis() + (tokens.expiresInSeconds - 60).coerceAtLeast(0) * 1000
    }

    private fun post(address: String, body: String): String {
        val (code, text) = postForAnswer(address, body)
        if (code !in 200..299) {
            val detail = runCatching { JSONObject(text) }.getOrNull()
                ?.let { it.optString("error_description").ifBlank { it.optString("error") } }
            throw IOException("Google refused the sign-in ($code)" + (detail?.takeIf(String::isNotBlank)?.let { ": $it" } ?: ""))
        }
        return text
    }

    /** The status and body of a form POST, whichever the status is. */
    private fun postForAnswer(address: String, body: String): Pair<Int, String> {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        }
        try {
            connection.outputStream.use { it.write(body.toByteArray()) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            return code to (stream?.bufferedReader()?.use { it.readText() } ?: "")
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val KEY_REFRESH = "google_refresh_token"
        const val DEVICE_CODE_URL = "https://oauth2.googleapis.com/device/code"
        const val TOKEN_URL = "https://oauth2.googleapis.com/token"
        const val REVOKE_URL = "https://oauth2.googleapis.com/revoke"
    }
}

/** What Google hands back to show: the code to type, where to type it, and how often to ask. */
data class DeviceCode(
    val deviceCode: String,
    val userCode: String,
    val verificationUrl: String,
    val expiresInSeconds: Long,
    val intervalSeconds: Long,
)

internal fun deviceCodeBody(clientId: String, scope: String): String =
    form("client_id" to clientId, "scope" to scope)

internal fun devicePollBody(clientId: String, clientSecret: String, deviceCode: String): String =
    form(
        "client_id" to clientId,
        "client_secret" to clientSecret,
        "device_code" to deviceCode,
        "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
    )

internal fun refreshBody(clientId: String, clientSecret: String, refreshToken: String): String =
    form(
        "client_id" to clientId,
        "client_secret" to clientSecret,
        "refresh_token" to refreshToken,
        "grant_type" to "refresh_token",
    )

internal fun parseDeviceCode(json: String): DeviceCode {
    val body = JSONObject(json)
    return DeviceCode(
        deviceCode = body.optString("device_code").takeIf(String::isNotBlank)
            ?: throw IOException("Google sent no code"),
        userCode = body.optString("user_code"),
        verificationUrl = body.optString("verification_url").ifBlank { body.optString("verification_uri") }
            .ifBlank { "https://www.google.com/device" },
        expiresInSeconds = body.optLong("expires_in", 1800L),
        intervalSeconds = body.optLong("interval", 5L).coerceAtLeast(1L),
    )
}

internal data class TokenResponse(val accessToken: String, val refreshToken: String?, val expiresInSeconds: Long)

internal fun parseTokenResponse(json: String): TokenResponse {
    val body = JSONObject(json)
    val access = body.optString("access_token").takeIf(String::isNotBlank)
        ?: throw IOException("Google sent no access token")
    return TokenResponse(
        access,
        body.optString("refresh_token").takeIf(String::isNotBlank),
        body.optLong("expires_in", 3600L),
    )
}

/** One answer to "has the owner approved the code yet". */
internal sealed interface PollOutcome {
    /** Not yet, or asked too fast: ask again after the interval. */
    data object Waiting : PollOutcome
    data class Refused(val reason: String) : PollOutcome
    data class Granted(val tokens: TokenResponse) : PollOutcome
}

internal fun parsePollAnswer(status: Int, json: String): PollOutcome {
    if (status in 200..299) return PollOutcome.Granted(parseTokenResponse(json))
    return when (runCatching { JSONObject(json).optString("error") }.getOrDefault("")) {
        "authorization_pending", "slow_down" -> PollOutcome.Waiting
        "access_denied" -> PollOutcome.Refused("Access to Google Drive was not allowed")
        "expired_token" -> PollOutcome.Refused("The code expired - press Connect again")
        else -> PollOutcome.Refused("Google refused the sign-in ($status)")
    }
}

private fun form(vararg pairs: Pair<String, String>): String =
    pairs.joinToString("&") { (key, value) -> "$key=${encode(value)}" }

private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
