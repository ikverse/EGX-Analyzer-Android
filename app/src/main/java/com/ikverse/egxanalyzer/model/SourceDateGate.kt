package com.ikverse.egxanalyzer.model

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Rejects a recommendation whose source is older than the session it claims.
 *
 * Channels re-post screenshots of old cards, and the model has dated them to the target session
 * with the real date left in `visible_source_date` - a 13 July card counted as a 30 July call. The
 * prompt forbids exactly that and was ignored twice, so the check is arithmetic here rather than an
 * instruction there.
 *
 * The session before the target is allowed, because that is where a genuine call for it is
 * published: cards headed `T+1` or `اسهم مرشحه` are printed during one session for the next. Only
 * dates older than that are rejected, which is what a re-post looks like.
 */
object SourceDateGate {

    fun accepts(visibleSourceDate: String?, targetDate: LocalDate?): Boolean {
        if (targetDate == null) return true
        // An unreadable date is not evidence of staleness; the model's own gate covers the rest.
        val printed = parse(visibleSourceDate) ?: return true
        if (printed > targetDate) return true
        return printed >= previousTradingSession(targetDate)
    }

    /** The exchange trades Sunday to Thursday, so Friday and Saturday are skipped going back. */
    fun previousTradingSession(from: LocalDate): LocalDate {
        var candidate = from.minusDays(1)
        while (candidate.dayOfWeek == DayOfWeek.FRIDAY || candidate.dayOfWeek == DayOfWeek.SATURDAY) {
            candidate = candidate.minusDays(1)
        }
        return candidate
    }

    /**
     * Reads a date as a source printed it.
     *
     * Sources write `13/7/2026`, `29/07/2026`, `30 JULY 2026` and `٢٨ يوليو ٢٠٢٦` interchangeably,
     * so all of them have to be understood; anything else is left unparsed rather than guessed.
     */
    fun parse(value: String?): LocalDate? {
        val text = value?.translateDigits()?.trim().orEmpty()
        if (text.isBlank()) return null

        ISO.find(text)?.let { match ->
            return runCatching {
                LocalDate.of(match.group(1), match.group(2), match.group(3))
            }.getOrNull()
        }
        NUMERIC.find(text)?.let { match ->
            return runCatching {
                LocalDate.of(match.group(3), match.group(2), match.group(1))
            }.getOrNull()
        }

        val words = Regex("[\\p{L}]+|\\d+").findAll(text.lowercase()).map { it.value }.toList()
        val monthAt = words.indexOfFirst { it in MONTHS }
        if (monthAt < 0) return null
        val month = MONTHS.getValue(words[monthAt])
        val day = listOfNotNull(words.getOrNull(monthAt - 1), words.getOrNull(monthAt + 1))
            .mapNotNull(String::toIntOrNull)
            .firstOrNull { it in 1..31 } ?: return null
        val year = words.mapNotNull(String::toIntOrNull).firstOrNull { it in 1900..2999 } ?: return null
        return runCatching { LocalDate.of(year, month, day) }.getOrNull()
    }

    private fun MatchResult.group(index: Int) = groupValues[index].toInt()

    private fun String.translateDigits(): String = map { character ->
        when (character) {
            in '٠'..'٩' -> '0' + (character - '٠')
            in '۰'..'۹' -> '0' + (character - '۰')
            else -> character
        }
    }.joinToString("")

    private val ISO = Regex("""(?<!\d)(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})(?!\d)""")
    private val NUMERIC = Regex("""(?<!\d)(\d{1,2})[-/.](\d{1,2})[-/.](\d{4})(?!\d)""")

    private val MONTHS = mapOf(
        "jan" to 1, "january" to 1, "يناير" to 1,
        "feb" to 2, "february" to 2, "فبراير" to 2,
        "mar" to 3, "march" to 3, "مارس" to 3,
        "apr" to 4, "april" to 4, "ابريل" to 4, "أبريل" to 4,
        "may" to 5, "مايو" to 5,
        "jun" to 6, "june" to 6, "يونيو" to 6, "يونيه" to 6,
        "jul" to 7, "july" to 7, "يوليو" to 7, "يوليه" to 7,
        "aug" to 8, "august" to 8, "اغسطس" to 8, "أغسطس" to 8,
        "sep" to 9, "sept" to 9, "september" to 9, "سبتمبر" to 9,
        "oct" to 10, "october" to 10, "اكتوبر" to 10, "أكتوبر" to 10,
        "nov" to 11, "november" to 11, "نوفمبر" to 11,
        "dec" to 12, "december" to 12, "ديسمبر" to 12,
    )
}
