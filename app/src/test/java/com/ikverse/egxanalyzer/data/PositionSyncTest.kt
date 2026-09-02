package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.Position
import com.ikverse.egxanalyzer.model.positionId
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * A trade is a row, not a file.
 *
 * Reports never change once written, so syncing them is a union. A position is edited when a price
 * was mistyped, closed when it is sold and removed when it should not have been recorded - and two
 * devices can do different things to one while both are offline. So what travels is each revision,
 * and the merge decides rather than whoever uploaded last.
 */
class PositionSyncTest {
    private val called = LocalDate.of(2026, 7, 20)

    private fun revision(
        ticker: String = "AMOC",
        entryPrice: Double = 10.0,
        at: Long = 1_000,
        by: String = "phone",
        exitPrice: Double? = null,
        deleted: Boolean = false,
    ) = SyncedPosition(
        position = Position(
            ticker = ticker,
            recommendationDate = called,
            companyArabic = "المصرية",
            channel = "First channel",
            entryPrice = entryPrice,
            entryDate = called,
            exitPrice = exitPrice,
            exitDate = exitPrice?.let { called.plusDays(1) },
            closedManually = exitPrice != null,
            entryLow = 9.8,
            entryHigh = 10.2,
            target1 = 11.0,
            target2 = 12.0,
            stopLoss = 9.0,
            windowSessions = 10,
            openedAt = Instant.parse("2026-07-20T09:00:00Z"),
            updatedAt = at,
            updatedBy = by,
        ),
        deleted = deleted,
    )

    @Test
    fun `keeping a trade open travels, and so does a window set by hand`() {
        val original = revision().let {
            it.copy(position = it.position.copy(keepOpen = true, windowCustom = true, windowSessions = 21))
        }

        val returned = SyncedPosition.fromDocument(original.toDocument())

        assertEquals(original, returned)
        assertTrue(returned!!.position.keepOpen)
        assertTrue(returned.position.windowCustom)
        assertEquals(21, returned.position.windowSessions)
    }

    @Test
    fun `a position written before trades could outlive their deadline reads as an ordinary one`() {
        // What an older device uploads: no keepOpen, no windowCustom. Both are false there, which
        // is what those trades were - the offered window, closing when it ran out.
        val older = JSONObject(revision().toDocument())
            .apply { remove("keepOpen"); remove("windowCustom") }
            .toString()

        val returned = SyncedPosition.fromDocument(older)!!

        assertFalse(returned.position.keepOpen)
        assertFalse(returned.position.windowCustom)
    }

    @Test
    fun `what kind of call a trade was taken on travels with it`() {
        val original = revision().let {
            it.copy(position = it.position.copy(isTPlusOne = true, windowSessions = 2))
        }

        val returned = SyncedPosition.fromDocument(original.toDocument())!!

        assertTrue(returned.position.isTPlusOne)
        assertEquals(original, returned)
    }

    @Test
    fun `a position written before a trade remembered it was a T+1 reads as an ordinary one`() {
        // Two sessions is what a T+1 is offered and also what a reader whose default is two takes
        // on every call, so the absent field cannot be guessed back from the window. False here,
        // and the backfill against this device's own record is what marks it.
        val older = JSONObject(
            revision().let { it.copy(position = it.position.copy(windowSessions = 2)) }.toDocument(),
        ).apply { remove("isTPlusOne") }.toString()

        val returned = SyncedPosition.fromDocument(older)!!

        assertFalse(returned.position.isTPlusOne)
        assertEquals(2, returned.position.windowSessions)
    }

    @Test
    fun `a reason for keeping a trade open travels with it`() {
        val original = revision().let {
            it.copy(position = it.position.copy(keepOpen = true, keepOpenNote = "Holding for T2"))
        }

        val returned = SyncedPosition.fromDocument(original.toDocument())!!

        assertEquals("Holding for T2", returned.position.keepOpenNote)
        assertEquals(original, returned)
    }

    @Test
    fun `a field written by a newer app survives being read and sent back`() {
        // The point of `unknown`: a device that does not understand a field must not delete it by
        // merely handling the trade. Without this, upgrading one phone quietly strips whatever the
        // other one knew.
        val fromNewerApp = JSONObject(revision().toDocument())
            .put("trailingStopPct", 5.5)
            .toString()

        val parsed = SyncedPosition.fromDocument(fromNewerApp)!!
        val resent = JSONObject(parsed.toDocument())

        assertEquals(5.5, resent.getDouble("trailingStopPct"), 0.0001)
    }

    @Test
    fun `a revision survives the round trip`() {
        val original = revision()

        assertEquals(original, SyncedPosition.fromDocument(original.toDocument()))
    }

    @Test
    fun `a closed position survives the round trip with its own prices`() {
        val sold = revision(exitPrice = 11.5)

        val back = SyncedPosition.fromDocument(sold.toDocument())

        assertEquals(sold, back)
        assertEquals(11.5, back!!.position.exitPrice!!, 0.001)
        assertTrue(back.position.closedManually)
    }

    /** A call the source never priced fully still travels; a missing level is not a zero. */
    @Test
    fun `absent levels come back absent rather than as zero`() {
        val sparse = SyncedPosition(
            position = Position(
                ticker = "COMI",
                recommendationDate = called,
                entryPrice = 5.0,
                entryDate = called,
                target1 = null,
                target2 = null,
                stopLoss = null,
                windowSessions = 5,
            ),
            deleted = false,
        )

        val back = SyncedPosition.fromDocument(sparse.toDocument())!!.position

        assertNull(back.target1)
        assertNull(back.stopLoss)
        assertNull(back.exitPrice)
        assertNull(back.companyArabic)
    }

    @Test
    fun `the file name carries the position and the revision`() {
        val name = revision(at = 42).fileName

        // The '@' the id uses is not a file-name character, so it is folded; the id inside the
        // document is the truth either way.
        assertEquals("position-AMOC_2026-07-20-42.json", name)
        assertEquals("AMOC_2026-07-20", SyncedPosition.positionIdOf(name))
    }

    /** A channel holds whatever anyone dropped in it; only this app's position files are ours. */
    @Test
    fun `a file that is not a position is ignored rather than guessed at`() {
        assertNull(SyncedPosition.positionIdOf("rule-rule-1-42.json"))
        assertNull(SyncedPosition.positionIdOf("deleted-abc.json"))
        assertNull(SyncedPosition.positionIdOf("abc-123.json"))
        assertNull(SyncedPosition.fromDocument("not json"))
        assertNull(SyncedPosition.fromDocument(JSONObject().put("id", "").toString()))
    }

    @Test
    fun `the same trade recorded on two devices is one position, not two`() {
        // The whole reason the id is derived from the call: a random one would have made these two
        // holdings that could never be reconciled.
        val phone = revision(entryPrice = 10.0, at = 1_000, by = "phone")
        val tablet = revision(entryPrice = 10.4, at = 2_000, by = "tablet")

        val merged = mergePositions(listOf(phone, tablet))

        assertEquals(1, merged.size)
        assertEquals(positionId("AMOC", called), merged.single().position.id)
        // The later edit stands.
        assertEquals(10.4, merged.single().position.entryPrice, 0.001)
    }

    @Test
    fun `two devices editing in the same millisecond still agree`() {
        val phone = revision(entryPrice = 10.0, at = 5_000, by = "phone")
        val tablet = revision(entryPrice = 10.4, at = 5_000, by = "tablet")

        // Whichever order they arrive in, the answer is the same - which is the only thing that
        // matters when neither device can see the other.
        assertEquals(
            mergePositions(listOf(phone, tablet)),
            mergePositions(listOf(tablet, phone)),
        )
        assertEquals("tablet", mergePositions(listOf(phone, tablet)).single().position.updatedBy)
    }

    @Test
    fun `a delete is a revision, so a later edit overtakes it and an earlier one cannot`() {
        val removed = revision(at = 2_000, deleted = true)
        val older = revision(entryPrice = 10.4, at = 1_000)
        val newer = revision(entryPrice = 10.6, at = 3_000)

        // An edit made before the delete does not bring the position back.
        assertTrue(mergePositions(listOf(older, removed)).single().deleted)
        // One made after it does, which is what makes a delete correctable rather than final.
        assertFalse(mergePositions(listOf(removed, newer)).single().deleted)
    }

    @Test
    fun `only what this device knows better is uploaded`() {
        val mine = listOf(
            revision(ticker = "AMOC", at = 3_000),
            revision(ticker = "COMI", at = 1_000),
            revision(ticker = "SWDY", at = 1_000),
        )
        val theirs = listOf(
            revision(ticker = "AMOC", at = 1_000),
            revision(ticker = "COMI", at = 5_000),
        )

        val toUpload = positionsToUpload(mine, theirs).map { it.position.ticker }

        // AMOC because this device edited it later, SWDY because the channel has never seen it.
        assertEquals(listOf("AMOC", "SWDY"), toUpload)
    }

    @Test
    fun `fields a newer app added survive an older one reading and writing them back`() {
        val document = JSONObject(revision().toDocument())
            .put("quantity", 500)
            .toString()

        val back = SyncedPosition.fromDocument(document)!!

        // Not understood, not dropped: an older device must not strip what a newer one recorded.
        assertEquals(500, JSONObject(back.unknown).getInt("quantity"))
        assertEquals(500, JSONObject(back.toDocument()).getInt("quantity"))
    }
}
