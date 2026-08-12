package com.ikverse.egxanalyzer.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Finding one model in a list of hundreds.
 *
 * OpenRouter answers with the whole catalogue, and the ids are punctuated three different ways, so
 * the search cannot ask the reader to reproduce the separators. Every term has to appear; where it
 * appears, and what sits between the terms, does not matter.
 */
class ModelFilterTest {

    private val catalogue = listOf(
        "qwen3.5-omni-plus",
        "qwen3-vl-plus",
        "qwen3-vl:4b",
        "openai/gpt-4o",
        "meta-llama/Llama-3.3-70B-Instruct",
    )

    @Test
    fun `an empty query hides nothing`() {
        assertEquals(catalogue, filterModels(catalogue, ""))
        assertEquals(catalogue, filterModels(catalogue, "   "))
    }

    @Test
    fun `a term is matched anywhere in the id, in any case`() {
        assertEquals(listOf("openai/gpt-4o"), filterModels(catalogue, "GPT"))
        assertEquals(listOf("meta-llama/Llama-3.3-70B-Instruct"), filterModels(catalogue, "instruct"))
    }

    /** The whole point: two words, and the separators between them are the app's problem. */
    @Test
    fun `every term must appear, whatever separates them`() {
        val expected = listOf("qwen3-vl-plus", "qwen3-vl:4b")
        assertEquals(expected, filterModels(catalogue, "qwen vl"))
        assertEquals(expected, filterModels(catalogue, "qwen-vl"))
        assertEquals(expected, filterModels(catalogue, "qwen/vl"))
    }

    /** Pasting the id back in must find it, not filter it out on its own punctuation. */
    @Test
    fun `a full id finds itself`() {
        assertEquals(listOf("openai/gpt-4o"), filterModels(catalogue, "openai/gpt-4o"))
        assertEquals(listOf("qwen3-vl:4b"), filterModels(catalogue, "qwen3-vl:4b"))
    }

    @Test
    fun `a term nothing carries matches nothing`() {
        assertEquals(emptyList<String>(), filterModels(catalogue, "gemini"))
        assertEquals(emptyList<String>(), filterModels(catalogue, "qwen gemini"))
    }

    @Test
    fun `order is left as the provider gave it`() {
        assertEquals(
            listOf("qwen3.5-omni-plus", "qwen3-vl-plus", "qwen3-vl:4b"),
            filterModels(catalogue, "qwen"),
        )
    }
}
