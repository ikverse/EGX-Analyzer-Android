package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.Position
import com.ikverse.egxanalyzer.model.PromptVersion
import com.ikverse.egxanalyzer.model.RestoreOutcome
import com.ikverse.egxanalyzer.model.RuleKind
import com.ikverse.egxanalyzer.model.RuleScope
import com.ikverse.egxanalyzer.model.RuleSlot
import com.ikverse.egxanalyzer.model.WordingRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * What a restore is allowed to do, which is narrower than what a sync does.
 *
 * The sync merge and this one share a comparison - newest `(updatedAt, device)` wins - and part
 * company on deletes. Between two live devices a tombstone has to travel or a delete does not
 * stick; out of a file it must not, because a file is one moment preserved and somebody opens a
 * backup precisely when something has gone missing. The tests below are mostly about that
 * difference, because it is the one an ordinary reading of "merge" would get wrong.
 */
class BackupRestoreTest {
    private val called = LocalDate.of(2026, 7, 20)

    private fun rule(
        id: String = "rule-1",
        phrase: String = "حقق المستهدف",
        at: Long = 1_000,
        by: String = "phone",
        deleted: Boolean = false,
    ) = SyncedRule(
        rule = WordingRule(
            id = id,
            slot = RuleSlot.EXCLUSION_TARGET_HIT,
            kind = RuleKind.EXCLUDE,
            phrase = phrase,
            scope = RuleScope.LOCAL,
            enabled = true,
            updatedAt = at,
            updatedBy = by,
        ),
        deleted = deleted,
    )

    private fun position(
        ticker: String = "AMOC",
        entryPrice: Double = 10.0,
        at: Long = 1_000,
        by: String = "phone",
        deleted: Boolean = false,
    ) = SyncedPosition(
        position = Position(
            ticker = ticker,
            recommendationDate = called,
            companyArabic = "المصرية",
            channel = "First channel",
            entryPrice = entryPrice,
            entryDate = called,
            exitPrice = null,
            exitDate = null,
            closedManually = false,
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

    private fun run(id: String) = SyncedRun(
        requestId = id,
        provider = "QWEN",
        model = "qwen-vl-max",
        completedAt = "2026-07-20T12:00:00Z",
        payload = """{"requestId":"$id"}""",
    )

    private fun promptVersion(id: String) = PromptVersion(
        id = id,
        sequence = 1,
        text = "prompt",
        schemaVersion = 4,
        ruleIds = emptyList(),
        reason = "shipped",
        device = "phone",
        createdAt = 1_000,
    )

    // Rules

    @Test
    fun `a rule the device has never seen is restored`() {
        val taken = rulesToRestore(mine = emptyList(), backup = listOf(rule()))

        assertEquals(listOf("rule-1"), taken.map { it.rule.id })
    }

    @Test
    fun `a newer revision in the backup wins`() {
        val taken = rulesToRestore(
            mine = listOf(rule(at = 1_000, phrase = "old")),
            backup = listOf(rule(at = 2_000, phrase = "new")),
        )

        assertEquals(listOf("new"), taken.map { it.rule.phrase })
    }

    @Test
    fun `an older revision in the backup is left alone`() {
        val taken = rulesToRestore(
            mine = listOf(rule(at = 2_000)),
            backup = listOf(rule(at = 1_000)),
        )

        assertTrue(taken.isEmpty())
    }

    /** The same tie-break the sync uses, so a restore cannot disagree with a sync about one rule. */
    @Test
    fun `two revisions of the same age are separated by the device`() {
        val taken = rulesToRestore(
            mine = listOf(rule(at = 1_000, by = "aaa")),
            backup = listOf(rule(at = 1_000, by = "zzz", phrase = "theirs")),
        )

        assertEquals(listOf("theirs"), taken.map { it.rule.phrase })
    }

    /**
     * The rule this whole file exists for.
     *
     * A restore that could delete is one nobody dares press, and the person pressing it is already
     * missing something. The delete still reaches other devices - it travels through the channel,
     * where both sides are live - it simply does not travel out of a file.
     */
    @Test
    fun `a rule the backup says was deleted is not deleted here`() {
        val taken = rulesToRestore(
            mine = listOf(rule(at = 1_000)),
            backup = listOf(rule(at = 9_000, deleted = true)),
        )

        assertTrue(taken.isEmpty())
    }

    @Test
    fun `a rule deleted in the backup and absent here is not resurrected either`() {
        val taken = rulesToRestore(mine = emptyList(), backup = listOf(rule(deleted = true)))

        assertTrue(taken.isEmpty())
    }

    // Trades

    @Test
    fun `a trade the device is missing is restored`() {
        val taken = positionsToRestore(mine = emptyList(), backup = listOf(position()))

        assertEquals(listOf("AMOC"), taken.map { it.position.ticker })
    }

    @Test
    fun `a trade edited here since the backup keeps the edit`() {
        val taken = positionsToRestore(
            mine = listOf(position(entryPrice = 11.0, at = 5_000)),
            backup = listOf(position(entryPrice = 10.0, at = 1_000)),
        )

        assertTrue(taken.isEmpty())
    }

    @Test
    fun `a trade the backup says was deleted is left standing`() {
        val taken = positionsToRestore(
            mine = listOf(position(at = 1_000)),
            backup = listOf(position(at = 9_000, deleted = true)),
        )

        assertTrue(taken.isEmpty())
    }

    /** The unknown column travels, or an older build erases a newer one's fields by restoring. */
    @Test
    fun `fields this build does not understand come back untouched`() {
        val incoming = position().copy(unknown = """{"trailingStop":9.5}""")

        val taken = positionsToRestore(mine = emptyList(), backup = listOf(incoming))

        assertEquals("""{"trailingStop":9.5}""", taken.single().unknown)
    }

    // Reports

    @Test
    fun `only the reports this device is missing are taken`() {
        val taken = runsToRestore(
            held = setOf("a"),
            buried = emptySet(),
            backup = listOf(run("a"), run("b")),
        )

        assertEquals(listOf("b"), taken.map { it.requestId })
    }

    /**
     * A delete made offline is a decision already taken.
     *
     * Restoring over it would undo it silently - and worse, the tombstone is still queued, so the
     * report would be published as deleted while sitting on disk here.
     */
    @Test
    fun `a report deleted here but not yet published does not come back`() {
        val taken = runsToRestore(
            held = emptySet(),
            buried = setOf("b"),
            backup = listOf(run("a"), run("b")),
        )

        assertEquals(listOf("a"), taken.map { it.requestId })
    }

    // Prompt versions

    @Test
    fun `a prompt version is taken once and never again`() {
        val backup = listOf(promptVersion("hash-1"), promptVersion("hash-2"))

        val first = promptVersionsToRestore(mine = emptySet(), backup = backup)
        val second = promptVersionsToRestore(
            mine = first.map { SyncedPromptVersion.keyFor(it.id) }.toSet(),
            backup = backup,
        )

        assertEquals(listOf("hash-1", "hash-2"), first.map { it.id })
        assertTrue(second.isEmpty())
    }

    /** The channel compares the file-name form, so a restore has to compare the same thing. */
    @Test
    fun `an id needing sanitising still matches what is already held`() {
        val version = promptVersion("hash/with spaces")

        val taken = promptVersionsToRestore(
            mine = setOf(SyncedPromptVersion.keyFor("hash/with spaces")),
            backup = listOf(version),
        )

        assertTrue(taken.isEmpty())
    }

    // What the user is told

    @Test
    fun `a restore that moved nothing says so rather than claiming success`() {
        val outcome = RestoreOutcome(0, 0, 0, 0, settingsAdopted = false)

        assertTrue(outcome.movedNothing)
        assertEquals("Nothing to restore - this device already had it all", outcome.summary)
    }

    @Test
    fun `what came back is named and counted`() {
        val outcome = RestoreOutcome(
            reports = 12,
            rules = 1,
            trades = 3,
            promptVersions = 4,
            settingsAdopted = true,
        )

        assertFalse(outcome.movedNothing)
        assertEquals("12 reports, 3 trades, 1 rule and settings restored", outcome.summary)
    }

    /** Prompt versions alone are still something, or a restore reports nothing and has moved. */
    @Test
    fun `a restore that only carried plumbing does not report itself as empty`() {
        val outcome = RestoreOutcome(0, 0, 0, 2, settingsAdopted = false)

        assertFalse(outcome.movedNothing)
        assertEquals("2 prompt versions restored", outcome.summary)
    }

    @Test
    fun `settings alone are worth a sentence`() {
        val outcome = RestoreOutcome(0, 0, 0, 0, settingsAdopted = true)

        assertEquals("Settings restored", outcome.summary)
    }
}
