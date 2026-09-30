package com.ikverse.egxanalyzer.model

/**
 * What one request cost in tokens, as the provider reported it.
 *
 * Every OpenAI-compatible answer carries this and the app used to throw it away, which left the
 * only record of what a run cost on the provider's own billing page. Summed across a run and kept
 * per model, it is the one number that says whether a cheaper model would have done.
 */
data class TokenUsage(
    val promptTokens: Long = 0,
    val completionTokens: Long = 0,
    val totalTokens: Long = 0,
) {
    operator fun plus(other: TokenUsage) = TokenUsage(
        promptTokens = promptTokens + other.promptTokens,
        completionTokens = completionTokens + other.completionTokens,
        totalTokens = totalTokens + other.totalTokens,
    )

    val isEmpty: Boolean get() = promptTokens == 0L && completionTokens == 0L && totalTokens == 0L

    companion object {
        val NONE = TokenUsage()
    }
}

/**
 * A token count short enough to sit on a row.
 *
 * A run is tens of thousands and a month is millions, and neither is worth reading digit by digit.
 * Under ten thousand is printed in full, because that is a figure small enough to mean something
 * exactly.
 */
fun formatTokenCount(value: Long): String = when {
    value < 10_000 -> value.toString()
    value < 1_000_000 -> "${(value / 100).toDouble().div(10).trimZero()}k"
    else -> "${(value / 100_000).toDouble().div(10).trimZero()}M"
}

// Locale.US because the digits sit in English diagnostic lines; the device locale would print
// Arabic-Indic numerals into half a sentence.
private fun Double.trimZero(): String = if (this == toLong().toDouble()) {
    toLong().toString()
} else {
    String.format(java.util.Locale.US, "%.1f", this)
}
