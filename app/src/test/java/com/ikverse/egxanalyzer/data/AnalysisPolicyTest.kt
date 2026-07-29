package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.AnalysisInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisPolicyTest {
    @Test
    fun `previous recommendations and achieved targets are excluded by source id`() {
        val inputs = listOf(
            AnalysisInput.Text("one", "هذه توصية سابقة تم تحقيق المستهدف"),
            AnalysisInput.Text("two", "شراء COMI عند مستوى الدعم"),
        )
        val result = AnalysisPolicy.filter(inputs, "", "")
        assertEquals(listOf("two"), result.accepted.map(AnalysisInput::sourceId))
        assertEquals("one", result.excluded.single().sourceId)
    }

    @Test
    fun `include phrases override built in and custom exclusions`() {
        val input = AnalysisInput.Text("one", "توصية سابقة VIP")
        val result = AnalysisPolicy.filter(
            inputs = listOf(input),
            includePhrases = "VIP",
            excludePhrases = "توصية",
        )
        assertEquals(listOf(input), result.accepted)
        assertTrue(result.excluded.isEmpty())
    }
}
