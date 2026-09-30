package com.ikverse.egxanalyzer.model

/**
 * One stable label per chat.
 *
 * Sources decorate their titles with emoji and change them from time to time, so the same channel
 * reaches the app as `إسأل فني`, `إسأل فني📉` and `إسأل فني📉🐎`. Grouping on the raw title split
 * one source's record into three, which is what the Insights ranking is built on.
 *
 * Only the label is folded. A chat is still identified by its Telegram id, because two genuinely
 * different chats can share a name once the emoji come off - a channel and its linked discussion
 * group usually do.
 *
 * Mirrors the desktop's `clean_channel_name` so the two apps report the same source names.
 */
fun cleanChannelName(value: String?, fallback: String = "Unknown chat"): String {
    val stripped = buildString {
        var index = 0
        val text = value.orEmpty()
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            if (!isDecorative(codePoint)) appendCodePoint(codePoint)
            index += Character.charCount(codePoint)
        }
    }
    return stripped
        .replace(WHITESPACE, " ")
        .trim()
        .trim(*TRIM_CHARS)
        .trim()
        .ifBlank { fallback }
}

private fun isDecorative(codePoint: Int): Boolean {
    val type = Character.getType(codePoint)
    return type == Character.OTHER_SYMBOL.toInt() ||
        type == Character.SURROGATE.toInt() ||
        codePoint == 0xFE0F || codePoint == 0x200D || codePoint == 0x20E3 ||
        codePoint in 0x1F000..0x1FAFF ||
        codePoint in 0x1FC00..0x1FFFF ||
        codePoint in 0x2600..0x27BF ||
        codePoint in 0x1F3FB..0x1F3FF ||
        codePoint in 0xE0020..0xE007F
}

private val WHITESPACE = Regex("\\s+")
private val TRIM_CHARS = charArrayOf(' ', '\t', '\r', '\n', '-', '|', '•', '·')
