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

    /**
     * A caption takes its whole message with it.
     *
     * Exclusion is by source id, and a photo carries the same id as the caption written above it,
     * so ruling the caption out is what keeps the picture from being sent. Android's Uri is stubbed
     * in unit tests, so the photo is stood in for here by a second input sharing its id.
     */
    @Test
    fun `everything sharing an excluded source id goes with it`() {
        val inputs = listOf(
            AnalysisInput.Text("one", "متابعة التوصيات السابقة"),
            AnalysisInput.Text("one", "أسطول لتداول الأوراق المالية"),
            AnalysisInput.Text("two", "اهم الاسهم اليوم"),
        )

        val result = AnalysisPolicy.filter(inputs, "", "")

        assertEquals(listOf("two"), result.accepted.map(AnalysisInput::sourceId))
        assertEquals("one", result.excluded.single().sourceId)
    }

    @Test
    fun `an emoji does not hide a marker`() {
        val inputs = listOf(
            AnalysisInput.Text("one", "متابعة التوصيات السابقة🐎🐎"),
            AnalysisInput.Text("two", "متابعة التوصيات🐎السابقة"),
            AnalysisInput.Text("three", "اهم الاسهم اليوم🐎🐎"),
        )

        val result = AnalysisPolicy.filter(inputs, "", "")

        assertEquals(listOf("three"), result.accepted.map(AnalysisInput::sourceId))
        assertEquals(listOf("one", "two"), result.excluded.map { it.sourceId })
    }

    @Test
    fun `a source the words do not settle is left for the model`() {
        val inputs = listOf(AnalysisInput.Text("one", "*كريستمارك CRST.CA* توصية شراء"))

        val result = AnalysisPolicy.filter(inputs, "", "")

        assertEquals(inputs, result.accepted)
        assertTrue(result.excluded.isEmpty())
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
