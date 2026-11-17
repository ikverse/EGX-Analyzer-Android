package com.ikverse.egxanalyzer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The parts of the code sign-in that do not touch the network: what is asked and how answers read. */
class GoogleDeviceAuthTest {

    @Test
    fun `the code request asks for drive file alone`() {
        assertEquals(
            "client_id=cid&scope=https%3A%2F%2Fwww.googleapis.com%2Fauth%2Fdrive.file",
            deviceCodeBody("cid", DRIVE_SCOPE),
        )
    }

    @Test
    fun `a code answer is read, and a missing page or interval falls back`() {
        assertEquals(
            DeviceCode("dev", "ABCD-EFGH", "https://www.google.com/device", 1800, 5),
            parseDeviceCode(
                """{"device_code":"dev","user_code":"ABCD-EFGH","verification_url":"https://www.google.com/device","expires_in":1800,"interval":5}""",
            ),
        )
        val sparse = parseDeviceCode("""{"device_code":"dev","user_code":"X"}""")
        assertEquals("https://www.google.com/device", sparse.verificationUrl)
        assertEquals(5L, sparse.intervalSeconds)
    }

    @Test
    fun `waiting and slowing down mean ask again, and the rest end it`() {
        assertEquals(PollOutcome.Waiting, parsePollAnswer(428, """{"error":"authorization_pending"}"""))
        assertEquals(PollOutcome.Waiting, parsePollAnswer(403, """{"error":"slow_down"}"""))
        assertTrue(parsePollAnswer(403, """{"error":"access_denied"}""") is PollOutcome.Refused)
        assertTrue(parsePollAnswer(400, """{"error":"expired_token"}""") is PollOutcome.Refused)
        assertTrue(parsePollAnswer(500, "not json") is PollOutcome.Refused)
    }

    @Test
    fun `an approval carries the tokens`() {
        assertEquals(
            PollOutcome.Granted(TokenResponse("at", "rt", 3599)),
            parsePollAnswer(200, """{"access_token":"at","refresh_token":"rt","expires_in":3599}"""),
        )
    }

    @Test
    fun `the poll and refresh carry the client secret and the right grant`() {
        val poll = devicePollBody("cid", "sec", "dev")
        assertTrue(poll.contains("client_secret=sec"))
        assertTrue(poll.contains("device_code=dev"))
        assertTrue(poll.contains("grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Adevice_code"))
        assertEquals(
            "client_id=cid&client_secret=sec&refresh_token=rt&grant_type=refresh_token",
            refreshBody("cid", "sec", "rt"),
        )
    }
}
