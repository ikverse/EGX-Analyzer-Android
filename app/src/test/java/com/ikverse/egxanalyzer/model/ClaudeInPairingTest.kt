package com.ikverse.egxanalyzer.model

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Reading ClaudeIn's QR code and a typed address, and what a run's ending means in words. */
class ClaudeInPairingTest {
    private val fingerprint = "AB".repeat(31) + "C0FFEE".takeLast(2)

    /** Exactly what `qr_payload` in ClaudeIn's windows.py writes, Tailscale address first. */
    private fun qr(
        addresses: String = """["100.101.102.103","192.168.1.20"]""",
        code: String = "ABCDE-FGH23",
        fp: String = fingerprint,
        extra: String = "",
    ) = """{"claudein":1,"addresses":$addresses,"port":8790,"code":"$code","fingerprint":"$fp","name":"my-pc"$extra}"""

    @Test
    fun readsClaudeInsOwnQrPayload() {
        val pairing = ClaudeInPairingInput.fromQr(qr())
        assertEquals(listOf("100.101.102.103", "192.168.1.20"), pairing.addresses)
        assertEquals(8790, pairing.port)
        assertEquals("ABCDEFGH23", pairing.code)
        assertEquals(fingerprint, pairing.fingerprint)
        assertEquals("my-pc", pairing.label)
        assertEquals(listOf("https://100.101.102.103:8790", "https://192.168.1.20:8790"), pairing.bases)
    }

    @Test
    fun ignoresFieldsItDoesNotKnow() {
        val pairing = ClaudeInPairingInput.fromQr(qr(extra = ""","app":"egx-analyzer","protocol":2"""))
        assertEquals("ABCDEFGH23", pairing.code)
    }

    @Test
    fun refusesWhatIsNotClaudeIns() {
        expectRefusal("not json at all", "isn't from ClaudeIn")
        expectRefusal("""{"addresses":["1.2.3.4"]}""", "isn't from ClaudeIn")
        expectRefusal(qr(addresses = "[]"), "no address")
        expectRefusal(qr(fp = "1234"), "fingerprint")
        expectRefusal(qr(code = "SHORT"), "pairing code")
        // 0, O, 1, I and L are not in ClaudeIn's alphabet.
        expectRefusal(qr(code = "ABCDE-FGH10"), "pairing code")
    }

    @Test
    fun aPairingRoundTripsThroughItsStoredForm() {
        val pairing = ClaudeInPairingInput.fromQr(qr())
        assertEquals(pairing, ClaudeInPairing.fromJson(pairing.toJson()))
        assertNull(ClaudeInPairing.fromJson("{}"))
        assertNull(ClaudeInPairing.fromJson("garbage"))
    }

    @Test
    fun typedAddressesInTheShapesClaudeInShowsThem() {
        assertEquals("100.101.102.103" to 8790, ClaudeInPairingInput.parseAddress("100.101.102.103"))
        assertEquals("100.101.102.103" to 8790, ClaudeInPairingInput.parseAddress(" 100.101.102.103:8790 "))
        assertEquals("my-pc.tail1234.ts.net" to 9000, ClaudeInPairingInput.parseAddress("https://my-pc.tail1234.ts.net:9000/"))
        assertEquals("fd7a:115c:a1e0::1" to 8790, ClaudeInPairingInput.parseAddress("[fd7a:115c:a1e0::1]:8790"))
        assertEquals("fd7a:115c:a1e0::1" to 8790, ClaudeInPairingInput.parseAddress("fd7a:115c:a1e0::1"))
        assertNull(ClaudeInPairingInput.parseAddress(""))
        assertNull(ClaudeInPairingInput.parseAddress("my pc"))
        assertNull(ClaudeInPairingInput.parseAddress("1.2.3.4:99999"))
        assertEquals("https://[fd7a:115c:a1e0::1]:8790", ClaudeInPairing.baseUrl("fd7a:115c:a1e0::1", 8790))
    }

    @Test
    fun codesIgnoreCaseSpacesAndDashes() {
        assertTrue(ClaudeInPairingInput.isCode("abcde-fgh23"))
        assertTrue(ClaudeInPairingInput.isCode("ABCDE FGH23"))
        assertEquals("ABCDEFGH23", ClaudeInPairingInput.normaliseCode(" abcde-fgh23 "))
    }

    @Test
    fun planShareComesOffLimits() {
        val plan = ClaudeInPlan.from(JSONObject("""{"status":"allowed","five_hour":0.34,"seven_day":1.4}"""), 5L)!!
        assertEquals(0.34, plan.fiveHour!!, 1e-9)
        assertEquals(1.0, plan.sevenDay!!, 1e-9)
        assertNull(ClaudeInPlan.from(JSONObject("""{"status":"allowed"}"""), 5L))
        assertNull(ClaudeInPlan.from(null, 5L))
    }

    @Test
    fun everyEndingHasPlainWords() {
        fun done(ending: String, kind: String? = null) = JSONObject().put("ending", ending).apply { kind?.let { put("error_kind", it) } }
        assertNull(ClaudeInOutcome.failure(done("done")))
        assertEquals(
            "Claude plan limit reached. It resets at 14:00.",
            ClaudeInOutcome.failure(done("failed: Claude plan limit reached; it resets at 14:00.", "rate_limited")),
        )
        assertTrue(ClaudeInOutcome.failure(done("failed: x", "cap"))!!.contains("cap"))
        assertEquals("ClaudeIn is paused on the PC.", ClaudeInOutcome.failure(done("failed: ClaudeIn is paused on the PC.", "paused")))
        assertTrue(ClaudeInOutcome.failure(done("failed: x", "not_allowed"))!!.contains("model"))
        assertEquals("Claude didn't answer in the requested shape.", ClaudeInOutcome.failure(done("failed: Claude didn't answer in the requested shape.", "format")))
        assertTrue(ClaudeInOutcome.failure(done("failed: Claude Code is signed out.", "unavailable"))!!.startsWith(ClaudeInOutcome.PC_NOT_AVAILABLE))
        assertEquals("Claude declined to answer.", ClaudeInOutcome.failure(done("declined")))
        assertTrue(ClaudeInOutcome.isStanding(done("failed: x", "rate_limited")))
        assertTrue(!ClaudeInOutcome.isStanding(done("failed: x", "service")))
    }

    private fun expectRefusal(text: String, words: String) {
        try {
            ClaudeInPairingInput.fromQr(text)
            fail("Accepted $text")
        } catch (refused: IllegalArgumentException) {
            assertTrue("'${refused.message}' should mention '$words'", refused.message!!.contains(words))
        }
    }
}
