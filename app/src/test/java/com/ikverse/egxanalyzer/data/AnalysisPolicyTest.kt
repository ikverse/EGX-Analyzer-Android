package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.RuleKind
import com.ikverse.egxanalyzer.model.RuleScope
import com.ikverse.egxanalyzer.model.RuleSlot
import com.ikverse.egxanalyzer.model.WordingRule
import com.ikverse.egxanalyzer.model.AnalysisInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisPolicyTest {

    /** What the app ships with and nothing else: the state a fresh install filters in. */
    private val shipped = RuleSet(emptyList())

    /** The old free-text boxes, expressed as the rules they became. */
    private fun rules(keep: String = "", drop: String = "") = RuleSet(
        listOf(RuleSlot.SOURCE_KEEP to keep, RuleSlot.SOURCE_DROP to drop).flatMap { (slot, raw) ->
            raw.split(",").map(String::trim).filter(String::isNotEmpty).map { phrase ->
                WordingRule(
                    id = "test:${slot.name}:$phrase",
                    slot = slot,
                    kind = if (slot == RuleSlot.SOURCE_KEEP) RuleKind.INCLUDE else RuleKind.EXCLUDE,
                    phrase = phrase,
                    scope = RuleScope.BOTH,
                )
            }
        },
    )
    @Test
    fun `previous recommendations and achieved targets are excluded by source id`() {
        val inputs = listOf(
            AnalysisInput.Text("one", "هذه توصية سابقة تم تحقيق المستهدف"),
            AnalysisInput.Text("two", "شراء COMI عند مستوى الدعم"),
        )
        val result = AnalysisPolicy.filter(inputs, shipped)
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

        val result = AnalysisPolicy.filter(inputs, shipped)

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

        val result = AnalysisPolicy.filter(inputs, shipped)

        assertEquals(listOf("three"), result.accepted.map(AnalysisInput::sourceId))
        assertEquals(listOf("one", "two"), result.excluded.map { it.sourceId })
    }

    @Test
    fun `a source the words do not settle is left for the model`() {
        val inputs = listOf(AnalysisInput.Text("one", "*كريستمارك CRST.CA* توصية شراء"))

        val result = AnalysisPolicy.filter(inputs, shipped)

        assertEquals(inputs, result.accepted)
        assertTrue(result.excluded.isEmpty())
    }

    @Test
    fun `include phrases override built in and custom exclusions`() {
        val input = AnalysisInput.Text("one", "توصية سابقة VIP")
        val result = AnalysisPolicy.filter(listOf(input), rules(keep = "VIP", drop = "توصية"))
        assertEquals(listOf(input), result.accepted)
        assertTrue(result.excluded.isEmpty())
    }

    /**
     * The wording these channels use when a call has already worked.
     *
     * They repost the whole table to announce a hit, so one missed caption is not one wasted image
     * but every row on it read a second time. The list matched `تحقق` and `تحقيق`, which appear in
     * none of 120 saved captions; what they write is `حقق المستهدف`.
     */
    @Test
    fun `an announcement that the target was reached is excluded`() {
        val inputs = listOf(
            AnalysisInput.Text(
                "one",
                "*سهم \"عامر جروب AMER.CA\"من توصياتنا في جلسة \"اليوم\" من ترشيحات T+1 " +
                    "حقق المستهدف الاول و الثاني \"4.83\" بنسبه صعود 3.21%*",
            ),
            AnalysisInput.Text("two", "✅ سهم نهر الخير حققنا نسبة ربح بلغت 21.45% خلال 3 اسابيع فقط"),
            AnalysisInput.Text("three", "*أسهم مرشحة للمتاجرة T+1*"),
        )

        val result = AnalysisPolicy.filter(inputs, shipped)

        assertEquals(listOf("three"), result.accepted.map(AnalysisInput::sourceId))
        assertEquals(listOf("one", "two"), result.excluded.map { it.sourceId })
        assertTrue(result.excluded.all { it.reason == "target_already_hit" })
    }

    @Test
    fun `a live call that merely names a target is kept`() {
        val inputs = listOf(
            AnalysisInput.Text("one", "توصية شراء CRST نطاق 1.92-1.93 يستهدف 2.10 والهدف الأول 2.01"),
        )

        val result = AnalysisPolicy.filter(inputs, shipped)

        assertEquals(inputs, result.accepted)
        assertTrue(result.excluded.isEmpty())
    }
}
