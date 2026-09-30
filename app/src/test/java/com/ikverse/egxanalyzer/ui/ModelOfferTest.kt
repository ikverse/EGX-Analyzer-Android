package com.ikverse.egxanalyzer.ui

import com.ikverse.egxanalyzer.model.CloudModelInfo
import com.ikverse.egxanalyzer.model.ModelModality
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Which models the picker puts up before anything is typed.
 *
 * The bar is not "known to see" but "not known to be something else": an embedder or a voice model
 * is held back, and a name this build has never heard of is offered. A new Qwen generation whose id
 * dropped the `-vl` marker was invisible under the old rule while the previous one was still
 * listed, which is exactly the failure this guards.
 */
class ModelOfferTest {

    private val catalogue = listOf(
        CloudModelInfo("qwen3.5-vl-plus"),
        CloudModelInfo("qwen3.6-plus"),
        CloudModelInfo("qwen3.7-max"),
        CloudModelInfo("text-embedding-v4"),
        CloudModelInfo("qwen3-coder-plus"),
        CloudModelInfo("cosyvoice-v2"),
        CloudModelInfo("some/text-only", setOf(ModelModality.TEXT)),
    )

    private fun offered(showAll: Boolean = false, chosen: String = "") =
        offeredModels(catalogue, showAll, chosen).map(CloudModelInfo::id)

    @Test
    fun `a name this build never heard of is still offered`() {
        assertEquals(
            listOf("qwen3.5-vl-plus", "qwen3.6-plus", "qwen3.7-max"),
            offered(),
        )
    }

    @Test
    fun `what is known to be something else is held back`() {
        listOf("text-embedding-v4", "qwen3-coder-plus", "cosyvoice-v2", "some/text-only")
            .forEach { assertEquals(it, false, it in offered()) }
    }

    @Test
    fun `the model in force is never filtered away`() {
        assertEquals(true, "qwen3-coder-plus" in offered(chosen = "qwen3-coder-plus"))
    }

    @Test
    fun `show all holds back nothing, in the order the provider gave`() {
        assertEquals(catalogue.map(CloudModelInfo::id), offered(showAll = true))
    }

    @Test
    fun `the order the provider gave is kept`() {
        assertEquals(
            listOf("qwen3.5-vl-plus", "qwen3.6-plus", "qwen3.7-max", "qwen3-coder-plus"),
            offered(chosen = "qwen3-coder-plus"),
        )
    }
}
