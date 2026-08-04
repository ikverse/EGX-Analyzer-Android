package com.ikverse.egxanalyzer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Syncing reports is a union, not a merge.
 *
 * A saved run never changes after it is written, so two devices can never disagree about one: the
 * only question is who is missing it. That is what makes it safe to upload automatically and to
 * pull without asking what to keep.
 */
class ReportSyncTest {

    private val run = SyncedRun(
        requestId = "0eb9d2ec-3901-4144-a676-597f02804462",
        provider = "QWEN",
        model = "qwen3.7-plus",
        completedAt = "2026-08-02T19:03:31Z",
        payload = """{"requestId":"0eb9d2ec-3901-4144-a676-597f02804462","recommendations":[]}""",
    )

    @Test
    fun `each side is told only what it is missing`() {
        val (upload, download) = syncPlan(local = setOf("a", "b"), remote = setOf("b", "c"))

        assertEquals(setOf("a"), upload)
        assertEquals(setOf("c"), download)
    }

    @Test
    fun `nothing moves when both sides already agree`() {
        val (upload, download) = syncPlan(local = setOf("a", "b"), remote = setOf("b", "a"))

        assertEquals(emptySet<String>(), upload)
        assertEquals(emptySet<String>(), download)
    }

    @Test
    fun `a first device uploads everything and downloads nothing`() {
        val (upload, download) = syncPlan(local = setOf("a", "b"), remote = emptySet())

        assertEquals(setOf("a", "b"), upload)
        assertEquals(emptySet<String>(), download)
    }

    @Test
    fun `a run survives the round trip`() {
        assertEquals(run, SyncedRun.fromDocument(run.toDocument()))
    }

    @Test
    fun `the file name carries the identity`() {
        assertEquals("${run.requestId}.json", run.fileName)
        assertEquals(run.requestId, SyncedRun.requestIdOf(run.fileName))
    }

    /** A chat can hold anything someone dropped in it; only this app's files are ours to read. */
    @Test
    fun `a file that is not ours is ignored rather than guessed at`() {
        assertNull(SyncedRun.requestIdOf("holiday-photo.jpg"))
        assertNull(SyncedRun.requestIdOf(".json"))
        assertNull(SyncedRun.fromDocument("not json at all"))
        assertNull(SyncedRun.fromDocument("""{"requestId":"x"}"""))
    }

    @Test
    fun `what moved is reported in plain words`() {
        assertEquals("Already in sync.", SyncOutcome(0, 0, 4).summary)
        assertEquals("Uploaded 1 analysis.", SyncOutcome(1, 0, 3).summary)
        assertEquals("Downloaded 2 analyses.", SyncOutcome(0, 2, 3).summary)
    }

    /**
     * A delete has to survive the other devices.
     *
     * Removing the file alone is not enough: a device that still holds the report sees it missing
     * from the channel and uploads it back, so the delete undoes itself. The marker is what stops
     * that, and it has to stop the upload as well as the download.
     */
    @Test
    fun `a deleted report is neither downloaded nor uploaded by anyone`() {
        val stillHasIt = syncActions(local = setOf("a"), remote = emptySet(), deleted = setOf("a"))
        assertEquals(emptySet<String>(), stillHasIt.upload)
        assertEquals(setOf("a"), stillHasIt.forget)

        val neverHadIt = syncActions(local = emptySet(), remote = setOf("a"), deleted = setOf("a"))
        assertEquals(emptySet<String>(), neverHadIt.download)
        assertEquals(emptySet<String>(), neverHadIt.forget)
    }

    @Test
    fun `everything not deleted still moves normally`() {
        val actions = syncActions(
            local = setOf("a", "gone"),
            remote = setOf("b", "gone"),
            deleted = setOf("gone"),
        )

        assertEquals(setOf("a"), actions.upload)
        assertEquals(setOf("b"), actions.download)
        assertEquals(setOf("gone"), actions.forget)
    }

    @Test
    fun `a tombstone names the report it buries`() {
        val marker = Tombstone("0eb9d2ec")

        assertEquals("deleted-0eb9d2ec.json", marker.fileName)
        assertEquals("0eb9d2ec", Tombstone.requestIdOf(marker.fileName))
    }

    /** A report file must never read as a tombstone, or a delete would bury the wrong thing. */
    @Test
    fun `only a tombstone reads as one`() {
        assertNull(Tombstone.requestIdOf("0eb9d2ec.json"))
        assertNull(Tombstone.requestIdOf("holiday-photo.jpg"))
        assertNull(Tombstone.requestIdOf("deleted-.json"))
    }
}
