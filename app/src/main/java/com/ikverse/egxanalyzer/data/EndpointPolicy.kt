package com.ikverse.egxanalyzer.data

import java.net.URI

object EndpointPolicy {
    fun validate(value: String): String? {
        val uri = runCatching { URI(value.trim()) }.getOrNull()
            ?: return "Enter a valid cloud endpoint."
        if (uri.scheme != "https") return "The cloud endpoint must use HTTPS."
        if (uri.userInfo != null || uri.host.isNullOrBlank()) return "Enter a valid cloud host."
        val host = uri.host.lowercase()
        if (
            host == "localhost" ||
            host == "0.0.0.0" ||
            host == "::1" ||
            host.startsWith("127.") ||
            host.startsWith("10.") ||
            host.startsWith("192.168.") ||
            host.matches(Regex("""172\.(1[6-9]|2\d|3[01])\..*"""))
        ) {
            return "Local and private-network model endpoints are not supported."
        }
        return null
    }
}
