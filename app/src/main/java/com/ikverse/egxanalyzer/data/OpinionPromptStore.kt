package com.ikverse.egxanalyzer.data

import android.content.res.AssetManager

/**
 * Supplies the Ask AI prompt, and only it.
 *
 * Its own store rather than a third method on [PromptStore] so the two can never be confused for
 * one another. The analysis prompt carries a schema the desktop agreed on, a rules anchor
 * [PromptComposer] fills in, and a version history the user approves; this one carries none of
 * that. Sharing a class would eventually mean sharing a rule, and a wording rule about reading a
 * Telegram card has nothing to say about an opinion on a stock.
 *
 * It is shipped as an asset for the one reason that also applies here: a prompt is edited far more
 * often than the code around it, and a string constant would put every wording change through a
 * Kotlin diff.
 */
class OpinionPromptStore(private val assets: AssetManager) {

    private val cached: String by lazy {
        assets.open(OPINION_PROMPT).bufferedReader().use { it.readText() }
    }

    fun opinionPrompt(): String = cached

    private companion object {
        const val OPINION_PROMPT = "stock_opinion.md"
    }
}
