package com.ikverse.egxanalyzer.data

import android.content.Context
import android.net.Uri
import com.ikverse.egxanalyzer.model.AppPreferences
import com.ikverse.egxanalyzer.model.CloudProvider
import com.ikverse.egxanalyzer.model.Position
import com.ikverse.egxanalyzer.model.PromptVersion
import com.ikverse.egxanalyzer.model.RuleKind
import com.ikverse.egxanalyzer.model.RuleScope
import com.ikverse.egxanalyzer.model.RuleSlot
import com.ikverse.egxanalyzer.model.WordingRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.time.Instant
import java.time.LocalDate

/**
 * That the file written on the way out is the record on the way back in.
 *
 * The one failure the rest of the suite cannot see. Every decision a restore makes is tested in
 * `BackupRestoreTest` as ordinary Kotlin, and all of it is worthless if what comes out of the zip
 * is not what went into it - a backup that reads back empty looks exactly like a phone that had
 * nothing on it, and the person finding out is the one who has already lost the original.
 *
 * Robolectric because it needs a real SQLite database, the same reason `LocalDataStoreMigrationTest`
 * runs under it.
 */
@RunWith(RobolectricTestRunner::class)
class BackupRoundTripTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val called = LocalDate.of(2026, 7, 20)

    private val rule = WordingRule(
        id = "rule-1",
        slot = RuleSlot.EXCLUSION_TARGET_HIT,
        kind = RuleKind.EXCLUDE,
        phrase = "حقق المستهدف",
        scope = RuleScope.LOCAL,
        enabled = true,
        updatedAt = 1_000,
        updatedBy = "phone",
    )

    private val position = Position(
        ticker = "AMOC",
        recommendationDate = called,
        companyArabic = "المصرية",
        channel = "First channel",
        entryPrice = 10.0,
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
        updatedAt = 1_000,
        updatedBy = "phone",
    )

    private val promptVersion = PromptVersion(
        id = "hash-1",
        sequence = 1,
        text = "the prompt",
        schemaVersion = 4,
        ruleIds = listOf("rule-1"),
        reason = "shipped",
        device = "phone",
        createdAt = 1_000,
    )

    private val settings = SettingsSnapshot(
        preferences = AppPreferences(defaultTradeWindowSessions = 7),
        provider = CloudProvider.QWEN,
        providers = emptyList(),
        useDefaultPromptOnly = false,
        promptHistory = emptyList(),
        updatedAt = 5_000,
        updatedBy = "phone",
    )

    /** A store holding one of everything a backup is supposed to carry. */
    private fun seededStore(): LocalDataStore = LocalDataStore(context).apply {
        adoptResult("run-1", "QWEN", "qwen-vl-max", "2026-07-20T12:00:00Z", """{"requestId":"run-1"}""")
        saveWordingRule(rule)
        savePosition(position)
        rememberPromptVersion(promptVersion)
        checkpoint()
    }

    private fun backupOf(store: LocalDataStore, settingsDocument: String): File {
        val file = File(context.cacheDir, "egx-backup-test.zip")
        file.outputStream().use { out ->
            writeBackup(out, store.databaseFile(), settingsDocument, "phone", store::checkpoint)
        }
        return file
    }

    @Test
    fun `everything written into a backup comes back out of it`() {
        val store = seededStore()

        val file = backupOf(store, settings.toDocument())
        store.close()
        val record = readBackup(context, Uri.fromFile(file))

        assertEquals(listOf("run-1"), record.runs.map { it.requestId })
        assertEquals(listOf("rule-1"), record.rules.map { it.rule.id })
        assertEquals(listOf("AMOC"), record.positions.map { it.position.ticker })
        assertEquals(listOf("hash-1"), record.promptVersions.map { it.id })
        assertNotNull(record.settings)
    }

    /**
     * The reason a backup is a zip and not the database on its own.
     *
     * Settings live in preferences, so a restore from a database alone comes back holding every
     * report and scoring them against a trade window nobody picked.
     */
    @Test
    fun `settings travel in the backup and survive the round trip`() {
        val store = seededStore()

        val file = backupOf(store, settings.toDocument())
        store.close()
        val restored = readBackup(context, Uri.fromFile(file)).settings

        assertEquals(7, restored?.preferences?.defaultTradeWindowSessions)
        assertEquals(5_000L, restored?.updatedAt)
    }

    /** A report's payload is copied as bytes, not re-encoded through this build's understanding. */
    @Test
    fun `a report comes back byte for byte`() {
        val store = seededStore()

        val file = backupOf(store, settings.toDocument())
        store.close()
        val run = readBackup(context, Uri.fromFile(file)).runs.single()

        assertEquals("""{"requestId":"run-1"}""", run.payload)
        assertEquals("qwen-vl-max", run.model)
    }

    /**
     * The diagnostics copy people already have on their phones and computers.
     *
     * `Save diagnostics` has written a bare database since the app was released, and refusing those
     * would strand the only copy some users hold. It carries no settings, which is the one thing a
     * restore from one cannot bring back.
     */
    @Test
    fun `a bare diagnostics database restores its record and admits it has no settings`() {
        val store = seededStore()
        val copy = File(context.cacheDir, "egx-diagnostics-test.db")
        store.databaseFile().inputStream().use { input -> copy.outputStream().use { input.copyTo(it) } }
        store.close()

        val record = readBackup(context, Uri.fromFile(copy))

        assertEquals(listOf("run-1"), record.runs.map { it.requestId })
        assertNull(record.settings)
    }

    /** By content, not by name: a file round a cloud folder and back can arrive called anything. */
    @Test
    fun `something that is not a backup is refused rather than half read`() {
        val notABackup = File(context.cacheDir, "egx-backup-2026-08-25.zip")
        notABackup.writeText("this is a shopping list")

        assertThrows(NotABackupException::class.java) {
            readBackup(context, Uri.fromFile(notABackup))
        }
    }

    /** A zip with no record in it is not a backup either, whatever else it happens to hold. */
    @Test
    fun `a zip without a record is refused`() {
        val store = seededStore()
        val file = File(context.cacheDir, "settings-only.zip")
        file.outputStream().use { out ->
            java.util.zip.ZipOutputStream(out).run {
                putNextEntry(java.util.zip.ZipEntry("settings.json"))
                write(settings.toDocument().toByteArray())
                closeEntry()
                finish()
                flush()
            }
        }
        store.close()

        assertThrows(NotABackupException::class.java) {
            readBackup(context, Uri.fromFile(file))
        }
    }

    /**
     * Pruning sorts backups by name and calls that chronological, so it had better be.
     *
     * The file name carries an ISO date and nothing else varies, which makes text order date order.
     * Reading the folder's own modified times instead would be at the mercy of whichever cloud app
     * syncs it, and several rewrite them on upload - which would drop the newest backup as though
     * it were the oldest.
     */
    @Test
    fun `backups sort by name into the order they were taken`() {
        val chronological = listOf(
            LocalDate.of(2026, 8, 9),
            LocalDate.of(2026, 8, 25),
            LocalDate.of(2026, 9, 3),
            LocalDate.of(2026, 12, 1),
        ).map(::backupFileName)

        assertEquals(chronological, chronological.shuffled().sorted())
    }

    /**
     * The user picked a folder, not a folder this app owns.
     *
     * Whatever else is in it is theirs, and a half-written `.part` is not a backup either - counting
     * one would let a failed write push a real backup out of the seven that are kept.
     */
    @Test
    fun `pruning only ever considers files this app named`() {
        assertTrue(isBackupFileName(backupFileName(LocalDate.of(2026, 8, 25))))
        assertFalse(isBackupFileName("tax-return-2025.zip"))
        assertFalse(isBackupFileName("egx-backup-2026-08-25.zip.part"))
        assertFalse(isBackupFileName("egx-diagnostics-2026-08-25.db"))
    }

    /**
     * A restore leaves nothing of its own behind.
     *
     * The scratch copy is a whole second database sitting in the app's storage, and one left there
     * would be read by the *next* restore instead of the file it was handed - quietly restoring
     * whatever the previous attempt had got as far as.
     */
    @Test
    fun `the scratch copy is gone once the backup has been read`() {
        val store = seededStore()

        val file = backupOf(store, settings.toDocument())
        store.close()
        readBackup(context, Uri.fromFile(file))

        assertTrue(context.getDatabasePath("restore-scratch.db").let { !it.exists() })
        assertTrue(!File(context.cacheDir, "restore-source").exists())
    }
}
