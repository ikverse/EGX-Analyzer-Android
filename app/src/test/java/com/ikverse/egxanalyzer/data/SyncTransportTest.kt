package com.ikverse.egxanalyzer.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The sorting both transports share: which file name is which record, and which copy wins.
 *
 * Every name ends in `.json`, so the order the branches are tried in is what keeps a tombstone or a
 * trade from being read as a report. The Telegram channel and the Drive folder both rely on it.
 */
class SyncTransportTest {

    private val names = listOf(
        "deleted-run-1.json" to "t1",
        "rule-r1-100.json" to "r1",
        "position-AMOC_2026-11-01-200.json" to "p1",
        "settings-Fold-300.json" to "s1",
        "settings-Tablet-250.json" to "s2",
        "prompt-v7.json" to "v7",
        "run-2.json" to "a",
        "run-2-r3.json" to "b",
        "run-3.json" to "c",
    )

    @Test
    fun `every kind of file lands in its own slot, and nothing else reads as a report`() {
        val listing = classifySyncFiles(names)
        assertEquals(mapOf("run-1" to "t1"), listing.tombstones)
        assertEquals(listOf("r1"), listing.ruleRevisions)
        assertEquals(listOf("p1"), listing.positionRevisions)
        assertEquals(setOf("settings-Fold-300.json", "settings-Tablet-250.json"), listing.settingsRevisions.keys)
        assertEquals(1, listing.promptVersions.size)
        assertEquals(setOf("run-2", "run-3"), listing.reports.keys)
        assertEquals(listOf("a" to 0L, "b" to 3L), listing.reports["run-2"])
    }

    @Test
    fun `the first copy seen in a slot is kept, as newest-first history has it`() {
        val listing = classifySyncFiles(listOf("deleted-x.json" to "newer", "deleted-x.json" to "older"))
        assertEquals("newer", listing.tombstones["x"])
    }

    @Test
    fun `the newest correction of a report wins across every folder or channel`() {
        val one = classifySyncFiles(listOf("run-2.json" to "a", "run-2-r3.json" to "b"))
        val two = classifySyncFiles(listOf("run-2-r5.json" to "c", "run-9.json" to "d"))
        val newest = newestReports(listOf(one, two)) { it }
        assertEquals(RemoteReport("c", 5), newest["run-2"])
        assertEquals(RemoteReport("d", 0), newest["run-9"])
    }

    @Test
    fun `the settings file to fetch is the one with the newest stamp`() {
        assertEquals("s1", newestSettings(classifySyncFiles(names)))
        assertEquals(null, newestSettings(classifySyncFiles(listOf("run-1.json" to "a"))))
    }
}
