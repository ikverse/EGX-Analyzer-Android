package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.DriveBackup
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The parts of the Drive backup that do not touch the network: what is asked, read and deleted. */
class DriveBackupTest {

    private fun backup(day: String, id: String = day) = DriveBackup(id, "egx-backup-$day.zip")

    @Test
    fun `a files list is read into backups, skipping anything without an id or name`() {
        val json = """
            {"files":[
              {"id":"a","name":"egx-backup-2026-11-14.zip","size":"2048"},
              {"id":"b","name":"notes.txt"},
              {"name":"no-id.zip"},
              {"id":"c","name":""}
            ]}
        """.trimIndent()
        assertEquals(
            listOf(DriveBackup("a", "egx-backup-2026-11-14.zip", 2048), DriveBackup("b", "notes.txt", null)),
            parseDriveFiles(json),
        )
        assertEquals(emptyList<DriveBackup>(), parseDriveFiles("{}"))
    }

    @Test
    fun `the restore list holds only our backups, newest first`() {
        val files = listOf(backup("2026-11-12"), DriveBackup("x", "holiday.jpg"), backup("2026-11-14"), backup("2026-11-13"))
        assertEquals(
            listOf("2026-11-14", "2026-11-13", "2026-11-12"),
            driveBackupsNewestFirst(files).map(DriveBackup::id),
        )
    }

    @Test
    fun `an earlier copy of today goes, and so does everything past the seventh`() {
        val days = (5..14).map { "2026-11-%02d".format(it) }
        val files = days.map { backup(it) } + backup("2026-11-14", id = "today-earlier") +
            DriveBackup("x", "holiday.jpg")
        val removed = driveFilesToRemove(files, writtenId = "2026-11-14", writtenName = "egx-backup-2026-11-14.zip")
        assertEquals(
            listOf("today-earlier", "2026-11-07", "2026-11-06", "2026-11-05"),
            removed.map(DriveBackup::id),
        )
        assertTrue(removed.none { it.id == "x" })
    }

    @Test
    fun `nothing is removed while seven or fewer are held`() {
        val files = (8..14).map { backup("2026-11-%02d".format(it)) }
        assertEquals(
            emptyList<DriveBackup>(),
            driveFilesToRemove(files, writtenId = "2026-11-14", writtenName = "egx-backup-2026-11-14.zip"),
        )
    }

    @Test
    fun `the queries and bodies say what Drive expects`() {
        assertEquals(
            "name = 'EGX Analyzer' and mimeType = 'application/vnd.google-apps.folder' and trashed = false",
            driveFolderQuery(),
        )
        assertEquals("'abc' in parents and trashed = false", driveChildrenQuery("abc"))
        val upload = JSONObject(driveUploadMetadata("egx-backup-2026-11-14.zip", "folder1"))
        assertEquals("egx-backup-2026-11-14.zip", upload.getString("name"))
        assertEquals("application/zip", upload.getString("mimeType"))
        assertEquals("folder1", upload.getJSONArray("parents").getString(0))
        assertEquals("EGX Analyzer", JSONObject(driveFolderMetadata()).getString("name"))
    }

    @Test
    fun `only the drive file scope is asked for`() {
        assertEquals("https://www.googleapis.com/auth/drive.file", DRIVE_SCOPE)
    }
}
