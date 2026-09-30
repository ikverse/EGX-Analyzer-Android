package com.ikverse.egxanalyzer.data

import android.content.res.AssetManager

/**
 * Supplies the canonical analysis prompt.
 *
 * The prompt is shipped as an asset rather than a string constant so it stays byte-identical to
 * the desktop's `app/ai/prompts/consolidated_recommendation.md`, and so a signed content pack can
 * later replace it without an app release - the same arrangement the desktop uses.
 */
class PromptStore(private val assets: AssetManager) {

    private val cached: String by lazy { read(CONSOLIDATED_PROMPT) }

    private val consolidation: String by lazy { read(CONSOLIDATION_PROMPT) }

    fun consolidatedPrompt(): String = cached

    /**
     * The second pass, which sees the occurrences every extraction request returned but none of the
     * images. Ranking and per-stock summaries need the whole run in view; extraction deliberately
     * does not have it.
     */
    fun consolidationPrompt(): String = consolidation

    private fun read(name: String): String =
        assets.open(name).bufferedReader().use { it.readText() }

    /**
     * The prompt's contract version. The desktop refuses a downloaded prompt whose schema does not
     * match the bundled one; this exposes the same value so Android can apply that rule later.
     */
    fun schemaVersion(): Int? = SCHEMA_MARKER.find(cached.take(512))?.groupValues?.get(1)?.toIntOrNull()

    private companion object {
        const val CONSOLIDATED_PROMPT = "consolidated_recommendation.md"
        const val CONSOLIDATION_PROMPT = "consolidation.md"
        val SCHEMA_MARKER = Regex("""<!--\s*EGX_PROMPT_SCHEMA:\s*(\d+)\s*-->""")
    }
}
