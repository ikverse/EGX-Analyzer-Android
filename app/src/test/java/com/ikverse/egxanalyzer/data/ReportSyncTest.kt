package com.ikverse.egxanalyzer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Syncing reports is a union over what a run *read*, and newest-wins over what has been corrected.
 *
 * A run's extraction never changes after it is written, so for an untouched report the only
 * question is still who is missing it. What the reader has corrected in it does change, and that is
 * the one thing two devices can disagree about - so the revision decides, and it is in the file
 * name so that deciding costs no downloads.
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
        val actions = syncActions(
            local = mapOf("a" to 0L, "b" to 0L),
            remote = mapOf("b" to 0L, "c" to 0L),
            deleted = emptySet(),
        )

        assertEquals(setOf("a"), actions.upload)
        assertEquals(setOf("c"), actions.download)
    }

    @Test
    fun `nothing moves when both sides already agree`() {
        val actions = syncActions(
            local = mapOf("a" to 0L, "b" to 0L),
            remote = mapOf("b" to 0L, "a" to 0L),
            deleted = emptySet(),
        )

        assertEquals(emptySet<String>(), actions.upload)
        assertEquals(emptySet<String>(), actions.download)
    }

    @Test
    fun `a first device uploads everything and downloads nothing`() {
        val actions = syncActions(
            local = mapOf("a" to 0L, "b" to 0L),
            remote = emptyMap(),
            deleted = emptySet(),
        )

        assertEquals(setOf("a", "b"), actions.upload)
        assertEquals(emptySet<String>(), actions.download)
    }

    /**
     * The whole reason a report carries a revision at all.
     *
     * Both devices hold the report, so the union that used to decide this would move nothing and
     * the correction would live on one phone for ever.
     */
    @Test
    fun `a report both sides hold travels from whichever has the newer correction`() {
        val mineIsNewer = syncActions(
            local = mapOf("a" to 3L),
            remote = mapOf("a" to 1L),
            deleted = emptySet(),
        )
        assertEquals(setOf("a"), mineIsNewer.upload)
        assertEquals(emptySet<String>(), mineIsNewer.download)

        val theirsIsNewer = syncActions(
            local = mapOf("a" to 1L),
            remote = mapOf("a" to 3L),
            deleted = emptySet(),
        )
        assertEquals(emptySet<String>(), theirsIsNewer.upload)
        assertEquals(setOf("a"), theirsIsNewer.download)
    }

    /** The ordinary case, and it has to stay free: nothing has been corrected on either side. */
    @Test
    fun `equal revisions move nothing`() {
        val actions = syncActions(
            local = mapOf("a" to 2L),
            remote = mapOf("a" to 2L),
            deleted = emptySet(),
        )

        assertEquals(emptySet<String>(), actions.upload)
        assertEquals(emptySet<String>(), actions.download)
    }

    @Test
    fun `a corrected report names its revision in the file name`() {
        val corrected = run.copy(editRevision = 4)

        assertEquals("${run.requestId}-r4.json", corrected.fileName)
        assertEquals(run.requestId, SyncedRun.requestIdOf(corrected.fileName))
        assertEquals(4L, SyncedRun.revisionOf(corrected.fileName))
        assertEquals(corrected, SyncedRun.fromDocument(corrected.toDocument()))
    }

    /**
     * Every report already in a channel was uploaded under the plain name and must stay readable.
     *
     * A request id is a UUID and carries plain hyphens, so the suffix has to be recognised by more
     * than a hyphen or every id would lose its last segment.
     */
    @Test
    fun `an uncorrected report keeps the name it has always had`() {
        assertEquals("${run.requestId}.json", run.fileName)
        assertEquals(0L, SyncedRun.revisionOf(run.fileName))
        assertEquals(run.requestId, SyncedRun.requestIdOf(run.fileName))
        assertEquals(0L, SyncedRun.fromDocument(run.toDocument())?.editRevision)
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
        assertEquals("Already in sync", SyncOutcome(0, 0, 4).summary)
        assertEquals("1 run uploaded", SyncOutcome(1, 0, 3).summary)
        assertEquals("2 runs downloaded", SyncOutcome(0, 2, 3).summary)
        assertEquals("1 up, 2 down", SyncOutcome(1, 2, 3).summary)
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
        val stillHasIt = syncActions(
            local = mapOf("a" to 0L),
            remote = emptyMap(),
            deleted = setOf("a"),
        )
        assertEquals(emptySet<String>(), stillHasIt.upload)
        assertEquals(setOf("a"), stillHasIt.forget)

        val neverHadIt = syncActions(
            local = emptyMap(),
            remote = mapOf("a" to 0L),
            deleted = setOf("a"),
        )
        assertEquals(emptySet<String>(), neverHadIt.download)
        assertEquals(emptySet<String>(), neverHadIt.forget)
    }

    /** A delete outranks a correction, or the newer revision would drag a buried report back. */
    @Test
    fun `a delete beats a correction`() {
        val actions = syncActions(
            local = mapOf("gone" to 5L),
            remote = mapOf("gone" to 1L),
            deleted = setOf("gone"),
        )

        assertEquals(emptySet<String>(), actions.upload)
        assertEquals(setOf("gone"), actions.forget)
    }

    @Test
    fun `everything not deleted still moves normally`() {
        val actions = syncActions(
            local = mapOf("a" to 0L, "gone" to 0L),
            remote = mapOf("b" to 0L, "gone" to 0L),
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
