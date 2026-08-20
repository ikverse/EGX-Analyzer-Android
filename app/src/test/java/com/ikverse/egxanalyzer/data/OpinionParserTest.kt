package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.StockOpinion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The model's answer into something the card can colour.
 *
 * Tolerant about the wrapping, strict about the content. A request that has already been paid for
 * must not be thrown away over three backticks; an answer with no verdict in it must not be given
 * one, because a card that colours an invented verdict green is worse than a card that says the
 * request failed.
 */
class OpinionParserTest {

    private val askedOn = LocalDate.parse("2026-08-20")

    private fun parse(response: String, searched: Boolean = true, windowDays: Int = 15) =
        OpinionParser.parse(
            response,
            model = "qwen-plus",
            askedOn = askedOn,
            searched = searched,
            newsWindowDays = windowDays,
        )

    private val whole = """
        {
          "verdict": "WAIT",
          "horizon": "SHORT",
          "confidence": "MEDIUM",
          "headline": "الفرصة فاتت عند هذه المستويات",
          "outlook": "سهم توزيعات محكوم بسعر اليوريا وسعر الغاز.",
          "on_the_call": { "stance": "OVERTAKEN", "detail": "المستويات كانت معقولة وقت النشر." },
          "unknowns": ["آخر نتائج أعمال معلنة", "أحجام التداول"]
        }
    """.trimIndent()

    @Test
    fun `a whole answer comes back whole`() {
        val opinion = parse(whole)

        assertEquals(StockOpinion.Verdict.WAIT, opinion.verdict)
        assertEquals(StockOpinion.Horizon.SHORT, opinion.horizon)
        assertEquals(StockOpinion.Confidence.MEDIUM, opinion.confidence)
        assertEquals(StockOpinion.Stance.OVERTAKEN, opinion.onTheCall.stance)
        assertEquals(2, opinion.unknowns.size)
        assertEquals("qwen-plus", opinion.model)
        assertEquals(askedOn, opinion.askedOn)
        assertTrue(opinion.searched)
    }

    /** Told to return bare JSON, models return fenced JSON often enough to plan for it. */
    @Test
    fun `a fenced answer is read rather than refused`() {
        val fenced = "```json\n$whole\n```"

        assertEquals(StockOpinion.Verdict.WAIT, parse(fenced).verdict)
    }

    @Test
    fun `prose either side of the object is stepped over`() {
        val chatty = "Here is my read:\n$whole\nHope that helps."

        assertEquals(StockOpinion.Verdict.WAIT, parse(chatty).verdict)
    }

    /**
     * Case and spacing are forgiven because losing a paid answer to an underscore would be an
     * expensive kind of strictness.
     */
    @Test
    fun `a token in the wrong case or spelling still reads`() {
        val loose = whole.replace("\"WAIT\"", "\"buy now\"")

        assertEquals(StockOpinion.Verdict.BUY_NOW, parse(loose).verdict)
    }

    /**
     * A model that has just said AVOID and left the horizon out is answering correctly: there is no
     * holding period for a stock it says not to buy.
     */
    @Test
    fun `a missing horizon reads as neither rather than as short`() {
        val noHorizon = whole.replace("\"horizon\": \"SHORT\",", "")

        assertEquals(StockOpinion.Horizon.NEITHER, parse(noHorizon).horizon)
    }

    /** An answer that never stated its confidence has not earned the middle of the scale. */
    @Test
    fun `a missing confidence falls to low`() {
        val noConfidence = whole.replace("\"confidence\": \"MEDIUM\",", "")

        assertEquals(StockOpinion.Confidence.LOW, parse(noConfidence).confidence)
    }

    @Test
    fun `an unrecognized token is not guessed at`() {
        val invented = whole.replace("\"OVERTAKEN\"", "\"MOSTLY_FINE\"")

        // Falls to the cautious reading rather than to SOUND: an unreadable stance must not come
        // out as an endorsement of the levels.
        assertEquals(StockOpinion.Stance.RISKY, parse(invented).onTheCall.stance)
    }

    @Test
    fun `an answer with no verdict is a failure, not a default`() {
        val gutless = whole.replace("\"verdict\": \"WAIT\",", "")

        val error = assertThrows(IllegalStateException::class.java) { parse(gutless) }
        assertTrue(error.message!!.contains("no verdict"))
    }

    @Test
    fun `an answer with no reading of the stock is a failure`() {
        val empty = whole.replace(
            "\"outlook\": \"سهم توزيعات محكوم بسعر اليوريا وسعر الغاز.\",",
            "\"outlook\": \"\",",
        )

        assertThrows(IllegalStateException::class.java) { parse(empty) }
    }

    @Test
    fun `something that is not JSON at all says so in words the user can act on`() {
        val error = assertThrows(IllegalStateException::class.java) {
            parse("I am unable to help with investment advice.")
        }

        assertTrue(error.message!!.contains("Try again"))
    }

    @Test
    fun `an answer that searched and one that did not are told apart`() {
        assertTrue(!parse(whole, searched = false).searched)
    }

    private val withFindings = """
        {
          "verdict": "BUY_NOW",
          "horizon": "SHORT",
          "confidence": "HIGH",
          "headline": "نتائج قوية وسعر لم يستوعبها بعد",
          "outlook": "شركة أسمدة تتحرك مع سعر اليوريا.",
          "on_the_call": { "stance": "SOUND", "detail": "المستويات معقولة." },
          "news": [
            {
              "headline": "أرباح الربع الثاني تقفز 22%",
              "date": "2026-08-12",
              "source": "مباشر",
              "tone": "BULLISH"
            },
            {
              "headline": "الشركة توقع عقد توريد جديد",
              "date": "2026-08-18",
              "source": "Reuters",
              "tone": "bullish"
            }
          ],
          "catalysts": [
            { "what": "قسيمة التوزيع", "when": "2026-09-14", "source": "إفصاح البورصة" }
          ],
          "risks": ["سيولة ضعيفة", "تعرض لسعر الغاز"],
          "unknowns": ["التقييم"]
        }
    """.trimIndent()

    /**
     * The findings come back as findings rather than as a paragraph.
     *
     * This is the whole reason the contract grew. News melted into prose loses its dates, and a
     * headline without a date is indistinguishable from one a year old - which is exactly how a
     * searched answer used to read.
     */
    @Test
    fun `news catalysts and risks are read back`() {
        val opinion = parse(withFindings)

        assertEquals(2, opinion.news.size)
        assertEquals(1, opinion.catalysts.size)
        assertEquals(2, opinion.risks.size)
        assertEquals("2026-09-14", opinion.catalysts.first().on)
        assertEquals("إفصاح البورصة", opinion.catalysts.first().source)
    }

    /** Newest first, whatever order the model happened to list them in. */
    @Test
    fun `news is ordered by its own dates`() {
        val opinion = parse(withFindings)

        assertEquals("2026-08-18", opinion.news.first().date)
        assertEquals("2026-08-12", opinion.news.last().date)
    }

    /** A tone is a token like every other, so a model shouting it still parses. */
    @Test
    fun `a tone in the wrong case is still a tone`() {
        assertEquals(StockOpinion.Tone.BULLISH, parse(withFindings).news.first().tone)
    }

    /**
     * An item with no headline is nothing; one with no source is still a finding.
     *
     * The line between them is whether anything was actually reported. Dropping a dated item over
     * a missing attribution would be throwing away something the user paid for, silently.
     */
    @Test
    fun `an item with no headline goes and an item with no source stays`() {
        val ragged = """
            {
              "verdict": "WAIT",
              "outlook": "لا جديد.",
              "news": [
                { "date": "2026-08-12", "source": "مباشر", "tone": "BULLISH" },
                { "headline": "خبر بلا مصدر", "date": "2026-08-14" }
              ]
            }
        """.trimIndent()

        val news = parse(ragged).news

        assertEquals(1, news.size)
        assertEquals("خبر بلا مصدر", news.first().headline)
        // Defaulted rather than refused: an unstated tone is neutral, which is what it reads as.
        assertEquals(StockOpinion.Tone.NEUTRAL, news.first().tone)
    }

    /**
     * An answer with nothing found keeps its window, and one that never searched does not.
     *
     * The sheet says "nothing in the last 15 days" from this, and it can only say that honestly
     * about a request that actually looked.
     */
    @Test
    fun `the window it was given is recorded on the answer`() {
        val quiet = """
            { "verdict": "WAIT", "outlook": "لا جديد.", "news": [] }
        """.trimIndent()

        assertEquals(15, parse(quiet).newsWindowDays)
        assertTrue(parse(quiet).news.isEmpty())
    }

    /** An older answer, saved before any of this existed, still parses to a whole opinion. */
    @Test
    fun `an answer with no findings at all is still an answer`() {
        val opinion = parse(whole)

        assertTrue(opinion.news.isEmpty())
        assertTrue(opinion.catalysts.isEmpty())
        assertTrue(opinion.risks.isEmpty())
    }
}
