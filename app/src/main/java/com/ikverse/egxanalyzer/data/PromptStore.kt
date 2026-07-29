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

    private val cached: String by lazy {
        assets.open(CONSOLIDATED_PROMPT).bufferedReader().use { it.readText() }
    }

    fun consolidatedPrompt(): String = cached

    /**
     * The prompt's contract version. The desktop refuses a downloaded prompt whose schema does not
     * match the bundled one; this exposes the same value so Android can apply that rule later.
     */
    fun schemaVersion(): Int? = SCHEMA_MARKER.find(cached.take(512))?.groupValues?.get(1)?.toIntOrNull()

    private companion object {
        const val CONSOLIDATED_PROMPT = "consolidated_recommendation.md"
        val SCHEMA_MARKER = Regex("""<!--\s*EGX_PROMPT_SCHEMA:\s*(\d+)\s*-->""")
    }
}
