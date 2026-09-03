package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.CloudModelInfo
import com.ikverse.egxanalyzer.model.ModelModality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the two `/models` answers and the `usage` block are read as.
 *
 * Qwen returns a name and nothing else; OpenRouter returns what each model actually takes in, which
 * is the only authoritative answer to "can this read a card". Both shapes have to survive, and so
 * does a row from neither of them.
 */
class ModelCatalogParsingTest {

    @Test
    fun `a bare list is read as ids with nothing claimed about them`() {
        val models = parseModels(
            """{"data":[{"id":"qwen3-vl-plus"},{"id":"text-embedding-v4"}]}""",
        )
        assertEquals(listOf("qwen3-vl-plus", "text-embedding-v4"), models.map(CloudModelInfo::id))
        assertTrue(models.all { it.statedModalities.isEmpty() })
    }

    @Test
    fun `OpenRouter's stated modalities are kept`() {
        val models = parseModels(
            """
            {"data":[
              {"id":"openai/gpt-4o","context_length":128000,
               "architecture":{"input_modalities":["text","image","file"],"modality":"text+image->text"}},
              {"id":"openai/gpt-oss-120b","architecture":{"input_modalities":["text"]}}
            ]}
            """.trimIndent(),
        )
        val vision = models.first { it.id == "openai/gpt-4o" }
        assertEquals(setOf(ModelModality.TEXT, ModelModality.IMAGE), vision.statedModalities)
        assertEquals(128_000, vision.contextLength)
        val text = models.first { it.id == "openai/gpt-oss-120b" }
        assertEquals(setOf(ModelModality.TEXT), text.statedModalities)
        assertNull(text.contextLength)
    }

    /** The older row shape, where the modalities are only in the arrow string. */
    @Test
    fun `the arrow form is read where the list is missing`() {
        val models = parseModels(
            """{"data":[{"id":"x/y","architecture":{"modality":"text+image->text"}}]}""",
        )
        assertEquals(setOf(ModelModality.TEXT, ModelModality.IMAGE), models.single().statedModalities)
    }

    @Test
    fun `usage is read whichever names the provider gives it`() {
        val classic = parseUsage("""{"usage":{"prompt_tokens":900,"completion_tokens":100,"total_tokens":1000}}""")
        assertEquals(900L, classic?.promptTokens)
        assertEquals(100L, classic?.completionTokens)
        assertEquals(1000L, classic?.totalTokens)

        val renamed = parseUsage("""{"usage":{"input_tokens":40,"output_tokens":2}}""")
        assertEquals(40L, renamed?.promptTokens)
        // Derived, because the provider that uses these names omits the total.
        assertEquals(42L, renamed?.totalTokens)
    }

    /**
     * The distinction the run's diagnostics rest on.
     *
     * A provider that says nothing is not a run that cost nothing, so this has to come back null
     * and be counted as unreported rather than added in as zero.
     */
    @Test
    fun `an answer with no usage reports none rather than zero`() {
        assertNull(parseUsage("""{"choices":[{"message":{"content":"{}"}}]}"""))
        assertNull(parseUsage("""{"usage":{}}"""))
        assertNull(parseUsage("not json at all"))
    }
}
