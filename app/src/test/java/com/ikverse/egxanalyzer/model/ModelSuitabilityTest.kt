package com.ikverse.egxanalyzer.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which models the picker offers, and which it holds back.
 *
 * A run sends screenshots, so a model that cannot see one cannot do the job. What the provider
 * published is believed outright; where it published nothing the name is read, and a name nobody
 * recognises is held back rather than rejected - the difference matters, because a filter that
 * rejected the unrecognised would make every model released after this build unreachable.
 */
class ModelSuitabilityTest {

    private fun suitability(id: String, vararg stated: ModelModality) =
        ModelSuitabilityRules.capabilitiesOf(CloudModelInfo(id, stated.toSet())).suitability

    @Test
    fun `what the provider published wins over the name`() {
        // OpenRouter says this one takes images, whatever its id looks like.
        assertEquals(
            ModelSuitability.SUITABLE,
            suitability("some/unheard-of-model", ModelModality.TEXT, ModelModality.IMAGE),
        )
        // And says this one does not, even though the name would have been read as vision.
        assertEquals(
            ModelSuitability.UNSUITABLE,
            suitability("openai/gpt-4o-text-only", ModelModality.TEXT),
        )
    }

    @Test
    fun `the vision families are offered`() {
        listOf(
            "qwen3-vl-plus",
            "qwen3.5-omni-plus",
            "qwen-vl-max",
            "openai/gpt-4o",
            "openai/gpt-4.1-mini",
            "google/gemini-2.5-flash",
            "anthropic/claude-sonnet-4",
            "mistralai/pixtral-12b",
            "opengvlab/internvl3-78b",
            "meta-llama/llama-4-scout",
        ).forEach {
            assertEquals(it, ModelSuitability.SUITABLE, suitability(it))
        }
    }

    @Test
    fun `what is plainly something else is rejected`() {
        listOf(
            "text-embedding-v4",
            "gte-rerank",
            "cosyvoice-v2",
            "paraformer-realtime-v2",
            "wanx2.1-t2i-turbo",
            "openai/whisper-large-v3",
            "omni-moderation-latest",
            "meta-llama/llama-guard-4",
            "qwen3-coder-plus",
            "qwen-audio-turbo",
        ).forEach {
            assertEquals(it, ModelSuitability.UNSUITABLE, suitability(it))
        }
    }

    /**
     * The rule that keeps this list from ageing badly.
     *
     * An id nobody here has heard of is hidden by the filter and reachable by typing it or by
     * turning the filter off. It is never refused.
     */
    @Test
    fun `an unrecognised name is unknown rather than refused`() {
        assertEquals(ModelSuitability.UNKNOWN, suitability("acme-2026-preview"))
        assertEquals(ModelSuitability.UNKNOWN, suitability("qwen3-max"))
    }

    @Test
    fun `an omni model is known to take audio too`() {
        val omni = ModelSuitabilityRules.capabilitiesOf(CloudModelInfo("qwen3.5-omni-plus"))
        assertTrue(ModelModality.AUDIO in omni.modalities)
        assertEquals("images · audio", omni.inputLabel())

        val vision = ModelSuitabilityRules.capabilitiesOf(CloudModelInfo("qwen3-vl-plus"))
        assertEquals("images", vision.inputLabel())
    }

    @Test
    fun `a model nothing is known about labels nothing`() {
        assertNull(ModelSuitabilityRules.capabilitiesOf(CloudModelInfo("acme-2026")).inputLabel())
    }

    /** Rounded on a row, exact on the screen where a bill is being checked. */
    @Test
    fun `token counts are short enough to sit on a row`() {
        assertEquals("512", formatTokenCount(512))
        assertEquals("9999", formatTokenCount(9_999))
        assertEquals("84.2k", formatTokenCount(84_210))
        assertEquals("128k", formatTokenCount(128_000))
        assertEquals("1.2M", formatTokenCount(1_234_567))
    }
}
